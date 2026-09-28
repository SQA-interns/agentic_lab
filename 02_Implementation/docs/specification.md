# Specification — Conference Registration System

Phase 2 artefact. Derived from:

- `01_Business/USER_STORIES.md`, `BUSINESS_RULES.md`, `FORM_SCHEMA.md`
- `docs/acceptance-criteria.md` (AC-xxx-yy)
- `02_Technical/TECH_STACK.md`, `TECHNICAL_CONSTRAINTS.md`,
  `SECURITY_REQUIREMENTS.md`, `DEPLOYMENT_CONSTRAINTS.md`

The technology stack is frozen (Java 21, Spring Boot 3.x, Maven Wrapper,
PostgreSQL 16, Flyway, React + TypeScript + Vite, reCAPTCHA v2, Docker
Compose). This document decides everything the inputs leave open:
architecture, API, data model, configuration, persistence, backup,
email, validation, security controls, export, error handling and
deployment.

---

## 1. System overview

```
 Browser (React SPA)
     │  HTTPS (prod: external nginx terminates TLS)
     ▼
 nginx (frontend container) ──/api/*──► Spring Boot backend ──► PostgreSQL 16
     static SPA                             │  │
                                            │  ├──► JSON backup files (volume)
                                            │  └──► SMTP (Mailpit locally)
                                            └──► Google reCAPTCHA siteverify (prod only)
```

- The **frontend** is a single-page React application with two
  registration forms (external participant, student), a confirmation
  view, and client-side validation for usability only.
- The **backend** is a Spring Boot REST service. It is the only
  security boundary: it validates every input, verifies reCAPTCHA,
  persists registrations to PostgreSQL and to a JSON backup file, sends
  emails and produces the organizer Excel export.
- No participant accounts, payment, editing, admin UI or identity
  provider (BR-Scope).

---

## 2. Backend architecture

### 2.1 Style and justification

A **layered architecture with ports** (dependency inversion toward the
application core) inside a single Spring Boot module, base package
`si.konferenca.registration`:

| Package | Responsibility | May depend on (application packages) |
| --- | --- | --- |
| `domain` | JPA entities, enums, value objects, Spring Data repository interfaces | nothing |
| `service` | Use cases (register, list form configuration, export), normalization and business validation, **port interfaces** for external concerns | `domain` |
| `api` | REST controllers, request/response DTOs, mapping DTO → service command, global exception handler | `service`, `domain` |
| `infrastructure` | Adapters implementing service ports: reCAPTCHA, JSON backup, mail, option catalog file loader, Excel writer | `service`, `domain`, `config` |
| `security` | Spring Security configuration, rate-limit filter, request-size filter | `config` |
| `config` | Typed `@ConfigurationProperties`, bean wiring | any |

Justification:

- The domain is small; a layered design is the simplest structure that
  keeps HTTP, business rules and I/O separable and testable.
- Ports in `service` (e.g. `CaptchaVerifier`, `RegistrationBackupStore`,
  `RegistrationNotifier`, `ConferenceOptionCatalog`,
  `RegistrationExportWriter`) let the core be unit-tested with Mockito
  and let the reCAPTCHA test mode be a different adapter selected by
  configuration.
- No dependency cycles between these packages.

These rules are verified with ArchUnit in Phase 4:

1. `domain` does not depend on `service`, `api`, `infrastructure`,
   `security`, `config`.
2. `service` does not depend on `api`, `infrastructure`, `security`,
   `config`.
3. `api` does not depend on `infrastructure`, `security`.
4. `infrastructure` does not depend on `api`, `security`.
5. Controllers (`@RestController`) reside only in `api`.
6. JPA entities (`@Entity`) reside only in `domain`.
7. Package slices are free of cycles.

### 2.2 Main components

