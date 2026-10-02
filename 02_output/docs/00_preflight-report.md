# Preflight report

> Written in: phase 0 · Source: `project/00_setup/*` · Procedure: `general/phases/0-preflight.md` · Agent: writes

| Check | Source of truth | Result |
|---|---|---|
| Run configuration complete | `project/00_setup/run-config.md` | Pass: model, effort, template version, run id present (file read) |
| Platforms and tools installed at the listed versions | `project/00_setup/tech-stack.md` | Pass with decisions: see "Platform results" (D-01, D-02, D-03) |
| Local environments and services running or reachable | `project/00_setup/environments.md` | Pass: Docker daemon up (`docker info`, linux/x86_64); ports 80, 1025, 5173, 5432, 8025, 8080 free (`netstat`); Maven Central, npm registry and Docker Hub reachable (62 requests) |
| Secrets present in `.env` or marked test-only | `project/00_setup/secrets.env.example` | Pass: `POSTGRES_PASSWORD`, `ORGANIZER_USERNAME`, `ORGANIZER_PASSWORD`, `ORGANIZER_EMAILS`, `NVD_API_KEY` non-empty (card command, presence only); reCAPTCHA keys test-mode only, SMTP keys not needed |
| Every listed dependency resolves | `project/00_setup/tech-stack.md` | Pass: 62 of 62 (`logs/0_resolve.log`); backend and frontend also resolve them in a real build |
| No listed dependency has a known Critical or High vulnerability | `project/00_setup/tech-stack.md` | Frontend pass: `npm audit` 0 Critical, 0 High, 5 Moderate (`logs/0_frontend-audit.log`). Backend pass: Dependency-Check 0 findings after two false positives were suppressed (D-07), OSS Index analyser disabled (D-06); raw output in `logs/0_backend-depscan-raw.log` |
| Clean working tree on the starting commit | repository | Pass: `git status --short` showed only untracked `03_statistics/run-log.json`; HEAD `5375170` on `tanej/single-agent/sdd/v001` |
| Input manifest written | `docs/00_input-manifest.sha256` | Pass: 25 files (20 under `01_input/`, 5 protected), LF-normalised |

## Platform results

| id | Pinned | Reported | Method | Result |
|---|---|---|---|---|
| java | Temurin 21.0.10+7 | Oracle 21.0.11+9-LTS | `java -version`; `java -XshowSettings:properties` | runs, other version: D-01 |
| node | 24.13.0 | 24.10.0 | `node --version`; `where node` (one installation) | runs, other version: D-02 |
| npm | 11.6.2 | 10.9.4 | `npm --version` | runs, other version: D-03 |
| docker | 29.8.0 | client 29.8.0, server 29.8.0 | `docker version` | match |
| compose | 5.5.1 | v5.5.1 | `docker compose version` | match |
| maven-wrapper | 3.3.2 (Maven 3.9.9) | Apache Maven 3.9.9 | `./mvnw -v` | match |

## Tool results (`out/scripts/verify.sh 0`)

| Tool | Exit | Key result | Log |
|---|---|---|---|
| backend-build | 0 | BUILD SUCCESS | `logs/0_backend-build.log` |
| backend-check (spotless, pmd, spotbugs) | 0 | BUILD SUCCESS | `logs/0_backend-check.log` |
| backend-test (surefire, jacoco) | 0 | no tests yet | `logs/0_backend-test.log` |
| backend-deps | 0 | 163 artifacts; every pinned version matches | `logs/0_backend-deps.log` |
| backend-depscan (dependency-check) | 0 | BUILD SUCCESS, 67 dependencies scanned, 0 findings (D-06, D-07) | `logs/0_backend-depscan.log` |
| frontend-build (tsc, vite) | 0 | built | `logs/0_frontend-build.log` |
| frontend-check (prettier, eslint, tsc) | 0 | clean | `logs/0_frontend-check.log` |
| frontend-test (vitest, coverage-v8) | 0 | no tests yet | `logs/0_frontend-test.log` |
| frontend-audit (npm audit) | 0 | 5 Moderate | `logs/0_frontend-audit.log` |
| semgrep | 0 | 0 findings, 348 rules | `logs/0_semgrep.log` |
| gitleaks | 0 | 0 leaks on this branch (D-04) | `logs/0_gitleaks.log` |
| cloc | 0 | runs | `logs/0_cloc.log` |

