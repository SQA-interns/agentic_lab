# Specification — Conference Registration System

Phase 2 artefact. Derived from the business inputs (`01_Business/`), the
Acceptance Criteria (`docs/acceptance-criteria.md`) and the technical
inputs (`02_Technical/`). It defines the design that Phase 3 implements.
Nothing here adds business functionality beyond the inputs; where the
inputs leave a decision open ("decided during Specification") the
decision and its justification are stated explicitly and marked
**Decision**.

---

## 1. Scope

In scope: external and student registration through a public web form,
configurable conference options, in-application confirmation, dual
persistence (PostgreSQL + raw JSON backup file), participant confirmation
email, organizer notification email with raw JSON attachment, organizer
Excel export behind access control, runtime health, container deployment.

Out of scope (BR-Scope, US-001..003): participant accounts, payment,
editing submitted registrations, an administrative configuration UI, a
full administrative dashboard, identity-provider integration, external
verification of student status.

---

## 2. Architecture

### 2.1 System context

```
Browser ──HTTPS──> external nginx (prod: TLS, Let's Encrypt, routing)
                     ├── /      → frontend container (nginx, static React build)
                     └── /api/* → backend container (Spring Boot, HTTP :8080)
backend ──JDBC──> PostgreSQL 16 container (volume: postgres_data)
backend ──FS────> /app/data/registrations (volume: registration_backups)
backend ──SMTP──> local SMTP (dev/test: Mailpit) | external SMTP (prod)
backend ──HTTPS─> Google reCAPTCHA siteverify (production mode only)
```

For local Docker Compose the frontend container's nginx also forwards
`/api/` to `backend:8080`, reproducing the production routing without the
external proxy.

### 2.2 Backend architecture — layered, with ports for side effects

**Decision:** a layered architecture with explicit ports for external side
effects. Justification: the domain is small (one aggregate, one write use
case, one read use case). A layered structure keeps it simple, while
putting email, file backup, reCAPTCHA and Excel generation behind
interfaces owned by the application layer keeps the use-case logic
testable with Mockito and prevents the application layer from depending
on infrastructure technology.

Base package `si.konferenca.registration`:

| Package | Responsibility | May depend on |
| --- | --- | --- |
| `web` (incl. sub-packages) | REST controllers, request/response DTOs, exception handling, HTTP filters (rate limit, request size), Spring Security configuration | `application`, `domain`, `config` |
| `application` | Use cases (`RegistrationService`, `ExportService`), input normalisation and validation, conference catalog, **ports** (`CaptchaVerifier`, `RegistrationBackup`, `RegistrationNotifier`, `RegistrationWorkbookWriter`), application exceptions | `domain`, `persistence`, `config` |
| `domain` | JPA entities and value types (`Registration`, `SelectedOption`, `RegistrationType`, `OptionCategory`, `ConferenceOption`, `ConsentDefinition`) | nothing in the other packages |
| `persistence` | Spring Data JPA repository `RegistrationRepository` | `domain` |
| `infrastructure` (incl. sub-packages) | Port implementations: reCAPTCHA client, JSON backup writer, SMTP mailer, Apache POI workbook writer | `application`, `domain`, `config` |
| `config` | `@ConfigurationProperties` records bound from environment/config files | `domain` |

Rules enforced by ArchUnit in Phase 4:

1. The dependency table above (layered architecture check).
2. `web` must not access `persistence` or `infrastructure`.
3. `application` must not access `web` or `infrastructure`.
4. `domain` must not depend on any other application package.
5. No cycles between the top-level packages.
6. Controllers (`@RestController`) reside only in `web`.

### 2.3 Frontend architecture

React + TypeScript + Vite single-page application (`frontend/src`):

| Module | Responsibility |
| --- | --- |
| `api/client.ts` | REST calls (`fetchConference`, `submitRegistration`), typed result mapping of backend error responses |
| `types.ts` | Shared TypeScript types mirroring the REST contract |
| `validation.ts` | Client-side validation mirroring backend rules (usability only, not a security boundary) |
| `components/RegistrationPage.tsx` | Loads conference configuration; holds page state (form / confirmation / load error) |
| `components/RegistrationForm.tsx` | Type selection, fixed fields, options, consents, captcha, submission, error display |
| `components/OptionGroup.tsx` | Checkbox group for one option category |
| `components/Captcha.tsx` | reCAPTCHA v2 widget (production) or deterministic test-mode checkbox |
| `components/Confirmation.tsx` | Success confirmation |
| `App.tsx` | Root component |

