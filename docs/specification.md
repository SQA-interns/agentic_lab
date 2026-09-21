# Technical Specification — Conference Registration System

**Phase 2 artefact.** Sources: `USER_STORIES.md`, `PROJECT_CONSTRAINTS.md`, `FORM_SCHEMA.md`, `docs/acceptance-criteria.md`.

This document is the primary technical source of truth for Phase 3 (Implementation). Every section carries traceability
to User Stories (US-xxx) and Acceptance Criteria (AC-xxx). No production code or tests are written in this phase.

---

## 1. Scope

A web application that lets conference participants register through one of two registration forms (external
participant, student), stores each accepted registration in a relational database and as a JSON file, emails a
confirmation to the participant and a notification with the JSON attached to the organizers, and lets organizers
download the registration list as an Excel workbook.

Out of scope (per `docs/acceptance-criteria.md` § Out of scope): participant accounts, editing/cancelling
registrations, payments, an organizer admin UI beyond the export, multi-conference support, public participant lists.

---

## 2. Architecture

### 2.1 System context

```
                 ┌──────────────────────────────┐
  Participant ──▶│ Frontend (static SPA-less MPA)│
   (browser)     │  nginx : 8080                 │
                 └───────────────┬──────────────┘
                                 │ REST / JSON (HTTPS at the edge)
                                 ▼
                 ┌──────────────────────────────┐        ┌──────────────┐
  Organizer  ───▶│ Backend (Node.js + Express)  │───────▶│ SMTP server  │
   (browser)     │  : 3000                       │        └──────────────┘
                 └───────┬───────────────┬──────┘
                         │               │
                  ┌──────▼─────┐  ┌──────▼──────────────┐
                  │ SQLite DB  │  │ JSON backup files   │
                  │ /data/db   │  │ /data/registrations │
                  └────────────┘  └─────────────────────┘
                        (single persistent volume)
```

### 2.2 Layering

The backend is a four-layer application. Dependencies point strictly downwards; a violation of this rule is an
architecture violation (AC-G-17, checked in Phase 5 with `dependency-cruiser`).

| Layer | Directory | May depend on | Responsibility |
| --- | --- | --- | --- |
| API / delivery | `backend/src/api` | application, domain, config, infrastructure/logging | HTTP routing, request/response mapping, middleware, status codes |
| Application | `backend/src/application` | domain, infrastructure (via ports), config | Use cases, transaction orchestration, side-effect sequencing |
| Domain | `backend/src/domain` | *nothing* (pure) | Registration model, validation rules, normalisation, reference generation, error taxonomy, outbound port interfaces |
| Infrastructure | `backend/src/infrastructure` | domain, config | SQLite access, JSON backup store, mail transport, Excel writing, logging |

Rules:

* `domain` imports nothing from `api`, `application`, `infrastructure` or `config`.
* `infrastructure` never imports `api` or `application`.
* `application` never imports `api`.
* No import cycles anywhere.
* Outbound port interfaces (currently `domain/ports/mailPort.ts`) belong to the domain layer, because both the
  use case that calls a port and the adapter that implements it depend on it. Placing a port in the application
  layer would force infrastructure to depend inwards, which the rules above forbid.

These rules are machine-checked by `dependency-cruiser` (`backend/.dependency-cruiser.cjs`), which also forbids any
module outside `infrastructure/db` from importing the database driver.

**ADR-001 — Layered modular monolith over microservices.** The system has one bounded context, a single write
use case and an expected volume of hundreds of registrations per conference. A layered monolith gives the lowest
operational cost, one deployment unit, and straightforward transactional consistency between the database write and
the JSON backup write (AC-005-04). Microservices would add coordination and distributed-transaction problems with no
benefit at this scale.

### 2.3 Technology choices

| Concern | Choice | Justification |
| --- | --- | --- |
| Runtime | Node.js 24 LTS, TypeScript (strict) | Available in the target environment; one language across frontend and backend; static types support the contract-oriented validation strategy. |
| HTTP framework | Express 5 | Mature, minimal, well-understood middleware model; first-class `supertest` integration for API tests. |
| Validation | Zod | Schema-first, declarative, produces per-field error lists in one pass (AC-G-04); one schema module reused by the route and by tests. |
| Relational DB | SQLite via `better-sqlite3` | See ADR-002. |
| Excel | ExcelJS | Writes real `.xlsx` (OOXML) with correct Unicode handling (AC-008-05) and streaming-free simple API. |
| Email | Nodemailer | De-facto standard SMTP client with pluggable transports, attachment support (AC-007-03) and a JSON transport usable in tests. |
| Security middleware | `helmet`, `cors`, `express-rate-limit` | Standard, audited, covers headers, origin allowlisting and rate limiting (AC-G-10, AC-G-11). |
| Logging | `pino` | Structured JSON logs with a redaction facility for personal data (AC-G-11). |
| Frontend build | Vite, TypeScript, no UI framework | Two static forms and one confirmation view; a framework would add build weight and attack surface without benefit. Vite's multi-page mode gives one HTML entry per registration variant. |
| Styling | Hand-written CSS with a mobile-first layout | Two forms; a CSS framework is unnecessary weight. |

