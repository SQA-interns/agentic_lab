# Conference Registration — Generated Specification

Task `conference-registration-v3`, configuration `single-agent-sdd-tjan`.
Fixed inputs: `01_Input_files/{PRODUCT,TECH_STACK,ARCHITECTURE,WORKFLOW,DEFINITION_OF_DONE}.md`.
This document references AR-xx / ST-xx / US-xxx / P-xx IDs instead of copying them.

## 0. Current state (resume pointer)

| Item | Value |
|---|---|
| Active slice | M6-a (final verification and reporting) |
| Last verified revision | see `03_Metrics/run.json` → `git.finalImplementationRevision` |
| Last check | final suite `tools/verify-all.sh final` (evidence `03_Metrics/evidence/final-suite.txt`) |
| Blockers | D-05: spring-core 6.2.19 / spring-security-core 6.5.11 High/Critical advisories whose fixes (6.2.20 / 6.5.12) are not publicly released for the Spring Boot 3.x line (ST-01) |
| Next action | none within scope; unblock requires an authorized stack change (Boot 4) or access to patched 3.x artifacts |

## 1. Scope

In scope: US-001…US-008 exactly. Out of scope (PRODUCT boundaries): participant
accounts, payment, editing registrations, student-status verification, admin
configuration UI, organizer dashboard, identity-provider integration, capacity
limits, selection cardinalities, repeated-email policy, retention periods.

Ordinary chosen behaviour for unspecified policy (flagged, not invented as law):
- **Repeated email**: the same email may register multiple times (each request is a
  separate registration). Only duplicate *requests* (same `clientRequestId`) are
  de-duplicated. Real deployment needs an organizer policy decision.
- **Selections**: zero or more options per group; each ID at most once per group.
- **Retention**: none implemented; real deployment needs a retention/erasure policy.
- **Consent**: the mechanism supports configured consents (`required: true|false`).
  The default production catalog ships **no** consent text because legal wording is
  not supplied; a synthetic fixture (clearly labelled as not a legal notice) is used
  for P-06. Real deployment is blocked on approved consent wording/legal review.
- **Text limits** (technical request bounds, not business rules): names ≤ 100,
  organization/institution/programme ≤ 200, student ID ≤ 64, email ≤ 254 chars.

## 2. Acceptance criteria (Given–When–Then)

Common definitions: *trim* = remove leading/trailing characters that are
`Character.isWhitespace` or `Character.isSpaceChar` or U+FEFF/U+200B (covers NBSP
U+00A0, U+2007, U+202F); interior text is preserved byte-for-byte (no Unicode
normalization). *Accepted* = HTTP 201 (or 200 replay) with registration ID after
both the DB commit and the published JSON file exist (AR-04).

### US-001 External participant
- **AC-001-01** Given an active catalog, when a valid external form (first name,
  last name, email, organization) with active options and a valid captcha is
  POSTed, then 201 with a server registration ID; one DB row, one JSON file with
  identical content (SHA-256 equal), pending email records. (P-01)
- **AC-001-02** Given first name ` Špela `, when accepted, then the stored
  value in DB, JSON and export is exactly `Špela`. (P-01)
- **AC-001-03** Given any required external field blank or whitespace/NBSP-only or
  missing, then 400 with field error codes and no DB row / JSON file / email. (P-03)
- **AC-001-04** Given a malformed email (e.g. `a@b`, `no-at.example.com`, `a b@x.org`), then 400, nothing stored. (P-03)
- **AC-001-05** Given an invalid/missing captcha token, then 400 `CAPTCHA_INVALID`, nothing stored. (P-03)
- **AC-001-06** Given the UI, when the external form is completed in a real browser, then the
  success panel appears only after the backend 201 response. (US-004)

### US-002 Student
- **AC-002-01** Given a valid student form (first name, last name, email, study
  institution, study programme, student ID) with active options, then accepted
  as in AC-001-01. (P-02)
- **AC-002-02** For each of the six required student fields omitted, blank or
  NBSP-only (sent directly to the API), then 400 naming that field and nothing stored. (P-02)
