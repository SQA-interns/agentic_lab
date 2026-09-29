# Specification

> Written in: phase 2 · Source: `01_acceptance-criteria.md`, `project/03_technical/*`, `project/04_security/*`, `project/05_quality/*`, `general/security/*` · Agent: writes

Contracts referenced below live in `docs/02_contracts/`. Versions are in `project/03_technical/tech-stack.md` only.

## 1. System overview

| Component | Role | Runs as |
|---|---|---|
| frontend | React single-page registration form and confirmation | nginx container serving static files; locally also proxies `/api` to the backend |
| backend | REST API under `/api`: options, registration, organizer export; storage, JSON copy, emails | Spring Boot container (eclipse-temurin JRE, non-root user) |
| PostgreSQL | registration store | `postgres` container, named volume |
| Mailpit | local/test SMTP substitute | `axllent/mailpit` container |

The frontend calls the backend only through `/api` (AR-01). Production: an external nginx terminates TLS, serves `/` from the frontend and forwards `/api` to the backend (`environments.md`).

## 2. Backend architecture (AR-02, AR-03)

Declared architecture: **layered with ports**. Root package `si.konferenca.registration`.

| Package | Contains | May depend on |
|---|---|---|
| `api` | REST controllers, request/response DTOs, exception handler | `application`, `domain` |
| `application` | use-case services (register, export, mail retry), ports (interfaces) for storage, JSON copy, mail, captcha, options catalog | `domain` |
| `domain` | registration model (JPA-mapped entities), registration type, option, consent, validation rules | nothing inside the root package; no Spring |
| `infrastructure` | adapters implementing the ports: Spring Data repository, JSON copy file store, SMTP mailer, reCAPTCHA client, options configuration loader, Excel writer | `application`, `domain` |
| `config` | Spring configuration: security, CORS, rate limiting, request-size filter, startup checks, properties | all |

Justification: the ports keep the use cases testable without PostgreSQL, SMTP or Google, and make the configuration-driven option catalogue (AR-04) and the reCAPTCHA test mode (SR-02) swappable adapters. It is small enough for one module.

ArchUnit rules (written in phase 5, run with the unit suite):

- A-1 `domain` does not depend on `api`, `application`, `infrastructure`, `config` or `org.springframework..`.
- A-2 `application` does not depend on `api`, `infrastructure` or `config`.
- A-3 `api` does not depend on `infrastructure`.
- A-4 `infrastructure` does not depend on `api` or `config`.
- A-5 classes annotated `@RestController` reside in `api`; Spring Data repositories reside in `infrastructure`.
- A-6 no cycles between the slices `si.konferenca.registration.(*)..` (AR-03).
- A-7 no class outside `config` reads environment variables or system properties directly (ES-01).

## 3. Configuration (ES-01, AR-04, AR-07)

All settings are Spring properties bound from environment variables. Settings marked "none" have no default and stop the application at startup when missing.

| Property | Environment variable | Default |
|---|---|---|
| `spring.datasource.url` / `username` | `DB_URL` / `DB_USERNAME` | none |
| `spring.datasource.password` | `POSTGRES_PASSWORD` | none (secret) |
| `spring.mail.host` / `port` | `SMTP_HOST` / `SMTP_PORT` | none |
| `spring.mail.username` / `password` | `SMTP_USERNAME` / `SMTP_PASSWORD` | empty (local Mailpit needs none) |
| `app.mail.starttls` | `SMTP_STARTTLS` | `true` |
| `app.mail.from` | `MAIL_FROM` | `registration@konferenca.si` |
| `app.conference-name` | `CONFERENCE_NAME` | `Conference` |
| `app.options-file` | `CONFERENCE_OPTIONS_FILE` | none |
| `app.json-copy-dir` | `JSON_COPY_DIR` | `/data/registrations` |
| `app.organizer.username` / `password` | `ORGANIZER_USERNAME` / `ORGANIZER_PASSWORD` | none (secret) |
| `app.organizer.emails` | `ORGANIZER_EMAILS` | none |
| `app.organizer.https-only` | `ORGANIZER_HTTPS_ONLY` | `true` |
| `app.recaptcha.test-mode` | `RECAPTCHA_TEST_MODE` | `false` |
| `app.recaptcha.site-key` / `secret-key` | `RECAPTCHA_SITE_KEY` / `RECAPTCHA_SECRET_KEY` | empty |
| `app.recaptcha.verify-url` | `RECAPTCHA_VERIFY_URL` | `https://www.google.com/recaptcha/api/siteverify` |
| `app.environment` | `APP_ENVIRONMENT` | `production` |
| `app.cors.allowed-origins` | `CORS_ALLOWED_ORIGINS` | empty (no cross-origin access) |
| `app.rate-limit.registration-per-10-min` | `RATE_LIMIT_REGISTRATION` | `20` per client IP |
| `app.rate-limit.export-per-min` | `RATE_LIMIT_EXPORT` | `10` per client IP |
| `app.max-request-bytes` | `MAX_REQUEST_BYTES` | `16384` |
| `app.mail.retry-interval` / `max-attempts` | `MAIL_RETRY_INTERVAL` / `MAIL_MAX_ATTEMPTS` | `PT5M` / `10` (D-11) |

