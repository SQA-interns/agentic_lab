# Release Notes — Conference Registration System 0.0.1

First release of the online conference registration system, built on the
frozen tooling scaffold (Java 21 / Spring Boot 3.5 / PostgreSQL 16 /
React + TypeScript + Vite / Docker Compose).

Documentation: [`docs/acceptance-criteria.md`](docs/acceptance-criteria.md),
[`docs/specification.md`](docs/specification.md),
[`docs/test-strategy.md`](docs/test-strategy.md),
[`docs/verification-report.md`](docs/verification-report.md).

## Features

- **External participant registration** (US-001) — first name, last name,
  email, organization / institution; optional conference options; mandatory
  privacy consent (not preselected).
- **Student registration** (US-002) — first name, last name, email, study
  institution, study programme, student ID (no external verification).
- **Configurable conference options** (US-003) — workshops, events, meals and
  other activities with stable id, display name and active flag, loaded from
  a JSON file (`APP_OPTIONS_FILE`, bundled sample by default). Only active
  options are offered and accepted; unknown/inactive ids are rejected.
  Changes apply after a backend restart; fixed fields are unaffected.
- **Confirmation** (US-004) — in-app confirmation with a registration
  reference, shown only after the backend returned `201 Created`.
- **Reliable storage** (US-005) — PostgreSQL (Flyway migration `V1`) plus an
  atomically written raw JSON backup per registration, in one transaction;
  a failed backup rolls the registration back.
- **Participant confirmation email** (US-006) and **organizer notification**
  (US-007) with all submitted data and the raw JSON attached; plain-text,
  sent after commit.
- **Excel export** (US-008) — `GET /api/organizer/registrations.xlsx`,
  protected by HTTP Basic organizer credentials (`ORGANIZER_USERNAME` /
  `ORGANIZER_PASSWORD`; export disabled when no password is set).

## REST API

| Method | Path | Access |
| --- | --- | --- |
| GET | `/api/form-config` | public |
| POST | `/api/registrations/external` | public (reCAPTCHA, rate limited) |
| POST | `/api/registrations/student` | public (reCAPTCHA, rate limited) |
| GET | `/api/organizer/registrations.xlsx` | organizer (HTTP Basic, rate limited) |
| GET | `/actuator/health`, `/actuator/health/{liveness,readiness}` | public, no details |

## Security

- Backend validation of all input (Bean Validation + business rules), strict
  JSON (unknown properties rejected), Unicode-aware trimming, control
  characters rejected; frontend validation for usability.
- Google reCAPTCHA v2 verified server-side; deterministic test mode only via
  `RECAPTCHA_TEST_MODE=true` (default `false`); production startup fails
  without secret or site key.
- Per-IP rate limiting (registrations 10/10 min, export 30/10 min, configurable).
- Request-size limit 16 KiB (filter, Tomcat, nginx).
- Security headers from Spring Security (backend) and nginx (frontend CSP
  allowing only reCAPTCHA origins).
- Uniform error responses without internal details; logs contain no
  personal data (unexpected errors log exception types only).
- Excel cells are strings; formula-like values are neutralised.
- Backend container runs as a non-root user.

## Dependencies added to the scaffold

| Dependency | Scope | Purpose |
| --- | --- | --- |
| `spring-boot-starter-security` | runtime | Export access control, security headers |
| `org.apache.poi:poi-ooxml` 5.5.1 | runtime | XLSX export |
| `spring-security-test` | test | Security test support |

No frontend dependencies were added.

## Version changes to scaffold tooling/configuration

- Spring Boot parent 3.4.4 → **3.5.16** (latest 3.x).
- Managed library overrides for security fixes: Tomcat **10.1.60**,
  PostgreSQL JDBC **42.7.13**.
- `spotbugs-maven-plugin` 10.12.15 (not published) → **4.10.4.1** so that
  SpotBugs can run.
- OWASP Dependency-Check configured with
  `backend/dependency-check-suppressions.xml` (justified, non-applicable
  Spring CVEs; enforced by `VulnerableFeatureGuardTest`).
- `vitest.config.ts` excludes `e2e/**`; `frontend/.prettierignore` and
  `frontend/.gitattributes` (LF) added; frontend container gets `nginx.conf`
  (SPA routing, headers, `/api` proxy for the local stack); Compose gains
  healthchecks and application environment variables.

## Verification status

Final complete test run: **164 passed, 0 failed** (backend unit 69,
backend integration 64, frontend 26, Playwright E2E 5). Semgrep 0
findings (frozen config); Dependency-Check 0 Critical / 0 High active;
npm audit production 0. All nine container demonstrations passed. Details
and residual findings: `docs/verification-report.md`.

## Known limitations

- Spring Framework 6.2.19 / Spring Security 6.5.11 carry CVEs whose fixes are
  not published as OSS for Spring Boot 3.x; they were triaged as affecting
  unused features (see verification report §4.1). Upgrade when possible.
- 24 Medium / 2 Low dependency findings and 3 Moderate dev-only npm findings
  (Vitest) remain.
- Emails are sent synchronously without retry; an SMTP outage does not block
  registration but the email is lost (logged by registration id).
- Option changes require a backend restart; rate limits are per instance.
- Production reCAPTCHA, external SMTP and the external TLS reverse proxy were
  not exercised live (mocked / local equivalents only).
