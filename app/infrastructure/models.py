from __future__ import annotations

from datetime import datetime
from uuid import uuid4

from sqlalchemy import (
    Boolean,
    CheckConstraint,
    DateTime,
    ForeignKey,
    Integer,
    String,
    Text,
    UniqueConstraint,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.infrastructure.database import Base


def uuid_string() -> str:
    return str(uuid4())


class Registration(Base):
    __tablename__ = "registrations"
    __table_args__ = (
        CheckConstraint(
            "(variant = 'external' AND organization IS NOT NULL AND study_institution IS NULL "
            "AND study_programme IS NULL AND student_id IS NULL) OR "
            "(variant = 'student' AND organization IS NULL AND study_institution IS NOT NULL "
            "AND study_programme IS NOT NULL AND student_id IS NOT NULL)",
            name="ck_registration_variant_fields",
        ),
    )

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=uuid_string)
    client_submission_id: Mapped[str] = mapped_column(String(36), unique=True, nullable=False)
    payload_hash: Mapped[str] = mapped_column(String(64), nullable=False)
    variant: Mapped[str] = mapped_column(String(16), nullable=False)
    first_name: Mapped[str] = mapped_column(String(100), nullable=False)
    last_name: Mapped[str] = mapped_column(String(100), nullable=False)
    email: Mapped[str] = mapped_column(String(254), nullable=False)
    organization: Mapped[str | None] = mapped_column(String(200))
    study_institution: Mapped[str | None] = mapped_column(String(200))
    study_programme: Mapped[str | None] = mapped_column(String(200))
    student_id: Mapped[str | None] = mapped_column(String(64))
    configuration_version: Mapped[str] = mapped_column(String(100), nullable=False)
    anti_automation_nonce: Mapped[str] = mapped_column(String(128), unique=True, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    backup_relative_path: Mapped[str] = mapped_column(
        String(100), nullable=False, default="pending"
    )
    backup_sha256: Mapped[str] = mapped_column(String(64), nullable=False, default="pending")
    backup_size_bytes: Mapped[int] = mapped_column(Integer, nullable=False, default=0)

    options: Mapped[list[RegistrationOption]] = relationship(
        back_populates="registration", cascade="all, delete-orphan"
    )
    consents: Mapped[list[RegistrationConsent]] = relationship(
        back_populates="registration", cascade="all, delete-orphan"
    )
    outbox_jobs: Mapped[list[EmailOutbox]] = relationship(
        back_populates="registration", cascade="all, delete-orphan"
    )


class RegistrationOption(Base):
    __tablename__ = "registration_options"

    registration_id: Mapped[str] = mapped_column(
        ForeignKey("registrations.id", ondelete="CASCADE"), primary_key=True
    )
    option_id: Mapped[str] = mapped_column(String(100), primary_key=True)
    display_name: Mapped[str] = mapped_column(String(200), nullable=False)
    category: Mapped[str] = mapped_column(String(20), nullable=False)
    registration: Mapped[Registration] = relationship(back_populates="options")


class RegistrationConsent(Base):
    __tablename__ = "registration_consents"

    registration_id: Mapped[str] = mapped_column(
        ForeignKey("registrations.id", ondelete="CASCADE"), primary_key=True
    )
    consent_id: Mapped[str] = mapped_column(String(100), primary_key=True)
    label: Mapped[str] = mapped_column(String(500), nullable=False)
    policy_version: Mapped[str] = mapped_column(String(100), nullable=False)
    accepted: Mapped[bool] = mapped_column(Boolean, nullable=False)
    accepted_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    registration: Mapped[Registration] = relationship(back_populates="consents")


class EmailOutbox(Base):
    __tablename__ = "email_outbox"
    __table_args__ = (UniqueConstraint("registration_id", "kind"),)

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=uuid_string)
    registration_id: Mapped[str] = mapped_column(
        ForeignKey("registrations.id", ondelete="CASCADE"), nullable=False, index=True
    )
    kind: Mapped[str] = mapped_column(String(40), nullable=False)
    recipient_set: Mapped[str] = mapped_column(Text, nullable=False)
    state: Mapped[str] = mapped_column(String(16), nullable=False, default="pending", index=True)
    attempt_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    available_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    lease_until: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    last_error_code: Mapped[str | None] = mapped_column(String(100))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    sent_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    registration: Mapped[Registration] = relationship(back_populates="outbox_jobs")


class RateLimitBucket(Base):
    __tablename__ = "rate_limit_buckets"

    network_hash: Mapped[str] = mapped_column(String(32), primary_key=True)
    action: Mapped[str] = mapped_column(String(40), primary_key=True)
    window_start: Mapped[datetime] = mapped_column(DateTime(timezone=True), primary_key=True)
    request_count: Mapped[int] = mapped_column(Integer, nullable=False)
    expires_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, index=True
    )
