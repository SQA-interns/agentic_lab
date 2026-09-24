# Release Notes — Conference Registration System 1.0.0

Date: 2026-09-24. Derived from the implemented code in this directory (not from the user stories
alone). Specification: `docs/specification.md`; verification: `docs/verification-report.md`.

## What is included

### Registration (US-001, US-002, US-004)
* Two forms in a React 19 + TypeScript single-page app: `/register/external` (first name, last
  name, email, organization/institution) and `/register/student` (first name, last name, email,
  study institution, study programme, student ID); start page `/` offers both.
* Mandatory privacy consent checkbox, never preselected.
* Client-side validation on blur and submit (same rules as the backend), field-level messages,
  focus on the first invalid field, submit button disabled while a request is in flight.
* Confirmation panel with registration ID shown **only** after the backend answers `201`;
  validation errors from the backend are mapped to the fields; server/network errors show a
  banner and keep the entered data.

### REST API (Spring Boot 3.5.16, Java 21)
| Endpoint | Purpose |
|---|---|
| `GET /api/options?type=EXTERNAL\|STUDENT` | active options for a variant |
| `GET /api/form-token` | signed anti-automation token |
| `POST /api/registrations/external`, `POST /api/registrations/student` | submit (201 / 400 / 413 / 415 / 429) |
| `GET /api/admin/registrations/export` | Excel export (HTTP Basic, organizer account) |
| `GET /actuator/health`, `/health/liveness`, `/health/readiness` | health probes |

Backend validation: trimming + Unicode NFC normalisation of all strings, required fields,
lengths, name/email/student-ID patterns, no control characters, consent must be `true`, option
IDs must exist, be active and be offered to the variant, unknown JSON properties rejected (the
two variants are not interchangeable).

### Configurable options (US-003)
* Workshops, events, meals and other activities in `config/conference-options.json`
  (id, category, name, active, optional audiences `EXTERNAL`/`STUDENT`), bind-mounted into the
  backend. Change the file and restart the backend — no code change. Invalid files (duplicate
  or malformed ids, missing name/category/active) stop the application at startup.

### Storage and backup (US-005)
* PostgreSQL 16 with Flyway migration `V1__init.sql` (check constraints enforce per-variant
  fields and consent); selected options stored with a display-name snapshot.
* One pretty-printed JSON backup per registration in `/data/backups` (named volume), written
  atomically with `0600` permissions, never overwritten; a failed backup rolls back the database
  transaction and the client receives an error instead of a confirmation.

### Email (US-006, US-007)
* After commit, asynchronously: plain-text UTF-8 confirmation to the participant and a
  notification to all configured organizers with `registration-<id>.json` attached (identical to
  the backup). Mail failures are logged and do not affect the stored registration.

### Excel export (US-008)
* `.xlsx` generated on request from current data: one row per registration, fixed fields,
  consent, options per category. Formula-like values are written as quote-prefixed text.

### Security and anti-automation
* Honeypot field; HMAC-signed single-use form token with minimum fill time (3 s) and maximum age
  (2 h); per-IP rate limits (registrations 10, tokens 60, admin 20 per 10 min) with atomic
  counters; client IP taken from Nginx-overwritten `X-Forwarded-For` only.
* Backend headers: CSP `default-src 'none'; frame-ancestors 'none'`, `nosniff`, `X-Frame-Options:
  DENY`, `Referrer-Policy: no-referrer`, `Cache-Control: no-store`; frontend Nginx headers: strict
  CSP, `nosniff`, `DENY`, `Referrer-Policy`, `Permissions-Policy`, COOP; `server_tokens off`.
* Request body limit 16 KiB; generic error bodies without stack traces; only the health endpoint
  of Actuator is exposed; no endpoint returns participant data except the authenticated export;
  no PII in logs.
* Non-root containers (`uid 10001`, `uid 101`); patched dependency versions pinned (Tomcat
  10.1.60, PostgreSQL JDBC 42.7.12, Jackson 2.21.5, commons-lang3 3.18.0, Log4j API 2.25.5).

## Deployment

```bash
cp .env.example .env        # fill in secrets, SMTP, organizer addresses
docker compose up -d --build
```
* Frontend: `http://<host>:8080` (put a TLS-terminating proxy in front).
* Backend ops port bound to `127.0.0.1:8081` (health, export for local admins).
* Export in a browser: `https://<host>/api/admin/registrations/export`, log in with
  `ADMIN_USERNAME` / `ADMIN_PASSWORD`.
* Verification overlay with a local mail catcher: `docker compose -f docker-compose.yml -f
  docker-compose.verify.yml up -d --build`, then `node scripts/smoke-test.mjs`
  (needs `ADMIN_USERNAME` / `ADMIN_PASSWORD` in the environment).

## Quality status at release
Backend 55 unit + 38 integration tests (Testcontainers PostgreSQL, GreenMail) and 32 frontend
tests pass; 0 lint/type errors; SpotBugs, Semgrep, Trivy and npm audit report 0 findings;
container smoke test 50/50. Details and coverage in `docs/verification-report.md`.

## Known limitations / open items
* Email delivery was demonstrated only against local mail catchers (GreenMail, Mailpit) — no
  live mail server was available; failed emails are not retried.
* OWASP Dependency-Check was not executed (requires an NVD API key); Trivy and npm audit were
  used as dependency audit.
* TLS/HSTS must be provided by the ingress in front of the frontend container.
* Rate-limit counters and used form tokens are in memory (single instance; reset on restart).
* No admin UI for options (by design, US-003); no editing of submitted registrations, payment or
  accounts (out of scope).
* Export verified with Apache POI, not opened in Microsoft Excel; responsive layout checked in a
  Chromium-based browser at 375 px and 1280 px.
