# Specification

> Written in: phase 2 · Source: `01_acceptance-criteria.md`, `project/00_setup/tech-stack.md`, `project/00_setup/environments.md`, `project/02_design/*`, `general/security/*` · Agent: writes

## 1. System overview

Two components (architecture.md): `backend` (Spring Boot REST API, package `si.konferenca.registration`) and `frontend` (React single page served by nginx). The local stack (`02_output/docker-compose.yml`) adds PostgreSQL and Mailpit. Contracts are in `02_contracts/`:

| Interface | Contract |
|---|---|
| Registration form (UI) | `ui.md` |
| Options, registration, export (REST) | `openapi.json` |
| Registration storage (SQL) | `database-schema.sql` |
| JSON copy (file) | `registration-copy.schema.json` |
| Conference options (file) | `conference-options.schema.json` |
| Emails (SMTP) | `emails.md` |
| Anti-automation | `recaptcha.md` |
| Environment configuration | `configuration.md` |

## 2. Backend architecture (AR-02 declaration)

Layered architecture with ports, chosen because the API has one core use case (register) with four side effects (database, file, SMTP, reCAPTCHA) that tests must be able to replace or fail independently, and because it gives ArchUnit a small, precise rule set.

| Package (under `si.konferenca.registration`) | Contains | May depend on |
|---|---|---|
| `domain` | JPA entities (`Registration`, option and consent rows), `RegistrationType`, `OptionCategory`, option catalog model (`ConferenceOption`, `ConsentDefinition`), `RegistrationRepository` (Spring Data) | nothing else in the application |
| `application` | use cases `RegistrationService`, `ExportService`, `RegistrationValidator`; ports `OptionCatalog`, `JsonCopyStore`, `NotificationSender`, `CaptchaVerifier`, `WorkbookWriter`; application exceptions | `domain` |
| `infrastructure` | adapters: options file loader, JSON copy file store, SMTP sender, Google reCAPTCHA client and test-mode verifier, POI workbook writer | `application`, `domain`, `settings` |
| `web` | REST controllers, request/response DTOs, error handler, rate-limit and request-size filters, organizer HTTPS filter | `application`, `domain`, `settings` |
| `settings` | `AppProperties`, the typed record of all `app.*` settings (ES-01) | nothing else in the application |
| `config` | Spring Security, startup checks, bean wiring | all |

ArchUnit rules (`ArchitectureTest`, checked in phase 5 and 6, DoD-04):

- A1: `domain` depends on no other application package.
- A2: `application` depends only on `domain` (and libraries).
- A3: `infrastructure` does not depend on `web`; `web` does not depend on `infrastructure`.
- A4: nothing depends on `config`; `domain` and `application` do not depend on `settings` (A1, A2).
- A5: no cycles between the top-level packages (AR-03).
- A6: classes annotated `@RestController` live in `web`; `@Entity` in `domain`.

## 3. Conference options (US-003, AR-04)

- Source: JSON file at `app.options-file` (`conference-options.schema.json`). The shipped `classpath:conference-options.json` is for local and test; production mounts its own file (configuration.md).
- Loaded and validated once at startup by the infrastructure loader; any violation (schema, duplicate option or consent id, unknown category, missing name) throws and the backend refuses to start (AC-003-03).
- `OptionCatalog` offers: active options in file order, lookup by id (active or inactive), consents. Changing options = edit the file and restart; the participant fields are fixed in code (BR-01, AC-003-02).
- `GET /api/options` returns active options only and all consents (AC-003-01). All options are available to both types (D-06); there is no per-category limit (D-09).

## 4. Registration use case (US-001, US-002, US-004, US-005, US-006, US-007)

### 4.1 Request handling order