| Component | Package | Responsibility |
| --- | --- | --- |
| `RegistrationController` | api | `POST /api/registrations/external`, `POST /api/registrations/student` |
| `FormConfigController` | api | `GET /api/form-config` |
| `ExportController` | api | `GET /api/organizer/registrations.xlsx` |
| `ApiExceptionHandler` | api | Maps exceptions to the error format (§4.5) |
| `RegistrationService` | service | Normalize → validate options/consent → verify captcha → persist + backup (one transaction) → notify after commit |
| `FormConfigService` | service | Active options and consents for the forms |
| `ExportService` | service | Loads all registrations ordered by submission time and delegates to `RegistrationExportWriter` |
| `ConferenceOptionCatalog` (port) | service | Returns configured options; lookup by id |
| `CaptchaVerifier` (port) | service | Verifies a reCAPTCHA token |
| `RegistrationBackupStore` (port) | service | Writes/deletes the JSON backup |
| `RegistrationNotifier` (port) | service | Sends participant confirmation + organizer notification |
| `RegistrationExportWriter` (port) | service | Writes registrations as an XLSX workbook |
| `FileConferenceOptionCatalog` | infrastructure | Loads options from a JSON file (§5) |
| `RecaptchaCaptchaVerifier` / `TestModeCaptchaVerifier` | infrastructure | Production / deterministic test mode (§7.3) |
| `FileSystemRegistrationBackupStore` | infrastructure | Atomic JSON file writes (§6.2) |
| `MailRegistrationNotifier` | infrastructure | Plain-text emails via `JavaMailSender` (§8) |
| `PoiRegistrationExportWriter` | infrastructure | Apache POI XLSX generation (§9) |
| `SecurityConfig`, `RateLimitFilter`, `RequestSizeLimitFilter` | security | §7 |

---

## 3. Data model

### 3.1 Registration types and fixed fields

| Field (JSON) | External | Student | Max length | Source |
| --- | --- | --- | --- | --- |
| `firstName` | required | required | 100 | FS |
| `lastName` | required | required | 100 | FS |
| `email` | required | required | 254 | FS |
| `organization` | required | — | 200 | FS-External |
| `studyInstitution` | — | required | 200 | FS-Student |
| `studyProgramme` | — | required | 200 | FS-Student |
| `studentId` | — | required | 50 | FS-Student |

Fixed fields are defined in code (DTOs, entity, migration) and are not
affected by option configuration (BR-Fields, AC-003-03).

### 3.2 Consent

FORM_SCHEMA requires support for mandatory consent. The system defines
one mandatory consent for both forms:

| id | Text | Mandatory |
| --- | --- | --- |
| `privacy` | "I agree that my personal data is processed for the purpose of organising the conference and managing my registration." | yes |

Consents are served by `GET /api/form-config` (so the form renders them
from data), are rendered unchecked (AC-002-10), and the backend rejects a
registration where a mandatory consent is not `true` (AC-001-08,
AC-002-06). The acceptance timestamp is the registration's `createdAt`.

### 3.3 Conference options

```
ConferenceOption { id: string, name: string, category: WORKSHOP|EVENT|MEAL|OTHER, active: boolean }
```

- `id`: stable identifier, pattern `^[a-z0-9][a-z0-9-]{0,63}$`.
- `name`: display name, 1–200 characters.
- `category`: one of the four configurable sets (FS-Options).

### 3.4 Database schema (Flyway `V1__create_registration_tables.sql`)

```sql
CREATE TABLE registration (
    id                UUID PRIMARY KEY,
    registration_type VARCHAR(16)  NOT NULL CHECK (registration_type IN ('EXTERNAL','STUDENT')),
    first_name        VARCHAR(100) NOT NULL,
    last_name         VARCHAR(100) NOT NULL,
    email             VARCHAR(254) NOT NULL,
    organization      VARCHAR(200),
    study_institution VARCHAR(200),
    study_programme   VARCHAR(200),
    student_id        VARCHAR(50),
    privacy_consent   BOOLEAN      NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT registration_type_fields CHECK (
      (registration_type = 'EXTERNAL' AND organization IS NOT NULL
         AND study_institution IS NULL AND study_programme IS NULL AND student_id IS NULL)
      OR
      (registration_type = 'STUDENT' AND organization IS NULL
         AND study_institution IS NOT NULL AND study_programme IS NOT NULL AND student_id IS NOT NULL)),
    CONSTRAINT registration_privacy_consent CHECK (privacy_consent)
);
CREATE INDEX registration_created_at_idx ON registration (created_at);

CREATE TABLE registration_option (
    registration_id UUID         NOT NULL REFERENCES registration (id) ON DELETE CASCADE,
    option_id       VARCHAR(64)  NOT NULL,
    category        VARCHAR(16)  NOT NULL,
    option_name     VARCHAR(200) NOT NULL,
    PRIMARY KEY (registration_id, option_id)
);
```

