# Specification

> Written in: phase 2 · Source: `01_acceptance-criteria.md`, `project/00_setup/tech-stack.md`, `project/00_setup/environments.md`, `project/02_design/*`, `general/standards.md` · Agent: writes

## 1. Components

| Component | Responsibility |
|---|---|
| backend (`02_output/backend`) | REST API under `/api`: form configuration, active options, registration (validate, verify anti-automation, store row and JSON copy, send emails), organizer export; health and readiness. |
| frontend (`02_output/frontend`) | Single-page registration form: type choice, fields, options, consent, anti-automation widget, client validation, confirmation view. Static files served by nginx, which also proxies `/api` to the backend in the local stack. |
| PostgreSQL | Registrations and their selected options. |
| JSON copy volume | One file per accepted registration. |
| Mailpit (local, test) / SMTP server (production) | Receives both emails. |

## 2. Contracts

| Interface (`architecture.md`) | Contract in `docs/02_contracts/` | Parser |
|---|---|---|
| Registration form (UI) | `registration-form.ui.json` (format: `ui-form.schema.json`) | ajv |
| Options, registration, export (REST) | `openapi.yaml` | Redocly CLI |
| Registration storage (SQL) | `registration-schema.sql` | PostgreSQL 16.15 |
| JSON copy (file) | `registration-copy.schema.json` | ajv |
| Conference options (configuration) | `conference-options.schema.json` | ajv |
| Emails (SMTP) | `email-messages.schema.json` | ajv |
| Anti-automation | `recaptcha-verify.schema.json` | ajv |

Examples are in `docs/02_contracts/examples/`. All are validated by `verify.sh <phase> contracts` (`logs/2_contracts.log`).

## 3. Backend internal architecture (AR-02)

Root package `si.konferenca.registration`; four layers, dependencies point inwards only.

| Package | Contains | May depend on |
|---|---|---|
| `domain` | Registration, option and consent types; validation rules; ports (interfaces) for storage, JSON copy, mail, anti-automation, options catalogue, clock | JDK only (no Spring, no persistence, no web) |
| `application` | Use cases: submit registration, list options, form configuration, export | `domain` |
| `adapter.in.web` | Controllers, request and response types, problem mapping, rate-limit and body-size filters | `application`, `domain` |
| `adapter.out.persistence`, `.jsoncopy`, `.mail`, `.captcha`, `.options`, `.excel` | Implementations of the domain ports | `domain` (never each other, never `adapter.in`) |
| `config` | Settings classes, security configuration, start-up checks, wiring | all |

ArchUnit rules written in phase 5 check exactly this table, plus "no package cycles" (AR-03). Reason: the acceptance flow (validate, store, copy, notify) has four outward dependencies; ports keep it testable without them and make the AR-05 ordering visible in one use case.

## 4. Behaviour

### 4.1 Registration (`POST /api/registrations`)

Order of processing; the first failing step ends the request.

| Step | Action | Failure answer |
|---|---|---|
| 1 | Rate limit per client, body-size limit, JSON parsing | 429, 413, 400 (`body`/`malformed`) |
| 2 | Trim every text value; validate all fields, options and consent; collect every field error | 400 with all field errors |
| 3 | Verify the anti-automation token (only when step 2 passed, so invalid input costs no call) | 400 `captchaToken`/`captcha_failed`; 503 if the verification service cannot be reached |
| 4 | In one database transaction: insert the registration and its options, write the JSON copy to a temporary file, force it to disk, move it atomically to `<id>.json`, commit | 503; the transaction is rolled back and the copy file removed |
| 5 | Send the participant email, then the organizer email; each failure is logged with the registration id only | none (D-10) |
| 6 | Answer 201 with the id and acceptance time | |

- Validation rules are in `openapi.yaml` (limits, codes). Unknown properties are rejected (`unknown_field`). Control characters, including CR and LF, are rejected in every text field (`invalid_characters`).
- Email format: one `local@domain` address, no whitespace, a dot in the domain, at most 254 characters. Reason: strict enough for BR-03 without rejecting valid internationalised addresses.
- Options: every identifier must be in the active set (SR-04); a repeated identifier is `option_duplicate` (D-11); an empty list is valid.
- The option name and category are stored with the registration as they were at acceptance. Reason: later configuration changes must not alter stored registrations.
- A second registration with the same email is a new registration (D-12).
- The id is a random UUID; the acceptance time is the backend clock in UTC.

### 4.2 Options and form configuration

