# Specification

> Written in: phase 2 · Source: `01_acceptance-criteria.md`, `project/stack.md`, `project/constraints.md`, `standards/security.md` · Procedure: `skills/write-specification` · Agent: writes

Contracts are in [`02_contracts/`](02_contracts/); this document links to them and does not repeat them.

## 1 Components

| Component | Responsibility | Interfaces |
|---|---|---|
| backend (`02_output/backend`) | validate, verify anti-automation token, store (database + JSON copy), send emails, export; health | [api.openapi.yaml](02_contracts/api.openapi.yaml), [db-schema.sql](02_contracts/db-schema.sql), [registration-copy.schema.json](02_contracts/registration-copy.schema.json), [conference-config.schema.json](02_contracts/conference-config.schema.json), [emails.json](02_contracts/emails.json), [recaptcha-siteverify.openapi.yaml](02_contracts/recaptcha-siteverify.openapi.yaml) |
| frontend (`02_output/frontend`) | one-page registration form for both types, client-side validation, confirmation | [ui-registration-form.json](02_contracts/ui-registration-form.json), calls only `/api/*` (AR-01) |
| PostgreSQL, Mailpit | local database and mail catcher (`02_output/docker-compose.yml`) | — |

## 2 Backend architecture (AR-02, AR-03)

Declared layering, root package `si.konferenca.registration`:

| Package | Contains | May depend on |
|---|---|---|
| `domain` | registration model, registration types, option catalog, consent catalog, validation rules | nothing outside `java.*` (no Spring, no Jakarta, no POI) |
| `application` | use cases (`RegistrationService`, `ExportService`), ports (interfaces) for storage, JSON copy, captcha, notifications, export writer | `domain` |
| `web` | REST controllers, request/response DTOs, problem-details mapping, rate-limit and size-limit filters | `application`, `domain` |
| `infrastructure` | JPA entities and repositories, file JSON copy store, SMTP notifier, reCAPTCHA client, Excel writer, config-file loader | `application`, `domain` |
| `config` | Spring configuration, security, typed properties, startup guards | all |

Reason: the domain rules (BR-01 to BR-05) are testable without Spring, and the storage order of AR-05 lives in one application service behind ports. ArchUnit rules (phase 4): exactly the table above, plus `web` must not access `infrastructure`, plus no package cycles across the five slices (AR-03).

## 3 Registration flow (US-001, US-002, US-004, US-005, AR-05)

`POST /api/registrations`, in order:

1. Size limit and rate limit filters (SR-03) → 413 / 429.
2. Parse JSON; unknown property, wrong JSON type or wrong content type → 400 `MALFORMED` / 415.
3. Validation (§4) → 400 with every field error at once.
4. Anti-automation verification (§6) → 400 `CAPTCHA_FAILED` or 503. Done after validation so a token is not spent on an invalid form.
5. Duplicate email check (D-09) on `email_normalized` (trimmed, lower-cased with `Locale.ROOT`) → 409 `DUPLICATE_EMAIL` on field `email`. The unique constraint catches a race and maps to the same 409.
6. One database transaction: insert registration, options and consents and flush; write the JSON copy to `JSON_COPY_DIR/<id>.json` via a temporary file and an atomic move; commit. A JSON write failure rolls the transaction back (AC-005-03). A failed insert or commit deletes the JSON file if it was written (AC-005-04). Either failure → 500 with no internals (AC-004-03); no email (AC-005-05).
7. Respond 201 with the confirmation (AC-004-01).
8. After commit, an asynchronous task sends the participant and organizer emails ([emails.json](02_contracts/emails.json)). A failure is logged with the registration id only (D-07, AC-006-03, AC-007-04).

The JSON copy holds the accepted, trimmed values, the option names and categories, and the consent wording at acceptance; the same bytes are attached to the organizer email (AC-007-02).

## 4 Validation (BR-01 to BR-05, SB-01)

Done on the backend in `domain`, regardless of what the frontend did. The frontend repeats the required, email and consent checks (NFR-03).

| Rule | Code |
|---|---|
| `type` is `external` or `student`; fields of the other type are absent or empty | `REQUIRED` on `type`, `NOT_ALLOWED` |
| Each required field is not empty after `String.strip()` | `REQUIRED` |
| Lengths after stripping: names 100, email 254, organization / study institution / programme 200, student ID 50 (= database columns) | `TOO_LONG` |
| No control characters (Unicode category Cc, which includes CR and LF) in any text field; letters of any script are allowed (BR-03) | `INVALID_CHARACTERS` |
| Email matches `^[^\s@]+@[^\s@]+\.[^\s@]+$` (same in the frontend) | `INVALID_EMAIL` |
| Each option id is configured, active, offered to the type, and not repeated (BR-04, D-05, D-08) | `UNKNOWN_OPTION`, `INACTIVE_OPTION`, `OPTION_NOT_OFFERED`, `DUPLICATE_OPTION` on `optionIds` |
| Every mandatory consent is in `consents`; every consent id is configured (BR-05) | `CONSENT_REQUIRED` on `consents.<id>`, `UNKNOWN_CONSENT` on `consents` |
| `captchaToken` present | `REQUIRED` on `captchaToken` |