- **AC-002-03** Students may select any active option from all groups (no student restriction).
- **AC-002-04** Browser journey for the student form succeeds end-to-end.

### US-003 Configurable options
- **AC-003-01** Given the catalog file lists options with `id`, `name`, `active`, when the service starts,
  then `GET /api/form-config` lists only active options per group (workshops,
  events, meals, otherActivities). (AR-07)
- **AC-003-02** Given an active known option ID, then accepted; given an unknown ID,
  an inactive ID, an ID from another group, or a duplicated ID sent directly to the
  API, then 400 `OPTION_INVALID` and nothing stored. (P-04)
- **AC-003-03** Given the catalog file is changed (option retired/added) and the
  backend restarted (no rebuild), then the new catalog is displayed and enforced,
  and fixed participant fields are unchanged. (P-05)
- **AC-003-04** Given an invalid catalog file (duplicate IDs, bad ID syntax, blank
  name, unreadable file), then the backend fails to start (fail fast).
- **AC-003-05** Given a required consent in the catalog (synthetic fixture), then the
  UI renders it unchecked; submission without it is rejected by the backend (400
  `CONSENT_REQUIRED`), with it is accepted; unknown consent IDs are rejected. (P-06)

### US-004 In-app confirmation
- **AC-004-01** Given submission in progress, the submit button is disabled and no success is shown.
- **AC-004-02** Given backend 201/200, the UI shows a confirmation panel with registration ID
  and states that confirmation email is *queued* (not that it arrived).
- **AC-004-03** Given 400/409/413/429/5xx/network error, the UI shows an error and never the success panel.

### US-005 Durable storage and recovery
- **AC-005-01** Each accepted registration exists as a PostgreSQL row (with its canonical
  raw JSON and SHA-256) and as `<backup>/registrations/<id>.json` on a persistent volume. (AR-03)
- **AC-005-02** Given a repeat of an accepted `clientRequestId` with the same payload, then
  200 with the original registration ID (`replayed: true`); no second row/file/email. Same ID
  with a different payload → 409 `REQUEST_ID_CONFLICT`. Concurrent duplicates yield exactly one registration. (P-07)
- **AC-005-03** Given the JSON write fails, then 503 `STORAGE_UNAVAILABLE`, no DB row, no published file. (P-07)
- **AC-005-04** Given the DB commit fails after the JSON was published, then 503, the published
  file is removed (or quarantined if removal fails), no success. (P-07)
- **AC-005-05** Given crash leftovers (staged temp files, published files without DB row,
  DB rows whose file is missing/mismatched), when reconciliation runs (startup + periodic),
  then temp files are deleted, orphan files moved to `<backup>/orphaned/`, missing/mismatched
  files re-published from the DB raw JSON (mismatched copy quarantined). If the DB holds zero
  registrations while files exist, no quarantine happens (protects a DB-restore scenario). (P-07)
- **AC-005-06** Given containers are recreated with volumes preserved, then previously accepted
  registrations and their JSON files remain available and exportable. (P-10)

### US-006 Participant confirmation email
- **AC-006-01** Given an accepted registration, one participant confirmation outbox record is
  committed in the same DB transaction as the registration; the dispatcher delivers it to the
  participant address via SMTP (verified in the SMTP catcher). Content is plain text, contains
  no raw HTML, and header values are CR/LF-free.
- **AC-006-02** Given SMTP unavailable, the registration stays accepted, the record stays
  `PENDING` with attempts/next attempt/last error; after SMTP recovers, it is delivered. After
  the max attempts it becomes `FAILED` (recorded operational failure). (P-08)

### US-007 Organizer notification
- **AC-007-01** Given an accepted registration, each configured organizer address receives a
  notification containing the submitted data and an attachment `registration-<id>.json` whose
  bytes equal the stored JSON file. (P-08)
- **AC-007-02** Same retry semantics as AC-006-02. Delivery is at-least-once: a crash between
  SMTP acceptance and the `SENT` update may produce a duplicate email (documented).

