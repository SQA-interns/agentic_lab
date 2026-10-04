# Specification

> Written in: phase 2 · Source: `01_acceptance-criteria.md`, `project/00_setup/tech-stack.md`, `project/00_setup/environments.md`, `project/02_design/*`, `general/standards.md` · Agent: writes

Contracts (all in `docs/02_contracts/`, validated by `verify.sh <phase> contracts`):

| Interface | Contract |
|---|---|
| Registration form (UI) | `registration-form.ui.json` (schema `ui-contract.schema.json`) |
| Options, registration, export (REST) | `registration-api.openapi.yaml` |
| Registration storage (SQL) | `registration-storage.sql` (= Flyway `V1`) |
| JSON copy (file) | `registration-copy.schema.json` |
| Conference options (configuration) | `conference-options.schema.json`, default `conference-options.example.json` |
| Emails (SMTP) | `email-message.schema.json` |
| Anti-automation (reCAPTCHA verify) | `recaptcha-siteverify.openapi.yaml` |

## 1. Components

| Component | Responsibility | Runs as |
|---|---|---|
| backend | REST API under `/api`; validation, anti-automation check, storage (database + JSON copy), emails, export, organizer tokens, health | Spring Boot jar in `eclipse-temurin` JRE image, user `app` (uid 10001) |
| frontend | One page with both forms; client-side validation; shows backend field errors and the confirmation | static Vite build in `nginx` image, non-root, port 8080 in the container |
| PostgreSQL | registration rows | `postgres` image, named volume `pgdata` |
| Mailpit | local SMTP catcher (local and tests only) | `axllent/mailpit` |

Local stack (`02_output/docker-compose.yml`): frontend `127.0.0.1:8081` (its nginx also proxies `/api` to the backend, so the page and API share one origin as in production), backend `127.0.0.1:8080`, Mailpit UI/API `127.0.0.1:8025`; PostgreSQL is not published. JSON copies on named volume `jsoncopies`. Health checks: backend `/actuator/health/readiness`, frontend `/`, PostgreSQL `pg_isready` (NFR-04, ES-09).

## 2. Backend internal architecture (AR-02, AR-03)

Layered, packages under `si.konferenca.registration`. Reason: four small use cases (form config, register, token, export) do not justify hexagonal mapping layers, but external systems stay behind ports so they can be replaced in tests.

| Layer | Package | Contains | May depend on |
|---|---|---|---|
| api | `..api..` | controllers, request/response DTOs, exception handler, rate-limit and size filters | application, domain |
| application | `..application..` | use-case services, validation rules, ports `CaptchaVerifier`, `JsonCopyStore`, `RegistrationNotifier`, `RegistrationExporter`, `OptionsCatalog`, `OrganizerTokens` | domain |
| domain | `..domain..` | JPA entities `Registration`, `RegistrationOption`, Spring Data repository, enums | `jakarta.persistence`, `org.springframework.data` only |
| infrastructure | `..infrastructure..` | port adapters: reCAPTCHA client, JSON file store, mail sender, Excel writer, options file loader | application, domain |
| config | `..config..` | Spring configuration, properties, security filter chain, startup guards | all |

ArchUnit rules (`ArchitectureTest`): the layer table above as a `layeredArchitecture()`; no cycles between `si.konferenca.registration.(*)..` slices; `application` and `domain` do not use `jakarta.servlet` or `org.springframework.web`; only `infrastructure` uses `org.apache.poi`, `jakarta.mail`, `org.springframework.mail` and `RestClient`.

## 3. Registration flow (AR-05, BR-06, BR-07, D-08)

1. Filters: request body above `MAX_REQUEST_BYTES` → 413; per-client rate limit → 429 (section 6).
2. Parse JSON. Unknown property or wrong JSON type → 400 `MALFORMED_REQUEST`.
3. Validate all fields (section 4) without external calls; collect every field error → 400 `VALIDATION_FAILED`.
4. Verify the anti-automation token (section 6) → 400 `VALIDATION_FAILED` with `captchaToken`/`CAPTCHA_FAILED`. Done after step 3 so a token is not used up by a form with field errors, and before step 5 so an automated client learns nothing about registered addresses.
5. Duplicate email (`email_normalized`) → 409 `EMAIL_ALREADY_REGISTERED`. The unique constraint also catches concurrent duplicates.
6. In one database transaction: insert `registration` and `registration_option` rows, flush, then write the JSON copy (temporary file in the same directory, then atomic move). Any failure rolls back the transaction; a commit failure deletes the written file. → 503 `STORAGE_UNAVAILABLE`, nothing kept, retry possible (AC-004-03, AC-005-03).
7. Return 201 only after the commit.
8. After the commit (same request, before the response is written): send the participant email, then one organizer email per address. Each send is wrapped: a failure is logged with the registration id and exception class only and never changes the response. SMTP connect/read/write timeouts 5 s/10 s/10 s keep the response bounded.

