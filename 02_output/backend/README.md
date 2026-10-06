# Backend

> Written in: phase 7 · Source: `project/02_design/architecture.md`, ES-06 · Agent: writes

Spring Boot REST API (`docs/02_contracts/openapi.yaml`): form setup, registration, organizer export.

## Prerequisites

- JDK 21 (`tech-stack.md`: Temurin 21.0.10+7); Maven comes through `./mvnw`.
- Docker Engine for the tests (Testcontainers starts `postgres:16.15-alpine` and `axllent/mailpit:v1.31.1`).

## Configuration

Environment variables, listed with defaults in `docs/02_specification.md` §5 and mapped in `src/main/resources/application.yml`. Secrets (`POSTGRES_PASSWORD`, `ORGANIZER_USERNAME`, `ORGANIZER_PASSWORD`, `ORGANIZER_EMAILS`, `RECAPTCHA_*`, `SMTP_*`) come from the repository-root `.env` (template: `01_input/01_project/00_setup/secrets.env.example`).

- Profile `local` (compose) and `test` supply local defaults; without a profile (production) every required value must be set and the startup guard refuses test mode, empty reCAPTCHA keys, plain-HTTP organizer access, short passwords, SMTP without STARTTLS and CORS.
- Options and the consent text: a YAML file named by `OPTIONS_FILE` (schema `docs/02_contracts/conference-options.schema.json`, example `src/main/resources/conference-options.yaml`). Change it and restart.

## Commands

| Purpose | Command (in `02_output/backend`) |
|---|---|
| Build | `./mvnw -B -DskipTests package` |
| Test (unit, integration, acceptance; coverage in `target/site/jacoco`) | `./mvnw -B verify` |
| Check (format, static analysis) | `./mvnw -B -DskipTests compile spotless:check spotbugs:check pmd:check pmd:cpd-check` |
| Format | `./mvnw -B spotless:apply` |
| Mutation | `./mvnw -B test-compile org.pitest:pitest-maven:mutationCoverage` |
| Run (with PostgreSQL and Mailpit) | `docker compose --env-file ../../.env -f ../docker-compose.yml up -d --build --wait backend` |

Health: `/actuator/health/liveness`, `/actuator/health/readiness` (inside the container network).

## Data retention (D-17)

Registrations and JSON copies are kept 12 months after the conference, then deleted by the operator:

```sh
docker compose --env-file ../../.env -f ../docker-compose.yml exec postgres psql -U registration -d registration -c "DELETE FROM registration"
docker compose --env-file ../../.env -f ../docker-compose.yml exec backend sh -c 'rm -f /data/json-copies/*.json'
```

## Troubleshooting

| Symptom | Fix |
|---|---|
| Testcontainers cannot reach Docker | start Docker; if the API version is rejected, add `api.version` to `src/test/resources/docker-java.properties` (KP-06) |
| Startup fails "Could not resolve placeholder" | a required variable is missing (production has no defaults) |
| Startup fails "Refusing to start" | a production safety rule was violated; the message lists which |
| Startup fails "Invalid option configuration" | fix the named entry in the `OPTIONS_FILE` |
| Dependency scan slow or stale | set `NVD_API_KEY` in `.env` (`../scripts/verify.sh <p> backend-depscan`) |