No new frontend runtime dependencies. The reCAPTCHA v2 widget is loaded
from Google's script URL at runtime only when not in test mode.

---

## 3. Data model

### 3.1 Conference options (configuration, not database)

`ConferenceOption { id: String, name: String, category: OptionCategory, active: boolean }`

`OptionCategory = WORKSHOP | EVENT | MEAL | OTHER` (FS-Options: Workshops,
Events, Meals, Other optional conference activities).

`ConsentDefinition { id: String, label: String, required: boolean }`
(FS-Consent: "support mandatory consent fields where required").

Identifier format for options and consents: `^[a-z0-9][a-z0-9_-]{0,63}$`.
Identifiers must be unique across all options (and across consents);
the application fails to start otherwise.

### 3.2 Registration (database)

Flyway migration `V1__create_registration_tables.sql`:

`registration`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | `uuid` PK | generated by the application |
| `registration_type` | `varchar(16)` not null | `EXTERNAL` / `STUDENT`, check constraint |
| `first_name` | `varchar(100)` not null | |
| `last_name` | `varchar(100)` not null | |
| `email` | `varchar(254)` not null | |
| `organization` | `varchar(200)` null | external only |
| `study_institution` | `varchar(200)` null | student only |
| `study_programme` | `varchar(200)` null | student only |
| `student_id` | `varchar(50)` null | student only |
| `created_at` | `timestamptz` not null | |

`registration_option` (element collection, snapshot of the option at the
time of registration so that later configuration changes do not alter
stored registrations)

| Column | Type |
| --- | --- |
| `registration_id` | `uuid` FK → `registration.id`, on delete cascade |
| `option_id` | `varchar(64)` not null |
| `option_name` | `varchar(200)` not null |
| `category` | `varchar(16)` not null |

PK (`registration_id`, `option_id`).

`registration_consent`

| Column | Type |
| --- | --- |
| `registration_id` | `uuid` FK → `registration.id`, on delete cascade |
| `consent_id` | `varchar(64)` not null |

PK (`registration_id`, `consent_id`).

Database encoding is UTF-8 (PostgreSQL default in the official image), so
Slovenian and other Unicode characters are stored unchanged (AC-001-09).

Hibernate runs with `ddl-auto: validate`; the schema is owned by Flyway.

### 3.3 Raw JSON representation

The same JSON document is used for the backup file and the organizer
email attachment:

```json
{
  "id": "uuid",
  "createdAt": "2026-01-01T10:00:00Z",
  "type": "EXTERNAL",
  "firstName": "…", "lastName": "…", "email": "…",
  "organization": "…",
  "studyInstitution": null, "studyProgramme": null, "studentId": null,
  "selectedOptions": [ { "id": "…", "name": "…", "category": "WORKSHOP" } ],
  "consents": [ "privacy" ]
}
```

It contains the normalised (trimmed) accepted data. The reCAPTCHA token is
never included.

---

## 4. Configuration

All environment-specific values come from environment variables (with
safe local defaults only where harmless) — no secrets in source code.

