# Preflight report

> Written in: phase 0 · Source: `project/00_setup/*` · Procedure: `general/phases/0-preflight.md` · Agent: writes

Starting commit `b6c9dc6` (detached HEAD); run branch `tjan/single-agent/sdd/v005` created on it.

| Check | Source of truth | Result |
|---|---|---|
| Run configuration complete | `project/00_setup/run-config.md` | pass: all five fields filled (read) |
| Platforms and tools installed at the listed versions | `project/00_setup/tech-stack.md` | pass: all run; java, compose, docker report other versions (D-01, D-02, D-07) |
| Local environments and services running or reachable | `project/00_setup/environments.md` | pass after D-04: Docker Engine 29.3.1 reachable (`docker version`, `docker info`); stack images resolve |
| Secrets present in `.env` or marked test-only | `project/00_setup/secrets.env.example` | pass after D-05: 5 provided keys non-empty (`sed … \| grep -q .`), `.env` git-ignored; RECAPTCHA_*/SMTP_* test-only or not needed |
| Every listed dependency resolves | `project/00_setup/tech-stack.md` | pass: Maven (`mvnw dependency:list`), npm (`npm install` + `npm ls`), images (Docker Hub tag API, HTTP 200) |
| No listed dependency has a known Critical or High vulnerability | `project/00_setup/tech-stack.md` | **open**: jscpd High (D-10). Maven: 2 false positives suppressed (D-08, D-09), 0 left. npm runtime: 0. vitest Critical fixed by upgrade (D-06) |
| Clean working tree on the starting commit | repository | pass: `git status` clean except untracked `03_statistics/run-log.json`; HEAD = `b6c9dc6` |
| Input manifest written | `docs/00_input-manifest.sha256` | pass: 27 files (21 under `01_input/` + 6 protected root/statistics files), LF-normalised |

## Platforms and tools

| id | Listed | Method 1 | Method 2 | Result |
|---|---|---|---|---|
| java | 21.0.10+7 (Temurin) | `java -version`: OpenJDK 21.0.12.1 (Ubuntu build) | `ls /usr/lib/jvm`: no Temurin JDK | runs, other version (D-01) |
| node / npm | 24.13.0 / 11.6.2 | not on `PATH` | `~/.nvm/versions/node/v24.13.0/bin/node --version`, `npm --version` | pass (verify.sh uses this path) |
| docker | 29.8.0 | `docker version`: daemon socket `~/.docker/desktop/docker.sock` missing | `docker context ls`, `/var/run/docker.sock` missing, `systemctl --user is-active docker-desktop`: inactive | pass after D-04: Engine 29.3.1, other version (D-07) |
| compose | 5.5.1 | `docker compose version`: v5.1.1 | same binary via Docker Desktop CLI | runs, other version (D-02) |
| Maven wrapper / Maven | 3.3.2 / 3.9.9 | `mvnw` header 3.3.2; `maven-wrapper.properties` | build log `apache-maven-3.9.9` | pass |
| Maven plugins (spring-boot, spotless, spotbugs, pmd, jacoco, pitest + junit5, dependency-check) | as listed | resolved and executed by `verify.sh 00` | versions in `pom.xml` | pass; dependency-check runs with the NVD key |
| npm tooling (vite … jscpd) | as listed | `npm ls --depth=0` | `verify.sh 00` runs each | pass; Stryker exits "no tests" (expected before phase 5) |
| Playwright Chromium | bundled, rev 1243 | `npx playwright install --dry-run` | `~/.cache/ms-playwright/chromium-1243` exists | pass, nothing installed |
| semgrep, gitleaks, cloc images | as listed | Docker Hub tag API: 200 | — | run via `verify.sh 00`: semgrep 1 Medium (`frontend/.npmrc` has no minimum release age; phase 6), gitleaks 0 on this branch, cloc ok |

## Dependency results

| id | version | Resolves | Highest vulnerability |
|---|---|---|---|
| Maven runtime + test (15 + 5 entries, incl. overrides tomcat 11.0.26, log4j-api 2.26.1, commons-lang3 3.20.0) | as listed | yes, exact (`logs/00_backend-deps.txt`) | none after suppressing D-08, D-09 (`logs/00_be-depcheck-raw.json`) |
| react, react-dom, @types/react, @types/react-dom, @vitejs/plugin-react, globals | as listed | yes | none (`npm audit --omit=dev`: 0) |
| vitest | 3.2.7 → 5.0.3 (D-06) | yes | none after upgrade; at 3.2.7 Critical: GHSA 1193683 (@vitest/mocker path traversal), 1241260/1241261 (tinypool 1.1.1 prototype pollution) |
| @vitest/coverage-v8 | 3.2.7 → 5.0.3 (D-06) | yes | none after upgrade |
| jscpd | 4.3.0 | yes | High: 1240992 (braces 3.0.3 via fast-glob/micromatch) — D-10 |
| @stryker-mutator/core | 10.0.0 | yes | Moderate: qs via typed-rest-client 2.3.1 |
| other npm test/build tooling | as listed | yes | none reported |
| postgres, eclipse-temurin, node, nginx, axllent/mailpit images | as listed | yes (tag API 200) | not scanned (no image scanner in `tooling`) |

## Component commands (ES-05)

| Component | build | test | check | run |
|---|---|---|---|---|
| backend | `./mvnw -B package` | `./mvnw -B verify` | `./mvnw -B spotless:check compile pmd:check pmd:cpd-check spotbugs:check` | `./mvnw spring-boot:run` |
| frontend | `npm run build` | `npm test` | `npm run check` | `npm run dev` |

## Asked the human

| When | What was missing | Answer | Re-check |
|---|---|---|---|
| 2026-10-08T20:55:53Z | D-04 Docker Engine; D-05 `.env`; D-06 npm tooling vulnerabilities | D-04, D-05: done; D-06: upgrade vitest (2026-10-08T21:03:29Z) | 2026-10-08T21:07:07Z: pass; jscpd remains (D-10) |
| 2026-10-08T21:07:07Z | D-10 jscpd High | pending | |
