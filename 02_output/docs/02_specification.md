# Specification

> Written in: phase 2 · Source: `01_acceptance-criteria.md`, `project/00_setup/tech-stack.md`, `project/00_setup/environments.md`, `project/02_design/*`, `general/standards.md` · Agent: writes

Contracts: [`openapi.yaml`](02_contracts/openapi.yaml) · [`database.sql`](02_contracts/database.sql) · [`json-copy.schema.json`](02_contracts/json-copy.schema.json) · [`conference-options.schema.json`](02_contracts/conference-options.schema.json) · [`emails.schema.json`](02_contracts/emails.schema.json) · [`recaptcha.schema.json`](02_contracts/recaptcha.schema.json) · [`registration-form.schema.json`](02_contracts/registration-form.schema.json). Validated by `verify.sh <phase> contracts`.

## 1. Components

| Component | Responsibility | Runs as |
|---|---|---|
| backend (`02_output/backend`) | REST API (`openapi.yaml`): form setup, validation, anti-automation verification, storage (DB + JSON copy), emails, organizer export | Spring Boot jar in `eclipse-temurin` JRE image, user `app` (uid 10001), port 8080 |
| frontend (`02_output/frontend`) | Single-page form (`registration-form.schema.json`), confirmation view; calls only relative `/api/...` (AR-01) | static `dist/` served by `nginx` image as user `nginx`, port 8080; proxies `/api/` to the backend |
| postgres, mailpit | database; local mail catcher | tech-stack images, compose only |

## 2. Backend internal architecture (AR-02, AR-03)

Layered by technical role under `si.konferenca.registration`. Chosen because the domain is small (one aggregate, one export) and the main risks are ordering (store before notify) and keeping web, persistence and external I/O apart, which layers express directly.

| Layer (package) | Contents | May use |
|---|---|---|
| `web` | controllers, request/response DTOs, `ProblemDetail` exception handler, rate-limit and body-size filters | `application`, `domain` |
| `application` | `RegistrationService` (validate → store → notify), `ExportService`, `RegistrationValidator`, ports (interfaces) `JsonCopyStore`, `Notifier`, `CaptchaVerifier` | `domain` |
| `domain` | `Registration`, `RegistrationType`, `OptionCatalog`, `ConferenceOption`, `OptionCategory`, `Text` (whitespace/charset rules) | nothing in the project |
| `infrastructure` | JPA entities and repositories, file JSON copy store, SMTP notifier, reCAPTCHA HTTP client, POI workbook writer, `@ConfigurationProperties` records | `application` (implements its ports), `domain` |
| `config` | Spring Security, CORS, async executor, startup guard | everything; used by nothing |

ArchUnit (`ArchitectureTest`, phase 5) checks exactly: the layer table as `layeredArchitecture()` (`config` not accessed by any layer); `domain` has no Spring, Jakarta Persistence or POI imports; `web` does not access `infrastructure`; `slices().matching("si.konferenca.registration.(*)..").should().beFreeOfCycles()` (AR-03); entities live only in `infrastructure.persistence`.

## 3. Registration flow (AR-05, BR-06, BR-07, D-14)

1. `POST /api/registrations` passes the rate-limit filter (SR-03) and the body-size filter, then Jackson binding (`FAIL_ON_UNKNOWN_PROPERTIES`; malformed → 400 `MALFORMED_REQUEST`).
2. `RegistrationValidator` (§4) collects every field error; any error → 400 `VALIDATION_FAILED`, nothing stored.
3. `CaptchaVerifier` (SR-01) runs after field validation so invalid forms do not consume tokens; failure → 400 field `recaptchaToken` `RECAPTCHA_FAILED`; verify endpoint unreachable/timeout → 503.
4. In one DB transaction: duplicate check on `email_normalized` (lower-case of the trimmed email; D-16) → 409; insert row and options (`database.sql`); `flush()`; write the JSON copy (`json-copy.schema.json`) to `<dir>/.tmp-<id>` with `fsync`, then atomic move to its final name. Any failure → rollback, temp file removed → 503 (AC-005-03/04). The unique constraint also catches concurrent duplicates → 409.
5. If the commit fails after the file was moved, the file is deleted before the error response (no orphan copy).
6. After commit, the two emails (`emails.schema.json`) are handed to a bounded async executor; the response 201 does not wait for them. A send failure logs `registrationId` and the exception class only (ES-07).
7. 201 `{registrationId, receivedAt}`; only then does the frontend show the confirmation.

`receivedAt` and `consent.givenAt` are the server time of acceptance (UTC, millisecond precision).

## 4. Validation (SB-01, BR-02..BR-05, KP-03)

