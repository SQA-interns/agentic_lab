from __future__ import annotations

import json
import logging
import re
from datetime import UTC, datetime
from typing import Any

_CONTROL = re.compile(r"[\x00-\x1f\x7f]")


class JsonFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        payload: dict[str, Any] = {
            "timestamp": datetime.now(UTC).isoformat(),
            "level": record.levelname,
            "logger": record.name,
            "message": _CONTROL.sub("?", record.getMessage()),
        }
        for key in ("request_id", "registration_id", "event", "outcome"):
            value = getattr(record, key, None)
            if value is not None:
                payload[key] = _CONTROL.sub("?", str(value))
        if record.exc_info and record.exc_info[0] is not None:
            payload["exception_type"] = record.exc_info[0].__name__
        return json.dumps(payload, ensure_ascii=False)


def configure_logging(level: str) -> None:
    handler = logging.StreamHandler()
    handler.setFormatter(JsonFormatter())
    root = logging.getLogger()
    root.handlers = [handler]
    root.setLevel(level)
