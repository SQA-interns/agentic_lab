# Preflight report

> Written in: phase 0 · Source: `project/00_setup/*` · Procedure: `general/phases/0-preflight.md` · Agent: writes

| Check | Source of truth | Result |
|---|---|---|
| Run configuration complete | `project/00_setup/run-config.md` | pass: model, effort, template version, run id present (read) |
| Platforms and tools installed at the listed versions | `project/00_setup/tech-stack.md` | pass after re-check: node v24.13.0 / npm 11.6.2 under nvm (D-09 follow-up; first search was faulty). java, docker, compose run with other versions (D-05..D-07). Maven 3.9.9 via wrapper 3.3.2, all Maven plugins run (`verify.sh` backend tools). semgrep 1.177.0, gitleaks v8.30.1 run; cloc tag 2.10 reports 1.98 (D-08). npm-based tools run through `verify.sh` (vite, tsc, eslint, prettier, vitest; others run in later phases). Log: `out/logs/0_platforms.log`, `0_container-tools.log` |
| Local environments and services running or reachable | `project/00_setup/environments.md` | pass: Docker Engine running (`docker run`), Docker Hub, Maven Central, npm registry reachable (HTTP); compose stack is built in later phases |
| Secrets present in `.env` or marked test-only | `project/00_setup/secrets.env.example` | pass: POSTGRES_PASSWORD, ORGANIZER_USERNAME, ORGANIZER_PASSWORD (≥ 16 chars), ORGANIZER_EMAILS, NVD_API_KEY non-empty (`sed … \| grep -q .`, then `grep -c "^KEY=..*"`); RECAPTCHA_* test-mode only, SMTP_* not needed |
| Every listed dependency resolves | `project/00_setup/tech-stack.md` | pass: 61/61 entries HTTP 200 (Maven Central POM, npm registry version document, `docker manifest inspect`), plus Maven 3.9.9 and pitest-junit5-plugin 1.2.3; backend resolved by `./mvnw` at exact versions (`out/logs/0_backend-deps.txt`) |
| No listed dependency has a known Critical or High vulnerability | `project/00_setup/tech-stack.md` | backend: pass (Dependency-Check 12.1.0, NVD analyser; 1 High + 1 Medium false positives suppressed, D-03, D-04; OSS Index disabled, D-02; raw `out/logs/0_dependency-check-raw.json`). npm set: pass after D-11, D-12 (approved pin changes): `npm audit` Critical 0, High 0, Moderate 2 (D-13); raw first audit superseded, current `out/logs/0_npm-audit.json` |
| Clean working tree on the starting commit | repository | pass: `git status` clean apart from `run-log.json` at HEAD b6c9dc6 (detached; branch created, D-10) |
| Input manifest written | `docs/00_input-manifest.sha256` | pass: 27 files, LF-normalised SHA-256 |

## Component commands (ES-05)

| Component | build | test | check | run |
|---|---|---|---|---|
| backend | `./mvnw -B package -DskipTests` | `./mvnw -B verify` | `./mvnw -B spotless:check pmd:check pmd:cpd-check spotbugs:check` | `./mvnw spring-boot:run` (needs the database settings) |
| frontend | `npm run build` | `npm test` | `npm run check` | `npm start` |

## Dependency results