Stored values are the stripped values, otherwise unchanged (no case or Unicode normalisation except `email_normalized`). Field error codes and statuses: [api.openapi.yaml](02_contracts/api.openapi.yaml).

## 5 Conference options and consents (US-003, AR-04, D-05, D-06)

A JSON file named by `CONFERENCE_CONFIG_FILE`, format [conference-config.schema.json](02_contracts/conference-config.schema.json), is read once at startup. The backend refuses to start when the file is missing, does not match the schema (checked in code, the same rules), or repeats an id. Restarting with a changed file changes the options and consents without a code change. The participant fields are fixed in code and the file has no way to change them (BR-01). `GET /api/form-config` returns active options only, plus all consents. The local stack mounts `02_output/config/conference-config.json`.

## 6 Anti-automation (SR-01, SR-02, AR-07)

- `live` mode: the frontend loads the Google reCAPTCHA v2 widget with the site key from `GET /api/form-config`. The backend posts the token to `RECAPTCHA_VERIFY_URL` ([recaptcha-siteverify.openapi.yaml](02_contracts/recaptcha-siteverify.openapi.yaml)) with a 5-second timeout and accepts it only when `success` is true. Timeout, non-200 or an unreadable body → 503 (fail closed).
- `test` mode (`RECAPTCHA_TEST_MODE=true`): no network call; the token `test-pass` is accepted and every other token rejected. The frontend shows the test checkbox from the UI contract.
- Startup guard: test mode is refused unless `APP_ENVIRONMENT` is `local` or `test`. With `APP_ENVIRONMENT=production`, empty `RECAPTCHA_SITE_KEY` or `RECAPTCHA_SECRET_KEY` are refused (SR-02).
- DoD-P05: the live path is tested against a local mock of the verification endpoint, with accepted, rejected and unavailable responses.

## 7 Organizer access and export (US-008, BR-08, SR-06, SR-07)

- `GET /api/registrations/export` requires the single role `ORGANIZER`, authenticated with HTTP Basic (realm `organizer`) against `ORGANIZER_USERNAME` / `ORGANIZER_PASSWORD`. At startup the password is BCrypt-hashed in memory and the plain value is not kept (SB-03). The backend refuses to start if the password is shorter than 16 characters. Reason for Basic: there is one read-only download for one role; a browser's built-in prompt lets an organizer download without an administration UI (out of scope), and identity providers are out of scope. Scanner finding: D-11.
- HTTPS only (SR-06): with `ORGANIZER_HTTPS_ONLY=true` (default), an export request that is not secure → 403 before credentials are checked. Behind the production proxy, `X-Forwarded-Proto` is honoured only from internal proxy addresses (Tomcat RemoteIpValve defaults). `false` is allowed only when `APP_ENVIRONMENT` is `local` or `test` (startup guard).
- 401 without or with wrong credentials (AC-008-02, AC-008-03). No session is created; CSRF protection is not needed because no state-changing endpoint is authenticated.
- Export workbook: one sheet `Registrations`, header row then one row per registration ordered by `received_at`. Columns: Registration ID, Received at (UTC, ISO 8601), Type, First name, Last name, Email, Organization / institution, Study institution, Study programme, Student ID, Workshops, Events, Meals, Other activities (option names joined with `; `), Consents (`id (timestamp)` joined with `; `). Every cell is a string cell, never a formula (prevents formula injection). No other field is exported (SR-07). File name `registrations.xlsx`.

## 8 Rate and size limits (SR-03, SB-06)

In-process fixed one-minute window per client address and endpoint group: `POST /api/registrations` `RATE_LIMIT_REGISTRATIONS_PER_MINUTE` (default 10), export `RATE_LIMIT_EXPORTS_PER_MINUTE` (default 10), `GET /api/form-config` 60. Exceeding → 429 with `Retry-After`. Request bodies over `MAX_REQUEST_BYTES` (default 16384) → 413, checked on `Content-Length` and while reading. Reason for in-process: one backend instance; no extra dependency.

## 9 Configuration (ES-01)

Environment variables; secrets from `.env` (see `project/secrets.env.example`), none has a default in code.

