# Preflight report

> Written in: phase 0 · Source: `project/00_setup/run-config.md`, `project/stack.md`, `project/secrets.env.example` · Procedure: `skills/preflight` · Agent: writes

| Check | Source of truth | Result |
|---|---|---|
| Run configuration complete | `project/00_setup/run-config.md` | pass: model, effort, template version, run id present; starting commit `20c7202` |
| Platforms and tools installed at the listed versions | `project/stack.md` | pass with D-01 (JDK 21.0.11, `java -version`) and D-02 (Node.js 24.10.0 / npm 10.9.4, `node --version`, `npm --version`); Docker 29.8.0 (`docker version`), Compose 5.5.1 (`docker compose version`); Maven tools resolved and run at their pins (`mvnw dependency:resolve-plugins`, verify.sh); npm tools at their pins (`npm ls --depth=0`); container tools pulled at their tags (`02_output/logs/00_image-pull.log`) |
| Local environments and services running or reachable | `project/stack.md` ("Environments") | pass: Docker daemon reachable; local stack and test containers are started from pinned images, all pulled |
| Secrets present in `.env` or marked test-only | `project/secrets.env.example` | pass: `secrets.sh check`; every required key present; RECAPTCHA keys test-mode only (empty) |
| Every listed dependency resolves | `project/stack.md` | pass: Maven `dependency:list` (`02_output/logs/00_backend-deplist.txt`), `npm install` + `npm ls`, `docker pull` |
| No listed dependency has a known Critical or High vulnerability | `project/stack.md` | pass after D-03: High CVE-2025-7962 on angus-activation 2.0.3 classified false positive (Low) by the human and suppressed for that artifact only; re-scan Critical 0, High 0, Medium 1 (`02_output/logs/0_be-depscan.log`, raw: `0_be-depscan-raw-report.json`); npm audit: 0 Critical, 0 High, 5 Medium (`02_output/logs/0_fe-depscan.log`) |
| Clean working tree on the starting commit | repository | pass: `git status --porcelain` empty apart from `run-log.json`; HEAD = starting commit |
| Input manifest written | `docs/00_input-manifest.sha256` | pass: 26 files, LF-normalised SHA-256 |

## Dependency results

Scanners: OWASP Dependency-Check (NVD; OSS Index disabled per `stack.md` options) for Maven; `npm audit` for npm. Container images are not scanned by the listed tools.

| id | version | Resolves | Highest vulnerability |
|---|---|---|---|
| org.springframework.boot:spring-boot-starter-parent | 4.1.1 | yes | none |
| org.springframework.boot:spring-boot-starter-webmvc | 4.1.1 | yes | none |
| org.springframework.boot:spring-boot-starter-data-jpa | 4.1.1 | yes | none |
| org.springframework.boot:spring-boot-starter-validation | 4.1.1 | yes | Medium: CVE-2025-15104 on transitive hibernate-validator 9.1.3.Final (product-name mismatch, Nu Html Checker) |
| org.springframework.boot:spring-boot-starter-mail | 4.1.1 | yes | Low (was High): CVE-2025-7962 on transitive angus-activation 2.0.3, false positive, suppressed (D-03) |
| org.springframework.boot:spring-boot-starter-actuator | 4.1.1 | yes | none |
| org.springframework.boot:spring-boot-starter-security | 4.1.1 | yes | none |
| org.springframework.boot:spring-boot-starter-flyway | 4.1.1 | yes | none |
| org.flywaydb:flyway-core | 12.4.0 | yes | none |
| org.flywaydb:flyway-database-postgresql | 12.4.0 | yes | none |
| org.postgresql:postgresql | 42.7.13 | yes | none |
| org.apache.poi:poi-ooxml | 5.5.1 | yes | none |
| org.apache.tomcat.embed:tomcat-embed-core | 11.0.26 | yes | none |
| org.apache.logging.log4j:log4j-api | 2.26.1 | yes | none |
| org.apache.commons:commons-lang3 | 3.20.0 | yes | none |
| org.springframework.boot:spring-boot-starter-test | 4.1.1 | yes | none |
| org.springframework.boot:spring-boot-starter-webmvc-test | 4.1.1 | yes | none |
| org.testcontainers:testcontainers-junit-jupiter | 2.0.5 | yes | none |
| org.testcontainers:testcontainers-postgresql | 2.0.5 | yes | none |
| com.tngtech.archunit:archunit-junit5 | 1.3.2 | yes | none |
| react, react-dom | 19.3.0 | yes | none |
| @types/react, @types/react-dom | 19.3.0 | yes | none |
| @vitejs/plugin-react | 4.7.0 | yes | none |
| @testing-library/react | 16.3.3 | yes | none |
| @testing-library/jest-dom | 6.9.1 | yes | none |
| jsdom | 26.1.0 | yes | none |
| globals | 15.15.0 | yes | none |
| vitest, @vitest/coverage-v8 (tooling) | 3.2.7 | yes | Medium: GHSA path traversal in transitive @vitest/mocker (test-only, dev server) |
| jscpd (tooling) | 5.4.0 | yes | Medium: qs DoS via transitive typed-rest-client (dev-only) |
| postgres | 16.15-alpine | yes | not scanned |
| eclipse-temurin | 21.0.10_7-jre-alpine | yes | not scanned |
| node | 24.13.0-alpine | yes | not scanned |
| nginx | 1.30.5-alpine | yes | not scanned |
| axllent/mailpit | v1.31.1 | yes | not scanned |

## Asked the human

| When | What was missing | Answer | Re-check |
|---|---|---|---|
| 2026-10-05T20:26:21Z | D-03: approval to classify High CVE-2025-7962 on angus-activation 2.0.3 as a false positive | 2026-10-05T20:34:17Z: option 1, suppress for that artifact only, keep raw report | `verify.sh 0 be-depscan`: Critical 0, High 0, exit 0 |