1. Filters: rate limit per client and bucket (5.2), request-size limit (5.3).
2. JSON binding with unknown properties rejected (AC-001-14); a malformed body gives 400 `validation_failed`.
3. `RegistrationValidator` (application): trims every text field (BR-02); checks required fields per type and forbids the other type's fields (BR-01, AC-001-02, AC-001-14, AC-002-02); max lengths as in `openapi.json`; rejects any control character (Unicode category Cc, which includes CR, LF and tab) in every text field (SR-05, AC-006-02); checks email format `^[^\s@]+@[^\s@]+\.[^\s@]+$` after trimming, max 254 (BR-03); checks option ids exist, are active and are unique (BR-04, SR-04, AC-001-05); checks consent ids are known and every required consent is given (BR-05, AC-001-06). All field errors are collected and returned together as 400.
4. reCAPTCHA verification via `CaptchaVerifier` (recaptcha.md); failure gives 400 on `recaptchaToken` (SR-01, AC-001-07, AC-001-08).
5. Duplicate check: an existing registration whose `lower(email)` matches gives 409 on `email` (D-10). The unique index also catches concurrent duplicates, mapped to the same 409.
6. Storage in one database transaction (AR-05, BR-07): insert the registration, option and consent rows, flush, then write the JSON copy file. The file is written to a temporary file in the same directory and moved atomically to `<id>.json`. If the file write fails, the transaction rolls back and the response is 500 `internal_error` (AC-005-03). If the commit fails after the file was written, the file is deleted.
7. After commit: send the participant email and the organizer email (emails.md). Each failure is caught and logged as `Email <kind> failed for registration <id>` without personal data (D-08, AC-006-03).
8. Response 201 with the `Registration` body; the frontend shows the confirmation only then (BR-06, AC-004-01).

### 4.2 Stored data (BR-07, SB-12, SB-13)

- Database: `database-schema.sql`, created by Flyway migration `V1__create_registration.sql` (AR-06, ES-08). JPA runs with `ddl-auto=validate`.
- `id` is a random UUID; `submitted_at` and each consent's `given_at` are the server time of acceptance (UTC).
- JSON copy: `registration-copy.schema.json`, UTF-8, written by Jackson with the fields in schema order and only the fields of the registration's type. The same bytes are attached to the organizer email.
- Personal data items are exactly those of `security-requirements.md`; purpose as stated there; retention D-11 (no automatic deletion; manual deletion described in the backend README).

## 5. Security design

### 5.1 Authentication and authorization (SB-02, SB-03, BR-08, SR-06)

- Spring Security, stateless, no sessions or cookies; CSRF protection is off because no cookie-based authentication exists.
- Public: `GET /api/options`, `GET /api/config`, `POST /api/registrations`, `GET /api/health/**`. Organizer: `GET /api/organizer/**` with HTTP Basic and role `ORGANIZER`. Everything else: denied (404/403 without detail).
- The organizer password from configuration is hashed with BCrypt at startup and only the hash is kept in memory (SB-03); comparison uses the password encoder.
- Organizer HTTPS-only (SR-06): when `app.organizer.https-only` is on, a filter before authentication answers 403 `forbidden` to any `/api/organizer/**` request that is not secure, without evaluating credentials. "Secure" follows `request.isSecure()`, which Tomcat's RemoteIpValve (`server.forward-headers-strategy=native`) sets from `X-Forwarded-Proto` only for requests from internal proxy addresses. Production runs behind nginx with TLS; the local stack sets the flag off (plain HTTP on 127.0.0.1 only).
- 401 responses carry `WWW-Authenticate: Basic realm="organizer"` and the `Error` body; they contain no registration data (AC-008-02).

### 5.2 Rate limiting (SR-03, SB-06, AC-001-10, AC-008-04)

In-memory fixed one-minute windows per client address (`request.getRemoteAddr()`, after RemoteIpValve) and bucket: `registration` (`POST /api/registrations`, default 10), `export` (`/api/organizer/**`, default 10, counted before authentication so failed logins count too), `read` (`/api/options`, `/api/config`, default 120). Over the limit: 429 `rate_limited` with `Retry-After` seconds. The map is bounded (entries of past windows are dropped). Single-instance deployment is assumed (release notes).

### 5.3 Request-size limit (SR-03, AC-001-11)

A filter rejects any request whose `Content-Length` exceeds `app.max-request-bytes` (default 16 KiB) with 413 `payload_too_large`, and wraps the body stream so a chunked body that grows past the limit is rejected the same way. nginx also sets `client_max_body_size 64k`.

### 5.4 Export (US-008, SR-07, AC-008-01)

`GET /api/organizer/registrations.xlsx` builds an `.xlsx` with Apache POI (`WorkbookWriter` adapter): sheet `Registrations`, row 1 header, one row per registration ordered by `submitted_at`. Header, exactly:

