# Backend

REST API of the conference registration (`docs/02_contracts/api.openapi.yaml`): validates and stores registrations in PostgreSQL and as raw JSON copies, sends the confirmation and organizer emails, and exports registrations for organizers. Java 21, Spring Boot 4.1.1.

## Prerequisites

- JDK 21 (`java -version`); Maven comes through the wrapper.
- Docker, for the tests (Testcontainers) and the local stack.

## Configuration

Environment variables (`docs/02_specification.md` §9). Secrets come from `.env` in the repository root (keys listed in `01_input/01_project/secrets.env.example`) and reach containers only through `01_input/00_general/tools/secrets.sh run`.

| Variable | Source | Default |
|---|---|---|
| `APP_ENVIRONMENT` | compose / environment | `production` |
| `DB_URL`, `DB_USERNAME` | compose / environment | none |
| `POSTGRES_PASSWORD` | `.env` | none |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_TLS` | compose / environment | none, none, `true` |
| `SMTP_USERNAME`, `SMTP_PASSWORD` | `.env` (production only) | empty (no authentication) |
| `MAIL_FROM`, `CONFERENCE_NAME` | environment | `no-reply@konferenca.si`, `Conference` |
| `CONFERENCE_CONFIG_FILE` | compose (`../config/conference-config.json`) | none |
| `JSON_COPY_DIR` | compose | `/data/registrations` |
| `RECAPTCHA_TEST_MODE` | compose (local, test only) | `false` |
| `RECAPTCHA_SITE_KEY`, `RECAPTCHA_SECRET_KEY` | `.env` (production) | none |
| `RECAPTCHA_VERIFY_URL` | environment | Google siteverify URL |
| `ORGANIZER_USERNAME`, `ORGANIZER_PASSWORD` (16+ characters), `ORGANIZER_EMAILS` | `.env` | none |
| `ORGANIZER_HTTPS_ONLY` | compose (`false` locally) | `true` |
| `ALLOWED_ORIGINS` | environment (local only) | empty |
| `RATE_LIMIT_REGISTRATIONS_PER_MINUTE`, `RATE_LIMIT_EXPORTS_PER_MINUTE`, `MAX_REQUEST_BYTES` | environment | `10`, `10`, `16384` |

The backend refuses to start with unsafe or missing settings and names the setting in the log.

## Build

```bash
./mvnw -B package -DskipTests
```

## Run

The backend runs in the local stack (PostgreSQL, Mailpit, frontend); from `02_output`:

```bash
./backend/mvnw -B -f backend/pom.xml package -DskipTests
bash ../01_input/00_general/tools/secrets.sh run POSTGRES_PASSWORD,ORGANIZER_USERNAME,ORGANIZER_PASSWORD,ORGANIZER_EMAILS -- docker compose up -d --build
```

API at `http://127.0.0.1:8080/api`, health at `http://127.0.0.1:8080/actuator/health/readiness`.

## Test

```bash
./mvnw -B test
```

Runs unit, integration, architecture and acceptance tests; Docker must be running. Coverage report: `target/site/jacoco/index.html`. Mutation testing: `./mvnw -B test-compile org.pitest:pitest-maven:mutationCoverage`.

## Check

```bash
./mvnw -B compile spotless:check pmd:check pmd:cpd-check spotbugs:check
```

`./mvnw -B spotless:apply` fixes formatting. All checks and scanners together: `bash ../scripts/verify.sh <phase>`.

## Troubleshooting

- Startup stops with "Unsafe or missing settings: …": set the named variable.
- Flyway "relation registration already exists": the database volume belongs to another stack; use this compose project (`registration-tanej04`).
- Tests fail with "Could not find a valid Docker environment": start Docker.
- `curl` from Git Bash sends non-ASCII command-line text in the Windows code page; send JSON bodies from a UTF-8 file (`--data-binary @file.json`).