- The options file (`conference-options.schema.json`) is read and validated at start-up; the backend refuses to start when it is invalid. A changed file takes effect at the next start (AR-04). Reason: no administration UI is in scope and a restart is a configuration-only change.
- `GET /api/options` returns active options only, in file order. `GET /api/form-config` returns the conference name, the consent wording and the anti-automation mode with the site key (AR-07).
- All active options are offered to both registration types (D-08).

### 4.3 Emails

- Plain text, UTF-8, composed with the mail library's API, never by string-building headers; subjects contain no participant input; values cannot contain line breaks (4.1) (SR-05).
- The organizer attachment is the JSON copy read back byte for byte.
- Sending is synchronous after the commit with a connection and read timeout. Reason: simpler than a queue, and the answer still does not depend on it.

### 4.4 Export (`GET /api/registrations/export`)

- One sheet `Prijave`, one row per registration, ordered by acceptance time. Columns: registration id, accepted at, type, first name, last name, email, organization, study institution, study programme, student ID, workshops, events, meals, other activities (names joined by `; `), consent given at. No other column (SR-07).
- Cells are written as text; a value starting with `=`, `+`, `-`, `@`, tab or carriage return is prefixed with an apostrophe. Reason: prevents formula injection from participant input (SB-05).
- The workbook is built in memory with a streaming writer and answered with `Cache-Control: no-store`.

### 4.5 Frontend

- Behaviour, labels and messages are in `registration-form.ui.json`. Language: Slovenian. Reason: the root package and the localisation requirement indicate a Slovenian audience; the inputs name no other language.
- Client validation repeats required, length and email format only; the backend remains the authority (SB-01).
- In anti-automation mode `recaptcha` the Google script is loaded from `https://www.google.com/recaptcha/api.js`; in mode `test` no request leaves the stack.

## 5. Configuration (ES-01)

Environment variables of the backend; the frontend has none at build time and reads `/api/form-config` at run time.

| Setting | Variable | Default | Notes |
|---|---|---|---|
| Environment | `APP_ENVIRONMENT` | `production` | `local`, `test` or `production` |
| Database URL, user | `DB_URL`, `DB_USER` | none | local and test values are in the compose file and test setup only |
| Database password | `POSTGRES_PASSWORD` | none | secret |
| SMTP host, port, TLS | `SMTP_HOST`, `SMTP_PORT`, `SMTP_STARTTLS` | none, `587`, `true` | local: Mailpit, port 1025, no TLS |
| SMTP account | `SMTP_USERNAME`, `SMTP_PASSWORD` | empty | secret; empty means no authentication |
| Sender address | `MAIL_FROM` | `prijave@konferenca.example` | |
| Conference name | `CONFERENCE_NAME` | `Konferenca` | |
| Organizer recipients | `ORGANIZER_EMAILS` | none | comma separated; at least one |
| Options file | `OPTIONS_FILE` | none | local and test: the example file |
| Consent wording | `CONSENT_TEXT` | `Soglašam z obdelavo svojih osebnih podatkov za namen prijave na konferenco.` | D-09, for human review |
| JSON copy directory | `JSON_COPY_DIR` | `/data/registrations` | on a named volume |
| reCAPTCHA test mode | `RECAPTCHA_TEST_MODE` | `false` | SR-02 |
| reCAPTCHA keys | `RECAPTCHA_SITE_KEY`, `RECAPTCHA_SECRET_KEY` | empty | secret key is a secret |
| reCAPTCHA verification URL | `RECAPTCHA_VERIFY_URL` | Google's URL | tests point it at a mock (DoD-P05) |
| Organizer account | `ORGANIZER_USERNAME`, `ORGANIZER_PASSWORD` | none | secret |
| Organizer HTTPS-only | `ORGANIZER_REQUIRE_HTTPS` | `true` | SR-06 |
| Allowed origin (CORS) | `CORS_ALLOWED_ORIGIN` | empty (no cross-origin access) | local development only |
| Rate limits | `RATE_LIMIT_REGISTRATION`, `RATE_LIMIT_EXPORT`, `RATE_LIMIT_READ` | `10`, `10`, `120` per minute and client | SR-03 |
| Request-size limit | `MAX_REQUEST_BYTES` | `16384` | SR-03 |
| Trusted proxy headers | `TRUST_FORWARDED_HEADERS` | `false` | `true` behind the production reverse proxy |