### US-008 Excel export
- **AC-008-01** Given organizer HTTP Basic credentials from environment, `GET
  /api/organizer/registrations/export.xlsx` returns a readable XLSX with one row per accepted
  registration (both forms), matching DB values. (P-09)
- **AC-008-02** Given no/invalid credentials, 401 with no registration data in the body. (P-09)

### Cross-cutting
- **AC-X-01** Production mode cannot use the deterministic captcha; startup fails when the
  reCAPTCHA secret/site key is missing, when `app.captcha.mode=test` is set without a
  `local`/`test` profile, or when a `prod` profile is combined with `local`/`test`. (ST-05)
- **AC-X-02** Organizer credential missing in production → startup fails; no credential in source. (AR-06)
- **AC-X-03** Public submission is rate limited per client IP (429); bodies > 32 KiB → 413.
- **AC-X-04** Error responses (RFC 7807) never echo submitted values or stack traces; logs contain
  registration IDs, not personal data.
- **AC-X-05** `/actuator/health/liveness` and `/actuator/health/readiness` (DB + backup dir writable) respond UP.

## 3. API (REST, JSON, UTF-8) — AR-01

| Method/path | Auth | Purpose |
|---|---|---|
| `GET /api/form-config` | public | Active options per group, consents, captcha mode/site key |
| `POST /api/registrations/external` | public + captcha | US-001 |
| `POST /api/registrations/student` | public + captcha | US-002 |
| `GET /api/organizer/registrations/export.xlsx` | HTTP Basic, role ORGANIZER | US-008 |
| `GET /actuator/health[/liveness|/readiness]` | public (no details) | ops |

Request (external; student replaces `organization` by `studyInstitution`, `studyProgramme`, `studentId`):
```json
{"clientRequestId":"<uuid v4>","firstName":"Špela","lastName":"Novak","email":"spela@example.org",
 "organization":"Univerza","selections":{"workshops":["ws-a"],"events":[],"meals":["lunch-1"],"otherActivities":[]},
 "consents":{"synthetic-consent":true},"captchaToken":"..."}
```
Responses: `201` / `200` (replay) `{"registrationId","participantType","submittedAt","emailStatus":"PENDING","replayed"}`;
`400` ProblemDetail `{"code":"VALIDATION_FAILED|CAPTCHA_INVALID|OPTION_INVALID|CONSENT_REQUIRED","errors":[{"field","code"}]}`;
`409 REQUEST_ID_CONFLICT`; `413 PAYLOAD_TOO_LARGE`; `415`; `429 RATE_LIMITED`; `503 STORAGE_UNAVAILABLE`; `500 INTERNAL_ERROR`.
Unknown JSON properties are rejected (400) so fixed fields cannot be extended by clients.

Processing order: (1) size/rate filters → (2) JSON parse + bean validation + trim → (3) option &
consent validation → (4) idempotency lookup by `clientRequestId` (read only: same fingerprint →
200 replay without re-verifying captcha, because a legitimate network retry cannot reuse a
single-use Google token; different fingerprint → 409) → (5) captcha verification → (6) durable
write protocol (§5). Captcha thus precedes every durable write.

## 4. Data model (Flyway, AR-03; `spring.jpa.hibernate.ddl-auto=validate`)

- `registration(id uuid PK, client_request_id uuid UNIQUE, request_fingerprint varchar(64),
  participant_type varchar(16) CHECK IN ('EXTERNAL','STUDENT'), first_name, last_name, email,
  organization, study_institution, study_programme, student_id, raw_json text, raw_json_sha256 varchar(64),
  created_at timestamptz)` + CHECK constraint requiring the type-specific columns.
- `registration_selection(id identity PK, registration_id FK ON DELETE CASCADE, option_group, position,
  option_id, option_name, UNIQUE(registration_id, option_group, option_id))` — name snapshot at submission.
