from __future__ import annotations

import json
from enum import StrEnum
from pathlib import Path
from threading import RLock
from typing import Annotated

from pydantic import BaseModel, ConfigDict, Field, StringConstraints, field_validator

from app.domain.schemas import OptionId, Variant


class OptionCategory(StrEnum):
    WORKSHOP = "workshop"
    EVENT = "event"
    MEAL = "meal"
    OTHER = "other"


class ConfigModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class ConferenceOption(ConfigModel):
    id: OptionId
    display_name: Annotated[str, StringConstraints(min_length=1, max_length=200)] = Field(
        alias="displayName"
    )
    category: OptionCategory
    active: bool
    variants: set[Variant]


class ConsentDefinition(ConfigModel):
    id: OptionId
    label: Annotated[str, StringConstraints(min_length=1, max_length=500)]
    policy_version: Annotated[str, StringConstraints(min_length=1, max_length=100)] = Field(
        alias="policyVersion"
    )
    required: bool
    variants: set[Variant]


class ConferenceConfiguration(ConfigModel):
    conference_name: Annotated[str, StringConstraints(min_length=1, max_length=200)] = Field(
        alias="conferenceName"
    )
    configuration_version: Annotated[str, StringConstraints(min_length=1, max_length=100)] = Field(
        alias="configurationVersion"
    )
    options: list[ConferenceOption]
    consents: list[ConsentDefinition]

    @field_validator("options")
    @classmethod
    def option_ids_unique(cls, value: list[ConferenceOption]) -> list[ConferenceOption]:
        ids = [item.id for item in value]
        if len(ids) != len(set(ids)):
            raise ValueError("option identifiers must be unique")
        return value

    @field_validator("consents")
    @classmethod
    def consent_ids_unique(cls, value: list[ConsentDefinition]) -> list[ConsentDefinition]:
        ids = [item.id for item in value]
        if len(ids) != len(set(ids)):
            raise ValueError("consent identifiers must be unique")
        return value


class ConfigurationRepository:
    """Reloads an atomically replaced conference configuration when it changes."""

    def __init__(self, path: Path) -> None:
        self._path = path
        self._lock = RLock()
        self._mtime_ns: int | None = None
        self._configuration: ConferenceConfiguration | None = None

    def get(self) -> ConferenceConfiguration:
        with self._lock:
            stat = self._path.stat()
            if self._configuration is None or stat.st_mtime_ns != self._mtime_ns:
                raw = json.loads(self._path.read_text(encoding="utf-8"))
                configuration = ConferenceConfiguration.model_validate(raw)
                if not any(consent.required for consent in configuration.consents):
                    raise ValueError("at least one mandatory consent must be configured")
                self._configuration = configuration
                self._mtime_ns = stat.st_mtime_ns
            return self._configuration