- Single-table mapping for both types (type-specific columns nullable,
  enforced by a check constraint). Simple, one query for export.
- `registration_option` stores the stable `option_id` (AC-003-05) plus a
  snapshot of category and display name at submission time, so the
  export remains meaningful if the option is later renamed or removed.
- Hibernate `ddl-auto: validate`; schema only via Flyway.
- The UUID is generated by the application (`UUID.randomUUID()`).

JPA: `Registration` entity with `@ElementCollection` of embeddable
`SelectedOption` (table `registration_option`). `RegistrationRepository
extends JpaRepository<Registration, UUID>` with
`findAllByOrderByCreatedAtAsc()` using an entity graph for options.

---

## 4. REST API

Base path `/api`. JSON uses UTF-8. All request bodies are
`application/json`.

### 4.1 `GET /api/form-config`

Public. Returns what the forms need. Only **active** options are
returned (AC-003-01), grouped by category in display order
WORKSHOP, EVENT, MEAL, OTHER, preserving configuration order within a
category (AC-003-02).

```json
{
  "options": [
    { "id": "ws-ai", "name": "Workshop: AI in practice", "category": "WORKSHOP" }
  ],
  "consents": [
    { "id": "privacy", "text": "…", "mandatory": true }
  ],
  "captcha": { "mode": "TEST" }
}
```

`captcha` is `{ "mode": "TEST" }` in test mode or
`{ "mode": "RECAPTCHA", "siteKey": "<public site key>" }` in production
mode. Response header `Cache-Control: no-store`.

### 4.2 `POST /api/registrations/external`

```json
{
  "firstName": "Ana", "lastName": "Novak", "email": "ana.novak@example.si",
  "organization": "Institut Jožef Stefan",
  "optionIds": ["ws-ai", "meal-lunch-d1"],
  "consents": { "privacy": true },
  "captchaToken": "…"
}
```

### 4.3 `POST /api/registrations/student`

```json
{
  "firstName": "Žiga", "lastName": "Čeh", "email": "ziga.ceh@student.uni-lj.si",
  "studyInstitution": "Univerza v Ljubljani", "studyProgramme": "Računalništvo in informatika",
  "studentId": "63210001",
  "optionIds": [], "consents": { "privacy": true }, "captchaToken": "…"
}
```

`optionIds` may be omitted or empty (AC-001-07); max 50 entries;
duplicates are collapsed.

**Success** — `201 Created`:

```json
{ "registrationId": "4b6f…", "registrationType": "EXTERNAL", "createdAt": "2026-09-28T10:00:00Z" }
```

`201` is returned only after the database transaction committed and the
JSON backup was written (BR-Success, AC-004-01, AC-005-04). Emails are
sent after the commit and do not influence the response (§8.3).

### 4.4 `GET /api/organizer/registrations.xlsx`

Organizer-only (HTTP Basic, §7.4). Returns
`application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
with `Content-Disposition: attachment; filename="registrations-<UTC yyyyMMdd-HHmmss>.xlsx"`
and `Cache-Control: no-store`. Unauthenticated/wrong credentials → `401`
with `WWW-Authenticate: Basic realm="organizer"` and no body data
(AC-008-04).

### 4.5 Error format and status codes

```json
{ "error": "VALIDATION_FAILED", "message": "The registration contains invalid data.",
  "fieldErrors": [ { "field": "email", "code": "INVALID_EMAIL", "message": "Email must be a valid email address." } ] }