| Rule | Backend and frontend behaviour |
|---|---|
| Trim | Remove leading and trailing characters where `Character.isWhitespace(c) \|\| Character.isSpaceChar(c) \|\| c == '﻿' \|\| c == '\u0085'` (covers U+00A0, U+2007, U+202F, U+3000). Frontend uses the same set as one regex in `src/validation`. The trimmed value is validated, stored, copied and mailed. |
| Required | After trim, empty → `REQUIRED`. External: first name, last name, email, organization. Student: first name, last name, email, study institution, study programme, student ID. |
| Other type's fields | Non-empty after trim → `NOT_ALLOWED_FOR_TYPE`. |
| Length | Code points after trim > max in `openapi.yaml` → `TOO_LONG`. |
| Characters | Any Unicode control (`Cc`) or format (`Cf`) character, or U+2028/U+2029, in a text field → `INVALID_CHARACTERS` (prevents header/markup injection, SR-05). All letters incl. č, š, ž, ć, đ accepted (BR-03). |
| Email | Trimmed, ≤ 254, matches `^[^\s@]+@[^\s@]+\.[^\s@]+$` and has no `Cc` → else `INVALID_EMAIL`. |
| Options | Each id: not in catalog → `UNKNOWN_OPTION`; inactive → `INACTIVE_OPTION`; not offered to the type → `OPTION_NOT_OFFERED` (SR-04, D-12). Duplicates in the list → 400 (schema `uniqueItems`, `MALFORMED_REQUEST`). Any number per category (D-15). |
| Consent | `consentGiven != true` → `CONSENT_REQUIRED`; the form checkbox starts unchecked (BR-05, SB-14). |

## 5. Configuration (ES-01, AR-04)

Spring profiles: `local` (compose, `npm run dev`) and `test` supply the defaults that `environments.md` allows; with no profile (production) every setting without a "yes" default must be set or startup fails.

| Env var | Meaning | Default (profile) |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `POSTGRES_PASSWORD` | JDBC URL, user, password (secret) | `jdbc:postgresql://postgres:5432/registration`, `registration` (local) |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_STARTTLS`, `SMTP_USERNAME`, `SMTP_PASSWORD` | SMTP server | `mailpit`, `1025`, `false` (local); prod requires STARTTLS `true` (guard) |
| `MAIL_FROM` | sender address | `registration@konferenca.local` |
| `CONFERENCE_NAME` | shown in emails and the form | `Konferenca 2026` |
| `OPTIONS_FILE` | YAML per `conference-options.schema.json`, imported as `spring.config.import` | `classpath:conference-options.yaml` (local, test) |
| `JSON_COPY_DIR` | directory on a persistent volume | `/data/json-copies` |
| `RECAPTCHA_TEST_MODE`, `RECAPTCHA_SITE_KEY`, `RECAPTCHA_SECRET_KEY`, `RECAPTCHA_VERIFY_URL` | SR-01/02 | `false`, —, —, Google siteverify; `true` in local/test |
| `ORGANIZER_USERNAME`, `ORGANIZER_PASSWORD`, `ORGANIZER_EMAILS` | export login (secret), notification recipients (comma separated) | none |
| `ORGANIZER_HTTPS_ONLY` | SR-06 | `true`; `false` only in local |
| `CORS_ALLOWED_ORIGIN` | dev server origin | empty (no CORS); `http://127.0.0.1:5173` (local) |
| `RATE_LIMIT_REGISTRATION_PER_MINUTE`, `RATE_LIMIT_EXPORT_PER_MINUTE`, `RATE_LIMIT_OPTIONS_PER_MINUTE`, `MAX_REQUEST_BYTES` | SR-03 | `30`, `20`, `120`, `16384` |

Startup guard (`config.StartupGuard`, fails the context) when neither `local` nor `test` is active: test mode on, empty site/secret key, `ORGANIZER_HTTPS_ONLY=false`, `SMTP_STARTTLS=false`, password shorter than 16, or a non-empty CORS origin. Option file problems (unknown category, duplicate id, blank name, bad id) fail binding/validation at startup (AC-003-04). Options change by editing the file and restarting (AC-003-01).

## 6. Security controls