| Property | Env variable | Default | Purpose |
| --- | --- | --- | --- |
| `spring.datasource.*` | `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` | scaffold defaults (local dev) | DB access |
| `spring.mail.host/port/username/password` | `SPRING_MAIL_HOST/PORT/USERNAME/PASSWORD` | `localhost:1025` | SMTP |
| `spring.mail.properties.mail.smtp.auth` | `SPRING_MAIL_SMTP_AUTH` | `false` | SMTP auth (prod) |
| `spring.mail.properties.mail.smtp.starttls.enable` | `SPRING_MAIL_SMTP_STARTTLS` | `false` | STARTTLS (prod) |
| `app.mail.from` | `APP_MAIL_FROM` | `registration@localhost` | Sender address |
| `app.mail.organizer-recipients` | `APP_MAIL_ORGANIZER_RECIPIENTS` | `organizer@localhost` | Comma-separated organizer addresses |
| `app.backup.directory` | `APP_BACKUP_DIRECTORY` | `./data/registrations` | JSON backup directory |
| `app.recaptcha.test-mode` | `RECAPTCHA_TEST_MODE` | `false` | Deterministic test mode (never default in prod) |
| `app.recaptcha.secret-key` | `RECAPTCHA_SECRET_KEY` | empty | Required when test mode is off |
| `app.recaptcha.site-key` | `RECAPTCHA_SITE_KEY` | empty | Public site key delivered to the frontend |
| `app.recaptcha.verify-url` | `RECAPTCHA_VERIFY_URL` | `https://www.google.com/recaptcha/api/siteverify` | Verification endpoint |
| `app.organizer.username` | `ORGANIZER_USERNAME` | `organizer` | Export credential |
| `app.organizer.password` | `ORGANIZER_PASSWORD` | empty | Export credential; if empty the export is disabled (always 401) |
| `app.rate-limit.registration.max-requests` | `APP_RATE_LIMIT_MAX_REQUESTS` | `10` | Per client IP per window |
| `app.rate-limit.registration.window` | `APP_RATE_LIMIT_WINDOW` | `PT10M` | Window length |
| `app.request.max-body-bytes` | `APP_REQUEST_MAX_BODY_BYTES` | `16384` | Request-size limit for `/api/**` |
| `app.cors.allowed-origins` | `APP_CORS_ALLOWED_ORIGINS` | empty (same-origin only) | Optional CORS origins |

### 4.1 Conference option configuration mechanism

**Decision:** conference options and consents are defined in a YAML file
`conference.yml`, bound to `app.conference` (`ConferenceProperties`).

- A default file is packaged at `classpath:conference.yml`.
- An external file overrides it: `spring.config.import` includes
  `optional:file:${APP_CONFERENCE_CONFIG:./config/conference.yml}`; in the
  container, `/app/config/conference.yml` may be mounted.
- Changing the programme = editing the YAML file and restarting; no code
  change, no admin UI (US-003, AC-003-03). The fixed participant fields
  are code/schema, not configuration, so option changes cannot alter them
  (AC-003-05).

```yaml
app:
  conference:
    options:
      - id: ws-example
        name: Example workshop
        category: WORKSHOP
        active: true
    consents:
      - id: privacy
        label: I agree to the processing of my personal data for the purpose of this conference registration.
        required: true
```

The packaged file contains example options in all four categories,
including one inactive option, and one required privacy consent. These
are sample configuration values only, to be replaced by the organizer.

Startup validation (fail fast): identifiers match the identifier format
and are unique; names/labels non-blank; every option has a category.

---

## 5. REST API

Base path `/api`. JSON bodies UTF-8. Unknown JSON properties are rejected.

### 5.1 `GET /api/conference`

Public. Returns the form configuration.

```json
{
  "options": [ { "id": "ws-example", "name": "Example workshop", "category": "WORKSHOP" } ],
  "consents": [ { "id": "privacy", "label": "…", "required": true } ],
  "recaptcha": { "siteKey": "…", "testMode": false }
}
```

Only **active** options are returned (AC-003-01, AC-003-02), in
configuration order.

### 5.2 `POST /api/registrations`

Public, rate limited, size limited, reCAPTCHA protected.

Request:

```json
{
  "type": "EXTERNAL | STUDENT",
  "firstName": "…", "lastName": "…", "email": "…",
  "organization": "…",
  "studyInstitution": "…", "studyProgramme": "…", "studentId": "…",
  "optionIds": ["…"],
  "consentIds": ["…"],
  "recaptchaToken": "…"
}
```

Responses:

