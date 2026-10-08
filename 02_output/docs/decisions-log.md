# Decisions log

> Written in: every phase · Format: `general/rules.md` ("Decision log format") · Agent: appends only

## Non-blocking

| D | Timestamp | Phase | Trigger | Choice | Status |
|---|---|---|---|---|---|
| D-01 | 2026-10-08T20:55:53Z | 0 | tech-stack `java` 21.0.10+7 (Temurin); host `java -version` reports OpenJDK 21.0.12.1 (Ubuntu) | Build and test with the host JDK 21; pin stays authoritative; the runtime image stays `eclipse-temurin:21.0.10_7-jre-alpine` | pending review |
| D-02 | 2026-10-08T20:55:53Z | 0 | tech-stack `compose` 5.5.1; `docker compose version` reports v5.1.1 | Use the installed Compose; pin stays authoritative | pending review |
| D-03 | 2026-10-08T20:55:53Z | 0 | `dependency-check-maven` OSS Index analyser needs Sonatype credentials not in `secrets.env.example`; Node/RetireJS analysers duplicate `npm audit` | Disable `ossindexAnalyzerEnabled`, `nodeAnalyzerEnabled`, `nodeAuditAnalyzerEnabled`, `retireJsAnalyzerEnabled` in `pom.xml`; NVD analyser stays on | pending review |
| D-07 | 2026-10-08T21:07:07Z | 0 | tech-stack `docker` 29.8.0; `docker version` and `docker info` report Engine 29.3.1 (Docker Desktop) | Use the running Engine; pin stays authoritative | pending review |
| D-08 | 2026-10-08T21:07:07Z | 0 | Dependency-Check CVE-2025-7962 (CVSS 7.5) on `angus-activation` 2.0.3, matched via `cpe:…:eclipse:angus_mail:2.0.3`; the CVE affects Jakarta/Angus Mail before 2.0.2; shipped `angus-mail` is 2.0.5 (KP-07) | False positive (identifier mismatch); suppressed in `backend/dependency-check-suppressions.xml`; raw report `logs/00_be-depcheck-raw.json` | pending review |
| D-09 | 2026-10-08T21:07:07Z | 0 | Dependency-Check CVE-2025-15104 (CVSS 5.3) on `hibernate-validator` 9.1.3.Final, matched via `cpe:…:validator:validator`; the CVE is in Nu Html Checker (validator.nu) | False positive (identifier mismatch); suppressed in the same file; raw report kept | pending review |
| D-11 | 2026-10-08T21:12:43Z | 1 | OQ-01 (options for students) unanswered; US-002 says "activities available to students" | Each option's configuration states which registration types may select it (default: both); a student form offers and accepts only options available to students (AC-002-06, AC-002-07) | pending review |
| D-12 | 2026-10-08T21:12:43Z | 1 | OQ-02 (consents) unanswered; BR-05 requires mandatory consents | One mandatory consent to processing the personal data for the conference registration; wording and list come from configuration; never preselected; registration rejected without it (AC-001-12, AC-001-13, AC-002-09) | pending review |
| D-13 | 2026-10-08T21:12:43Z | 1 | OQ-03 (storage succeeds, email fails) unanswered; scope priority 1 | The registration stays accepted and the confirmation is shown; the email failure is logged without personal data; no automatic retry (AC-006-03, AC-007-03) | pending review |
| D-14 | 2026-10-08T21:12:43Z | 1 | OQ-04 (options per category) unanswered | A maximum per category comes from configuration, default 1; more is rejected (AC-001-11) | pending review |
| D-15 | 2026-10-08T21:12:43Z | 1 | OQ-05 (second registration with the same email) unanswered | Rejected: one registration per email address, compared case-insensitively after trimming (AC-001-15) | pending review |
| D-16 | 2026-10-08T21:12:43Z | 1 | OQ-06 (retention) unanswered; SB-13 | Retention period from configuration, default 365 days after registration; then the database record and the JSON copy are deleted (AC-005-05) | pending review |
| D-17 | 2026-10-08T21:19:49Z | 2 | Contract validation needs an OpenAPI/JSON Schema parser; none is listed in `tech-stack.md` | Dev-only tooling: host Python 3.12 with preinstalled PyYAML 6.0.1 and jsonschema 4.10.3 (nothing installed) plus the vendored official OpenAPI 3.1 meta-schema 2022-10-07 (`scripts/oas-3.1-schema-2022-10-07.json`); SQL validated by applying it to the pinned `postgres` image | pending review |

## Blocking