```

| Situation | Status | `error` |
| --- | --- | --- |
| Bean validation / business validation failed (incl. unknown/inactive option, missing consent) | 400 | `VALIDATION_FAILED` |
| Malformed JSON, unknown JSON property, wrong type | 400 | `MALFORMED_REQUEST` |
| reCAPTCHA token missing/invalid | 400 | `CAPTCHA_FAILED` |
| Wrong content type | 415 | `UNSUPPORTED_MEDIA_TYPE` |
| Wrong method | 405 | `METHOD_NOT_ALLOWED` |
| Unknown path | 404 | `NOT_FOUND` |
| Body larger than limit | 413 | `PAYLOAD_TOO_LARGE` |
| Rate limit exceeded | 429 | `RATE_LIMITED` (+ `Retry-After`) |
| Unauthenticated organizer request | 401 | (no body) |
| Persistence/backup failure or any unexpected error | 500 | `INTERNAL_ERROR` |

Field error codes: `REQUIRED`, `TOO_LONG`, `INVALID_EMAIL`,
`INVALID_CHARACTERS`, `UNKNOWN_OPTION`, `INACTIVE_OPTION`,
`TOO_MANY_OPTIONS`, `CONSENT_REQUIRED`. Field names equal the JSON
property names (`optionIds`, `consents.privacy`).

Error responses never contain stack traces, exception messages, SQL or
submitted values (secure error handling). Unexpected errors are logged
server-side with the stack trace but without request body data.

---

## 5. Conference option configuration

Mechanism (US-003, TECHNICAL_CONSTRAINTS Configuration): a JSON file
read by `FileConferenceOptionCatalog` at application startup.

- Location: environment variable `APP_OPTIONS_FILE` (a filesystem path).
  If unset, the bundled classpath resource `conference-options.json` is
  used (a sample programme).
- Format:

```json
{ "options": [
  { "id": "ws-ai", "name": "Workshop: AI in practice", "category": "WORKSHOP", "active": true }
] }
```

- Changing the programme = editing the file and restarting the backend.
  No code, schema or fixed-field change is involved (AC-003-03).
- Entries missing `id`, `name`, `category` or `active`, or with an
  invalid id/category/name, are **skipped** with a warning log (entry
  index only) and are never offered or accepted (AC-003-04).
- Duplicate ids are a configuration error: startup fails with a clear
  message.
- A missing/unreadable file set explicitly by `APP_OPTIONS_FILE` fails
  startup (fail fast).

Validation of submitted option ids (SECURITY: "validation of all
configurable-option identifiers"): each id is checked against the
catalog; not found → `UNKNOWN_OPTION` (AC-002-08); found but inactive →
`INACTIVE_OPTION` (AC-002-09). Ids are never used to build file paths or
SQL.

---

## 6. Persistence and backup

### 6.1 Registration flow (`RegistrationService.register`)

1. DTO values arrive already trimmed (§10.1) and bean-validated.
2. Resolve options against the catalog; validate mandatory consent.
3. Verify the reCAPTCHA token (after cheap validation to avoid calling
   Google for invalid forms).
4. In one `@Transactional` method:
   1. build `Registration` (new UUID, `createdAt = now(UTC)`), save and
      flush;
   2. write the JSON backup file.
   If step ii fails, an exception rolls back the transaction →
   `500 INTERNAL_ERROR`, no confirmation (AC-004-03).
   If the commit fails after the file was written, the backup file is
   deleted (best effort, registered via transaction synchronization
   `afterCompletion` with status rolled back).
5. After commit: send emails (§8).
6. Return `201`.

Rejected submissions never reach step 4, so nothing is stored
(AC-005-03).

### 6.2 JSON backup

- Directory: `APP_BACKUP_DIR` (default `./data/registrations`;
  container: `/app/data/registrations` on the `registration_backups`
  volume), created on startup if missing.
- File name: `<createdAt as yyyyMMdd'T'HHmmss'Z'>_<uuid>.json` — built
  only from server-generated values.
- Content: the accepted (normalized) registration: `registrationId`,
  `registrationType`, `createdAt`, all fixed fields of the type,
  `consents`, `options` (`id`, `category`, `name`). The captcha token is
  not stored.
- Written atomically: write to `<name>.tmp` in the same directory, then
  `Files.move(..., ATOMIC_MOVE)`. UTF-8.
- The same JSON bytes are attached to the organizer email (§8).

### 6.3 Durability

PostgreSQL data and backup files live on named Docker volumes
(`postgres_data`, `registration_backups`) and survive container
recreation (AC-005-02, DEPLOYMENT Persistence).

---

## 7. Security controls

### 7.1 Input validation (backend = security boundary)

- Bean Validation on request DTOs (§10.1) + business validation in the
  service (options, consent).
- Jackson `FAIL_ON_UNKNOWN_PROPERTIES = true` (no mass assignment,
  malformed payloads rejected).
- Text fields reject ISO control characters (`INVALID_CHARACTERS`),
  allow any other Unicode (AC-001-10).
- Frontend validation mirrors the rules for usability only.

### 7.2 Protection against malicious input and output encoding

- SQL: only Spring Data JPA / parameterized queries.
- HTML/JS: React escapes all rendered values; no
  `dangerouslySetInnerHTML`. API responses are JSON only.
- Emails are `text/plain`; subjects contain no user input; recipient
  address is the validated email; no header is built from user input
  (safe email generation).
- Excel: values are written as string cells (`CellType.STRING`), never
  as formulas; additionally values beginning with `=`, `+`, `-`, `@`,
  TAB or CR are prefixed with a single quote character `'` to
  neutralize formula injection when data is copied elsewhere.