| Status | Body `code` | When |
| --- | --- | --- |
| `201 Created` | — body `{ "registrationId": "uuid", "type": "…" }` | Registration accepted, stored in DB and JSON backup |
| `400` | `VALIDATION_FAILED` with `fieldErrors` | Any validation rule of §6 violated |
| `400` | `MALFORMED_REQUEST` | Unparseable JSON, unknown property, wrong JSON type, unknown `type` value |
| `400` | `CAPTCHA_FAILED` | reCAPTCHA token missing or verification failed |
| `413` | `PAYLOAD_TOO_LARGE` | Body exceeds `app.request.max-body-bytes` |
| `415` | `UNSUPPORTED_MEDIA_TYPE` | Not `application/json` |
| `429` | `RATE_LIMITED` | Rate limit exceeded (with `Retry-After` header) |
| `500` | `REGISTRATION_FAILED` | Storage (DB or JSON backup) failed; nothing is confirmed |
| `503` | `CAPTCHA_UNAVAILABLE` | Production reCAPTCHA service unreachable |

Error body shape (all errors):

```json
{ "code": "VALIDATION_FAILED", "message": "Human-readable summary",
  "fieldErrors": [ { "field": "email", "message": "Enter a valid email address." } ] }
```

`fieldErrors` is empty for non-validation errors. Messages never contain
stack traces, SQL, internal paths or echoed input.

### 5.3 `GET /api/admin/registrations/export`

Organizer only (§8.3). Returns
`application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` with
`Content-Disposition: attachment; filename="registrations-<yyyyMMdd-HHmmss>.xlsx"`
and `Cache-Control: no-store`. `401` with `WWW-Authenticate: Basic` when
unauthenticated or credentials are wrong.

### 5.4 Health

`GET /actuator/health`, `/actuator/health/liveness`,
`/actuator/health/readiness` (Spring Boot Actuator, probes enabled). Only
`health` and `info` are exposed; health details are not shown to
anonymous callers. The readiness group includes the database. The mail
health indicator is disabled so that an SMTP outage does not mark the
registration service unready (registration is still accepted, see §7).

---

## 6. Validation

Performed in `application` by `RegistrationValidator`; the backend is the
security boundary. The frontend mirrors the rules for usability only.

1. **Normalisation first:** all string fields are trimmed
   (`String.strip()`, Unicode-aware) and empty strings become `null`
   (AC-001-06, AC-001-08). Duplicate ids in `optionIds`/`consentIds` are
   collapsed.
2. **Bean Validation** on the normalised command with validation groups
   per registration type:

   | Field | EXTERNAL | STUDENT | Constraints |
   | --- | --- | --- | --- |
   | `firstName`, `lastName` | required | required | ≤ 100 chars |
   | `email` | required | required | ≤ 254 chars, email format |
   | `organization` | required | must be absent | ≤ 200 chars |
   | `studyInstitution` | must be absent | required | ≤ 200 chars |
   | `studyProgramme` | must be absent | required | ≤ 200 chars |
   | `studentId` | must be absent | required | ≤ 50 chars |

   - Email format: `local@domain.tld` — regex
     `^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$`
     (AC-001-07, AC-002-05). No whitespace, exactly one `@`, a dotted
     domain.
   - All text fields: no control characters (`\p{Cntrl}`), which blocks
     header/line injection into emails and export (protection against
     malicious input). Letters of any script are allowed (AC-001-09).
   - Fields of the other registration type must be absent; a non-empty
     value is a field error ("Not applicable to this registration type")
     (AC-002-08).
3. `type` is required; missing → `VALIDATION_FAILED` on `type`.
4. **Options:** at most 50 ids; each id must exist in the configuration
   **and** be active; otherwise a field error on `optionIds`
   ("Contains an unknown or unavailable option.") (AC-001-10, AC-001-11,
   AC-002-06). Selecting no options is valid (AC-001-04).
5. **Consents:** each submitted id must be a configured consent (else a
   field error on `consentIds`); every `required` consent must be present
   (else a field error on `consents.<id>`: "This consent is required.")
   (AC-001-13, AC-002-07).
6. All field errors are collected and returned together.
7. reCAPTCHA is verified **after** input validation succeeds, so invalid
   input does not consume a token check, and **before** anything is
   stored.

---

## 7. Registration use case and persistence/backup behaviour

`RegistrationService.register(request, clientIp)`:

