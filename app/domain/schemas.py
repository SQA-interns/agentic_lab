from __future__ import annotations

import re
import unicodedata
from enum import StrEnum
from typing import Annotated, Literal
from uuid import UUID

from pydantic import (
    BaseModel,
    ConfigDict,
    EmailStr,
    Field,
    StringConstraints,
    ValidationInfo,
    field_validator,
    model_validator,
)

_CONTROL_CHARACTERS = re.compile(r"[\x00-\x1f\x7f-\x9f]")


def normalize_text(value: str) -> str:
    normalized = unicodedata.normalize("NFC", value.strip())
    if _CONTROL_CHARACTERS.search(normalized):
        raise ValueError("control characters are not allowed")
    return normalized


Name = Annotated[str, StringConstraints(min_length=1, max_length=100)]
LongText = Annotated[str, StringConstraints(min_length=1, max_length=200)]
StudentId = Annotated[str, StringConstraints(min_length=1, max_length=64)]
OptionId = Annotated[
    str,
    StringConstraints(min_length=1, max_length=100, pattern=r"^[a-z0-9][a-z0-9._-]*$"),
]


class Variant(StrEnum):
    EXTERNAL = "external"
    STUDENT = "student"


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)


class ExternalParticipant(StrictModel):
    first_name: Name = Field(alias="firstName")
    last_name: Name = Field(alias="lastName")
    email: EmailStr
    organization: LongText

    @field_validator("first_name", "last_name", "organization", mode="before")
    @classmethod
    def normalize_strings(cls, value: object) -> object:
        return normalize_text(value) if isinstance(value, str) else value

    @field_validator("email", mode="before")
    @classmethod
    def normalize_email(cls, value: object) -> object:
        if not isinstance(value, str):
            return value
        value = normalize_text(value)
        if "@" in value:
            local, domain = value.rsplit("@", 1)
            return f"{local}@{domain.lower()}"
        return value


class StudentParticipant(StrictModel):
    first_name: Name = Field(alias="firstName")
    last_name: Name = Field(alias="lastName")
    email: EmailStr
    study_institution: LongText = Field(alias="studyInstitution")
    study_programme: LongText = Field(alias="studyProgramme")
    student_id: StudentId = Field(alias="studentId")

    @field_validator(
        "first_name",
        "last_name",
        "study_institution",
        "study_programme",
        "student_id",
        mode="before",
    )
    @classmethod
    def normalize_strings(cls, value: object) -> object:
        return normalize_text(value) if isinstance(value, str) else value

    @field_validator("email", mode="before")
    @classmethod
    def normalize_email(cls, value: object) -> object:
        return ExternalParticipant.normalize_email(value)


class RegistrationRequest(StrictModel):
    submission_id: UUID = Field(alias="submissionId")
    variant: Variant
    participant: ExternalParticipant | StudentParticipant
    option_ids: list[OptionId] = Field(default_factory=list, alias="optionIds", max_length=100)
    consents: dict[OptionId, bool]
    challenge_token: Annotated[str, StringConstraints(min_length=20, max_length=2048)] = Field(
        alias="challengeToken"
    )
    website: Annotated[str, StringConstraints(max_length=200)] = ""

    @field_validator("participant", mode="before")
    @classmethod
    def validate_variant_participant(cls, value: object, info: ValidationInfo) -> object:
        variant = info.data.get("variant")
        if variant == Variant.EXTERNAL:
            return ExternalParticipant.model_validate(value)
        if variant == Variant.STUDENT:
            return StudentParticipant.model_validate(value)
        return value

    @field_validator("option_ids")
    @classmethod
    def unique_option_ids(cls, value: list[str]) -> list[str]:
        if len(value) != len(set(value)):
            raise ValueError("option identifiers must be unique")
        return value

    @model_validator(mode="after")
    def participant_matches_variant(self) -> RegistrationRequest:
        if self.variant == Variant.EXTERNAL and not isinstance(
            self.participant, ExternalParticipant
        ):
            raise ValueError("external variant requires external participant fields")
        if self.variant == Variant.STUDENT and not isinstance(self.participant, StudentParticipant):
            raise ValueError("student variant requires student participant fields")
        return self


class RegistrationResponse(StrictModel):
    registration_id: UUID = Field(alias="registrationId")
    status: Literal["registered"] = "registered"
    message: str = "Your registration was received."

    model_config = ConfigDict(populate_by_name=True)


class FieldDefinition(StrictModel):
    name: str
    label: str
    input_type: Literal["text", "email"] = Field(alias="inputType")
    max_length: int = Field(alias="maxLength")
    autocomplete: str

    model_config = ConfigDict(populate_by_name=True)


class FormContextResponse(StrictModel):
    conference_name: str = Field(alias="conferenceName")
    configuration_version: str = Field(alias="configurationVersion")
    variant: Variant
    fields: list[FieldDefinition]
    options: list[dict[str, object]]
    consents: list[dict[str, object]]
    challenge_token: str = Field(alias="challengeToken")

    model_config = ConfigDict(populate_by_name=True)


FIXED_FIELDS: dict[Variant, list[FieldDefinition]] = {
    Variant.EXTERNAL: [
        FieldDefinition(
            name="firstName",
            label="First name",
            input_type="text",
            max_length=100,
            autocomplete="given-name",
        ),
        FieldDefinition(
            name="lastName",
            label="Last name",
            input_type="text",
            max_length=100,
            autocomplete="family-name",
        ),
        FieldDefinition(
            name="email",
            label="Email",
            input_type="email",
            max_length=254,
            autocomplete="email",
        ),
        FieldDefinition(
            name="organization",
            label="Organization / institution",
            input_type="text",
            max_length=200,
            autocomplete="organization",
        ),
    ],
    Variant.STUDENT: [
        FieldDefinition(
            name="firstName",
            label="First name",
            input_type="text",
            max_length=100,
            autocomplete="given-name",
        ),
        FieldDefinition(
            name="lastName",
            label="Last name",
            input_type="text",
            max_length=100,
            autocomplete="family-name",
        ),
        FieldDefinition(
            name="email",
            label="Email",
            input_type="email",
            max_length=254,
            autocomplete="email",
        ),
        FieldDefinition(
            name="studyInstitution",
            label="Study institution",
            input_type="text",
            max_length=200,
            autocomplete="organization",
        ),
        FieldDefinition(
            name="studyProgramme",
            label="Study programme",
            input_type="text",
            max_length=200,
            autocomplete="off",
        ),
        FieldDefinition(
            name="studentId",
            label="Student ID",
            input_type="text",
            max_length=64,
            autocomplete="off",
        ),
    ],
}
