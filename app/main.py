from __future__ import annotations

import logging
import re
import uuid
from collections.abc import AsyncIterator, Awaitable, Callable
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Any

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.staticfiles import StaticFiles
from sqlalchemy import select
from sqlalchemy.exc import SQLAlchemyError
from starlette.middleware.trustedhost import TrustedHostMiddleware
from starlette.types import ASGIApp, Message, Receive, Scope, Send

from app.api.errors import error_response, request_validation_handler
from app.api.routes import router
from app.application import RegistrationService
from app.core.config import Settings
from app.core.logging import configure_logging
from app.core.security import ChallengeSigner, network_digest
from app.domain.configuration import ConfigurationRepository
from app.infrastructure import models  # noqa: F401
from app.infrastructure.database import Database
from app.infrastructure.json_store import JsonBackupStore
from app.infrastructure.models import Registration
from app.infrastructure.rate_limit import DatabaseRateLimiter, RateLimitExceeded

logger = logging.getLogger(__name__)
SAFE_REQUEST_ID = re.compile(r"^[A-Za-z0-9._:-]{1,100}$")


class RequestLimitMiddleware:
    def __init__(
        self,
        app: ASGIApp,
        rate_limiter: DatabaseRateLimiter,
        network_hash_secret: str,
        registration_rate_limit: int,
        max_bytes: int = 32 * 1024,
    ) -> None:
        self.app = app
        self.rate_limiter = rate_limiter
        self.network_hash_secret = network_hash_secret
        self.registration_rate_limit = registration_rate_limit
        self.max_bytes = max_bytes

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http" or scope.get("path") != "/api/v1/registrations":
            await self.app(scope, receive, send)
            return
        client = scope.get("client")
        host = client[0] if client else "unknown"
        try:
            self.rate_limiter.check(
                network_digest(self.network_hash_secret, host),
                "registration",
                self.registration_rate_limit,
            )
        except RateLimitExceeded:
            logger.warning(
                "registration rate limit exceeded",
                extra={"event": "registration", "outcome": "rate_limited"},
            )
            await _plain_error(
                send,
                429,
                "rate_limited",
                "Too many requests. Please try again later.",
                headers=[(b"retry-after", b"60")],
            )
            return
        except SQLAlchemyError:
            await _plain_error(
                send, 503, "service_unavailable", "The service is temporarily unavailable."
            )
            return
        headers = dict(scope.get("headers", []))
        content_type = headers.get(b"content-type", b"").split(b";", 1)[0].lower()
        if content_type != b"application/json":
            logger.info(
                "registration media type rejected",
                extra={"event": "registration", "outcome": "unsupported_media_type"},
            )
            await _plain_error(send, 415, "unsupported_media_type", "Use application/json.")
            return
        try:
            declared = int(headers.get(b"content-length", b"0"))
        except ValueError:
            declared = self.max_bytes + 1
        if declared > self.max_bytes:
            logger.info(
                "registration payload rejected",
                extra={"event": "registration", "outcome": "payload_too_large"},
            )
            await _plain_error(send, 413, "payload_too_large", "The request is too large.")
            return
        body = bytearray()
        more_body = True
        while more_body:
            message = await receive()
            if message["type"] == "http.request":
                body.extend(message.get("body", b""))
                more_body = message.get("more_body", False)
                if len(body) > self.max_bytes:
                    logger.info(
                        "registration payload rejected",
                        extra={"event": "registration", "outcome": "payload_too_large"},
                    )
                    await _plain_error(send, 413, "payload_too_large", "The request is too large.")
                    return
            else:
                more_body = False
        replayed = False

        async def replay_receive() -> Message:
            nonlocal replayed
            if not replayed:
                replayed = True
                return {"type": "http.request", "body": bytes(body), "more_body": False}
            return {"type": "http.disconnect"}

        await self.app(scope, replay_receive, send)