| Setting | Variable | Default |
|---|---|---|
| Environment name | `APP_ENVIRONMENT` (`production`, `local`, `test`) | `production` (fail safe) |
| Database URL / user / password | `DB_URL`, `DB_USERNAME`, `POSTGRES_PASSWORD` | none |
| SMTP | `SMTP_HOST`, `SMTP_PORT`, `SMTP_TLS`, `SMTP_USERNAME`, `SMTP_PASSWORD` | none; `SMTP_TLS` `true`; username/password optional |
| Sender, conference name | `MAIL_FROM`, `CONFERENCE_NAME` | `no-reply@konferenca.si`, `Conference` |
| Options and consents file | `CONFERENCE_CONFIG_FILE` | none |
| JSON copy directory | `JSON_COPY_DIR` | `/data/registrations` |
| reCAPTCHA | `RECAPTCHA_TEST_MODE`, `RECAPTCHA_SITE_KEY`, `RECAPTCHA_SECRET_KEY`, `RECAPTCHA_VERIFY_URL` | `false`, none, none, Google siteverify URL |
| Organizer | `ORGANIZER_USERNAME`, `ORGANIZER_PASSWORD`, `ORGANIZER_EMAILS`, `ORGANIZER_HTTPS_ONLY` | none, none, none, `true` |
| CORS | `ALLOWED_ORIGINS` (comma separated) | empty (same origin only); refused in production |
| Limits | `RATE_LIMIT_REGISTRATIONS_PER_MINUTE`, `RATE_LIMIT_EXPORTS_PER_MINUTE`, `MAX_REQUEST_BYTES` | 10, 10, 16384 |

Production startup guard (`APP_ENVIRONMENT=production`): test mode off, both reCAPTCHA keys set, HTTPS-only on, `SMTP_TLS` true, `ALLOWED_ORIGINS` empty. Any violation stops startup with a message naming the setting, never its value.

## 10 Error behaviour (ES-07, SB-07)

- Every error response is `application/problem+json` ([api.openapi.yaml](02_contracts/api.openapi.yaml) `Problem`) with a fixed title per status and no exception text, class name, SQL or path.
- Unexpected exceptions → 500 `Internal error`; the log line has the exception and the registration id if known, never request bodies, names or emails.
- Spring Boot's whitelabel error page and `server.error.include-*` are off.
- The frontend shows field errors next to their fields and other errors in one alert ([ui-registration-form.json](02_contracts/ui-registration-form.json)). It never shows a raw response body.

## 11 Security controls

| Control | Implementation |
|---|---|
| SB-02 | Spring Security: export requires `ORGANIZER`; `/api/form-config`, `POST /api/registrations` and `/actuator/health/**` are public; everything else is denied |
| SB-03 | BCrypt in memory, secrets only from the environment |
| SB-04 | TLS by the production reverse proxy; SMTP TLS required in production |
| SB-05 | JPA with bound parameters only; plain-text emails; React escaping; Excel string cells |
| SB-07 | §10 |
| SB-08, SB-09 | `verify.sh` dependency, static and secret scans in phases 0, 2, 6 |
| SB-10 | Backend (Spring Security headers): `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, `Cache-Control: no-store`. Frontend (nginx): CSP `default-src 'self'; script-src 'self' https://www.google.com/recaptcha/ https://www.gstatic.com/recaptcha/; frame-src https://www.google.com/recaptcha/ https://recaptcha.google.com/recaptcha/; style-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'`, plus the same three headers |
| SB-11 | Backend image runs as a non-root user; nginx runs as user `nginx` on port 8080 |
| SB-12, SB-13 | Only the fields of `project/constraints.md` ("Personal data"); retention: D-10 |
| SB-14 | Consent checkboxes unchecked by default; consents stored with timestamp and wording |
| SR-05 | Control characters rejected (§4); recipients parsed as single addresses; plain text bodies |
| SR-07 | Export columns (§7) and email content ([emails.json](02_contracts/emails.json)) are fixed lists |

## 12 Deployment and operations (NFR-02, NFR-04, ES-09)

- `02_output/docker-compose.yml` (project name `registration`): `postgres` (volume `pgdata`), `mailpit` (UI/API `127.0.0.1:8025`), `backend` (`127.0.0.1:8080`, volume `jsoncopies` at `/data/registrations`, config file mounted read-only), `frontend` (`127.0.0.1:8081` → nginx 8080, proxies `/api/` to `backend:8080` as the production proxy does). Local values: `APP_ENVIRONMENT=local`, `RECAPTCHA_TEST_MODE=true`, `ORGANIZER_HTTPS_ONLY=false`, `SMTP_TLS=false`. Secrets come from `.env`.
- Named volumes keep the database and JSON copies across `docker compose down` / `up` (NFR-02).
- Actuator exposes only `health` with liveness and readiness groups at `/actuator/health/{liveness,readiness}`. Readiness includes the database. The compose health checks use them, and the frontend depends on a healthy backend (NFR-04). `/actuator` is not under `/api`, so the production proxy does not publish it.
- Images: backend multi-stage (build with the Maven wrapper, run on `eclipse-temurin` JRE alpine); frontend multi-stage (`node` build, `nginx` runtime). Both use the `project/stack.md` pins.
- End-to-end tests run in the Playwright container on the compose network against `http://frontend:8080` and Mailpit `http://mailpit:8025`.

