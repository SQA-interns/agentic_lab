from __future__ import annotations

import argparse
import hashlib
import logging
import time
from datetime import UTC, datetime, timedelta

from sqlalchemy import or_, select, update
from sqlalchemy.orm import selectinload

from app.core.config import Settings
from app.core.logging import configure_logging
from app.infrastructure.database import Database
from app.infrastructure.email import Mailer
from app.infrastructure.json_store import JsonBackupStore
from app.infrastructure.models import EmailOutbox, Registration

logger = logging.getLogger(__name__)


class OutboxWorker:
    def __init__(self, settings: Settings, database: Database, mailer: Mailer) -> None:
        self._settings = settings
        self._database = database
        self._mailer = mailer

    def process_one(self) -> bool:
        now = datetime.now(UTC)
        with self._database.session_factory.begin() as session:
            job = session.scalar(
                select(EmailOutbox)
                .where(
                    or_(
                        EmailOutbox.state.in_(["pending", "retry"])
                        & (EmailOutbox.available_at <= now),
                        (EmailOutbox.state == "sending") & (EmailOutbox.lease_until < now),
                    )
                )
                .order_by(EmailOutbox.available_at, EmailOutbox.created_at)
                .with_for_update(skip_locked=True)
                .limit(1)
            )
            if job is None:
                return False
            job.state = "sending"
            job.attempt_count += 1
            job.lease_until = now + timedelta(seconds=self._settings.worker_lease_seconds)
            job_id = job.id

        try:
            with self._database.session_factory() as session:
                job = session.get(EmailOutbox, job_id)
                if job is None:
                    return False
                registration = session.scalar(
                    select(Registration)
                    .where(Registration.id == job.registration_id)
                    .options(
                        selectinload(Registration.options),
                        selectinload(Registration.consents),
                    )
                )
                if registration is None:
                    raise RuntimeError("outbox registration is missing")
                message = self._mailer.build_message(job, registration)
            self._mailer.send(message)
        except Exception as exc:
            self._record_failure(job_id, exc)
            return True

        with self._database.session_factory.begin() as session:
            session.execute(
                update(EmailOutbox)
                .where(EmailOutbox.id == job_id)
                .values(
                    state="sent",
                    sent_at=datetime.now(UTC),
                    lease_until=None,
                    last_error_code=None,
                )
            )
        logger.info("email sent", extra={"event": "email", "outcome": "sent"})
        return True

    def _record_failure(self, job_id: str, error: Exception) -> None:
        now = datetime.now(UTC)
        with self._database.session_factory.begin() as session:
            job = session.get(EmailOutbox, job_id)
            if job is None:
                return
            exhausted = job.attempt_count >= self._settings.worker_max_attempts
            delay = min(3600, 30 * (2 ** max(0, job.attempt_count - 1)))
            jitter = int(hashlib.sha256(job.id.encode()).hexdigest()[:2], 16) % 20
            job.state = "failed" if exhausted else "retry"
            job.available_at = now + timedelta(seconds=delay + jitter)
            job.lease_until = None
            job.last_error_code = type(error).__name__[:100]
        logger.warning(
            "email delivery failed",
            extra={"event": "email", "outcome": "failed", "registration_id": job_id},
        )


def requeue_failed(database: Database) -> int:
    with database.session_factory.begin() as session:
        result = session.execute(
            update(EmailOutbox)
            .where(EmailOutbox.state == "failed")
            .values(
                state="retry",
                attempt_count=0,
                available_at=datetime.now(UTC),
                lease_until=None,
                last_error_code=None,
            )
        )
        return result.rowcount


def main() -> None:
    parser = argparse.ArgumentParser(description="Process registration email outbox")
    parser.add_argument("--once", action="store_true", help="process at most one message")
    parser.add_argument(
        "--requeue-failed", action="store_true", help="make permanently failed jobs retryable"
    )
    arguments = parser.parse_args()
    settings = Settings.from_env()
    configure_logging(settings.log_level)
    database = Database(settings.database_url)
    if settings.auto_create_schema:
        database.create_schema()
    backups = JsonBackupStore(settings.backup_directory)
    worker = OutboxWorker(settings, database, Mailer(settings, backups))
    if arguments.requeue_failed:
        print(requeue_failed(database))
        return
    if arguments.once:
        worker.process_one()
        return
    while True:
        if not worker.process_one():
            time.sleep(2)


if __name__ == "__main__":
    main()