1. Normalise and validate (§6). Failure → `ValidationException` → 400.
2. `CaptchaVerifier.verify(token, clientIp)`; failure → 400
   `CAPTCHA_FAILED`; service unreachable → 503.
3. In **one database transaction** (`TransactionTemplate`):
   a. build `Registration` (UUID, `createdAt = now`, option snapshots);
   b. `saveAndFlush` (DB constraint errors surface here);
   c. `RegistrationBackup.write(json)` — write the raw JSON file.
   If (b) or (c) throws, the transaction is rolled back and the request
   fails with 500 `REGISTRATION_FAILED`; the frontend shows no
   confirmation (AC-004-03, TECHNICAL_CONSTRAINTS "Persistence").
4. After commit: `RegistrationNotifier.registrationAccepted(registration, json)`
   sends the participant confirmation and the organizer notification.
   Email failures are logged (without personal data) and do **not** undo
   the registration, because the registration has already been reliably
   stored (US-005) and the organizer receives all data via export.
5. Return 201.

JSON backup writer (`infrastructure.backup.FileRegistrationBackup`):

- Directory `app.backup.directory`, created at startup if missing.
- File name `<createdAt yyyyMMdd'T'HHmmss'Z'>_<uuid>.json` — the name
  contains no personal data.
- Atomic write: write `<name>.tmp` then `Files.move(ATOMIC_MOVE)`; UTF-8.
- In containers the directory `/app/data/registrations` lives on the
  named volume `registration_backups`, surviving container recreation
  (DEPLOYMENT_CONSTRAINTS "Persistence", AC-005-02, AC-005-04).
- A registration that is not accepted is never written (AC-005-03).

The PostgreSQL data directory lives on the named volume `postgres_data`.

---

## 8. Security controls

