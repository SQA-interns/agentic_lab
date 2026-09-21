"""Create registration, selection, consent, outbox, and rate-limit tables."""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "20260921_01"
down_revision: str | None = None
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "registrations",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("client_submission_id", sa.String(36), nullable=False, unique=True),
        sa.Column("payload_hash", sa.String(64), nullable=False),
        sa.Column("variant", sa.String(16), nullable=False),
        sa.Column("first_name", sa.String(100), nullable=False),
        sa.Column("last_name", sa.String(100), nullable=False),
        sa.Column("email", sa.String(254), nullable=False),
        sa.Column("organization", sa.String(200)),
        sa.Column("study_institution", sa.String(200)),
        sa.Column("study_programme", sa.String(200)),
        sa.Column("student_id", sa.String(64)),
        sa.Column("configuration_version", sa.String(100), nullable=False),
        sa.Column("anti_automation_nonce", sa.String(128), nullable=False, unique=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("backup_relative_path", sa.String(100), nullable=False),
        sa.Column("backup_sha256", sa.String(64), nullable=False),
        sa.Column("backup_size_bytes", sa.Integer(), nullable=False),
        sa.CheckConstraint(
            "(variant = 'external' AND organization IS NOT NULL AND study_institution IS NULL "
            "AND study_programme IS NULL AND student_id IS NULL) OR "
            "(variant = 'student' AND organization IS NULL AND study_institution IS NOT NULL "
            "AND study_programme IS NOT NULL AND student_id IS NOT NULL)",
            name="ck_registration_variant_fields",
        ),
    )
    op.create_table(
        "registration_options",
        sa.Column(
            "registration_id",
            sa.String(36),
            sa.ForeignKey("registrations.id", ondelete="CASCADE"),
            primary_key=True,
        ),
        sa.Column("option_id", sa.String(100), primary_key=True),
        sa.Column("display_name", sa.String(200), nullable=False),
        sa.Column("category", sa.String(20), nullable=False),
    )
    op.create_table(
        "registration_consents",
        sa.Column(
            "registration_id",
            sa.String(36),
            sa.ForeignKey("registrations.id", ondelete="CASCADE"),
            primary_key=True,
        ),
        sa.Column("consent_id", sa.String(100), primary_key=True),
        sa.Column("label", sa.String(500), nullable=False),
        sa.Column("policy_version", sa.String(100), nullable=False),
        sa.Column("accepted", sa.Boolean(), nullable=False),
        sa.Column("accepted_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_table(
        "email_outbox",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column(
            "registration_id",
            sa.String(36),
            sa.ForeignKey("registrations.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("kind", sa.String(40), nullable=False),
        sa.Column("recipient_set", sa.Text(), nullable=False),
        sa.Column("state", sa.String(16), nullable=False),
        sa.Column("attempt_count", sa.Integer(), nullable=False),
        sa.Column("available_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("lease_until", sa.DateTime(timezone=True)),
        sa.Column("last_error_code", sa.String(100)),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("sent_at", sa.DateTime(timezone=True)),
        sa.UniqueConstraint("registration_id", "kind"),
    )
    op.create_index("ix_email_outbox_registration_id", "email_outbox", ["registration_id"])
    op.create_index("ix_email_outbox_state", "email_outbox", ["state"])
    op.create_table(
        "rate_limit_buckets",
        sa.Column("network_hash", sa.String(32), primary_key=True),
        sa.Column("action", sa.String(40), primary_key=True),
        sa.Column("window_start", sa.DateTime(timezone=True), primary_key=True),
        sa.Column("request_count", sa.Integer(), nullable=False),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index("ix_rate_limit_buckets_expires_at", "rate_limit_buckets", ["expires_at"])


def downgrade() -> None:
    op.drop_index("ix_rate_limit_buckets_expires_at", table_name="rate_limit_buckets")
    op.drop_table("rate_limit_buckets")
    op.drop_index("ix_email_outbox_state", table_name="email_outbox")
    op.drop_index("ix_email_outbox_registration_id", table_name="email_outbox")
    op.drop_table("email_outbox")
    op.drop_table("registration_consents")
    op.drop_table("registration_options")
    op.drop_table("registrations")
