# Preflight report

> Written in: phase 0 · Source: `project/00_setup/run-config.md`, `project/stack.md`, `project/secrets.env.example` · Procedure: `skills/preflight` · Agent: writes

| Check | Source of truth | Result |
|---|---|---|
| Run configuration complete | `project/00_setup/run-config.md` | pass: model, effort, template version, run id present (read) |
| Platforms and tools installed at the listed versions | `project/stack.md` | pass with decisions: java OpenJDK 21.0.12.1 (D-03), docker 29.3.1 (D-04), compose v5.1.1 (D-05), node 24.13.0 + npm 11.6.2 after D-01; tool table below |
| Local environments and services running or reachable | `project/stack.md` ("Environments") | pass: Docker engine reachable (`docker version`, `docker ps`); test env proven by the Testcontainers smoke test (`logs/00_be-test.log`); local stack is built in phase 4 |
| Secrets present in `.env` or marked test-only | `project/secrets.env.example` | pass after D-02: `secrets.sh check` (present/missing only); RECAPTCHA_* empty, test-mode only |
| Every listed dependency resolves | `project/stack.md` | pass: 66/66 artifacts at exact version (registry/Maven Central GET, `docker manifest inspect`; `logs/00_resolve.log`); installed trees match pins (`dependency:tree`, `package-lock.json` compare) |
| No listed dependency has a known Critical or High vulnerability | `project/stack.md` | pass after D-07, D-08: be-depscan 0/0/0/0, fe-depscan critical=0 high=0 medium=2 (D-10); Medium D-09 suppressed as false positive |
| Clean working tree on the starting commit | repository | pass: `git status --porcelain` empty except allowed untracked files; HEAD 20c720214a74 at start |
| Input manifest written | `docs/00_input-manifest.sha256` | pass: 26 files (every file under `01_input/`, `AGENTS.md`, `README.md`, `03_statistics/metrics.md`, `03_statistics/run-log.template.json`), LF-normalised SHA-256. `03_statistics/usage.md` excluded: the human fills it after the run (README step 6). |

## Tool versions

Method: each tool's own version command, run twice where it failed the first time.

| Tool | Listed | Reported | Method |
|---|---|---|---|
| Java | Temurin 21.0.10+7 | OpenJDK 21.0.12.1 (D-03) | `java -version` |
| Node.js / npm | 24.13.0 / 11.6.2 | 24.13.0 / 11.6.2 (D-01) | `node --version`, `npm --version` |
| Docker / Compose | 29.8.0 / 5.5.1 | 29.3.1 / v5.1.1 (D-04, D-05) | `docker version`, `docker compose version` |
| Maven (wrapper 3.3.2) | 3.9.9 | 3.9.9 | `./mvnw -v` |
| Maven plugins (boot, spotless, spotbugs, pmd, jacoco, pitest + junit5, dependency-check) | stack pins | as listed | executed by `verify.sh 00 all` from `pom.xml` pins |
| vite, tsc, eslint, prettier, playwright, jscpd, stryker | stack pins | as listed | `node_modules/.bin/<tool> --version` |
| vitest, @vitest/coverage-v8 | 3.2.7 | 4.1.11 (D-07 replaces the pin) | `package-lock.json` |
| semgrep, gitleaks, redocly, playwright image | stack pins | 1.177.0, v8.30.1, 2.57.0, 1.63.0 | `docker run <image> --version` |
| cloc image 2.10 | 2.10 | 1.98 (D-06) | `docker run aldanial/cloc:2.10 --version` |

## Dependency results

Scanners: OWASP Dependency-Check 12.1.0 (OSS Index disabled per `stack.md` options; NVD via `NVD_API_KEY`, `logs/00_be-depscan.log`), npm audit 11.6.2 (`logs/00_fe-depscan.log`). Resolution of every row: `logs/00_resolve.log`.

| id | version | Resolves | Highest vulnerability |
|---|---|---|---|
| backend runtime and test (20 Maven entries) | as listed | yes | none after suppression of CVE-2025-7962 on angus-activation (transitive, false positive, D-08) and CVE-2025-15104 (false positive, D-09) |
| frontend runtime (react, react-dom 19.3.0) | as listed | yes | none |
| frontend build and test (npm tooling) | as listed; vitest 4.1.11 (D-07) | yes | Medium: qs 6.15.1 via @stryker-mutator/core (D-10) |
| vitest, @vitest/coverage-v8 | 3.2.7 | yes | Critical: tinypool GHSA-5gmw-xhrv-c9v3, GHSA-85c8-ppgw-ccpr; replaced by 4.1.11 (D-07) |
| container images (postgres, eclipse-temurin, node, nginx, mailpit) | as listed | yes | not scanned in phase 0 (no image scanner listed in `stack.md`) |
| container tools (semgrep, gitleaks, cloc, redocly, playwright) | as listed | yes | n/a (run-time tools, not shipped) |

## Asked the human

| When | What was missing | Answer | Re-check |
|---|---|---|---|
| 2026-10-06T07:43:39Z | D-01 Node.js 24.13.0; D-02 `.env` | D-02: `.env` filled (08:45:02Z); D-01: agent asked to install (09:02:04Z) | `secrets.sh check` pass 08:45:02Z; node/npm fresh shell pass 09:02:30Z |
| 2026-10-06T11:38:04Z | D-07 npm Critical fix; D-08 High false-positive downgrade | D-07: vitest 4.1.11; D-08: suppress (11:40:16Z) | `verify.sh 00 all`: all 19 tools exit 0 |