Not yet runnable (no tests or sources to run on; added to `verify.sh` when they are): pitest, Stryker, jscpd, PMD CPD, Playwright (its Chromium download is requested when phase 3 needs it).

## Dependency results

Resolution: HTTP 200 for the exact version at the listed source (Maven Central POM, npm registry version document, `docker manifest inspect`). Vulnerability column: frontend from `npm audit`; backend from Dependency-Check (NVD).

| id | version | Resolves | Highest vulnerability |
|---|---|---|---|
| org.springframework.boot:spring-boot-starter-parent, -webmvc, -data-jpa, -validation, -mail, -actuator, -security, -flyway, -test, -webmvc-test, spring-boot-maven-plugin | 4.1.1 | yes | none |
| org.flywaydb:flyway-core, flyway-database-postgresql | 12.4.0 | yes | none |
| org.postgresql:postgresql | 42.7.13 | yes | none |
| org.apache.poi:poi-ooxml | 5.5.1 | yes | none |
| org.apache.tomcat.embed:tomcat-embed-core | 11.0.26 | yes | none |
| org.apache.logging.log4j:log4j-api | 2.26.1 | yes | none |
| org.apache.commons:commons-lang3 | 3.20.0 | yes | none |
| org.testcontainers:testcontainers-junit-jupiter, testcontainers-postgresql | 2.0.5 | yes | none |
| com.tngtech.archunit:archunit-junit5 | 1.3.2 | yes | none |
| Maven tooling: maven-wrapper 3.3.2, apache-maven 3.9.9, spotless 2.44.3, spotbugs 4.10.4.1, pmd 3.26.0, jacoco 0.8.12, pitest 1.30.0, pitest-junit5 1.2.3, dependency-check 12.1.0 | as pinned | yes | not shipped |
| react, react-dom, @types/react, @types/react-dom | 19.3.0 | yes | none |
| @vitejs/plugin-react | 4.7.0 | yes | none |
| @testing-library/react 16.3.3, @testing-library/jest-dom 6.9.1, jsdom 26.1.0, globals 15.15.0 | as pinned | yes | none |
| npm tooling: vite 6.4.3, typescript 5.9.3, eslint and @eslint/js 9.39.5, typescript-eslint 8.70.1, eslint-plugin-react-hooks 5.2.0, eslint-plugin-react-refresh 0.4.26, prettier 3.9.9, @playwright/test 1.63.0, @stryker-mutator/core and vitest-runner 10.0.0, jscpd 4.3.0 | as pinned | yes | Moderate (transitive `qs`), dev only |
| vitest, @vitest/coverage-v8 | 3.2.7 | yes | Moderate (transitive `@vitest/mocker`), dev only |
| postgres 16.15-alpine, eclipse-temurin 21.0.10_7-jre-alpine, node 24.13.0-alpine, nginx 1.30.5-alpine, axllent/mailpit v1.31.1 | as pinned | yes | not scanned by a listed tool |
| semgrep/semgrep 1.177.0, zricethezav/gitleaks v8.30.1, aldanial/cloc 2.10 | as pinned | yes, and run | not shipped |

## Asked the human

| When | What was missing | Answer | Re-check |
|---|---|---|---|
| 2026-10-02T10:23:22Z | D-05: permission to run the `.env` presence check and the NVD dependency scan | 2026-10-02T10:37:13Z: option 1 | check 4 pass; scan pass (D-06, D-07) |