- `registration_consent(id identity PK, registration_id FK, consent_id, consent_text, UNIQUE(registration_id, consent_id))`.
- `email_outbox(id uuid PK, registration_id uuid, kind PARTICIPANT_CONFIRMATION|ORGANIZER_NOTIFICATION,
  recipient, subject, body, attachment_name, attachment_content text NULL, status PENDING|SENT|FAILED,
  attempts int, next_attempt_at, last_error varchar(500), created_at, sent_at)`; index on (status, next_attempt_at).
  `registration_id` is not an FK so the notification module has no schema dependency on registration.

Raw JSON (canonical, UTF-8, non-ASCII unescaped, fixed property order): `{schemaVersion:1,
registrationId, participantType, submittedAt, participant:{…fixed fields…},
selections:{workshops:[{id,name}],events,meals,otherActivities}, consents:[{id,text}]}`.
The same bytes are stored in `raw_json`, the file, and the organizer attachment.

## 5. Durable write protocol and recovery (AR-04; not one ACID transaction)

1. Generate `registrationId` (UUID v4), build canonical JSON, compute SHA-256.
2. **Stage**: write `<backup>/staging/<id>.json.tmp`, `fsync` file.
3. **Publish**: `ATOMIC_MOVE` to `<backup>/registrations/<id>.json`, `fsync` directory.
   Failure → delete temp, 503, nothing in DB.
4. **Commit** one DB transaction: registration + selections + consents + outbox rows.
   - Unique violation on `client_request_id` (concurrent duplicate) → delete own published file,
     return the winner as replay (200) if fingerprints match, else 409.
   - Any other failure → delete published file (move to `orphaned/` if delete fails), 503.
5. Return 201. Email delivery happens later (§6).

Crash windows: after 2 → temp leftover; after 3 before 4 → published orphan. Reconciliation
(`BackupReconciler`, at startup and every 10 min; files younger than a 10-min grace period are
ignored in the periodic run so in-flight requests are never touched) handles these as in AC-005-05.
Recovery for the organizer (US-005): DB is the authoritative index (persistent `pgdata` volume),
JSON copies on the `backups` volume; export (US-008) reads the DB; `registrations/*.json` files are
directly readable and verifiable against `raw_json_sha256`. Restoring after DB loss = restore DB
backup; JSON files remain as raw evidence (import tooling is out of scope — flagged).

## 6. Email outbox (AR-05, ST-04)

Rows are written in the registration transaction. `OutboxDispatcher` (`@Scheduled`, fixed delay
5 s) claims due `PENDING` rows via `SELECT … FOR UPDATE SKIP LOCKED LIMIT 10`, sends with
`JavaMailSender` (connect/read timeouts 10 s), marks `SENT`; on failure increments attempts,
stores a sanitized error class name (kept after a later success as the last failure reason), `next_attempt_at = now + min(30 s·2^(n-1), 30 min)`; after 12
attempts → `FAILED` (logged at ERROR with outbox ID only). At-least-once delivery. Organizer
recipients: `APP_ORGANIZER_EMAILS` (comma separated). Plain-text bodies; subject contains no user data.
Local: Mailpit SMTP catcher (compose). Production: external SMTP via `SPRING_MAIL_*` env.

## 7. Security (AR-06, AR-08, ST-01, ST-05)

- Spring Security: stateless, CSRF disabled (no cookies/sessions; export is a read-only GET),
  `/api/organizer/**` requires role ORGANIZER via HTTP Basic; everything else explicitly permitted;
  any other path denied. Organizer user from `APP_ORGANIZER_USERNAME` / `APP_ORGANIZER_PASSWORD`
  (bcrypt-hashed in memory at startup; required, ≥ 12 chars in all profiles).
- Headers: nosniff, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, API CSP
  `default-src 'none'; frame-ancestors 'none'`, HSTS when request is secure (proxy sets
  `X-Forwarded-Proto`). Frontend nginx sets a CSP allowing only self + Google reCAPTCHA origins.
- Forwarded headers: `server.forward-headers-strategy=native`, Tomcat RemoteIpValve trusts only
  `APP_TRUSTED_PROXIES` regex (default: private Docker ranges). Rate limit key = resolved client IP.
- Rate limit: in-memory token bucket per IP, `APP_RATE_LIMIT_PER_MINUTE` (default 20) for POST
  `/api/registrations/**`. Single-instance only (documented limitation).
