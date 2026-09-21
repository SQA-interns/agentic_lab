from __future__ import annotations

import hashlib
import json
import os
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any
from uuid import uuid4


@dataclass(frozen=True, slots=True)
class StoredJson:
    relative_path: str
    sha256: str
    size_bytes: int


class JsonBackupStore:
    def __init__(self, directory: Path) -> None:
        self.directory = directory.resolve()

    def ensure_ready(self) -> None:
        self.directory.mkdir(mode=0o700, parents=True, exist_ok=True)
        probe = self.directory / ".write-probe"
        try:
            with probe.open("wb") as handle:
                handle.write(b"ready")
                handle.flush()
                os.fsync(handle.fileno())
        finally:
            probe.unlink(missing_ok=True)

    def write(self, registration_id: str, snapshot: dict[str, Any]) -> StoredJson:
        if not _is_uuid_text(registration_id):
            raise ValueError("registration identifier is not a UUID")
        self.directory.mkdir(mode=0o700, parents=True, exist_ok=True)
        payload = json.dumps(
            snapshot, ensure_ascii=False, sort_keys=True, separators=(",", ":")
        ).encode("utf-8")
        target = self.directory / f"{registration_id}.json"
        descriptor, temporary_name = tempfile.mkstemp(
            prefix=f".{registration_id}.", suffix=".tmp", dir=self.directory
        )
        temporary = Path(temporary_name)
        try:
            os.fchmod(descriptor, 0o600)
            with os.fdopen(descriptor, "wb") as handle:
                handle.write(payload)
                handle.flush()
                os.fsync(handle.fileno())
            os.replace(temporary, target)
            directory_fd = os.open(self.directory, os.O_RDONLY)
            try:
                os.fsync(directory_fd)
            finally:
                os.close(directory_fd)
        except BaseException:
            temporary.unlink(missing_ok=True)
            raise
        return StoredJson(
            relative_path=target.name,
            sha256=hashlib.sha256(payload).hexdigest(),
            size_bytes=len(payload),
        )

    def read_verified(self, stored: StoredJson) -> bytes:
        path = self._resolve_relative(stored.relative_path)
        payload = path.read_bytes()
        if len(payload) != stored.size_bytes or not _constant_digest(payload, stored.sha256):
            raise OSError("registration backup integrity check failed")
        return payload

    def delete(self, relative_path: str) -> None:
        self._resolve_relative(relative_path).unlink(missing_ok=True)

    def reconcile(self, referenced_paths: set[str]) -> dict[str, int]:
        """Quarantine incomplete and unreferenced registration backup files."""
        self.directory.mkdir(mode=0o700, parents=True, exist_ok=True)
        quarantine = self.directory / "quarantine"
        temporary_files = list(self.directory.glob(".*.tmp"))
        orphan_files = [
            path for path in self.directory.glob("*.json") if path.name not in referenced_paths
        ]
        candidates = temporary_files + orphan_files
        if candidates:
            quarantine.mkdir(mode=0o700, exist_ok=True)
        for path in candidates:
            destination = quarantine / f"{path.name}.{uuid4().hex}.orphan"
            os.replace(path, destination)
        return {"temporary": len(temporary_files), "unreferenced": len(orphan_files)}

    def _resolve_relative(self, relative_path: str) -> Path:
        if Path(relative_path).name != relative_path:
            raise ValueError("invalid backup path")
        resolved = (self.directory / relative_path).resolve()
        if resolved.parent != self.directory:
            raise ValueError("backup path escapes configured directory")
        return resolved


def _constant_digest(payload: bytes, expected: str) -> bool:
    import hmac

    return hmac.compare_digest(hashlib.sha256(payload).hexdigest(), expected)


def _is_uuid_text(value: str) -> bool:
    from uuid import UUID

    try:
        return str(UUID(value)) == value
    except ValueError:
        return False
