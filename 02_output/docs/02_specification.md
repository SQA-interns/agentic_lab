# Specification

> Written in: phase 2 · Source: `01_acceptance-criteria.md`, `project/00_setup/tech-stack.md`, `project/00_setup/environments.md`, `project/02_design/*`, `general/standards.md` · Agent: writes

Contracts (`docs/02_contracts/`, validated by `verify.sh <phase> contracts`):

| Contract | Interface |
|---|---|
| [registration-api.openapi.yaml](02_contracts/registration-api.openapi.yaml) | options, registration, export (REST) |
| [ui-form.json](02_contracts/ui-form.json) | registration form (UI): test ids, labels, messages |
| [database.sql](02_contracts/database.sql) | registration storage (SQL, Flyway `V1`) |
| [registration-copy.schema.json](02_contracts/registration-copy.schema.json) | JSON copy (file) |
| [conference-config.schema.json](02_contracts/conference-config.schema.json) | conference options (configuration file) |
| [emails.schema.json](02_contracts/emails.schema.json) | emails (SMTP) |
| [recaptcha-verify.openapi.yaml](02_contracts/recaptcha-verify.openapi.yaml) | anti-automation (Google verify call) |

## 1. Components

| Component | Responsibility |
|---|---|
| backend (`02_output/backend`) | Serves `/api`: form definition, validation and storage of registrations, JSON copy, emails, export; health under `/actuator/health/{liveness,readiness}` (ES-09, NFR-04) |
| frontend (`02_output/frontend`) | React single page: type choice, form, client validation, reCAPTCHA widget, confirmation; built by Vite, served by nginx as static files |
| `02_output/docker-compose.yml` | Local stack: backend, frontend, PostgreSQL, Mailpit; named volumes `db-data`, `json-copies` (NFR-02); ports bound to 127.0.0.1 only |
| `02_output/config/conference.json` | Local conference options (AR-04), mounted read-only into the backend |

## 2. Backend internal architecture (AR-02)

Ports-and-adapters, chosen because storage, file copy, mail and reCAPTCHA are external and must be replaceable by test substitutes.

| Package under `si.konferenca.registration` | Contains | May depend on |
|---|---|---|
| `domain` | Registration model, normalisation, validation rules, option catalogue | nothing else in the application |
| `application` | Use cases (`RegisterParticipant`, `ExportRegistrations`, `GetRegistrationForm`) and port interfaces (`RegistrationStore`, `JsonCopyStore`, `Notifier`, `CaptchaVerifier`, `WorkbookWriter`) | `domain` |
| `api` | REST controllers, request/response DTOs, problem mapping, rate limit and size filters | `application`, `domain` |
| `adapter.persistence` | JPA entities and repositories, `RegistrationStore` | `application`, `domain` |
| `adapter.jsoncopy` | `JsonCopyStore` on the file system | `application`, `domain` |
| `adapter.mail` | `Notifier` over SMTP | `application`, `domain` |
| `adapter.captcha` | Google and test-mode `CaptchaVerifier` | `application`, `domain` |
| `adapter.export` | `WorkbookWriter` (Apache POI) | `application`, `domain` |
| `config` | Spring configuration, security, settings, startup guards, options loader | all |

ArchUnit rules check exactly this table, adapters do not depend on each other or on `api`, and there are no slice cycles (AR-03).

## 3. Configuration (ES-01)

All values come from environment variables; the backend has no default for a secret.

