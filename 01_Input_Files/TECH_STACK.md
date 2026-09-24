# Tech Stack (frozen for this experiment)

> **DRAFT — review before freezing.** Backend and frontend framework are
> now decided (Spring Boot, React); everything else below is a reasoned
> default built around that choice. Change anything, then treat the file
> as immutable for the run.

## Language / runtime

Java 21 LTS (backend) · Node.js 22 LTS as the frontend build toolchain
only, not a runtime dependency of the shipped app.

## Backend framework

Spring Boot 3.x, bootstrapped via Spring Initializr. Starters:

* `spring-boot-starter-web` — REST API
* `spring-boot-starter-data-jpa` — persistence
* `spring-boot-starter-validation` — request validation (Bean Validation)
* `spring-boot-starter-security` — backend security controls, anti-bot/
  rate-limiting support
* `spring-boot-starter-mail` — participant/organizer email
* `spring-boot-starter-actuator` — health/readiness endpoints for the
  container-execution check in `DEFINITION_OF_DONE.md`
* Apache POI (`poi-ooxml`) — Excel export

Build tool: Maven (`mvnw` wrapper committed to the repo, so no local
Maven install is required).

## Database

PostgreSQL 16, accessed through Spring Data JPA/Hibernate. Migrations
managed with Flyway (or Liquibase — pick one, don't leave schema
evolution unmanaged).

## Frontend

React 18+, built with Vite. TypeScript, to match the backend's typed
nature and keep the two codebases comparably strict. No UI component
library mandated — plain CSS or a lightweight utility framework is
fine; do not pull in a heavy design system for a PoC.

Frontend and backend are separate deployables communicating over the
REST API defined in `PROJECT_CONSTRAINTS.md` — not server-rendered,
not bundled into the Spring Boot jar. Keeps the "frontend/backend
through REST" constraint unambiguous and keeps each side's tests
scoped to one language.

## Testing

**Backend:** JUnit 5 + Mockito for unit tests; Spring Boot Test
(`MockMvc` or `WebTestClient`) for API/contract tests; **Testcontainers**
for integration tests that run against a real containerized PostgreSQL
rather than an in-memory substitute — required, since an H2-backed
"integration" test would not exercise the same persistence behavior as
production and would undermine the container-execution check.

**Frontend:** Vitest + React Testing Library for component/unit tests.
No end-to-end browser framework is mandated; add Playwright only if
`DEFINITION_OF_DONE.md`'s container-execution check needs browser-level
verification.

## Static analysis (mandatory — see DEFINITION_OF_DONE.md)

**Backend:**
* Lint/format: Spotless (with Google Java Format or equivalent)
* Bug pattern analysis: SpotBugs
* Security scan: Semgrep, plus OWASP Dependency-Check (Maven plugin) as
  the dependency-audit gate
* Duplication: PMD CPD
* Architecture conformance: **ArchUnit** — write the layering rules
  (e.g. controller → service → repository, no reverse dependencies) as
  executable tests. This replaces dependency-cruiser for this stack and
  is what produces the architecture/layer-violation metrics.

**Frontend:**
* Lint: ESLint + `typescript-eslint`, strict config
* Format: Prettier
* Type check: `tsc --noEmit`
* Duplication: jscpd

## Deployment target

Two containers, composed via `docker-compose.yml`:

* **backend** — multi-stage Docker build (Maven build stage →
  `eclipse-temurin:21-jre-alpine` runtime stage), non-root user.
* **frontend** — multi-stage Docker build (Vite build stage → static
  output served by Nginx), non-root user.
* **db** — official `postgres:16` image, named volume for persistence.

The built containers must actually be run as part of verification —
see `DEFINITION_OF_DONE.md`.

## Explicitly not decided by this file

Internal package/module structure within the Spring Boot app (layered,
hexagonal/ports-and-adapters, or otherwise), and the React app's
internal component/state structure, remain the agent's decision. That
is what the experiment measures — freezing it here would defeat the
point.