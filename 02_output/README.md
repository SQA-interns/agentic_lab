# Conference registration

> Written in: phase 7 · Source: `project/02_design/architecture.md`, ES-06 · Agent: writes

## Overview

Online registration for a conference. External participants and students register through a web
form and choose from configurable workshops, events, meals and other activities. Every accepted
registration is stored in PostgreSQL and as a JSON file, the participant gets a confirmation
email, the organizers get a notification with the JSON attached, and an organizer can download
all registrations as an Excel workbook.

## Components

| Component | What it is | README |
|---|---|---|
| backend | Spring Boot REST API under `/api`, Java 21 | [backend/README.md](backend/README.md) |
| frontend | React registration form, served by nginx | [frontend/README.md](frontend/README.md) |
| PostgreSQL, Mailpit | database and local mail catcher, started by `docker-compose.yml` | |

## Quick start

Prerequisites: Docker with Compose, JDK 21, and `.env` in the repository root (copy
`01_input/01_project/00_setup/secrets.env.example` and fill `POSTGRES_PASSWORD`,
`ORGANIZER_USERNAME`, `ORGANIZER_PASSWORD`, `ORGANIZER_EMAILS`). Run in `02_output`:

```sh
(cd backend && ./mvnw -B -DskipTests package)
docker compose --env-file ../.env up -d --build --wait
```

| What | Where |
|---|---|
| Registration form | http://127.0.0.1:8080/ |
| Caught emails (Mailpit) | http://127.0.0.1:8025/ |
| Organizer export | http://127.0.0.1:8080/api/registrations/export (organizer user name and password) |

```sh
docker compose --env-file ../.env ps        # all four services "healthy"
docker compose --env-file ../.env down      # stop; registrations and JSON copies are kept
```

The local stack uses plain HTTP on 127.0.0.1, the anti-automation test mode and the options in
`config/conference-options.json`. It is not a production configuration; production settings are
in [backend/README.md](backend/README.md).

## Checks

Every check and scanner runs through one script, from the repository root (Git Bash on Windows):

```sh
bash 02_output/scripts/verify.sh 7                    # build, checks, tests, scanners
bash 02_output/scripts/verify.sh 7 stack-up demo e2e  # local stack, runtime demonstration, browser tests
bash 02_output/scripts/verify.sh 7 stack-down
```

Each tool prints one line; its full output is in `logs/7_<tool>.log`.

## Documentation

| Document | Content |
|---|---|
| [docs/release-notes.md](docs/release-notes.md) | what was delivered, limitations, manual tests, decisions to review |
| [docs/01_acceptance-criteria.md](docs/01_acceptance-criteria.md) | acceptance criteria per user story |
| [docs/02_specification.md](docs/02_specification.md) | design, configuration, security controls, error behaviour |
| [docs/02_contracts/](docs/02_contracts/) | REST API, database schema, JSON copy, options file, emails, anti-automation, form |
| [docs/03_test-strategy.md](docs/03_test-strategy.md) | test levels and runs |
| [docs/06_verification-report.md](docs/06_verification-report.md) | definition of done, runtime demonstration, findings |
| [docs/decisions-log.md](docs/decisions-log.md) | every decision taken during the run |
