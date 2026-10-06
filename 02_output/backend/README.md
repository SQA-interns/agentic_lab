# Backend

> Written in: phase 7 · Source: ES-05, ES-06, `docs/02_specification.md` · Agent: writes

Spring Boot 4.1 REST API: form configuration, registration (validation, reCAPTCHA, storage in PostgreSQL plus a raw JSON copy, emails) and the organizer Excel export. Contract: `../docs/02_contracts/openapi.yaml`.

## Prerequisites

- JDK 21 (the Maven wrapper downloads Maven 3.9.9)
- Docker, for the tests (Testcontainers starts PostgreSQL and Mailpit)

## Configuration

Environment variables; full list with defaults in `../docs/02_specification.md` section 4.

| Setting | Source | Notes |
|---|---|---|
| `POSTGRES_PASSWORD`, `ORGANIZER_USERNAME`, `ORGANIZER_PASSWORD`, `ORGANIZER_EMAILS` | `.env` (secrets) | required; password ≥ 16 characters in production |
| `RECAPTCHA_SITE_KEY`, `RECAPTCHA_SECRET_KEY` | `.env` (secrets) | required when `RECAPTCHA_TEST_MODE` is false |
| `SMTP_USERNAME`, `SMTP_PASSWORD` | `.env` (secrets) | optional SMTP authentication |
| `APP_ENVIRONMENT` | environment | `production` (default), `local`, `test` |
| `DB_URL`, `DB_USERNAME` | environment | JDBC URL and user |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_STARTTLS`, `MAIL_FROM` | environment | SMTP server and sender |
| `CONFERENCE_NAME`, `CONFERENCE_CONFIG_FILE` | environment | options and consents file per `conference-config.schema.json` |
| `JSON_COPY_DIR` | environment | persistent directory for the raw JSON copies |
| `RECAPTCHA_TEST_MODE`, `ORGANIZER_HTTPS_ONLY`, `CORS_ALLOWED_ORIGIN` | environment | relaxed values allowed only in `local`/`test` |
| `RATE_LIMIT_*`, `MAX_REQUEST_BYTES` | environment | per-minute limits and body size |

Changing options or consents: edit the configuration file and restart; no code change.

## Build

```sh
./mvnw -B -ntp -DskipTests package
```

Produces `target/registration-backend-0.1.0.jar`; `docker build -t registration-backend .` packages it on `eclipse-temurin:21.0.10_7-jre-alpine` (non-root).

## Run

Locally with the whole stack: see `../README.md` ("Quick start"). Health: `/actuator/health/liveness`, `/actuator/health/readiness`.

## Test

```sh
./mvnw -B -ntp test
```

Acceptance tests are in `src/test/java/.../acceptance/` (frozen, listed in `../docs/03_acceptance-manifest.sha256`); architecture rules in `.../architecture/`. Coverage report: `target/site/jacoco/index.html`. Mutation: `./mvnw -B -ntp test-compile org.pitest:pitest-maven:mutationCoverage`.

## Check

```sh
./mvnw -B -ntp -DskipTests verify
```

Runs Spotless (google-java-format), PMD with CPD, and SpotBugs. Format: `./mvnw spotless:apply`.

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| startup fails with `RECAPTCHA_TEST_MODE may be true only when …` | test mode or relaxed setting outside `local`/`test`; set `APP_ENVIRONMENT` or the keys |
| startup fails with `conference configuration: …` | the options file violates `conference-config.schema.json`; the message names the property |
| tests fail with `Could not find a valid Docker environment` | Docker is not running |
| readiness DOWN | database unreachable or `JSON_COPY_DIR` not writable |
