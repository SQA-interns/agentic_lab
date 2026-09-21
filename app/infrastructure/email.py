from __future__ import annotations

import json
import smtplib
from email.message import EmailMessage
from email.policy import SMTP

from app.core.config import Settings
from app.infrastructure.json_store import JsonBackupStore, StoredJson
from app.infrastructure.models import EmailOutbox, Registration


class Mailer:
    def __init__(self, settings: Settings, backups: JsonBackupStore) -> None:
        self._settings = settings
        self._backups = backups

    def build_message(self, job: EmailOutbox, registration: Registration) -> EmailMessage:
        recipients = json.loads(job.recipient_set)
        if not isinstance(recipients, list) or not all(
            isinstance(item, str) for item in recipients
        ):
            raise ValueError("invalid outbox recipients")
        message = EmailMessage(policy=SMTP)
        message["From"] = self._settings.smtp_from_address
        message["To"] = ", ".join(recipients)
        message["Message-ID"] = f"<{job.id}@conference-registration>"
        selected = ", ".join(option.display_name for option in registration.options) or "None"
        if job.kind == "participant_confirmation":
            backup = self._verified_backup(registration)
            conference_name = str(backup.get("conferenceName", "Conference"))
            message["Subject"] = f"{conference_name} registration confirmation"
            message.set_content(
                f"Your registration for {conference_name} was received successfully.\n\n"
                f"Registration ID: {registration.id}\n"
                f"Registration type: {registration.variant}\n"
                f"Selected activities: {selected}\n"
            )
        elif job.kind == "organizer_notification":
            message["Subject"] = "New conference registration"
            participant_lines = [
                f"Registration ID: {registration.id}",
                f"Registration type: {registration.variant}",
                f"First name: {registration.first_name}",
                f"Last name: {registration.last_name}",
                f"Email: {registration.email}",
                f"Organization / institution: {registration.organization or ''}",
                f"Study institution: {registration.study_institution or ''}",
                f"Study programme: {registration.study_programme or ''}",
                f"Student ID: {registration.student_id or ''}",
                f"Selected activities: {selected}",
            ]
            message.set_content("\n".join(participant_lines) + "\n")
            backup_bytes = self._verified_backup_bytes(registration)
            message.add_attachment(
                backup_bytes,
                maintype="application",
                subtype="json",
                filename=f"registration-{registration.id}.json",
            )
        else:
            raise ValueError("unknown email kind")
        return message

    def _verified_backup(self, registration: Registration) -> dict[str, object]:
        parsed = json.loads(self._verified_backup_bytes(registration))
        if not isinstance(parsed, dict):
            raise ValueError("registration backup is not an object")
        return parsed

    def _verified_backup_bytes(self, registration: Registration) -> bytes:
        return self._backups.read_verified(
            StoredJson(
                relative_path=registration.backup_relative_path,
                sha256=registration.backup_sha256,
                size_bytes=registration.backup_size_bytes,
            )
        )

    def send(self, message: EmailMessage) -> None:
        with smtplib.SMTP(
            self._settings.smtp_host,
            self._settings.smtp_port,
            timeout=self._settings.smtp_timeout_seconds,
        ) as smtp:
            if self._settings.smtp_starttls:
                smtp.starttls()
            if self._settings.smtp_username and self._settings.smtp_password:
                smtp.login(self._settings.smtp_username, self._settings.smtp_password)
            smtp.send_message(message)