**ADR-002 — SQLite as the relational database.** `PROJECT_CONSTRAINTS.md` requires a relational database, not a
specific engine. The workload is write-light (one insert per registration, a few hundred per conference), single-writer
and read-rare (export). SQLite in WAL mode is a fully relational, ACID, production-grade engine for this profile, is
embedded in the backend container, needs no separate service, and keeps the whole system recoverable from one mounted
volume together with the JSON backups. The repository layer is the only module that speaks SQL, so migrating to
PostgreSQL later means replacing `registrationRepository.ts` and the migration files. Trade-off accepted: the backend
scales vertically only, and horizontal scaling would require replacing the engine.

**ADR-003 — Multi-page frontend rather than a client-side router.** The two variants are separate URLs
(`/` and `/studentska-prijava/`) mirroring the reference applications. Separate HTML entries keep each page small,
allow the confirmation view to be pure DOM, and avoid routing code.

**ADR-004 — Configuration-driven conference options.** Workshops, events, meals and other activities live in a JSON
configuration file outside the source tree, loaded and schema-validated at startup (US-003, AC-003-01, AC-003-06).
The database stores only submitted option identifiers plus the display name at submission time, so changing the
configuration never requires a schema change (AC-003-02) and historical registrations stay meaningful (AC-003-07).

**ADR-005 — Self-contained anti-automation instead of a third-party CAPTCHA.** A third-party CAPTCHA would add an
external runtime dependency, a per-deployment account and a privacy/data-transfer question for participant data.
The specified control combines a signed, short-lived, single-use form token, a minimum human fill time, a honeypot
field and per-IP rate limiting (§ 11). The token verification is isolated in `formTokenService` so a CAPTCHA provider
can be added as an additional check without touching the registration use case.

### 2.4 Repository layout

```
backend/
  src/
    config/          env.ts  optionsConfig.ts  formatIssues.ts
    domain/          registration.ts  normalize.ts  reference.ts  errors.ts
                     schema/registrationSchema.ts  schema/optionsConfigSchema.ts
                     ports/mailPort.ts
    application/     registrationService.ts  exportService.ts  formTokenService.ts
    infrastructure/  db/database.ts  db/migrations.ts  db/registrationRepository.ts
                     backup/jsonBackupStore.ts
                     mail/mailer.ts  mail/templates.ts
                     excel/excelExporter.ts
                     logging/logger.ts
    api/             routes/health.ts  routes/registrationConfig.ts
                     routes/registrations.ts  routes/export.ts
                     middleware/errorHandler.ts  middleware/security.ts
                     middleware/rateLimit.ts  middleware/basicAuth.ts
                     app.ts
    server.ts
  Dockerfile
config/
  conference-options.json
frontend/
  index.html                      (external participant form)
  studentska-prijava/index.html   (student form)
  src/  main.ts  registrationPage.ts  api.ts  validation.ts  dom.ts  styles.css
  Dockerfile  nginx.conf
docker-compose.yml
.env.example
```

---

## 3. Data model

### 3.1 Domain model

```
Registration
  reference            string   stable public identifier, unique
  variant              'external' | 'student'
  firstName            string
  lastName             string
  email                string
  organization         string | null         (external only)
  studyInstitution     string | null         (student only)
  studyProgramme       string | null         (student only)
  studentId            string | null         (student only)
  privacyConsent       true                  (mandatory, never preselected in UI)
  privacyConsentAt     ISO-8601 UTC
  selectedOptions      SelectedOption[]
  createdAt            ISO-8601 UTC

SelectedOption
  optionId             string   identifier from the configuration
  group                'workshops' | 'events' | 'meals' | 'other'
  displayName          string   display name captured at submission time
```

### 3.2 Relational schema (SQLite)

```sql
CREATE TABLE registrations (
  id                   INTEGER PRIMARY KEY AUTOINCREMENT,
  reference            TEXT    NOT NULL UNIQUE,
  variant              TEXT    NOT NULL CHECK (variant IN ('external','student')),
  first_name           TEXT    NOT NULL,
  last_name            TEXT    NOT NULL,
  email                TEXT    NOT NULL,
  organization         TEXT,
  study_institution    TEXT,
  study_programme      TEXT,
  student_id           TEXT,
  privacy_consent      INTEGER NOT NULL CHECK (privacy_consent IN (1)),
  privacy_consent_at   TEXT    NOT NULL,
  created_at           TEXT    NOT NULL,
  json_backup_file     TEXT    NOT NULL
);

CREATE TABLE registration_options (
  registration_id      INTEGER NOT NULL REFERENCES registrations(id) ON DELETE CASCADE,
  option_id            TEXT    NOT NULL,
  option_group         TEXT    NOT NULL,
  display_name         TEXT    NOT NULL,
  position             INTEGER NOT NULL,
  PRIMARY KEY (registration_id, option_id)
);

CREATE INDEX idx_registrations_created_at ON registrations(created_at);

CREATE TABLE schema_migrations (
  version    INTEGER PRIMARY KEY,
  applied_at TEXT NOT NULL
);
```

