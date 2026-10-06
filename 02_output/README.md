# Conference registration

> Written in: phase 7 · Source: `project/02_design/architecture.md`, ES-06 · Agent: writes

## Overview

Online registration for external participants and students of a conference: configurable workshops, events, meals and other activities; storage in PostgreSQL plus a raw JSON copy; confirmation and organizer emails; an Excel export for organizers; reCAPTCHA v2 against automated submissions.

## Components

- [backend](backend/README.md): Spring Boot REST API under `/api` (Java 21).
- [frontend](frontend/README.md): React single-page form, served by nginx, proxies `/api` to the backend.
- `docker-compose.yml`: local stack (backend, frontend, PostgreSQL, Mailpit).

## Quick start

Clone into a short path (Windows path limits, KP-05):

```sh
git -c core.longpaths=true clone <repository-url> reg
cd reg
cp 01_input/01_project/00_setup/secrets.env.example .env   # then fill the values marked "provided"
cd 02_output
docker compose --env-file ../.env up -d --build --wait
```

| What | Where |
|---|---|
| Registration form | http://127.0.0.1:8090 |
| Mails (Mailpit) | http://127.0.0.1:8025 |
| Organizer export | `curl -u "$ORGANIZER_USERNAME:$ORGANIZER_PASSWORD" -o registrations.xlsx http://127.0.0.1:8090/api/admin/registrations/export` |

Stop with `docker compose --env-file ../.env down` (data stays in the named volumes `pgdata`, `jsoncopies`); `down -v` deletes it. Ports can be changed with `FRONTEND_PORT` and `MAILPIT_PORT`.

All checks and scanners: `scripts/verify.sh <phase> [tool ...]` (logs in `logs/`). Runtime demonstration: `scripts/runtime-demo.sh`.

## Documentation

| Document | Content |
|---|---|
| [docs/01_acceptance-criteria.md](docs/01_acceptance-criteria.md) | acceptance criteria per story |
| [docs/02_specification.md](docs/02_specification.md) | architecture, configuration, security controls |
| [docs/02_contracts/](docs/02_contracts/) | OpenAPI, database, JSON copy, options file, emails, reCAPTCHA, form |
| [docs/03_test-strategy.md](docs/03_test-strategy.md) | test levels and runs |
| [docs/06_verification-report.md](docs/06_verification-report.md) | DoD evidence and findings |
| [docs/decisions-log.md](docs/decisions-log.md) | decisions |
| [docs/release-notes.md](docs/release-notes.md) | delivered scope, limitations, manual tests |
