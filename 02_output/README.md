# Conference registration

> Written in: phase 7 · Source: `project/03_technical/architecture.md`, ES-06 · Agent: writes

## Overview

Online registration for a conference. External participants and students register through a web form, choose configurable workshops, events, meals and other activities, and give the required consents. Each accepted registration is stored in PostgreSQL and as a raw JSON copy, confirmed in the application and by email, and announced to the organizers by email with the JSON attached. Organizers download all registrations as an Excel workbook.

## Components

| Component | Folder | What it is |
|---|---|---|
| backend | [`backend/`](backend/README.md) | Spring Boot REST API under `/api`: options, registration, organizer export; PostgreSQL, JSON copies, SMTP, reCAPTCHA verification |
| frontend | [`frontend/`](frontend/README.md) | React single-page form served by nginx; talks to the backend only through `/api` |

The local stack (`docker-compose.yml`) adds PostgreSQL and Mailpit (a local mail catcher).

## Quick start

On Windows, clone with long paths enabled (`git -c core.longpaths=true clone …`) or into a short directory: some source paths exceed 260 characters under deep folders.

Prerequisites: Docker Engine 29.8.0 with Compose 5.5.1, Temurin JDK 21.0.10+7 (to build the backend jar). Versions: `01_input/01_project/03_technical/tech-stack.md`.

1. Create `.env` in the repository root from `01_input/01_project/01_setup/secrets.env.example` and fill `POSTGRES_PASSWORD`, `ORGANIZER_USERNAME`, `ORGANIZER_PASSWORD` (at least 16 characters) and `ORGANIZER_EMAILS`. reCAPTCHA and SMTP keys are not needed locally.
2. From `02_output/`:

   ```bash
   cd backend && ./mvnw -B package -DskipTests && cd ..
   ```

   ```bash
   docker compose --env-file ../.env up -d --build
   ```

3. Open http://127.0.0.1:8080 (form), http://127.0.0.1:8025 (Mailpit, delivered emails). The export is at http://127.0.0.1:8080/api/admin/registrations/export with the organizer credentials from `.env`.
4. Stop with `docker compose --env-file ../.env down` (data stays in the named volumes; add `-v` to delete it).

Locally the stack runs in reCAPTCHA test mode (a "not a robot (test mode)" checkbox) and allows organizer access over plain HTTP on 127.0.0.1 only; production refuses both (see the backend README).

## Documentation

| Document | Content |
|---|---|
| [`docs/01_acceptance-criteria.md`](docs/01_acceptance-criteria.md) | acceptance criteria per user story |
| [`docs/02_specification.md`](docs/02_specification.md) | architecture, configuration, API, processing, security, traceability |
| [`docs/02_contracts/`](docs/02_contracts/) | OpenAPI, JSON schemas (JSON copy, options configuration), email contract |
| [`docs/03_test-strategy.md`](docs/03_test-strategy.md) | test levels, first and final runs |
| [`docs/06_verification-report.md`](docs/06_verification-report.md) | Definition of Done evidence, security review, findings, measures |
| [`docs/release-notes.md`](docs/release-notes.md) | delivered scope, limitations, manual tests, pending decisions |
| [`docs/decisions-log.md`](docs/decisions-log.md) | decisions D-01 … |
| [`docs/00_preflight-report.md`](docs/00_preflight-report.md), [`docs/00_progress.md`](docs/00_progress.md) | environment checks, run progress |
| `logs/` | raw tool output referenced by the documents |