| Requirement (SECURITY_REQUIREMENTS) | Control |
| --- | --- |
| Backend validation of all input | §6 in `application`; unknown JSON properties rejected; enum/type errors → 400 |
| Frontend validation for usability | `validation.ts` mirrors §6; server errors are still displayed |
| Protection against malicious input | Length limits, control-character rejection, identifier whitelist against configuration, JPA parameter binding, no dynamic SQL |
| Protection against automated submissions | reCAPTCHA v2 + rate limiting |
| Backend verification of reCAPTCHA | §8.1 |
| Rate limiting | §8.2 |
| Secure relational DB access | Spring Data JPA with bound parameters; credentials via env; dedicated DB user; DB port not required to be public in production |
| Output encoding | React JSX escaping only (no `dangerouslySetInnerHTML`); emails are `text/plain`; Excel values are written as string cells (never formulas); JSON via Jackson |
| HTTP security headers | Backend via Spring Security: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`, `Cache-Control: no-store`. Frontend nginx: CSP allowing self + Google reCAPTCHA hosts, `nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: strict-origin-when-cross-origin`, `Permissions-Policy`. HSTS is added by the external TLS-terminating proxy |
| Request-size limits | `RequestSizeLimitFilter` on `/api/**`: `Content-Length` > limit → 413; bodies without length are read through a bounded stream → 413 on overflow. Frontend nginx `client_max_body_size 16k` |
| Secure error handling | `@RestControllerAdvice` maps all exceptions to the §5.2 body; `server.error.include-stacktrace: never`, `include-message: never`; unexpected exceptions logged with registration id / exception type only |
| Secret/configuration management | §4 env variables; no secrets in source; production requires `RECAPTCHA_SECRET_KEY` and `ORGANIZER_PASSWORD` |
| Dependency vulnerabilities | Frozen OWASP Dependency-Check and npm audit; only one new backend runtime dependency (Apache POI `poi-ooxml`, current release) and Spring Security starter (permitted by TECH_STACK) |
| Safe generation of email content | Plain-text bodies built from validated, control-character-free values; subject contains no user input except the registration type; recipients are validated addresses; the attachment is Jackson-generated JSON |
| No unnecessary personal-data logging | Logs contain registration UUID, type and technical error class only; never names, emails, student IDs or tokens |
| Validation of configurable-option identifiers | Startup validation of configuration (§4.1) and request-time whitelist check (§6.4) |

### 8.1 reCAPTCHA

Port `CaptchaVerifier` with implementation
`infrastructure.captcha.RecaptchaVerifier`:

- **Production mode** (`test-mode=false`, the default): POST
  `secret`, `response`, `remoteip` form-encoded to `verify-url` with
  connect/read timeouts (3 s / 5 s). Accept only if `success == true`.
  Blank token → `CAPTCHA_FAILED` without calling Google. IO failure →
  `CAPTCHA_UNAVAILABLE` (503). If `secret-key` is empty in production
  mode, application startup fails.
- **Test mode** (`RECAPTCHA_TEST_MODE=true`, only via environment, e.g.
  Docker Compose dev and automated tests): no network call; the token is
  accepted if and only if it equals the constant `test-mode-token`;
  any other token fails. A warning is logged at startup.
- Frontend: in production mode renders the Google reCAPTCHA v2 checkbox
  (explicit render with the site key); in test mode renders a checkbox
  "I am not a robot (reCAPTCHA test mode)" that yields `test-mode-token`.
  The submit button requires a captcha token (frontend verification).

### 8.2 Rate limiting

`RegistrationRateLimitFilter` (in `web`) applies to
`POST /api/registrations` only: fixed window per client IP,
`max-requests` per `window` (defaults 10 per 10 minutes). Excess → 429
with `Retry-After`. In-memory map with expired-entry cleanup and a hard
cap of 10 000 tracked clients. Client IP = `request.getRemoteAddr()` with
`server.forward-headers-strategy: native` so that `X-Forwarded-For` from
the trusted internal reverse proxy is honoured by Tomcat's
`RemoteIpValve` (internal proxy addresses only).

### 8.3 Organizer access control for the export

**Decision:** HTTP Basic authentication with a single organizer
credential configured through environment variables, enforced by Spring
Security on `/api/admin/**`. Justification: satisfies the organizer-level
access-control requirement with no accounts, no admin UI and no identity
provider (BR-Organizer, SECURITY_REQUIREMENTS); a browser can download
the export directly by opening the URL; transport confidentiality is
provided by the production HTTPS proxy.

- The password from `ORGANIZER_PASSWORD` is BCrypt-encoded in memory at
  startup (`InMemoryUserDetailsManager`, role `ORGANIZER`).
- If `ORGANIZER_PASSWORD` is empty, no user is registered and every export
  request is refused (fail closed).
- Stateless: no session, CSRF protection disabled (no cookie-based
  authentication exists; the only authenticated endpoint is a safe GET).
- All other `/api/**` endpoints listed in §5 are `permitAll`; any other
  path is denied.

### 8.4 CORS

Same-origin by default (frontend and `/api` share the origin behind the
proxy). If `APP_CORS_ALLOWED_ORIGINS` is set, only those origins are
allowed for `GET`/`POST` on `/api/conference` and `/api/registrations`.

---

## 9. Email behaviour

Port `RegistrationNotifier`, implementation
`infrastructure.mail.SmtpRegistrationNotifier` (Spring `JavaMailSender`,
UTF-8 MIME messages):

**Participant confirmation** (US-006, AC-006-01, AC-006-03)

- To: the registration email. From: `app.mail.from`.
- Subject: `Conference registration confirmation`.
- Plain-text body: greeting with first and last name, registration type
  (External participant / Student), registration id, the fixed fields
  submitted, the selected options grouped by category (or "none"), and a
  note that the email is the record of the registration.

**Organizer notification** (US-007, AC-007-01, AC-007-03,
TECHNICAL_CONSTRAINTS "Email")

- To: every address in `app.mail.organizer-recipients`.
- Subject: `New conference registration (<type label>)`.
- Plain-text body: all submitted registration data (type, all fixed
  fields, options, consents, id, timestamp).
- Attachment: `registration-<uuid>.json`, `application/json`, the raw
  JSON of §3.3.

The two emails are sent independently; failure of one does not prevent
the other. Neither is sent for a rejected registration (AC-006-02,
AC-007-02) because the notifier is invoked only after a successful
commit.

---

## 10. Export

`ExportService.exportWorkbook()` (read-only transaction) loads all
registrations ordered by `createdAt` and passes them to the port
`RegistrationWorkbookWriter`, implemented by
`infrastructure.export.PoiRegistrationWorkbookWriter` (Apache POI XSSF).

Sheet `Registrations`, header row (bold, frozen), one row per
registration (AC-008-01, AC-008-02, AC-008-05):

`Registration ID | Registered at (UTC) | Type | First name | Last name | Email | Organization / institution | Study institution | Study programme | Student ID | Workshops | Events | Meals | Other activities | Consents`

- Type values: `External participant` / `Student`.
- Option columns: display-name snapshots joined with `; ` (AC-008-03).
- Consents: consent ids joined with `; `.
- All values are string cells (no formula evaluation), preserving Unicode
  exactly (AC-008-04).
- Empty database → header row only (AC-008-05).

Access control per §8.3 (AC-008-06, AC-008-07).

---

## 11. Frontend behaviour

- On load: `GET /api/conference`. If it fails, show an error message and
  a retry button; no form is shown.
- Registration type chooser (radio group): "External participant" /
  "Student" (AC-001-01, AC-002-01). Switching type shows the fixed fields
  of that type only (AC-002-08).
- Fixed fields with visible labels, `required`, `maxLength`, `type=email`
  for Email, `autocomplete` hints.
- Options: one fieldset per category that has active options, checkbox per
  option labelled with its display name (AC-003-01).
- Consents: checkbox per configured consent, initially **unchecked**
  (AC-001-12); required consents marked.
- Captcha per §8.1.
- Submit: client validation (§6 mirror) → field messages next to fields,
  no request sent. On `201` → replace the form with the confirmation
  ("Thank you, your registration has been received." plus the note that a
  confirmation email is being sent) (AC-004-01). On `400
  VALIDATION_FAILED` → show server field errors (AC-004-02). On other
  errors → show a general error message, keep the entered data, no
  confirmation (AC-004-03). The submit button is disabled while the
  request is in flight (prevents double submission).
- No account, login, payment or edit functions (AC-001-14).
- Accessibility: labels bound to inputs, error messages linked via
  `aria-describedby`, confirmation and errors announced with
  `role="status"` / `role="alert"`.

---

## 12. Error handling (backend)

`web.ApiExceptionHandler` (`@RestControllerAdvice`):

| Exception | Response |
| --- | --- |
| `ValidationException` (application) | 400 `VALIDATION_FAILED` + field errors |
| `HttpMessageNotReadableException` | 400 `MALFORMED_REQUEST` |
| `CaptchaFailedException` | 400 `CAPTCHA_FAILED` |
| `CaptchaUnavailableException` | 503 `CAPTCHA_UNAVAILABLE` |
| `HttpMediaTypeNotSupportedException` | 415 `UNSUPPORTED_MEDIA_TYPE` |
| `HttpRequestMethodNotSupportedException` | 405 `METHOD_NOT_ALLOWED` |
| `NoResourceFoundException` | 404 `NOT_FOUND` |
| `RegistrationStorageException` / any other `Exception` | 500 `REGISTRATION_FAILED` / `INTERNAL_ERROR`, logged without personal data |

413 and 429 are produced by the filters with the same body shape; 401 by
Spring Security's Basic entry point.

---

## 13. Container deployment

- `docker-compose.yml` (local/dev): `postgres` (healthcheck `pg_isready`),
  `smtp` (Mailpit), `backend`, `frontend`. Backend waits for a healthy
  database and has a healthcheck on `/actuator/health/readiness`.
  Frontend waits for a healthy backend. Environment: test-mode reCAPTCHA,
  Mailpit SMTP, dev organizer credentials supplied via `${VAR:-default}`
  substitution, backup directory `/app/data/registrations` on the
  `registration_backups` volume, database on `postgres_data`.
- Backend image: the scaffold Dockerfile (runs the jar built by
  `./mvnw package`), extended to run as a non-root user and to create
  `/app/data` owned by that user so the named volume is writable.
- Frontend image: scaffold multi-stage build, plus `nginx.conf` serving
  the SPA, forwarding `/api/` to `backend:8080`, adding security headers
  and `client_max_body_size 16k`.
- Production: same images; environment supplies real SMTP settings,
  `RECAPTCHA_TEST_MODE=false`, `RECAPTCHA_SECRET_KEY`, `RECAPTCHA_SITE_KEY`,
  `ORGANIZER_PASSWORD`, organizer recipients and database credentials.
  External nginx terminates TLS and forwards `/api` to the backend.

---

## 14. New dependencies

| Side | Dependency | Scope | Reason |
| --- | --- | --- | --- |
| Backend | `org.springframework.boot:spring-boot-starter-security` | runtime | Access control for export, security headers, CORS (permitted by TECH_STACK) |
| Backend | `org.apache.poi:poi-ooxml` | runtime | Excel (`.xlsx`) workbook generation (US-008) |
| Backend | `org.springframework.security:spring-security-test` | test | Security tests |
| Frontend | none | — | — |

---

## 15. Requirement traceability

| AC | Design elements |
| --- | --- |
| AC-001-01 | §11 type chooser/fixed fields; §6 table |
| AC-001-02 | §5.2, §6, §7 |
| AC-001-03 | §5.1, §6.4, §3.2 `registration_option` |
| AC-001-04 | §6.4 (empty selection valid) |
| AC-001-05 | §6.2 required fields; §5.2 field errors; §11 |
| AC-001-06 | §6.1 normalisation (blank → null) |
| AC-001-07 | §6.2 email format |
| AC-001-08 | §6.1 trimming; stored normalised values |
| AC-001-09 | §6.2 (letters of any script), §3.2 UTF-8, §3.3, §10 |
| AC-001-10 | §6.4 |
| AC-001-11 | §6.4 active check |
| AC-001-12 | §11 consents unchecked |
| AC-001-13 | §6.5 |
| AC-001-14 | §1 scope, §11 |
| AC-002-01 | §11, §6 table |
| AC-002-02 | §5.2, §6, §7 |
| AC-002-03 | §6.4, §3.2 |
| AC-002-04 | §6.1, §6.2 |
| AC-002-05 | §6.2 |
| AC-002-06 | §6.4 |
| AC-002-07 | §6.5 |
| AC-002-08 | §6.2 "must be absent", §11 type switching |
| AC-002-09 | §6 (Student ID only required non-blank), §1 |
| AC-003-01 | §4.1, §5.1, §11 |
| AC-003-02 | §5.1 active only, §6.4 |
| AC-003-03 | §4.1 file-based configuration + restart |
| AC-003-04 | §3.1, §4.1 startup validation |
| AC-003-05 | §4.1 (fixed fields are code/schema) |
| AC-004-01 | §5.2 201, §11 confirmation only after 201 |
| AC-004-02 | §5.2 400, §11 |
| AC-004-03 | §7 step 3 rollback, §11 general error |
| AC-005-01 | §3.2, §7 |
| AC-005-02 | §3.2 PostgreSQL volume, §13 |
| AC-005-03 | §6 before §7 storage |
| AC-005-04 | §3.3, §7 JSON backup on volume |
| AC-006-01 | §9 participant confirmation |
| AC-006-02 | §7 step 4, §9 |
| AC-006-03 | §9 body content |
| AC-007-01 | §9 organizer notification |
| AC-007-02 | §7 step 4, §9 |
| AC-007-03 | §9 body + attachment |
| AC-008-01 | §5.3, §10 |
| AC-008-02 | §10 (reads current DB state per request) |
| AC-008-03 | §10 columns |
| AC-008-04 | §10 string cells, §3.2 UTF-8 |
| AC-008-05 | §10 empty export |
| AC-008-06 | §8.3 |
| AC-008-07 | §8.3 |

Technical constraints traceability: REST (§5), confirmation only after
successful backend response (§11), dual persistence (§7), email content
and attachment (§9), Excel export with access control (§8.3, §10),
configurable options (§4.1), runtime health (§5.4, §13), architecture
(§2), security requirements (§8), deployment/persistence volumes and
env-based configuration (§4, §13).
