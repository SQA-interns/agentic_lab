from __future__ import annotations

from datetime import UTC, datetime, timedelta

from sqlalchemy import select, update
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import sessionmaker

from app.infrastructure.models import RateLimitBucket


class RateLimitExceeded(RuntimeError):
    pass


class DatabaseRateLimiter:
    def __init__(self, sessions: sessionmaker, window_seconds: int = 60) -> None:  # type: ignore[type-arg]
        self._sessions = sessions
        self._window_seconds = window_seconds

    def check(self, network_hash: str, action: str, limit: int) -> None:
        now = datetime.now(UTC)
        epoch = int(now.timestamp())
        window_epoch = epoch - (epoch % self._window_seconds)
        window = datetime.fromtimestamp(window_epoch, UTC)
        expires = window + timedelta(seconds=self._window_seconds * 2)
        count: int | None = None
        try:
            with self._sessions.begin() as session:
                count = session.scalar(
                    update(RateLimitBucket)
                    .where(
                        RateLimitBucket.network_hash == network_hash,
                        RateLimitBucket.action == action,
                        RateLimitBucket.window_start == window,
                    )
                    .values(request_count=RateLimitBucket.request_count + 1)
                    .returning(RateLimitBucket.request_count)
                )
                if count is None:
                    session.add(
                        RateLimitBucket(
                            network_hash=network_hash,
                            action=action,
                            window_start=window,
                            request_count=1,
                            expires_at=expires,
                        )
                    )
                    count = 1
        except IntegrityError:
            # Another replica created this window after our update missed it.
            with self._sessions.begin() as session:
                count = session.scalar(
                    update(RateLimitBucket)
                    .where(
                        RateLimitBucket.network_hash == network_hash,
                        RateLimitBucket.action == action,
                        RateLimitBucket.window_start == window,
                    )
                    .values(request_count=RateLimitBucket.request_count + 1)
                    .returning(RateLimitBucket.request_count)
                )
        if count is None:
            raise RuntimeError("rate-limit bucket could not be updated")
        if count > limit:
            raise RateLimitExceeded
        self._remove_expired(now)

    def _remove_expired(self, now: datetime) -> None:
        # Cleanup is deliberately infrequent and bounded by the current network request path.
        if int(now.timestamp()) % 60 != 0:
            return
        with self._sessions.begin() as session:
            expired = session.scalars(
                select(RateLimitBucket).where(RateLimitBucket.expires_at < now).limit(500)
            )
            for bucket in expired:
                session.delete(bucket)
