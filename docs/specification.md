# Conference Registration System — Technical Specification

## 1. Scope and source of truth

This specification implements the eight User Stories through AC-001–AC-050 while preserving the fixed field schema in `FORM_SCHEMA.md`. It intentionally excludes payment, attendee accounts, organizer data editing, badge generation, workshop capacity/waitlists, and a configuration administration UI because none is requested.

The application is a small production-oriented web system with two public form variants, a versioned JSON REST API, relational persistence, durable JSON backups, durable email jobs, and an authenticated Excel export.

## 2. Architecture and technology

### 2.1 Runtime architecture

The deployable system has four processes/resources:

1. **Web application** — Python 3.12, FastAPI, Pydantic, SQLAlchemy, and Uvicorn. It serves the static HTML/CSS/JavaScript frontend and `/api/v1` REST endpoints from one origin.
2. **Email worker** — the same application image running a worker entry point. It claims durable outbox rows and sends participant and organizer messages through SMTP.
3. **Relational database** — PostgreSQL 16 in the supplied container composition. SQLite may be used only by fast local checks and isolated tests; PostgreSQL is the deployment database.
4. **Persistent backup storage** — a mounted filesystem directory shared read/write by web and worker processes. JSON is written atomically and read by the organizer-email worker.

Serving frontend and API from the same origin reduces cross-origin exposure and makes origin enforcement understandable. The web process remains stateless except for database and backup-volume state, so it can be restarted safely. PostgreSQL is chosen for transactions and concurrent access. A database-backed outbox is chosen so SMTP outages never discard notification intent.

### 2.2 Source layout and responsibilities

```text
app/
  api/             HTTP routes, request/response mapping, auth dependencies
  core/            settings, logging, security, rate limiting
  domain/          fixed schemas, normalization, registration service
  infrastructure/  database models/session, repositories, JSON store, SMTP, Excel
  static/          HTML, CSS, browser JavaScript
  main.py          FastAPI composition and middleware
  worker.py        durable email-outbox processor
config/
  conference-options.json
migrations/        Alembic database migrations
docs/
scripts/            operational entry points where needed
```

Dependencies point inward: API and infrastructure depend on domain contracts; the domain does not import FastAPI, SQLAlchemy, SMTP, or workbook code. Route functions contain no persistence logic. The registration service coordinates validation, storage, backup, and outbox creation.

### 2.3 Architecture decisions

| Decision | Rationale | Traces |
| --- | --- | --- |
| One backend serving a framework-free frontend | Keeps deployment and same-origin security simple while still separating browser and REST concerns. | AC-002, AC-007, AC-043, AC-048 |
| PostgreSQL plus atomic filesystem JSON | Meets both mandatory storage forms and supports concurrency/recovery. | AC-019–AC-024 |
| Database outbox with separate worker | Makes email intent durable and retryable without delaying the browser on SMTP. | AC-025–AC-032 |
| Mounted, validated JSON option configuration | Lets organizers change programme choices without changing fixed fields or rebuilding application code. | AC-011–AC-015 |
| Client-generated submission UUID and payload hash | Makes retries idempotent after ambiguous network failures. | AC-017–AC-018, AC-023 |
| Same-origin JSON API plus signed time-bound challenge, honeypot, and database-backed throttling | Provides layered cross-site and automated-submission controls without an external CAPTCHA provider. | AC-041–AC-043 |
| Environment-secret organizer bearer credential | Protects export without adding an unrequested account-management product area. | AC-033, AC-037, AC-045 |

## 3. Configuration

### 3.1 Conference configuration

`config/conference-options.json` is mounted into the container and read through a configuration repository. Operators update it with an atomic file replacement; no source-code change or image rebuild is required. It contains:

- `conferenceName` and `configurationVersion`;
- `options`, each with unique stable `id`, `displayName`, `category` (`workshop`, `event`, `meal`, or `other`), `active`, and applicable `variants` (`external`, `student`, or both);
- `consents`, each with stable `id`, `label`, `policyVersion`, applicable `variants`, and `required`. At least one required privacy/data-processing consent is supplied in the default configuration.

