from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urlparse


class ConfigurationError(RuntimeError):
    """Raised when runtime configuration is unsafe or incomplete."""


def _boolean(name: str, default: bool) -> bool:
    raw = os.getenv(name)
    if raw is None:
        return default
    if raw.lower() in {"1", "true", "yes", "on"}:
        return True
    if raw.lower() in {"0", "false", "no", "off"}:
        return False
    raise ConfigurationError(f"{name} must be a boolean")


def _integer(name: str, default: int, minimum: int = 1) -> int:
    try:
        value = int(os.getenv(name, str(default)))
    except ValueError as exc:
        raise ConfigurationError(f"{name} must be an integer") from exc
    if value < minimum:
        raise ConfigurationError(f"{name} must be at least {minimum}")
    return value


def _value(name: str, default: str) -> str:
    file_name = os.getenv(f"{name}_FILE")
    if file_name:
        try:
            return Path(file_name).read_text(encoding="utf-8").strip()
        except OSError as exc:
            raise ConfigurationError(f"unable to read {name}_FILE") from exc
    return os.getenv(name, default)


@dataclass(frozen=True, slots=True)
class Settings:
    environment: str
    database_url: str
    backup_directory: Path
    conference_config_path: Path
    public_origin: str
    trusted_hosts: tuple[str, ...]
    challenge_secret: str
    network_hash_secret: str
    export_token: str
    organizer_emails: tuple[str, ...]
    smtp_host: str
    smtp_port: int
    smtp_username: str | None
    smtp_password: str | None
    smtp_from_address: str
    smtp_starttls: bool
    smtp_timeout_seconds: int
    worker_max_attempts: int
    worker_lease_seconds: int
    auto_create_schema: bool
    log_level: str
    enforce_https_headers: bool
    registration_rate_limit: int
    export_rate_limit: int

    @property
    def production(self) -> bool:
        return self.environment == "production"

    @classmethod
    def from_env(cls) -> Settings:
        environment = os.getenv("APP_ENV", "development").lower()
        settings = cls(
            environment=environment,
            database_url=_value("DATABASE_URL", "sqlite:///./data/registration.db"),
            backup_directory=Path(os.getenv("BACKUP_DIRECTORY", "./data/backups")),
            conference_config_path=Path(
                os.getenv("CONFERENCE_CONFIG_PATH", "./config/conference-options.json")
            ),
            public_origin=os.getenv("PUBLIC_ORIGIN", "http://localhost:8000").rstrip("/"),
            trusted_hosts=tuple(
                item.strip()
                for item in os.getenv("TRUSTED_HOSTS", "localhost,127.0.0.1,testserver").split(",")
                if item.strip()
            ),
            challenge_secret=_value("CHALLENGE_SECRET", "development-challenge-secret"),
            network_hash_secret=_value("NETWORK_HASH_SECRET", "development-network-hash-secret"),
            export_token=_value("EXPORT_TOKEN", "development-export-token"),
            organizer_emails=tuple(
                item.strip()
                for item in os.getenv("ORGANIZER_EMAILS", "organizer@example.test").split(",")
                if item.strip()
            ),
            smtp_host=os.getenv("SMTP_HOST", "localhost"),
            smtp_port=_integer("SMTP_PORT", 1025),
            smtp_username=os.getenv("SMTP_USERNAME") or None,
            smtp_password=_value("SMTP_PASSWORD", "") or None,
            smtp_from_address=os.getenv("SMTP_FROM_ADDRESS", "conference@example.test"),
            smtp_starttls=_boolean("SMTP_STARTTLS", False),
            smtp_timeout_seconds=_integer("SMTP_TIMEOUT_SECONDS", 15),
            worker_max_attempts=_integer("WORKER_MAX_ATTEMPTS", 8),
            worker_lease_seconds=_integer("WORKER_LEASE_SECONDS", 120),
            auto_create_schema=_boolean("AUTO_CREATE_SCHEMA", environment != "production"),
            log_level=os.getenv("LOG_LEVEL", "INFO").upper(),
            enforce_https_headers=_boolean("ENFORCE_HTTPS_HEADERS", environment == "production"),
            registration_rate_limit=_integer("REGISTRATION_RATE_LIMIT", 20),
            export_rate_limit=_integer("EXPORT_RATE_LIMIT", 10),
        )
        settings.validate()
        return settings

    def validate(self) -> None:
        if self.environment not in {"development", "test", "production"}:
            raise ConfigurationError("APP_ENV must be development, test, or production")
        if not self.trusted_hosts:
            raise ConfigurationError("TRUSTED_HOSTS must not be empty")
        if not self.organizer_emails:
            raise ConfigurationError("ORGANIZER_EMAILS must not be empty")
        email_settings = (*self.organizer_emails, self.smtp_from_address)
        if any("\n" in address or "\r" in address for address in email_settings):
            raise ConfigurationError("email settings contain prohibited control characters")
        if bool(self.smtp_username) != bool(self.smtp_password):
            raise ConfigurationError("SMTP_USERNAME and SMTP_PASSWORD must be configured together")
        if len(self.challenge_secret) < 16 or len(self.network_hash_secret) < 16:
            raise ConfigurationError("anti-automation secrets must contain at least 16 characters")
        if len(self.export_token) < 16:
            raise ConfigurationError("EXPORT_TOKEN must contain at least 16 characters")
        origin = urlparse(self.public_origin)
        if not origin.scheme or not origin.netloc or origin.path not in {"", "/"}:
            raise ConfigurationError("PUBLIC_ORIGIN must be an absolute origin without a path")
        if self.production:
            forbidden = {
                "development-challenge-secret",
                "development-network-hash-secret",
                "development-export-token",
            }
            if {self.challenge_secret, self.network_hash_secret, self.export_token} & forbidden:
                raise ConfigurationError("development secrets are forbidden in production")
            if (
                min(
                    len(self.challenge_secret),
                    len(self.network_hash_secret),
                    len(self.export_token),
                )
                < 32
            ):
                raise ConfigurationError("production secrets must contain at least 32 characters")
            if not self.database_url.startswith("postgresql+"):
                raise ConfigurationError("production DATABASE_URL must use PostgreSQL")
            if origin.scheme != "https":
                raise ConfigurationError("production PUBLIC_ORIGIN must use HTTPS")
            if not self.smtp_starttls:
                raise ConfigurationError("SMTP_STARTTLS must be enabled in production")
            if not self.smtp_username or not self.smtp_password:
                raise ConfigurationError("authenticated SMTP is required in production")
            if self.auto_create_schema:
                raise ConfigurationError("AUTO_CREATE_SCHEMA must be disabled in production")