`Registration ID`, `Submitted at (UTC)`, `Type`, `First name`, `Last name`, `Email`, `Organization / institution`, `Study institution`, `Study programme`, `Student ID`, `Workshops`, `Events`, `Meals`, `Other activities`, `Consents`

Option columns hold the stored option names joined with `; `; `Consents` holds `<id> (<givenAt ISO-8601>)` joined with `; `. All cells are string cells (no formulas), so input starting with `=`, `+`, `-` or `@` is never evaluated. No database-internal or system fields appear. Response headers: `Content-Disposition: attachment; filename="registrations.xlsx"`, `Cache-Control: no-store`. The same labels are used in the organizer email body.

### 5.5 Errors and logs (SB-07, ES-07, AC-005-03)

- One `@RestControllerAdvice` maps every exception to the `Error` schema. 500 responses always say `An unexpected error occurred. Please try again later.`; no exception class, stack trace, SQL or path. Spring's default error page and whitelabel are disabled (`server.error.include-*: never`).
- Logs contain registration ids, never names, emails, tokens, passwords or request bodies. Hibernate SQL logging stays off.

### 5.6 Headers and CORS (SB-10, SB-04)

- Backend: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`, `Referrer-Policy: no-referrer`, `Cache-Control: no-store` on API responses.
- Frontend nginx: CSP allowing only self plus Google reCAPTCHA script and frame origins, `frame-ancestors 'none'`, `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy: no-referrer`, `server_tokens off`.
- CORS: none unless `app.cors.allowed-origins` is set (local development only). The frontend reaches the API through the same origin (`/api` proxy), AR-01.
- TLS (SB-04): terminated by the external production nginx (environments.md); HSTS is set there. Backend traffic to PostgreSQL and SMTP inside the container network; SMTP TLS configurable (`SMTP_TLS`).

### 5.7 Least privilege (SB-11)

Backend image runs as user `app`; frontend image runs nginx as user `nginx` on port 8080; the database user owns only the application database.

### 5.8 Startup checks (SR-02, AC-001-15)

`StartupChecks` in `config` validates `settings.AppProperties` at startup (configuration.md, "Startup refusal"). `application-production.yml` has no defaults for database, SMTP and options file, so missing values stop startup there as well.

## 6. Frontend design (US-001 … US-004, NFR-03, AR-01, AR-07)

- React 19 + TypeScript + Vite; no router (one page). Modules: `api.ts` (fetch wrapper for `/api`, maps `Error` bodies), `validation.ts` (client-side rules mirroring 4.1 step 3 for required fields, email and consents), `RegistrationForm.tsx`, `OptionGroups.tsx`, `ConsentList.tsx`, `Recaptcha.tsx`, `Confirmation.tsx`, `App.tsx`.
- Behaviour and element names: `ui.md`. The frontend never holds a secret; the site key comes from `/api/config` at runtime (AR-07). Rendering uses React text nodes only (no `dangerouslySetInnerHTML`), SB-05.
- Build output is static files served by nginx (`frontend/nginx.conf`), which also forwards `/api` to the backend in the local stack.

## 7. Health and operations (ES-09, NFR-04, NFR-02)

- Actuator under `/api/health`, with `/api/health/liveness` and `/api/health/readiness` (probes on; readiness includes the database). Only `health` is exposed; details are hidden.
- Container health checks: backend `wget /api/health/readiness`; frontend `wget /`; PostgreSQL `pg_isready`. Compose starts the frontend only when the backend is healthy.
- Persistent data: named volumes `pgdata` (database) and `jsondata` (JSON copies at `/data/json`), which survive `docker compose down` and `up` (NFR-02).

## 8. Test design summary

Details in `03_test-strategy.md` (phase 3).

- Backend acceptance tests (`backend/src/test/java/si/konferenca/registration/acceptance/`): start the application on a random port with Testcontainers PostgreSQL and a Mailpit container, and use only HTTP, the SQL schema, the JSON copy directory and Mailpit's API. reCAPTCHA test mode for most tests; a local mock verification endpoint for AC-001-08. Clients are separated with distinct `X-Forwarded-For` addresses.
- Frontend end-to-end tests (`frontend/e2e/`): Playwright against the running local compose stack; Mailpit API and the export for NFR-01.
- Unit and integration tests (phase 5): backend `src/test/java/.../unit` and `.../integration`, ArchUnit `ArchitectureTest`; frontend `src/**/*.test.tsx` with Vitest and Testing Library.

## 9. Engineering standards mapping

| ES | Where |
|---|---|
| ES-01 | `configuration.md`; `settings.AppProperties`; secrets without defaults |
| ES-02 | `.gitignore` excludes `.env`; `secrets.env.example` lists every key |
| ES-03 | root `.gitattributes`, `.gitignore` |
| ES-04 | exact versions in `pom.xml` and `package.json`; `package-lock.json` committed |
| ES-05 | component READMEs: build, test, check, run commands |
| ES-06 | root and component READMEs (phase 7) |
| ES-07 | 5.5 |
| ES-08 | Flyway, 4.2 |
| ES-09 | 7 |
| ES-10 | commit discipline |

## Traceability

| AC / SR / AR | Section |
|---|---|
| AC-001-01 | 4.1, 4.2, `openapi.json` createRegistration |
| AC-001-02, AC-001-03, AC-001-04 | 4.1 step 3 |
| AC-001-05 | 3, 4.1 step 3 |
| AC-001-06 | 4.1 step 3, `conference-options.schema.json` consents |
| AC-001-07, AC-001-08 | 4.1 step 4, `recaptcha.md` |
| AC-001-09 | 4.1 step 5, `database-schema.sql` unique index |
| AC-001-10 | 5.2 |
| AC-001-11 | 5.3 |
| AC-001-12 | 6, `ui.md` 3, 5 |
| AC-001-13 | 4.1 step 3 (Unicode accepted), 4.2 (UTF-8 throughout) |
| AC-001-14 | 4.1 steps 2 and 3 |
| AC-001-15 | 5.8, `configuration.md` |
| AC-002-01 | 3 (D-06), 4.1, 4.2 |
| AC-002-02 | 4.1 step 3 |
| AC-002-03 | 6, `ui.md` 3 |
| AC-003-01, AC-003-02, AC-003-03 | 3 |
| AC-003-04 | 6, `ui.md` 4 |
| AC-004-01, AC-004-02, AC-004-03 | 4.1 step 8, 6, `ui.md` |
| AC-005-01 | 4.2, `database-schema.sql` |
| AC-005-02 | 4.1 step 6, 4.2 |
| AC-005-03 | 4.1 step 6, 5.5 |
| AC-006-01, AC-006-02, AC-006-03 | 4.1 steps 3 and 7, `emails.md` |
| AC-007-01, AC-007-02 | 4.1 step 7, `emails.md` |
| AC-008-01 | 5.4 |
| AC-008-02, AC-008-03 | 5.1 |
| AC-008-04 | 5.2 |
| SR-01 | 4.1 step 4, `recaptcha.md` |
| SR-02 | 5.8, `configuration.md` |
| SR-03 | 5.2, 5.3 |
| SR-04 | 3, 4.1 step 3 |
| SR-05 | 4.1 step 3, `emails.md` |
| SR-06 | 5.1 |
| SR-07 | 4.2, 5.4, `emails.md` |
| SB-01 | 4.1 step 3 (server side, whatever the client does) |
| SB-02 | 5.1 |
| SB-03 | 5.1 (BCrypt), configuration.md (secrets from environment) |
| SB-04 | 5.6 |
| SB-05 | 5.4 (string cells), 6 (React text nodes), JPA parameter binding, `emails.md` (plain text) |
| SB-06 | 5.2 |
| SB-07 | 5.5 |
| SB-08 | Dependency-Check and npm audit in phases 0 and 6 |
| SB-09 | Semgrep, SpotBugs, PMD and gitleaks in phase 6 |
| SB-10 | 5.6 |
| SB-11 | 5.7 |
| SB-12, SB-13 | 4.2, D-11 |
| SB-14 | `ui.md` 5, 4.2 (`given_at`) |
| AR-01 | 5.6 (same-origin `/api`), 6 |
| AR-02 | 2 |
| AR-03 | 2 (rule A5) |
| AR-04 | 3 |
| AR-05 | 4.1 step 6 |
| AR-06 | 4.2 |
| AR-07 | 6, `configuration.md` Frontend |
| NFR-01 | 4.2 (UTF-8 in database, file, emails, workbook); end-to-end test |
| NFR-02 | 7 (named volumes) |
| NFR-03 | 6, `ui.md` Validation and errors |
| NFR-04 | 7 |
