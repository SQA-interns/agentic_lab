# Specification — Conference Registration System

Phase 2 artefact. Inputs: `01_Business/*`, `docs/acceptance-criteria.md`,
`02_Technical/*`. The machine-readable API contract is
`docs/contracts/openapi.yaml`; the two file formats the system reads or
writes are `docs/contracts/conference-options.schema.json` and
`docs/contracts/registration-backup.schema.json`. Where this document
and the contract disagree, the contract is authoritative for the HTTP
surface and this document for everything else.

Escalated ambiguities and their defaults are in `docs/decisions-log.md`
(D-1 … D-6). This specification implements those defaults.

---

## 1. Scope

In scope: public external and student registration forms (US-001,
US-002), file-based configurable options (US-003), in-application
confirmation (US-004), durable storage in PostgreSQL plus a raw JSON
backup with a restore mechanism (US-005), participant confirmation and
organizer notification emails (US-006, US-007), and an organizer-only
Excel export (US-008).

Out of scope (BUSINESS_RULES "Scope boundaries"): participant accounts,
payment, editing registrations, an admin UI for options, an admin
dashboard, identity-provider integration.

## 2. Architecture

Two separately deployed applications talking REST over `/api`
(TECHNICAL_CONSTRAINTS "Client/server communication"):

```
Browser ──HTTPS──▶ external nginx (TLS, Let's Encrypt)
                     ├── /      ─▶ frontend container (nginx serving the React build)
                     └── /api   ─▶ backend container (Spring Boot, :8080)
                                      ├── PostgreSQL 16
                                      ├── SMTP (Mailpit locally, external in prod)
                                      ├── filesystem: JSON backups, options file
                                      └── Google reCAPTCHA siteverify (prod mode only)
```

### 2.1 Backend — layered, package by layer

Root package `si.konferenca.registration`.

| Package | Responsibility | May depend on (application packages) |
| --- | --- | --- |
| `web` | REST controllers, request/response DTOs, mapping DTO ⇄ service command/result, `@RestControllerAdvice` producing Problem Details | `service`, `domain` |
| `service` | Use cases: register, list active options, export, restore; business validation; transaction boundaries; orchestration of persistence, backup and notification | `domain`, `persistence`, `integration` |
| `domain` | JPA entities, enums (`RegistrationType`, `OptionCategory`), value logic (normalization) | none |
| `persistence` | Spring Data JPA repositories | `domain` |
| `integration` | Adapters to the outside: mail sender, JSON backup store, reCAPTCHA verifier, Excel workbook writer, options-file reader | `domain` |
| `config` | Spring configuration, `@ConfigurationProperties`, Spring Security, servlet filters (rate limit, body size) | any (wiring only) |

Rules (verified later with ArchUnit, TECH_STACK "ArchUnit"):

1. `domain` depends on no other application package.
2. `persistence` depends only on `domain`.
3. `integration` depends only on `domain` (and `config` properties
   classes are not referenced — adapters receive values by constructor).
4. `web` never depends on `persistence` or `integration`.
5. `service` never depends on `web` or `config`.
6. No package cycles.
7. Controllers live only in `web`; `@Entity` classes only in `domain`;
   Spring Data repositories only in `persistence`.

Justification: the application is small and its behaviour is a single
write path plus two read paths. A plain layered structure keeps the
security-relevant boundaries obvious (input validation at `service`,
all outbound side effects isolated in `integration`, where they can be
replaced by test doubles), without the ceremony of ports/adapters
interfaces for every collaborator.

### 2.2 Frontend

React + TypeScript + Vite single-page application:

- `src/api/` — typed REST client for the contract (fetch).
- `src/components/` — `RegistrationPage` (type switch), `RegistrationForm`
  (shared form driven by per-type field definitions), `OptionGroups`,
  `ConsentField`, `Captcha`, `Confirmation`, `ErrorSummary`.
- `src/validation/` — client-side validation mirroring §6 (usability
  only; not a security boundary, SECURITY_REQUIREMENTS).

Served in production by the frontend container's nginx, which also sets
the HTML security headers (§8.6).

## 3. Data model

