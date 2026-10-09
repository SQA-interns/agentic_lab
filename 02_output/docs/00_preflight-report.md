# Preflight report

> Written in: phase 0 · Source: `project/00_setup/run-config.md`, `project/stack.md`, `project/secrets.env.example` · Procedure: `skills/preflight` · Agent: writes

| Check | Source of truth | Result |
|---|---|---|
| Run configuration complete | `project/00_setup/run-config.md` | pass: model, effort, template version, run id filled; start commit `6166314` recorded in `03_statistics/run-log.json` |
| Platforms and tools installed at the listed versions | `project/stack.md` | pass with D-01, D-02: node 24.13.0 / npm 11.6.2, Docker 29.8.0, Compose 5.5.1 (`--version`); java runs as Oracle 21.0.11+9 (D-01); Maven wrapper 3.3.2 / Maven 3.9.9 (`./mvnw -v`); every Maven plugin and npm tool at its pin (`dependency:resolve-plugins`, `npm ls --depth=0`); semgrep 1.177.0, gitleaks v8.30.1, redocly 2.57.0, Playwright 1.63.0 (version command in each image); cloc tag 2.10 prints 1.98 (D-02) |
| Local environments and services running or reachable | `project/stack.md` ("Environments") | pass: Docker engine running; every pinned image pulls (`docker pull`); `eclipse-temurin:21.0.10_7-jre-alpine` reports Temurin-21.0.10+7 |
| Secrets present in `.env` or marked test-only | `project/secrets.env.example` | pass: `secrets.sh check` exit 0; 7 present, `RECAPTCHA_SITE_KEY` and `RECAPTCHA_SECRET_KEY` empty and test-mode only |
| Every listed dependency resolves | `project/stack.md` | pass: Maven `dependency:resolve` and `dependency:list` show every pin (Spring Framework 7.0.9, Security 7.1.1, JUnit 6.0.3, Mockito 5.23.0 via Boot 4.1.1); `npm install` exit 0; all images pull |
| No listed dependency has a known Critical or High vulnerability | `project/stack.md` | pass after D-07, D-08: vitest and @vitest/coverage-v8 at 4.1.11 (D-07); CVE-2025-7962 suppressed as false positive (D-08), CVE-2025-15104 likewise (D-05); re-scan: backend 0/0/0/0, frontend critical 0, high 0, moderate 2 (`qs`, D-06). Container images: no scanner listed in `project/stack.md` |
| Clean working tree on the starting commit | repository | pass: `git status` clean (only ignored `.env`, `.claude/`) at `6166314` on `tanej/single-agent/sdd/v005` |
| Input manifest written | `docs/00_input-manifest.sha256` | pass: 27 files, LF-normalised `sha256sum` |

## Dependency results

Method: backend `verify.sh 0 be-depscan` (Dependency-Check 12.1.0, NVD with `NVD_API_KEY` through `secrets.sh run`, OSS Index disabled per `project/stack.md`); frontend `verify.sh 0 fe-audit` (npm audit). Both run twice. Only entries with a result are listed; every other entry resolves at its pin with no known vulnerability.

| id | version | Resolves | Highest vulnerability |
|---|---|---|---|
| org.springframework.boot:spring-boot-starter-mail (transitive org.eclipse.angus:angus-activation 2.0.3) | 4.1.1 | yes | CVE-2025-7962, CVSS 7.5 High, CPE false positive (D-04) |
| org.springframework.boot:spring-boot-starter-validation (transitive hibernate-validator 9.1.3.Final) | 4.1.1 | yes | CVE-2025-15104, CVSS 5.3 Medium, CPE false positive (D-05) |
| vitest | 3.2.7 | yes | Critical: GHSA-5gmw-xhrv-c9v3, GHSA-85c8-ppgw-ccpr via tinypool (D-03); replaced by 4.1.11: none (D-07) |
| @vitest/coverage-v8 | 3.2.7 | yes | Critical, inherited from vitest (D-03); replaced by 4.1.11: none (D-07) |
| @stryker-mutator/core (transitive qs 6.15.1) | 10.0.0 | yes | Moderate: GHSA-q8mj-m7cp-5q26, GHSA-x5fp-wj9c-mxmx, GHSA-4mjr-xmp4-gh2g (D-06) |
| all other `dependencies` and `tooling` entries | as listed | yes | none |

## Asked the human

| When | What was missing | Answer | Re-check |
|---|---|---|---|
| 2026-10-09T08:27:05Z | D-03: replacement version for vitest and @vitest/coverage-v8; D-04: approval to classify CVE-2025-7962 on angus-activation as a false positive | 2026-10-09T08:34:24Z: D-03 option 1, 4.1.11 (D-07); D-04 option 1, false positive Low (D-08) | `verify.sh 0 be-depscan,fe-audit`: backend 0 Critical/High, frontend 0 Critical/High; frontend build, check and test pass on 4.1.11 |

## Component commands (ES-05)

`project/constraints.md` leaves them empty; run from the component folder.

| Component | Build | Test | Check | Run |
|---|---|---|---|---|
| backend | `./mvnw -B -DskipTests package` | `./mvnw -B verify` | `./mvnw -B spotless:check pmd:check pmd:cpd-check spotbugs:check` | `./mvnw spring-boot:run` |
| frontend | `npm run build` | `npm test` | `npm run check` | `npm run dev` |
