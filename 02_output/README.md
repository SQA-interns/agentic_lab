# Conference registration

> Written in: phase 7 · Source: `project/02_design/architecture.md`, ES-06 · Agent: writes

## Overview

Online registration for a conference: external participants and students fill in a form, choose
configured workshops, events, meals and other activities, and give the mandatory consent. Every
accepted registration is stored in PostgreSQL and as a raw JSON copy, confirmed in the page and by
email, announced to the organizers with the JSON attached, and exportable to Excel by organizers.

## Components

- [backend](backend/README.md): Spring Boot REST API under `/api` (validation, anti-automation check, storage, emails, export)
- [frontend](frontend/README.md): React page with both forms, served by nginx

## Quick start

Prerequisites: JDK 21, Node.js 24 with npm, Docker with Compose (versions: `01_input/01_project/00_setup/tech-stack.md`), and a filled `.env` in the repository root (keys: `01_input/01_project/00_setup/secrets.env.example`). Run from the repository root:

```bash
(cd 02_output/backend && ./mvnw -B -DskipTests package)
(cd 02_output/frontend && npm ci)
bash 01_input/00_general/tools/secrets.sh run POSTGRES_PASSWORD,ORGANIZER_USERNAME,ORGANIZER_PASSWORD,ORGANIZER_EMAILS \
  -- docker compose -f 02_output/docker-compose.yml up -d --build --wait
```

| URL | What |
|---|---|
| http://127.0.0.1:8081 | registration page (reCAPTCHA test mode on the local stack) |
| http://127.0.0.1:8025 | Mailpit, the local mail catcher |
| http://127.0.0.1:8080/actuator/health | backend health |

Organizer export from the local machine (the token is valid 15 minutes):

```bash
TOKEN=$(curl -s -H 'Content-Type: application/json' -d '{"username":"<organizer>","password":"<password>"}' \
  http://127.0.0.1:8080/api/organizer/token | sed -E 's/.*"token":"([^"]+)".*/\1/')
curl -s -H "Authorization: Bearer $TOKEN" -o registrations.xlsx http://127.0.0.1:8080/api/organizer/registrations/export
```

Checks, tests and scanners (`<phase>` names the log prefix in `02_output/logs/`):

```bash
bash 02_output/scripts/verify.sh <phase>                 # all tools that need no running stack
bash 02_output/scripts/verify.sh <phase> e2e             # end-to-end tests against the running stack
```

Stop the stack (data stays in the volumes `registration_pgdata` and `registration_jsoncopies`):

```bash
bash 01_input/00_general/tools/secrets.sh run POSTGRES_PASSWORD,ORGANIZER_USERNAME,ORGANIZER_PASSWORD,ORGANIZER_EMAILS \
  -- docker compose -f 02_output/docker-compose.yml down
```

## Documentation

| Document | Content |
|---|---|
| [docs/01_acceptance-criteria.md](docs/01_acceptance-criteria.md) | acceptance criteria per user story |
| [docs/02_specification.md](docs/02_specification.md) | architecture, flow, validation, configuration, security controls |
| [docs/02_contracts/](docs/02_contracts/) | REST, UI, storage, JSON copy, options, email and reCAPTCHA contracts |
| [docs/03_test-strategy.md](docs/03_test-strategy.md) | test levels, runs, coverage, mutation |
| [docs/06_verification-report.md](docs/06_verification-report.md) | Definition of Done evidence, findings |
| [docs/release-notes.md](docs/release-notes.md) | delivered scope, limitations, manual tests, decisions to review |
| [docs/decisions-log.md](docs/decisions-log.md) | all decisions |
