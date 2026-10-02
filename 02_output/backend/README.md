# Backend

> Written in: phases 0, 7 · Source: `project/02_design/architecture.md` · Agent: writes

Spring Boot REST API of the conference registration: form configuration, options, registration
(validation, anti-automation check, database row and JSON copy, emails) and the organizer export.

## Prerequisites

- JDK 21 (the Maven wrapper downloads Maven 3.9.9)
- Docker: the tests start PostgreSQL and Mailpit containers; the run command uses Compose

## Commands (ES-05)

Run in `02_output/backend`.

| Purpose | Command |
|---|---|
| build | `./mvnw -B -DskipTests package` |
| test | `./mvnw -B verify` |
| check | `./mvnw -B compile spotless:check pmd:check spotbugs:check` |
| run | `docker compose --env-file ../../.env -f ../docker-compose.yml up -d --build --wait backend` |

- Run starts the backend with PostgreSQL and Mailpit; build first, because the image copies the jar.
- Format the sources with `./mvnw spotless:apply`.
- Coverage report after test: `target/site/jacoco/index.html`.

## Configuration

Environment variables; the full list with defaults is in `../docs/02_specification.md`, section 5.

| Variable | Source | Notes |
|---|---|---|
| `POSTGRES_PASSWORD`, `ORGANIZER_USERNAME`, `ORGANIZER_PASSWORD`, `ORGANIZER_EMAILS` | `.env` in the repository root (secret) | required everywhere |
| `RECAPTCHA_SITE_KEY`, `RECAPTCHA_SECRET_KEY` | secret store of the environment | required unless `RECAPTCHA_TEST_MODE=true` |
| `SMTP_USERNAME`, `SMTP_PASSWORD` | secret store of the environment | optional; empty means no SMTP login |
| `DB_URL`, `DB_USER`, `SMTP_HOST`, `SMTP_PORT`, `SMTP_STARTTLS` | deployment configuration | local values are in `../docker-compose.yml` |
| `OPTIONS_FILE` | deployment configuration | path of the options file (`../config/conference-options.json` locally); read at start |
| `JSON_COPY_DIR` | deployment configuration | default `/data/registrations`; must be a persistent volume |
| `APP_ENVIRONMENT` | deployment configuration | default `production`; `local` or `test` otherwise |
| `ORGANIZER_REQUIRE_HTTPS`, `TRUST_FORWARDED_HEADERS` | deployment configuration | production: `true` and `true` behind the reverse proxy |
| `CONFERENCE_NAME`, `MAIL_FROM`, `CONSENT_TEXT`, rate limits, `MAX_REQUEST_BYTES` | deployment configuration | have defaults |

Production refuses to start with the test mode on, with empty reCAPTCHA keys, or with
`ORGANIZER_REQUIRE_HTTPS=false`.

To change the conference options, edit the options file and restart the backend.

## Operations

- Health: `/actuator/health/liveness` and `/actuator/health/readiness` (inside the container network).
- Removing registrations after the retention period (proposed: 12 months after the conference):
  delete the rows of table `registration` (their options go with them) and the files in `JSON_COPY_DIR`.

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| Start fails with `Invalid configuration: ...` | the message names the setting to correct |
| Start fails with `The options file ... is not valid` | fix the file against `../docs/02_contracts/conference-options.schema.json` |
| Start fails with `Could not resolve placeholder` | a required variable (for example `DB_URL`, `ORGANIZER_EMAILS`) is not set |
| Tests fail with `Could not find a valid Docker environment` | start Docker |
| Export answers 403 | organizer access needs HTTPS; locally set `ORGANIZER_REQUIRE_HTTPS=false` (already set in the compose file) |
| Registration answers 503 | the database or the JSON copy directory is not writable, or the reCAPTCHA service is unreachable; see the backend log (registration id and error class only) |
| Registration answers 429 | rate limit; wait a minute or raise `RATE_LIMIT_REGISTRATION` |