- Body limit 32 KiB via filter (Content-Length and streamed count) + nginx `client_max_body_size`.
- Captcha: `CaptchaVerifier` interface. `RecaptchaVerifier` (default/prod) posts to Google
  `siteverify` with secret from env; requires `APP_RECAPTCHA_SECRET` and `APP_RECAPTCHA_SITE_KEY`.
  `DeterministicCaptchaVerifier` only when `app.captcha.mode=test` AND profile `local` or `test` is
  active (guard fails startup otherwise); accepts exactly `app.captcha.test-token`. Tests never call Google
  (`MockRestServiceServer`).

## 8. Module boundaries (AR-02; ArchUnit)

Base package `org.example.conference`:
- `shared` — text normalization, API error model, web/security/infra config. Depends on no feature module.
- `catalog` — option/consent catalog loading and lookup. Depends only on `shared`.
- `captcha` — verifier + mode guard. Depends only on `shared`.
- `notification` — outbox entity, enqueue API, dispatcher. Depends only on `shared`.
- `registration` — API, validation, write protocol, backup store, reconciliation. May depend on
  `catalog`, `captcha`, `notification`, `shared`.
- `export` — Excel export. May depend on `registration` (read-only query API) and `shared`.
ArchUnit: the above allowed-dependency matrix, no slice cycles, controllers not accessed by other
modules, `shared` independent.

## 9. Deployment (ST-09, AR-08)

`02_Implementation/compose.yaml`: `postgres` (16), `mailpit` (local SMTP catcher), `backend`,
`frontend` (nginx-unprivileged serving the Vite build and proxying `/api` + `/actuator/health` to
the backend — local stand-in for the external nginx; HTTP only). Named volumes `pgdata`, `backups`.
Catalog mounted read-only from `config/`. Images pinned by digest (recorded in
`docs/resolved-versions.md`). `compose.prod.yaml` overlay: profile `prod`, no Mailpit, required env
via `${VAR:?}`; external host nginx terminates TLS with Let's Encrypt (example
`deploy/nginx-external.conf`), sets `X-Forwarded-For/Proto`, and forwards to the frontend container;
application containers expose HTTP only on the internal network.

## 10. Material implementation choices

| Choice | Reason |
|---|---|
| Two POST endpoints (external/student) with separate DTOs | Bean Validation per form, no discriminator ambiguity |
| YAML catalog file at `APP_CATALOG_PATH` | Human-editable, restart-only change (AR-07) |
| Publish-file-then-commit ordering | A file without a DB row is unambiguously un-accepted → safe orphan quarantine |
| Raw JSON also in DB | Allows re-publishing missing/mismatched backup file and hash verification |
| Idempotency lookup before captcha | Allows safe network retries (Google tokens are single-use); returns only the prior ID for identical payload |
| Outbox table with attachment copy | Notification module independent of registration schema |
| Apache POI XSSF for Excel | Standard Java XLSX library; string cells (no formula evaluation) |
| GreenMail (tests) / Mailpit (compose) | Real SMTP servers; allows outage simulation |
| Runtime form-config endpoint | Frontend image independent of environment/catalog (no rebuild on catalog change) |
| No router library; tabs switch forms | Two forms only |
| `clientRequestId` regenerated when form data changes | Same payload retry ⇒ same ID; edited payload ⇒ new ID (avoids 409 on edits) |
| Dependency-Check with NVD only, OSS Index disabled | OSS Index requires credentials not available |
| NVD data via official NVD JSON 2.0 data feeds (`nvdDatafeedUrl`) | No NVD API key; keyless NVD REST API returned HTTP 503 during preflight |
| Composed constraints `@RequiredText(max)` / `@RequiredEmail` | One definition of the fixed-field rules for both forms (removes CPD duplication); each composing constraint keeps its own error code |
| Explicit PMD ruleset (bestpractices, errorprone, security minus `GuardLogStatement`, `AvoidFieldNameMatchingMethodName`) | Excluded rules are style-only: SLF4J placeholders already defer formatting; record accessors intentionally match field names |
| Absent `selections`/`consents` normalized to empty immutable collections | Same semantics as empty; immutable request objects |
| Frontend nginx container serves SPA + proxies `/api` | Local stand-in for the external nginx; in production the external nginx forwards to it |
| Local credentials generated into gitignored `.env` (`tools/init-local-env.sh`) | No credential in source (AR-06), even for local use |
| E2E/runtime checks inspect PostgreSQL and the backup volume through `docker compose exec` | Evidence comes from the real stores, not from API responses only |

