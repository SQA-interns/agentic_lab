# Frozen technology stack

| ID | Required choice |
|---|---|
| ST-01 | Backend: Java 21; Spring Boot 3.x; Maven Wrapper; Spring Web, Data JPA/Hibernate, Bean Validation, Mail, Actuator; Spring Security for organizer access and HTTP controls. |
| ST-02 | Database: PostgreSQL 16 and Flyway migrations. |
| ST-03 | Frontend: React 19, TypeScript, Vite 6, npm and a lockfile; Node 22 for build/test. |
| ST-04 | Local email: isolated SMTP catcher; production email: externally configured SMTP. |
| ST-05 | Anti-automation: Google reCAPTCHA v2 client flow with server-side token verification; deterministic local/test mode and real production mode. Never call Google from automated tests. |
| ST-06 | Tests: JUnit 5, Mockito, Spring Boot Test, MockMvc or WebTestClient, PostgreSQL Testcontainers, JaCoCo; Vitest, React Testing Library, Playwright. |
| ST-07 | Static/architecture checks: Spotless, SpotBugs, PMD/CPD, ArchUnit; ESLint/typescript-eslint, Prettier, `tsc --noEmit`, jscpd. |
| ST-08 | Security checks: Semgrep, OWASP Dependency-Check for backend, `npm audit` for frontend. |
| ST-09 | Containers: frontend, backend, PostgreSQL; Docker Compose for local orchestration. Use immutable image digests or explicitly recorded image tags. |

There is no scaffold. Create the build and runtime files under
`02_Implementation` and pin all direct dependency and plugin versions;
create a frontend lockfile. Choose compatible releases within stated ranges;
record exact resolved versions, including inherited Maven dependencies and
wrapper/tool versions. Pin one baseline before feature work; later within-range
changes need a recorded reason and fresh affected checks. The Markdown owns
allowed choices; manifests/lockfiles record their exact realization and cannot
override these constraints. Keep the required stack; do not silently move to
Spring Boot 4 or Node 24 because a local generator/default suggests it.
Inspect dependency resolution and security advisories before asserting a
clean scan. A fixed dependency version may carry an unresolved advisory:
report it as an unmet gate. Do not change this input or replace a required
scanner with another one without a new experimental condition.

Local/test reCAPTCHA mode requires an explicit non-production profile and
must fail closed when production configuration is missing or invalid. Do not
ship development SMTP credentials or test bypass in production mode.
