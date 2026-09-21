from __future__ import annotations

import base64
import hashlib
import hmac
import json
import secrets
import time
from dataclasses import dataclass
from typing import Any


class ChallengeError(ValueError):
    """A challenge is malformed, invalid, expired, or bound to another variant."""


def _encode(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).rstrip(b"=").decode("ascii")


def _decode(value: str) -> bytes:
    padding = "=" * (-len(value) % 4)
    try:
        return base64.b64decode(value + padding, altchars=b"-_", validate=True)
    except (ValueError, TypeError) as exc:
        raise ChallengeError("invalid challenge encoding") from exc


@dataclass(frozen=True, slots=True)
class Challenge:
    nonce: str
    variant: str
    issued_at: int
    expires_at: int


class ChallengeSigner:
    def __init__(self, secret: str, lifetime_seconds: int = 1800) -> None:
        self._secret = secret.encode()
        self._lifetime_seconds = lifetime_seconds

    def issue(self, variant: str, now: int | None = None) -> str:
        issued_at = int(time.time() if now is None else now)
        payload = {
            "nonce": secrets.token_urlsafe(24),
            "variant": variant,
            "iat": issued_at,
            "exp": issued_at + self._lifetime_seconds,
        }
        encoded = _encode(json.dumps(payload, separators=(",", ":")).encode())
        signature = _encode(hmac.digest(self._secret, encoded.encode(), "sha256"))
        return f"{encoded}.{signature}"

    def verify(self, token: str, variant: str, now: int | None = None) -> Challenge:
        try:
            encoded, supplied_signature = token.split(".", 1)
        except ValueError as exc:
            raise ChallengeError("invalid challenge") from exc
        expected = _encode(hmac.digest(self._secret, encoded.encode(), "sha256"))
        if not hmac.compare_digest(supplied_signature, expected):
            raise ChallengeError("invalid challenge signature")
        try:
            payload: dict[str, Any] = json.loads(_decode(encoded))
            challenge = Challenge(
                nonce=str(payload["nonce"]),
                variant=str(payload["variant"]),
                issued_at=int(payload["iat"]),
                expires_at=int(payload["exp"]),
            )
        except (KeyError, TypeError, ValueError, json.JSONDecodeError) as exc:
            raise ChallengeError("invalid challenge payload") from exc
        current = int(time.time() if now is None else now)
        if challenge.variant != variant or challenge.issued_at > current + 60:
            raise ChallengeError("challenge does not match submission")
        if challenge.expires_at < current or challenge.expires_at <= challenge.issued_at:
            raise ChallengeError("challenge expired")
        return challenge


def network_digest(secret: str, network_identifier: str) -> str:
    return hmac.new(secret.encode(), network_identifier.encode(), hashlib.sha256).hexdigest()[:32]


def bearer_matches(header: str | None, expected: str) -> bool:
    if not header or not header.startswith("Bearer "):
        return False
    supplied = header.removeprefix("Bearer ")
    return hmac.compare_digest(supplied.encode(), expected.encode())
