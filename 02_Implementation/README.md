# Conference Registration System

Online registration for external participants and students, with
configurable workshops/events/meals/other activities, confirmation and
organizer emails, a PostgreSQL store plus raw JSON backups, and an
organizer-only Excel export.

- Backend: Java 21, Spring Boot 3.5, PostgreSQL 16 (Flyway), Spring Mail
- Frontend: React + TypeScript + Vite, served by nginx
- Anti-automation: Google reCAPTCHA v2 (deterministic test mode for local use)

## 1. Prerequisites

- Docker with Docker Compose v2 (daemon running)
- JDK 21 (the Maven Wrapper `backend/mvnw` downloads Maven itself)
- Node.js 22+ and npm (only to run frontend tests or the dev server)

## 2. Configure

All commands below run from this directory (`02_Implementation/`).

```bash
cp .env.example .env
```

Edit `.env` (it is git-ignored — never commit it):

| Variable | Meaning |
| --- | --- |
| `POSTGRES_PASSWORD` | Database password used by both the `postgres` and `backend` containers |
| `APP_ORGANIZER_USERNAME`, `APP_ORGANIZER_PASSWORD` | Organizer login for export/restore (password ≥ 16 characters) |
| `APP_ORGANIZER_EMAILS` | Comma-separated organizer notification recipients |
| `APP_MAIL_FROM`, `APP_CONFERENCE_NAME` | Sender address and conference name used in emails |
| `RECAPTCHA_TEST_MODE` | `true` only for local use/tests; `false` (default) in production |
| `RECAPTCHA_SITE_KEY`, `RECAPTCHA_SECRET_KEY` | Required when test mode is off — the backend refuses to start without them |

Conference options (workshops, events, meals, other activities) live in
`config/conference-options.json` (format:
`docs/contracts/conference-options.schema.json`). Edit the file to change
the programme; the running backend picks up the change on the next
request. Options are never deleted — remove an entry or set
`"active": false` to stop offering it.

For production SMTP, add `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`,
`SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` and
`SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true` to the backend
service environment (see `docs/specification.md` §9 for every setting).

## 3. Build and run with Docker Compose

```bash
cd backend && ./mvnw clean package -DskipTests && cd ..
docker compose up -d --build
```

The backend image copies `backend/target/*.jar`, so build the JAR first.

| URL | What |
| --- | --- |
| <http://localhost/> | Registration form (frontend container; also proxies `/api`) |
| <http://localhost:8080/actuator/health/readiness> | Backend readiness (compose publishes 8080 on 127.0.0.1 only) |
| <http://localhost:8025/> | Mailpit — captured local email |

Check that everything is up:

```bash
docker compose ps
curl -s http://localhost:8080/actuator/health/readiness
```

Stop with `docker compose down` (data volumes are kept; add `-v` only
if you really want to delete all registrations and backups).

## 4. Organizer operations

Excel export of all registrations:

```bash
curl -u "$APP_ORGANIZER_USERNAME:$APP_ORGANIZER_PASSWORD" -o registrations.xlsx http://localhost/api/organizer/registrations/export
```

Restore registrations from the JSON backups (e.g. after restoring an
empty or damaged database; idempotent, sends no email):

```bash
curl -u "$APP_ORGANIZER_USERNAME:$APP_ORGANIZER_PASSWORD" -H 'Content-Type: application/json' -d '{}' http://localhost/api/organizer/backups/restore
```

The JSON backups are on the `registration_backups` volume
(`/app/data/registrations/<id>.json` in the backend container).

## 5. Tests and checks

Backend (needs Docker for Testcontainers):

```bash
cd backend
./mvnw test                                   # acceptance + unit + integration tests, JaCoCo report
./mvnw spotless:check pmd:check pmd:cpd-check  # formatting, PMD, duplication
./mvnw compile com.github.spotbugs:spotbugs-maven-plugin:spotbugs
./mvnw test-compile org.pitest:pitest-maven:mutationCoverage
```

Frontend:

```bash
cd frontend
npm ci
npm run lint && npm run typecheck && npm run format && npm test
npm run test:coverage
npm run test:mutation
npm run duplication
```

End-to-end (backend running on :8080 in test mode, e.g. via compose):

```bash
cd frontend && npx playwright install chromium && npx playwright test
```

## 6. Documentation map

| Document | Content |
| --- | --- |
| `docs/acceptance-criteria.md` | Acceptance criteria (Given–When–Then) |
| `docs/specification.md` | Architecture, API, data model, security, configuration |
| `docs/contracts/` | OpenAPI contract and JSON schemas (options file, backup) |
| `docs/acceptance/MANIFEST.sha256` | Hashes of the frozen acceptance tests |
| `docs/test-strategy.md` | Test levels, red run, first/final complete runs |
| `docs/verification-report.md` | Self-verification results and findings |
| `docs/decisions-log.md` | Every escalated decision and its resolution |
| `docs/implementation-notes.md` | Added dependencies and implementation decisions |
| `RELEASE_NOTES.md` | What is delivered, known limitations and open items |
