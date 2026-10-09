# Specification

> Written in: phase 2 · Source: `01_acceptance-criteria.md`, `project/stack.md`, `project/constraints.md`, `standards/security.md` · Procedure: `skills/write-specification` · Agent: writes

## 1. Components

| Component | Responsibility | Contracts |
|---|---|---|
| frontend (React, served by nginx) | registration page: type choice, fields, options, consents, anti-automation widget, client validation, field errors, confirmation | [ui-form.json](02_contracts/ui-form.json), [openapi.yaml](02_contracts/openapi.yaml) |
| backend (Spring Boot) | form data, validation, anti-automation verification, storage (database + JSON copy), emails, export, health | [openapi.yaml](02_contracts/openapi.yaml), [database.sql](02_contracts/database.sql), [registration-copy.schema.json](02_contracts/registration-copy.schema.json), [conference-options.schema.json](02_contracts/conference-options.schema.json), [emails.json](02_contracts/emails.json), [export-workbook.json](02_contracts/export-workbook.json), [recaptcha-siteverify.yaml](02_contracts/recaptcha-siteverify.yaml) |
| PostgreSQL 16 | registrations | [database.sql](02_contracts/database.sql) |
| Mailpit (local/test) / SMTP (production) | email delivery | [emails.json](02_contracts/emails.json) |

The frontend calls only `/api/*` with relative URLs (AR-01). nginx in the frontend image serves the static files and proxies `/api/` to the backend, so browser and API share one origin locally and in production (the external reverse proxy does the same routing).

## 2. Backend architecture (AR-02, AR-03)

Layered with ports, root package `si.konferenca.registration`. Reason: one small service with three external side effects (file, mail, reCAPTCHA); ports keep the registration rules testable without them and make AR-05's ordering explicit in one service.

| Layer (package) | Contains | May depend on |
|---|---|---|
| `api` | REST controllers, request/response DTOs, exception handler, rate-limit and size-limit filters | `application`, `domain` |
| `application` | use cases (`RegistrationService`, `ExportService`, `RegistrationFormService`), validation, port interfaces (`RegistrationRepository`, `RegistrationCopyStore`, `RegistrationNotifier`, `CaptchaVerifier`, `WorkbookWriter`) | `domain` |
| `domain` | registration model (JPA entities), enums, option catalogue model | nothing in the application |
| `infrastructure` | Spring Data repository, JSON copy file store, SMTP notifier, reCAPTCHA client, POI workbook writer, options-file loader | `application`, `domain` |
| `config` | Spring configuration, security, typed properties, startup checks | every layer |

ArchUnit rules (test `ArchitectureTest`): the table above as a `layeredArchitecture()` (`api` accessed by `config` only; `infrastructure` accessed by `config` only; `application` not accessing `api` or `infrastructure`; `domain` accessing no other layer); no cycles between slices `si.konferenca.registration.(*)..` (AR-03); `api` classes do not use `jakarta.persistence` or `org.springframework.jdbc`; only `infrastructure` uses `jakarta.mail`, `org.apache.poi` and `java.net.http`.

## 3. Registration flow (AR-05, BR-06, BR-07)

`POST /api/registrations` in this order; the first failing step ends the request:

1. Size limit filter (SR-03): `Content-Length` or bytes read above `MAX_REQUEST_BYTES` → 413. Rate-limit filter → 429.
2. JSON parsing into the type selected by `type` → 400 `invalid_request` (malformed JSON, missing or unknown `type`, non-string values).
3. Field validation (section 4), all errors at once → 400 `validation_failed`.
4. Anti-automation verification (section 6) → 400 `captcha_failed` or 503 `captcha_unavailable`.
5. Duplicate check on `email_normalized` → 409 `duplicate_email` (field `email`). The unique constraint also catches a concurrent duplicate and maps to the same 409.
6. One database transaction: insert registration, options and consents (names, categories and consent texts copied from the configuration), flush; then write the JSON copy (section 5); then commit. A copy failure throws and rolls back the insert (AC-005-03). If the commit fails after the file was written, the file is deleted before the error is returned (AC-005-04). Either failure → 500 `internal_error`.
7. After commit: send the participant email, then the organizer email (section 7). Each failure is logged with the registration id only and swallowed (D-11).
8. 201 with `registrationId`, `registeredAt`, `type`.

`registrationId` is a random UUID; `registeredAt` is the server clock in UTC at step 6, truncated to milliseconds; every consent's `givenAt` equals `registeredAt`.

## 4. Validation (SB-01, BR-02, BR-03, D-15, D-17)

One `RegistrationValidator` in `application`; the frontend mirrors the same rules in `src/validation.ts`.

