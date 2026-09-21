from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from datetime import UTC, datetime
from typing import Any
from uuid import UUID, uuid4

from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import sessionmaker

from app.core.security import ChallengeError, ChallengeSigner
from app.domain.configuration import ConferenceConfiguration, ConfigurationRepository
from app.domain.schemas import RegistrationRequest, Variant
from app.infrastructure.json_store import JsonBackupStore, StoredJson
from app.infrastructure.models import (
    EmailOutbox,
    Registration,
    RegistrationConsent,
    RegistrationOption,
)


class DomainValidationError(ValueError):
    def __init__(self, fields: dict[str, str]) -> None:
        super().__init__("registration validation failed")
        self.fields = fields


class SubmissionConflictError(ValueError):
    pass


class AutomationRejectedError(ValueError):
    pass


@dataclass(frozen=True, slots=True)
class RegistrationResult:
    registration_id: UUID
    existing: bool


class RegistrationService:
    def __init__(
        self,
        sessions: sessionmaker,  # type: ignore[type-arg]
        configurations: ConfigurationRepository,
        challenges: ChallengeSigner,
        backups: JsonBackupStore,
        organizer_emails: tuple[str, ...],
    ) -> None:
        self._sessions = sessions
        self._configurations = configurations
        self._challenges = challenges
        self._backups = backups
        self._organizer_emails = organizer_emails

    def register(self, request: RegistrationRequest) -> RegistrationResult:
        if request.website:
            raise AutomationRejectedError
        try:
            challenge = self._challenges.verify(request.challenge_token, request.variant.value)
        except ChallengeError as exc:
            raise AutomationRejectedError from exc

        configuration = self._configurations.get()
        selected_options, accepted_consents = self._validate_configuration(request, configuration)
        normalized_payload = request.model_dump(
            mode="json",
            by_alias=True,
            exclude={"challenge_token", "website"},
        )
        payload_hash = hashlib.sha256(
            json.dumps(normalized_payload, ensure_ascii=False, sort_keys=True).encode("utf-8")
        ).hexdigest()

        with self._sessions() as session:
            existing = session.scalar(
                select(Registration).where(
                    Registration.client_submission_id == str(request.submission_id)
                )
            )
            if existing is not None:
                if existing.payload_hash != payload_hash:
                    raise SubmissionConflictError
                return RegistrationResult(UUID(existing.id), existing=True)

        registration_id = str(uuid4())
        now = datetime.now(UTC)
        participant = request.participant
        participant_data = participant.model_dump(mode="json")
        registration = Registration(
            id=registration_id,
            client_submission_id=str(request.submission_id),
            payload_hash=payload_hash,
            variant=request.variant.value,
            first_name=str(participant.first_name),
            last_name=str(participant.last_name),
            email=str(participant.email),
            organization=(
                participant_data.get("organization")
                if request.variant == Variant.EXTERNAL
                else None
            ),
            study_institution=(
                participant_data.get("study_institution")
                if request.variant == Variant.STUDENT
                else None
            ),
            study_programme=(
                participant_data.get("study_programme")
                if request.variant == Variant.STUDENT
                else None
            ),
            student_id=(
                participant_data.get("student_id") if request.variant == Variant.STUDENT else None
            ),
            configuration_version=configuration.configuration_version,
            anti_automation_nonce=challenge.nonce,
            created_at=now,
        )
        registration.options = [
            RegistrationOption(
                option_id=option.id,
                display_name=option.display_name,
                category=option.category.value,
            )
            for option in selected_options
        ]
        registration.consents = [
            RegistrationConsent(
                consent_id=consent.id,
                label=consent.label,
                policy_version=consent.policy_version,
                accepted=True,
                accepted_at=now,
            )
            for consent in accepted_consents
        ]
        registration.outbox_jobs = [
            EmailOutbox(
                kind="participant_confirmation",
                recipient_set=json.dumps([str(participant.email)]),
                state="pending",
                available_at=now,
                created_at=now,
            ),
            EmailOutbox(
                kind="organizer_notification",
                recipient_set=json.dumps(list(self._organizer_emails)),
                state="pending",
                available_at=now,
                created_at=now,
            ),
        ]
        snapshot = self._snapshot(
            registration,
            request,
            configuration,
            selected_options,
            accepted_consents,
        )
        stored: StoredJson | None = None
        try:
            with self._sessions.begin() as session:
                session.add(registration)
                session.flush()
                stored = self._backups.write(registration_id, snapshot)
                registration.backup_relative_path = stored.relative_path
                registration.backup_sha256 = stored.sha256
                registration.backup_size_bytes = stored.size_bytes
        except IntegrityError as exc:
            if stored is not None:
                self._backups.delete(stored.relative_path)
            return self._resolve_integrity_race(request.submission_id, payload_hash, exc)
        except BaseException:
            if stored is not None:
                self._backups.delete(stored.relative_path)
            raise
        return RegistrationResult(UUID(registration_id), existing=False)

    def _resolve_integrity_race(
        self, submission_id: UUID, payload_hash: str, original: IntegrityError
    ) -> RegistrationResult:
        with self._sessions() as session:
            existing = session.scalar(
                select(Registration).where(Registration.client_submission_id == str(submission_id))
            )
            if existing is None:
                raise AutomationRejectedError from original
            if existing.payload_hash != payload_hash:
                raise SubmissionConflictError from original
            return RegistrationResult(UUID(existing.id), existing=True)

    @staticmethod
    def _validate_configuration(
        request: RegistrationRequest, configuration: ConferenceConfiguration
    ) -> tuple[list[Any], list[Any]]:
        applicable_options = {
            option.id: option
            for option in configuration.options
            if option.active and request.variant in option.variants
        }
        invalid_options = [item for item in request.option_ids if item not in applicable_options]
        fields: dict[str, str] = {}
        if invalid_options:
            fields["optionIds"] = "One or more selected activities are unavailable."

        applicable_consents = {
            consent.id: consent
            for consent in configuration.consents
            if request.variant in consent.variants
        }
        unknown_consents = set(request.consents) - set(applicable_consents)
        if unknown_consents:
            fields["consents"] = "One or more consent choices are not recognized."
        for consent in applicable_consents.values():
            if consent.required and request.consents.get(consent.id) is not True:
                fields[f"consents.{consent.id}"] = "This consent is required."
        if fields:
            raise DomainValidationError(fields)
        return (
            [applicable_options[item] for item in request.option_ids],
            [
                applicable_consents[item]
                for item, accepted in request.consents.items()
                if accepted and item in applicable_consents
            ],
        )

    @staticmethod
    def _snapshot(
        registration: Registration,
        request: RegistrationRequest,
        configuration: ConferenceConfiguration,
        selected_options: list[Any],
        accepted_consents: list[Any],
    ) -> dict[str, Any]:
        return {
            "schemaVersion": 1,
            "registrationId": registration.id,
            "submissionId": registration.client_submission_id,
            "registeredAt": registration.created_at.isoformat(),
            "variant": registration.variant,
            "conferenceName": configuration.conference_name,
            "configurationVersion": configuration.configuration_version,
            "participant": request.participant.model_dump(mode="json", by_alias=True),
            "options": [
                {
                    "id": option.id,
                    "displayName": option.display_name,
                    "category": option.category.value,
                }
                for option in selected_options
            ],
            "consents": [
                {
                    "id": consent.id,
                    "label": consent.label,
                    "policyVersion": consent.policy_version,
                    "accepted": True,
                    "acceptedAt": registration.created_at.isoformat(),
                }
                for consent in accepted_consents
            ],
        }
