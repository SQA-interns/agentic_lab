# Specification

> Written in: phase 2 · Source: `01_acceptance-criteria.md`, `project/03_technical/*`, `project/04_security/*`, `project/05_quality/*`, `general/security/*` · Agent: writes

Contracts: `docs/02_contracts/` (`openapi.yaml`, `registration-record.schema.json`, `catalog-config.schema.json`, `export-workbook.json`, `examples/`). They are validated by `frontend/scripts/validate-contracts.mjs` (`npm run contracts`).

## 1. Components and modules

| Component | Folder | Runtime |
|---|---|---|
| backend | `02_output/backend` | Spring Boot 3.5.16 on Temurin 21, one deployable (AR-02) |
| frontend | `02_output/frontend` | React 19 SPA built by Vite, served by nginx 1.28.0, which also forwards `/api/` to the backend (AR-08) |
| local stack | `02_output/compose.yaml` | PostgreSQL 16.14, Mailpit v1.24.1, backend, frontend |

Backend packages under `lab.conference`. ArchUnit checks the allowed dependency direction and that there are no cycles (AR-02):

| Module | Responsibility | May depend on |
|---|---|---|
| `options` | Load/validate startup catalog; lookup of active options | `platform` |
| `registration` | REST endpoints, validation, captcha, idempotency, JSON store, reconciliation, DB entities | `options`, `notifications`, `platform` |
| `notifications` | Durable outbox, SMTP sender, retry scheduler | `platform` |
| `export` | Organizer Excel export | `registration`, `options`, `platform` |
| `platform` | Security config, rate limiting, origin check, error handling, profile guard, clock | none of the above |

## 2. REST interface (AR-01)

Defined in `openapi.yaml`: `GET /api/catalog`, `POST /api/registrations/external`, `POST /api/registrations/student`, `GET /api/organizer/export.xlsx` (HTTP Basic), `GET /actuator/health/{liveness,readiness}` (ES-09; the public proxy does not route `/actuator`).

Status codes: 201 accepted; 200 idempotent replay; 400 validation/captcha/consent (field errors); 403 disallowed Origin; 409 request-ID conflict (D-17); 413 body > 16 384 bytes; 415 not JSON; 429 rate limit (`Retry-After`); 503 storage failure (not accepted). Unknown JSON properties are rejected (`UNKNOWN_FIELD`).

## 3. Validation rules (SB-01, BR-01…BR-04, SR-03)

Applied on the backend in this order; any failure means nothing is written:

1. Body ≤ 16 384 bytes, `Content-Type: application/json`, well-formed JSON, no unknown properties.
2. `clientRequestId` is a UUID (`INVALID_FORMAT`).
3. Text fields are trimmed of leading/trailing characters for which `Character.isWhitespace` or `Character.isSpaceChar` is true (covers NBSP U+00A0, U+2007, U+202F, and so on). Blank → `REQUIRED`. Content is otherwise preserved, with no case folding or normalisation (BR-01).
4. Single-line fields reject ISO control characters (`INVALID_CHARACTERS`), which prevents header/line injection (SR-04).
5. Maximum lengths in Unicode code points: names 100; email 254; organization, study institution and study programme 200; student ID 64; captcha token 4 096.
6. Email: `local@domain`, local part ≤ 64 characters from the RFC 5322 atext set plus dots (no leading, trailing or double dots), and a domain of ≥ 2 DNS labels (`INVALID_EMAIL`).
7. Selections: only the groups `workshops`, `events`, `meals` and `other`; ≤ 50 IDs per group; no duplicates (`DUPLICATE_OPTION`); each ID must be an active option **of that group** in the loaded catalog. Unknown, inactive and wrong-group IDs give the same `UNKNOWN_OPTION` error (BR-03). There are no capacity rules and no student-only rules (OQ-04).
8. Consent: if the catalog consent is `required`, `consentGiven` must be `true` (`CONSENT_REQUIRED`) (BR-04).
9. Captcha, verified last so that cheap checks fail first (`CAPTCHA_FAILED`) (SR-01).

All field errors from steps 2–8 are returned together. The frontend repeats rules 3, 5, 6 and 8 for usability only (BR-02).

## 4. Acceptance flow and durability (AR-04, AR-09, BR-05, BR-06, NFR-01)

