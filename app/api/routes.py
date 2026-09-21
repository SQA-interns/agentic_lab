from __future__ import annotations

import logging
from pathlib import Path
from typing import Annotated

from fastapi import APIRouter, Header, Request
from fastapi import Response as FastAPIResponse
from fastapi.responses import FileResponse, JSONResponse, Response
from sqlalchemy import text
from sqlalchemy.exc import SQLAlchemyError

from app.api.errors import error_response
from app.application import (
    AutomationRejectedError,
    DomainValidationError,
    RegistrationService,
    SubmissionConflictError,
)
from app.core.config import Settings
from app.core.security import ChallengeSigner, bearer_matches, network_digest
from app.domain.configuration import ConfigurationRepository
from app.domain.schemas import FIXED_FIELDS, FormContextResponse, RegistrationRequest, Variant
from app.infrastructure.database import Database
from app.infrastructure.excel import create_registration_workbook
from app.infrastructure.json_store import JsonBackupStore
from app.infrastructure.rate_limit import DatabaseRateLimiter, RateLimitExceeded

logger = logging.getLogger(__name__)
router = APIRouter()


def _state(request: Request, name: str) -> object:
    return getattr(request.app.state, name)


def _settings(request: Request) -> Settings:
    return _state(request, "settings")  # type: ignore[return-value]


def _network_key(request: Request) -> str:
    settings = _settings(request)
    host = request.client.host if request.client else "unknown"
    return network_digest(settings.network_hash_secret, host)


def _check_rate(request: Request, action: str, limit: int) -> JSONResponse | None:
    limiter: DatabaseRateLimiter = _state(request, "rate_limiter")  # type: ignore[assignment]
    try:
        limiter.check(_network_key(request), action, limit)
    except RateLimitExceeded:
        return error_response(
            request,
            429,
            "rate_limited",
            "Too many requests. Please try again later.",
            headers={"Retry-After": "60"},
        )
    except SQLAlchemyError as exc:
        logger.error("rate limiter unavailable", exc_info=exc, extra={"event": "rate_limit"})
        return error_response(
            request, 503, "service_unavailable", "The service is temporarily unavailable."
        )
    return None


@router.get("/api/v1/forms/{variant}", response_model=FormContextResponse)
def form_context(
    request: Request, variant: str, response: FastAPIResponse
) -> FormContextResponse | JSONResponse:
    try:
        selected_variant = Variant(variant)
    except ValueError:
        return error_response(request, 404, "not_found", "Registration form not found.")
    limited = _check_rate(request, "form_context", 60)
    if limited:
        return limited
    repository: ConfigurationRepository = _state(request, "configurations")  # type: ignore[assignment]
    signer: ChallengeSigner = _state(request, "challenges")  # type: ignore[assignment]
    try:
        configuration = repository.get()
    except (OSError, ValueError) as exc:
        logger.error("configuration unavailable", exc_info=exc, extra={"event": "config_load"})
        return error_response(
            request, 503, "service_unavailable", "Registration is temporarily unavailable."
        )
    options = [
        {
            "id": option.id,
            "displayName": option.display_name,
            "category": option.category.value,
        }
        for option in configuration.options
        if option.active and selected_variant in option.variants
    ]
    consents = [
        {
            "id": consent.id,
            "label": consent.label,
            "policyVersion": consent.policy_version,
            "required": consent.required,
        }
        for consent in configuration.consents
        if selected_variant in consent.variants
    ]
    response.headers["Cache-Control"] = "no-store"
    logger.info(
        "form context returned",
        extra={
            "event": "form_context",
            "outcome": "success",
            "request_id": request.state.request_id,
        },
    )
    return FormContextResponse(
        conference_name=configuration.conference_name,
        configuration_version=configuration.configuration_version,
        variant=selected_variant,
        fields=FIXED_FIELDS[selected_variant],
        options=options,
        consents=consents,
        challenge_token=signer.issue(selected_variant.value),
    )