| Control | Implementation |
|---|---|
| Organizer access (BR-08, SB-02, SB-03) | Spring Security HTTP Basic on `/api/admin/**` only, realm `organizer`; the configured password is BCrypt-hashed at startup into an in-memory user; `/api/options`, `/api/registrations` permitAll; everything else under `/api` denied; actuator only `health` exposed, not proxied by nginx. Stateless (no session, no CSRF token needed: no cookie auth). 401 problem JSON on failure. |
| SR-06 | Filter before authentication on `/api/admin/**`: if `ORGANIZER_HTTPS_ONLY` and `!request.isSecure()` and the client address is not loopback → 403 `HTTPS_REQUIRED`. `server.forward-headers-strategy=native` (Tomcat RemoteIpValve, default internal-proxy ranges) makes `isSecure()` and the client address reflect the trusted proxy's `X-Forwarded-Proto`/`X-Forwarded-For`. |
| SR-03 / SB-06 | In-memory fixed-window counter per client address and endpoint group (limits in §5) → 429 with `Retry-After`; window map bounded (evicts expired windows). Body-size filter: `Content-Length` > `MAX_REQUEST_BYTES` → 413; streamed bodies capped at the same size. Nginx `client_max_body_size 32k`. Limits are generous enough for a shared NAT (a class of students). |
| SR-01/02 | §3 step 3, `recaptcha.schema.json`, startup guard. |
| SR-05 / SB-05 | Plain-text emails; subjects and recipients only from configuration; control characters rejected (§4); JPA parameter binding; React escaping (no `dangerouslySetInnerHTML`); export cells written as string cells (never formulas, AC-008-06). |
| SR-07 | Export and organizer email are built from an explicit column/field list (no `id` internals other than registration id, no file paths). |
| SB-07 / ES-07 | `ProblemDetail` handler: fixed titles, no exception messages; logs contain ids and exception classes, never field values or emails. `server.error.include-*=never`. |
| SB-10 / KP-02 | Backend (Spring Security headers) on every `/api` response: `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, `Cache-Control: no-store`. Frontend nginx: an include file with the full header set (CSP allowing `'self'` plus `https://www.google.com/recaptcha/`, `https://www.gstatic.com/recaptcha/` for scripts and frames) added in every static `location`, none at `server` level and none in `location /api/` (no duplicates). |
| KP-01 | nginx `proxy_set_header Host backend;`, `absolute_redirect off;`, no `$host`/`$http_host` anywhere. |
| SB-11 | Both images run as non-root; compose `read_only` root FS for frontend with tmpfs for nginx temp dirs; `no-new-privileges`. |
| SB-12/13 | Only the fields in `business-rules.md`; retention per D-17 (backend README procedure). |
| SB-04 | TLS terminated by the external nginx in production (manual test, release notes); SMTP STARTTLS enforced in production by the guard. |
| SB-08/09 | `verify.sh` depscan, semgrep, gitleaks (phase 6). |

## 7. Error behaviour

| Situation | Response | Frontend |
|---|---|---|
| Field errors | 400 `VALIDATION_FAILED` + `errors[]` | message under each field (`registration-form.schema.json` `x-messages`), values kept |
| Duplicate email | 409 `DUPLICATE_EMAIL` | message at the email field |
| Option errors | 400 with `optionIds[n]` field | general option message above the options |
| 413/415/429/500/503, network error | problem JSON (fixed text) | `form-error` general message, values kept, no confirmation |
| Email failure after storage | 201 (D-14) | confirmation |
| Options cannot be loaded | — | general error instead of the form, retry button |

## 8. Frontend

React 19 function components, no router, no state library. Modules: `src/api` (only module calling `fetch`, paths start with `/api/`), `src/validation` (§4 rules), `src/components` (`RegistrationForm`, `OptionGroups`, `Captcha`, `Confirmation`), `src/App.tsx`. Client validation runs on submit and on blur (NFR-03). Captcha: loads Google's script only when `testMode` is false. UI text English (decision below). Accessible: labels bound to inputs, errors via `aria-describedby`, `role="alert"` on the general error.

## 9. Deployment and environments

- `02_output/docker-compose.yml`: `postgres` (volume `pgdata`, healthcheck `pg_isready`), `mailpit` (UI 127.0.0.1:`${MAILPIT_PORT:-8025}`), `backend` (profile `local`, volume `jsoncopies:/data/json-copies`, healthcheck `wget` `/actuator/health/readiness`, depends on healthy postgres), `frontend` (127.0.0.1:`${FRONTEND_PORT:-8090}`, healthcheck, depends on healthy backend). Secrets via `--env-file ../.env` (README). Named volumes survive `down`/`up` (NFR-02); ports bound to 127.0.0.1 only.
- Actuator: liveness/readiness groups enabled, `show-details: never` (ES-09, NFR-04).
- Backend Dockerfile: build stage `eclipse-temurin:21.0.10_7-jdk-alpine` running `./mvnw package` (D-19), runtime `eclipse-temurin:21.0.10_7-jre-alpine`. Frontend Dockerfile: `node:24.13.0-alpine` `npm ci && npm run build`, runtime `nginx:1.30.5-alpine`.
- Production: same images behind the external nginx (TLS, `/` → frontend, `/api` → backend); no compose profile `local`.

## 10. Test architecture (for phases 3 and 5)