1. Validate (section 3). No writes before this point.
2. Look up `client_request_id`. If it exists with the same request fingerprint (SHA-256 of the canonical normalised content), return 200 with the original result. If the fingerprint differs, return 409.
3. The server generates `registrationId` (UUID v4) and builds the canonical registration JSON (`registration-record.schema.json`, UTF-8).
4. Publish the JSON atomically: write `staging/<id>.json.tmp`, `fsync` it, `ATOMIC_MOVE` it to `registrations/<id>.json`, then `fsync` the directory. Paths come only from the generated UUID (SR-02). On failure, delete the temp file and return 503.
5. In one database transaction, insert the registration, its selections, the JSON SHA-256 and the notification intents (1 participant + 1 per organizer address). Commit.
6. On a commit failure (database down), delete the published JSON (best effort) and return 503. On a unique violation of `client_request_id` (a concurrent duplicate), delete our JSON and continue as in step 2.
7. Return 201 only after step 5 has committed. **Accepted** means the DB row plus a JSON file whose SHA-256 matches.

Reconciliation (`registration` module) runs at startup and every `RECONCILE_INTERVAL` (default 5 min):
- It moves `registrations/*.json` files older than the grace period (default 2 min) that have no DB row to `orphaned/` (AC-005-04).
- It deletes `staging/*.tmp` files older than the grace period.
- It counts DB rows whose JSON file is missing or has a different hash and logs them by registration ID only (no personal data).

The export reads DB rows only, so orphaned files never appear (AC-008-04).

## 5. Data model (ES-08, AR-03)

Flyway migrations in `backend/src/main/resources/db/migration`; Hibernate `ddl-auto=validate` (no auto-DDL).

- `registration`: `id uuid PK`, `client_request_id uuid UNIQUE`, `request_fingerprint char(64)`, `form_type varchar(16)`, `first_name`, `last_name`, `email`, `organization`, `study_institution`, `study_programme`, `student_id` (nullable according to form type), `consent_id`, `consent_given`, `json_sha256 char(64)`, `accepted_at timestamptz`.
- `registration_selection`: `(registration_id FK, group_id, option_id)` PK, `option_name`, `position`.
- `notification_outbox`: `id bigserial`, `registration_id FK`, `kind` (`PARTICIPANT`, `ORGANIZER`), `recipient`, `subject`, `body_text`, `attachment_name`, `attachment_sha256` (nullable), `status` (`PENDING`, `SENT`), `attempts`, `next_attempt_at`, `last_attempt_at`, `sent_at`, `last_error` (error class only), `created_at`.

## 6. Notifications (AR-05, BR-07, BR-08, SR-04)

- The outbox rows are written in the acceptance transaction. A scheduler (`NOTIFY_POLL_INTERVAL`, default 5 s) claims up to 20 due `PENDING` rows with `FOR UPDATE SKIP LOCKED`, sends them, and marks them `SENT` with `sent_at`.
- On failure: `attempts+1`, then `next_attempt_at = now + min(2^attempts × 5 s, 15 min)`. Retries never stop; the row stays `PENDING`. "SENT" means only that the SMTP server accepted the message; delivery is never claimed beyond that.
- Delivery is at-least-once. A crash between SMTP acceptance and marking the row `SENT` can resend it. Each message carries a stable `Message-ID: <registrationId.kind.n@lab-conference>` so that duplicates can be recognised.
- Participant mail: to the submitted email; fixed subject "Lab Conference – registration received"; plain text with the registration ID, name, form type, selected activity names and a note that it was generated automatically.
- Organizer mail: to each `ORGANIZER_EMAILS` address; subject "New registration <registrationId>"; plain-text summary of all submitted fields; attachment `registration-<id>.json` read from the JSON store at send time and checked against `attachment_sha256` (mismatch → retry, logged).
- Only the recipient address comes from user input. Recipients are validated email addresses with no control characters and are set through the `InternetAddress` API. Subjects contain no user text. Bodies are `text/plain; charset=UTF-8`, so HTML is never rendered.

## 7. Captcha (SR-01)

`CAPTCHA_MODE=stub|recaptcha`.
- `stub`: the token `local-captcha-ok` passes; anything else fails. It is allowed only when `APP_PROFILE` is `local` or `test`. The frontend then shows a "Local test captcha" checkbox that sets the token.
- `recaptcha`: the server POSTs to `https://www.google.com/recaptcha/api/siteverify` with `RECAPTCHA_SECRET_KEY` (3 s timeout). Any error or non-success result rejects the submission (fail closed). The frontend renders the Google v2 widget with `RECAPTCHA_SITE_KEY` from `/api/catalog`.
- Startup guard: `APP_PROFILE=production` with `CAPTCHA_MODE=stub`, a missing secret or site key, or any profile other than `local`/`test`/`production` stops startup.

## 8. Organizer export and authentication (AR-06, BR-09, SR-04)