The loader rejects duplicate IDs, blank names, unknown categories/variants, and invalid shapes. It reloads when the file changes. A newly invalid configuration causes form-context and registration endpoints to return 503 and readiness to fail rather than silently serving unintended stale settings. Registration snapshots preserve selected option names/categories and consent policy versions, so later configuration changes do not alter old records.

### 3.2 Environment configuration

Typed settings include database URL, backup directory, public origin, anti-automation HMAC secret, IP-hashing secret, organizer export token, organizer email list, SMTP host/port/credentials/security mode/from address, log level, and worker retry parameters. Production startup fails for missing/placeholder secrets, a non-PostgreSQL database URL, an insecure public origin, or non-TLS SMTP settings. Secrets are never committed or returned by endpoints.

## 4. Data model

All timestamps are timezone-aware UTC. Primary identifiers are UUIDs. Database constraints back up application validation.

### 4.1 `registrations`

- `id` UUID primary key
- `client_submission_id` UUID unique, required
- `payload_hash` fixed-length SHA-256 digest, required
- `variant` enum: `external` or `student`
- common required fields: `first_name`, `last_name`, `email`
- external-only required field: `organization`; student-only columns are null
- student-only required fields: `study_institution`, `study_programme`, `student_id`; external-only column is null
- `configuration_version` required
- `anti_automation_nonce` unique, required
- `created_at` required
- `backup_relative_path`, `backup_sha256`, and `backup_size_bytes` required

A check constraint enforces the external/student column combination. Email is not unique because the inputs do not prohibit a participant making more than one registration.

### 4.2 `registration_options`

- composite primary key: `registration_id`, `option_id`
- immutable snapshot: `display_name`, `category`

### 4.3 `registration_consents`

- composite primary key: `registration_id`, `consent_id`
- immutable snapshot: `label`, `policy_version`, `accepted` (must be true for required consent), `accepted_at`

### 4.4 `email_outbox`

- `id` UUID primary key
- `registration_id` foreign key
- `kind` enum: `participant_confirmation` or `organizer_notification`
- `recipient_set` (participant address or the organizer-recipient configuration snapshot)
- `state` enum: `pending`, `sending`, `sent`, `retry`, `failed`
- `attempt_count`, `available_at`, `lease_until`, `last_error_code`, `created_at`, `sent_at`
- unique constraint on `(registration_id, kind)`

The table stores no SMTP password and no rendered participant data. The worker renders from the registration and option/consent snapshots. Expired leases are reclaimable after worker failure.

### 4.5 `rate_limit_buckets`

- composite key: HMAC-hashed network identifier, action, and time-window start
- request count and expiry

Only a keyed, truncated digest of the remote address is stored, never the raw address. Expired buckets are periodically removed. Rate updates are atomic to work across web replicas.

## 5. REST API contract

All API responses carry `X-Request-ID`; caller-supplied request IDs are accepted only when syntactically safe, otherwise generated. Errors use:

```json
{
  "error": {
    "code": "validation_failed",
    "message": "Please correct the highlighted fields.",
    "fields": {"participant.email": "Enter a valid email address."},
    "requestId": "..."
  }
}
```

Error messages expose no stack, SQL, path, SMTP, credential, or internal connection details.

### 5.1 `GET /api/v1/forms/{variant}`

Returns the selected fixed field definitions, currently active applicable options grouped by category, applicable consents, conference/configuration labels, and a signed anti-automation challenge. Inactive options are never returned. The response uses `Cache-Control: no-store`.

The challenge contains a random nonce, variant, issued timestamp, and expiry, authenticated with HMAC-SHA-256. It is valid for 30 minutes and bound to the requested variant.

Responses: 200; 404 for unknown variant; 429 when throttled; 503 for invalid/unavailable configuration or database.

### 5.2 `POST /api/v1/registrations`

Requires `Content-Type: application/json`, a body no larger than 32 KiB, and a trusted `Origin` matching configured public origin in production. Request shape:

