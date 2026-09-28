# Conference Registration System

Online registration for conference participants (external participants and
students), with configurable conference options, confirmation emails,
organizer notifications, JSON backups and an organizer Excel export.

Design: [`docs/specification.md`](docs/specification.md).
Acceptance criteria: [`docs/acceptance-criteria.md`](docs/acceptance-criteria.md).

## Layout

```
backend/    Spring Boot 3.5 / Java 21 REST API (Maven Wrapper)
frontend/   React + TypeScript + Vite SPA, served by nginx in the container
docker-compose.yml  postgres, Mailpit (SMTP), backend, frontend
docs/       acceptance criteria, specification, test strategy, verification
```

## Running locally with Docker Compose

```bash
cd backend && ./mvnw -DskipTests package && cd ..
docker compose up -d --build
```

- Registration form: http://localhost/
- Mailpit (captured emails): http://localhost:8025/
- Export: `curl -u organizer:$ORGANIZER_PASSWORD -o registrations.xlsx http://localhost/api/organizer/registrations.xlsx`
  (compose falls back to the local-only password `local-dev-organizer` when
  `ORGANIZER_PASSWORD` is not set in the host environment)
- Health: http://localhost:8080/actuator/health/readiness

Compose runs reCAPTCHA in deterministic **test mode** (`RECAPTCHA_TEST_MODE=true`):
the form shows an "I am not a robot (test mode)" checkbox and the backend
accepts only the token `test-mode-pass`. Production must set
`RECAPTCHA_TEST_MODE=false` (the default), `RECAPTCHA_SECRET_KEY` and
`RECAPTCHA_SITE_KEY`.

## Running without containers (development)

```bash
docker compose up -d postgres smtp
cd backend && RECAPTCHA_TEST_MODE=true ORGANIZER_PASSWORD=dev ./mvnw spring-boot:run
cd frontend && npm ci && npm run dev   # http://localhost:5173, proxies /api to :8080
```

## Configuration

All environment-specific values are environment variables — see
specification §12. Conference options are configured in a JSON file
(`APP_OPTIONS_FILE`, default: bundled `backend/src/main/resources/conference-options.json`);
edit the file and restart the backend to change the programme.

## Implementation notes

### Added dependencies (versus the frozen scaffold)

| Dependency | Scope | Reason |
| --- | --- | --- |
| `spring-boot-starter-security` | runtime | Organizer access control (HTTP Basic), security headers (allowed by TECH_STACK) |
| `org.apache.poi:poi-ooxml` 5.5.1 | runtime | Excel (XLSX) export (US-008) |
| `spring-security-test` | test | Security testing support |

The Spring Boot parent was raised from 3.4.4 to **3.5.16** (latest 3.x patch)
to address dependency vulnerabilities (SECURITY_REQUIREMENTS, spec §7.11).
No frontend dependencies were added.

### Important decisions

- Layered architecture with ports (`api` → `service` ← `infrastructure`,
  `domain` at the core) — spec §2.
- Emails are sent synchronously after the DB commit; a mail failure is
  logged (registration id only) and does not revoke an accepted
  registration — spec §8.3.
- The JSON backup is written inside the DB transaction; failure rolls the
  registration back and the client gets 500 without confirmation. If the
  commit fails, the backup file is deleted — spec §6.1.
- Bean validation errors are reported first; option/consent business
  validation runs only once the fixed fields are valid.
- `registration_option` stores an option snapshot (id, category, name) so
  exports stay meaningful after configuration changes.
- Frontend nginx proxies `/api` to the backend for the local compose stack;
  in production the external reverse proxy does this.
- Backend container runs as a non-root user.
