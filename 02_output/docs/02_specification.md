# Specification

> Written in: phase 2 · Source: `01_acceptance-criteria.md`, `project/00_setup/tech-stack.md`, `project/00_setup/environments.md`, `project/02_design/*`, `general/standards.md` · Agent: writes

Contracts (normative, validated by `scripts/verify.sh <phase> contracts`):

| Contract | Interface |
|---|---|
| [`api.openapi.yaml`](02_contracts/api.openapi.yaml) | REST: form configuration, registration, export |
| [`ui-form.json`](02_contracts/ui-form.json) ([schema](02_contracts/ui-form.schema.json)) | registration form UI: fields, labels, test ids, states |
| [`database.sql`](02_contracts/database.sql) | PostgreSQL schema (Flyway `V1`) |
| [`registration-copy.schema.json`](02_contracts/registration-copy.schema.json) | raw JSON copy file and organizer attachment |
| [`conference-options.schema.json`](02_contracts/conference-options.schema.json) | conference options configuration file |
| [`emails.schema.json`](02_contracts/emails.schema.json) | participant and organizer emails over SMTP |
| [`recaptcha-verify.openapi.yaml`](02_contracts/recaptcha-verify.openapi.yaml) | backend → Google reCAPTCHA verification |

## 1. Components

| Component | Responsibility |
|---|---|
| backend (`02_output/backend`, Spring Boot) | serves `/api` (contract), validates and stores registrations, writes JSON copies, sends emails, exports Excel, runs retention; exposes `/actuator/health/{liveness,readiness}` |
| frontend (`02_output/frontend`, React + Vite, nginx) | single registration page per `ui-form.json`; talks to the backend only via `/api` (AR-01) |
| PostgreSQL | registration storage on named volume `pgdata` |
| Mailpit | local/test SMTP catcher |

## 2. Backend internal architecture (AR-02, AR-03)

Declared: a layered architecture in root package `si.konferenca.registration`. Reason: one small use case plus an export; layers keep HTTP, rules and I/O separable for unit tests and ArchUnit.

| Package | Contains | May depend on |
|---|---|---|
| `api` | controllers, request/response records, problem-detail exception handler, rate-limit and body-size filters | `service`, `domain`, `config` |
| `service` | registration use case, validation, option catalogue, export, notification, retention | `domain`, `persistence`, `infrastructure`, `config` |
| `domain` | registration model, value types (registration type, category), text normalisation | nothing in the project |
| `persistence` | JPA entities and Spring Data repositories | `domain` |
| `infrastructure` | JSON copy file store, SMTP mail sender, reCAPTCHA verifier, Excel writer | `domain`, `config` |
| `config` | `@ConfigurationProperties` records, startup checks, security configuration | `domain` |

ArchUnit rules (phase 5): exactly the dependency table above as a `layeredArchitecture()`; no layer accesses `api`; `domain` imports no Spring or Jakarta Persistence types; slices `si.konferenca.registration.(*)..` are free of cycles (AR-03).

## 3. Registration flow (AR-05, BR-06, BR-07)

`POST /api/registrations` processing order:

1. Filters: rate limit per client address (SR-03) → 429; body larger than `MAX_REQUEST_BYTES` → 413; content type not JSON → 415; malformed JSON → 400 `malformed`.
2. Normalise: trim Unicode whitespace from every text value (section 4).
3. Validate all fields and collect every field error (section 4) → 400 `ValidationProblem`.
4. Verify anti-automation token (section 8) → 400 with `recaptchaToken: captcha_failed`. Reason for order: cheap local validation first gives the user all field errors at once; the token is single-use, so it is spent only on otherwise valid input.
5. In one database transaction: insert `registration`, `registration_option`, `registration_consent`; flush (unique email → 409, D-15); write the JSON copy to `JSON_COPY_DIR/<id>.tmp`, `fsync`, atomic move to `<id>.json`; commit. If the file write fails the transaction rolls back; if the commit fails the file is deleted. Either failure → 500 with a generic problem and nothing stored (AC-005-03).
6. Respond 201 `{id, receivedAt}`.
7. After commit, an asynchronous executor sends the participant email and the organizer email (section 7). Reason: SMTP latency or failure must not delay or change the confirmation (D-13).

Registration id: random UUID. Reason: not guessable, created before the insert so the file name is known.

## 4. Validation rules (SB-01, BR-01..BR-05, KP-03)