- Spring Security HTTP Basic on `/api/organizer/**` only, with an in-memory single user from `ORGANIZER_USERNAME` and `ORGANIZER_PASSWORD_HASH`. The hash uses the `DelegatingPasswordEncoder` format, and only `{bcrypt}` (cost ≥ 10) or `{pbkdf2}` is accepted at startup. Stateless: no session and no JWT. `WWW-Authenticate: Basic realm="Lab Conference organizer"`.
- Workbook per `export-workbook.json` (Apache POI XSSF). Every value is written as a string cell. Values starting with `= + - @ \t \r` get a quote-prefix style, so Excel shows the text and never evaluates it. Response headers: `Cache-Control: no-store`, `Content-Disposition: attachment`.
- CSRF: the export is a safe GET and the registration POSTs use no credentials. Spring Security CSRF stays enabled by default and is ignored only for `/api/registrations/**`, which is additionally protected by the Origin check (section 9) and the JSON-only content type. CSRF is not disabled globally.

## 9. Abuse and transport controls (SB-04, SB-06, SB-10, SR-03, AR-08)

- Rate limits (in-memory token bucket per client IP): registration POSTs `REGISTRATION_RATE_LIMIT_PER_MINUTE` (default 20; local Compose 120); organizer export `EXPORT_RATE_LIMIT_PER_MINUTE` (default 10). An exceeded limit returns 429 with `Retry-After`, before any other processing.
- Client IP: Tomcat `RemoteIpValve` trusts `X-Forwarded-For` only from `TRUSTED_PROXIES` (a regex; default: none, which means the socket address). Forwarded headers are not otherwise trusted.
- Origin: registration POSTs with an `Origin` header outside `ALLOWED_ORIGINS` are rejected with 403. No CORS is enabled (same-origin through the proxy).
- Headers:
  - Backend: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`, `Referrer-Policy: no-referrer`, `Cache-Control: no-store`.
  - Frontend nginx: the CSP in `frontend/nginx/nginx.conf` (self plus the Google reCAPTCHA hosts), `nosniff`, `DENY`, `no-referrer`.
- TLS: terminated by the external nginx in production (SB-04). Containers use internal HTTP; localhost uses HTTP only.

## 10. Configuration (ES-01, AR-07)

| Setting | Default | Notes |
|---|---|---|
| `APP_PROFILE` | none (required) | `local`, `test` or `production` |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | none | `DB_PASSWORD` secret |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_TLS_ENABLED`, `SMTP_USERNAME`, `SMTP_PASSWORD` | none / 25 / true / empty | auth used only if a username is set; TLS required in production |
| `MAIL_FROM`, `ORGANIZER_EMAILS` | none | comma-separated organizer list |
| `CAPTCHA_MODE`, `RECAPTCHA_SITE_KEY`, `RECAPTCHA_SECRET_KEY` | none | see section 7 |
| `CONFERENCE_CONFIG_PATH` | none | JSON per `catalog-config.schema.json`; startup fails if invalid (D-19) |
| `REGISTRATION_JSON_DIR` | none | persistent volume; `registrations/`, `staging/`, `orphaned/` created at start |
| `ORGANIZER_USERNAME`, `ORGANIZER_PASSWORD_HASH` | none | secrets |
| `PUBLIC_BASE_URL`, `ALLOWED_ORIGINS`, `TRUSTED_PROXIES` | none / none / none | documented local values in `compose.yaml` |
| `REGISTRATION_RATE_LIMIT_PER_MINUTE`, `EXPORT_RATE_LIMIT_PER_MINUTE` | 20 / 10 | |
| `NOTIFY_POLL_INTERVAL`, `RECONCILE_INTERVAL`, `RECONCILE_GRACE` | PT5S / PT5M / PT2M | ISO-8601 durations |

## 11. Frontend (NFR-03, NFR-04)

- Plain history routing: `/` (choose a form), `/register/external`, `/register/student`, `/organizer`. The UI is English.
- Forms load `/api/catalog`, render each group as a `<fieldset>` with a `<legend>` and checkboxes, and render the consent checkbox unchecked. A `clientRequestId` (`crypto.randomUUID()`) is generated per form instance and reused for retries after errors.
- Errors:
  - Each input has a `<label>`. Invalid fields get `aria-invalid` and `aria-describedby` pointing to a text message (not colour-only), and an error summary (`role="alert"`) receives focus.
  - Server field errors map to the same fields.
  - Network errors or 5xx show "Your registration was not saved. Please try again." and keep the entered data.
- Success page after 201/200 only: heading "Registration received", the registration ID, and a note that a confirmation email will follow (it does not claim delivery).
- Visible focus outline on all interactive elements; everything is keyboard reachable.
- The organizer page is a link to `/api/organizer/export.xlsx`; the browser's native Basic authentication dialog is used, and the frontend never handles credentials.

## 12. Deployment (AR-08, SB-11, NFR-05)

