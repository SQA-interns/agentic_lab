# Preflight report

> Written in: phase 0 · Source: `project/01_setup/*`, `project/03_technical/*` · Procedure: `general/skills/preflight` · Agent: writes

Run `conference-single-sdd-001`. First pass 2026-09-30T09:51Z (failed); human answers applied; re-check passed. Raw evidence: `out/logs/phase0-*`.

## Run facts

| Item | Value |
|---|---|
| Harness | Claude Code CLI, single agent, no delegation; tools: Bash, Read, Write, Edit, AskUserQuestion |
| Model identifier | `claude-opus-5-5` (harness-reported; label "Opus 5.5") |
| Effort | label "medium" from run-config; the harness does not expose an effort setting to the agent, so provider support could not be verified |
| Start commit | `52eb3eda302372a75e2178e5e347d85aeaa39b19` (clean) |
| Run branch | `run/single-sdd/conference-001`, created locally at the start commit (D-07) |
| Transcript | `~/.claude/projects/-home-tjan-kazar-storage-Work-lab-agentic-lab/257f106d-b679-40d9-82ff-5205eee578b5.jsonl` |

## Checks

| Check | Source of truth | Result |
|---|---|---|
| Run configuration complete | `project/01_setup/run-config.md` | PASS: workflow `sdd` exists; run id, model label, effort and template version are present |
| Platforms and tools installed at the listed versions | `project/03_technical/tech-stack.md` | PASS after D-01…D-04, D-08 (see "Host tools") |
| Local environments and services running or reachable | `project/03_technical/environments.md` | PASS: Docker daemon reachable; Maven Central, npm, Docker Hub, GitHub and PyPI reachable; Compose images build (`out/logs/phase0-compose-build.log`). A foreign Compose project `conference` is left untouched (D-10) |
| Secrets present in `.env` or marked test-only | `project/01_setup/secrets.env.example` | PASS: `02_output/.env` (git-ignored, mode 600) has DB_PASSWORD, ORGANIZER_USERNAME and ORGANIZER_PASSWORD_HASH (synthetic, D-06). RECAPTCHA_*, SMTP_* and NVD_API_KEY are empty: not needed for local/test (stub captcha, isolated Mailpit); NVD access worked without a key |
| Every listed dependency resolves | `project/03_technical/tech-stack.md` | PASS: 22 npm, 26 Maven and 8 image tags resolve; the Maven build and `npm ci` succeed |
| No listed dependency has a known Critical or High vulnerability | `project/03_technical/tech-stack.md` | PASS after D-05 and D-11. npm: 0 Critical/High (6 Moderate, 4 Low; dev-only). Maven after the tomcat 10.1.60 override: the remaining Critical/High in spring-core/web 6.2.19, spring-security 6.5.11 and pgjdbc 42.7.11 are downgraded to Low as not reachable (approved, D-11) |
| Clean working tree on the starting commit | repository | PASS: clean at the start commit; run branch created (D-07) |
| Input manifest written | `docs/00_input-manifest.sha256` | PASS: 32 files, LF-normalised; re-verified unchanged at the end of phase 0 |

## Host tools

| Tool | Pinned | Used | Result |
|---|---|---|---|
| java | Temurin 21.0.12+8 | Temurin 21.0.12+8 at `~/.local/jdks/jdk-21.0.12+8` (checksum verified); image `eclipse-temurin:21.0.12_8` | PASS (D-01) |
| maven / wrapper | 3.9.11 / 3.3.2 | wrapper 3.3.2 (checksum verified) → Maven 3.9.11 (SHA-256 pinned) | PASS (D-02) |
| node / npm | 22.23.3 / 10.9.9 | `~/.nvm/versions/node/v22.23.3`; image `node:22.23.3` | PASS |
| Docker Engine / Compose | 28.1.1 / 2.35.1 | 29.3.1 / v5.1.1 | ACCEPTED DEVIATION (D-03) |
| semgrep / gitleaks / cloc | 1.120.0 / 8.24.3 / 2.04 | digest-pinned images, versions printed at run time | PASS (D-04) |
| Spotless, SpotBugs, PMD/CPD, JaCoCo, PIT, dependency-check | as listed | ran on the skeleton (`phase0-backend-quality.log`, `phase0-backend-pitest.log`, `phase0-dependency-check*.log`) | PASS |
| Vitest+coverage, ESLint, Prettier, tsc, Playwright, Stryker, jscpd | as listed (vitest 3.2.7, D-05) | ran on the skeleton; Chromium rev 1193 installed (D-08) | PASS |
| mailpit / nginx / postgres images | v1.24.1 / 1.28.0 / 16.14 | pinned by digest in `compose.yaml` and the Dockerfiles | PASS |

## Dependency results

| id | version | Resolves | Highest vulnerability |
|---|---|---|---|
| react, react-dom | 19.2.8 | yes (MIT) | none |
| vitest, @vitest/coverage-v8 | 3.2.7 (D-05; pinned 3.2.4 had a Critical) | yes (MIT) | Moderate GHSA-82fw-gwwq-j7x9 (dev-only) |
| @stryker-mutator/core, vitest-runner | 9.0.1 | yes (Apache-2.0) | Moderate (ajv), Low (@babel/core); dev-only |
| eslint, @eslint/js | 9.25.1 | yes (MIT) | Low (@eslint/plugin-kit); dev-only |
| other npm pins (17) + @types/node 22.20.4 (D-14) | as listed | yes (MIT / Apache-2.0) | none |
| spring-boot-* 3.5.16 | 3.5.16 | yes | spring-core/web 6.2.19 and spring-security 6.5.11: Critical/High not reachable → Low (D-11) |
| tomcat-embed-* (Boot-managed) | 10.1.60 (override, D-11) | yes | none at High or above |
| org.postgresql:postgresql | 42.7.11 | yes | CVE-2026-54291 (reported HIGH, CVSS 5.9), not reachable → Low (D-11) |
| flyway 11.7.2, poi-ooxml 5.4.1, testcontainers 1.21.4, archunit 1.4.1 | as listed | yes | none at High or above |
| transitive (Boot BOM) | per `phase0-backend-dependency-tree.txt` | yes | Medium: jackson-databind, commons-lang3, spring-data-jpa, log4j-api (reported MEDIUM) |

Licences: `out/logs/phase0-licence-inventory.txt` (D-16).

## Asked the human

| When | What was missing | Answer | Re-check |
|---|---|---|---|
| 2026-09-30T09:51Z | D-01…D-08: JDK, Maven, Docker versions, scanners, vitest Critical, .env, branch, Playwright browser | 2026-09-30T09:57Z: all defaults approved | all passed |
| 2026-09-30T10:09Z | D-11: Critical/High CVEs in the Boot 3.5.16 graph | 2026-09-30T10:13Z: option 1 (Tomcat override + reachability downgrade) | re-scan: no Tomcat Critical/High; remainder as approved |