- File paths: built only from server-generated UUID and timestamp.

### 7.3 Anti-automation (reCAPTCHA v2)

- Frontend: in `RECAPTCHA` mode the SPA loads
  `https://www.google.com/recaptcha/api.js?render=explicit` and renders
  the v2 checkbox with the site key from `/api/form-config`; the token
  is sent as `captchaToken`. In `TEST` mode it renders a deterministic
  local checkbox "I am not a robot (test mode)" that sets the token to
  the constant `test-mode-pass`.
- Backend: `CaptchaVerifier` is chosen by `app.recaptcha.test-mode`
  (env `RECAPTCHA_TEST_MODE`, **default `false`**):
  - `RecaptchaCaptchaVerifier` (production): POST
    `https://www.google.com/recaptcha/api/siteverify` with `secret`,
    `response` and `remoteip`; accepted only if `success == true`.
    Connect/read timeouts 5 s; any error → rejected. Startup fails if
    test mode is off and `RECAPTCHA_SECRET_KEY` is blank.
  - `TestModeCaptchaVerifier`: accepts exactly `test-mode-pass`,
    rejects anything else; never calls Google. Logs a warning at startup
    that test mode is active.
- Missing/blank token → `CAPTCHA_FAILED`.

### 7.4 Organizer access control (export)

- Spring Security HTTP Basic on `/api/organizer/**`, role `ORGANIZER`.
- Credentials from `ORGANIZER_USERNAME` (default `organizer`) and
  `ORGANIZER_PASSWORD` (no default). The password is BCrypt-encoded in
  memory at startup. If `ORGANIZER_PASSWORD` is blank, no organizer user
  exists and every export request is refused (401) — the export is
  never public.
- Stateless (`SessionCreationPolicy.STATELESS`); no cookies are issued.
  CSRF protection is disabled because no cookie- or session-based
  authentication exists (Basic credentials are not sent automatically
  cross-site for fetch/XHR without user interaction and the only
  organizer operation is a read-only GET).
- All other paths: `/api/form-config`, `POST /api/registrations/*`,
  `/actuator/health/**` are public; everything else is denied.

### 7.5 Rate limiting

`RateLimitFilter` (in-memory, fixed window per client IP):

| Scope | Default limit | Property |
| --- | --- | --- |
| `POST /api/registrations/**` | 10 per 10 minutes | `app.rate-limit.registration.*` |
| `/api/organizer/**` | 30 per 10 minutes | `app.rate-limit.organizer.*` |

Exceeding returns `429 RATE_LIMITED` with `Retry-After`. Client IP is
`request.getRemoteAddr()` with `server.forward-headers-strategy: native`
so `X-Forwarded-For` from the trusted reverse proxy is honoured. Stale
windows are evicted. Limits are configurable (tests use large limits).