Flyway migration `V1__init.sql` (JPA runs with `ddl-auto: validate`).

### 3.1 `conference_option`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | `varchar(64)` PK | Stable identifier from the options file, pattern `^[a-z0-9][a-z0-9_-]{0,63}$` |
| `category` | `varchar(16)` not null | `WORKSHOP`, `EVENT`, `MEAL`, `OTHER` (CHECK constraint) |
| `name` | `varchar(200)` not null | Display name |
| `active` | `boolean` not null | |
| `sort_order` | `integer` not null | Position in the options file |

Options are never deleted (AC-003-04): an option that disappears from
the options file is set `active = false`.

### 3.2 `registration`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | `uuid` PK | Generated by the backend |
| `type` | `varchar(16)` not null | `EXTERNAL` or `STUDENT` (CHECK) |
| `first_name` | `varchar(100)` not null | |
| `last_name` | `varchar(100)` not null | |
| `email` | `varchar(254)` not null | Stored as entered after trimming (case preserved) |
| `organization` | `varchar(200)` null | Required iff `EXTERNAL` |
| `study_institution` | `varchar(200)` null | Required iff `STUDENT` |
| `study_programme` | `varchar(200)` null | Required iff `STUDENT` |
| `student_id` | `varchar(50)` null | Required iff `STUDENT` |
| `personal_data_consent_at` | `timestamptz` not null | When the mandatory consent (D-2) was given |
| `submitted_at` | `timestamptz` not null | Acceptance time (UTC) |

A CHECK constraint enforces the per-type null/not-null combination, so
the database refuses a half-typed row even if application validation
were bypassed.

### 3.3 `registration_option`

| Column | Type | Notes |
| --- | --- | --- |
| `registration_id` | `uuid` FK → `registration(id)` | |
| `option_id` | `varchar(64)` FK → `conference_option(id)` | |
| PK | (`registration_id`, `option_id`) | |

Option names are shown from `conference_option` at read time; the
identifier is the stable link (AC-003-05).

Database access is only through Spring Data JPA / parameterized queries
(no string-built SQL). The DB user and password come from environment
variables (§9).

## 4. Configurable options (US-003)

Mechanism: a JSON **options file** (schema
`docs/contracts/conference-options.schema.json`) whose path is
`app.options.file` (env `APP_OPTIONS_FILE`). Example:

```json
{
  "options": [
    { "id": "ws-ai", "category": "WORKSHOP", "name": "Delavnica: umetna inteligenca", "active": true },
    { "id": "lunch-day1", "category": "MEAL", "name": "Kosilo, 1. dan", "active": true }
  ]
}
```

Synchronization (`service.OptionCatalogService` + `integration.OptionsFileReader`):

- At startup the file is read, validated against the schema rules
  (unique ids, id pattern, known category, name 1–200 chars after trim,
  `active` boolean) and upserted into `conference_option`: name,
  category, active and `sort_order` (array index) are updated for each
  listed id; every DB option whose id is **not** listed becomes
  inactive. Startup fails if the file is missing or invalid.
- At runtime, before every read of the option catalog (listing options
  and validating a submission), the file's last-modified time and size
  are checked; if either changed, the file is re-read and re-synced as
  above. If the changed file is invalid, the error is logged, the
  previous catalog stays in force, and the file is retried on the next
  change.
- This lets an organizer change the programme by editing one file — no
  code change, rebuild or schema change (AC-003-02, AC-003-03,
  TECHNICAL_CONSTRAINTS "Configuration"). The container mounts the file
  from the host (§10).

Options are available to both registration types (D-1).

## 5. REST API

Full contract: `docs/contracts/openapi.yaml`. Summary:

| Method & path | Auth | Purpose | Success |
| --- | --- | --- | --- |
| `GET /api/options` | public | Active options, ordered by category (WORKSHOP, EVENT, MEAL, OTHER) then `sort_order` | 200 |
| `GET /api/config` | public | Public client configuration: reCAPTCHA site key, test-mode flag | 200 |
| `POST /api/registrations` | public + reCAPTCHA | Submit a registration | 201 |
| `GET /api/organizer/registrations/export` | organizer (HTTP Basic) | Excel export | 200 xlsx |
| `POST /api/organizer/backups/restore` | organizer (HTTP Basic) | Restore registrations from JSON backups | 200 |
| `GET /actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness` | public (not routed by the reverse proxy) | Container health | 200 |