Startup checks (fail fast, message names the setting, never its value):

- S-1 (SR-02) `APP_ENVIRONMENT=production` with `RECAPTCHA_TEST_MODE=true` → refuse to start.
- S-2 (SR-02) `RECAPTCHA_TEST_MODE=false` with an empty site key or secret key → refuse to start.
- S-3 (SR-06) `ORGANIZER_HTTPS_ONLY=false` with `APP_ENVIRONMENT=production` → refuse to start.
- S-4 (ES-01) organizer username, password (≥ 16 characters) and emails present.
- S-5 (AR-04) the options file exists and is valid against `options-config.schema.json` rules (checked in code): unique ids, known categories, at least one mandatory consent.

Options configuration (AR-04, D-09, D-10, D-12): a JSON file (`02_contracts/options-config.schema.json`) with `options[]` (`id`, `name`, `category`, `active`, optional `registrationTypes`), optional `categoryLimits` and `consents[]` (`id`, `text`, `mandatory`). It is read at startup; changing options means editing the file and restarting. Participant fields are fixed in code and cannot be configured (BR-01). The shipped file is `backend/config/options.json`.

The frontend has no secret; it reads the public reCAPTCHA site key and the test-mode flag from `GET /api/config` (AR-07).

## 4. REST API (AR-01; contract `02_contracts/openapi.yaml`)

| Operation | Access | Success | Errors |
|---|---|---|---|
| `GET /api/config` | public | 200 site key, test mode flag, conference name | — |
| `GET /api/options?type=EXTERNAL\|STUDENT` | public | 200 active options for that type grouped by category, consents, category limits | 400 unknown type |
| `POST /api/registrations` | public + reCAPTCHA | 201 confirmation (reference, type, names, email, selected options, submission time) | 400 field errors, 413 body too large, 415, 429 rate limit, 500 storage failure (generic message) |
| `GET /api/admin/registrations/export` | organizer (HTTP Basic) | 200 `.xlsx` attachment | 401, 403 plain HTTP from non-localhost when HTTPS-only, 429 |
| `GET /actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness` | public | 200 `UP` | 503 `DOWN` |

Error body: `{ "message": string, "errors": [ { "field": string, "message": string } ] }`. `field` uses the request property name (`firstName`, `optionIds`, `consentIds`, `captchaToken`, …). Messages contain no internals (ES-07, SB-07).

## 5. Registration processing

Validation (SB-01, BR-01..BR-05, SR-04), all on the backend, in this order, collecting every field error:

1. Body ≤ `MAX_REQUEST_BYTES`; JSON with no unknown properties.
2. `type` is `EXTERNAL` or `STUDENT`.
3. Every text field is trimmed; required fields of the type are non-empty after trimming; fields of the other type must be absent or empty; maximum 200 characters (email 254); no control characters (rejects `\r`, `\n`, SR-05).
4. Email matches a practical address syntax (local@domain.tld, no spaces or control characters) and parses as a strict `InternetAddress`.
5. `optionIds`: no duplicates; each is a configured option, active, and available to the type; per-category count ≤ the configured limit, if any.
6. `consentIds`: every mandatory consent present; no unknown consent ids.
7. `captchaToken` verified (section 7).

Storage (BR-07, AR-05), in one database transaction:

1. Insert the registration, its options (id, name, category snapshot) and consents (id, wording shown, given-at timestamp).
2. Flush; write the JSON copy (`02_contracts/registration-copy.schema.json`) to `JSON_COPY_DIR/<reference>.json` via a temporary file and an atomic move.
3. Commit. If step 2 fails, the transaction rolls back; if the commit fails, the file is deleted. Only then is 201 returned (BR-06).