### 7.6 HTTP security headers

Backend (Spring Security): `X-Content-Type-Options: nosniff`,
`X-Frame-Options: DENY`, `Cache-Control: no-store` (Spring default),
`Referrer-Policy: no-referrer`,
`Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`,
HSTS on secure (forwarded HTTPS) requests.

Frontend nginx: `X-Content-Type-Options`, `X-Frame-Options DENY`,
`Referrer-Policy strict-origin-when-cross-origin`, `Permissions-Policy`,
and CSP:
`default-src 'self'; script-src 'self' https://www.google.com/recaptcha/ https://www.gstatic.com/recaptcha/; frame-src https://www.google.com/recaptcha/ https://recaptcha.google.com/recaptcha/; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'`.
`server_tokens off`.

### 7.7 Request size limits

- `RequestSizeLimitFilter`: requests to `/api/**` with a body larger
  than **16 KiB** (`app.request.max-body-bytes`) → `413`; the body stream
  is additionally wrapped so reading beyond the limit fails even without
  `Content-Length` (chunked).
- Tomcat: `max-http-form-post-size: 16KB`, `max-swallow-size: 64KB`,
  `max-http-request-header-size: 8KB`.
- nginx: `client_max_body_size 16k`.
- DTO limits: field lengths (§3.1), max 50 option ids.

### 7.8 Database access

Dedicated application DB user supplied via environment; no credentials
in source (defaults only point to local development values in
`application.yml` as the scaffold already does, and to environment in
compose). Parameterized JPA queries only; Flyway migrations.

### 7.9 Secrets and configuration

All environment-specific values come from environment variables
(§12). No secret has a non-empty default in source except local
development database defaults inherited from the scaffold. reCAPTCHA
secret and organizer password have no defaults.

### 7.10 Personal-data logging

Logs contain registration id, type and technical errors only — never
names, emails, student ids or request bodies. Mail failures log the
registration id and exception class. Hibernate SQL logging off.

### 7.11 Dependency vulnerabilities

Keep Spring Boot on the latest available 3.x patch release at
implementation time; new dependencies limited to Apache POI
(`poi-ooxml`, Excel) and `spring-boot-starter-security`. OWASP
Dependency-Check and `npm audit` are run in Verification; high/critical
findings in used code paths are fixed by upgrading.

---

## 8. Email

### 8.1 Participant confirmation (AC-006-01)

- To: registration email. From: `APP_MAIL_FROM`.
- Subject: `Conference registration confirmed`.
- Plain-text body: greeting with first name, statement that the
  registration was received, registration reference (UUID), registration
  type, the submitted fixed fields, and the selected options by display
  name.

### 8.2 Organizer notification (AC-007-01)

- To: every address in `APP_ORGANIZER_EMAILS` (comma separated). If
  empty, the notification is skipped with a warning.
- Subject: `New conference registration (<EXTERNAL|STUDENT>)`.
- Plain-text body with all submitted registration data (fixed fields,
  consent, selected options, reference, timestamp).
- Attachment: `registration-<uuid>.json` (`application/json`), the same
  bytes as the backup file (TECHNICAL_CONSTRAINTS Email).

### 8.3 Delivery semantics

- Sent synchronously **after** the transaction commits; only for
  accepted registrations (AC-006-02, AC-007-02).
- A mail failure does not change the `201` result — the registration is
  already stored and confirmed; the failure is logged (registration id
  only). Participant and organizer emails are attempted independently.
- SMTP connection/read/write timeouts 5 s/10 s/10 s.
- SMTP host/port/username/password/STARTTLS configured by
  `SPRING_MAIL_*` environment variables (local: Mailpit).

---

## 9. Excel export (US-008)

- Apache POI `SXSSFWorkbook`, one sheet `Registrations`, bold header
  row, frozen header, auto-filter.
- One row per registration ordered by `createdAt` ascending.
- Columns: `Registration ID`, `Submitted at (UTC)`, `Type`
  (`External`/`Student`), `First name`, `Last name`, `Email`,
  `Organization / institution`, `Study institution`, `Study programme`,
  `Student ID`, `Privacy consent` (`Yes`), `Workshops`, `Events`,
  `Meals`, `Other activities`. Option cells list
  `<name> [<id>]` separated by `; ` (AC-008-02, AC-003-05).