- Whitespace = Unicode `White_Space` property (includes U+0020, U+00A0, U+2000–U+200A, U+202F, U+205F, U+3000, tabs, line breaks). Backend: a `domain` helper trims by code point using that set, not `String.strip()` (which misses U+00A0). Frontend: `String.prototype.trim()` (ECMAScript `WhiteSpace` + `LineTerminator`, includes U+00A0 and U+FEFF; U+FEFF also removed by the backend helper).
- Required per type (BR-01): both: `firstName`, `lastName`, `email`; EXTERNAL: `organization`; STUDENT: `studyInstitution`, `studyProgramme`, `studentId`. A field of the other type that is non-empty → `not_allowed_for_type`. Empty after trim → `required`.
- Lengths (after trim): names 100, email 254, organization and study fields 200, student id 50 → `too_long`. Reason: bounded storage and email size.
- Text: any Unicode letters, marks, digits, punctuation, symbols and spaces; control and format characters (Unicode `Cc`, `Cf`) → `invalid_characters`. Reason: SR-05 (no CR/LF in emails), BR-03 keeps č, š, ž.
- Email (BR-03): exactly one `@`; local part 1–64 chars without whitespace, `"`, `,`, `;`, `<`, `>`; domain of dot-separated labels `[\p{L}\p{N}-]+`, at least two labels, last label 2+ letters → else `invalid_email`. Same rule in the frontend. Normalised email for D-15 = trimmed, `toLowerCase(Locale.ROOT)`.
- Options (BR-04, SR-04): each id must exist in the configuration (`unknown_option`), be active (`inactive_option`), allow the registration type (`option_not_available`, D-11); per category count ≤ configured maximum (`too_many_options`, D-14). Selecting no option is allowed. Errors use field `optionIds`.
- Consents (BR-05): every mandatory consent id present (`consent_missing`); unknown id → `unknown_consent`. Field `consentIds`.
- Unknown JSON properties → 400 `malformed` (strict deserialisation).

## 5. Conference options (AR-04, US-003)

Mechanism: a JSON file at `CONFERENCE_OPTIONS_FILE`, read and validated at startup against [`conference-options.schema.json`](02_contracts/conference-options.schema.json) plus id uniqueness; invalid or missing file → the backend does not start. Changes take effect on restart, no code change (AC-003-02). Reason: simplest configuration-only mechanism; no admin UI is in scope. Participant fields are code (BR-01), not in the file. `GET /api/form` returns only active options. Local and test use `backend/src/main/resources/options/conference-options.local.json` (from the example contract); production must set the path.

## 6. Organizer access and export (BR-08, US-008, SR-06, SR-07)

