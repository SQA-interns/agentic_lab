# Specification

> Written in: phase 2 · Source: `01_acceptance-criteria.md`, `project/stack.md`, `project/constraints.md`, `standards/security.md` · Procedure: `skills/write-specification` · Agent: writes

Contracts (`docs/02_contracts/`) hold every interface detail; this document links to them and does not repeat them.

## 1. Components

| Component | Responsibility | Contracts |
|---|---|---|
| frontend (React, Vite, served by nginx) | single-page form for both registration types, client-side validation, anti-automation widget, confirmation | `ui-registration-form.json`, `openapi.yaml` |
| backend (Spring Boot) | form configuration, validation, anti-automation verification, storage (database + raw JSON copy), emails, organizer export, health | `openapi.yaml`, `registration-storage.sql`, `registration-copy.schema.json`, `conference-config.schema.json`, `email-messages.json`, `recaptcha-siteverify.openapi.yaml` |
| PostgreSQL 16 | registration records | `registration-storage.sql` |
| Mailpit (local, test) / SMTP server (production) | email delivery | `email-messages.json` |

The frontend reaches the backend only through `/api` (AR-01): locally the frontend nginx proxies `/api` to the backend; in production the external reverse proxy does.

## 2. Backend internal architecture (AR-02)

Declared style: a strict layered architecture in packages under `si.konferenca.registration`. Reason: one small service with two use cases (register, export); layers give a structure that ArchUnit can check exactly.

| Package | Contains | May depend on (application packages) |
|---|---|---|
| `web` | REST controllers, request/response records, exception handler, rate-limit and body-size filters, Spring Security configuration | `service`, `domain`, `config` |
| `service` | `RegistrationService`, `ExportService`, business-rule validation, Excel writing | `persistence`, `integration`, `domain`, `config` |
| `persistence` | Spring Data repositories, `JsonCopyStore` (file writer) | `domain`, `config` |
| `integration` | `AntiAutomationVerifier` (reCAPTCHA client, test mode), `MailNotifier` | `domain`, `config` |
| `config` | configuration properties, conference catalog loader, startup guards | `domain` |
| `domain` | JPA entities and value types (`Registration`, `RegistrationType`, `Category`, `ConferenceOption`, `ConsentDefinition`) | none |
| root | `RegistrationApplication` only | any |

ArchUnit rules (written in phase 3, checked in phase 6, DoD-04):

1. The layer table above, as `layeredArchitecture()`, with nothing accessing `web`.
2. No cycles between top-level packages (AR-03, `slices().matching("si.konferenca.registration.(*)..")`).
3. `@RestController` classes only in `web`; Spring Data `Repository` types only in `persistence`; `JavaMailSender` and `java.net.http` only in `integration`; `org.apache.poi` only in `service`; `domain` uses no Spring class.
4. Schema changes only through Flyway: `src/main/resources/db/migration` holds `V1__registration.sql`, identical to `registration-storage.sql` (AR-06); `spring.jpa.hibernate.ddl-auto=validate`.

## 3. Registration flow

`POST /api/registrations` (`openapi.yaml`), in this order:

1. Filters: rate limit per client address (SR-03), body size limit, JSON content type.
2. Anti-automation (SR-01): the token is verified on the backend before any other processing. Live mode calls `recaptcha-siteverify.openapi.yaml`; test mode accepts only `test-mode-pass`. Failure → `CAPTCHA_FAILED`; Google unreachable → `CAPTCHA_UNAVAILABLE`. Nothing is stored.
3. Validation (SB-01), all field errors collected in one response: trim every text value (BR-02); required per type, fields of the other type absent (BR-01); maximum lengths; control characters rejected (D-17); email `local@domain.tld`, one `@`, no whitespace, parsed by `InternetAddress` in strict mode (BR-03); option ids known, active, available to the type, not duplicated, category maxima (BR-04, SR-04, D-11, D-14); every configured consent given (BR-05).
4. Duplicate check (D-15): `email_normalized` = trimmed, lower-cased (`Locale.ROOT`) email; an existing row → `DUPLICATE_EMAIL` (409). The unique constraint catches races and maps to the same error.
5. Storage (BR-07, AR-05), in one database transaction: insert the rows of `registration-storage.sql`; then write the raw JSON copy (`registration-copy.schema.json`) to a temporary file in the copy directory, `fsync`, atomic move to its final name; then commit. A failing file write rolls the transaction back; a failing commit deletes the file. Any storage failure → `STORAGE_FAILED` (503), no confirmation, no email (AC-004-03, AC-005-03).
6. Response 201 `RegistrationAccepted`, only after step 5 succeeded (BR-06).
7. Emails after commit, synchronously, SMTP connect/read timeouts 10 s (D-13): participant confirmation, then one organizer notification to all `ORGANIZER_EMAILS` with the stored JSON bytes attached (`email-messages.json`). A send failure is logged as `registration <id>: <message type> not sent` and does not change the response.