## 13 Frontend

React single page: type selector, the fields of the selected type, option groups filtered to active options offered to the type, consents, captcha (live or test) and Register button. Client-side checks before sending (NFR-03); while sending, the button is disabled. On 201 the form is replaced by the confirmation; on an error the form stays with the entered values and the errors. Accessible names follow [ui-registration-form.json](02_contracts/ui-registration-form.json). There is no secret in the bundle; the site key comes from the API at runtime (AR-07).

## 14 Choices the inputs leave open

| Choice | Reason |
|---|---|
| One endpoint for both types with a `type` discriminator | the rules are shared; one validation path |
| Emails asynchronous after commit | the response does not wait for SMTP; storage comes first (priority 1) |
| JSON copy written inside the database transaction, before commit | gives the "both or neither" behaviour of AR-05 without a second store |
| Option names and consent wording are copied into each registration | configuration may change later (AR-04); the export and the copy show what the participant saw |
| UUID registration ids | not guessable; safe in file names |
| Field length limits (§4) | bound storage and request size; generous for real names |
| English UI and email texts, UTF-8 everywhere | requirements and contracts are in English; Slovenian data is preserved (NFR-01) |
| Basic authentication for the export | §7; Semgrep High lowered to Low by the human (D-11) |

## Traceability

| AC / SR / SB / NFR / AR | Section |
|---|---|
| AC-001-01, AC-002-01 | §13, [ui-registration-form.json](02_contracts/ui-registration-form.json) |
| AC-001-02, AC-002-02 | §3, [api.openapi.yaml](02_contracts/api.openapi.yaml) |
| AC-001-03, AC-001-04, AC-001-05, AC-001-06, AC-002-03, AC-002-04 | §4, §13 |
| AC-001-07, AC-002-05 | §4, [db-schema.sql](02_contracts/db-schema.sql) |
| AC-001-08, AC-001-09, AC-001-10, AC-001-11, AC-002-06, AC-002-07 | §4, §5 |
| AC-001-12, AC-001-13, AC-002-08 | §4, §11 (SB-14), §13 |
| AC-001-14, AC-002-09 | §6 |
| AC-001-15, AC-002-10 | §3 step 5 |
| AC-003-01, AC-003-02, AC-003-03, AC-003-04, AC-003-05 | §5, [conference-config.schema.json](02_contracts/conference-config.schema.json) |
| AC-004-01, AC-004-02, AC-004-03 | §3, §10, §13 |
| AC-005-01, AC-005-02, AC-005-03, AC-005-04, AC-005-05 | §3 step 6, [db-schema.sql](02_contracts/db-schema.sql), [registration-copy.schema.json](02_contracts/registration-copy.schema.json) |
| AC-006-01, AC-006-02, AC-006-03, AC-006-04 | §3 step 8, [emails.json](02_contracts/emails.json) |
| AC-007-01, AC-007-02, AC-007-03, AC-007-04 | §3 step 8, [emails.json](02_contracts/emails.json) |
| AC-008-01, AC-008-02, AC-008-03, AC-008-04, AC-008-05 | §7 |
| SR-01, SR-02 | §6, §9 |
| SR-03 | §8 |
| SR-04 | §4 |
| SR-05 | §4, §11, [emails.json](02_contracts/emails.json) |
| SR-06 | §7, §9 |
| SR-07 | §7, [emails.json](02_contracts/emails.json) |
| SB-01 | §4 |
| SB-02, SB-03, SB-04, SB-05, SB-07, SB-08, SB-09, SB-10, SB-11, SB-12, SB-13, SB-14 | §11 |
| SB-06 | §8 |
| NFR-01 | §4, §14, [db-schema.sql](02_contracts/db-schema.sql) |
| NFR-02, NFR-04 | §12 |
| NFR-03 | §4, §13 |
| AR-01 | §1, §12 |
| AR-02, AR-03 | §2 |
| AR-04 | §5 |
| AR-05 | §3 step 6 |
| AR-06 | [db-schema.sql](02_contracts/db-schema.sql) (Flyway V1 copy) |
| AR-07 | §6, §13 |