| Variable | Used by | Default | Notes |
|---|---|---|---|
| `APP_ENVIRONMENT` | backend | `production` | `production`, `local` or `test`; the safest value is the default |
| `DATABASE_URL`, `DATABASE_USER`, `POSTGRES_PASSWORD` | backend | none | compose sets local values |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_TLS`, `SMTP_USERNAME`, `SMTP_PASSWORD` | backend | none; `SMTP_TLS=true` | local: Mailpit, `SMTP_TLS=false`, empty credentials |
| `MAIL_FROM`, `CONFERENCE_NAME` | backend | `registration@localhost`, `Conference` | shown in emails |
| `ORGANIZER_EMAILS` | backend | none | comma separated, each validated at startup |
| `ORGANIZER_USERNAME`, `ORGANIZER_PASSWORD` | backend | none | password ≥ 16 characters, BCrypt-hashed at startup; the plain value is not kept (SB-03) |
| `ORGANIZER_HTTPS_ONLY` | backend | `true` | `false` refused unless `APP_ENVIRONMENT=local` or `test` (SR-06) |
| `CONFERENCE_CONFIG_PATH` | backend | none | options file (AR-04) |
| `JSON_COPY_DIR` | backend | `/data/json-copies` | on a named volume |
| `RECAPTCHA_TEST_MODE` | backend | `false` | `true` refused when `APP_ENVIRONMENT=production` (SR-02) |
| `RECAPTCHA_SITE_KEY`, `RECAPTCHA_SECRET_KEY` | backend | none | required unless test mode; site key is served to the frontend (AR-07) |
| `RECAPTCHA_VERIFY_URL` | backend | Google siteverify URL | overridden by tests with a mock (DoD-P05) |
| `CORS_ALLOWED_ORIGIN` | backend | empty (CORS off) | only set locally for the Vite dev server |
| `RATE_LIMIT_REGISTRATIONS_PER_MINUTE`, `RATE_LIMIT_EXPORTS_PER_MINUTE`, `RATE_LIMIT_FORMS_PER_MINUTE` | backend | 10, 10, 60 | per client address |
| `MAX_REQUEST_BYTES` | backend | 16384 | SR-03 |

Startup guards: the backend refuses to start when the options file is missing, does not match its schema or repeats an id; when a guard above fails; or when a required value is empty. The frontend has no build-time configuration; it reads everything from `GET /api/registration-form/{type}`.

## 4. Registration flow

1. The frontend loads the form definition for the chosen type, renders fixed fields, the four categories in order and the consents unticked (BR-01, BR-04, BR-05).
2. On submit the frontend validates as in section 5 and shows errors next to fields (NFR-03); the submit button is disabled while a request is in flight.
3. The backend, in this order: size and rate checks → body parsing (unknown properties: `MALFORMED`) → field normalisation and validation → option and consent checks → reCAPTCHA (SR-01) → duplicate email → storage. All field errors of one request are reported together; reCAPTCHA is verified only when the fields are valid, so an invalid form does not use up a token.
4. Storage (AR-05, BR-07): in one database transaction insert the registration, its options and consents; write the JSON copy to a temporary file in `JSON_COPY_DIR`, flush it to disk and atomically rename it to `<id>.json`; then commit. If the file write fails the transaction rolls back; if the commit fails the file is deleted. Either failure returns 503 and nothing is kept (AC-005-03). The unique `email_normalized` decides concurrent duplicates (409).
5. 201 is returned only after the commit. The frontend then replaces the form with the confirmation (BR-06).
6. After the commit, an asynchronous task sends the participant confirmation and the organizer notification with the JSON copy bytes attached. A failure is logged with the registration id and the error class only; it never affects the stored registration (D-16).

## 5. Validation (SB-01, BR-02, BR-03, BR-04, BR-05)

Implemented identically in the backend (authoritative) and the frontend (convenience).

| Rule | Detail |
|---|---|
| Whitespace | Leading and trailing characters with the Unicode `White_Space` property plus U+FEFF are removed; this includes U+00A0 (KP-03). JavaScript `trim()` matches this set; Java uses an explicit set, not `String.strip()` |
| Required | Every fixed field of the type is non-empty after trimming → `REQUIRED`; fields of the other type are refused as `MALFORMED` |
| Length | After trimming, at most the `maxLength` in the API contract → `TOO_LONG` |
| Email | After trimming, `^[^\s@]+@[^\s@]+\.[^\s@]+$`, at most 254 → `INVALID_EMAIL`; no control characters can pass, so no header injection (SR-05) |
| Text | Any Unicode letters are kept unchanged (NFC is not applied); control characters U+0000–U+001F and U+007F are rejected as `MALFORMED` |
| Options | Every id must be configured, active and available to the type (SR-04, D-14) → `OPTION_NOT_AVAILABLE`; more than the category maximum → `TOO_MANY_OPTIONS` (D-17) |
| Consents | Each mandatory consent id present → else `CONSENT_REQUIRED` with `consentId`; an unknown id → `UNKNOWN_CONSENT` |
| Duplicate | Trimmed, lower-cased email already stored → 409 `ALREADY_REGISTERED` (D-18) |
| reCAPTCHA | Google verify (`success=true`) or, in test mode, token `test-pass` → else `CAPTCHA_FAILED` |

## 6. Security controls

| Control | Design |
|---|---|
| Organizer access (BR-08, SB-02) | Spring Security: `GET /api/registrations/export` requires HTTP Basic as the single organizer user; everything else under `/api` is public; `/actuator/health/**` public, all other actuator endpoints off |
| HTTPS for credentials (SR-06, SB-04) | With `ORGANIZER_HTTPS_ONLY=true` the export needs `request.isSecure()` (the proxy's `X-Forwarded-Proto`, trusted only from internal proxy addresses) or a loopback client; otherwise 403 before the credentials are checked |
| Credentials (SB-03) | BCrypt hash created at startup, constant-time comparison by Spring Security |
| Rate limits, size (SB-06, SR-03) | Fixed one-minute window per client address in a servlet filter, 429 with `Retry-After`; bodies above `MAX_REQUEST_BYTES` → 413 |
| Headers (SB-10, KP-02) | Backend (Spring Security) sets on every `/api` response: `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, `Cache-Control: no-store`. nginx sets its own set on static responses only, in every static `location`, and adds nothing on `/api` |
| Frontend CSP (SB-10) | `default-src 'self'; script-src 'self' https://www.google.com/recaptcha/ https://www.gstatic.com/recaptcha/; frame-src https://www.google.com/recaptcha/ https://recaptcha.google.com/recaptcha/; style-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'` |
| nginx host handling (KP-01) | `proxy_set_header Host backend`; `absolute_redirect off`; no `$host` or `$http_host` anywhere |
| Output encoding (SB-05) | React escapes all text; emails are plain text; queries are JPA parameters only; workbook cells are written as string cells, never formulas |
| Errors and logs (SB-07, ES-07) | Problem bodies carry only `title`, `status` and field codes; logs carry registration ids, never names, emails or tokens; no stack traces in responses |
| Containers (SB-11) | Backend on `eclipse-temurin` JRE as a non-root user; nginx as user `nginx` listening on 8080; read-only options file |
| Export content (SR-07) | Only the columns in section 7; no database ids other than the registration id, no internal fields |
| Personal data (SB-12, SB-13, SB-14) | Only the fields in `security-requirements.md`; retention per D-19, stated in the consent text; consents never preselected and stored with wording and timestamp |
| Scanning (SB-08, SB-09) | Dependency-Check and `npm audit`; Semgrep and Gitleaks via `verify.sh` |

## 7. Export (US-008)

One sheet `Registrations`, row 1 headings, then one row per registration ordered by submission time: Registration ID, Type, First name, Last name, Email, Organization / institution, Study institution, Study programme, Student ID, Workshops, Events, Meals, Other activities (option names joined with `; `), Consents (given consent ids joined with `; `), Registered at (UTC, ISO 8601 with `Z`). Type is `External participant` or `Student`; a field the type does not have is an empty cell. All cells are text. Response headers per the API contract.

## 8. Frontend

Type choice → form → confirmation, in one page (`ui-form.json`). The form definition is fetched per type; options are checkboxes, and once a category reaches its maximum the remaining boxes are disabled. In `GOOGLE` mode the widget is loaded from Google with the served site key; in `TEST` mode a labelled checkbox yields the token `test-pass`. Backend field errors are shown at the field with `aria-describedby`; 503 and network errors show `GENERAL`, 429 `RATE_LIMITED`. In local compose the frontend nginx proxies `/api` to the backend (same origin); in production the external proxy does it (AR-01).

## 9. Choices the inputs leave open

| Choice | Reason |
|---|---|
| Ports-and-adapters backend | External services must be swappable for local substitutes in tests |
| Options in a JSON file at `CONFERENCE_CONFIG_PATH`, validated against a schema at startup | Reconfigurable without code changes and fails fast on a bad file (AR-04) |
| Form definition served by the backend, including the site key | One frontend image for every environment; no secret in the bundle (AR-07) |
| HTTP Basic for the single organizer | One role and one operation; no session or CSRF surface (ASVS L1) |
| JSON copy written inside the transaction, before commit | The only order that makes AR-05 hold for both failure cases |
| Emails sent asynchronously after commit | Storage before notification (scope priority 1); slow SMTP cannot delay the confirmation |
| In-memory rate limiting | No rate-limit library is pinned; one backend instance |
| Test mode token `test-pass` | Deterministic, no network to Google in test and local (environments.md) |
| Field length limits | Generous for real names and institutions; bound storage and email size |
| English user interface | No language is specified; Slovenian characters are supported in data |
| Redocly CLI 2.62.0 and host Python `jsonschema` for contract checks | No validator is pinned (D-20) |

## 10. Traceability

| IDs | Section or contract |
|---|---|
| AC-001-01, AC-002-01, AC-003-03 | §4 step 1, §8, `ui-form.json`, API `RegistrationForm.fields` |
| AC-001-02, AC-001-03, AC-002-02 | §4, API `submitRegistration` 201 |
| AC-001-04, AC-001-05, AC-001-06, AC-002-03 | §5 Whitespace, Required |
| AC-001-07, AC-002-04 | §5 Email |
| AC-001-08 | §5 Text |
| AC-001-09, AC-002-05, AC-003-01 | §4 step 1, §8, `conference-config.schema.json` |
| AC-001-10, AC-001-11, AC-001-12, AC-002-06, AC-003-02 | §5 Options |
| AC-001-13, AC-001-14, AC-002-07, AC-003-04 | §5 Consents, §8, `conference-config.schema.json` consents |
| AC-001-15, AC-002-08 | §5 Duplicate, `database.sql` `email_normalized` |
| AC-001-16, AC-002-09 | §5 reCAPTCHA, `recaptcha-verify.openapi.yaml` |
| AC-004-01, AC-004-02, AC-004-03 | §4 steps 5, §8, `ui-form.json` confirmation and messages |
| AC-005-01, AC-005-02, AC-005-03, AC-005-04, AC-005-05 | §4 step 4, `database.sql`, `registration-copy.schema.json`, §1 volumes |
| AC-006-01, AC-006-02, AC-006-03, AC-006-04 | §4 step 6, `emails.schema.json` |
| AC-007-01, AC-007-02, AC-007-03, AC-007-04 | §4 step 6, `emails.schema.json` |
| AC-008-01, AC-008-02, AC-008-03, AC-008-04, AC-008-05 | §6 Organizer access, §7, API `exportRegistrations` |
| SR-01, SR-02 | §3, §5 reCAPTCHA, `recaptcha-verify.openapi.yaml` |
| SR-03 | §6 Rate limits, API 413/429 |
| SR-04 | §5 Options |
| SR-05 | §5 Email, `emails.schema.json` |
| SR-06 | §6 HTTPS for credentials |
| SR-07 | §6 Export content, §7 |
| SB-01 | §5 |
| SB-02, SB-03, SB-04 | §6 |
| SB-05, SB-06, SB-07, SB-08, SB-09, SB-10, SB-11, SB-12, SB-13, SB-14 | §6 |
| NFR-01 | §5 Text, `registration-copy.schema.json`, `emails.schema.json` (UTF-8), §7 |
| NFR-02 | §1 named volumes |
| NFR-03 | §4 step 2, §8 |
| NFR-04 | §1 health endpoints |
| AR-01 | §8, API servers `/api` |
| AR-02, AR-03 | §2 |
| AR-04 | §3 `CONFERENCE_CONFIG_PATH`, `conference-config.schema.json` |
| AR-05 | §4 step 4 |
| AR-06 | `database.sql` (Flyway V1) |
| AR-07 | §9, API `RegistrationForm.recaptcha.siteKey` |
| KP-01, KP-02 | §6 nginx host handling, Headers |
| KP-03 | §5 Whitespace |
| KP-04 | repository `.gitattributes`; `verify.sh` hashes LF content |
| KP-05 | short paths under `02_output/`; root README clone instruction (phase 7) |
| KP-06 | `src/test/resources/docker-java.properties` only if Testcontainers fails (phase 3/5) |
| KP-07 | `backend/dependency-check-suppressions.xml`, D-03 |
| KP-08 | End-to-end tests start a fresh compose stack under its own project name (phase 3) |
