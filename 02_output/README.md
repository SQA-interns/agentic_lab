# Lab Conference registration

> Written in: phase 7 · Source: `project/03_technical/architecture.md`, ES-06 · Agent: writes

## Overview

A conference registration application for synthetic lab use:
- external participants and students register through separate forms and choose workshops, events, meals and other activities from a catalog that is configured at startup;
- a registration counts as **accepted** only once it is stored in PostgreSQL **and** as a durable raw JSON file;
- participants receive a confirmation email, and organizers receive a notification with the JSON attached (durable retry through an outbox);
- an authenticated organizer can download all accepted registrations as Excel.

Private lab use only: no licence is granted (OQ-05), and only synthetic data and reserved `example.test` addresses may be used.

## Components

| Component | Folder | README |
|---|---|---|
| Backend: Spring Boot 3.5.16 REST API, Java 21 | `backend/` | [backend/README.md](backend/README.md) |
| Frontend: React 19 / Vite SPA served by nginx | `frontend/` | [frontend/README.md](frontend/README.md) |
| Local stack: PostgreSQL 16.14, Mailpit, backend, frontend | `compose.yaml` | this file |
| Synthetic catalog | `config/conference.local.json` | backend README, "Catalog" |
| Helper scripts | `tools/` | `hash-password.sh`, `runtime-demo.sh`, `manifests.sh` |

## Quick start (local, synthetic)

Prerequisites: Docker Engine with Compose (pinned 28.1.1 / 2.35.1; verified on 29.3.1 / v5.1.1), Git, and Python 3 (used only by `tools/runtime-demo.sh`). Free host ports 18080 and 18025.

```sh
cd 02_output
# 1. Secrets: copy the list of names, then fill the values (never commit .env)
cp ../01_input/01_project/01_setup/secrets.env.example .env
#    DB_PASSWORD=<random string, e.g. from: openssl rand -base64 24>
#    ORGANIZER_USERNAME=organizer
#    ORGANIZER_PASSWORD_HASH='<output of tools/hash-password.sh, in single quotes>'
tools/hash-password.sh          # builds the backend image on first use; asks for the organizer password
# 2. Start everything
docker compose up -d --build
docker compose ps               # wait until backend and frontend are "healthy"
```

Then open:
- http://127.0.0.1:18080: registration forms (use the "Local test captcha" checkbox);
- http://127.0.0.1:18080/organizer: Excel export (log in with the organizer username and password);
- http://127.0.0.1:18025: Mailpit, the isolated SMTP catcher; no mail leaves the machine.

Optional: `tools/runtime-demo.sh` exercises every core flow against the running stack, including a restart, an SMTP outage and a catalog change. It reads the organizer password from `.local/organizer-demo-password`, a git-ignored file you create.

Stop with `docker compose down`. Registration data stays in the volumes `agenticlab_pgdata` and `agenticlab_jsondata`, and Mailpit keeps copies of the mails in memory. To delete **all** synthetic personal data (SB-13, OQ-03), run `docker compose down -v`.

Environment names and their defaults: [docs/02_specification.md](docs/02_specification.md) section 10, and the component READMEs.

## Local substitutes vs production

| Concern | Local (this repository) | Production (not verified in this run) |
|---|---|---|
| TLS | plain HTTP on 127.0.0.1 | external nginx with Let's Encrypt; set `TRUSTED_PROXIES` to the proxy address |
| Captcha | `CAPTCHA_MODE=stub` (`APP_PROFILE=local`) | `CAPTCHA_MODE=recaptcha` with real keys; the stub is refused when `APP_PROFILE=production` |
| Mail | Mailpit catcher | authenticated SMTP with `SMTP_TLS_ENABLED=true` (enforced in production) |
| Consent text | synthetic fixture | real wording and legal basis still needed (OQ-02, blocks production) |
| Retention | until the operator runs `docker compose down -v` | policy not supplied (OQ-03, blocks production) |

The manual production checklist is in [docs/release-notes.md](docs/release-notes.md).

## Documentation

| Document | Content |
|---|---|
| [docs/00_preflight-report.md](docs/00_preflight-report.md) | environment and dependency preflight |
| [docs/01_acceptance-criteria.md](docs/01_acceptance-criteria.md) | acceptance criteria per story |
| [docs/02_specification.md](docs/02_specification.md) | design, security mapping, configuration |
| [docs/02_contracts/](docs/02_contracts/) | OpenAPI, JSON Schemas, export contract |
| [docs/03_test-strategy.md](docs/03_test-strategy.md) | test levels, first and final runs |
| [docs/06_verification-report.md](docs/06_verification-report.md) | DoD evidence, findings, traceability |
| [docs/decisions-log.md](docs/decisions-log.md) | all decisions |
| [docs/release-notes.md](docs/release-notes.md) | delivered scope, limitations, manual tests |
| `logs/` | raw tool output (scanners, test runs, runtime demonstration) |
