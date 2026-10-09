# Conference registration

> Written in: phase 7 · Source: `project/constraints.md` ("Components"), ES-06 · Agent: writes

## Overview

Online registration for a conference: external participants and students register through a web form with configurable workshops, events, meals and other activities. Each accepted registration is stored in PostgreSQL and as a raw JSON copy, confirmed by email to the participant and notified to the organizers with the JSON attached. Organizers download all registrations as an Excel workbook.

## Components

| Component | What it is | README |
|---|---|---|
| backend | Spring Boot REST API under `/api` (Java 21) | [backend/README.md](backend/README.md) |
| frontend | React page served by nginx, which proxies `/api` to the backend | [frontend/README.md](frontend/README.md) |

The local stack (`docker-compose.yml`) adds PostgreSQL 16 and Mailpit (mail catcher).

## Quick start

Prerequisites: Docker Engine with Compose, JDK 21, Git Bash; a filled `.env` in the repository root (keys in `01_input/01_project/secrets.env.example`). Commands run from the repository root.

```bash
bash 01_input/00_general/tools/secrets.sh check
(cd 02_output/backend && ./mvnw -B -DskipTests package)
(cd 02_output && bash ../01_input/00_general/tools/secrets.sh run POSTGRES_PASSWORD,ORGANIZER_USERNAME,ORGANIZER_PASSWORD,ORGANIZER_EMAILS -- docker compose up -d --build --wait)
```

| URL | What |
|---|---|
| http://127.0.0.1:8080 | registration page (anti-automation in test mode: tick "I am not a robot (test mode)") |
| http://127.0.0.1:8080/api/export | organizer workbook (browser asks for `ORGANIZER_USERNAME` / `ORGANIZER_PASSWORD`) |
| http://127.0.0.1:8025 | Mailpit: the emails sent |

Stop (data stays in the named volumes `db-data` and `registration-copies`):

```bash
(cd 02_output && bash ../01_input/00_general/tools/secrets.sh run POSTGRES_PASSWORD,ORGANIZER_USERNAME,ORGANIZER_PASSWORD,ORGANIZER_EMAILS -- docker compose down)
```

All checks, scanners and tests: `bash 02_output/scripts/verify.sh <label> [tools]` (tool list at the top of the script; `stack-up`, `e2e`, `runtime` and `stack-down` drive the local stack).

## Documentation

| Document | Content |
|---|---|
| [docs/01_acceptance-criteria.md](docs/01_acceptance-criteria.md) | acceptance criteria per user story |
| [docs/02_specification.md](docs/02_specification.md) | architecture, flows, configuration, security controls |
| [docs/02_contracts/](docs/02_contracts/) | REST API, database, JSON copy, options file, emails, workbook, UI |
| [docs/03_test-strategy.md](docs/03_test-strategy.md) | test levels, runs and measures |
| [docs/06_verification-report.md](docs/06_verification-report.md) | definition of done, runtime demonstration, findings |
| [docs/release-notes.md](docs/release-notes.md) | what was delivered, limitations, checks before production |
| [docs/decisions-log.md](docs/decisions-log.md) | decisions D-01..D-20 |
