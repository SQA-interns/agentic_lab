# Conference registration

Online registration for a conference: external participants and students register through a web
form, choose configurable workshops, events, meals and other activities, and get a confirmation.
Every registration is stored in PostgreSQL and as a raw JSON copy; the participant and the
organizers are emailed; organizers export all registrations as an Excel workbook.

## Components

| Component | Folder | What it is |
|---|---|---|
| backend | [`backend/`](backend/README.md) | Spring Boot REST API under `/api` (Java 21) |
| frontend | [`frontend/`](frontend/README.md) | React single page (Vite), served by nginx |
| local stack | `docker-compose.yml` | backend, frontend, PostgreSQL 16, Mailpit (mail catcher) |
| options | `config/conference.json` | workshops, events, meals, other activities and consents |

## Quick start (local)

Prerequisites: Java 21, Node.js 24.13.0 with npm 11.6.2, Docker Engine with Compose. On Windows,
clone into a short path: `git -c core.longpaths=true clone <repository> C:\reg`.

```sh
cp 01_input/01_project/00_setup/secrets.env.example .env   # then fill the values in .env
(cd 02_output/backend && ./mvnw -B -DskipTests package)
02_output/scripts/compose.sh up -d --build --wait
```

Then open http://127.0.0.1:8081 (registration form) and http://127.0.0.1:8025 (Mailpit).
The organizer export is `http://127.0.0.1:8081/api/registrations/export` with the
`ORGANIZER_USERNAME` / `ORGANIZER_PASSWORD` from `.env`. Stop with
`02_output/scripts/compose.sh down` (add `-v` to delete the data volumes).

`compose.sh` passes only the keys the stack needs from `.env`; locally reCAPTCHA runs in test
mode (tick "I am not a robot (test mode)").

## Checks

```sh
02_output/scripts/verify.sh <label>          # every tool; logs in 02_output/logs/<label>_*.log
02_output/scripts/verify.sh <label> backend_test frontend_e2e   # selected tools
02_output/scripts/runtime-demo.sh            # end-to-end demonstration against the local stack
```

## Documentation map

| Document | Content |
|---|---|
| `docs/01_acceptance-criteria.md` | acceptance criteria per user story |
| `docs/02_specification.md` | architecture, configuration, security, traceability |
| `docs/02_contracts/` | REST API, UI, database, JSON copy, options file, emails, reCAPTCHA |
| `docs/03_test-strategy.md` | test levels and runs |
| `docs/06_verification-report.md` | verification evidence and findings |
| `docs/release-notes.md` | delivered scope, limitations, manual tests |
| `docs/decisions-log.md` | every decision of the run |