## 11. Material unknowns / blockers

- Legal consent wording and consent categories: not supplied → production catalog has none; blocks real deployment, not the DoD.
- Production secrets (SMTP, reCAPTCHA keys, organizer credential, TLS domain) are environment-supplied; not tested against real Google/SMTP.
- `NVD_API_KEY` unavailable and keyless NVD REST API returned 503 → resolved by using the official NVD JSON data feeds (see §10).

## 12. Slice plan and milestone map

| Slice | Milestone | Behaviour / invariant | Verification |
|---|---|---|---|
| M0-a | M0 | Maven+Vite skeletons, Flyway V1, readiness, form-config endpoint, compose stack | `./mvnw verify` (IT: Flyway on Testcontainers), `npm run build`, compose up + health + frontend→backend probe |
| M1-a | M1 | Catalog loader + validation | unit tests |
| M1-b | M1 | External registration API: validation, trim, options, captcha, idempotency, write protocol, outbox rows, reconciliation | MockMvc + Testcontainers ITs incl. fault injection |
| M1-c | M1 | External form UI + success-after-201 | Vitest/RTL + Playwright on compose stack; DB/file inspection |
| M2-a | M2 | Student API + UI | ITs for each required field, Playwright student + external |
| M3-a | M3 | Consent support + catalog restart demo | ITs with fixture catalog; compose restart script with alternate catalog |
| M4-a | M4 | Outbox dispatcher, retry/backoff, SMTP outage | GreenMail IT; compose Mailpit stop/start probe |
| M5-a | M5 | Excel export + Basic auth | MockMvc IT parsing XLSX; compose probe |
| M6-a | M6 | Static/security/architecture checks, container recreation, failure probes, README, metrics | full suite (§13) |

Slice status: M0-a, M1-a…c, M2-a, M3-a, M4-a, M5-a verified (see `03_Metrics/events.jsonl`). M6-a:
all checks pass except D-05 (blocked, see §0).

## 13. Acceptance-to-verification mapping and commands

Filled in per slice; see §14 table. Commands (from `02_Implementation`):
- Backend: `cd backend && ./mvnw -B verify` (unit + IT + JaCoCo), `./mvnw spotless:check spotbugs:check pmd:check pmd:cpd-check`, `./mvnw org.owasp:dependency-check-maven:check`.
- Frontend (Node 22): `npm ci && npm run lint && npm run typecheck && npm run format:check && npm test && npm run build && npm run cpd`, `npm audit`.
- Runtime: `docker compose up -d --build`, `npm run e2e` (Playwright against `http://localhost:8088`), `tools/runtime-probe.sh`.
- Security: `semgrep scan --config p/default --config p/java --config p/typescript --config p/secrets`.

## 14. Acceptance → verification map

Backend tests: `backend/src/test/java/org/example/conference/**` (unit `*Test`, integration `*IT`
on Testcontainers PostgreSQL 16). Frontend: `frontend/src/__tests__/*` (Vitest/RTL),
`frontend/e2e/*.spec.ts` (Playwright on the compose stack). Runtime: `tools/runtime_probe.py`,
`tools/m3-catalog-restart.sh`.