### 5.1 `POST /api/registrations`

Request `application/json` (UTF-8), at most 16 KiB:

```json
{
  "type": "EXTERNAL",
  "firstName": "Živa",
  "lastName": "Čepič",
  "email": "ziva.cepic@example.si",
  "organization": "Univerza v Ljubljani",
  "optionIds": ["ws-ai", "lunch-day1"],
  "personalDataConsent": true,
  "recaptchaToken": "<token>"
}
```

Student registrations use `studyInstitution`, `studyProgramme`,
`studentId` instead of `organization`.

Response `201 Created`:

```json
{
  "registrationId": "7c0e…",
  "submittedAt": "2026-09-29T10:15:30Z",
  "type": "EXTERNAL",
  "firstName": "Živa",
  "lastName": "Čepič",
  "email": "ziva.cepic@example.si",
  "options": [ { "id": "ws-ai", "category": "WORKSHOP", "name": "Delavnica: umetna inteligenca" } ]
}
```

Errors are RFC 9457 Problem Details (`application/problem+json`):

| Status | When | `code` / `errors[].code` |
| --- | --- | --- |
| 400 | Validation failed (one entry per failing field) | `VALIDATION_FAILED`; field codes `REQUIRED`, `INVALID_EMAIL`, `TOO_LONG`, `INVALID_CHARACTERS`, `CONSENT_REQUIRED`, `UNKNOWN_OPTION`, `INACTIVE_OPTION`, `TOO_MANY_OPTIONS`, `FIELD_NOT_ALLOWED`, `INVALID_VALUE` |
| 400 | reCAPTCHA token missing or rejected | `RECAPTCHA_FAILED` (field `recaptchaToken`) |
| 400 | Body is not parseable JSON, or has unknown properties or wrong JSON types | `MALFORMED_REQUEST` |
| 413 | Body larger than 16 KiB | `PAYLOAD_TOO_LARGE` |
| 415 | Content type is not `application/json` | `UNSUPPORTED_MEDIA_TYPE` |
| 429 | Rate limit exceeded (header `Retry-After`) | `RATE_LIMITED` |
| 500 | Registration could not be stored (DB or backup failure) or unexpected error | `REGISTRATION_NOT_SAVED` / `INTERNAL_ERROR` — generic text, no internals |

Validation order: body size → JSON parse → reCAPTCHA → field
validation → option validation. reCAPTCHA is checked before field
validation so an automated client learns nothing about validation
rules without a valid token. All field errors are reported together.

### 5.2 Organizer endpoints

HTTP Basic authentication with a single organizer account configured by
`APP_ORGANIZER_USERNAME` and `APP_ORGANIZER_PASSWORD` (§8.4). Missing or
wrong credentials → `401` with `WWW-Authenticate: Basic realm="organizer"`
and a Problem Details body that contains no registration data
(AC-008-06).

`GET /api/organizer/registrations/export` → `200`,
`Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`,
`Content-Disposition: attachment; filename="registrations-<yyyyMMdd-HHmmss>.xlsx"`,
`Cache-Control: no-store`. Workbook layout in §7.

`POST /api/organizer/backups/restore` (request `Content-Type:
application/json`, body `{}`; the JSON content type forces a CORS
preflight, which blocks cross-site form posts riding on cached Basic
credentials) → `200 {"restored": n, "alreadyPresent": m, "failed": k}`.

## 6. Validation (backend is authoritative)

Applied in `service` after JSON binding. Normalization first:

1. Every string field is stripped of leading/trailing Unicode
   whitespace (`String.strip()`), AC-001-05, AC-002-04.
2. An empty string after stripping is treated as absent (AC-001-03).

Rules:

| Field | Rule | Code |
| --- | --- | --- |
| `type` | Required; `EXTERNAL` or `STUDENT` | `REQUIRED` / `INVALID_VALUE` |
| `firstName`, `lastName` | Required; ≤ 100 chars | `REQUIRED` / `TOO_LONG` |
| `email` | Required; ≤ 254 chars; matches `^[^\s@]+@[^\s@]+\.[^\s@]+$` with exactly one `@` and no leading/trailing dot in the domain | `REQUIRED` / `TOO_LONG` / `INVALID_EMAIL` |
| `organization` | EXTERNAL: required, ≤ 200. STUDENT: must be absent | `REQUIRED` / `TOO_LONG` / `FIELD_NOT_ALLOWED` |
| `studyInstitution`, `studyProgramme` | STUDENT: required, ≤ 200. EXTERNAL: must be absent | same |
| `studentId` | STUDENT: required, ≤ 50. EXTERNAL: must be absent | same |
| all text fields | No control characters (Unicode category Cc, which includes CR/LF/TAB, and Cf format characters are rejected) | `INVALID_CHARACTERS` |
| `personalDataConsent` | Must be JSON `true` | `CONSENT_REQUIRED` |
| `optionIds` | Optional (absent ≡ empty); ≤ 50 entries; duplicates collapsed; each id must exist (`UNKNOWN_OPTION`) and be active (`INACTIVE_OPTION`) at submission time | as listed |

Length limits count Unicode code points. Any Unicode letter is allowed
(AC-001-06). Error entries use the JSON property name as `field`; option
errors use `optionIds[<index>]`.

Jackson is configured to fail on unknown properties and on
null-for-primitive, and with stream read limits (max string length
16 KiB, max nesting depth 10).

## 7. Excel export (US-008)

Generated with Apache POI (`poi-ooxml`, `SXSSFWorkbook` streaming) in
`integration.ExcelExportWriter`. One sheet named `Registrations`.
Row 1 is the header; one row per registration ordered by
`submitted_at`, then `id`.

| # | Header | Content |
| --- | --- | --- |
| A | `Registration ID` | UUID |
| B | `Submitted at (UTC)` | ISO-8601 instant, e.g. `2026-09-29T10:15:30Z` |
| C | `Type` | `External` or `Student` |
| D | `First name` | |
| E | `Last name` | |
| F | `Email` | |
| G | `Organization / institution` | EXTERNAL only, else empty |
| H | `Study institution` | STUDENT only, else empty |
| I | `Study programme` | STUDENT only, else empty |
| J | `Student ID` | STUDENT only, else empty |
| K | `Workshops` | Selected option names in the category, `; `-separated, by `sort_order` |
| L | `Events` | same |
| M | `Meals` | same |
| N | `Other activities` | same |
| O | `Personal data consent at (UTC)` | ISO-8601 instant |

All values are written as string cells (never formulas), so participant
text such as `=HYPERLINK(...)` is shown literally and never evaluated.
With no registrations the workbook contains only the header row
(AC-008-05). Export reads the database at request time (AC-008-03).

## 8. Security controls (SECURITY_REQUIREMENTS; OWASP ASVS 5.0 L1 baseline)

### 8.1 Input handling

- Backend validation of every field (§6) — frontend validation is only
  for usability (§2.2).
- Malicious input: control-character rejection, length limits, strict
  JSON binding (unknown properties rejected), JSON stream limits,
  option identifiers checked against the catalog (never used to build
  paths or queries), parameterized persistence only.
- Request-size limit: a servlet filter reads at most 16 KiB of any
  request body under `/api/`, answering `413` beyond that, regardless of
  whether `Content-Length` is sent. Tomcat `max-http-form-post-size`
  and `max-swallow-size` are also bounded.

### 8.2 Anti-automation — Google reCAPTCHA v2 (TECH_STACK "Anti-automation")

- Frontend: in production mode the reCAPTCHA v2 checkbox widget is
  rendered with the site key from `GET /api/config`; its token is sent
  as `recaptchaToken`. Submitting without a token shows the field
  message "Please confirm you are not a robot." and sends nothing
  *(Phase 3 clarification: the earlier "submit disabled until a token
  exists" wording would have hidden validation messages, contradicting
  AC-004-02)*. In test mode the frontend renders a local checkbox
  labelled "I am not a robot" that yields the token `test-pass`; no
  Google script is loaded.
