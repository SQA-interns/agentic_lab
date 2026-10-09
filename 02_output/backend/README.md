# Backend

> Written in: phase 7 · Source: `project/constraints.md` ("Components"), ES-05, ES-06 · Agent: writes

Spring Boot REST API (`docs/02_contracts/openapi.yaml`): form data, registration, organizer export, health.

## Prerequisites

- JDK 21 (the image runs Eclipse Temurin 21.0.10); Maven comes with the wrapper (`./mvnw`)
- Docker: the tests start PostgreSQL and Mailpit with Testcontainers; the image is built by Compose

## Configuration

Environment variables (specification section 9). Secrets come from `.env` through `01_input/00_general/tools/secrets.sh`; `docker-compose.yml` sets the local values of the rest.

| Variable | Source | Local value / default |
|---|---|---|
| `DATABASE_URL`, `DATABASE_USER` | setting | Compose: the `postgres` service |
| `POSTGRES_PASSWORD` | secret (`.env`) | — |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_TLS` | setting | Compose: `mailpit`, `1025`, `false`; default TLS `true` |
| `SMTP_USERNAME`, `SMTP_PASSWORD` | secret (`.env`), production only | empty: no SMTP login |
| `MAIL_FROM`, `CONFERENCE_NAME` | setting | `registration@konferenca.si`, `Conference` |
| `OPTIONS_FILE` | setting | `classpath:conference-options.json`; production: a `file:` location (schema `docs/02_contracts/conference-options.schema.json`) |
| `JSON_COPY_DIR` | setting | `/data/registrations` (named volume) |
| `RECAPTCHA_TEST_MODE` | setting | default `false`; Compose and tests `true` |
| `RECAPTCHA_SITE_KEY`, `RECAPTCHA_SECRET_KEY` | secret (`.env`), production | required when test mode is off |
| `RECAPTCHA_VERIFY_URL` | setting | Google's `siteverify` |
| `ORGANIZER_USERNAME`, `ORGANIZER_PASSWORD`, `ORGANIZER_EMAILS` | secret (`.env`) | — |
| `ORGANIZER_HTTPS_ONLY` | setting | default `true`; Compose `false` (plain HTTP on 127.0.0.1) |
| `CORS_ALLOWED_ORIGINS` | setting | empty (same origin only) |
| `RATE_LIMIT_REGISTRATIONS_PER_MINUTE`, `RATE_LIMIT_EXPORTS_PER_MINUTE`, `RATE_LIMIT_FORM_PER_MINUTE`, `MAX_REQUEST_BYTES` | setting | `30`, `10`, `120`, `16384` |

The application refuses to start with test mode on under the `production` profile, without reCAPTCHA keys when test mode is off, or without organizer settings.

## Commands

From `02_output/backend`:

| Purpose | Command |
|---|---|
| Build | `./mvnw -B -DskipTests package` |
| Test (all levels, coverage in `target/site/jacoco`) | `./mvnw -B verify` |
| Check (format, lint, duplication, static analysis) | `./mvnw -B spotless:check pmd:check pmd:cpd-check spotbugs:check` |
| Format | `./mvnw -B spotless:apply` |
| Run (local stack, from `02_output`) | `bash ../01_input/00_general/tools/secrets.sh run POSTGRES_PASSWORD,ORGANIZER_USERNAME,ORGANIZER_PASSWORD,ORGANIZER_EMAILS -- docker compose up -d --build --wait backend` |

Health: `docker exec registration-backend-1 wget -q -O - http://127.0.0.1:8080/actuator/health/readiness`.

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| Tests fail with "Could not find a valid Docker environment" | start Docker; Testcontainers needs it |
| Compose build: `registration-backend-0.1.0.jar` not found | run the build command first; the image copies the jar |
| Startup: "Invalid conference options file …" | the message names the problem in `OPTIONS_FILE` |
| Startup: "RECAPTCHA_SITE_KEY and RECAPTCHA_SECRET_KEY must be set …" | set the keys, or `RECAPTCHA_TEST_MODE=true` outside production |
| Export answers 403 `https_required` | organizer access over plain HTTP from another machine; use HTTPS (or `ORGANIZER_HTTPS_ONLY=false` on localhost) |
| Registration answers 429 | rate limit per client address and minute; wait or raise the limit |