* `PRAGMA journal_mode = WAL`, `PRAGMA foreign_keys = ON`, `PRAGMA synchronous = FULL` — durability before the success
  response (AC-005-04, AC-005-07).
* The composite primary key on `registration_options` makes duplicate option identifiers within one registration
  impossible at the storage level (AC-003-09); they are additionally de-duplicated during normalisation.
* Only `registrationRepository.ts` contains SQL. All statements are parameterised — no string-built SQL anywhere
  (AC-G-09).
* Migrations are numbered SQL steps applied in a transaction at startup and recorded in `schema_migrations`;
  re-running is a no-op.

### 3.3 JSON backup representation (AC-005-02, AC-005-08)

One file per registration at `${DATA_DIR}/registrations/${reference}.json` (AC-005-03), UTF-8, pretty-printed:

```json
{
  "schemaVersion": 1,
  "reference": "REG-20260921-3KQ7ZB4M2A",
  "variant": "student",
  "createdAt": "2026-09-21T10:44:12.501Z",
  "participant": {
    "firstName": "Ana",
    "lastName": "Novak",
    "email": "ana.novak@example.org",
    "organization": null,
    "studyInstitution": "Univerza v Mariboru",
    "studyProgramme": "Računalništvo in informacijske tehnologije",
    "studentId": "F1234567"
  },
  "selectedOptions": [
    { "optionId": "workshop-ai", "group": "workshops", "displayName": "Delavnica: Uvod v UI" }
  ],
  "consent": { "privacy": true, "grantedAt": "2026-09-21T10:44:12.498Z" },
  "conference": { "name": "Conference 2026" }
}
```

The file is self-contained: variant, all fixed fields, option identifiers **and** their display names at submission
time, consent and timestamp — enough to rebuild the registration list without the database (AC-005-08).

### 3.4 Conference options configuration (US-003)

`config/conference-options.json`, path overridable by `OPTIONS_CONFIG_FILE`:

```json
{
  "conferenceName": "Conference 2026",
  "groups": [
    {
      "id": "workshops",
      "displayName": "Delavnice",
      "options": [
        { "id": "workshop-ai", "displayName": "Delavnica: Uvod v UI", "active": true,
          "availableTo": ["external", "student"] }
      ]
    },
    { "id": "events",  "displayName": "Dogodki",         "options": [] },
    { "id": "meals",   "displayName": "Obroki",          "options": [] },
    { "id": "other",   "displayName": "Ostale aktivnosti","options": [] }
  ]
}
```

* `id`, `displayName`, `active` are mandatory per option (AC-003-01). `availableTo` is optional and defaults to both
  variants; it expresses "activities available to students" (US-002, AC-002-02, AC-002-08).
* Group ids are fixed to the four groups named in `FORM_SCHEMA.md`; the four groups must all be present, options
  inside them are free.
* The file is validated by `optionsConfigSchema` at startup. Duplicate identifiers (across all groups), missing
  fields, unknown group ids or unknown extra keys abort startup with a message naming the problem — the application
  never serves registrations against an invalid configuration (AC-003-06).
* Changing this file changes the offered options and the accepted identifiers with no code, schema or API change
  (AC-003-02).

---

## 4. REST API

Base path `/api`. Content type `application/json; charset=utf-8` except the export. All responses carry
`X-Request-Id`.

### 4.1 `GET /api/health`

`200 { "status": "ok", "uptimeSeconds": number }`. No authentication. Used by the container healthcheck.

### 4.2 `GET /api/registration-config?variant=external|student`

Supplies everything the frontend needs to render a form (AC-003-08).

```json
{
  "conferenceName": "Conference 2026",
  "variant": "student",
  "fields": ["firstName","lastName","email","studyInstitution","studyProgramme","studentId"],
  "fieldRules": {
    "firstName": { "required": true, "maxLength": 100 },
    "email":     { "required": true, "maxLength": 254, "format": "email" },
    "studentId": { "required": true, "maxLength": 50, "pattern": "^[\\p{L}\\p{N}][\\p{L}\\p{N}._\\-/]{1,49}$" }
  },
  "consents": [
    { "id": "privacy", "required": true, "text": "Strinjam se z obdelavo osebnih podatkov …" }
  ],
  "optionGroups": [
    { "id": "workshops", "displayName": "Delavnice",
      "options": [ { "id": "workshop-ai", "displayName": "Delavnica: Uvod v UI" } ] }
  ],
  "formToken": "<base64url payload>.<base64url hmac>",
  "formTokenTtlSeconds": 1800
}
```

* Only options with `active: true` **and** the requested variant in `availableTo` are returned (AC-001-02,
  AC-002-02, AC-003-03, AC-003-04).
* `variant` missing or not one of the two values → `400 VALIDATION_ERROR`.
* Consent entries are returned unchecked; the frontend must render them unchecked (AC-G-08).

### 4.3 `POST /api/registrations`

Request (`application/json`, strict — unknown properties are rejected, AC-G-06):