- Backend (`integration.RecaptchaVerifier`):
  - **Production mode** (`app.recaptcha.test-mode=false`, the default):
    POST `secret`, `response` and `remoteip` form-encoded to
    `app.recaptcha.verify-url` (default
    `https://www.google.com/recaptcha/api/siteverify`, overridable so
    Verification can point it at a mock). Accept only when the reply has
    `success: true`. Timeouts 3 s connect / 5 s read; any error or
    timeout is a failed verification (fail closed).
  - **Test mode** (`app.recaptcha.test-mode=true`, only set by
    environment/test configuration): no network call; the token
    `test-pass` passes, any other value (including blank) fails. This is
    deterministic.
  - **Startup guard:** in production mode, a blank
    `app.recaptcha.secret-key` or `app.recaptcha.site-key` makes the
    application refuse to start (HUMAN_INPUTS_MANIFEST row 1).
  - When test mode is enabled, a WARN line is logged at startup.

### 8.3 Rate limiting

In-memory fixed-window limiter (`config.RateLimitFilter`) keyed by
client IP:

- `POST /api/registrations`: default 30 requests / 60 s per IP
  (`APP_RATE_LIMIT_REGISTRATION_PER_MINUTE`). Deliberately generous so a
  group of participants behind one NAT address (a university network)
  is not locked out; reCAPTCHA is the primary control.
- `/api/organizer/**`: default 10 requests / 60 s per IP
  (`APP_RATE_LIMIT_ORGANIZER_PER_MINUTE`) — brute-force protection for
  Basic credentials.
- Exceeded → `429` with `Retry-After` seconds. The limiter map is
  bounded (oldest windows evicted beyond 10 000 keys).
- Client IP: `request.getRemoteAddr()` with
  `server.forward-headers-strategy=native`, so Tomcat's `RemoteIpValve`
  honours `X-Forwarded-For` only from internal proxy addresses (the
  external nginx on the private container network).

### 8.4 Organizer access control

- Spring Security, stateless (no session), HTTP Basic for
  `/api/organizer/**` only; every other `/api/**` path is public but
  rate limited. CSRF protection is disabled because nothing is
  cookie-authenticated; the one organizer POST requires
  `application/json` (§5.2). No CORS configuration is registered, so
  cross-origin browser calls are refused (frontend and API share an
  origin behind the reverse proxy).
- Credentials come only from `APP_ORGANIZER_USERNAME` /
  `APP_ORGANIZER_PASSWORD`. At startup the password is hashed with
  BCrypt into an in-memory user; the plain value is not kept. Startup
  fails if either is blank or the password is shorter than 16
  characters. No default exists in source.
- Credentials are transported only over HTTPS in production (TLS at the
  reverse proxy, DEPLOYMENT_CONSTRAINTS).

### 8.5 Error handling and logging

- `@RestControllerAdvice` maps every exception to Problem Details;
  stack traces, SQL, class names and exception messages are never
  returned (`server.error.include-*: never`).
- Logs contain the registration id and outcome only — never names,
  emails, student IDs, request bodies or tokens. SQL parameter logging
  is off.

### 8.6 HTTP security headers

- Backend (Spring Security): `X-Content-Type-Options: nosniff`,
  `X-Frame-Options: DENY`, `Cache-Control: no-store`,
  `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`,
  `Referrer-Policy: no-referrer`; HSTS is sent by the external proxy.
- Frontend nginx: `Content-Security-Policy: default-src 'self';
  script-src 'self' https://www.google.com/recaptcha/ https://www.gstatic.com/recaptcha/;
  frame-src https://www.google.com/recaptcha/ https://recaptcha.google.com/recaptcha/;
  style-src 'self'; img-src 'self' data:; connect-src 'self';
  object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'`,
  `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`,
  `Referrer-Policy: strict-origin-when-cross-origin`,
  `Permissions-Policy: camera=(), microphone=(), geolocation=()`.

### 8.7 Output encoding

