# Backend

> Written in: phase 7 · Source: `project/02_design/architecture.md`, ES-05, ES-06 · Agent: writes

Spring Boot REST API (`docs/02_contracts/registration-api.openapi.yaml`): form configuration,
registrations, organizer token and Excel export, health under `/actuator/health`.

## Prerequisites

- JDK 21 (the Maven wrapper downloads Maven 3.9.9)
- Docker, for the tests (Testcontainers starts PostgreSQL and Mailpit)

## Configuration

All settings come from environment variables (`docs/02_specification.md`, section 5). The Spring
profile selects the environment: `production` (default), `local`, `test`; only `local` and `test`
have defaults for the database, the mail catcher and the options file.

| Variable | Source |
|---|---|
| `POSTGRES_PASSWORD`, `ORGANIZER_USERNAME`, `ORGANIZER_PASSWORD`, `ORGANIZER_EMAILS` | `.env` (secrets, via `secrets.sh run`) |
| `RECAPTCHA_SITE_KEY`, `RECAPTCHA_SECRET_KEY`, `SMTP_USERNAME`, `SMTP_PASSWORD` | `.env`, production only |
| `DB_URL`, `DB_USERNAME`, `SMTP_HOST`, `SMTP_PORT`, `SMTP_STARTTLS`, `MAIL_FROM`, `CONFERENCE_NAME`, `CONFERENCE_OPTIONS_FILE`, `JSON_COPY_DIR` | environment (local values in `../docker-compose.yml`) |
| `RECAPTCHA_TEST_MODE`, `RECAPTCHA_VERIFY_URL`, `ORGANIZER_HTTPS_ONLY`, `CORS_ALLOWED_ORIGIN`, `RATE_LIMIT_*`, `MAX_REQUEST_BYTES` | environment, with safe defaults |

Conference options and the consent wording: a JSON file per `docs/02_contracts/conference-options.schema.json`
(example: `conference-options.example.json`); restart after a change. Production refuses to start with
test mode on, empty reCAPTCHA keys, `ORGANIZER_HTTPS_ONLY=false` or no options file.

## Build

```bash
./mvnw -B -DskipTests package          # target/registration-backend-0.1.0.jar
```

## Run

Normally as the `backend` service of `../docker-compose.yml` (see `../README.md`). The image is built
from the jar: `docker build -t registration-backend .`

## Test

```bash
./mvnw -B test                         # unit, integration, architecture and acceptance tests
./mvnw -B test -Dtest='!*AcceptanceTest'   # without the acceptance tests
```

## Check

```bash
./mvnw -B compile spotless:check pmd:check pmd:cpd-check spotbugs:check
./mvnw -B spotless:apply               # fix formatting
```

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| tests fail with "Could not find a valid Docker environment" | Docker is not running; start it |
| start fails with `unsafe configuration: …` | the named setting is missing or unsafe for the profile; set it |
| start fails with `CONFERENCE_OPTIONS_FILE is not set` or `invalid options file` | set the variable or fix the file against the schema |
| `Schema validation` error at start | the database was changed outside Flyway; restore it from the migrations |
| registrations answer 503 | the database or `JSON_COPY_DIR` is not writable; nothing was stored |