```json
{
  "submissionId": "UUID",
  "variant": "external",
  "participant": {
    "firstName": "Ana",
    "lastName": "Novak",
    "email": "ana@example.test",
    "organization": "Example"
  },
  "optionIds": ["workshop-accessibility"],
  "consents": {"privacy": true},
  "challengeToken": "signed-token",
  "website": ""
}
```

`website` is an invisible, accessibility-excluded honeypot and must be empty. Unknown properties are rejected at every object level. The student participant object replaces `organization` with `studyInstitution`, `studyProgramme`, and `studentId`.

On first success the endpoint returns 201:

```json
{
  "registrationId": "UUID",
  "status": "registered",
  "message": "Your registration was received."
}
```

A retry with the same `submissionId` and normalized payload hash returns the same successful resource and does not create storage or email duplicates. Reuse with different content returns 409. Other responses: 400 malformed JSON; 403 invalid origin/challenge or filled honeypot (generic message); 415 unsupported media type; 422 field/option/consent validation; 429 throttled; 503 persistence/configuration unavailable. Responses never claim success until database and JSON backup are durable.

### 5.3 `GET /api/v1/organizer/registrations.xlsx`

Requires `Authorization: Bearer <configured-token>`, compared in constant time, and is separately throttled. Missing/invalid credentials return 401 with no participant information. A successful 200 response has the official XLSX media type, a safe attachment filename, and `Cache-Control: no-store, private`. Data is read from one consistent database transaction snapshot.

### 5.4 Health endpoints

- `GET /health/live` reports process liveness only.
- `GET /health/ready` succeeds only when the database, valid conference configuration, and writable backup mount are available. It does not disclose connection/configuration details.

## 6. Validation and normalization

Browser and backend enforce the same user-facing rules, but backend validation is authoritative.

| Field | Rule after trimming and Unicode NFC normalization |
| --- | --- |
| First/last name | required, 1–100 characters |
| Email | required, standards-based format, maximum 254 characters; domain normalized case-insensitively |
| Organization / study institution / study programme | required for applicable variant, 1–200 characters |
| Student ID | required for student, 1–64 characters |
| Option IDs | unique array, at most 100 entries; each must be active and applicable in the current configuration |
| Consents | all applicable required consent IDs must be exactly `true`; unknown IDs rejected |

Whitespace-only fields fail. Control characters are rejected. Human punctuation and all valid Unicode, including Slovenian characters, are preserved. Variant-inapplicable fields and unexpected fields fail rather than being ignored. Database-bound values use parameterized SQL exclusively. HTML and email templates auto-escape participant values; no participant value enters headers, file paths, log-message templates, or code. JSON is produced by a serializer, not string concatenation.

The browser uses appropriate input types, lengths, `required`, and custom messages. The server returns field-keyed validation errors suitable for display. Malformed, oversized, and unsupported requests are rejected before domain processing.

## 7. Registration consistency and JSON backup

The service performs these ordered steps:

1. Enforce request size/type/origin, rate limit, challenge, honeypot, schema, consent, and current-option rules.
2. Normalize the payload and calculate a canonical payload hash. Resolve an existing idempotent submission before creating anything.
3. Start a database transaction; create the registration, immutable selections/consents, and two outbox jobs; flush constraints without committing.
4. Serialize the complete registration snapshot as UTF-8 JSON with schema version, registration/submission IDs, UTC timestamp, variant, applicable participant fields, configuration version, option snapshots, and consent snapshots. It never includes secrets, challenge token, network identifier, or payload hash.
5. Write only to a server-generated `<registration UUID>.json` path under the configured directory: create a restrictive-permission temporary file, write, flush, `fsync`, atomically rename, then `fsync` the directory. Record SHA-256 and size in the database row.
6. Commit the database transaction. Only after commit may the API return 201.

If JSON creation fails, the transaction rolls back. If database commit fails after rename, the service removes the uncommitted backup where possible and returns failure. A startup/maintenance reconciliation reports and quarantines orphan temporary/unreferenced files; it never treats them as successful registrations. The backup mount and database volume are external to disposable containers.

## 8. Email processing