Start-up checks (the backend refuses to start): a required setting without a default is missing; the options file is invalid; in `production`, test mode is on, a reCAPTCHA key is empty, or `ORGANIZER_REQUIRE_HTTPS` is off; in any environment, test mode is off and a reCAPTCHA key is empty, or a rate limit or the request-size limit is not positive (D-18).

## 6. Security controls

Standard: OWASP ASVS 5.0 Level 1 for both components.

| Control | Design |
|---|---|
| Input validation (SB-01, SR-04) | All rules of 4.1 run in the backend for every request; database constraints repeat the structural ones. |
| Organizer access (SB-02, SB-03, BR-08) | HTTP Basic on the export only; one account from configuration, held in memory only as a bcrypt hash computed at start-up; comparison through the password encoder; stateless, no session, no cookie. Reason: one role and one operation need no session or token service, and identity providers are out of scope. |
| HTTPS-only organizer access (SR-06, SB-04) | With `ORGANIZER_REQUIRE_HTTPS` on, an export request that is not HTTPS (directly or per trusted `X-Forwarded-Proto`) is answered 403 before credentials are read, with no Basic challenge. TLS is terminated by the external proxy. |
| Anti-automation (SR-01, SR-02) | Backend verification for every registration; test mode only by setting, refused in production. |
| Rate and size limits (SB-06, SR-03) | Fixed-window counters per client address in memory, per group: registration, export, read. Body limit enforced while reading. Reason: one backend instance, so no shared store and no new dependency is needed. Failed organizer logins count against the export limit. |
| Output encoding (SB-05, SR-05) | JSON by the serializer; SQL only through JPA parameters; emails as in 4.3; Excel as in 4.4; the frontend renders text only through React (no raw HTML). |
| Errors and logs (SB-07, ES-07) | Problem responses carry fixed texts; stack traces and exception messages never leave the backend. Logs contain the registration id, outcome and error class, never field values, tokens or credentials. |
| Security headers (SB-10) | Backend: `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, `Cache-Control: no-store`. Frontend (nginx): CSP allowing `'self'` plus the reCAPTCHA script and frame origins (`https://www.google.com/recaptcha/`, `https://www.gstatic.com/recaptcha/`), `frame-ancestors 'none'`, `nosniff`, `Referrer-Policy: no-referrer`. |
| CSRF and CORS | No cookie or session exists, so CSRF protection is off; CORS allows only the configured origin, none by default. |
| Least privilege (SB-11) | Both containers run as a non-root user with a read-only root file system where the image allows; the backend writes only to the JSON copy volume and `/tmp`. |
| Dependencies and source (SB-08, SB-09) | Dependency-Check, `npm audit`, Semgrep and gitleaks through `verify.sh`. |
| Secrets (ES-02) | Only from the environment; no default in code; the frontend bundle contains none (AR-07). |

### Personal data (SB-12, SB-13, SB-14)

Only the items of `security-requirements.md` ("Personal data") are collected, for the purposes listed there. The anti-automation call sends the token only, not the client address. Consent is one unticked checkbox; its wording and time are stored with the registration. Retention (D-13): the system deletes nothing by itself; proposed period for human confirmation: until 12 months after the conference, then the organizer deletes the database rows and the JSON copies (procedure in the backend README).

## 7. Error behaviour

| Situation | Answer | Stored | Emails |
|---|---|---|---|
| Invalid input, consent missing, option not selectable | 400, field errors | nothing | none |
| Anti-automation token missing or refused | 400, `captcha_failed` | nothing | none |
| Anti-automation service unreachable | 503 | nothing | none |
| Database or JSON copy write fails | 503, "not received" | nothing (rolled back, copy removed) | none |
| An email fails after storage | 201 | row and copy | the other email is still attempted |
| Limit exceeded | 429 with `Retry-After`, or 413 | nothing | none |
| Export without or with wrong credentials | 401 | | |
| Export over plain HTTP when HTTPS is required | 403 | | |
| Unexpected error | 500, fixed text | nothing | none |

## 8. Runtime and deployment

- `02_output/docker-compose.yml` starts PostgreSQL, Mailpit, backend and frontend; every port is published on 127.0.0.1 only. Named volumes hold the database and the JSON copies (NFR-02).
- Health: `/actuator/health/liveness` and `/actuator/health/readiness` (readiness includes the database); only `health` is exposed. Compose health checks use them, and the frontend waits for a healthy backend (NFR-04, ES-09).
- Backend image: the jar built by the Maven wrapper is copied into the `eclipse-temurin` JRE image. Reason: `tech-stack.md` lists no JDK image. Frontend image: built in the `node` image, served by `nginx` on an unprivileged port; its `/api` route forwards to the fixed host name `backend`, which must resolve on the container network when nginx starts (D-18).
- Schema changes only through Flyway; the first migration equals `registration-schema.sql`; Hibernate validates and never alters the schema (AR-06, ES-08).