```json
{
  "variant": "student",
  "firstName": "Ana",
  "lastName": "Novak",
  "email": "ana.novak@example.org",
  "organization": null,
  "studyInstitution": "Univerza v Mariboru",
  "studyProgramme": "Računalništvo",
  "studentId": "F1234567",
  "selectedOptionIds": ["workshop-ai", "meal-lunch"],
  "consents": { "privacy": true },
  "formToken": "…",
  "website": ""
}
```

`website` is the honeypot (§ 11). Variant-inapplicable fields may be omitted, `null` or empty string; any value sent
for them is discarded and never stored (AC-001-13, AC-002-12).

**`201 Created`**

```json
{
  "reference": "REG-20260921-3KQ7ZB4M2A",
  "variant": "student",
  "email": "ana.novak@example.org",
  "createdAt": "2026-09-21T10:44:12.501Z",
  "selectedOptions": [
    { "optionId": "workshop-ai", "group": "workshops", "displayName": "Delavnica: Uvod v UI" }
  ],
  "confirmationEmailQueuedTo": "ana.novak@example.org"
}
```

Returned only after the database row and the JSON file are durably written (AC-005-04); it is the sole trigger for the
frontend confirmation (AC-004-01, AC-004-02, AC-004-03).

**Error responses**

| Status | `error.code` | When |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Any field rule, consent, variant or option-identifier rule violated. `error.fields` lists **every** violation (AC-G-04). |
| 400 | `MALFORMED_REQUEST` | Body absent, not JSON, or not a JSON object (AC-G-06). |
| 400 | `ANTI_AUTOMATION_FAILED` | Honeypot filled, token missing/invalid/expired/replayed, or submitted faster than the minimum fill time (AC-G-10). Deliberately does not say which check failed. |
| 401 | `UNAUTHORIZED` | Export without valid credentials (AC-008-07). |
| 413 | `PAYLOAD_TOO_LARGE` | Body above the configured limit (AC-G-11). |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Content-Type is not JSON. |
| 429 | `RATE_LIMITED` | Rate limit exceeded; includes `Retry-After` (AC-G-10). |
| 500 | `INTERNAL_ERROR` | Unexpected failure. Generic message only — never a stack trace, SQL or file path (AC-G-05, AC-G-11). |

Error envelope:

```json
{ "error": { "code": "VALIDATION_ERROR",
             "message": "The registration could not be accepted because some fields are invalid.",
             "fields": [ { "field": "email", "code": "invalid_email",
                           "message": "Enter a valid email address." } ] },
  "requestId": "…" }
```

`field` uses the request property name; for option problems it is `selectedOptionIds` and `message` names the
offending identifier (AC-003-04, AC-003-05, AC-002-08).

### 4.4 `GET /api/export/registrations.xlsx`

HTTP Basic authentication against `EXPORT_USERNAME` / `EXPORT_PASSWORD`, compared in constant time; failure →
`401` with `WWW-Authenticate: Basic realm="Conference export"` and no body data (AC-008-07).

Success → `200` with
`Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` and
`Content-Disposition: attachment; filename="registrations-<YYYYMMDD-HHmmss>.xlsx"` (AC-008-09).

### 4.5 API conventions

* Only `GET` and `POST` are used; unmatched routes → `404 NOT_FOUND`; wrong method on a known path → `405`.
* No endpoint returns registration data other than the authenticated export (no public participant list).
* Request bodies above `MAX_BODY_BYTES` are rejected before parsing.

---

## 5. Validation

### 5.1 Normalisation (applied before every rule, backend)

1. Reject non-string values for string fields (type error).
2. Unicode `NFC` normalisation — Slovenian characters are preserved and comparable (AC-001-10).
3. Trim leading/trailing whitespace, including Unicode whitespace (AC-001-11).
4. Reject any remaining C0/C1 control character, including CR and LF, in single-line fields — this removes
   header-injection and log-injection vectors at the boundary (AC-G-09, AC-006-05).
5. A value that is empty after trimming is treated as absent (AC-001-11).

### 5.2 Field rules

| Field | Variants | Rule |
| --- | --- | --- |
| `variant` | both | required, `external` or `student` |
| `firstName` | both | required, 1–100 chars |
| `lastName` | both | required, 1–100 chars |
| `email` | both | required, ≤254 chars, matches the email pattern, exactly one `@`, non-empty local and domain parts, domain contains a dot |
| `organization` | external | required, 1–200 chars |
| `studyInstitution` | student | required, 1–200 chars |
| `studyProgramme` | student | required, 1–200 chars |
| `studentId` | student | required, 2–50 chars, `^[\p{L}\p{N}][\p{L}\p{N}._\-/]{1,49}$` (AC-002-10) |
| `selectedOptionIds` | both | optional array of strings, ≤50 entries, each ≤64 chars; duplicates collapsed (AC-003-09) |
| `consents.privacy` | both | required, must be exactly boolean `true` (AC-001-07, AC-002-06) |
| `formToken` | both | required non-empty string |
| `website` | both | must be absent, `null` or empty after trimming (honeypot) |