- Empty list → header row only (AC-008-06).
- Unicode preserved (XLSX is UTF-8 XML) (AC-008-03).
- Generated on each request from the database (current list,
  AC-008-05).

---

## 10. Validation rules

### 10.1 Normalization

Request DTOs are Java records whose canonical constructor trims all
strings (`String.strip()`, Unicode-aware); `null` stays `null`. All
validation runs on trimmed values; trimmed values are stored
(AC-001-09).

### 10.2 Field rules

| Rule | Implementation | Code |
| --- | --- | --- |
| Required (not null, not blank after trim) | `@NotBlank` | `REQUIRED` |
| Max length (§3.1) | `@Size(max)` | `TOO_LONG` |
| Email format | `@Email` + `@Pattern("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")` | `INVALID_EMAIL` |
| No control characters | `@Pattern("^[^\\p{Cntrl}]*$")` | `INVALID_CHARACTERS` |
| Option ids ≤ 50 | `@Size(max=50)` | `TOO_MANY_OPTIONS` |
| Option id known | service | `UNKNOWN_OPTION` |
| Option active | service | `INACTIVE_OPTION` |
| Mandatory consent true | service | `CONSENT_REQUIRED` |
| Captcha token present and valid | service | `CAPTCHA_FAILED` |

Frontend mirrors required/length/email/consent rules; server field
errors are displayed next to the fields; entered values are kept
(AC-004-02).

---

## 11. Frontend

- `App` loads `/api/form-config`, lets the user choose
  **External participant** or **Student** (two forms, AC-001-02,
  AC-002-02), and renders:
  - fixed fields of the selected type with labels from FORM_SCHEMA;
  - option checkboxes grouped in fieldsets *Workshops*, *Events*,
    *Meals*, *Other activities* (empty groups hidden);
  - consent checkboxes (unchecked initially);
  - captcha widget (§7.3);
  - submit button (disabled while submitting).
- On `201`: show the confirmation view "Registration received" with the
  reference id and a note that a confirmation email was sent
  (AC-004-01). Only then.
- On `400`: show field errors inline and a summary; values kept
  (AC-004-02). On `CAPTCHA_FAILED`: reset captcha and show a message.
- On `429`, `5xx` or network error: show "Your registration was not
  completed. Please try again later." — no confirmation (AC-004-03).
- If form configuration cannot be loaded, an error message is shown
  instead of the form.
- Structure: `src/api.ts` (fetch client and types), `src/validation.ts`,
  `src/components/*` (`RegistrationForm`, `OptionGroups`, `Captcha`,
  `Confirmation`). No new runtime dependencies.

---

## 12. Configuration (environment)

