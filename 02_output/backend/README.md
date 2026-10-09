# Backend

Spring Boot 4 REST API (`/api`) for registrations, the form definition and the organizer export.
Design: `../docs/02_specification.md`; API: `../docs/02_contracts/registration-api.openapi.yaml`.

## Prerequisites

Java 21 (the Maven wrapper downloads Maven 3.9.9). Docker for the tests (Testcontainers starts
PostgreSQL 16 and Mailpit) and for the container image.

## Configuration

All settings are environment variables (specification section 3); secrets have no default.

| Variable | Source |
|---|---|
| `DATABASE_URL`, `DATABASE_USER`, `POSTGRES_PASSWORD` | environment / `.env` (password) |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_TLS`, `SMTP_USERNAME`, `SMTP_PASSWORD` | environment / `.env` (credentials) |
| `ORGANIZER_USERNAME`, `ORGANIZER_PASSWORD` (≥ 16 characters), `ORGANIZER_EMAILS` | `.env` |
| `RECAPTCHA_SITE_KEY`, `RECAPTCHA_SECRET_KEY` | `.env` (production); `RECAPTCHA_TEST_MODE=true` locally |
| `CONFERENCE_CONFIG_PATH` | path to the options file, e.g. `../config/conference.json` |
| `APP_ENVIRONMENT` (`production` default), `ORGANIZER_HTTPS_ONLY`, `JSON_COPY_DIR`, `MAIL_FROM`, `CONFERENCE_NAME`, `CORS_ALLOWED_ORIGIN`, `RATE_LIMIT_*`, `MAX_REQUEST_BYTES`, `SERVER_PORT` | environment, defaults in the specification |

The backend refuses to start with an invalid options file, reCAPTCHA test mode in production,
empty reCAPTCHA keys outside test mode, or plain-HTTP organizer access in production.

## Commands (from this folder)

| Task | Command |
|---|---|
| build | `./mvnw -B -DskipTests package` |
| test (unit, integration, acceptance) | `./mvnw -B verify` |
| check (format, lint, static analysis) | `./mvnw -B spotless:check pmd:check pmd:cpd-check spotbugs:check` |
| format | `./mvnw -B spotless:apply` |
| run (local stack) | `../scripts/compose.sh up -d --build --wait` after the build |
| mutation | `./mvnw -B test-compile org.pitest:pitest-maven:mutationCoverage` |

Health: `GET /actuator/health/liveness` and `/actuator/health/readiness`. Database changes go
through Flyway migrations in `src/main/resources/db/migration`.

## Troubleshooting

| Symptom | Fix |
|---|---|
| Testcontainers cannot reach Docker | start Docker; if the API version is rejected, set `api.version` in `src/test/resources/docker-java.properties` (KP-06) |
| `Invalid configuration: …` at start | the message names the setting to fix |
| port 8080 in use | stop the other process or set `SERVER_PORT` |
| emails missing locally | open Mailpit at http://127.0.0.1:8025; send failures are logged with the registration id |
