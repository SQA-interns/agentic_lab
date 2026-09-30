# Backend

> Written in: phase 7 · Source: `project/02_design/architecture.md`, ES-05, ES-06 · Agent: writes

Spring Boot REST API (package `si.konferenca.registration`): `GET /api/options`, `GET /api/config`, `POST /api/registrations`, `GET /api/organizer/registrations.xlsx`, `GET /api/health[/liveness|/readiness]`. Contract: `../docs/02_contracts/openapi.json`. Architecture (layers `domain`, `application`, `infrastructure`, `web`, `settings`, `config`, checked by `ArchitectureTest`): `../docs/02_specification.md` §2.

## Prerequisites

- Eclipse Temurin JDK 21 (`java -version`); Maven comes with the wrapper (`./mvnw`, downloads Maven 3.9.9).
- Docker, for the tests (Testcontainers starts PostgreSQL and Mailpit) and for the local stack.
- Versions: `../../01_input/01_project/00_setup/tech-stack.md`.

## Configuration

All settings come from environment variables (full list with defaults: `../docs/02_contracts/configuration.md`).

| Variable | Source | Notes |
|---|---|---|
| `DATABASE_URL`, `DATABASE_USER` | environment | local default `jdbc:postgresql://localhost:5432/registration`, `registration`; required in production |
| `POSTGRES_PASSWORD` | `.env` (secret) | no default |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_TLS`, `SMTP_AUTH` | environment | local default Mailpit `localhost:1025`, no TLS; production requires host and port, STARTTLS is on unless `SMTP_TLS=false` |
| `SMTP_USERNAME`, `SMTP_PASSWORD` | `.env` (secret) | production only; set `SMTP_AUTH=true` when used |
| `MAIL_FROM`, `CONFERENCE_NAME` | environment | sender and name shown in emails |
| `OPTIONS_FILE` | environment | conference options and consents, format `../docs/02_contracts/conference-options.schema.json`; local default `classpath:conference-options.json`; production: a file on a mounted volume, e.g. `file:/config/conference-options.json` |
| `JSON_COPY_DIR` | environment | directory for `<id>.json` copies; must be on a persistent volume |
| `RECAPTCHA_TEST_MODE` | environment | `true` only for local and test; production refuses to start with it |
| `RECAPTCHA_SITE_KEY`, `RECAPTCHA_SECRET_KEY` | `.env` (secret) | required whenever test mode is off |
| `ORGANIZER_USERNAME`, `ORGANIZER_PASSWORD` | `.env` (secret) | the organizer login for the export (stored only as a BCrypt hash in memory) |
| `ORGANIZER_EMAILS` | `.env` | comma-separated notification recipients |
| `ORGANIZER_HTTPS_ONLY` | environment | default `true`; `false` only for plain HTTP on 127.0.0.1 |
| `RATE_LIMIT_*`, `MAX_REQUEST_BYTES`, `CORS_ALLOWED_ORIGINS` | environment | defaults 10 / 10 / 120 requests per minute, 16384 bytes, no CORS |
| `SPRING_PROFILES_ACTIVE` | environment | `production` enables the production checks |

Changing the offered options: edit the options file and restart; never reuse an option id for another option (keep old ones with `"active": false`).

## Build

```bash
./mvnw -B package -DskipTests
```

Produces `target/registration-backend-1.0.0.jar`; `docker build .` packages it into a non-root image (`eclipse-temurin:21.0.10_7-jre-alpine`).

## Run

With the whole local stack (recommended), from `02_output/`:

```bash
docker compose --env-file ../.env up -d --build
```

Stand-alone against your own PostgreSQL and SMTP (set the variables above first):

```bash
./mvnw spring-boot:run
```

## Test

```bash
./mvnw -B test
```

Runs the frozen acceptance tests (`src/test/java/.../acceptance`, black-box over HTTP with Testcontainers) and the unit, integration and architecture tests. Docker must be running. Coverage report: `target/site/jacoco/index.html`.

Mutation testing (unit tests only):

```bash
./mvnw -B test-compile org.pitest:pitest-maven:1.30.0:mutationCoverage
```

## Check

```bash
./mvnw -B verify -DskipTests
```

Spotless (google-java-format), PMD, CPD and SpotBugs. Format the code with `./mvnw spotless:apply`. Dependency scan (needs `NVD_API_KEY` in the environment):

```bash
./mvnw -B org.owasp:dependency-check-maven:12.1.0:check
```

## Operations

- Health: `/api/health/liveness`, `/api/health/readiness` (readiness includes the database); the image has a health check.
- Database schema: Flyway migrations in `src/main/resources/db/migration` run at startup.
- Deployment condition (F-10): expose the backend only to the reverse proxy. It trusts `X-Forwarded-For`/`X-Forwarded-Proto` from private-network addresses, and the proxy must set `X-Forwarded-For $remote_addr` and `X-Forwarded-Proto $scheme`.
- Retention (D-11, pending review): no automatic deletion. To delete a registration, delete its row (options and consents follow by cascade) and its JSON copy:

  ```bash
  docker compose --env-file ../.env exec postgres psql -U registration -d registration -c "DELETE FROM registration WHERE id = '<id>';"
  ```

  ```bash
  docker compose --env-file ../.env exec backend rm /data/json/<id>.json
  ```

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| `Refusing to start: RECAPTCHA_SITE_KEY and RECAPTCHA_SECRET_KEY are required when test mode is off` | Set both keys, or `RECAPTCHA_TEST_MODE=true` locally |
| `Refusing to start: ORGANIZER_...` | Set `ORGANIZER_USERNAME`, `ORGANIZER_PASSWORD` and at least one valid address in `ORGANIZER_EMAILS` |
| `Invalid conference options configuration: ...` | The options file violates the schema; the message names the problem |
| Tests fail with `Could not find a valid Docker environment` | Start Docker; Testcontainers needs it |
| Export answers 403 `Organizer access requires HTTPS.` | Call it through the HTTPS proxy, or set `ORGANIZER_HTTPS_ONLY=false` for local plain HTTP |
| Registration answers 500 | Check the JSON copy directory is writable (`JSON_COPY_DIR`); nothing was stored |
| Emails missing, registration accepted | SMTP unreachable; the log shows `Email <kind> failed for registration <id>` (D-08) |