The raw JSON copy is serialized once; the same bytes are written to disk and attached to the organizer email (AC-007-02). Copies are pretty-printed UTF-8 without escaping non-ASCII characters (NFR-01).

## 4. Configuration (ES-01, AR-04)

Environment variables; secrets have no default (ES-02). `APP_ENVIRONMENT` selects the startup guards.

| Variable | Default | Notes |
|---|---|---|
| `APP_ENVIRONMENT` | `production` | `production`, `local` or `test`; the safe value is the default |
| `DB_URL`, `DB_USERNAME`, `POSTGRES_PASSWORD` | none | compose sets them locally |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_STARTTLS` | `localhost`, `1025`, `false` (local/test only) | production must set them; `SMTP_USERNAME`/`SMTP_PASSWORD` optional |
| `MAIL_FROM` | `registration@konferenca.si` | sender address |
| `CONFERENCE_NAME` | `Conference 2026` | shown in the form and emails |
| `CONFERENCE_CONFIG_FILE` | bundled `conference-config.json` (local/test only) | file per `conference-config.schema.json`, validated at startup; invalid → refuse to start |
| `JSON_COPY_DIR` | `/var/lib/registration/json-copies` | persistent volume; created if missing, must be writable at startup (readiness) |
| `RECAPTCHA_TEST_MODE` | `false` | `true` allowed only when `APP_ENVIRONMENT` is `local` or `test` (SR-02) |
| `RECAPTCHA_SITE_KEY`, `RECAPTCHA_SECRET_KEY` | none | required when test mode is off |
| `RECAPTCHA_VERIFY_URL` | `https://www.google.com/recaptcha/api/siteverify` | tests point it at a mock (DoD-P05) |
| `ORGANIZER_USERNAME`, `ORGANIZER_PASSWORD`, `ORGANIZER_EMAILS` | none | password at least 16 characters |
| `ORGANIZER_HTTPS_ONLY` | `true` | `false` allowed only when `APP_ENVIRONMENT` is `local` or `test` (SR-06) |
| `CORS_ALLOWED_ORIGIN` | empty | allowed only in `local` (Vite dev server) |
| `RATE_LIMIT_REGISTRATIONS`, `RATE_LIMIT_EXPORTS`, `RATE_LIMIT_CONFIG` | `20`, `10`, `120` per minute per client | SR-03; generous so a shared campus address is not locked out |
| `MAX_REQUEST_BYTES` | `16384` | SR-03 |

Startup guards (fail fast, message names the variable, never its value): in `production`, test mode on, empty reCAPTCHA keys, HTTPS-only off, a CORS origin, a missing `CONFERENCE_CONFIG_FILE` or mail host, or an organizer password shorter than 16 characters refuse to start (SR-02, SR-06). In every environment missing organizer credentials, an invalid conference configuration or an unwritable JSON copy directory refuse to start.

Option changes (US-003): edit the configuration file and restart; option ids are stable, inactive options stay in the file so older registrations keep their meaning; registrations store the display name and category valid at registration time.

## 5. Security controls

