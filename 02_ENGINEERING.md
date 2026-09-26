# Engineering contract — frozen choices and constraints

## Frozen stack

| Area | Required technology |
|---|---|
| Backend | Java 21 LTS; Spring Boot 3.x; Maven Wrapper; Spring Web, Data JPA, Bean Validation, Mail and Actuator; Spring Security where needed |
| Data | PostgreSQL 16; JPA/Hibernate; Flyway migrations |
| Frontend | React, TypeScript, Vite; communicates with backend over REST |
| Testing | JUnit 5, Mockito, Spring Boot Test, MockMvc/WebTestClient, Testcontainers; Vitest, React Testing Library, Playwright |
| Quality | JaCoCo; Spotless, SpotBugs, PMD/CPD, ArchUnit; ESLint/typescript-eslint, Prettier, `tsc --noEmit`, jscpd |
| Security | Semgrep; OWASP Dependency-Check; `npm audit` |
| Runtime | Docker Compose; backend, frontend and PostgreSQL containers; local SMTP service |
| Anti-automation | Google reCAPTCHA v2; frontend interaction and server-side validation |

Do not substitute technologies. The scaffold freezes tooling only—not package
layout, REST design, data model, or architecture. The specification must choose
and justify those, and later ArchUnit tests must verify the chosen design.

## Runtime and deployment

- Local development/test may use HTTP. Production uses containerized frontend
  and backend behind an external nginx proxy that terminates HTTPS, handles
  Let's Encrypt, routes frontend traffic, and forwards `/api` to the backend.
- Containers expose HTTP internally; TLS termination is not an application
  container concern.
- Database data and raw JSON backups survive container recreation.
- Secrets and environment-specific values are configuration, never hard-coded.
- Development/test reCAPTCHA uses deterministic environment-only test mode;
  production defaults to real server-side validation and must never silently
  use test mode.
- Automated checks must not call the live Google reCAPTCHA service.
- Expose health/readiness suitable for container verification.

## Security constraints

The specification must cover backend validation of all input (frontend
validation is usability only), malicious input, automated submissions, rate
limiting where appropriate, secure database access, safe output/error/email
handling, HTTP security headers, request-size limits, secret management,
dependency vulnerabilities, minimal personal-data logging, and configurable
option-ID validation. Export access must be protected. Decide and justify the
organizer access-control and conference-option configuration mechanisms during
specification.

## Specification gate

Write `docs/specification.md` after acceptance criteria and before code. It
must define component responsibilities, API, data model/migrations,
configuration, persistence/backup, email, validation, security controls,
export, error behavior, containers, and traceability to ACs. No production
code or feature tests at this gate.
