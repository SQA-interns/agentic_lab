# Release notes

> Written in: phase 7 · Agent: writes

Release 1.0.0 · run `04_conference-registration_opus5.5_sdd_template-1.0` · stack: Spring Boot 4.1.1 (D-05, D-07, D-08), React 19.3.0, PostgreSQL 16.15.

## Delivered

- US-001 / US-002: registration forms for external participants and students with fixed fields, trimming, email and Unicode handling, client- and server-side validation, field errors next to the fields (NFR-03).
- US-003: options (workshops, events, meals, other) and consents from a configuration file; inactive options hidden and rejected; optional per-type availability (D-09) and per-category limits (D-12).
- US-004: in-application confirmation with reference and selection, shown only after the registration is stored (BR-06).
- US-005: storage in PostgreSQL (Flyway) plus a raw JSON copy on a persistent volume; success only after both are written (AR-05); data survives container recreation (NFR-02).
- US-006 / US-007: plain-text participant confirmation and organizer notification with the JSON attached; failed emails are retried (D-11).
- US-008: Excel export for the organizer account (HTTP Basic), HTTPS-only outside localhost (SR-06).
- Anti-automation: reCAPTCHA v2 verified on the backend (SR-01), deterministic test mode for local and test only (SR-02); rate limits and request-size limit (SR-03).
- Operations: liveness/readiness endpoints and container health checks (NFR-04), non-root containers, security headers, local Docker Compose stack.
- Verification: 224 automated tests pass (67 + 10 frozen acceptance, 3 end-to-end); details in `docs/06_verification-report.md`.

## Known limitations

- Retention is not automated: registrations and JSON copies stay until an operator deletes them (D-14; procedure in `backend/README.md`).
- One organizer account; no administration UI; option changes need a file edit and a backend restart (out of scope).
- Rate limits are kept in memory per backend instance and reset on restart; with several instances each has its own counters.
- A second registration with the same email is accepted (D-13); organizers see duplicates in the export.
- The emails and the form are in English; the conference name and consent wording come from configuration.
- The frontend image proxies `/api` to a service named `backend`; other topologies need the external reverse proxy to route `/api`.
- `.npmrc` cannot set `min-release-age` with the pinned npm 11.6.2 (F-03).
- Dependency-Check suppresses CVE-2025-7962 for `angus-activation` as a false positive (F-07, D-18/D-19); re-check the suppression when upgrading the mail libraries.

## Must be tested manually by a human

1. reCAPTCHA production: with real `RECAPTCHA_SITE_KEY`/`RECAPTCHA_SECRET_KEY` and `APP_ENVIRONMENT=production`, the Google widget appears, one real submission succeeds, and a submission without solving the challenge is refused (`environments.md`).
2. SMTP delivery: participant and organizer emails reach real mailboxes through the production SMTP server (STARTTLS, authentication), including č/š/ž and the JSON attachment; check spam classification and the sender address.
3. TLS and reverse proxy: HTTPS with Let's Encrypt, HTTP→HTTPS redirect, `/` served from the frontend and `/api` forwarded to the backend with `X-Forwarded-For`/`X-Forwarded-Proto`; the proxy must be a trusted internal address for the backend (Tomcat internal proxies) so that SR-06 and per-client rate limits work.
4. Organizer export over the production HTTPS URL with the real organizer credentials; the same request over plain HTTP is refused; the workbook opens in Excel and LibreOffice with č/š/ž intact.
5. Production start-up refuses test mode, empty reCAPTCHA keys and `ORGANIZER_HTTPS_ONLY=false` (automated for the application; confirm with the real deployment configuration).
6. Backups of the database volume and the JSON copy volume, and a restore.
7. Accessibility and usability of the form with a screen reader and keyboard only, and on a phone.
8. Consent wording, privacy notice link and retention period with the product owner (D-10, D-14).

## Decisions pending review

| Decision | Summary |
|---|---|
| D-02 | ES-03 repository files live in `02_output/`; the root `.env` is excluded only through the local `.git/info/exclude` — add `.env` to a root `.gitignore` in the template |
| D-03 | `aldanial/cloc:2.10` contains cloc 1.98 |
| D-06 | OSS Index analyzer disabled (needs credentials); NVD is the data source |
| D-09 | all active options offered to both types unless restricted in configuration (OQ-01) |
| D-10 | one mandatory data-processing consent with proposed wording (OQ-02) |
| D-11 | email failure keeps the registration; retries every 5 min, 10 attempts (OQ-03) |
| D-12 | any number of options per category unless configured (OQ-04) |
| D-13 | duplicate emails allowed (OQ-05) |
| D-14 | retention proposed 12 months after the conference, manual deletion (OQ-06) |
| D-15 | contract validators used outside the project (swagger-parser, ajv) |
| D-16 | `@types/node` 24.19.0 added to the frontend dev dependencies |
| D-17 | one acceptance test (NFR-04 health) passed on the bootstrap skeleton |
| F-03, F-04, F-08 | Medium findings accepted with reasons (`docs/06_verification-report.md`) |

Other branches of this repository contain secret-scanner matches from earlier runs (Gitleaks history scan); they are not part of this release.