| Control | Design |
|---|---|
| Organizer access (BR-08, SB-02, D-19) | HTTP Basic on `/api/export/**` only. At startup the password is hashed with BCrypt (strength 12) and the plain value is dropped (SB-03). No session (`STATELESS`), no remember-me. Everything else is `permitAll` on exactly the documented paths; any other path is denied. |
| HTTPS-only organizer access (SR-06, SB-04) | Tomcat trusts `X-Forwarded-Proto` from private-network proxies only (`server.forward-headers-strategy=native`). With HTTPS-only on, an export request that is not secure is answered `403 HTTPS_REQUIRED` before the `Authorization` header is read. Locally the flag is off (plain HTTP on 127.0.0.1). |
| Rate limits and size limit (SR-03, SB-06) | In-memory fixed one-minute window per client address (behind the trusted proxy) and endpoint group; `429` with `Retry-After`. Failed organizer logins count against the export limit. Bodies above `MAX_REQUEST_BYTES` → `413` before parsing. |
| Anti-automation (SR-01, SR-02) | §3 step 2; the site key is served by `GET /api/form-config`, the secret never leaves the backend (AR-07). |
| Input validation (SB-01, SR-04) | §3 step 3, independent of the frontend; JSON with unknown properties → `MALFORMED_REQUEST`. |
| Output encoding (SB-05, SR-05) | JPA parameter binding only. Emails are `text/plain; charset=UTF-8`; no participant input in any header except the strictly parsed recipient. Excel cells are string cells (no formulas, AC-008-07). The frontend renders with React text nodes only (no `dangerouslySetInnerHTML`). |
| Minimal export (SR-07) | Export columns and email fields are the ones in `openapi.yaml` and `email-messages.json`; no database id, file name or system data. |
| Headers (SB-10) | Backend: `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, `Cache-Control: no-store`. Frontend nginx: CSP `default-src 'self'; script-src 'self' https://www.google.com/recaptcha/ https://www.gstatic.com/recaptcha/; frame-src https://www.google.com/recaptcha/ https://recaptcha.google.com/recaptcha/; style-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'`, plus the same nosniff, framing and referrer headers. |
| Least privilege (SB-11) | Backend image runs as a dedicated non-root user; nginx runs unprivileged on port 8080; database user is the application user only. |
| Errors and logs (ES-07, SB-07) | Error bodies per `openapi.yaml` `Error`, no stack traces (`server.error.include-*=never`); logs carry registration ids and error codes, never names, emails, tokens or secrets; request bodies are never logged. |
| Personal data (SB-12, SB-13, SB-14) | Only the fields of BR-01 plus options and consents; retention D-16; consent stored with wording and time, never preselected (UI contract). |
| Dependencies and source (SB-08, SB-09) | `verify.sh` dependency, static-analysis and secret scans in phase 6; findings handled by `standards/security.md`. |

## 6. Error behaviour

| Situation | Status, code | Frontend |
|---|---|---|
| field rules violated | 400 `VALIDATION_FAILED` + `fieldErrors` | message next to each field (NFR-03), values kept |
| body not parseable, wrong shape | 400 `MALFORMED_REQUEST` | form error |
| token missing, wrong, rejected | 400 `CAPTCHA_FAILED` | form error, widget reset |
| verification service unreachable | 503 `CAPTCHA_UNAVAILABLE` | form error |
| email already registered | 409 `DUPLICATE_EMAIL` | form error (contact the organizers) |
| too large, wrong media type | 413 `PAYLOAD_TOO_LARGE`, 415 `UNSUPPORTED_MEDIA_TYPE` | form error |
| rate limit | 429 `RATE_LIMITED` | form error |
| storage failure | 503 `STORAGE_FAILED` | general error, no confirmation |
| export: no or wrong credentials / plain HTTP | 401 `UNAUTHORIZED` / 403 `HTTPS_REQUIRED` | n/a |
| anything else | 500 `INTERNAL_ERROR` | general error |
| backend unreachable | none | generic form error |

## 7. Frontend

Modules: `api` (typed fetch client for `openapi.yaml`), `texts` (every string of `ui-registration-form.json`, D-18), `validation` (client copies of the rules in §3 step 3 except options availability, which the UI enforces by not offering them), `AntiAutomation` (live widget loaded from Google with the served site key, or the test-mode checkbox), `RegistrationForm`, `Confirmation`, `App` (loads `/api/form-config`, shows a load error if it fails). Client validation runs on submit only (D-22: an error appearing on blur shifted the layout under the pointer); the submit button is disabled while a request is in flight. Server `fieldErrors` replace client errors for the same field.

## 8. Health and deployment (ES-09, NFR-02, NFR-04)

- Backend: Actuator `/actuator/health/liveness` and `/actuator/health/readiness` (readiness includes the database and the JSON copy directory; the mail indicator is disabled so an SMTP outage does not take the form down, D-13). Not proxied under `/api`; used by container health checks.
- Frontend: nginx serves `/healthz` (static 200) for its health check.
- `02_output/docker-compose.yml` (project name `conference-registration`): `postgres` (named volume `pgdata`), `mailpit`, `backend` (named volume `json-copies`), `frontend` (published on `127.0.0.1:8088`); Mailpit UI and API on `127.0.0.1:8026`; nothing else published. Health checks gate start order. Secrets are passed by `secrets.sh run … -- docker compose up` and referenced as `${KEY:?}`.
- Images: backend = the jar built by `./mvnw package` on `eclipse-temurin:21.0.10_7-jre-alpine`; frontend = `node:24.13.0-alpine` build stage then `nginx:1.30.5-alpine`.

