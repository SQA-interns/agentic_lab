# Release notes

> Written in: phase 7 · Agent: writes

Version 0.1.0 · branch `tjan/single-agent/sdd/v005` · verification: [`06_verification-report.md`](06_verification-report.md)

## Delivered

| Story | Delivered |
|---|---|
| US-001 | External participant form with browser and server validation (Unicode, NBSP trimming), options, consent, reCAPTCHA |
| US-002 | Student form; options can be limited to students or external participants (D-11) |
| US-003 | Options, per-category limits and consents from a JSON file (`CONFERENCE_OPTIONS_FILE`), applied on restart |
| US-004 | Confirmation shown only after the backend accepted the registration; field errors next to fields |
| US-005 | PostgreSQL row plus raw JSON copy written in one transaction; retention deletes both after `RETENTION_DAYS` (D-16) |
| US-006 | Plain-text confirmation email to the participant after storage |
| US-007 | Organizer email to every `ORGANIZER_EMAILS` address with the stored JSON copy attached |
| US-008 | Excel export for the organizer (HTTP Basic over HTTPS, D-26) |

Tests: backend 231, frontend 56, end-to-end 6, all passing; mutation backend 76%, frontend 74.46%.

## Known limitations

- One registration per email address (D-15); a participant cannot edit or cancel a registration (out of scope).
- Email failures are logged and not retried (D-13); the registration stays valid.
- Rate limits are per backend instance and kept in memory; run one backend instance.
- Option changes need a backend restart. Only one consent wording is configured locally (D-12); the real wording must come from the organizer.
- Dev tooling has known advisories that do not ship: jscpd (High, D-10), qs (Medium, F-03).
- The organizer export uses HTTP Basic; the downgrade of the semgrep High was approved (D-26).

## Must be tested manually by a human

- Google reCAPTCHA v2: one real submission with production keys, and one with a failed challenge (`environments.md`).
- SMTP: delivery of both emails to a real mailbox through the production SMTP server, including Slovenian characters and the JSON attachment.
- TLS and reverse proxy: HTTPS with Let's Encrypt, HTTP→HTTPS redirect, `/api` routed to the backend, `X-Forwarded-Proto` set; export refused over plain HTTP from a remote address.
- Production start-up: refuses test mode, empty keys or `ORGANIZER_HTTPS_ONLY=false`.
- The real conference options file and consent wording (D-12, D-14 limits).
- The Excel export opened in the organizers' spreadsheet program.

## Decisions pending review

Non-blocking decisions (details in [`decisions-log.md`](decisions-log.md)):

| D | Topic |
|---|---|
| D-01, D-02, D-07 | Host JDK 21.0.12.1, Compose v5.1.1 and Docker Engine 29.3.1 differ from the pins |
| D-03 | Dependency-Check OSS Index, Node and RetireJS analysers off |
| D-11..D-16 | Answers to OQ-01..OQ-06: student options, consent, email failure, per-category limits, duplicate email, retention |
| D-17 | Contract validation with host Python, PyYAML, jsonschema and the vendored OpenAPI 3.1 meta-schema |
| D-18, D-19, D-20 | Compose stack in phase 3; `@types/node` 24.13.0; `scripts/compose.sh` filters `.env` |
| D-22 | Alphabetical test order for the frozen harness |
| D-24 | DoD-08/09 evidence added in phase 7 |

Suppressed findings:

| D | Tool | Suppressed |
|---|---|---|
| D-08, D-09 | Dependency-Check | CVE-2025-7962 on angus-activation, CVE-2025-15104 on hibernate-validator (identifier mismatch) |
| D-21, D-23 | SpotBugs | EI_EXPOSE_REP2 on injected beans in `NotificationService`, `ExportService` |
| D-25 | gitleaks | test constant and ephemeral generated passwords in phase 3 logs |
| D-26 | semgrep | `use-of-basic-authentication` (approved downgrade) |

Commit-size guide exceeded by 7 commits (F-07); history was not rewritten.
