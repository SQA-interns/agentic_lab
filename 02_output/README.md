# Conference registration

> Written in: phase 7 · Source: `project/02_design/architecture.md`, ES-06 · Agent: writes

## Overview

Online registration for a conference: external participants and students register through a web form, choose configurable workshops, events, meals and other activities, and receive a confirmation email. Organizers receive each registration by email with its raw JSON copy and can export all registrations to Excel. Registrations are stored in PostgreSQL and as JSON files.

## Components

- [backend](backend/README.md): Spring Boot REST API under `/api` (Java 21, PostgreSQL, SMTP, reCAPTCHA).
- [frontend](frontend/README.md): React single-page form served by nginx, which also proxies `/api` locally.

The local stack (`docker-compose.yml`) runs backend, frontend, PostgreSQL 16 and Mailpit on `127.0.0.1` only.

## Quick start

Prerequisites: Git, Docker Engine with Compose, JDK 21, Node.js 24.13.0 with npm 11.6.2 on `PATH`.

```sh
git -c core.longpaths=true clone <repository-url> reg   # clone into a short path (Windows path limits)
cd reg
cp 01_input/01_project/00_setup/secrets.env.example .env  # fill POSTGRES_PASSWORD, ORGANIZER_USERNAME,
                                                         # ORGANIZER_PASSWORD (16+ chars), ORGANIZER_EMAILS
cd 02_output/backend && ./mvnw -B package -DskipTests && cd ..
scripts/compose.sh up -d --build --wait
```

- Registration form: http://127.0.0.1:8081 (reCAPTCHA test mode: tick "I am not a robot (test mode)")
- Emails (Mailpit): http://127.0.0.1:8025
- Export: `curl -u "$ORGANIZER_USERNAME:$ORGANIZER_PASSWORD" -o registrations.xlsx http://127.0.0.1:8081/api/export`
- Stop: `scripts/compose.sh down` (add `-v` to delete the data volumes)

`scripts/compose.sh` passes only the `KEY=value` lines of `.env` to Compose and never prints them.

## Checks

```sh
scripts/verify.sh local            # every check, test and scanner; logs in logs/local_*.log
scripts/runtime-demo.sh            # end-to-end demonstration on a fresh local stack
```

## Documentation

- [Acceptance criteria](docs/01_acceptance-criteria.md)
- [Specification](docs/02_specification.md) and [contracts](docs/02_contracts/)
- [Test strategy](docs/03_test-strategy.md)
- [Verification report](docs/06_verification-report.md)
- [Decisions](docs/decisions-log.md)
- [Release notes](docs/release-notes.md)