Any other property in the body → `VALIDATION_ERROR` with `code: "unknown_field"` (AC-G-06).

### 5.3 Option validation (AC-G-07)

For each submitted identifier, in order: it must exist in the configuration, be `active`, and list the submitted
variant in `availableTo`. Failure yields one field error per offending identifier naming that identifier. Validation
runs against the configuration snapshot held in memory, which is loaded at startup.

### 5.4 Error aggregation

Field-shape errors, cross-field variant errors and option errors are collected in a single pass and returned
together (AC-G-04). Zod's `safeParse` issue list is mapped to the `error.fields` array; the mapping is the only place
that converts library errors into the public contract.

### 5.5 Frontend validation (AC-G-03, AC-G-13)

The frontend enforces the same rules — required, max length, email format, student-ID pattern, mandatory consent —
using the `fieldRules` delivered by `/api/registration-config`, so the rules are declared once on the backend. It
validates on blur and on submit, marks invalid inputs with `aria-invalid`, links messages with `aria-describedby`,
and moves focus to the first invalid control. Frontend validation is a usability layer only; the backend repeats every
rule for every request (AC-G-03) and backend field errors are rendered on the corresponding inputs (AC-004-04).

---

## 6. Registration use case (US-001, US-002, US-004, US-005, US-006, US-007)

`registrationService.register(input, context)`:

1. **Anti-automation** — honeypot, form token, minimum fill time (§ 11). Failure → `ANTI_AUTOMATION_FAILED`,
   nothing stored, no email.
2. **Normalise and validate** (§ 5). Failure → `VALIDATION_ERROR`, nothing stored, no email
   (AC-001-09, AC-002-09).
3. **Build the registration** — strip fields not belonging to the variant, resolve each option identifier to its
   group and current display name, generate the reference (§ 6.1), set `createdAt` and `privacyConsentAt`.
4. **Persist atomically** (§ 6.2).
5. **Return** the `201` payload. The HTTP response is sent at this point; storage is already durable.
6. **Dispatch emails after the response**, sequentially and independently: participant confirmation (US-006), then
   organizer notification with the JSON attachment (US-007). Each failure is caught, logged with the reference and
   the transport error, and never affects the already-returned success or the stored data (AC-005-05, AC-006-04,
   AC-007-05).

### 6.1 Registration reference

`REG-<YYYYMMDD>-<10 chars>` where the 10 characters come from a cryptographically random draw over the Crockford
base32 alphabet (`0-9A-Z` without `I`, `L`, `O`, `U`). The value is URL- and filename-safe (AC-005-03), unpredictable
(it must not be guessable, since it is quoted in emails), and unique by construction; the `UNIQUE` constraint is the
authority and a collision retries generation up to 5 times before failing the request.

### 6.2 Atomic persistence (AC-005-04, AC-005-06)

```
BEGIN IMMEDIATE
  INSERT INTO registrations …                -- UNIQUE(reference) enforces exactly-once
  INSERT INTO registration_options …         -- one row per selected option
  write ${DATA_DIR}/registrations/<ref>.json.tmp, fsync, rename to <ref>.json, fsync directory
COMMIT                                        -- on any throw: ROLLBACK and unlink the JSON file
```

The JSON file is written **inside** the transaction and before `COMMIT`, so a failed file write leaves no database
row; a failed `COMMIT` removes the file in the rollback handler. The temp-file-plus-rename sequence means a
`<ref>.json` that exists is always complete. Either both artefacts exist or neither does, and the client is told the
registration failed (`INTERNAL_ERROR`). `BEGIN IMMEDIATE` plus SQLite's single-writer lock serialises concurrent
submissions (AC-005-06).

---

## 7. Email (US-006, US-007)

Transport: Nodemailer, selected by `MAIL_TRANSPORT`:

* `smtp` — SMTP using `SMTP_HOST`, `SMTP_PORT`, `SMTP_SECURE`, optional `SMTP_USER`/`SMTP_PASS`. Required in
  production.
* `json` — messages are not sent; the composed message is captured. Used for local development and tests
  (`MAIL_TRANSPORT=json` is refused when `NODE_ENV=production`).

Both messages are composed in `templates.ts` as `text` plus `html`. Every participant-supplied value is HTML-escaped
in the `html` part, and all fields are already free of CR/LF after normalisation, so neither markup nor additional
headers can be injected (AC-006-05).

**Participant confirmation** (AC-006-01 … AC-006-03) — one message to the submitted address, From `MAIL_FROM`,
subject `Potrditev prijave <reference>`, body: greeting with the participant's name, the reference, the participant
type, the display names of the selected options (or an explicit "none selected"), and the conference name.

**Organizer notification** (AC-007-01 … AC-007-07) — one message to `ORGANIZER_EMAILS` (comma-separated,
configuration only, AC-007-06), `Reply-To` the participant address, subject
`Nova prijava <reference> (<variant>)`, body listing the reference, creation timestamp, participant type, every fixed
field of the variant and the selected options, with the registration JSON attached as
`<reference>.json` (`application/json`). The attachment is produced by reading the stored file back from disk, so it
is byte-identical to the persisted backup (AC-007-03).