- Backend acceptance (`src/test/java/.../acceptance/`): `@SpringBootTest(webEnvironment=RANDOM_PORT)` over real HTTP, Testcontainers `postgres:16.15-alpine` and `axllent/mailpit:v1.31.1` (GenericContainer, mail checked through the Mailpit API), JSON copy dir in a temp folder, reCAPTCHA test mode; production reCAPTCHA path against a JDK `HttpServer` stub (DoD-P05). Storage-failure ACs make the JSON copy dir unwritable or stop the DB container.
- Frontend acceptance (`tests/acceptance/`): Vitest + Testing Library against the rendered `App` with `fetch` stubbed at the `/api` boundary.
- End-to-end (`tests/e2e/`, Playwright Chromium): global setup starts a fresh compose project `regtest-e2e` (`docker-compose.yml` + `docker-compose.e2e.yml`, ports 18090/18025, test-only credentials, `down -v` in teardown) — never an already running server (KP-08).
- `docker-java.properties` with `api.version` only if Testcontainers cannot reach the engine (KP-06).

## 11. Choices the inputs leave open

| Choice | Decision | Reason |
|---|---|---|
| Options mechanism (AR-04) | YAML file imported as Spring config | no new dependency; validated binding gives AC-003-04 |
| Consent wording location | in the options file | same change process as options (D-13) |
| Option name/category in DB | copied per registration | export stays correct after reconfiguration |
| Emails timing | async after commit | storage first; response not delayed by SMTP (D-14) |
| Duplicate detection | unique `email_normalized` | race-safe (D-16) |
| Organizer auth | HTTP Basic + BCrypt in memory | one role, one operation; no IdP (security-requirements) |
| Rate limiting | in-process fixed window | single backend instance; no new dependency |
| UI language | English | inputs are English; labels in one place for later translation |
| Contract validators | redocly/cli 2.57.0, python3-jsonschema 4.10.3 (host) | D-18 |
| Backend build image | `eclipse-temurin:21.0.10_7-jdk-alpine` | D-19; clean-checkout `docker compose up` without a host JDK |
| Local ports | 8090 frontend, 8025 Mailpit; e2e 18090/18025 | host 8088/8026/5432 already in use by other projects |

## Traceability

| AC / SR / AR | Section |
|---|---|
| AC-001-01, AC-002-01, AC-001-12, AC-001-13 | §3, §4, `openapi.yaml` `createRegistration` |
| AC-001-02, AC-001-03, AC-001-04, AC-002-02, AC-002-03 | §4 Trim, Required |
| AC-001-05, AC-002-04 | §4 Email |
| AC-001-06, AC-008-05 | §4 Characters; NFR-01 |
| AC-001-07, AC-001-08, AC-002-05, AC-002-07 | §4 Options, SR-04 |
| AC-001-09, AC-001-10, AC-002-08 | §4 Consent, `registration-form.schema.json` |
| AC-001-11, AC-002-06, AC-002-09 | §8, `registration-form.schema.json`, `getRegistrationSetup` |
| AC-001-14 | §3 step 3, `recaptcha.schema.json` |
| AC-001-15 | §3 step 4, `database.sql` `uq_registration_email` |
| AC-003-01..04 | §5, `conference-options.schema.json` |
| AC-004-01..03 | §3 step 7, §7 |
| AC-005-01, AC-005-02, AC-005-05 | §3 step 4, `database.sql`, `json-copy.schema.json`, §9 volumes |
| AC-005-03, AC-005-04 | §3 steps 4–5 |
| AC-006-01..03, AC-007-01..03 | §3 step 6, `emails.schema.json` |
| AC-008-01..06 | §6 organizer access, SR-07, `exportRegistrations` |
| SR-01, SR-02 | §3, §5 guard, §6 |
| SR-03, SR-04, SR-05, SR-06, SR-07 | §6, §4 |
| SB-01..SB-14 | §4 (SB-01), §6 (SB-02..SB-13), phase 6 scans (SB-08, SB-09) |
| NFR-01 | §4, §10 e2e with č/š/ž |
| NFR-02 | §9 named volumes |
| NFR-03 | §8, §7 |
| NFR-04 | §9 actuator and healthchecks |
| AR-01 | §1, §8 `src/api` |
| AR-02, AR-03 | §2 |
| AR-04 | §5, §11 |
| AR-05 | §3 steps 4–7 |
| AR-06 | `database.sql` as Flyway V1 |
| AR-07 | §5 site key via `/api/options`, §8 |
| KP-01, KP-02 | §6 |
| KP-03 | §4 Trim |
| KP-04 | repository `.gitattributes`; manifest hashes LF |
| KP-05 | short paths under `02_output/`; root README clone note (phase 7) |
| KP-06 | §10 |
| KP-07 | phase 0 D-08/D-09 procedure, reused in phase 6 |
| KP-08 | §10 e2e fresh compose project |
