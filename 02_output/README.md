# Conference registration

> Written in: phase 7 · Source: `project/constraints.md` ("Components"), ES-06 · Agent: writes

## Overview

Online registration for a conference: external participants and students register through a web form, choose configurable workshops, events, meals and other activities, and receive a confirmation email. Every accepted registration is stored in PostgreSQL and as a raw JSON copy; organizers get an email with the JSON attached and can download all registrations as an Excel workbook.

## Components

| Component | Folder | What it is |
|---|---|---|
| backend | [`backend/`](backend/README.md) | Spring Boot REST API under `/api` (Java 21) |
| frontend | [`frontend/`](frontend/README.md) | React single-page form, served by nginx (Node 24 build) |
| local stack | `docker-compose.yml` | backend, frontend, PostgreSQL 16, Mailpit (mail catcher) |

## Quick start

Prerequisites: Docker Engine with Compose, and `.env` in the repository root filled from `01_input/01_project/secrets.env.example` (`POSTGRES_PASSWORD`, `ORGANIZER_USERNAME`, `ORGANIZER_PASSWORD`, `ORGANIZER_EMAILS`). The backend jar must be built first (`backend/README.md`, "Build").

From the repository root:

```sh
02_output/backend/mvnw -B -ntp -f 02_output/backend/pom.xml -DskipTests package
bash 01_input/00_general/tools/secrets.sh run POSTGRES_PASSWORD,ORGANIZER_USERNAME,ORGANIZER_PASSWORD,ORGANIZER_EMAILS \
  -- docker compose -f 02_output/docker-compose.yml up -d --build --wait
```

- Registration form: http://127.0.0.1:8088/
- Emails (Mailpit): http://127.0.0.1:8026/
- Export (organizer credentials from `.env`): http://127.0.0.1:8088/api/export/registrations.xlsx

Stop: `bash 01_input/00_general/tools/secrets.sh run POSTGRES_PASSWORD,ORGANIZER_USERNAME,ORGANIZER_PASSWORD,ORGANIZER_EMAILS -- docker compose -f 02_output/docker-compose.yml down` (data stays on the named volumes).

All checks and scanners: `02_output/scripts/verify.sh <label> [default|all|tool,...]`; runtime demonstration: `02_output/scripts/runtime-demo.sh`.

## Documentation

| Document | Content |
|---|---|
| [`docs/01_acceptance-criteria.md`](docs/01_acceptance-criteria.md) | acceptance criteria per user story |
| [`docs/02_specification.md`](docs/02_specification.md) | architecture, configuration, security controls, traceability |
| [`docs/02_contracts/`](docs/02_contracts/) | OpenAPI, JSON schemas, SQL, email and UI contracts |
| [`docs/03_test-strategy.md`](docs/03_test-strategy.md) | test levels, runs, coverage and mutation |
| [`docs/06_verification-report.md`](docs/06_verification-report.md) | definition of done evidence, findings, runtime demonstration |
| [`docs/decisions-log.md`](docs/decisions-log.md) | every decision taken during the run |
| [`docs/release-notes.md`](docs/release-notes.md) | delivered scope, limitations, checks before production |
