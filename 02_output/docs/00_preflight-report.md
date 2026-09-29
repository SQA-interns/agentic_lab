# Preflight report

> Written in: phase 0 · Source: `project/01_setup/*`, `project/03_technical/*` · Procedure: `general/skills/preflight` · Agent: writes

| Check | Source of truth | Result |
|---|---|---|
| Run configuration complete | `project/01_setup/run-config.md` | Pass: workflow `sdd` exists; model, effort, template version 1.0, run id present |
| Platforms and tools installed at the listed versions | `project/03_technical/tech-stack.md` | Pass: Temurin 21.0.10+7, Node 24.13.0, npm 11.6.2, Docker Engine 29.8.0, Compose v5.5.1. Maven is not on the host by design: `mvnw` 3.3.2 (only-script) downloads Apache Maven 3.9.9. Every Maven plugin, npm tool and container tool ran at its listed version (`out/logs/phase0-backend-tools.log`). The `aldanial/cloc:2.10` image contains cloc 1.98 (D-03) |
| Local environments and services running or reachable | `project/03_technical/environments.md` | Pass: Docker Desktop running; Docker Hub and Maven Central reachable; postgres, eclipse-temurin, node, nginx and mailpit images pulled; Playwright Chromium v1243 already present in the user cache (matches 1.63.0) |
| Secrets present in `.env` or marked test-only | `project/01_setup/secrets.env.example` | Pass: `NVD_API_KEY` set (D-01 was a false alarm from the check skipping the last line of `.env`; D-04). `POSTGRES_PASSWORD`, `ORGANIZER_USERNAME`, `ORGANIZER_PASSWORD` (≥ 16 chars), `ORGANIZER_EMAILS` set. `RECAPTCHA_*` empty (test-mode only locally, allowed). `SMTP_*` empty (not needed locally, allowed) |
| Every listed dependency resolves | `project/03_technical/tech-stack.md` | Pass: every Maven coordinate of the updated stack (HTTP 200 on Maven Central; resolved versions in `out/logs/phase0-backend-dependency-tree.log`), 23 npm packages (`npm view`), 8 container tags (`docker manifest inspect`) |
| No listed dependency has a known Critical or High vulnerability | `project/03_technical/tech-stack.md` | Frontend pass: `npm audit` 0 critical, 0 high, 5 moderate (`out/logs/phase0-npm-audit.json`). Backend pass after D-05/D-08: first scan of the original set found 7 Critical + 9 High (spring 6.2.19, spring-security 6.5.11, postgresql 42.7.11); after the human moved the stack to Spring Boot 4.1.1 + Tomcat 11.0.26, Dependency-Check (NVD) reports 0 Critical, 0 High, 2 Medium over 67 dependencies (`out/logs/phase0-dependency-check-report.json`); OSS Index analyzer disabled (D-06) |
| Clean working tree on the starting commit | repository | Pass: HEAD `8b50617`; only untracked `.env` (now excluded locally, D-02) |
| Input manifest written | `docs/00_input-manifest.sha256` | Pass: 32 files, LF-normalised; re-written after the human updated `tech-stack.md` (D-08), still in phase 0 |

## Dependency results

Licences: all npm packages report MIT or Apache-2.0; Maven licences as declared in `tech-stack.md`, re-checked in phase 6.

| id | version | Resolves | Highest vulnerability |
|---|---|---|---|
| spring-core, spring-web (Boot 4.1.1-managed) | 7.0.9 | yes | none |
| spring-security-core, spring-security-web (Boot 4.1.1-managed) | 7.1.1 | yes | none |
| org.postgresql:postgresql | 42.7.13 | yes | none |
| org.apache.tomcat.embed:tomcat-embed-core | 11.0.26 | yes | none |
| flyway-core, flyway-database-postgresql | 12.4.0 | yes | none |
| log4j-api 2.26.1, commons-lang3 3.20.0, poi-ooxml 5.5.1 | as listed | yes | none |
| angus-activation 2.0.3 (via starter-mail) | managed | yes | Medium (CVE-2025-7962) |
| hibernate-validator 9.1.3.Final (via starter-validation) | managed | yes | Medium (CVE-2025-15104) |
| Maven build plugins (spring-boot, spotless, spotbugs, pmd, jacoco, pitest + junit5 plugin, dependency-check, wrapper, Maven 3.9.9) | as listed | yes | not scanned (build-time tooling) |
| react, react-dom | 19.3.0 | yes | none |
| vitest, @vitest/coverage-v8 | 3.2.7 | yes | Moderate (GHSA: @vitest/mocker redirect-mock path traversal; test-time only) |
| @stryker-mutator/core (via typed-rest-client → qs) | 10.0.0 | yes | Moderate (qs DoS; build-time only) |
| other npm dev tools (types, vite, typescript, eslint family, prettier, testing-library, jsdom, globals, playwright, jscpd) | as listed | yes | none |
| postgres, eclipse-temurin, node, nginx, axllent/mailpit, semgrep, gitleaks, cloc images | as listed | yes | not scanned by a listed tool |

## Asked the human

| When | What was missing | Answer | Re-check |
|---|---|---|---|
| 2026-09-29T23:18Z | `NVD_API_KEY` in `.env` (D-01) | already present on line 25 (23:21Z) | pass (D-04) |
| 2026-09-29T23:24Z | Critical/High backend dependency vulnerabilities (D-05) | move to Spring Boot 4.1.1 (D-07, 23:26Z); `tech-stack.md` updated by the human (D-08, 23:30Z) | pass: 0 Critical, 0 High |