@router.post("/api/v1/registrations", status_code=201)
def register(request: Request, payload: RegistrationRequest) -> JSONResponse:
    settings = _settings(request)
    origin = request.headers.get("origin")
    if (settings.production and origin != settings.public_origin) or (
        origin is not None and origin != settings.public_origin
    ):
        return error_response(request, 403, "request_rejected", "The request was rejected.")
    service: RegistrationService = _state(request, "registrations")  # type: ignore[assignment]
    try:
        result = service.register(payload)
    except DomainValidationError as exc:
        logger.info(
            "registration validation rejected",
            extra={
                "event": "registration",
                "outcome": "validation_rejected",
                "request_id": request.state.request_id,
            },
        )
        return error_response(
            request,
            422,
            "validation_failed",
            "Please correct the highlighted fields.",
            exc.fields,
        )
    except AutomationRejectedError:
        logger.warning(
            "registration automation check rejected",
            extra={
                "event": "registration",
                "outcome": "automation_rejected",
                "request_id": request.state.request_id,
            },
        )
        return error_response(request, 403, "request_rejected", "The request was rejected.")
    except SubmissionConflictError:
        logger.info(
            "registration submission conflict",
            extra={
                "event": "registration",
                "outcome": "idempotency_conflict",
                "request_id": request.state.request_id,
            },
        )
        return error_response(
            request,
            409,
            "submission_conflict",
            "This submission identifier was already used for different information.",
        )
    except (OSError, SQLAlchemyError, ValueError) as exc:
        logger.error(
            "registration persistence failed",
            exc_info=exc,
            extra={"event": "registration", "outcome": "storage_failure"},
        )
        return error_response(
            request,
            503,
            "service_unavailable",
            "Registration could not be confirmed. Please try again later.",
        )
    logger.info(
        "registration accepted",
        extra={
            "event": "registration",
            "outcome": "existing" if result.existing else "created",
            "registration_id": result.registration_id,
            "request_id": request.state.request_id,
        },
    )
    return JSONResponse(
        {
            "registrationId": str(result.registration_id),
            "status": "registered",
            "message": "Your registration was received.",
        },
        status_code=201,
    )


@router.get("/api/v1/organizer/registrations.xlsx")
def export_registrations(
    request: Request,
    authorization: Annotated[str | None, Header()] = None,
) -> Response:
    settings = _settings(request)
    limited = _check_rate(request, "export", settings.export_rate_limit)
    if limited:
        return limited
    if not bearer_matches(authorization, settings.export_token):
        logger.warning(
            "export authorization rejected",
            extra={
                "event": "export",
                "outcome": "unauthorized",
                "request_id": request.state.request_id,
            },
        )
        return error_response(
            request,
            401,
            "unauthorized",
            "Organizer authorization is required.",
            headers={"WWW-Authenticate": "Bearer"},
        )
    database: Database = _state(request, "database")  # type: ignore[assignment]
    try:
        with database.session_factory.begin() as session:
            workbook = create_registration_workbook(session)
    except SQLAlchemyError as exc:
        logger.error("export failed", exc_info=exc, extra={"event": "export"})
        return error_response(request, 503, "service_unavailable", "Export is unavailable.")
    logger.info(
        "export created",
        extra={
            "event": "export",
            "outcome": "success",
            "request_id": request.state.request_id,
        },
    )
    return Response(
        workbook,
        media_type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        headers={
            "Content-Disposition": 'attachment; filename="conference-registrations.xlsx"',
            "Cache-Control": "no-store, private",
        },
    )


@router.get("/health/live")
def live() -> dict[str, str]:
    return {"status": "live"}


@router.get("/health/ready")
def ready(request: Request) -> Response:
    database: Database = _state(request, "database")  # type: ignore[assignment]
    configurations: ConfigurationRepository = _state(request, "configurations")  # type: ignore[assignment]
    backups: JsonBackupStore = _state(request, "backups")  # type: ignore[assignment]
    try:
        configurations.get()
        backups.ensure_ready()
        with database.session_factory() as session:
            session.execute(text("SELECT 1"))
    except Exception as exc:
        logger.warning("readiness failed", exc_info=exc, extra={"event": "readiness"})
        return Response('{"status":"not_ready"}', status_code=503, media_type="application/json")
    return JSONResponse({"status": "ready"})


@router.get("/")
@router.get("/external")
@router.get("/student")
def frontend(request: Request) -> FileResponse:
    static_directory: Path = _state(request, "static_directory")  # type: ignore[assignment]
    return FileResponse(static_directory / "index.html")