Notifications (US-006, US-007, D-11), after the commit: the participant email and the organizer email are sent; each result is stored as `PENDING`/`SENT`/`FAILED` with an attempt count. A scheduled job retries `FAILED`/`PENDING` emails every `MAIL_RETRY_INTERVAL` up to `MAIL_MAX_ATTEMPTS`. A mail failure never changes the HTTP result.

Duplicates by email are allowed (D-13).

## 6. Persistence (ES-08, AR-06)

Flyway migrations in `backend/src/main/resources/db/migration`; Hibernate `ddl-auto=validate`.

| Table | Columns |
|---|---|
| `registration` | `id` bigint identity (internal), `reference` uuid unique (public), `type`, `first_name`, `last_name`, `email`, `organization`, `study_institution`, `study_programme`, `student_id`, `submitted_at` timestamptz, `participant_mail_status`, `organizer_mail_status`, `mail_attempts`, `last_mail_attempt_at` |
| `registration_option` | `registration_id` fk, `option_id`, `option_name`, `category` |
| `registration_consent` | `registration_id` fk, `consent_id`, `consent_text`, `given_at` |

Text columns are `text`/`varchar` in UTF-8 (NFR-01). All queries go through JPA with bound parameters (SB-05).

## 7. Anti-automation (SR-01, SR-02)

`CaptchaVerifier` port with two adapters:

- Production: POST `secret` and `response` to `app.recaptcha.verify-url` over HTTPS (timeout 5 s); accepted only when `success=true`. A timeout or error rejects the registration (400 on `captchaToken`). Tested against a mocked verification endpoint including a rejected token (DoD-P05).
- Test mode (only when `RECAPTCHA_TEST_MODE=true`, never in production, S-1): accepts exactly the token `test-mode-token`, rejects anything else; makes no network call.

The frontend renders the Google v2 widget with the site key, or in test mode a local "I am not a robot (test mode)" checkbox that yields `test-mode-token`. A missing token is rejected by the backend whatever the frontend does.

## 8. Emails (US-006, US-007, SR-05, SR-07; contract `02_contracts/emails.md`)

Plain-text UTF-8 messages, built with `MimeMessageHelper`. Subjects contain only the configured conference name and fixed text; user input appears only in the body and as the participant recipient address (validated, rule 4). No HTML, so markup is shown literally. The organizer email lists the registration data and attaches `registration-<reference>.json`, byte-identical to the JSON copy. Neither message contains database ids or delivery status.

## 9. Organizer export (US-008, BR-08, SR-06, SR-07)

Spring Security HTTP Basic on `/api/admin/**` only, one in-memory user whose password is BCrypt-hashed at startup (SB-03); no sessions; everything else is `permitAll`. A filter before authentication rejects `/api/admin/**` with 403 when `ORGANIZER_HTTPS_ONLY=true`, the request is not secure (as reported by the trusted reverse proxy via `X-Forwarded-Proto`) and the client address is not loopback, so credentials are never processed over plain HTTP (SR-06).

Workbook (Apache POI): one sheet `Registrations`, header row then one row per registration ordered by submission time; columns Reference, Submitted at (UTC), Type, First name, Last name, Email, Organization, Study institution, Study programme, Student ID, Workshops, Events, Meals, Other, Consents. All values are written as string cells, so no value is evaluated as a formula. No internal id or mail status (SR-07).

## 10. Security controls