## 9. Choices the inputs leave open

| Choice | Decision | Reason |
|---|---|---|
| Options mechanism (AR-04) | JSON file read at start-up | 4.2 |
| Organizer mechanism | HTTP Basic, single configured account | 6 |
| Rate limiter | in-memory fixed window, own code | 6 |
| Language of UI and emails | Slovenian | 4.5 |
| Email delivery | synchronous after commit | 4.3 |
| Client identity for limits | remote address; forwarded header only when trusted by setting | a forged header must not bypass the limit |
| Contract validation tools | Redocly CLI container, ajv from the frontend lock file | D-14 |
| Open questions OQ-01..OQ-06 | D-08..D-13 | `decisions-log.md` |

## Traceability

| AC / SR / SB / NFR / AR | Section or contract |
|---|---|
| AC-001-01, AC-002-01 | 4.1; `openapi.yaml` (`createRegistration`) |
| AC-001-02, AC-002-02, AC-002-12, AC-001-14, AC-002-11 | `registration-form.ui.json` |
| AC-001-03..06, AC-002-03..06 | 4.1; `openapi.yaml` (`Text100`, `Text200`, `Email`, `FieldError`) |
| AC-001-07, AC-001-08, AC-001-10, AC-001-11, AC-002-07, AC-003-04 | 4.1 (options); `openapi.yaml` (`OptionIds`) |
| AC-001-09, AC-002-08 | 4.1; `openapi.yaml` (`Consent`) |
| AC-001-12, AC-002-09 | 4.1 step 3; `recaptcha-verify.schema.json` |
| AC-001-13 | 4.1 (D-12) |
| AC-002-10, AC-003-01, AC-003-02, AC-003-03, AC-003-05 | 4.2; `conference-options.schema.json`; `openapi.yaml` (`getOptions`) |
| AC-004-01, AC-004-02, AC-004-03 | 7; `registration-form.ui.json` (`views`) |
| AC-005-01 | 4.1 step 4; `registration-schema.sql` |
| AC-005-02 | 4.1 step 4; `registration-copy.schema.json` |
| AC-005-03, AC-005-04 | 4.1 step 4; 7 |
| AC-005-05 | 8 (named volumes) |
| AC-006-01, AC-006-02, AC-006-03 | 4.3; 7; `email-messages.schema.json` |
| AC-007-01, AC-007-02, AC-007-03, AC-007-04 | 4.3; 7; `email-messages.schema.json` |
| AC-008-01, AC-008-04, AC-008-05, AC-008-06 | 4.4; `openapi.yaml` (`exportRegistrations`) |
| AC-008-02, AC-008-03 | 6 (organizer access); `openapi.yaml` (401, 403) |
| SR-01, SR-02 | 6; 5 (start-up checks); `recaptcha-verify.schema.json` |
| SR-03 | 6 (limits); 5 |
| SR-04 | 4.1 |
| SR-05 | 4.3; `email-messages.schema.json` |
| SR-06 | 6 (HTTPS-only) |
| SR-07 | 4.4; `email-messages.schema.json` |
| SB-01 | 6 (input validation) |
| SB-02, SB-03 | 6 (organizer access) |
| SB-04 | 6 (HTTPS-only); 8 |
| SB-05 | 6 (output encoding); 4.4 |
| SB-06 | 6 (limits) |
| SB-07 | 6 (errors and logs) |
| SB-08, SB-09 | 6 (dependencies and source) |
| SB-10 | 6 (security headers) |
| SB-11 | 6 (least privilege); 8 |
| SB-12, SB-13, SB-14 | 6 (personal data); `registration-form.ui.json` (`consent`) |
| NFR-01 | 4.1, 4.3, 4.4 (UTF-8 throughout); contract examples |
| NFR-02 | 8 |
| NFR-03 | 4.5; `registration-form.ui.json` (`rules`) |
| NFR-04 | 8 |
| AR-01 | 4.5; `registration-form.ui.json` (`rules`) |
| AR-02, AR-03 | 3 |
| AR-04 | 4.2; `conference-options.schema.json` |
| AR-05 | 4.1 steps 4 to 6 |
| AR-06 | 8; `registration-schema.sql` |
| AR-07 | 4.2; 6 (secrets) |
