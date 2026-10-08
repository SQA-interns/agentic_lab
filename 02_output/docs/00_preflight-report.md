# Preflight report

> Written in: phase 0 · Source: `project/00_setup/*` · Procedure: `general/phases/0-preflight.md` · Agent: writes

Starting commit `b6c9dc6` (detached HEAD); run branch `tjan/single-agent/sdd/v005` created on it.

| Check | Source of truth | Result |
|---|---|---|
| Run configuration complete | `project/00_setup/run-config.md` | pass: all five fields filled (read) |
| Platforms and tools installed at the listed versions | `project/00_setup/tech-stack.md` | **fail** (Docker Engine, see below); java, compose: other version (D-01, D-02) |
| Local environments and services running or reachable | `project/00_setup/environments.md` | **fail**: Docker Engine not reachable, so the local stack and Testcontainers cannot run (D-04) |
| Secrets present in `.env` or marked test-only | `project/00_setup/secrets.env.example` | **fail**: `.env` does not exist (`test -f`; `find -maxdepth 1 -name '.env*'`) (D-05) |
| Every listed dependency resolves | `project/00_setup/tech-stack.md` | pass: Maven (`mvnw dependency:list`), npm (`npm install` + `npm ls`), images (Docker Hub tag API, HTTP 200) |
| No listed dependency has a known Critical or High vulnerability | `project/00_setup/tech-stack.md` | **open**: npm dev tooling Critical/High (D-06); Maven scan not run, no `NVD_API_KEY` (D-05) |
| Clean working tree on the starting commit | repository | pass: `git status` clean except untracked `03_statistics/run-log.json`; HEAD = `b6c9dc6` |
| Input manifest written | `docs/00_input-manifest.sha256` | pass: 27 files (21 under `01_input/` + 6 protected root/statistics files), LF-normalised |

## Platforms and tools

| id | Listed | Method 1 | Method 2 | Result |
|---|---|---|---|---|
| java | 21.0.10+7 (Temurin) | `java -version`: OpenJDK 21.0.12.1 (Ubuntu build) | `ls /usr/lib/jvm`: no Temurin JDK | runs, other version (D-01) |
| node / npm | 24.13.0 / 11.6.2 | not on `PATH` | `~/.nvm/versions/node/v24.13.0/bin/node --version`, `npm --version` | pass (verify.sh uses this path) |
| docker | 29.8.0 | `docker version`: daemon socket `~/.docker/desktop/docker.sock` missing | `docker context ls`, `/var/run/docker.sock` missing, `systemctl --user is-active docker-desktop`: inactive | **fail** (D-04) |
| compose | 5.5.1 | `docker compose version`: v5.1.1 | same binary via Docker Desktop CLI | runs, other version (D-02) |
| Maven wrapper / Maven | 3.3.2 / 3.9.9 | `mvnw` header 3.3.2; `maven-wrapper.properties` | build log `apache-maven-3.9.9` | pass |
| Maven plugins (spring-boot, spotless, spotbugs, pmd, jacoco, pitest + junit5, dependency-check) | as listed | resolved and executed by `verify.sh 00` | versions in `pom.xml` | pass; dependency-check skipped (no key) |
| npm tooling (vite … jscpd) | as listed | `npm ls --depth=0` | `verify.sh 00` runs each | pass; Stryker exits "no tests" (expected before phase 5) |
| Playwright Chromium | bundled, rev 1243 | `npx playwright install --dry-run` | `~/.cache/ms-playwright/chromium-1243` exists | pass, nothing installed |
| semgrep, gitleaks, cloc images | as listed | Docker Hub tag API: 200 | — | resolve; cannot run until D-04 |

## Dependency results

| id | version | Resolves | Highest vulnerability |
|---|---|---|---|
| Maven runtime + test (15 + 5 entries, incl. overrides tomcat 11.0.26, log4j-api 2.26.1, commons-lang3 3.20.0) | as listed | yes, exact (`logs/00_backend-deps.txt`) | not scanned yet (D-05) |
| react, react-dom, @types/react, @types/react-dom, @vitejs/plugin-react, globals | as listed | yes | none (`npm audit --omit=dev`: 0) |
| vitest | 3.2.7 | yes | Critical: GHSA 1193683 (@vitest/mocker path traversal), 1241260/1241261 (tinypool 1.1.1 prototype pollution) — D-06 |
| @vitest/coverage-v8 | 3.2.7 | yes | Moderate (via vitest) |
| jscpd | 4.3.0 | yes | High: 1240992 (braces 3.0.3 via fast-glob/micromatch) — D-06 |
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
| 2026-10-08T20:55:53Z | D-04 Docker Engine; D-05 `.env`; D-06 npm tooling vulnerabilities | pending | |