The Compose project `agenticlab` uses named volumes `pgdata` and `jsondata` and mounts the catalog read-only. Only `127.0.0.1:18080` (app) and `127.0.0.1:18025` (Mailpit UI) are published. The backend runs as UID 10001 and the frontend as the `nginx` user (non-root). The JSON volume is mounted only into the backend; nginx serves only the built SPA, so JSON files cannot be reached through static hosting (SR-02). In production, an external nginx with TLS routes `/` to the frontend and `/api/` to the backend, and sets `X-Forwarded-For`, which `TRUSTED_PROXIES` must match.

## 13. Security verification standard

OWASP ASVS **5.0.0** (release 2025-05-30), Level 2 for the applicable controls (security-requirements.md). Mapping of the applicable chapters:

| ASVS 5.0.0 chapter | Where |
|---|---|
| V1 Encoding and sanitization | sections 6, 8 (plain-text mail, formula-safe cells), JPA parameter binding |
| V2 Validation and business logic | section 3; anti-automation: sections 7, 9 |
| V3 Web frontend security | section 9 headers; React output encoding |
| V4 API and web service | sections 2, 9 (content type, size, Origin) |
| V5 File handling | section 4 (generated names, atomic write, no user paths) |
| V6 Authentication | section 8 (slow salted hash, Basic over HTTPS, rate limit) |
| V8 Authorization | section 8 (export only for the organizer; everything else public) |
| V11 Cryptography | bcrypt/pbkdf2 only; SHA-256 fingerprints |
| V12 Secure communication | section 9 TLS at the proxy; SMTP TLS in production |
| V13 Configuration | section 10, startup guards, secrets from the environment |
| V14 Data protection | section 14 |
| V16 Security logging and error handling | problem+json without internals; logs without personal data |

Not applicable, with reason: V7 session management and V10 OAuth/OIDC (no sessions, no IdP, by scope); V9 self-contained tokens (none); V15 secure coding architecture is covered by the dependency scans in phase 6; V17 WebRTC (none).

## 14. Personal data (SB-12…SB-14)

Collected: only the fields in business-rules.md "Data". Purposes and retention: security-requirements.md (synthetic lab data until the operator deletes the volumes). Every representation lives in `pgdata` (registration, outbox bodies), `jsondata` (JSON files) or Mailpit (mail copies), and deleting the three volumes deletes all of it. The consent checkbox is never preselected. Logs contain registration IDs and error classes only, never names, emails or bodies (ES-07).

## Traceability

| AC / SR / AR | Section |
|---|---|
| AC-001-01, AC-002-01 | 2, 3, 4 |
| AC-001-02, AC-002-02 | 3 (rules 3, 5) |
| AC-001-03, AC-002-03 | 3 (rule 6) |
| AC-001-04, AC-002-04 | 3 (rule 3) |
| AC-001-05, AC-002-05 | 3 (rule 7) |
| AC-001-06, AC-002-06 | 3 (rule 9), 7 |
| AC-001-07, AC-002-07 | 3 (rule 8), 11 |
| AC-001-08 | 2, 3 (rules 1, 5) |
| AC-001-09, AC-002-08 | 11 |
| AC-001-10 | 9 |
| AC-003-01…AC-003-04 | 1 (`options`), 10, 11; `catalog-config.schema.json` |
| AC-004-01, AC-004-02 | 11 |
| AC-004-03…AC-004-05 | 4 (step 2) |
| AC-005-01…AC-005-05 | 4, 5, 12 |
| AC-006-01…AC-006-03 | 6, 3 (rule 4) |
| AC-007-01, AC-007-02 | 6 |
| AC-008-01…AC-008-04, AC-008-06 | 8, 11; `export-workbook.json` |
| AC-008-05 | 9 |
| SR-01 | 7 |
| SR-02 | 4, 12 |
| SR-03 | 3, 9 |
| SR-04 | 3 (rule 4), 6, 8 |
| SR-05 | 6, 10, 12 (Mailpit only locally, reserved domains) |
| SR-06 | phase 6 process (raw scanner reports kept in `out/logs/`) |
| SB-01 | 3 |
| SB-02 | 8 |
| SB-03 | 8, 10 |
| SB-04 | 9, 12 |
| SB-05 | 6, 8, 11 (React escaping), JPA parameters |
| SB-06 | 9 |
| SB-07 | 2, 14 |
| SB-08, SB-09 | phase 0/6 scans (dependency-check, npm audit, semgrep, gitleaks) |
| SB-10 | 9 |
| SB-11 | 12 |
| SB-12…SB-14 | 14 |
| NFR-01 | 4 |
| NFR-02 | 4, 6, 12 |
| NFR-03 | 3, 11 |
| NFR-04 | 11 |
| NFR-05 | 12; component READMEs |
| AR-01 | 1, 2 |
| AR-02 | 1 |
| AR-03 | 5 |
| AR-04 | 4 |
| AR-05 | 6 |
| AR-06 | 8 |
| AR-07 | 10 |
| AR-08 | 9, 12 |
| AR-09 | 4 |
