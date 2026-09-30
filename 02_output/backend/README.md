# Backend (conference registration API)

Spring Boot 3.5.16 REST backend on Java 21. It validates and accepts registrations, stores each one in PostgreSQL and as a durable JSON file, sends participant and organizer mail through a retrying outbox, and provides the organizer Excel export. Contract: `../docs/02_contracts/openapi.yaml`; design: `../docs/02_specification.md`.

## Prerequisites

| Tool | Version | Note |
|---|---|---|
| Temurin JDK | 21.0.12+8 | set `JAVA_HOME`; the run used `~/.local/jdks/jdk-21.0.12+8` |
| Maven | 3.9.11 | downloaded by `./mvnw` (Maven Wrapper 3.3.2, SHA-256 pinned); needs `unzip` |
| Docker Engine + Compose | 28.1.1 / 2.35.1 (verified on 29.3.1 / v5.1.1, D-03) | tests start PostgreSQL 16.14 and Mailpit v1.24.1 containers |

The tests need a non-root user, because the storage-failure test makes a directory read-only.

## Commands (ES-05)

Run from `02_output/backend`.

| Purpose | Command |
|---|---|
| Build | `./mvnw -B -DskipTests package` → `target/conference-backend-1.0.0.jar` |
| Test (unit + integration + frozen acceptance) | `./mvnw -B verify` |
| Check (format, lint, static analysis, duplication) | `./mvnw -B -Pquality -DskipTests verify` (Spotless check, PMD, CPD, SpotBugs) |
| Format | `./mvnw -B spotless:apply` |
| Run | from `02_output`: `docker compose up -d --build` (see `../README.md`); or locally, see "Run without Compose" |
| Coverage | produced by `verify`: `target/site/jacoco` (unit), `target/site/jacoco-it` (integration + acceptance) |
| Mutation | `./mvnw -B test-compile org.pitest:pitest-maven:mutationCoverage` → `target/pit-reports` |
| Dependency scan | `./mvnw -B org.owasp:dependency-check-maven:check` (set `NVD_API_KEY` for faster updates) |
| Password hash for the organizer | `../tools/hash-password.sh` |

## Configuration

Every environment-specific value comes from an environment variable. Secrets have no defaults. The full table is in `../docs/02_specification.md` section 10.

| Variable | Kind | Source | Example (local) |
|---|---|---|---|
| `APP_PROFILE` | setting | Compose / shell | `local` (`local`, `test` or `production`; required) |
| `DB_URL`, `DB_USERNAME` | setting | Compose | `jdbc:postgresql://postgres:5432/conference`, `conference` |
| `DB_PASSWORD` | **secret** | `.env` | generated |
| `ORGANIZER_USERNAME` | **secret** | `.env` | `organizer` |
| `ORGANIZER_PASSWORD_HASH` | **secret** | `.env` | `{bcrypt}$2a$12$…` from `tools/hash-password.sh` (only `{bcrypt}` cost ≥ 10 or `{pbkdf2}` accepted) |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_TLS_ENABLED` | setting | Compose | `mailpit`, `1025`, `false` (TLS is mandatory when `APP_PROFILE=production`) |
| `SMTP_USERNAME`, `SMTP_PASSWORD` | **secret** | `.env` | empty locally |
| `MAIL_FROM`, `ORGANIZER_EMAILS` | setting | Compose | `registrations@example.test`, `organizer@example.test` |
| `CAPTCHA_MODE` | setting | Compose | `stub` (token `local-captcha-ok`; refused in production) or `recaptcha` |
| `RECAPTCHA_SITE_KEY`, `RECAPTCHA_SECRET_KEY` | **secret** | `.env` | empty locally; both required for `recaptcha` |
| `CONFERENCE_CONFIG_PATH` | setting | Compose | `/config/conference.json` (the catalog; see below) |
| `REGISTRATION_JSON_DIR` | setting | Compose | `/data/registrations` (persistent volume) |
| `ALLOWED_ORIGINS`, `TRUSTED_PROXIES`, `PUBLIC_BASE_URL` | setting | Compose | see `../compose.yaml` |
| `REGISTRATION_RATE_LIMIT_PER_MINUTE`, `EXPORT_RATE_LIMIT_PER_MINUTE` | setting | Compose | `120` / `10` (defaults 20 / 10) |
| `NOTIFY_POLL_INTERVAL`, `RECONCILE_INTERVAL`, `RECONCILE_GRACE` | setting | defaults | `PT5S`, `PT5M`, `PT2M` |

**Catalog:** a JSON file following `../docs/02_contracts/catalog-config.schema.json` (example: `../config/conference.local.json`). To change the activities, edit the file and restart the backend; no rebuild is needed. An invalid file stops the startup with a message that names the problem.

**Storage:** the database schema is managed by Flyway (`src/main/resources/db/migration`). JSON files are written to `REGISTRATION_JSON_DIR/registrations/<registrationId>.json`. Files without a database row are moved to `orphaned/`.

## Run without Compose

Start PostgreSQL and an SMTP catcher yourself, then:

```sh
export APP_PROFILE=local DB_URL=jdbc:postgresql://localhost:5432/conference DB_USERNAME=conference \
  DB_PASSWORD=... ORGANIZER_USERNAME=organizer ORGANIZER_PASSWORD_HASH='{bcrypt}...' \
  SMTP_HOST=localhost SMTP_PORT=1025 SMTP_TLS_ENABLED=false MAIL_FROM=registrations@example.test \
  ORGANIZER_EMAILS=organizer@example.test CAPTCHA_MODE=stub \
  CONFERENCE_CONFIG_PATH=../config/conference.local.json REGISTRATION_JSON_DIR=/tmp/registrations \
  ALLOWED_ORIGINS=http://localhost:5173
java -jar target/conference-backend-1.0.0.jar
```

Health: `GET /actuator/health/liveness` and `GET /actuator/health/readiness` (readiness includes the database).

## Modules

`lab.conference.platform` (security, rate limits, errors), `options` (catalog), `registration` (API, validation, JSON store, reconciliation), `notifications` (outbox, SMTP), `export` (Excel). Dependency rules are enforced by `ArchitectureTest`.

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| Startup fails with `APP_PROFILE must be ...` | set `APP_PROFILE` explicitly |
| `CAPTCHA_MODE=stub is not allowed in production` | use `recaptcha` with both keys in production |
| `ORGANIZER_PASSWORD_HASH must be a {bcrypt} or {pbkdf2} hash` | generate one with `../tools/hash-password.sh`; in `.env`, wrap it in single quotes |
| `catalog ...` / `unknown option group` / `duplicate option id` | fix the catalog file (see the schema) |
| Registration answers 503 | database or JSON volume not writable; nothing was accepted; the client may retry with the same `clientRequestId` |
| Mails not arriving | check SMTP settings; accepted registrations stay `PENDING` in `notification_outbox` and are retried (at-least-once) |
| Tests fail with Docker errors | Docker must be running and reachable by the current user |
| `./mvnw` checksum error | install `unzip`; the wrapper verifies the pinned `.zip` distribution |