## 9. Choices left open by the inputs

| Choice | Decision | Reason |
|---|---|---|
| Option availability, consents, email failure, limits, duplicates, retention | D-11 to D-16 | open questions OQ-01 to OQ-06 |
| Control characters, UI language | D-17, D-18 | conservative validation; requirements language |
| Organizer authentication | HTTP Basic, D-19 | identity providers out of scope; one read-only operation |
| Conference options mechanism (AR-04) | JSON file validated by a schema, loaded at startup | no code change; schema validation catches mistakes before start |
| Emails sent synchronously after commit | §3 step 7 | deterministic for tests; storage never depends on mail |
| Rate limiter in memory | §5 | one backend instance; no extra dependency |
| Test mode token `test-mode-pass` | `recaptcha-siteverify.openapi.yaml` | deterministic tests without network calls to Google |

## Traceability

| AC / SR / SB / NFR / AR | Section |
|---|---|
| AC-001-01, AC-002-01 | `ui-registration-form.json` (forms); §7 |
| AC-001-02, AC-001-03, AC-002-02, AC-002-03 | §3; `openapi.yaml` createRegistration |
| AC-001-04 to AC-001-07, AC-001-16, AC-002-04 to AC-002-07 | §3 step 3; `openapi.yaml` Error.fieldErrors |
| AC-001-08 to AC-001-10, AC-002-08, AC-002-09 | §3 step 3; `conference-config.schema.json` |
| AC-001-11, AC-001-12, AC-002-10 | §3 step 3; `ui-registration-form.json` (consent) |
| AC-001-13, AC-002-11 | §3 step 2; `recaptcha-siteverify.openapi.yaml` |
| AC-001-14, AC-002-12 | §3 step 4; `registration-storage.sql` (unique) |
| AC-001-15 | §7; `ui-registration-form.json` (fieldError) |
| AC-003-01 to AC-003-04 | §4 (option changes); `conference-config.schema.json`; `openapi.yaml` getFormConfig |
| AC-004-01 to AC-004-03 | §3 step 6; §6; `ui-registration-form.json` (confirmation, formError) |
| AC-005-01 to AC-005-05 | §3 step 5; `registration-storage.sql`; `registration-copy.schema.json`; §8 |
| AC-006-01 to AC-006-05 | §3 step 7; `email-messages.json` |
| AC-007-01 to AC-007-05 | §3 step 7; `email-messages.json`; `registration-copy.schema.json` |
| AC-008-01 to AC-008-07 | §5; `openapi.yaml` exportRegistrations |
| SR-01, SR-02 | §3 step 2; §4 startup guards; `recaptcha-siteverify.openapi.yaml` |
| SR-03 | §5 rate limits |
| SR-04 | §3 step 3 |
| SR-05 | §5 output encoding; `email-messages.schema.json`; D-17 |
| SR-06 | §5 HTTPS-only |
| SR-07 | §5 minimal export; `openapi.yaml` x-export-columns |
| SB-01, SB-05 | §3 step 3; §5 |
| SB-02, SB-03, SB-04 | §5 organizer access, HTTPS-only |
| SB-06 | §5 rate limits |
| SB-07 | §5 errors and logs; §6 |
| SB-08, SB-09 | §5 dependencies and source |
| SB-10, SB-11 | §5 headers, least privilege; §8 |
| SB-12, SB-13, SB-14 | §5 personal data; D-12, D-16 |
| NFR-01 | §3 (JSON encoding); §5 (UTF-8 emails); AC-001-07, AC-005-05, AC-006-02, AC-007-04, AC-008-06 |
| NFR-02 | §8 named volumes |
| NFR-03 | §7; §6 |
| NFR-04 | §8 health |
| AR-01 | §1 |
| AR-02, AR-03 | §2 |
| AR-04 | §4 option changes; `conference-config.schema.json` |
| AR-05 | §3 steps 5 and 6 |
| AR-06 | §2 rule 4; `registration-storage.sql` |
| AR-07 | §5 anti-automation; `openapi.yaml` FormConfig.antiAutomation |