JSON copy: file `<acceptedAt yyyyMMdd'T'HHmmssSSS'Z'>_<id>.json` in `JSON_COPY_DIR`; content per `registration-copy.schema.json`, serialized once; the same bytes are the organizer attachment. `acceptedAt` and `consent.givenAt` are the same server time.

## 4. Validation rules (SB-01, BR-02, BR-03, BR-04, BR-05)

The backend is authoritative; the frontend applies the same rules before submitting (NFR-03). Strings are trimmed (Unicode whitespace) first; nothing else is changed, so č, š, ž and every other letter pass unchanged (NFR-01).

| Field | Rule → code |
|---|---|
| every required text field | empty after trim → `REQUIRED`; contains a control character (Unicode `Cc`, U+2028, U+2029) → `INVALID_CHARACTERS` |
| firstName, lastName | ≤ 100 → else `TOO_LONG` |
| organization, studyInstitution, studyProgramme | ≤ 200 → else `TOO_LONG` |
| studentId | ≤ 50 → else `TOO_LONG` |
| email | ≤ 254 → else `TOO_LONG`; `^[^\s@<>()\[\],;:"\\]+@[^\s@<>()\[\],;:"\\]+\.[^\s@<>()\[\],;:"\\]{2,}$` → else `INVALID_EMAIL` |
| field of the other type (e.g. `organization` on STUDENT) | present and non-null → `NOT_ALLOWED` |
| optionIds | each id configured → else `UNKNOWN_OPTION`; active → else `INACTIVE_OPTION`; no repeats → else `DUPLICATE_OPTION`; empty list allowed (D-09) |
| consentGiven | must be `true` → else `CONSENT_REQUIRED` (BR-05, D-07) |
| captchaToken | missing/blank → `REQUIRED`; failed check → `CAPTCHA_FAILED` |

Email uniqueness key: trimmed, `toLowerCase(Locale.ROOT)` (D-10).

## 5. Configuration (ES-01, AR-04)

Spring profile = environment: `production` (default when none is set), `local`, `test`. Defaults that `environments.md` allows only for local and test live in `application-local.yml` and `application-test.yml`; `application.yml` has none, and no secret has a default anywhere.