| id | version | Resolves | Highest vulnerability |
|---|---|---|---|
| `org.springframework.boot:spring-boot-starter-parent` | 4.1.1 | yes (HTTP 200) | none |
| `org.springframework.boot:spring-boot-starter-webmvc` | 4.1.1 | yes (HTTP 200) | none |
| `org.springframework.boot:spring-boot-starter-data-jpa` | 4.1.1 | yes (HTTP 200) | none |
| `org.springframework.boot:spring-boot-starter-validation` | 4.1.1 | yes (HTTP 200) | none (transitive: 2 false positives, D-03, D-04) |
| `org.springframework.boot:spring-boot-starter-mail` | 4.1.1 | yes (HTTP 200) | none (transitive: 2 false positives, D-03, D-04) |
| `org.springframework.boot:spring-boot-starter-actuator` | 4.1.1 | yes (HTTP 200) | none |
| `org.springframework.boot:spring-boot-starter-security` | 4.1.1 | yes (HTTP 200) | none |
| `org.springframework.boot:spring-boot-starter-flyway` | 4.1.1 | yes (HTTP 200) | none |
| `org.flywaydb:flyway-core` | 12.4.0 | yes (HTTP 200) | none |
| `org.flywaydb:flyway-database-postgresql` | 12.4.0 | yes (HTTP 200) | none |
| `org.postgresql:postgresql` | 42.7.13 | yes (HTTP 200) | none |
| `org.apache.poi:poi-ooxml` | 5.5.1 | yes (HTTP 200) | none |
| `org.apache.tomcat.embed:tomcat-embed-core` | 11.0.26 | yes (HTTP 200) | none |
| `org.apache.logging.log4j:log4j-api` | 2.26.1 | yes (HTTP 200) | none |
| `org.apache.commons:commons-lang3` | 3.20.0 | yes (HTTP 200) | none |
| `org.springframework.boot:spring-boot-starter-test` | 4.1.1 | yes (HTTP 200) | none |
| `org.springframework.boot:spring-boot-starter-webmvc-test` | 4.1.1 | yes (HTTP 200) | none |
| `org.testcontainers:testcontainers-junit-jupiter` | 2.0.5 | yes (HTTP 200) | none |
| `org.testcontainers:testcontainers-postgresql` | 2.0.5 | yes (HTTP 200) | none |
| `com.tngtech.archunit:archunit-junit5` | 1.3.2 | yes (HTTP 200) | none |
| `react` | 19.3.0 | yes (HTTP 200) | none |
| `react-dom` | 19.3.0 | yes (HTTP 200) | none |
| `@types/react` | 19.3.0 | yes (HTTP 200) | none |
| `@types/react-dom` | 19.3.0 | yes (HTTP 200) | none |
| `@vitejs/plugin-react` | 4.7.0 | yes (HTTP 200) | none |
| `@testing-library/react` | 16.3.3 | yes (HTTP 200) | none |
| `@testing-library/jest-dom` | 6.9.1 | yes (HTTP 200) | none |
| `jsdom` | 26.1.0 | yes (HTTP 200) | none |
| `globals` | 15.15.0 | yes (HTTP 200) | none |
| `postgres` | 16.15-alpine | yes (HTTP 200) | not scanned (images scanned in phase 6) |
| `eclipse-temurin` | 21.0.10_7-jre-alpine | yes (HTTP 200) | not scanned (images scanned in phase 6) |
| `node` | 24.13.0-alpine | yes (HTTP 200) | not scanned (images scanned in phase 6) |
| `nginx` | 1.30.5-alpine | yes (HTTP 200) | not scanned (images scanned in phase 6) |
| `axllent/mailpit` | v1.31.1 | yes (HTTP 200) | not scanned (images scanned in phase 6) |
| `maven-wrapper` | 3.3.2 | yes (HTTP 200) | n/a (tool) |
| `org.springframework.boot:spring-boot-maven-plugin` | 4.1.1 | yes (HTTP 200) | n/a (tool) |
| `com.diffplug.spotless:spotless-maven-plugin` | 2.44.3 | yes (HTTP 200) | n/a (tool) |
| `com.github.spotbugs:spotbugs-maven-plugin` | 4.10.4.1 | yes (HTTP 200) | n/a (tool) |
| `org.apache.maven.plugins:maven-pmd-plugin` | 3.26.0 | yes (HTTP 200) | n/a (tool) |
| `org.jacoco:jacoco-maven-plugin` | 0.8.12 | yes (HTTP 200) | n/a (tool) |
| `org.pitest:pitest-maven` | 1.30.0 | yes (HTTP 200) | n/a (tool) |
| `org.owasp:dependency-check-maven` | 12.1.0 | yes (HTTP 200) | n/a (tool) |
| `vite` | 6.4.3 | yes (HTTP 200) | none |
| `typescript` | 5.9.3 | yes (HTTP 200) | none |
| `eslint` | 9.39.5 | yes (HTTP 200) | none |
| `@eslint/js` | 9.39.5 | yes (HTTP 200) | none |
| `typescript-eslint` | 8.70.1 | yes (HTTP 200) | none |
| `eslint-plugin-react-hooks` | 5.2.0 | yes (HTTP 200) | none |
| `eslint-plugin-react-refresh` | 0.4.26 | yes (HTTP 200) | none |
| `prettier` | 3.9.9 | yes (HTTP 200) | none |
| `vitest` | 3.2.7 → 4.1.11 (approved D-11) | yes (HTTP 200) | none after change |
| `@vitest/coverage-v8` | 3.2.7 → 4.1.11 (approved D-11) | yes (HTTP 200) | none after change |
| `@playwright/test` | 1.63.0 | yes (HTTP 200) | none |
| `@stryker-mutator/core` | 10.0.0 | yes (HTTP 200) | Moderate, transitive qs (D-13) |
| `@stryker-mutator/vitest-runner` | 10.0.0 | yes (HTTP 200) | none |
| `jscpd` | 4.3.0 → 5.4.0 (approved D-12) | yes (HTTP 200) | none after change |
| `npm audit (npm 11.6.2)` | 11.6.2 | yes (HTTP 200) | none |
| `semgrep/semgrep` | 1.177.0 | yes (HTTP 200) | not scanned (images scanned in phase 6) |
| `zricethezav/gitleaks` | v8.30.1 | yes (HTTP 200) | not scanned (images scanned in phase 6) |
| `aldanial/cloc` | 2.10 | yes (HTTP 200) | not scanned (images scanned in phase 6) |
| `org.pitest:pitest-junit5-plugin` | 1.2.3 | yes (HTTP 200) | n/a (tool) |

## Asked the human

| When | What was missing | Answer | Re-check |
|---|---|---|---|
| 2026-10-09T10:54:33Z | Node.js 24.13.0 with npm 11.6.2 on the host (D-09) | 2026-10-09T10:58:38Z: already installed, defaults | pass (nvm v24.13.0); frontend skeleton built; npm audit raised D-11, D-12 |
| 2026-10-09T11:01:38Z | Approval to change vitest/@vitest/coverage-v8 (D-11) and jscpd (D-12) pins | 2026-10-09T11:07:30Z: defaults | pass: pins changed, audit 0 Critical/High |
