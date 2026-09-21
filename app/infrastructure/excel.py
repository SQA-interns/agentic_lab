from __future__ import annotations

import re
from datetime import datetime
from io import BytesIO

from openpyxl import Workbook  # type: ignore[import-untyped]
from sqlalchemy import select
from sqlalchemy.orm import Session, selectinload

from app.infrastructure.models import Registration

HEADERS = [
    "Registration ID",
    "Registered at",
    "Variant",
    "First name",
    "Last name",
    "Email",
    "Organization / institution",
    "Study institution",
    "Study programme",
    "Student ID",
    "Workshops",
    "Events",
    "Meals",
    "Other activities",
    "Consent evidence",
]
ILLEGAL_SPREADSHEET_CHARACTERS = re.compile(r"[\x00-\x08\x0b-\x0c\x0e-\x1f]")


def safe_cell(value: str | None) -> str:
    if value is None:
        return ""
    cleaned = ILLEGAL_SPREADSHEET_CHARACTERS.sub("", value)
    if cleaned.lstrip().startswith(("=", "+", "-", "@")):
        return "'" + cleaned
    return cleaned


def create_registration_workbook(session: Session) -> bytes:
    registrations = session.scalars(
        select(Registration)
        .options(selectinload(Registration.options), selectinload(Registration.consents))
        .order_by(Registration.created_at, Registration.id)
    ).all()
    workbook = Workbook(write_only=True)
    sheet = workbook.create_sheet("Registrations")
    sheet.append(HEADERS)
    for registration in registrations:
        by_category: dict[str, list[str]] = {
            "workshop": [],
            "event": [],
            "meal": [],
            "other": [],
        }
        for option in sorted(registration.options, key=lambda item: item.option_id):
            by_category[option.category].append(
                safe_cell(f"{option.display_name} [{option.option_id}]")
            )
        consents = "; ".join(
            safe_cell(
                f"{consent.consent_id} [{consent.policy_version}] {_timestamp(consent.accepted_at)}"
            )
            for consent in sorted(registration.consents, key=lambda item: item.consent_id)
        )
        sheet.append(
            [
                registration.id,
                _timestamp(registration.created_at),
                registration.variant,
                safe_cell(registration.first_name),
                safe_cell(registration.last_name),
                safe_cell(registration.email),
                safe_cell(registration.organization),
                safe_cell(registration.study_institution),
                safe_cell(registration.study_programme),
                safe_cell(registration.student_id),
                "; ".join(by_category["workshop"]),
                "; ".join(by_category["event"]),
                "; ".join(by_category["meal"]),
                "; ".join(by_category["other"]),
                consents,
            ]
        )
    output = BytesIO()
    workbook.save(output)
    return output.getvalue()


def _timestamp(value: datetime) -> str:
    return value.isoformat()
