# Implementation phase log (Phase 3)

* Start: 2026-09-24T19:56:34+02:00 (after spec commit `ceb74c9`)
* End: 2026-09-24T21:01:24+02:00 (implementation commit `0ea12e9`)
* First working primary happy path: **2026-09-24T20:58:43+02:00** — external registration
  POSTed through the Nginx container to the running backend container; observed as backup file
  `registration-20260924T185843Z-10ec7820-….json` and two Mailpit messages. The PowerShell client
  printed an `Invoke-WebRequest` NonInteractive error *after* the request had been sent (missing
  `-UseBasicParsing`), so the first client-side confirmed 201 was at 20:59:14+02:00
  (id `11c14902-…`). The server-side timestamp is used as the first-happy-path value.

## Files created

All files listed by `git show --name-only 0ea12e9` (82 files, 8 375 inserted lines incl.
`package-lock.json`). Groups:

* `02_Implementation/backend/**` — Maven project (Initializr scaffold: `mvnw`, `mvnw.cmd`,
  `.mvn/wrapper`, `.gitattributes`, `.gitignore`), 35 Java sources, `application.yml`,
  `conference-options.json`, Flyway `V1__init.sql`, `Dockerfile`, static-analysis configs.
* `02_Implementation/frontend/**` — Vite/React/TS app (13 source files), ESLint/Prettier/jscpd
  config, Nginx config, `Dockerfile`.
* `02_Implementation/docker-compose.yml`, `docker-compose.verify.yml`, `.env.example`,
  `config/conference-options.json`, `.gitattributes`, `.gitignore`.

## Files modified

* Initializr-generated `backend/pom.xml` — rewritten (Boot 3.5.16 parent, starters, plugins).
* Initializr-generated `RegistrationApplication.java` — added `@ConfigurationPropertiesScan`.
* Deleted from scaffold: `HELP.md`, `application.properties`, `static/`, `templates/`, generated
  test sources (tests belong to Phase 4).
* Nothing outside `02_Implementation/` and `03_Run-Statistics/` was touched.

## Dependencies added

Backend runtime: spring-boot-starter-web, -data-jpa, -validation, -security, -mail, -actuator
(Boot 3.5.16 managed), flyway-core, flyway-database-postgresql, postgresql (runtime),
poi-ooxml 5.5.1, spotbugs-annotations 4.10.4 (provided).
Backend test (declared now, used in Phase 4): spring-boot-starter-test, spring-security-test,
spring-boot-testcontainers, testcontainers junit-jupiter + postgresql, greenmail-junit5 2.1.14,
archunit-junit5 1.5.0.
Backend build plugins: jacoco 0.8.15, spotless 3.10.2 (google-java-format), spotbugs 4.10.4.1 +
findsecbugs 1.14.0, maven-pmd-plugin 3.28.0 (CPD + complexity), dependency-check-maven 13.0.0.

Frontend runtime: react 19.3.0, react-dom 19.3.0.
Frontend dev: vite 8.3.1, @vitejs/plugin-react 6.1.1, typescript 6.0.3, eslint 10.11.0,
@eslint/js, typescript-eslint 8.70.1, eslint-plugin-react-hooks 7.1.1, globals, prettier 3.9.9,
jscpd 5.3.2, vitest 5.0.1, @vitest/coverage-v8, jsdom, @testing-library/{react,dom,user-event,jest-dom}.

Container images: postgres:16, eclipse-temurin:21-jdk-alpine / 21-jre-alpine, node:22-alpine,
nginxinc/nginx-unprivileged:1.27-alpine, axllent/mailpit:v1.27 (verification overlay only).

## Implementation decisions

1. **Spring Boot 3.5.16 instead of Initializr default 4.1.1.** start.spring.io no longer offers
   3.x; TECH_STACK mandates 3.x. The project was bootstrapped via Initializr (wrapper, layout)
   and the parent pinned to the latest 3.5.x from Maven Central. Deviation logged.
2. Layered packages `api → service → repository → domain`, `config` cross-cutting; the rate-limit
   filter is wired into the security chain by bean name to avoid a `config → api` class dependency.
3. Rate limiting inside the Spring Security chain after `HeaderWriterFilter` so 429/413 responses
   carry security headers (lesson from the prior run's findings). Atomic `ConcurrentHashMap.compute`.
4. Anti-automation = honeypot + HMAC-signed single-use form token with minimum fill time + rate
   limit. Token consumed atomically (`putIfAbsent`) only on successful registration.
5. Backup written inside the DB transaction; failure rolls back; rollback after write deletes the
   file (TransactionSynchronization). Atomic temp-file + rename, `0600` permissions.
6. Emails via `@TransactionalEventListener(AFTER_COMMIT)` + `@Async`: SMTP failure never affects a
   stored registration; mail health indicator disabled so SMTP outage does not mark the app DOWN.
7. Unknown JSON properties rejected, so the two variants' payloads are not interchangeable.
8. Organizer export via HTTP Basic (single account from env, BCrypt in memory); no admin UI (out of
   scope per US-003/US-008).
9. Frontend without router/UI libraries; path-based switch; Nginx proxies `/api` (same origin → no
   CORS); frontend security headers via an included snippet (avoids `add_header` inheritance loss).
10. Mailpit only in `docker-compose.verify.yml` so that production compose matches TECH_STACK's
    three services.