- HTTP Basic on `GET /api/export` only, one in-memory user from `ORGANIZER_USERNAME` / `ORGANIZER_PASSWORD`; the password is BCrypt-hashed at startup and the plain value discarded (SB-03). Startup fails if either is empty or the password is shorter than 16 characters. Reason: one role, one operation, no identity provider (security requirements).
- Missing or wrong credentials → 401 with `WWW-Authenticate`; failed attempts count against the export rate limit (SB-06).
- HTTPS only (SR-06): with `ORGANIZER_HTTPS_ONLY=true` a request is allowed only when it is secure (`X-Forwarded-Proto: https` from the trusted proxy, via Spring's forwarded-header support restricted to the proxy address range) or comes from a loopback address; otherwise 403 before credentials are checked.
- Workbook (Apache POI, one sheet `Registrations`): header row, then one row per registration ordered by `receivedAt`: id, type, received at, first name, last name, email, organization, study institution, study programme, student id, workshops, events, meals, other activities (display names joined with `; `), consents (`id @ time`). All cells are string cells, so no value is evaluated as a formula. `Content-Disposition: attachment; filename="registrations.xlsx"`, `Cache-Control: no-store`. No other data (SR-07).

## 7. Emails (US-006, US-007, SR-05)

Format per [`emails.schema.json`](02_contracts/emails.schema.json): plain text UTF-8, fixed subjects, recipient is the validated email or `ORGANIZER_EMAILS`. The organizer attachment is the stored JSON copy file read back byte for byte (AC-007-02). Send failures: logged at WARN with the registration id and the exception class only (ES-07); no retry (D-13). Rejected registrations send nothing.

## 8. Anti-automation (SR-01, SR-02)

- Production: the frontend renders the Google reCAPTCHA v2 checkbox with the site key from `GET /api/form`; the backend verifies every token per [`recaptcha-verify.openapi.yaml`](02_contracts/recaptcha-verify.openapi.yaml) (5 s timeout, fail closed).
- Test mode (`RECAPTCHA_TEST_MODE=true`, test and local only): no Google script or call; the frontend shows a checkbox `recaptcha-test` producing the token `test-mode-pass`; the backend accepts only that token.
- Startup check: when `APP_ENVIRONMENT=production` (the default) the backend refuses to start if test mode is on, either key is empty, `ORGANIZER_HTTPS_ONLY=false` or `CORS_ALLOWED_ORIGIN` is set. Reason: a missing setting fails safe.

## 9. Configuration (ES-01)

Environment variables; secrets have no default (`.env`, listed in `secrets.env.example`).

| Variable | Default | Used for |
|---|---|---|
| `APP_ENVIRONMENT` | `production` | `production`, `local` or `test`; enables the section 8 startup checks |
| `DATABASE_URL`, `DATABASE_USER` | none (compose sets local values) | datasource |
| `POSTGRES_PASSWORD` | secret | datasource |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_STARTTLS` | none (compose: `mailpit`, `1025`, `false`) | mail |
| `SMTP_USERNAME`, `SMTP_PASSWORD` | secret, may be empty locally | mail |
| `MAIL_FROM` | `registration@localhost` | sender |
| `CONFERENCE_NAME` | `Conference` | emails, form |
| `CONFERENCE_OPTIONS_FILE` | none (local/test: classpath file) | section 5 |
| `JSON_COPY_DIR` | `/data/registrations` (named volume `jsoncopies`) | section 3 |
| `RECAPTCHA_TEST_MODE` | `false` | section 8 |
| `RECAPTCHA_SITE_KEY`, `RECAPTCHA_SECRET_KEY` | secret | section 8 |
| `RECAPTCHA_VERIFY_URL` | `https://www.google.com/recaptcha/api/siteverify` | section 8, mocked in tests |
| `ORGANIZER_USERNAME`, `ORGANIZER_PASSWORD`, `ORGANIZER_EMAILS` | secret | sections 6, 7 |
| `ORGANIZER_HTTPS_ONLY` | `true` | section 6 |
| `CORS_ALLOWED_ORIGIN` | empty (no CORS) | local Vite dev server only |
| `RATE_LIMIT_REGISTRATIONS_PER_MINUTE` / `RATE_LIMIT_EXPORTS_PER_MINUTE` | `10` / `10` | SR-03 |
| `MAX_REQUEST_BYTES` | `16384` | SR-03 |
| `RETENTION_DAYS` | `365` | D-16 |

Rate limiting: in-process fixed one-minute window per client address and endpoint, 429 with `Retry-After`. Reason: a single backend instance; no extra dependency. Limit 10 per minute keeps a shared campus/NAT address usable (High in the severity scale would be locking out legitimate users).

## 10. Security controls

| Control | Implementation |
|---|---|
| SB-01, SR-04 | section 4, backend authoritative; frontend repeats rules for usability only |
| SB-02, BR-08 | section 6; Spring Security permits `/api/form`, `POST /api/registrations`, `/actuator/health/**`; everything else denied |
| SB-03 | BCrypt in memory; secrets only from environment |
| SB-04 | production TLS at the external nginx; `ORGANIZER_HTTPS_ONLY` (SR-06) |
| SB-05 | JPA parameter binding only; React escapes output; plain-text emails; POI string cells |
| SB-06, SR-03 | section 9 rate limits and body size |
| SB-07, ES-07 | problem details carry fixed titles; stack traces and messages disabled; logs carry registration ids, never names, emails or tokens |
| SB-08 | `verify.sh` Dependency-Check, `npm audit --omit=dev` (D-10) |
| SB-09 | semgrep, gitleaks, SpotBugs, PMD |
| SB-10, KP-02 | backend: Spring Security headers on `/api` (`Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`); frontend nginx: same set on static responses with CSP `default-src 'self'; script-src 'self' https://www.google.com/recaptcha/ https://www.gstatic.com/recaptcha/; frame-src https://www.google.com/recaptcha/ https://recaptcha.google.com/recaptcha/; style-src 'self'; img-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'`. `add_header ... always` only inside the static `location` blocks, never at `server` level and never in the `/api` location, so each header appears once |
| SB-11 | backend image runs as UID 10001; frontend nginx runs as user `nginx` on port 8080; read-only root filesystem where possible |
| SB-12, SB-13 | only the fields in `security-requirements.md` are collected; retention D-16 (daily scheduled job at 03:00 deletes rows and JSON files older than `RETENTION_DAYS`) |
| SB-14 | consents never preselected (`ui-form.json`); stored with wording and time |
| SR-01, SR-02 | section 8 |
| SR-05 | section 4 control characters, section 7 |
| SR-06 | section 6 |
| SR-07 | export columns in section 6; JSON copy and email schemas forbid other fields |
| KP-01 | frontend nginx `/api` location: `proxy_set_header Host backend;` fixed, `proxy_set_header X-Forwarded-Proto $scheme`, no `$host`/`$http_host` anywhere, no redirects built from the request |

## 11. Frontend

One React component tree: `App` loads `GET /api/form` → `RegistrationForm` (type selector, fields, option groups, consents, anti-automation, submit) → `Confirmation`. Client validation mirrors section 4 and blocks submission with errors next to fields; backend `400` field errors are mapped to the same places (NFR-03). Labels in English per `ui-form.json`; `<html lang="en">`. Reason: the requirements and contracts are in English; Slovenian text in data is supported unchanged (NFR-01). API base path is relative `/api`; the site key comes only from the API (AR-07).

## 12. Deployment and runtime (NFR-02, NFR-04, KP-05, KP-06, KP-08)

- `02_output/docker-compose.yml`: `db` (postgres image, volume `pgdata`, `pg_isready` health check), `mailpit` (ports 127.0.0.1:8025 UI, SMTP internal), `backend` (volume `jsoncopies` at `/data/registrations`, health check `wget -qO- http://127.0.0.1:8080/actuator/health/readiness`, `depends_on` db healthy), `frontend` (nginx, published on `127.0.0.1:8081`, proxies `/api` to `backend:8080`, health check on `/`). Only `127.0.0.1` is published. Secrets via `--env-file ../.env`; local settings (`APP_ENVIRONMENT=local`, `RECAPTCHA_TEST_MODE=true`, `ORGANIZER_HTTPS_ONLY=false`) in the compose file.
- Production: the external nginx forwards `/api` to the backend and `/` to the frontend; it sets `X-Forwarded-Proto`.
- Testcontainers: if the Docker API version cannot be negotiated, `src/test/resources/docker-java.properties` sets `api.version` (KP-06, non-blocking decision).
- End-to-end tests start their own stack with a unique compose project name and tear it down (KP-08).
- Paths under `02_output/` stay below 100 characters relative to the repository root (KP-05). `.gitattributes` keeps LF (KP-04).

## 13. Error behaviour

| Situation | Response | User sees |
|---|---|---|
| field errors | 400 `ValidationProblem` | message next to each field |
| anti-automation failed | 400, field `recaptchaToken` | message at the widget, widget reset |
| duplicate email | 409 `urn:problem:email-already-registered` | "This email address is already registered." |
| too large / unsupported type | 413 / 415 | generic form error |
| rate limited | 429 + `Retry-After` | "Too many attempts, please try again in a minute." |
| storage failure, any unexpected error | 500, title "Registration could not be processed" | generic form error; form data kept |
| `GET /api/form` fails | — | form error, no form |

## Traceability

| ID | Section or contract |
|---|---|
| AC-001-01, AC-002-01 | §3, `api.openapi.yaml`, `ui-form.json` |
| AC-001-02, AC-002-02 | `ui-form.json` fields, §4 |
| AC-001-03..AC-001-07, AC-002-03..AC-002-05 | §4 |
| AC-001-08, AC-002-06 | §5, `GET /api/form`, `ui-form.json` option groups |
| AC-001-09..AC-001-11, AC-002-07, AC-002-08 | §4 options, `conference-options.schema.json` |
| AC-001-12, AC-001-13, AC-002-09 | §4 consents, `ui-form.json` consents |
| AC-001-14, AC-002-10 | §8, `recaptcha-verify.openapi.yaml` |
| AC-001-15 | §3 step 5, `database.sql` `uq_registration_email` |
| AC-003-01..AC-003-03 | §5, `conference-options.schema.json` |
| AC-004-01, AC-004-02 | §3, §13, `ui-form.json` confirmation/formError |
| AC-005-01, AC-005-02 | §3, `database.sql`, `registration-copy.schema.json` |
| AC-005-03 | §3 step 5 |
| AC-005-04 | §12 volumes |
| AC-005-05 | §10 SB-13 retention |
| AC-006-01..AC-006-03, AC-007-01..AC-007-04 | §7, `emails.schema.json` |
| AC-008-01..AC-008-04 | §6, `api.openapi.yaml` `/export` |
| SR-01..SR-07 | §10 |
| SB-01..SB-14 | §10 |
| NFR-01 | §4, §11 |
| NFR-02 | §12 volumes |
| NFR-03 | §11, `ui-form.json` |
| NFR-04 | §1, §12 health checks |
| AR-01 | §1, §11 |
| AR-02, AR-03 | §2 |
| AR-04 | §5 |
| AR-05 | §3 |
| AR-06 | `database.sql`, §12 (Flyway `V1`) |
| AR-07 | §8, §11 |
| KP-01, KP-02 | §10 |
| KP-03 | §4 |
| KP-04, KP-05, KP-06, KP-08 | §12 |
| KP-07 | §10 SB-08 (D-08, D-09 suppression file) |
