# Conference registration

Online registration for a conference: external participants and students register through a web form, choose configured workshops, events, meals and other activities, and receive a confirmation email. Each registration is stored in PostgreSQL and as a raw JSON copy; organizers get an email with the JSON attached and can export all registrations as an Excel workbook.

## Components

| Component | Purpose | README |
|---|---|---|
| backend | REST API: validation, storage, emails, export, health | [backend/README.md](backend/README.md) |
| frontend | registration page served by nginx | [frontend/README.md](frontend/README.md) |
| local stack | both components, PostgreSQL, Mailpit (mail catcher) | [docker-compose.yml](docker-compose.yml) |

Options and consents are configured in [config/conference-config.json](config/conference-config.json) (format: `docs/02_contracts/conference-config.schema.json`); a change takes effect after restarting the backend.

## Quick start

Prerequisites: JDK 21, Node.js 24, Docker; `.env` in the repository root filled from `01_input/01_project/secrets.env.example`. From this folder (`02_output`):

```bash
./backend/mvnw -B -f backend/pom.xml package -DskipTests
bash ../01_input/00_general/tools/secrets.sh run POSTGRES_PASSWORD,ORGANIZER_USERNAME,ORGANIZER_PASSWORD,ORGANIZER_EMAILS -- docker compose up -d --build --wait
```

| What | Where |
|---|---|
| Registration page | http://127.0.0.1:8081 |
| Emails (Mailpit) | http://127.0.0.1:8025 |
| Organizer export | http://127.0.0.1:8081/api/registrations/export (organizer credentials from `.env`) |
| Health | http://127.0.0.1:8080/actuator/health/readiness |

Locally the anti-automation check runs in test mode (tick "I am not a robot (test mode)"). Stop with `docker compose down`; data stays in the volumes `registration-tanej04_pgdata` and `registration-tanej04_jsoncopies`.

All tests, checks and scanners: `bash scripts/verify.sh <phase>` (one line per tool, logs in `logs/`); end-to-end tests: `bash scripts/verify.sh <phase> e2e` with the stack running.

## Documentation

| Document | Content |
|---|---|
| [docs/01_acceptance-criteria.md](docs/01_acceptance-criteria.md) | acceptance criteria per user story |
| [docs/02_specification.md](docs/02_specification.md) | architecture, configuration, security controls, traceability |
| [docs/02_contracts/](docs/02_contracts/) | API, database, JSON copy, configuration, email and UI contracts |
| [docs/03_test-strategy.md](docs/03_test-strategy.md) | test levels, runs and measures |
| [docs/06_verification-report.md](docs/06_verification-report.md) | definition of done, findings, traceability |
| [docs/decisions-log.md](docs/decisions-log.md) | decisions D-01 to D-19 |
| [docs/release-notes.md](docs/release-notes.md) | delivered stories, limitations, checks before production |
