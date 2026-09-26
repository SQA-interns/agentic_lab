# Release Notes — Conference Registration System 0.0.1

First release, built on the frozen tooling scaffold. Details:
`docs/acceptance-criteria.md` (48 criteria), `docs/specification.md`,
`docs/test-strategy.md`, `docs/verification-report.md`.

## Features

- **External participant and student registration** (US-001, US-002) in
  one React form with a type switch; fixed fields per `FORM_SCHEMA.md`,
  client-side validation for usability and authoritative backend
  validation (required fields, email format, Unicode incl. Slovenian
  characters, Unicode-aware trimming, length limits, control-character
  rejection, fields of the other type rejected).
- **Configurable conference options** (US-003): workshops, events, meals
  and other activities with id, display name, category and active flag,
  plus consent definitions, in `conference.yml`. The packaged file holds
  sample values; an external file (`APP_CONFERENCE_CONFIG`, e.g. mounted
  at `/app/config/conference.yml`) overrides it after a restart. Only
  active options are offered and accepted; configuration is validated
  at startup.
- **Mandatory consent** that is never preselected.
- **In-app confirmation** only after a `201` from the backend (US-004);
  field errors or a general error otherwise, with the input kept.
- **Dual persistence** (US-005): PostgreSQL (Flyway migration `V1`) and a
  raw JSON file per registration, written atomically in the same
  transaction boundary (a backup failure rolls back the database insert).
- **Participant confirmation email** (US-006) and **organizer
  notification** with all submitted data and the raw JSON attached
  (US-007), plain-text UTF-8 over SMTP.
- **Excel export** (US-008): `GET /api/admin/registrations/export`
  returns an `.xlsx` with one row per registration, protected by HTTP
  Basic organizer credentials.
- **Health**: `/actuator/health`, `/actuator/health/readiness`,
  `/actuator/health/liveness`.

## Security controls

reCAPTCHA v2 (frontend widget + backend `siteverify`; deterministic test
mode only via `RECAPTCHA_TEST_MODE=true`, off by default, startup fails in
production mode without a secret), per-IP rate limit on registration,
16 KiB request-size limit, strict JSON (unknown properties rejected),
security headers on backend and frontend (CSP, `nosniff`, `DENY`,
referrer/permissions policy), uniform error bodies without internals,
formula-safe Excel cells, no personal data in logs, all secrets via
environment variables.

## Architecture

Layered backend with ports for side effects (`web` → `application` →
`domain` / `persistence`; `infrastructure` implements application ports;
`config` holds bound properties), enforced by 7 ArchUnit rules including
a cycle check. React SPA with a small REST client.

## Dependencies

| Change | Dependency | Reason |
| --- | --- | --- |
| Added (runtime) | `spring-boot-starter-security` | Export access control, headers, CORS |
| Added (runtime) | `org.apache.poi:poi-ooxml` 5.5.1 | `.xlsx` export |
| Added (test) | `spring-security-test` | Security tests |
| Changed | `spring-boot-starter-parent` 3.4.4 → **3.5.16** | Reduce known Critical/High CVEs (human decision during verification) |
| Frontend | none added | — |

## Deployment

- `backend/Dockerfile` now runs as a non-root user and pre-creates the
  backup directory for the `registration_backups` volume. Build the jar
  first: `cd backend && ./mvnw package`.
- `frontend/Dockerfile` adds `nginx.conf` (SPA routing, `/api/` proxy to
  `backend:8080`, security headers, body-size limit).
- `docker-compose.yml`: healthchecks (`pg_isready`, backend readiness),
  ordered startup, backup directory, organizer credentials and mail
  settings via `${VAR:-default}` for local use.
- Production configuration: `RECAPTCHA_TEST_MODE=false`,
  `RECAPTCHA_SECRET_KEY`, `RECAPTCHA_SITE_KEY`, `ORGANIZER_PASSWORD`,
  `APP_MAIL_FROM`, `APP_MAIL_ORGANIZER_RECIPIENTS`, `SPRING_MAIL_*`
  (incl. `SPRING_MAIL_SMTP_AUTH`/`_STARTTLS`), `SPRING_DATASOURCE_*`;
  TLS is terminated by the external nginx proxy.
- Repository hygiene: `.gitattributes` (LF line endings) and
  `frontend/.prettierignore`.

## Verification status

- Final complete test run: **140 passed, 0 failed** (backend unit 75,
  backend integration/API 25, frontend 34, E2E 6).
- Static checks, ArchUnit and all nine container demonstrations passed.

## Known issues

- **Open: vulnerable dependencies (V-09, Critical).** OWASP
  Dependency-Check still reports 22 Critical / 27 High matches (per jar)
  in `tomcat-embed-core` 10.1.55, `spring-core`/`spring-web` 6.2.19,
  `spring-security-*` 6.5.11, `log4j-api` 2.24.3 and `angus-activation`
  2.0.3 on the newest Spring Boot 3.x release. Fixes require a move
  beyond Spring Boot 3.x, which the frozen tech stack does not allow.
  Documented exception to DoD §3, authorized by the human; the findings
  were not triaged for exploitability.
- The frozen scaffold pins a non-existent `spotbugs-maven-plugin`
  version (10.12.15); SpotBugs has to be invoked with explicit
  coordinates (e.g. 4.10.4.1).
- npm audit: 3 moderate advisories in the dev-only Vitest toolchain from
  the frozen lockfile (0 in production dependencies).
- Not verified against live external services: Google reCAPTCHA in
  production mode, authenticated external SMTP, the production reverse
  proxy.
- Unknown API paths are denied with `401` and a Basic challenge, which a
  browser may show as a login prompt.
- Email delivery failures are logged but do not undo an already stored
  registration; the confirmation page says an email "is being sent".