| Control | Implementation |
|---|---|
| SB-01 | Section 5 validation on the backend |
| SB-02 | Section 9; all other endpoints are intentionally public (`security-requirements.md`) |
| SB-03 | BCrypt hash of the organizer password at startup; secrets only from environment |
| SB-04 | Production TLS at the external nginx; HSTS header on the backend when the request is secure; SR-06 filter |
| SB-05 | JPA parameter binding; React escapes output; plain-text emails; POI string cells |
| SB-06, SR-03 | In-memory token-bucket rate limiter per client IP on `POST /api/registrations` and `/api/admin/**` (429); request-size filter (413) |
| SB-07, ES-07 | Global exception handler returns generic messages; logs carry the registration reference only, never names, emails or secrets |
| SB-08 | Dependency-Check and `npm audit` in phase 6 |
| SB-09 | Semgrep, SpotBugs, PMD, Gitleaks in phase 6 |
| SB-10 | Backend: CSP `default-src 'none'; frame-ancestors 'none'`, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`. Frontend nginx: CSP allowing self and the reCAPTCHA origins, same framing/nosniff/referrer headers |
| SB-11 | Both images run as a non-root user; compose sets `read_only` where possible and `no-new-privileges` |
| SB-12, SB-13 | Only the fields in `business-rules.md` are collected; retention D-14 documented in the backend README |
| SB-14 | Consent checkboxes start unchecked; consent id, wording and timestamp are stored |
| SR-01..SR-07 | Sections 7, 3 (S-1, S-2), 10 (SB-06), 5 (rule 5), 8, 9, 9 |

## 11. Frontend

One page. Registration type radio (external participant, student); the fields of the chosen type; active options for that type as checkboxes grouped under Workshops, Events, Meals, Other; consents as unchecked checkboxes; the captcha widget; Submit. Client-side validation mirrors backend rules 3–4 and mandatory consents, shows messages next to fields and blocks submission (NFR-03). Backend field errors are shown next to the matching fields. After 201 the form is replaced by the confirmation (reference and selected options); after an error no confirmation is shown (BR-06). All text is `lang="sl"`-safe UTF-8.

## 12. Runtime and operations

- `02_output/docker-compose.yml`: `postgres`, `mailpit`, `backend`, `frontend`; ports bound to `127.0.0.1`; named volumes `db-data` and `registration-copies` (NFR-02); health checks on `pg_isready`, backend `/actuator/health/readiness`, frontend `/` (NFR-04, ES-09).
- Readiness includes the database and a writable JSON copy directory.
- The compose file reads secrets from the repository-root `.env` (`env_file`), never from committed files.

## 13. Test approach (details in `03_test-strategy.md`)

| Level | Tool | Scope |
|---|---|---|
| Backend acceptance | JUnit 6 + MockMvc on the full Spring context, Testcontainers PostgreSQL and Mailpit (listed image; its HTTP API reads messages, its chaos API simulates an SMTP outage), temporary JSON copy directory, test-mode captcha, mocked verify endpoint (JDK `HttpServer`) | AC-001..AC-008 backend behaviour, SR-01..SR-07 |
| Frontend acceptance | Vitest + Testing Library against the public UI, `fetch` stubbed at the network boundary | AC-001-08, AC-002-03, AC-003-03, AC-004-01..03, NFR-03 |
| End-to-end | Playwright against the running compose stack, Mailpit API | NFR-01 flow, AC-001-01, AC-002-01, AC-007-01, AC-008-01 |
| Unit / integration | JUnit, Vitest, ArchUnit | phase 5 |

## Traceability

| AC / SR / AR | Section |
|---|---|
| AC-001-01, AC-002-01 | 4, 5 |
| AC-001-02, AC-001-03, AC-001-04, AC-002-02 | 5 (rules 3, 4) |
| AC-001-05 | 5, 6 |
| AC-001-06 | 5 (rule 5) |
| AC-001-07 | 5 (rule 6) |
| AC-001-08, AC-002-03, AC-003-03 | 11 |
| AC-003-01, AC-003-02, AC-003-04, AC-003-05 | 3 (options configuration), 4, 5 (rule 5) |
| AC-004-01, AC-004-02, AC-004-03 | 11, 4 (error body) |
| AC-005-01 | 5 (storage), 6 |
| AC-005-02 | 5 (storage), `registration-copy.schema.json` |
| AC-005-03 | 5 (storage) |
| AC-006-01, AC-006-02, AC-006-03 | 5 (notifications), 8 |
| AC-007-01, AC-007-02 | 8 |
| AC-008-01, AC-008-02, AC-008-03, AC-008-04 | 9 |
| SR-01, SR-02 | 7, 3 (S-1, S-2) |
| SR-03 | 10 (SB-06) |
| SR-04 | 5 (rule 5) |
| SR-05 | 5 (rule 3), 8 |
| SR-06 | 9, 3 (S-3) |
| SR-07 | 8, 9 |
| SB-01 … SB-14 | 10 |
| NFR-01 | 5, 6, 8, 9, 13 (end-to-end) |
| NFR-02 | 12 |
| NFR-03 | 11 |
| NFR-04 | 12, 4 (actuator) |
| AR-01 | 1, 4 |
| AR-02, AR-03 | 2 |
| AR-04 | 3 |
| AR-05 | 5 (storage) |
| AR-06 | 6 |
| AR-07 | 3, 11 |