## D-04: Docker Engine is not running
- Timestamp: 2026-10-08T20:55:53Z
- Phase: 0
- Trigger: preflight checks 2 and 3; `docker version` and `docker context ls` (context `desktop-linux`): socket `~/.docker/desktop/docker.sock` missing, `docker-desktop` user service inactive. Needed for Testcontainers, the local stack (`environments.md`), semgrep, gitleaks and cloc.
- Options: 1. (default) Human starts Docker Desktop with Docker Engine 29.8.0 (`systemctl --user start docker-desktop`), then the agent re-checks. 2. Human approves another Docker Engine version that is already installed (non-blocking record of the reported version).
- Human response: Docker started by the human (2026-10-08T21:03:29Z)
- Resolution: 1; re-checked: `docker version`/`docker info` report Engine 29.3.1 (D-07)

## D-05: `.env` is missing
- Timestamp: 2026-10-08T20:55:53Z
- Phase: 0
- Trigger: preflight check 4; no `.env` in the repository root (`test -f`, `find`). Dependency-Check cannot run without `NVD_API_KEY`.
- Options: 1. (default) Human copies `01_input/01_project/00_setup/secrets.env.example` to `.env` and fills the provided keys `POSTGRES_PASSWORD`, `ORGANIZER_USERNAME`, `ORGANIZER_PASSWORD` (random, at least 16 characters), `ORGANIZER_EMAILS`, `NVD_API_KEY`; `RECAPTCHA_*` and `SMTP_*` may stay empty. 2. Human fills all keys except `NVD_API_KEY` and approves running without the Maven dependency scan (not recommended: SB-08).
- Human response: `.env` added by the human (2026-10-08T21:03:29Z)
- Resolution: 1; re-checked: all five provided keys non-empty (presence check only)

## D-06: Critical/High vulnerabilities in pinned frontend dev tooling
- Timestamp: 2026-10-08T20:55:53Z
- Phase: 0
- Trigger: preflight check 6, `npm audit` (`logs/00_fe-audit.log`): vitest 3.2.7 Critical (@vitest/mocker path traversal <4.1.11; tinypool 1.1.1 prototype pollution to RCE <2.1.2); jscpd 4.3.0 High (braces 3.0.3 stack-exhaustion DoS via fast-glob/micromatch). Real advisories, not identifier mismatches. All are dev/test-only and not shipped: `npm audit --omit=dev` reports 0. Fixes exist only as major upgrades (vitest/@vitest/coverage-v8 5.0.3, jscpd 5.4.0).
- Options: 1. (default) Keep the pins; accept the findings as dev-only tooling that never ships and only runs locally on project code; phase 6 gates on `npm audit --omit=dev` and reports the full audit. 2. Approve upgrading vitest and @vitest/coverage-v8 to 5.0.3 and jscpd to 5.4.0 (agent checks compatibility with @stryker-mutator/vitest-runner 10.0.0 first). 3. Approve dropping jscpd and using PMD CPD for frontend duplication, keeping vitest at 3.2.7 under option 1.
- Human response: "lets upgrade vitest" (2026-10-08T21:03:29Z)
- Resolution: 2 for vitest and @vitest/coverage-v8 → 5.0.3 (commit 7f6f484; Critical findings gone). jscpd not covered by the answer → D-10

## D-10: High vulnerability in jscpd 4.3.0 (remainder of D-06)
- Timestamp: 2026-10-08T21:07:07Z
- Phase: 0
- Trigger: after the vitest upgrade, `npm audit` (`logs/00_fe-audit.log`) still reports High GHSA-vfj7-8cjw-p6xm in braces 3.0.3 (stack-exhaustion DoS), reached only through jscpd 4.3.0 → @jscpd/finder → fast-glob → micromatch → braces (5 High entries, one advisory). Dev-only, not shipped (`npm audit --omit=dev`: 0). Also 2 Moderate (qs via @stryker-mutator/core → typed-rest-client), non-blocking.
- Options: 1. (default) Approve upgrading jscpd to 5.4.0 (the only fixed version), in line with the vitest upgrade. 2. Drop jscpd and measure frontend duplication with PMD CPD (already pinned, supports TypeScript). 3. Keep jscpd 4.3.0 and accept the finding as dev-only tooling run on project code only; phase 6 gates on `npm audit --omit=dev`.
- Human response: "3" (2026-10-08T21:10:48Z)
- Resolution: 3; jscpd 4.3.0 kept, High accepted as dev-only tooling (not shipped). Phase 6 gates SB-08 on `npm audit --omit=dev`; the full audit is reported in the release notes.