Recipient isolation (AC-007-07): the two messages are separate `sendMail` calls. The participant message addresses
only the participant; the organizer message addresses only organizers, with no `Cc`/`Bcc` crossing between them.

---

## 8. Excel export (US-008)

`exportService.buildWorkbook()` reads every registration with its options ordered by `created_at`, then writes one
worksheet `Registrations`:

| Column | Source |
| --- | --- |
| Reference | `reference` |
| Created at (UTC) | `created_at` |
| Participant type | `variant` |
| First name / Last name / Email | fixed fields |
| Organization | external only, empty for students |
| Study institution / Study programme / Student ID | student only, empty for externals |
| Privacy consent | `Yes` |
| Privacy consent at (UTC) | `privacy_consent_at` |
| Workshops / Events / Meals / Other activities | display names of the selected options of that group, newline-separated |

* Header row present and frozen; columns sized; one row per registration (AC-008-02, AC-008-03).
* Every cell is written as an explicit string/number — never a formula. Any string starting with `=`, `+`, `-` or
  `@` is prefixed with an apostrophe so spreadsheet applications treat it as text (AC-008-08, CSV/Excel injection).
* Data is read at request time from the database, so any registration accepted before the request appears
  (AC-008-04).
* No registrations → a workbook with only the header row (AC-008-06).
* Unicode is preserved by the OOXML writer (AC-008-05).

---

## 9. Frontend behaviour (US-001, US-002, US-004)

### 9.1 Pages

`/` external participant form, `/studentska-prijava/` student form, each linking to the other. Each page is one
HTML document with a `<form>` and a hidden confirmation region.

### 9.2 Load

On load the page calls `GET /api/registration-config?variant=…`, then renders the fixed fields for the variant, the
option groups as checkbox lists (groups with no active options are hidden), and the consent checkbox **unchecked**
(AC-G-08). The honeypot input is a text input named `website`, visually hidden with CSS, `tabindex="-1"`,
`autocomplete="off"`, and `aria-hidden="true"` so assistive technology ignores it. The time of a successful config
load is the reference point for the minimum fill time. If the config request fails, the page shows a technical error
and no form.

### 9.3 Submit

Client-side validation runs first; on failure nothing is sent and errors are shown inline. Otherwise the submit
button is disabled and labelled as busy for the duration of the request (AC-004-06) and a JSON body is POSTed to
`/api/registrations`.

| Outcome | Behaviour |
| --- | --- |
| `201` | Replace the form with the confirmation region: "Your registration has been received", the reference, the participant name, the selected options, and "A confirmation email has been sent to `<email>`" (AC-004-01 … AC-004-03). |
| `400 VALIDATION_ERROR` | Show each `error.fields` entry on its input, a summary at the top, focus the first invalid input; no confirmation (AC-004-04). |
| `400 ANTI_AUTOMATION_FAILED` | Show a message asking the participant to reload the form and try again; keep entered data; no confirmation. |
| `429` | Show a "too many attempts, try again later" message; no confirmation. |
| `5xx`, network error, timeout | Show a technical-error message distinct from validation messages, keep all entered data, re-enable the submit button for a retry (AC-004-05). |

The confirmation is rendered only in the `201` branch — there is no optimistic path (AC-004-01).

### 9.4 Responsiveness and accessibility (AC-G-12, AC-G-13)

Mobile-first single-column layout; a two-column field grid appears from 720 px. Targets ≥44 px high, base font
16 px (prevents mobile zoom-on-focus), no fixed pixel widths that force horizontal scrolling at 375 px. Every input
has a `<label for>`; option groups are `<fieldset>` + `<legend>`; errors use `role="alert"` and `aria-describedby`;
the confirmation region has `role="status"` and receives focus; full keyboard operability; visible focus outlines.

---

## 10. Error handling

* Domain and application code throw typed errors (`ValidationError`, `AntiAutomationError`, `NotFoundError`,
  `ConfigurationError`, `PersistenceError`) declared in `domain/errors.ts`.
* One Express error-handling middleware maps error types to the § 4.3 envelope. Anything unrecognised becomes
  `500 INTERNAL_ERROR` with a fixed message; the original error, its stack and the request id are logged server-side
  only (AC-G-05, AC-G-11).
* `process.on('unhandledRejection'|'uncaughtException')` logs and exits non-zero so the container restarts.
* `SIGTERM`/`SIGINT` stop accepting connections, wait for in-flight requests, close the database, then exit.
* Startup failures (missing required configuration, invalid options file, unwritable data directory) log one clear
  message naming the problem and exit with code 1 before the port is opened (AC-003-06, AC-G-15).

---

## 11. Security controls