| Variable | Meaning | Default |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | environment | `production` |
| `DB_URL`, `DB_USERNAME`, `POSTGRES_PASSWORD` | database | local/test only (password: none) |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_STARTTLS`, `SMTP_USERNAME`, `SMTP_PASSWORD` | mail server; STARTTLS required when on | local/test: Mailpit, STARTTLS off; production: STARTTLS on |
| `MAIL_FROM`, `CONFERENCE_NAME` | sender, name in emails and form | `registrations@localhost`, `Conference` |
| `CONFERENCE_OPTIONS_FILE` | options file (`conference-options.schema.json`) | local/test: bundled copy of `conference-options.example.json` |
| `JSON_COPY_DIR` | JSON copy directory | `/data/json-copies` |
| `RECAPTCHA_TEST_MODE`, `RECAPTCHA_SITE_KEY`, `RECAPTCHA_SECRET_KEY`, `RECAPTCHA_VERIFY_URL` | anti-automation | test mode `false`; verify URL Google's |
| `ORGANIZER_USERNAME`, `ORGANIZER_PASSWORD`, `ORGANIZER_EMAILS` | organizer login, notification recipients (comma separated) | none |
| `ORGANIZER_HTTPS_ONLY` | SR-06 | `true` |
| `CORS_ALLOWED_ORIGIN` | extra allowed origin (Vite dev server only) | empty: no CORS |
| `RATE_LIMIT_REGISTRATIONS`, `RATE_LIMIT_TOKENS`, `RATE_LIMIT_EXPORTS`, `RATE_LIMIT_FORM_CONFIG` | requests per client per minute | 10, 5, 10, 60 |
| `MAX_REQUEST_BYTES` | body limit | 16384 |

Startup guards (backend refuses to start): options file missing, unreadable, not matching the schema, or with duplicate ids; any required secret empty; in `production`: test mode on, either reCAPTCHA key empty, `ORGANIZER_HTTPS_ONLY=false`, or `CONFERENCE_OPTIONS_FILE` unset (SR-02). Outside test mode, empty reCAPTCHA keys also refuse start. Options are read once at start; a change needs a restart and never a code change (AC-003-03).

## 6. Security controls (ASVS 5.0 Level 1)

| Control | Design |
|---|---|
| Anti-automation (SR-01) | Production: frontend renders the reCAPTCHA v2 widget with the site key from `GET /api/form-config`; backend POSTs secret + token to `RECAPTCHA_VERIFY_URL` (`recaptcha-siteverify.openapi.yaml`) with 5 s timeouts; only `success: true` passes; error, timeout or bad body fails. Test mode (SR-02): no Google call; token `test-pass` passes, anything else fails |
| Organizer access (BR-08, SB-02, SB-03) | `POST /api/organizer/token` with username/password; password compared with a BCrypt (strength 12) hash computed at start, username in constant time; returns a 256-bit random token valid 15 min, stored only as SHA-256 hash in memory. `GET /api/organizer/registrations/export` needs `Authorization: Bearer`. Bearer instead of HTTP Basic because Basic sends the password with every request (Semgrep `use-of-basic-authentication`, High, phase 2) |
| HTTPS only (SR-06, SB-04) | Organizer endpoints answer 403 `HTTPS_REQUIRED` before reading credentials unless the request is secure (`X-Forwarded-Proto: https` from the trusted proxy via `server.forward-headers-strategy=native`) or from a loopback address; `ORGANIZER_HTTPS_ONLY=false` is allowed only on local plain HTTP. TLS itself ends at the external nginx |
| Rate limits, size limit (SR-03, SB-06) | In-memory fixed one-minute window per client address and endpoint group (section 5); 429 with `Retry-After`. Body limit checked on `Content-Length` and while reading → 413 |
| Option check (SR-04) | `OptionsCatalog` holds the configured set; ids validated as in section 4 |
| Email safety (SR-05) | text/plain UTF-8 bodies only; subjects and headers contain no user input; addresses are validated and contain no control characters |
| Minimal export (SR-07) | export and organizer email show only the registration fields of `registration-copy.schema.json`; no database ids beyond the registration UUID, no internal fields |
| Security headers (SB-10) | backend: `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, `Cache-Control: no-store`. Frontend nginx: CSP `default-src 'self'; script-src 'self' https://www.google.com/recaptcha/ https://www.gstatic.com/recaptcha/; frame-src https://www.google.com/recaptcha/ https://recaptcha.google.com/recaptcha/; connect-src 'self'; img-src 'self' data:; style-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'` plus the same three headers |
| CSRF, sessions | stateless API, no cookies, no sessions; CSRF protection off because no ambient credential exists |
| Output encoding, queries (SB-05) | React escapes all text; JPA parameter binding only; Excel cells written as string cells (no formulas) |
| Logs and errors (SB-07, ES-07) | logs carry registration id, outcome and exception class, never names, emails or tokens; error bodies per contract with fixed messages; `server.error.include-*` off; generic 500 `INTERNAL_ERROR` for anything unexpected |
| Least privilege (SB-11) | both images run as non-root; database user owns only its schema |
| Personal data (SB-12, SB-13, SB-14) | only the fields of BR-01 Data plus selected options and consent with time; purpose as in `security-requirements.md`; retention per D-11; consent unchecked by default |
| Dependencies, scans (SB-08, SB-09) | `verify.sh` tools `be-depcheck`, `fe-audit`, `semgrep`, `gitleaks` |
| Secrets (SB-03, ES-02) | only from environment; `.env` git-ignored; no default in code |

## 7. Error behaviour

| Situation | Backend | Frontend |
|---|---|---|
| field errors | 400 `VALIDATION_FAILED` + `errors[]` | message under each field (`registration-form.ui.json`), focus first invalid field |
| captcha failed | 400, field `captchaToken` | message at the widget; widget reset |
| duplicate email | 409 | form error `EMAIL_ALREADY_REGISTERED` |
| storage failure | 503 | form error `STORAGE_UNAVAILABLE`; entered values kept |
| rate limited / too large / malformed / other | 429 / 413 / 400 / 500 | form error (`RATE_LIMITED` or `GENERIC`) |
| email failure | none (logged, D-08) | confirmation as usual |

## 8. Frontend