- Every text field is stripped of leading and trailing Unicode whitespace (`String.strip()`), then: absent, null or empty → `required`; contains a character of Unicode category Cc → `invalid_characters`; longer than its maximum (email 254, names 100, organization / study institution / study programme 200, student ID 50) in code points → `too_long`.
- Email additionally matches `^[^\s@]+@[^\s@]+\.[^\s@]+$` → otherwise `invalid_email`. `email_normalized` = `email.toLowerCase(Locale.ROOT)`.
- A property of the other type present (e.g. `organization` on a student) → `not_allowed`; unknown properties → 400 `invalid_request`.
- Options (SR-04, BR-04, D-09, D-12): each id must exist in the configuration (`unknown_option`), be active (`inactive_option`) and be available to the type (`option_not_available`); per category the count must not exceed `maxSelections` (`too_many_options`). All option errors use field `optionIds`.
- Consents (BR-05, D-10): every mandatory consent id present, else `consent_required`; unknown id → `unknown_consent`; field `consentIds`.
- Text is otherwise unrestricted Unicode (NFR-01); no normalisation beyond trimming.

## 5. Storage (BR-07, AR-06, NFR-02)

- Schema: [database.sql](02_contracts/database.sql), applied by Flyway as `V1__create_registration_tables.sql`; `spring.jpa.hibernate.ddl-auto=validate`.
- JSON copy: [registration-copy.schema.json](02_contracts/registration-copy.schema.json), one file per registration `registration-<id>.json` in `JSON_COPY_DIR`, serialised once to UTF-8 bytes; written to a temporary file in the same directory, forced to disk, then moved atomically. The same bytes go into the organizer attachment.
- Local and production: the database data and `JSON_COPY_DIR` are named Docker volumes (`db-data`, `registration-copies`); the backend creates `JSON_COPY_DIR` at startup if missing and refuses to start if it is not writable.
- Retention (SB-13, D-14): nothing is deleted automatically.

## 6. Anti-automation (SR-01, SR-02, AR-07)