- React escapes all rendered text; no `dangerouslySetInnerHTML`.
- Emails are `text/plain; charset=UTF-8`, so participant text cannot
  inject markup. Subjects are fixed strings (no participant data) and
  recipient addresses are validated (no CR/LF), so header injection is
  impossible.
- Excel cells are string cells (§7).

### 8.8 Secrets and configuration

All environment-specific values and secrets come from environment
variables (§9). None has a production default in source; the local
compose setup reads them from a git-ignored `.env` file.

### 8.9 Dependency vulnerabilities

OWASP Dependency-Check (backend) and `npm audit` (frontend) run during
Verification; findings are triaged by `SEVERITY_TAXONOMY.md`.

## 9. Configuration (environment variables)

Spring property names (column 2) were added in Phase 3 so the
acceptance harness can configure the application; `application.yml`
maps each environment variable to its property explicitly.

| Variable | Spring property | Purpose | Default |
| --- | --- | --- | --- |
| `SPRING_DATASOURCE_URL`/`_USERNAME`/`_PASSWORD` | `spring.datasource.*` | Database | URL `jdbc:postgresql://localhost:5432/conference`; username/password none — required |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE` | `spring.mail.*` | SMTP | `localhost:1025`, no auth |
| `APP_MAIL_FROM` | `app.mail.from` | Sender address | `registration@localhost` |
| `APP_ORGANIZER_EMAILS` | `app.organizer.emails` | Comma-separated organizer notification recipients | none — required |
| `APP_ORGANIZER_USERNAME`, `APP_ORGANIZER_PASSWORD` | `app.organizer.username`, `app.organizer.password` | Export/restore credentials | none — required |
| `APP_OPTIONS_FILE` | `app.options.file` | Options file path | `./config/conference-options.json` |
| `APP_BACKUP_DIR` | `app.backup.dir` | JSON backup directory | `./data/registrations` |
| `APP_CONFERENCE_NAME` | `app.conference-name` | Used in email bodies | `Conference` |
| `RECAPTCHA_TEST_MODE` | `app.recaptcha.test-mode` | Deterministic test mode | `false` |
| `RECAPTCHA_SITE_KEY`, `RECAPTCHA_SECRET_KEY` | `app.recaptcha.site-key`, `app.recaptcha.secret-key` | reCAPTCHA keys | none — required in production mode |
| `RECAPTCHA_VERIFY_URL` | `app.recaptcha.verify-url` | siteverify endpoint | Google's URL |
| `APP_RATE_LIMIT_REGISTRATION_PER_MINUTE`, `APP_RATE_LIMIT_ORGANIZER_PER_MINUTE` | `app.rate-limit.registration-per-minute`, `app.rate-limit.organizer-per-minute` | Rate limits | 30, 10 |

Startup fails fast on missing required values (Spring
`@Validated @ConfigurationProperties`).

*(Phase 4 note: defaults are applied as lowest-precedence default
properties in `Application.main`, not in `application.yml`; see
`docs/implementation-notes.md` decision 1.)*

## 10. Persistence, backup and recovery (US-005)

Registration write path (`service.RegistrationService.register`):

1. Verify reCAPTCHA, validate and normalize (§6).
2. In one database transaction: insert `registration` and its
   `registration_option` rows; then write the JSON backup file (step 3)
   before commit.
3. JSON backup (`integration.JsonBackupStore`): serialize the
   registration per `docs/contracts/registration-backup.schema.json`
   (UTF-8), write to `<backupDir>/<registrationId>.json.tmp`, `fsync`,
   then atomically move to `<registrationId>.json`. A write failure
   throws, rolling back the transaction → `500 REGISTRATION_NOT_SAVED`,
   no confirmation (AC-004-03, AC-005-02).
4. If the commit itself fails after the backup was written, a
   transaction-completion callback deletes the backup file, so the two
   stores never disagree about an unconfirmed registration.
5. After commit: send emails (§11). Email failures never affect the
   response (D-3, AC-006-04, AC-007-03).
6. Respond `201`.

Both the PostgreSQL data directory and the backup directory are named
Docker volumes, surviving container recreation (DEPLOYMENT_CONSTRAINTS
"Persistence").

Recovery (AC-005-03): `POST /api/organizer/backups/restore` reads every
`*.json` file in the backup directory, validates it against the backup
schema, and inserts each registration whose id is not already in the
database — preserving its id, timestamps, fields and option links.
Options referenced by a backup but absent from `conference_option` are
recreated as inactive options with the name recorded in the backup, so
no registration is dropped. It is idempotent (`alreadyPresent`), sends
no emails, and reports unreadable files as `failed` (without aborting
the others). Documented operator procedure: restore the database volume
(or start with an empty database), keep the backup volume, call the
endpoint.

## 11. Email (US-006, US-007; TECHNICAL_CONSTRAINTS "Email")

Sent through Spring Mail (`integration.MailNotifier`) after commit,
synchronously, each message independently; SMTP connect/read/write
timeouts 5 s. Failures are logged at WARN with the registration id
only.

**Participant confirmation** — to the registration's email:

- Subject: `Registration confirmation` (fixed).
- `text/plain; charset=UTF-8` body: greeting with first and last name,
  statement that the registration for `<APP_CONFERENCE_NAME>` was
  received, registration id, registration type (`External participant`
  / `Student`), and the selected options grouped by category (or
  `No optional activities selected.`).

**Organizer notification** — to each address in `APP_ORGANIZER_EMAILS`:

- Subject: `New registration <registrationId>` (id only — no personal
  data in headers).
- `text/plain; charset=UTF-8` body: every submitted field (type, names,
  email, type-specific fields, selected options with ids and names,
  submission time).
- Attachment `registration-<registrationId>.json`
  (`application/json`), byte-identical to the JSON backup file.

## 12. Frontend behaviour

- Radio choice between **External participant** and **Student**; the
  form shows only that type's fields (AC-002-08).
- Options from `GET /api/options`, grouped under the headings
  `Workshops`, `Events`, `Meals`, `Other activities` (empty groups
  hidden), each as a checkbox with the display name (AC-001-09,
  AC-003-01).
- Mandatory consent checkbox, unchecked on load (AC-001-08), label:
  "I agree to the processing of my personal data for the purpose of
  conference registration." (D-2).
- Client-side checks mirror §6 and show messages next to fields;
  submission errors from the server (`400`) are mapped to the same
  places; other failures show a general "Registration could not be
  completed. Please try again later." message and no confirmation
  (AC-004-02, AC-004-03).
- On `201` the form is replaced by a confirmation panel with heading
  `Registration received`, the registration id, name, email and selected
  options (AC-004-01). Nothing is shown as confirmed before a `201`
  (TECHNICAL_CONSTRAINTS).
- Accessible labels on every input (`<label for>`), error messages
  linked with `aria-describedby`.

### 12.1 UI contract (Phase 3 clarification, used by `frontend/e2e/`)

The accessible names below are part of the specification so the
end-to-end acceptance tests can address the UI without knowing its
implementation.

| Element | Accessible role / name |
| --- | --- |
| Type choice | radios `External participant`, `Student` (default: `External participant`) |
| Text inputs | labels `First name`, `Last name`, `Email`, `Organization / institution`, `Study institution`, `Study programme`, `Student ID` (only the selected type's fields are rendered) |
| Option sets | `<fieldset data-option-group>` with `<legend>` `Workshops` / `Events` / `Meals` / `Other activities` (role `group`); one checkbox per active option, labelled with its display name |
| Consent | checkbox labelled with the §12 consent text |
| reCAPTCHA (test mode) | checkbox `I am not a robot` |
| Submit | button `Register` |
| Confirmation | heading `Registration received`; shows `<first name> <last name>`, the email, the registration id and the selected option names |
| Field error | message text next to the field, input has `aria-invalid="true"`; texts: required → `This field is required.`, email → `Enter a valid email address.`, too long → `This value is too long.`, invalid characters → `This value contains characters that are not allowed.`, consent → `You must agree to the processing of your personal data.`, reCAPTCHA → `Please confirm you are not a robot.`; for other server field codes the server's `message` is shown |
| General failure | element with role `alert` containing `Registration could not be completed. Please try again later.` |

## 13. Error handling summary

| Situation | HTTP | User-visible result |
| --- | --- | --- |
| Invalid input | 400 | Field messages, no confirmation |
| reCAPTCHA failed | 400 | "Please confirm you are not a robot." |
| DB/backup failure | 500 | Generic failure, no confirmation |
| Email failure | 201 | Confirmation (registration stored); failure logged |
| Rate limited | 429 | "Too many attempts, please wait and try again." |
| Organizer auth failure | 401 | No data |

## 14. Health and deployment

- Actuator `health` with liveness/readiness probes (database included
  in readiness); only `health` and `info` exposed. The external proxy
  routes only `/api`, so actuator is reachable only on the container
  network (TECHNICAL_CONSTRAINTS "Runtime health").
- `docker-compose.yml`: `postgres` (16), `smtp` (Mailpit), `backend`
  (health check on `/actuator/health/readiness`, env from `.env`,
  volumes for backups and a read-only bind mount of the options file),
  `frontend` (nginx with §8.6 headers and `/api` proxying to `backend`
  for local use; in production the external nginx does the proxying).
- Containers run HTTP internally; TLS is terminated by the external
  nginx (DEPLOYMENT_CONSTRAINTS).
- The backend image runs as a non-root user.

## 15. Testing approach (overview; details in `docs/test-strategy.md`, Phase 5)

- **Acceptance tests (Phase 3, frozen):**
  `backend/src/test/java/si/konferenca/registration/acceptance/` —
  `@SpringBootTest(webEnvironment = RANDOM_PORT)` driving the real HTTP
  API per the contract, with Testcontainers PostgreSQL 16 and a Mailpit
  container (image as in compose) whose HTTP API is used to read sent
  mail; reCAPTCHA in test mode. `frontend/e2e/` — Playwright tests of
  the UI against the running stack.
- **Unit tests (Phase 5):** JUnit 5 + Mockito for services and
  adapters, Vitest + React Testing Library for components, ArchUnit for
  §2.1.

## 16. Requirement traceability

| Acceptance criteria | Specification sections |
| --- | --- |
| AC-001-01, AC-002-01 | §5.1, §6, §10 |
| AC-001-02, AC-001-03, AC-002-02 | §6 (strip, REQUIRED) |
| AC-001-04, AC-002-03 | §6 (INVALID_EMAIL) |
| AC-001-05, AC-001-06, AC-002-04 | §6 normalization; §3 UTF-8 storage |
| AC-001-07, AC-001-08, AC-002-05 | §6 (CONSENT_REQUIRED), §12 |
| AC-001-09, AC-003-01 | §4, §5 `GET /api/options`, §12 |
| AC-001-10, AC-001-11, AC-002-06 | §6 `optionIds` |
| AC-001-12, AC-001-13, AC-002-07 | §6 UNKNOWN_OPTION / INACTIVE_OPTION |
| AC-002-08 | §6 FIELD_NOT_ALLOWED, §12 |
| AC-003-02, AC-003-03 | §4 runtime re-sync |
| AC-003-04, AC-003-05 | §3.1 never delete, §3.3 FK by id |
| AC-004-01 … AC-004-03 | §5.1, §10, §12, §13 |
| AC-005-01, AC-005-02 | §3, §10 |
| AC-005-03 | §10 restore |
| AC-006-01 … AC-006-04 | §11, §10 step 5 |
| AC-007-01 … AC-007-03 | §11, §10 step 5 |
| AC-008-01 … AC-008-06 | §5.2, §7, §8.4 |

| Technical / security requirement | Section |
| --- | --- |
| REST between frontend and backend | §2, §5 |
| DB + raw JSON persistence | §10 |
| Emails, organizer JSON attachment | §11 |
| Excel export with access control | §5.2, §7, §8.4 |
| Options changeable without schema change | §4 |
| Runtime health | §14 |
| reCAPTCHA v2 frontend + backend, test mode | §8.2 |
| Rate limiting, headers, size limits, errors, secrets, logging, encoding | §8 |
| Persistence survives container recreation | §10, §14 |
| Secrets not hard-coded | §8.8, §9 |
