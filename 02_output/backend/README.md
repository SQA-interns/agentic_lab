# Backend

> Written in: phase 7 · Source: `project/02_design/architecture.md`, ES-06 · Agent: writes

Spring Boot REST API (`/api/form`, `/api/registrations`, `/api/export`, contract `docs/02_contracts/api.openapi.yaml`), with health at `/actuator/health/{liveness,readiness}`.

## Prerequisites

- JDK 21 (the Maven wrapper downloads Maven 3.9.9)
- Docker Engine for the tests (Testcontainers starts PostgreSQL and Mailpit)

## Configuration

All settings are environment variables (specification section 9). Secrets come from the repository `.env` (`01_input/01_project/00_setup/secrets.env.example`); the local stack sets the others in `../docker-compose.yml`.

| Variable | Source | Note |
|---|---|---|
| `DATABASE_URL`, `DATABASE_USER`, `POSTGRES_PASSWORD` | compose / `.env` | required |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_STARTTLS`, `SMTP_USERNAME`, `SMTP_PASSWORD` | compose / `.env` | production needs a host |
| `ORGANIZER_USERNAME`, `ORGANIZER_PASSWORD`, `ORGANIZER_EMAILS` | `.env` | password at least 16 characters |
| `RECAPTCHA_SITE_KEY`, `RECAPTCHA_SECRET_KEY`, `RECAPTCHA_TEST_MODE` | `.env` / compose | production refuses test mode or empty keys |
| `APP_ENVIRONMENT` | compose | `production` (default), `local`, `test` |
| `CONFERENCE_NAME`, `CONFERENCE_OPTIONS_FILE`, `JSON_COPY_DIR`, `MAIL_FROM` | compose | options file schema: `docs/02_contracts/conference-options.schema.json` |
| `ORGANIZER_HTTPS_ONLY`, `CORS_ALLOWED_ORIGIN`, `RATE_LIMIT_*`, `MAX_REQUEST_BYTES`, `RETENTION_DAYS`, `RETENTION_CRON` | defaults | see specification |

Conference options change by editing the options file and restarting; outside production the bundled `src/main/resources/options/conference-options.local.json` is used.

## Build, check, test, run

```sh
./mvnw -B package -DskipTests                                                  # build target/registration-backend-0.1.0.jar
./mvnw -B spotless:check compile pmd:check pmd:cpd-check spotbugs:check       # check
./mvnw -B verify                                                               # unit, integration, acceptance tests + JaCoCo
../scripts/compose.sh up -d --build --wait backend                             # run (with db and mailpit)
```

Mutation testing: `./mvnw -B test-compile org.pitest:pitest-maven:mutationCoverage`. Dependency scan: `NVD_API_KEY` in the environment, then `./mvnw -B org.owasp:dependency-check-maven:check`.

## Troubleshooting

- Tests fail with Docker errors: start Docker; if Testcontainers cannot negotiate the API version, set `api.version` in `src/test/resources/docker-java.properties`.
- Running one acceptance class alone: include `Us001*` first, e.g. `./mvnw test -Dtest='Us001*,Us005*'` (D-22).
- Startup stops with `Invalid configuration: ...`: the message names the missing or unsafe setting.
- `relation "registration" does not exist`: Flyway did not run; check the datasource settings.