| Control | Specification | AC |
| --- | --- | --- |
| Transport | The application serves plain HTTP inside the container network and is expected behind a TLS-terminating reverse proxy; `TRUST_PROXY` configures Express to read `X-Forwarded-For` for rate limiting. Documented in the deployment section. | AC-G-11 |
| Security headers | `helmet` on the API: `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, `Cross-Origin-Resource-Policy`, HSTS in production, `X-Powered-By` removed. The frontend's nginx adds a Content-Security-Policy with `default-src 'self'`, no inline scripts, `frame-ancestors 'none'`. | AC-G-11 |
| CORS | `cors` restricted to the exact origins in `CORS_ALLOWED_ORIGINS`; no wildcard; credentials disabled; only `GET`/`POST` and the needed headers. | AC-G-11 |
| Body limit | `express.json({ limit: MAX_BODY_BYTES })`, default 32 KB → `413`. | AC-G-11 |
| SQL injection | Only parameterised statements; no dynamic SQL string building. | AC-G-09 |
| XSS | The frontend renders all dynamic values through `textContent`, never `innerHTML`; the CSP forbids inline and third-party scripts; emails escape every participant value. Stored values are kept verbatim and rendered inertly. | AC-G-09, AC-006-05 |
| Header/log injection | CR/LF and control characters are rejected during normalisation; logs are structured JSON. | AC-G-09 |
| Spreadsheet injection | Formula-leading cell values are prefixed with `'` in the export. | AC-008-08 |
| Mass assignment | Strict schemas reject unknown properties; only the variant's own fields are mapped to the domain object. | AC-G-06, AC-001-13 |
| Export access | HTTP Basic auth with constant-time comparison; export-specific rate limit; `Cache-Control: no-store`. | AC-008-07 |
| Rate limiting | Per client address, asymmetric by design. Write: `POST /api/registrations` 5 / 10 min — the actual anti-abuse control. Reads: `GET /api/registration-config` 300 / 5 min and a global API backstop of 1200 / 15 min — deliberately loose, because one page load costs one configuration request and many legitimate participants share a single public address behind NAT; a read limit tight enough to deter an attacker denies the form to everyone behind that address during a registration rush, which is the worse failure. Organizer export: 10 / 15 min. All four are environment-tunable. Standard `RateLimit-*` headers, `429` + `Retry-After`. | AC-G-10 |
| Anti-automation | (a) **Honeypot** `website` — any non-empty value rejects. (b) **Signed form token**: `base64url(JSON{nonce,variant,issuedAt})` + `.` + `base64url(HMAC-SHA256(payload, FORM_TOKEN_SECRET))`, compared in constant time; rejected when the signature is wrong, the variant differs from the submission, the age exceeds `FORM_TOKEN_TTL_SECONDS` (1800) or is below `FORM_MIN_FILL_SECONDS` (3), or the nonce is already in the single-use replay cache (a TTL-bounded in-memory set, so one token buys one registration). All failures return the same opaque `ANTI_AUTOMATION_FAILED`. | AC-G-10 |
| Personal data in logs | `pino` redaction for `req.body.email`, names, student id and the `authorization` header; application logs identify registrations by reference only. No personal data in URLs or query strings. | AC-G-11 |
| Secrets | Only via environment variables; `.env` is git-ignored; `.env.example` contains placeholders only; the container runs as a non-root user. | AC-G-15 |
| Dependencies | Runtime dependencies limited to the table in § 2.3; `npm audit` is part of verification. | AC-G-11 |

Known limitation to be documented: the replay cache and the rate-limit counters are per-process, so running more
than one backend instance weakens both controls until a shared store is introduced. Recorded as an accepted
limitation for the single-instance deployment specified here.

---

## 12. Configuration

| Variable | Required | Default | Purpose |
| --- | --- | --- | --- |
| `NODE_ENV` | no | `development` | Mode |
| `PORT` | no | `3000` | Backend port |
| `DATA_DIR` | no | `./data` | Root for the database file and JSON backups |
| `DATABASE_FILE` | no | `${DATA_DIR}/registrations.db` | SQLite file |
| `OPTIONS_CONFIG_FILE` | no | `./config/conference-options.json` | Conference options |
| `FORM_TOKEN_SECRET` | **yes** | — | HMAC key for form tokens (≥32 chars) |
| `FORM_TOKEN_TTL_SECONDS` | no | `1800` | Token lifetime |
| `FORM_MIN_FILL_SECONDS` | no | `3` | Minimum human fill time |
| `CORS_ALLOWED_ORIGINS` | **yes in production** | `http://localhost:8080` | Comma-separated origin allowlist |
| `MAX_BODY_BYTES` | no | `32768` | Request body limit |
| `TRUST_PROXY` | no | `false` | Express proxy trust setting |
| `MAIL_TRANSPORT` | no | `json` (`smtp` required in production) | Email transport |
| `SMTP_HOST`/`SMTP_PORT`/`SMTP_SECURE`/`SMTP_USER`/`SMTP_PASS` | yes when `smtp` | — | SMTP connection |
| `MAIL_FROM` | **yes** | — | Sender address |
| `ORGANIZER_EMAILS` | **yes** | — | Comma-separated organizer recipients |
| `EXPORT_USERNAME`/`EXPORT_PASSWORD` | **yes** | — | Export Basic credentials (password ≥12 chars) |
| `CONFERENCE_NAME` | no | from the options file | Name used in emails and the UI |
| `LOG_LEVEL` | no | `info` | pino level |
| `RATE_LIMIT_REGISTRATION_MAX` | no | `5` | Registrations per window per address |
| `RATE_LIMIT_REGISTRATION_WINDOW_MINUTES` | no | `10` | Window for the registration limit |
| `RATE_LIMIT_CONFIG_MAX` | no | `300` | Form-configuration reads per window per address |
| `RATE_LIMIT_CONFIG_WINDOW_MINUTES` | no | `5` | Window for the configuration limit |
| `RATE_LIMIT_GLOBAL_MAX` | no | `1200` | Requests per window per address across the API |
| `RATE_LIMIT_GLOBAL_WINDOW_MINUTES` | no | `15` | Window for the global limit |