| AC | Verification |
|---|---|
| AC-001-01, -02 (P-01) | `RegistrationApiIT.externalRegistrationWithUnicodeAndNbspIsStoredConsistently`; e2e `US-001 external participant registers with Unicode and NBSP`; `TextNormalizerTest`; `fields.test.ts` |
| AC-001-03 (P-03) | `RegistrationApiIT.externalRequiredFieldsAreEnforced`; e2e `blank and malformed input is rejected`; `fields.test.ts` |
| AC-001-04 (P-03) | `RegistrationApiIT.malformedEmailIsRejectedWithoutEchoingValue`; `fields.test.ts isValidEmail` |
| AC-001-05 (P-03) | `RegistrationApiIT.invalidCaptchaIsRejected`; `RecaptchaVerifierTest`; `CaptchaConfigurationTest` |
| AC-001-06, AC-004-01..03 | e2e `submitAndAssertOrdering` (response held until UI checked); `RegistrationForm.test.tsx` (success only after 201; 400/429/500/503/network never success) |
| AC-002-01, -03, -04 | `RegistrationApiIT.studentRegistrationIsAccepted`; e2e `US-002 student registers through the same stack` |
| AC-002-02 (P-02) | `RegistrationApiIT.studentRequiredFieldsAreEnforced` (6 fields × missing/empty/space/NBSP); e2e `student required fields are enforced in the UI` |
| AC-003-01 | `FoundationIT.formConfigListsOnlyActiveOptions`; `CatalogLoaderTest` |
| AC-003-02 (P-04) | `RegistrationApiIT.invalidSelectionsAreRejected` (unknown/inactive/wrong group/duplicate) |
| AC-003-03 (P-05) | `CatalogChangeIT`; runtime `m3-catalog-restart.sh` + e2e `@catalog-changed` (same image digest, new catalog shown/enforced) |
| AC-003-04 | `CatalogLoaderTest.invalidCatalogsFailFast`, `missingFileFailsFast` |
| AC-003-05 (P-06) | `RegistrationApiIT.requiredConsentIsEnforced`; `RegistrationForm.test.tsx` (unchecked, blocked); e2e `@catalog-changed` consent tests (UI + API) |
| AC-005-01 | `RegistrationApiIT` (DB row == file, SHA-256); runtime probes `assert_stored` |
| AC-005-02 (P-07) | `RegistrationApiIT.repeatedRequestIdIsIdempotent`, `concurrentDuplicateRequestsCreateOneRegistration`; runtime `idempotency`; `RegistrationForm.test.tsx` request-ID reuse |
| AC-005-03, -04 (P-07) | `StorageFailureIT` (fault injection); runtime `storage` (read-only backup dir; PostgreSQL stopped) |
| AC-005-05 (P-07) | `StorageFailureIT.reconciliation*` |
| AC-005-06 (P-10) | runtime `recreate` (`docker compose down` + `up`, rows and JSON hashes unchanged) + e2e after recreation |
| AC-006-01, AC-007-01 | `NotificationIT.deliversParticipantConfirmationAndOrganizerNotificationWithJsonAttachment` (GreenMail); runtime `mail` (Mailpit, attachment == file) |
| AC-006-02, AC-007-02 (P-08) | `NotificationIT.smtpOutageKeepsRegistrationAndRetriesAfterRecovery`, `permanentFailureIsRecorded`, `OutboxBackoffTest`; runtime `mail` (Mailpit stopped/started) |
| AC-008-01, -02 (P-09) | `ExportIT`; runtime `export` (401 ×3 without data; workbook parsed == DB) |
| AC-X-01 | `CaptchaConfigurationTest`; runtime `prod-isolation` (4 fail-closed startups) |
| AC-X-02 | `OrganizerProperties` validation (startup fails without credential; exercised by runtime `prod-isolation` env) |
| AC-X-03 | `RateLimitFilterTest`; `SecurityIT.oversizedBodyIsRejected` |
| AC-X-04 | `SecurityIT.wrongMediaTypeAndMalformedJsonAreRejectedWithoutDetails`, `malformedEmail…WithoutEchoingValue`, `ExportIT.unauthorizedRequestsRevealNoData` |
| AC-X-05 | `FoundationIT.readinessAndLivenessAreUp`; `SecurityIT.healthDoesNotExposeDetails`; Docker healthcheck |
| AR-02 boundaries | `ArchitectureTest` (module matrix, no cycles, export uses query API only) |