The worker atomically leases available outbox jobs using row locking with skip-locked semantics. It loads the committed registration snapshot and sends:

- **Participant confirmation:** addressed only to the submitted participant email, containing conference name, registration ID, variant, and safely rendered selected activity names. It does not expose internal notes, credentials, or other participants.
- **Organizer notification:** addressed to the configured organizer-recipient snapshot, containing the full submitted registration information and attaching the exact correlated JSON backup as `application/json` after verifying its recorded hash.

Headers are application-generated; submitted values appear only in safely encoded bodies/attachment. SMTP uses authenticated TLS in production. A deterministic message ID based on outbox ID supports downstream deduplication. Success marks `sent`. Transient failure records a bounded error category and reschedules with exponential backoff and jitter. Permanent failure or exhausted retries becomes `failed` and remains queryable/requeueable through an operator command. Worker restart reclaims expired leases. No failure creates another registration or another outbox row.

## 9. Excel export

The exporter creates an Office Open XML `.xlsx` workbook in memory/read-only streaming mode as appropriate. It has a `Registrations` sheet with stable headers:

`Registration ID`, `Registered at`, `Variant`, `First name`, `Last name`, `Email`, `Organization / institution`, `Study institution`, `Study programme`, `Student ID`, `Workshops`, `Events`, `Meals`, `Other activities`, `Consent evidence`.

One row is emitted per committed registration, ordered by timestamp then ID. Inapplicable fields are blank. Options use stored `display name [stable-id]` snapshots and are joined deterministically. Consent evidence uses consent ID, policy version, and acceptance timestamp. Unicode is preserved.

Every participant-controlled cell is explicitly a text cell. Values beginning (after whitespace/control characters) with `=`, `+`, `-`, or `@` are prefixed with an apostrophe, and disallowed control characters are removed, preventing spreadsheet formula injection. With zero registrations, the workbook still contains headers.

## 10. Frontend behavior and accessibility

Routes `/` and `/external` render the external form; `/student` renders the student form. Semantic HTML contains a real form, labelled inputs, fieldsets/legends for grouped checkboxes, a skip link, visible focus, and a live status region. JavaScript fetches form context, renders only server-provided active options/consents with text APIs (never `innerHTML` for configured text), and retains the challenge.

On submit, browser validation shows inline errors and focuses the first invalid field. A generated submission UUID remains stable across safe retries. During a request the submit button is disabled and status says submission is in progress. Only a 201 or matching idempotent success replaces the form with confirmation. Validation errors are mapped to fields; other failures preserve entered values, explain that registration was not confirmed, refresh an expired challenge when appropriate, and offer retry. No email-delivery promise is shown because email processing is asynchronous.

Responsive CSS uses a single column on narrow viewports and a bounded two-column layout where space permits, with minimum touch targets, wrapping labels, and no page-level horizontal overflow at 320 CSS pixels or wider. It respects reduced motion and does not rely on color alone.

## 11. Security and privacy controls

- Default-deny CORS; strict configured-origin validation for registration in production; JSON-only writes reduce form-post CSRF exposure.
- HMAC-signed, expiring, variant-bound challenge; honeypot; unique successful nonce; database-backed per-network and per-submission throttles with generic rejection responses.
- Constant-time organizer-token comparison, export-specific throttling, and TLS required at the deployment boundary.
- Trusted-host enforcement; proxy headers honored only from explicitly configured proxies.
- Response headers: restrictive Content-Security-Policy using self-hosted assets, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, restrictive `Permissions-Policy`, frame denial through CSP, and HSTS when HTTPS is configured.
- Parameterized persistence, strict schemas, bounded inputs, safe output contexts, server-generated paths, spreadsheet-cell neutralization, and no shell invocation.
- Structured logs with request/registration IDs and outcome codes, but no request bodies, email addresses, names, tokens, SMTP errors containing secrets, or raw IP addresses. Log values are control-character sanitized.
- Non-root containers, read-only root filesystem where supported, restrictive backup permissions, pinned dependency ranges/lock data, and no secrets in image or repository.
- Error handlers produce bounded public errors while full stack traces remain restricted to non-production development diagnostics.