`config/env.ts` parses and validates the whole environment with a Zod schema at startup and exposes a frozen typed
object; a missing or invalid required value aborts startup with a message naming the variable (AC-G-15). The frontend
receives its API base URL at build time via `VITE_API_BASE_URL`.

---

## 13. Containerized deployment (AC-G-14, AC-G-15, AC-G-16)

* `backend/Dockerfile` — multi-stage: `node:24-alpine` builder compiles TypeScript and installs production
  dependencies; the runtime stage copies `dist/`, `node_modules/` and `config/`, runs as the non-root `node` user,
  exposes `3000`, and declares a `HEALTHCHECK` against `/api/health`.
* `frontend/Dockerfile` — multi-stage: Node builds the Vite bundle; `nginx:alpine` serves `/usr/share/nginx/html`
  with the security headers and CSP of § 11, gzip, and caching rules; runs on port `8080`.
* `docker-compose.yml` — services `backend` and `frontend`; `frontend` depends on a healthy `backend`; a named
  volume `registration-data` is mounted at `/data` in the backend and holds both the SQLite file and
  `registrations/*.json`, so all persistent state survives container restart and recreation (AC-G-16); environment is
  read from `.env`. `docker compose up --build` starts the whole system (AC-G-14).
* The database is embedded in the backend service (ADR-002); there is no separate database container, and the
  persistent volume is the database.

---

## 14. Testing strategy (to be executed in Phase 4)

Phase 4 will implement, and justify per level:

* **Unit** — domain normalisation, validation rules, reference generation, form-token signing/verification, email
  template escaping, export cell escaping.
* **Component** — repository against a temporary SQLite file; JSON backup store against a temp directory; Excel
  writer output re-read with ExcelJS.
* **Integration** — the registration use case end-to-end inside the process: validation → persistence → JSON →
  captured emails, including the rollback path.
* **REST API / contract** — `supertest` against the Express app for every status code and envelope in § 4.
* **Acceptance** — one test per Acceptance Criterion that is observable through the API or the built artefacts,
  named with its AC id.
* **Negative / edge / security** — malicious input, oversized bodies, unknown fields, inactive and unknown option
  ids, replayed and expired tokens, honeypot, rate limits, export authentication, spreadsheet injection.
* **Failure/recovery** — persistence failure leaves nothing behind; email failure does not fail the registration;
  data survives a simulated restart.
* **End-to-end** — browser-driven form submission and confirmation, plus the responsive checks of AC-G-12, if a
  browser automation runtime can be provisioned in this environment; otherwise the same criteria are verified
  manually in Phase 5 and the limitation is recorded.

Coverage, lint, type-check and `npm audit` results are recorded in Phase 5.

---

## 15. Traceability

| US | Specification sections |
| --- | --- |
| US-001 | 3.1, 3.2, 4.2, 4.3, 5, 6, 9 |
| US-002 | 3.1, 3.2, 3.4, 4.2, 4.3, 5.2, 5.3, 6, 9 |
| US-003 | 2.3 (ADR-004), 3.2, 3.4, 4.2, 5.3 |
| US-004 | 4.3, 6, 9.3, 10 |
| US-005 | 3.2, 3.3, 6.2, 10, 13 |
| US-006 | 6 (step 6), 7 |
| US-007 | 6 (step 6), 7 |
| US-008 | 4.4, 8, 11 |

| AC group | Specification sections |
| --- | --- |
| AC-001-* | 4.2, 4.3, 5, 6, 9 |
| AC-002-* | 4.2, 4.3, 5.2, 5.3, 6, 9 |
| AC-003-* | 3.4, 4.2, 5.3, 10 |
| AC-004-* | 4.3, 9.3, 9.4 |
| AC-005-* | 3.2, 3.3, 6.2, 13 |
| AC-006-* | 7 |
| AC-007-* | 7 |
| AC-008-* | 4.4, 8, 11 |
| AC-G-01 … AC-G-08 | 4, 5, 9 |
| AC-G-09 … AC-G-11 | 11 |
| AC-G-12, AC-G-13 | 9.4 |
| AC-G-14 … AC-G-16 | 12, 13 |
| AC-G-17 | this document, `docs/acceptance-criteria.md`, `docs/verification-report.md`, `RELEASE_NOTES.md`, `experiment/run-log.json` |
