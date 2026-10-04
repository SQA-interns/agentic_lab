# Preflight report

> Written in: phase 0 · Source: `project/00_setup/*` · Procedure: `general/phases/0-preflight.md` · Agent: writes

Run `tanej-03_conference-registration_opus5.5_sdd_template-tanej-1.2`, starting commit `a4272e3`. Tool logs: `out/logs/0_*.log`.

| Check | Source of truth | Result |
|---|---|---|
| Run configuration complete | `project/00_setup/run-config.md` | pass: model, effort, template version, run id, unattended flag filled (read) |
| Platforms and tools installed at the listed versions | `project/00_setup/tech-stack.md` | pass with decisions: Java runs as 21.0.11 (D-01), Node 24.10.0 / npm 10.9.4 (D-02), cloc image reports 1.98 (D-05); every other entry reports its pin (table below) |
| Local environments and services running or reachable | `project/00_setup/environments.md` | pass: Docker Engine 29.8.0 and Compose 5.5.1 running; Testcontainers PostgreSQL started by `be-test`; all runtime images pulled and started (`--version`) |
| Secrets present in `.env` or marked test-only | `project/00_setup/secrets.env.example` | pass: `secrets.sh check` run twice (second time in a fresh shell): 5 required keys present, `SMTP_*` (not needed) present, `RECAPTCHA_*` (test-mode only) empty; `secrets.sh leak-check`: no value in any file or commit message |
| Every listed dependency resolves | `project/00_setup/tech-stack.md` | pass: `./mvnw dependency:list` (`out/logs/0_backend-deps.log`), `npm install` + `npm ls` (`out/logs/0_frontend-install.log`), `docker pull` of every image |
| No listed dependency has a known Critical or High vulnerability | `project/00_setup/tech-stack.md` | pass: Dependency-Check 0 after suppressing 2 false positives (D-03 High, D-04 Medium); npm audit 0 Critical/High, 5 Medium |
| Clean working tree on the starting commit | repository | pass: `git status` clean apart from `run-log.json`; HEAD `a4272e3` |
| Input manifest written | `docs/00_input-manifest.sha256` | pass: 27 files, LF-normalised |

## Platforms and tools

| id | pinned | reported | method |
|---|---|---|---|
| java | 21.0.10+7 Temurin | 21.0.11+9 Oracle | `java -version`; `where java`, `JAVA_HOME` (D-01) |
| node / npm | 24.13.0 / 11.6.2 | 24.10.0 / 10.9.4 | `node --version`, `npm --version`; `where node` (D-02) |
| docker / compose | 29.8.0 / 5.5.1 | 29.8.0 / v5.5.1 | `docker version`, `docker compose version` |
| maven-wrapper | 3.3.2 (Maven 3.9.9) | 3.3.2, Maven 3.9.9 | `.mvn/wrapper/maven-wrapper.properties`; build log |
| Maven plugins (boot, spotless, spotbugs, pmd, jacoco, pitest + junit5, dependency-check) | as listed | as listed | `pom.xml`; each ran through `verify.sh` |
| npm tools (vite, typescript, eslint, @eslint/js, typescript-eslint, react-hooks, react-refresh, prettier, vitest, coverage-v8, playwright, @types/node, ajv, stryker core + runner, jscpd) | as listed | as listed | `npm ls --depth=0`; `npx playwright --version` 1.63.0 |
| semgrep/semgrep | 1.177.0 | 1.177.0 | `semgrep --version` |
| zricethezav/gitleaks | v8.30.1 | v8.30.1 | `gitleaks version` |
| aldanial/cloc | 2.10 | 1.98 | `cloc --version` (D-05) |
| redocly/cli | 2.57.0 | 2.57.0 | `redocly --version` |
| mcr.microsoft.com/playwright | v1.63.0-noble | chromium-1243, firefox-1543, webkit-2359 | `ls /ms-playwright`; `verify.sh 0 e2e` |

## Dependency results

| id | version | Resolves | Highest vulnerability |
|---|---|---|---|
| spring-boot-starter-parent, -webmvc, -data-jpa, -validation, -mail, -actuator, -security, -flyway, -test, -webmvc-test | 4.1.1 | yes (Framework 7.0.9, Security 7.1.1) | none (hibernate-validator 9.1.3: false positive D-04; angus-activation 2.0.3: false positive D-03) |
| flyway-core, flyway-database-postgresql | 12.4.0 | yes | none |
| org.postgresql:postgresql | 42.7.13 | yes | none |
| poi-ooxml | 5.5.1 | yes | none |
| tomcat-embed-core | 11.0.26 | yes | none |
| log4j-api | 2.26.1 | yes | none |
| commons-lang3 | 3.20.0 | yes | none |
| testcontainers-junit-jupiter, testcontainers-postgresql | 2.0.5 | yes | none |
| archunit-junit5 | 1.3.2 | yes | none |
| JUnit Jupiter / Mockito (Boot-managed) | 6.0.3 / 5.23.0 | yes | none |
| react, react-dom, @types/react, @types/react-dom | 19.3.0 | yes | none |
| @vitejs/plugin-react | 4.7.0 | yes | none |
| @testing-library/react / jest-dom | 16.3.3 / 6.9.1 | yes | none |
| jsdom / globals | 26.1.0 / 15.15.0 | yes | none |
| vitest, @vitest/coverage-v8 (tooling) | 3.2.7 | yes | Medium: GHSA-82fw-gwwq-j7x9 via @vitest/mocker (test-only) |
| jscpd (tooling) | 5.4.0 | yes | Medium: qs advisories via typed-rest-client (dev-only) |
| postgres / eclipse-temurin / node / nginx / axllent/mailpit images | 16.15-alpine / 21.0.10_7-jre-alpine / 24.13.0-alpine / 1.30.5-alpine / v1.31.1 | yes, report 16.15 / 21.0.10 / v24.13.0 / 1.30.5 / v1.31.1 | not scanned in phase 0 (images are scanned with the shipped artifacts in phase 6) |

Other phase 0 scanner results: Semgrep 1 Medium (`npm-missing-minimum-release-age` on `frontend/.npmrc`), gitleaks no leaks (branch history and working tree); both carried to phase 6.

## Component commands (ES-05)

| Component | build | test | check | run |
|---|---|---|---|---|
| backend | `./mvnw -B -DskipTests package` | `./mvnw -B test` | `./mvnw -B compile spotless:check pmd:check pmd:cpd-check spotbugs:check` | `./mvnw spring-boot:run` (needs PostgreSQL) |
| frontend | `npm run build` | `npm test` | `npm run check` | `npm run dev` |

## Asked the human

| When | What was missing | Answer | Re-check |
|---|---|---|---|
| — | nothing: no check needed a human | — | — |