## 12. Error handling and observability

Domain errors map deterministically to HTTP statuses/codes. Unexpected errors are logged with safe correlation context and return generic 500 responses. Database/configuration/backup unavailability returns 503. SMTP failures are isolated to the outbox and do not retroactively invalidate a durable registration.

Structured events cover form-context outcome, registration validation/rejection/success, backup result, export authorization/outcome, email job state, configuration reload failure, and readiness changes. Metrics may be derived by operators from these events, but no external telemetry system is required by scope.

## 13. Containerized deployment and operations

The repository supplies a multi-stage `Dockerfile`, `.dockerignore`, and `compose.yaml` with `web`, `worker`, and `db` services. Web and worker use the same non-root image. PostgreSQL and JSON backups use named volumes; configuration is a read-only mount. Health checks and dependency readiness are configured. The web service runs migrations through an explicit deployment command before serving rather than racing migrations across replicas.

An example environment file documents names but contains no working secrets. The operations documentation covers build, migration, startup, option configuration replacement, export authentication, SMTP settings, backup/restore of both database and JSON volume, failed-email inspection/retry, health checks, and secret rotation. TLS terminates at a production ingress/reverse proxy; local composition is for reproducible deployment validation.

## 14. Verification and test strategy

Tests are created only after implementation is complete, in the dedicated Tests phase.

- **Unit tests** use the specification/implementation to cover normalization, variant validation, configuration validation, challenge signing/expiry, canonical hashing, path generation, email rendering, workbook cell neutralization, and retry scheduling.
- **API/contract tests** use this specification and AC to verify REST shapes/statuses, unknown fields, payload limits, origin/auth handling, idempotency, active-option validation, errors, and security headers.
- **Integration tests** verify database transactions, JSON/database correlation and compensation, outbox durability/leasing, SMTP message recipients/attachment, Excel contents, concurrency, persistence across process restart, and dependency failures.
- **Acceptance/browser component tests** use User Stories and AC to exercise both form variants, frontend validation, backend-gated confirmation, retry states, configurable options, accessibility semantics, and responsive layout behavior.
- **Security tests** probe injection contexts, traversal-shaped input, formula injection, oversized/malformed data, challenge tampering/replay/expiry, throttling, origin checks, export authorization, and information leakage.
- **Regression and edge-case tests** cover Unicode, trimming, empty options/export, configuration changes, concurrent submissions, ambiguous retries, and worker recovery.

Static verification comprises Ruff formatting/lint, mypy type checking, frontend ESLint if a JavaScript lint dependency is introduced, dependency vulnerability audit, Bandit, migration validation, image build, and production-configuration startup. Coverage is measured rather than targeted by altering product behavior.

## 15. Traceability

| Requirement area | User Stories | Acceptance Criteria | Specification sections |
| --- | --- | --- | --- |
| External form | US-001 | AC-001–AC-005 | 3, 5, 6, 10 |
| Student form | US-002 | AC-006–AC-010 | 3, 5, 6, 10 |
| Configurable programme | US-003 | AC-011–AC-015, AC-035 | 3, 4, 5, 9 |
| In-app confirmation | US-004 | AC-016–AC-018 | 5, 7, 10 |
| Relational and JSON durability | US-005 | AC-019–AC-024 | 4, 7, 13 |
| Participant email | US-006 | AC-025–AC-028 | 4, 8 |
| Organizer notification | US-007 | AC-029–AC-032 | 4, 8 |
| Excel export | US-008 | AC-033–AC-038 | 5, 9 |
| Validation/security | US-001–US-008 | AC-039–AC-045, AC-050 | 5, 6, 11, 12 |
| Responsive/accessibility quality | US-001, US-002, US-004 | AC-046–AC-047 | 10 |
| Deployment/recovery | US-003, US-005–US-008 | AC-048–AC-049 | 7, 8, 13 |

Every criterion has an implementation destination and a planned independent verification level. No implementation or feature-test artefact is introduced by this specification phase.
