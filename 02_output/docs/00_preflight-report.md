# Preflight report

> Written in: phase 0 · Source: `project/00_setup/*` · Procedure: `general/skills/preflight` · Agent: writes

Run `05_conference-registration_opus5.5_sdd_template-1.2`, starting commit `30047fcf688c0ebaf965c9863185bd1b7cd11f2d`, checked 2026-09-30.

| Check | Source of truth | Result |
|---|---|---|
| Run configuration complete | `project/00_setup/run-config.md` | Pass: model, effort, template version and run id filled (read the file) |
| Platforms and tools installed at the listed versions | `project/00_setup/tech-stack.md` | Pass: `java -version` 21.0.10+7 Temurin; `node --version` v24.13.0, `npm --version` 11.6.2; `docker version` 29.8.0; `docker compose version` v5.5.1. Maven tools resolve and run in `./mvnw verify` (spotless 2.44.3, pmd 3.26.0, spotbugs 4.10.4.1, jacoco 0.8.12), dependency-check 12.1.0 ran; pitest 1.30.0 and junit5 plugin 1.2.3 resolve. npm tools report their pinned versions (`npx <tool> --version`). Container tools: semgrep 1.177.0, gitleaks v8.30.1; cloc reports 1.98 for tag 2.10 (D-01, non-blocking) |
| Local environments and services running or reachable | `project/00_setup/environments.md` | Pass: Docker running; all images pulled (`logs/00_image-pull.log`); `docker compose up` starts postgres, mailpit, backend, frontend, all healthy; `/api/health/readiness` returns UP directly and through the frontend proxy |
| Secrets present in `.env` or marked test-only | `project/00_setup/secrets.env.example` | Pass: `POSTGRES_PASSWORD`, `ORGANIZER_USERNAME`, `ORGANIZER_PASSWORD` (length ≥ 16 checked by length only), `ORGANIZER_EMAILS`, `NVD_API_KEY` non-empty (sed/tr/grep -q check, then key names listed with values masked). `RECAPTCHA_*` test-mode only, `SMTP_*` not needed locally: empty, allowed. `.env` is ignored by git |
| Every listed dependency resolves | `project/00_setup/tech-stack.md` | Pass: every Maven coordinate returns HTTP 200 from Maven Central (`logs/00_maven-resolve.log`), every npm package resolves (`logs/00_npm-resolve.log`), every image pulls. Resolved tree confirms overrides: tomcat-embed-core 11.0.26, log4j-api 2.26.1, commons-lang3 3.20.0 (`logs/00_backend-deps.txt`) |
| No listed dependency has a known Critical or High vulnerability | `project/00_setup/tech-stack.md` | Pass after human approval: Dependency-Check first reported CVE-2025-7962 (CVSS 7.5 High) on transitive `angus-activation 2.0.3`, a CPE false positive; the human approved a suppression (D-04) and the re-run reports no vulnerability with `failBuildOnCVSS=7`. CVE-2025-15104 Medium false positive (D-05). `npm audit`: 5 moderate, 0 high/critical, all in dev/test tooling (`logs/00_npm-audit.log`) |
| Clean working tree on the starting commit | repository | Pass: `git status` clean at `30047fc` except untracked `03_statistics/run-log.json`; `.env` ignored |
| Input manifest written | `docs/00_input-manifest.sha256` | Pass: 25 files (all of `01_input/`, `AGENTS.md`, `README.md`, `03_statistics/metrics.md`, `run-log.template.json`, `usage.md`), LF-normalised SHA-256 |

## Dependency results

| id | version | Resolves | Highest vulnerability |
|---|---|---|---|
| org.springframework.boot:spring-boot-starter-* (parent, webmvc, data-jpa, validation, mail, actuator, security, flyway, test, webmvc-test), spring-boot-maven-plugin | 4.1.1 | yes | High (CVE-2025-7962 via transitive angus-activation 2.0.3 of starter-mail; false positive, D-04); Medium CVE-2025-15104 via hibernate-validator (false positive, D-05) |
| org.flywaydb:flyway-core, flyway-database-postgresql | 12.4.0 | yes | none |
| org.postgresql:postgresql | 42.7.13 | yes | none |
| org.apache.poi:poi-ooxml | 5.5.1 | yes | none |
| org.apache.tomcat.embed:tomcat-embed-core | 11.0.26 | yes | none |
| org.apache.logging.log4j:log4j-api | 2.26.1 | yes | none |
| org.apache.commons:commons-lang3 | 3.20.0 | yes | none |
| org.testcontainers:testcontainers-junit-jupiter, testcontainers-postgresql | 2.0.5 | yes | none |
| com.tngtech.archunit:archunit-junit5 | 1.3.2 | yes | none |
| react, react-dom, @types/react, @types/react-dom | 19.3.0 | yes | none |
| @vitejs/plugin-react | 4.7.0 | yes | none |
| @testing-library/react / jest-dom | 16.3.3 / 6.9.1 | yes | none |
| jsdom / globals | 26.1.0 / 15.15.0 | yes | none |
| vitest, @vitest/coverage-v8 | 3.2.7 | yes | Moderate GHSA-82fw-gwwq-j7x9 (@vitest/mocker, test-only) |
| other npm tooling (vite, typescript, eslint, prettier, playwright, stryker, jscpd) | as listed | yes | Moderate qs (transitive of jscpd tooling, dev-only) |
| container images (postgres, eclipse-temurin, node, nginx, mailpit) | as listed | yes | not scanned (no image scanner in tooling) |

## Asked the human

| When | What was missing | Answer | Re-check |
|---|---|---|---|
| 2026-09-30T14:22Z | D-04: approve treating CVE-2025-7962 on angus-activation 2.0.3 as a false positive | option 1, suppress as false positive (14:23Z) | Dependency-Check re-run: BUILD SUCCESS, no vulnerable dependency reported (`logs/00_dependency-check.log`) |
