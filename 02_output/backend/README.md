# Backend

> Written in: phase 7 · Source: `project/03_technical/architecture.md`, ES-05, ES-06 · Agent: writes

Spring Boot REST API (`si.konferenca.registration`) for options, registration and the organizer export. Architecture: layered with ports (`docs/02_specification.md` section 2); API contract: `docs/02_contracts/openapi.yaml`.

## Prerequisites

- Temurin JDK 21.0.10+7 (`java -version`). Maven is not needed: `./mvnw` downloads Apache Maven 3.9.9.
- Docker Engine 29.8.0 running (the tests start PostgreSQL and Mailpit with Testcontainers).
- For the dependency scan: `NVD_API_KEY` in the repository-root `.env`.

## Configuration

Every setting comes from an environment variable (ES-01). Secrets have no default; the application refuses to start without them or with an unsafe combination.

| Variable | Meaning | Default | Source |
|---|---|---|---|
| `DB_URL`, `DB_USERNAME` | JDBC URL and user | none | environment / compose |
| `POSTGRES_PASSWORD` | database password (secret) | none | `.env` |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_STARTTLS`, `SMTP_AUTH` | mail server | none, 25, `true`, `false` | environment |
| `SMTP_USERNAME`, `SMTP_PASSWORD` | SMTP account (secret, production) | empty | `.env` |
| `MAIL_FROM`, `CONFERENCE_NAME` | sender, name in emails and the form | `registration@konferenca.si`, `Conference` | environment |
| `CONFERENCE_OPTIONS_FILE` | options and consents file (`docs/02_contracts/options-config.schema.json`) | image: `/app/config/options.json` | environment |
| `JSON_COPY_DIR` | JSON copy directory (persistent volume) | `/data/registrations` | environment |
| `ORGANIZER_USERNAME`, `ORGANIZER_PASSWORD` (≥ 16 characters), `ORGANIZER_EMAILS` | organizer login and notification recipients (secrets) | none | `.env` |
| `ORGANIZER_HTTPS_ONLY` | refuse organizer credentials over plain HTTP except from localhost | `true` | environment |
| `RECAPTCHA_TEST_MODE`, `RECAPTCHA_SITE_KEY`, `RECAPTCHA_SECRET_KEY`, `RECAPTCHA_VERIFY_URL` | reCAPTCHA v2 | `false`, empty, empty, Google siteverify | `.env` / environment |
| `APP_ENVIRONMENT` | `production`, `local` or `test` | `production` | environment |
| `CORS_ALLOWED_ORIGINS` | comma-separated origins (local development only) | empty | environment |
| `RATE_LIMIT_REGISTRATION`, `RATE_LIMIT_EXPORT` | per client: registrations per 10 min, export requests per min | 20, 10 | environment |
| `MAX_REQUEST_BYTES`, `MAIL_RETRY_INTERVAL`, `MAIL_MAX_ATTEMPTS` | body limit, email retry (D-11) | 16384, `PT5M`, 10 | environment |

Startup refuses: test mode with `APP_ENVIRONMENT=production`; empty reCAPTCHA keys without test mode; `ORGANIZER_HTTPS_ONLY=false` in production; missing organizer settings or options file.

Conference options: edit the options file (options, per-category limits, consents with wording) and restart; no code change (AR-04). Option ids must stay stable.

## Build, test, check, run (ES-05)

Run from `02_output/backend/`.

| Purpose | Command |
|---|---|
| build | `./mvnw -B package -DskipTests` |
| test (unit, ArchUnit, frozen acceptance; coverage in `target/site/jacoco`) | `./mvnw -B verify` |
| check (format, lint, duplication, static analysis) | `./mvnw -B -DskipTests compile spotless:check pmd:check pmd:cpd-check spotbugs:check` |
| run (local stack) | `docker compose --env-file ../.env up -d --build` from `02_output/` |
| mutation | `./mvnw -B org.pitest:pitest-maven:mutationCoverage "-DexcludedTestClasses=si.konferenca.registration.acceptance.*"` |
| dependency scan | `NVD_API_KEY=… ./mvnw -B org.owasp:dependency-check-maven:check` (suppressions: `dependency-check-suppressions.xml`, D-18/D-19) |
| format fix | `./mvnw -B spotless:apply` |

The container image (`Dockerfile`, eclipse-temurin JRE, non-root uid 10001) copies `target/registration-backend-1.0.0.jar`, so build the jar first.

## Operations

- Health: `/actuator/health/liveness`; readiness `/actuator/health/readiness` (database and writable JSON copy directory).
- Data: PostgreSQL (schema by Flyway, `src/main/resources/db/migration`) and `JSON_COPY_DIR/<reference>.json`. Back up both.
- Retention (D-14, pending review): proposed 12 months after the conference. Deletion is manual:

  ```bash
  docker compose --env-file ../.env exec postgres psql -U registration -d registration -c "DELETE FROM registration WHERE submitted_at < '2027-10-01'"
  ```

  then delete the matching `<reference>.json` files from the copy volume.
- Logs contain registration references only, never names, emails or secrets.

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| `Unsafe or incomplete configuration: …` at startup | the message names the missing or unsafe variable; set it in `.env` or the environment |
| `Invalid options file: …` | fix the options file as described; it must contain at least one mandatory consent |
| tests fail with Docker or Testcontainers errors | start Docker; the tests pull `postgres:16.15-alpine` and `axllent/mailpit:v1.31.1` |
| readiness `DOWN` | database unreachable or `JSON_COPY_DIR` not writable by uid 10001 |
| organizer export returns 403 | request over plain HTTP from a non-local address while `ORGANIZER_HTTPS_ONLY=true`; use HTTPS through the reverse proxy |
| emails missing | check `SMTP_*`; failed emails are retried every `MAIL_RETRY_INTERVAL` up to `MAIL_MAX_ATTEMPTS` |
