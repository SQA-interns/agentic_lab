# Conference registration

> Written in: phase 7 · Source: `project/02_design/architecture.md`, ES-06 · Agent: writes

## Overview

Online registration for a conference. External participants and students register through a web form, choose workshops, events, meals and other activities from a configurable list, and give the required consents. Every accepted registration is stored in PostgreSQL and as a raw JSON copy on a persistent volume, the participant gets a confirmation email, and the organizers get a notification with the JSON attached. Organizers export all registrations as an Excel workbook. The form is protected by Google reCAPTCHA v2, rate limits and a request-size limit.

## Components

- [backend](backend/README.md): Spring Boot REST API under `/api` (Java 21): options, registration, export, health.
- [frontend](frontend/README.md): React single page served by nginx, which also forwards `/api` to the backend.
- `docker-compose.yml`: the local stack: backend, frontend, PostgreSQL 16 and the Mailpit mail catcher.

## Quick start

Prerequisites: Docker Engine with Docker Compose, JDK 21 (Temurin), Node.js 24 with npm 11 (versions: `../01_input/01_project/00_setup/tech-stack.md`).

1. At the repository root, copy `01_input/01_project/00_setup/secrets.env.example` to `.env` and fill `POSTGRES_PASSWORD`, `ORGANIZER_USERNAME`, `ORGANIZER_PASSWORD` (at least 16 random characters) and `ORGANIZER_EMAILS`. The reCAPTCHA and SMTP keys stay empty locally.
2. Build the backend jar (the backend image only packages it):

   ```bash
   cd 02_output/backend && ./mvnw -B package -DskipTests
   ```

3. Start the stack from `02_output/`:

   ```bash
   docker compose --env-file ../.env up -d --build
   ```

4. Open http://127.0.0.1:8081 for the form and http://127.0.0.1:8025 for the emails (Mailpit). The organizer export is http://127.0.0.1:8081/api/organizer/registrations.xlsx (the browser asks for the organizer login).
5. Stop with `docker compose --env-file ../.env down`; data stays in the named volumes `pgdata` and `jsondata` (add `-v` to delete them).

On Windows, clone into a short path (for example `C:\src\registration`): some test paths are long, and checkout fails beyond the 260-character path limit.

Locally, reCAPTCHA runs in its deterministic test mode and organizer access is allowed over plain HTTP on 127.0.0.1. Production needs real reCAPTCHA keys, HTTPS through a reverse proxy and an SMTP server: see the component READMEs and `docs/release-notes.md`.

## Documentation

| Document | Content |
|---|---|
| [docs/01_acceptance-criteria.md](docs/01_acceptance-criteria.md) | Acceptance criteria per user story |
| [docs/02_specification.md](docs/02_specification.md) | Architecture, design decisions, traceability |
| [docs/02_contracts/](docs/02_contracts/) | REST API (OpenAPI), JSON copy and options schemas, database schema, emails, UI, configuration, reCAPTCHA |
| [docs/03_test-strategy.md](docs/03_test-strategy.md) | Test levels, runs, coverage and mutation scores |
| [docs/06_verification-report.md](docs/06_verification-report.md) | Definition of Done evidence, security review, findings |
| [docs/release-notes.md](docs/release-notes.md) | What is delivered, limitations, manual tests, pending decisions |
| [docs/decisions-log.md](docs/decisions-log.md) | Decisions D-01 … D-12 |
| [docs/00_preflight-report.md](docs/00_preflight-report.md) | Environment and dependency checks |