- `RECAPTCHA_TEST_MODE=false` (default): the form gets `captcha.mode=recaptcha` and `RECAPTCHA_SITE_KEY`; the backend posts the token to `RECAPTCHA_VERIFY_URL` (default Google's) per [recaptcha-siteverify.yaml](02_contracts/recaptcha-siteverify.yaml), 5 s timeout.
- `RECAPTCHA_TEST_MODE=true`: the form gets `captcha.mode=test`, shows `captcha-test`; the backend accepts exactly the token `test-valid` and calls nothing.
- `captchaToken` is not a validated field: absent, empty or rejected → 400 `captcha_failed` (after field validation passed).
- Startup refuses (exception naming the setting, never its value) when test mode is off and either key is empty, or when test mode is on and the Spring profile `production` is active.
- Production code path tests use a local HTTP stub as `RECAPTCHA_VERIFY_URL`, with accepted, rejected, non-200 and timeout answers (DoD-P05).

## 7. Emails (US-006, US-007, SR-05)

[emails.json](02_contracts/emails.json). Plain text UTF-8 only, built from fixed templates; subjects contain only the configured conference name; recipients are the validated participant address and `ORGANIZER_EMAILS` (comma-separated, each validated at startup). Sent with Spring `JavaMailSender` over SMTP (STARTTLS required when `SMTP_TLS=true`).

## 8. Export and organizer access (US-008, BR-08, SR-06, SR-07)

- `GET /api/export` requires role `ORGANIZER` via HTTP Basic (accepted by D-20). The single user is built at startup from `ORGANIZER_USERNAME` and a BCrypt hash of `ORGANIZER_PASSWORD` (SB-03); the plain value is not kept. Missing or empty organizer settings → startup refuses.
- `OrganizerHttpsFilter` before authentication: if `ORGANIZER_HTTPS_ONLY=true` and the request is neither secure (`X-Forwarded-Proto: https` from the trusted reverse proxy, via `server.forward-headers-strategy=native`) nor from a loopback address → 403 `https_required`.
- Workbook: [export-workbook.json](02_contracts/export-workbook.json), built in memory with POI `XSSFWorkbook`; only the listed columns (SR-07).
- Every other path under `/api` except the two public endpoints is denied; actuator exposes only `health` (with liveness and readiness groups).

## 9. Configuration (ES-01)

Environment variables, mapped in `application.yml`; secrets have no default. `local` means the defaults in `docker-compose.yml`, `test` means the test configuration.

| Variable | Default | Notes |
|---|---|---|
| `DATABASE_URL`, `DATABASE_USER`, `POSTGRES_PASSWORD` | none | compose sets URL/user; password from `.env` |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_TLS` | none, none, `true` | compose: `mailpit`, `1025`, `false` |
| `SMTP_USERNAME`, `SMTP_PASSWORD` | empty | production only |
| `MAIL_FROM` | `registration@konferenca.si` | |
| `CONFERENCE_NAME` | `Conference` | |
| `OPTIONS_FILE` | `classpath:conference-options.json` (copy of [conference-options.example.json](02_contracts/conference-options.example.json)) | a Spring resource location (`classpath:` or `file:`); profile `production` requires an explicit `file:` location |
| `JSON_COPY_DIR` | `/data/registrations` | named volume |
| `RECAPTCHA_TEST_MODE`, `RECAPTCHA_SITE_KEY`, `RECAPTCHA_SECRET_KEY`, `RECAPTCHA_VERIFY_URL` | `false`, empty, empty, Google | compose and tests: test mode `true` |
| `ORGANIZER_USERNAME`, `ORGANIZER_PASSWORD`, `ORGANIZER_EMAILS` | none | from `.env` |
| `ORGANIZER_HTTPS_ONLY` | `true` | compose: `false` (plain HTTP on 127.0.0.1; requests arrive from the Docker gateway, not loopback) |
| `CORS_ALLOWED_ORIGINS` | empty (no CORS) | only for a dev server on another origin |
| `RATE_LIMIT_REGISTRATIONS_PER_MINUTE`, `RATE_LIMIT_EXPORTS_PER_MINUTE`, `RATE_LIMIT_FORM_PER_MINUTE`, `MAX_REQUEST_BYTES` | `30`, `10`, `120`, `16384` | per client IP |

## 10. Security controls

| Control | Implementation |
|---|---|
| Rate limits (SR-03, SB-06) | in-memory fixed one-minute window per client IP and endpoint group; 429 `rate_limited` with `Retry-After`; client IP from `request.getRemoteAddr()` after `forward-headers-strategy=native` (trusted proxies only). 30 registrations per minute per IP keeps a lecture hall behind one NAT usable. |
| Size limit (SR-03) | filter on `/api/**` for request bodies; Tomcat `max-swallow-size` and `max-http-form-post-size` at the same value |
| Headers (SB-10) | backend: Spring Security defaults (`X-Content-Type-Options`, `X-Frame-Options: DENY`, cache control) plus `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`, `Referrer-Policy: no-referrer`. nginx: `Content-Security-Policy: default-src 'self'; script-src 'self' https://www.google.com/recaptcha/ https://www.gstatic.com/recaptcha/; frame-src https://www.google.com/recaptcha/ https://recaptcha.google.com/recaptcha/; connect-src 'self'; img-src 'self' data:; style-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'`, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, `server_tokens off` |
| CSRF | disabled: no cookie or session authentication exists (Basic only, stateless) |
| Output encoding (SB-05) | JPA parameter binding only; React escapes text; plain-text emails; string-only workbook cells |
| Logging (ES-07, SB-07) | log registration ids, error codes and exception class names only; never request bodies, names, emails, tokens or credentials; `server.error.include-*` off |
| Least privilege (SB-11) | backend runs as user `app`, frontend as `nginx`, port 8080 inside both containers |
| TLS (SB-04) | external reverse proxy in production; SMTP STARTTLS when `SMTP_TLS=true` |
| Data minimisation (SB-12, SB-13) | only the BR-01 fields, options and consents are stored; client IP is used for rate limiting and reCAPTCHA only and not stored |

## 11. Error behaviour

- Every error body follows `ErrorResponse` in [openapi.yaml](02_contracts/openapi.yaml), including 404 `not_found`, 405 (`invalid_request`) and Spring Security's 401/403, which the security configuration writes as JSON.
- Unexpected exceptions → 500 `internal_error`, message "The registration could not be processed. Please try again later.", logged with a random error id and the exception class only.
- Frontend: field errors next to fields; other errors in `form-error` with a fixed message per error code (429: "Too many attempts. Please wait a minute and try again."; 503 and 500: the generic message).

## 12. Frontend

- Modules: `api.ts` (fetch wrappers, relative `/api` URLs), `validation.ts` (section 4 rules), `messages.ts` (all texts, D-18), `RegistrationPage.tsx`, `RegistrationForm.tsx`, `OptionsFieldset.tsx`, `Captcha.tsx` (test checkbox or Google widget loaded from `https://www.google.com/recaptcha/api.js` with the site key from the form data), `Confirmation.tsx`.
- No secret in the bundle (AR-07): the site key arrives at runtime from `/api/registration-form`.

## 13. Local stack and health (NFR-02, NFR-04, ES-09)

`02_output/docker-compose.yml`: `postgres` (`postgres:16.15-alpine`, volume `db-data`, `pg_isready` health check), `mailpit` (`axllent/mailpit:v1.31.1`, UI/API on 127.0.0.1:8025), `backend` (built from `backend/Dockerfile`, volume `registration-copies` on `/data/registrations`, health check `wget` on `/actuator/health/readiness`, depends on healthy postgres), `frontend` (built from `frontend/Dockerfile`, published on `127.0.0.1:8080`, health check on `/`, depends on healthy backend). Only frontend and Mailpit publish ports, both on 127.0.0.1. Readiness includes the database.

## 14. Choices the inputs leave open

| Choice | Reason |
|---|---|
| Options from a JSON file read at startup (AR-04) | simplest configuration-only mechanism; validated against a schema; restart is acceptable for a programme change |
| One `POST` with a `type` discriminator instead of two endpoints | one validation and storage path; the type decides the field set |
| Form data in one `GET /api/registration-form` | one request supplies options, consents and anti-automation mode |
| Test-mode token `test-valid` | deterministic, lets tests also send a wrong token |
| Option names copied into the registration | a later configuration change must not rewrite stored registrations or exports |
| Plain-text emails | removes markup injection entirely (SR-05) |
| In-memory rate limiting | one backend instance; no extra service in `project/stack.md` |

## Traceability

| AC / SR / AR | Section |
|---|---|
| AC-001-01, AC-002-01 | 3, 5, [openapi.yaml](02_contracts/openapi.yaml) |
| AC-001-02, AC-002-02, AC-003-05 | 12, [ui-form.json](02_contracts/ui-form.json) |
| AC-001-03..06, AC-001-16, AC-001-18, AC-002-03..06, AC-002-14, AC-002-16 | 4 |
| AC-001-07..11, AC-002-07..09 | 4, [conference-options.schema.json](02_contracts/conference-options.schema.json) |
| AC-001-12, AC-001-13, AC-002-10, AC-002-11 | 4, [ui-form.json](02_contracts/ui-form.json) |
| AC-001-14, AC-002-12 | 6 |
| AC-001-15, AC-002-13 | 3 (step 5), [database.sql](02_contracts/database.sql) |
| AC-001-17, AC-002-15 | 12, [ui-form.json](02_contracts/ui-form.json) |
| AC-003-01..04, AC-003-06 | 9 (`OPTIONS_FILE`), 14, [conference-options.schema.json](02_contracts/conference-options.schema.json), [openapi.yaml](02_contracts/openapi.yaml) |
| AC-004-01..03 | 3, 11, [ui-form.json](02_contracts/ui-form.json) |
| AC-005-01..05 | 3 (step 6), 5, [database.sql](02_contracts/database.sql), [registration-copy.schema.json](02_contracts/registration-copy.schema.json) |
| AC-006-01..05, AC-007-01..05 | 3 (step 7), 7, [emails.json](02_contracts/emails.json) |
| AC-008-01..05 | 8, [export-workbook.json](02_contracts/export-workbook.json) |
| SR-01, SR-02 | 6, [recaptcha-siteverify.yaml](02_contracts/recaptcha-siteverify.yaml) |
| SR-03 | 3 (step 1), 10 |
| SR-04 | 4 |
| SR-05 | 7, 10, [emails.json](02_contracts/emails.json) |
| SR-06 | 8 |
| SR-07 | 8, [emails.json](02_contracts/emails.json), [export-workbook.json](02_contracts/export-workbook.json) |
| SB-01 | 4 |
| SB-02, SB-03 | 8 |
| SB-04, SB-05, SB-06, SB-07, SB-10, SB-11, SB-12 | 10 |
| SB-08, SB-09 | `out/scripts/verify.sh` (dependency scans, Semgrep, gitleaks) |
| SB-13 | 5 (retention, D-14), 10 |
| SB-14 | 4 (consents), [database.sql](02_contracts/database.sql) |
| NFR-01 | 4, 5, 7, [export-workbook.json](02_contracts/export-workbook.json) |
| NFR-02 | 5, 13 |
| NFR-03 | 4, 12, [ui-form.json](02_contracts/ui-form.json) |
| NFR-04 | 13 |
| AR-01 | 1, 12 |
| AR-02, AR-03 | 2 |
| AR-04 | 9, 14, [conference-options.schema.json](02_contracts/conference-options.schema.json) |
| AR-05 | 3 |
| AR-06 | 5, [database.sql](02_contracts/database.sql) |
| AR-07 | 6, 12 |
