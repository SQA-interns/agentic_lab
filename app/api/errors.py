from __future__ import annotations

import logging
from typing import Any

from fastapi import Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

logger = logging.getLogger(__name__)


def error_response(
    request: Request,
    status_code: int,
    code: str,
    message: str,
    fields: dict[str, str] | None = None,
    headers: dict[str, str] | None = None,
) -> JSONResponse:
    payload: dict[str, Any] = {
        "error": {
            "code": code,
            "message": message,
            "requestId": getattr(request.state, "request_id", "unavailable"),
        }
    }
    if fields:
        payload["error"]["fields"] = fields
    return JSONResponse(payload, status_code=status_code, headers=headers)


async def request_validation_handler(
    request: Request, exception: RequestValidationError
) -> JSONResponse:
    fields: dict[str, str] = {}
    for error in exception.errors():
        location = [str(item) for item in error["loc"] if item not in {"body", "query"}]
        field = ".".join(location) or "request"
        fields.setdefault(field, _friendly_validation_message(error["type"], field))
    invalid_json = any(error["type"] == "json_invalid" for error in exception.errors())
    logger.warning(
        "request validation rejected",
        extra={
            "event": "request_validation",
            "outcome": "invalid_json" if invalid_json else "invalid_fields",
            "request_id": getattr(request.state, "request_id", "unavailable"),
        },
    )
    return error_response(
        request,
        400 if invalid_json else 422,
        "invalid_json" if invalid_json else "validation_failed",
        "The request body is not valid JSON."
        if invalid_json
        else "Please correct the highlighted fields.",
        None if invalid_json else fields,
    )


def _friendly_validation_message(error_type: str, field: str) -> str:
    if "missing" in error_type:
        return "This field is required."
    if field.endswith("email") or "email" in error_type:
        return "Enter a valid email address."
    if "too_long" in error_type or "string_too_long" in error_type:
        return "This value is too long."
    if "too_short" in error_type or "string_too_short" in error_type:
        return "This value is required."
    if "extra_forbidden" in error_type:
        return "Unexpected fields are not allowed."
    return "Enter a valid value."