One page (`App`): type radio group, the type's fields, options grouped by category (fieldsets in `registration-form.ui.json` order; empty categories hidden), consent checkbox with the configured text, anti-automation widget, submit button. Data from `GET /api/form-config` at load; a load failure shows the generic error and no form. `src/api.ts` is the only module that calls `fetch`, always relative `/api/...` (AR-01). The reCAPTCHA script is loaded only outside test mode; the site key is the only key in the frontend and comes from form-config at run time, never from the build (AR-07). UI language English (D-12).

## 9. Choices the inputs leave open

| Choice | Reason |
|---|---|
| Options as a JSON file read at start (AR-04) | readable by organizers, schema-validated, mounted as a volume; no admin UI needed |
| Option name and category copied into each registration | export and emails stay correct after a rename (AC-003-04) |
| Emails sent synchronously after commit | storage always first (scope priority 1); deterministic for tests; bounded by timeouts |
| One email per organizer address | one failing address does not stop the others; recipients do not see each other |
| Bearer token for the export | avoids sending the password on every request (section 6) |
| No retry queue for failed emails | not required by any story (D-08); failures are logged for the operator |
| Backend jar built on the host, copied into the JRE image | no Maven/JDK build image is in `tech-stack.md` |
| Tests: Testcontainers PostgreSQL and Mailpit; reCAPTCHA mock with the JDK `HttpServer` | local substitutes from `environments.md` without adding dependencies |

## Traceability

| AC / SR / SB / NFR / AR | Section or contract |
|---|---|
| AC-001-01, AC-002-01 | 3; `registration-api.openapi.yaml` `createRegistration` |
| AC-001-02, AC-002-02 | 8; `registration-form.ui.json` `forms` |
| AC-001-03, AC-001-04, AC-001-05, AC-001-06, AC-002-03, AC-002-04, AC-002-05, AC-002-06 | 4; `registration-api.openapi.yaml` `FieldError` |
| AC-001-07, AC-001-08, AC-001-09, AC-001-10, AC-002-10 | 4 (optionIds) |
| AC-001-11, AC-001-12, AC-002-07 | 4 (consentGiven), 8; `registration-form.ui.json` `consent` |
| AC-001-13, AC-002-11 | 3 step 4, 6 (anti-automation); `recaptcha-siteverify.openapi.yaml` |
| AC-001-14, AC-002-09 | 3 step 5, 4; `registration-storage.sql` `registration_email_normalized_uk` |
| AC-002-08, AC-003-01, AC-003-02 | 5, 8; `registration-api.openapi.yaml` `getFormConfig` |
| AC-003-03 | 5 (options read at start); `conference-options.schema.json` |
| AC-003-04 | 9; `registration-storage.sql` `registration_option` |
| AC-004-01, AC-004-02 | 7, 8; `registration-form.ui.json` `confirmation`, `errors` |
| AC-004-03, AC-005-03 | 3 step 6, 7 |
| AC-005-01, AC-005-04 | 3; `registration-storage.sql`; 1 (named volumes) |
| AC-005-02 | 3; `registration-copy.schema.json` |
| AC-006-01, AC-006-02, AC-006-03 | 3 step 8; `email-message.schema.json` |
| AC-007-01, AC-007-02, AC-007-03 | 3 step 8; `email-message.schema.json` |
| AC-008-01, AC-008-04, AC-008-05, AC-008-06 | 6 (minimal export); `registration-api.openapi.yaml` `exportRegistrations` |
| AC-008-02, AC-008-03 | 6 (organizer access); `createOrganizerToken`, `exportRegistrations` 401 |
| SR-01, SR-02 | 5 (startup guards), 6 |
| SR-03 | 3 step 1, 6 |
| SR-04, SR-05, SR-06, SR-07 | 4, 6 |
| SB-01 | 4 |
| SB-02, SB-03 | 6 (organizer access) |
| SB-04, SB-05, SB-06, SB-07, SB-08, SB-09, SB-10, SB-11, SB-12, SB-13, SB-14 | 6 |
| NFR-01 | 4; `registration-copy.schema.json` |
| NFR-02 | 1 (named volumes) |
| NFR-03 | 4, 7, 8 |
| NFR-04 | 1 (health checks) |
| AR-01 | 1, 8 |
| AR-02, AR-03 | 2 |
| AR-04 | 5; `conference-options.schema.json` |
| AR-05 | 3 |
| AR-06 | `registration-storage.sql` |
| AR-07 | 6 (anti-automation), 8 |
