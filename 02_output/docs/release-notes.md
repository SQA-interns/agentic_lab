# Release notes

> Written in: phase 7 · Agent: writes

Release 0.1.0 of the conference registration (backend and frontend). Verified in `docs/06_verification-report.md`: 240 tests pass, no open Critical or High finding.

## Delivered

| Story | Delivered |
|---|---|
| US-001, US-002 | External and student forms with exactly the BR-01 fields; server-side validation (trimming, Unicode, email format, lengths, control characters); one registration per email (D-10) |
| US-003 | Options and consent wording from a JSON file checked at start (`conference-options.schema.json`); only active options offered; stable ids |
| US-004 | Confirmation in the page only after the backend accepted; reasons shown next to the fields |
| US-005 | Row in PostgreSQL and raw JSON copy on a volume, both written before the answer; neither kept if one fails |
| US-006, US-007 | Plain-text confirmation to the participant; one notification per organizer address with the JSON copy attached; an email failure never affects storage (D-08) |
| US-008 | Excel export for organizers with a 15-minute bearer token (HTTPS or localhost only) |
| Security | reCAPTCHA v2 checked on the backend, rate and size limits, security headers, non-root images, startup guards for production |

## Known limitations

- No retry for failed emails; a failure is logged with the registration id only (D-08).
- No deletion of registrations or JSON copies by the application; retention is a manual procedure (D-11).
- A second registration with the same email is refused; changes go through the organizers (D-10).
- Option changes need a restart; the UI is in English (D-12).
- Rate limits are kept in memory per backend instance; with several instances each counts separately.
- Container base images are not scanned for vulnerabilities (F-03); dev-only tools carry 5 moderate advisories (F-02).

## Must be tested manually by a human

1. reCAPTCHA v2 with the production keys: one real registration passes, a missing or expired token is refused.
2. SMTP: confirmation and organizer notification (with the JSON attachment) arrive in real mailboxes; STARTTLS is used.
3. Reverse proxy: HTTPS with Let's Encrypt, HTTP redirects to HTTPS, `/` goes to the frontend and `/api` to the backend, `X-Forwarded-For` and `X-Forwarded-Proto` are set; organizer token over HTTPS works, over HTTP is refused (403).
4. Scan the shipped images (`registration-backend`, `registration-frontend`) with an image scanner before production (F-03).
5. Production start refuses unsafe settings: test mode on, empty reCAPTCHA keys, `ORGANIZER_HTTPS_ONLY=false`, no options file.
6. Set the retention period and the manual deletion procedure for both the database rows and the JSON copies (D-11, SB-13).
7. Open an exported workbook in Excel and LibreOffice; check Slovenian characters.
8. Backups of the volumes `pgdata` and `jsoncopies`.

## Decisions pending review

Blocking decision D-15 was answered by the human (option 1). Every other decision is pending review (`docs/decisions-log.md`):

| Decision | Subject |
|---|---|
| D-01, D-02, D-05 | Host JDK 21.0.11 and Node 24.10.0 / npm 10.9.4 instead of the pins; cloc image reports 1.98 |
| D-03, D-04 | Suppressed Dependency-Check false positives: CVE-2025-7962 (angus-activation; angus-mail 2.0.5 is fixed), CVE-2025-15104 (Nu Html Checker, not hibernate-validator) |
| D-06 … D-11 | Open questions OQ-01 … OQ-06: all options for both types, one mandatory consent from configuration, storage before email, any number of distinct options, one registration per email, retention by hand |
| D-12 | English UI text |
| D-13 | Hibernate annotation for the `smallint` order column; domain may use `org.hibernate.annotations` |
| D-14, D-16 | Suppressed SpotBugs false positives: contract SQL executed by the migration; injected collaborators stored in constructors |
| D-17 | Email rule rejects empty domain labels (specification, backend, frontend) |

Accepted findings to review: F-01 (no npm minimum release age), F-02 (dev-only advisories), F-03 (no image scan), F-04 (startup guards run after the options file and database), F-05 (8 oversized commits).