| Variable | Default | Purpose |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` | local dev (scaffold) | DB |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT` | `localhost`, `1025` | SMTP |
| `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | empty | SMTP auth (prod) |
| `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH` / `_STARTTLS_ENABLE` | `false` | SMTP (prod) |
| `APP_MAIL_FROM` | `no-reply@conference.local` | Sender |
| `APP_ORGANIZER_EMAILS` | empty | Organizer notification recipients |
| `APP_BACKUP_DIR` | `./data/registrations` | JSON backups |
| `APP_OPTIONS_FILE` | empty → classpath sample | Option configuration |
| `RECAPTCHA_TEST_MODE` | `false` | Deterministic captcha test mode |
| `RECAPTCHA_SECRET_KEY`, `RECAPTCHA_SITE_KEY` | empty | Production reCAPTCHA |
| `ORGANIZER_USERNAME` | `organizer` | Export user |
| `ORGANIZER_PASSWORD` | empty (export disabled) | Export password |
| `APP_RATE_LIMIT_*` | §7.5 | Rate limits |

---

## 13. Runtime health

Spring Boot Actuator: `/actuator/health`, `/actuator/health/liveness`,
`/actuator/health/readiness` exposed (details `never`); readiness
includes the DB. `info` is not exposed. The compose backend service has
a healthcheck on `/actuator/health/readiness`; the frontend depends on a
healthy backend.

---

## 14. Container deployment

- `backend/Dockerfile`: runtime image `eclipse-temurin:21-jre-alpine`,
  copies the jar built by `./mvnw package`, runs as a non-root user,
  owns `/app/data`.
- `frontend/Dockerfile`: Node build stage + `nginx:1.27-alpine` with an
  `nginx.conf` providing SPA fallback, security headers, body size limit
  and `/api/` proxy to `backend:8080` (local compose; in production the
  external nginx forwards `/api`, DEPLOYMENT_CONSTRAINTS).
- `docker-compose.yml`: postgres, Mailpit, backend, frontend; volumes
  `postgres_data`, `registration_backups`; backend env includes
  `RECAPTCHA_TEST_MODE=true`, `APP_ORGANIZER_EMAILS`,
  `ORGANIZER_PASSWORD` (from host env with a local-only fallback) and
  healthchecks. Containers speak HTTP; TLS is terminated by the external
  nginx in production.

---

## 15. Testing approach (outline; detailed in Phase 4)

Backend unit tests (service, validation, adapters), MockMvc web tests,
Testcontainers PostgreSQL integration tests (full registration flow,
backup, export), ArchUnit rules (§2.1); frontend Vitest + React Testing
Library; Playwright E2E against the running stack in captcha test mode.

---

## 16. Requirement traceability

| AC | Design elements |
| --- | --- |
| AC-001-01 | §4.2, §6.1 |
| AC-001-02 | §11, §3.1 |
| AC-001-03, AC-001-04 | §10.2 `@NotBlank`, §10.1, §11 |
| AC-001-05 | §10.2 email rule |
| AC-001-06, AC-001-07 | §4.2 `optionIds`, §5, §3.4 `registration_option` |
| AC-001-08 | §3.2, §10.2 `CONSENT_REQUIRED` |
| AC-001-09 | §10.1 |
| AC-001-10 | §7.1, §3.4 (UTF-8 PostgreSQL), §9 |
| AC-002-01 … AC-002-06 | §4.3, §3.1, §10, §3.2 |
| AC-002-07 | §3.1 (studentId only format-checked) |
| AC-002-08, AC-002-09 | §5 option validation |
| AC-002-10 | §3.2, §11 |
| AC-003-01, AC-003-02 | §4.1 |
| AC-003-03, AC-003-04 | §5 |
| AC-003-05 | §3.4 `option_id` snapshot |
| AC-004-01 | §4.3 success, §11 |
| AC-004-02 | §4.5, §11 |
| AC-004-03 | §6.1 rollback, §11 error view |
| AC-005-01, AC-005-04 | §6.1, §9 |
| AC-005-02 | §6.3 |
| AC-005-03 | §6.1 |
| AC-006-01, AC-006-02 | §8.1, §8.3 |
| AC-007-01, AC-007-02 | §8.2, §8.3 |
| AC-008-01 … AC-008-06 | §4.4, §7.4, §9 |

| Technical / security requirement | Section |
| --- | --- |
| REST communication; confirmation only after backend success | §4, §11 |
| DB + raw JSON persistence | §6 |
| Participant + organizer email, JSON attachment | §8 |
| Excel export with organizer access control | §4.4, §7.4, §9 |
| Configurable options without schema change | §5 |
| Runtime health | §13 |
| Architecture defined and justified | §2 |
| Backend + frontend validation | §7.1, §10, §11 |
| Malicious input, output encoding | §7.2 |
| Automated submissions, reCAPTCHA backend verification, test mode | §7.3 |
| Rate limiting | §7.5 |
| Secure DB access | §7.8 |
| HTTP security headers | §7.6 |
| Request-size limits | §7.7 |
| Secure error handling | §4.5 |
| Secret/configuration management | §7.9, §12 |
| Dependency vulnerabilities | §7.11 |
| Safe email content | §7.2, §8 |
| No unnecessary personal-data logging | §7.10 |
| Option identifier validation | §5 |
| Containers, TLS at external proxy, persistent volumes | §6.3, §14 |