async def _plain_error(
    send: Send,
    status: int,
    code: str,
    message: str,
    headers: list[tuple[bytes, bytes]] | None = None,
) -> None:
    payload = (
        f'{{"error":{{"code":"{code}","message":"{message}","requestId":"unavailable"}}}}}}'
    ).encode()
    await send(
        {
            "type": "http.response.start",
            "status": status,
            "headers": [
                (b"content-type", b"application/json"),
                (b"content-length", str(len(payload)).encode()),
                *(headers or []),
            ],
        }
    )
    await send({"type": "http.response.body", "body": payload})


def create_app(settings: Settings | None = None) -> FastAPI:
    resolved = settings or Settings.from_env()
    configure_logging(resolved.log_level)
    database = Database(resolved.database_url)
    backups = JsonBackupStore(resolved.backup_directory)
    configurations = ConfigurationRepository(resolved.conference_config_path)
    challenges = ChallengeSigner(resolved.challenge_secret)

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        backups.ensure_ready()
        configurations.get()
        if resolved.auto_create_schema:
            database.create_schema()
        with database.session_factory() as session:
            referenced = set(session.scalars(select(Registration.backup_relative_path)).all())
        reconciliation = backups.reconcile(referenced)
        if reconciliation["temporary"] or reconciliation["unreferenced"]:
            logger.warning(
                "orphan registration backups quarantined",
                extra={"event": "backup_reconciliation", "outcome": "quarantined"},
            )
        yield

    app = FastAPI(
        title="Conference Registration API",
        version="1.0.0",
        docs_url=None if resolved.production else "/docs",
        redoc_url=None,
        lifespan=lifespan,
    )
    app.state.settings = resolved
    app.state.database = database
    app.state.backups = backups
    app.state.configurations = configurations
    app.state.challenges = challenges
    rate_limiter = DatabaseRateLimiter(database.session_factory)
    app.state.rate_limiter = rate_limiter
    app.state.registrations = RegistrationService(
        database.session_factory,
        configurations,
        challenges,
        backups,
        resolved.organizer_emails,
    )
    app.state.static_directory = Path(__file__).parent / "static"
    app.add_middleware(
        RequestLimitMiddleware,
        rate_limiter=rate_limiter,
        network_hash_secret=resolved.network_hash_secret,
        registration_rate_limit=resolved.registration_rate_limit,
    )
    app.add_middleware(TrustedHostMiddleware, allowed_hosts=list(resolved.trusted_hosts))

    @app.middleware("http")
    async def request_metadata(
        request: Request, call_next: Callable[[Request], Awaitable[Any]]
    ) -> Any:
        supplied = request.headers.get("x-request-id", "")
        request.state.request_id = (
            supplied if SAFE_REQUEST_ID.fullmatch(supplied) else str(uuid.uuid4())
        )
        try:
            response = await call_next(request)
        except Exception as exc:
            logger.error(
                "unexpected request failure",
                exc_info=exc,
                extra={"request_id": request.state.request_id, "event": "request"},
            )
            response = error_response(
                request, 500, "internal_error", "An unexpected error occurred."
            )
        response.headers["X-Request-ID"] = request.state.request_id
        response.headers["X-Content-Type-Options"] = "nosniff"
        response.headers["Referrer-Policy"] = "no-referrer"
        response.headers["Permissions-Policy"] = "camera=(), microphone=(), geolocation=()"
        response.headers["Content-Security-Policy"] = (
            "default-src 'self'; script-src 'self'; style-src 'self'; "
            "img-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'"
        )
        if resolved.enforce_https_headers:
            response.headers["Strict-Transport-Security"] = "max-age=31536000; includeSubDomains"
        return response

    app.add_exception_handler(RequestValidationError, request_validation_handler)  # type: ignore[arg-type]
    app.include_router(router)
    app.mount("/static", StaticFiles(directory=app.state.static_directory), name="static")
    return app


app = create_app()
