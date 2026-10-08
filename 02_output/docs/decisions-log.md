# Decisions log

> Written in: every phase · Format: `general/rules.md` ("Decision log format") · Agent: appends only

## Non-blocking

| D | Timestamp | Phase | Trigger | Choice | Status |
|---|---|---|---|---|---|
| D-01 | 2026-10-08T20:55:53Z | 0 | tech-stack `java` 21.0.10+7 (Temurin); host `java -version` reports OpenJDK 21.0.12.1 (Ubuntu) | Build and test with the host JDK 21; pin stays authoritative; the runtime image stays `eclipse-temurin:21.0.10_7-jre-alpine` | pending review |
| D-02 | 2026-10-08T20:55:53Z | 0 | tech-stack `compose` 5.5.1; `docker compose version` reports v5.1.1 | Use the installed Compose; pin stays authoritative | pending review |
| D-03 | 2026-10-08T20:55:53Z | 0 | `dependency-check-maven` OSS Index analyser needs Sonatype credentials not in `secrets.env.example`; Node/RetireJS analysers duplicate `npm audit` | Disable `ossindexAnalyzerEnabled`, `nodeAnalyzerEnabled`, `nodeAuditAnalyzerEnabled`, `retireJsAnalyzerEnabled` in `pom.xml`; NVD analyser stays on | pending review |

## Blocking

## D-04: Docker Engine is not running
- Timestamp: 2026-10-08T20:55:53Z
- Phase: 0
- Trigger: preflight checks 2 and 3; `docker version` and `docker context ls` (context `desktop-linux`): socket `~/.docker/desktop/docker.sock` missing, `docker-desktop` user service inactive. Needed for Testcontainers, the local stack (`environments.md`), semgrep, gitleaks and cloc.
- Options: 1. (default) Human starts Docker Desktop with Docker Engine 29.8.0 (`systemctl --user start docker-desktop`), then the agent re-checks. 2. Human approves another Docker Engine version that is already installed (non-blocking record of the reported version).
- Human response: none
- Resolution:

## D-05: `.env` is missing
- Timestamp: 2026-10-08T20:55:53Z
- Phase: 0
- Trigger: preflight check 4; no `.env` in the repository root (`test -f`, `find`). Dependency-Check cannot run without `NVD_API_KEY`.
- Options: 1. (default) Human copies `01_input/01_project/00_setup/secrets.env.example` to `.env` and fills the provided keys `POSTGRES_PASSWORD`, `ORGANIZER_USERNAME`, `ORGANIZER_PASSWORD` (random, at least 16 characters), `ORGANIZER_EMAILS`, `NVD_API_KEY`; `RECAPTCHA_*` and `SMTP_*` may stay empty. 2. Human fills all keys except `NVD_API_KEY` and approves running without the Maven dependency scan (not recommended: SB-08).
- Human response: none
- Resolution:

## D-06: Critical/High vulnerabilities in pinned frontend dev tooling
- Timestamp: 2026-10-08T20:55:53Z
- Phase: 0
- Trigger: preflight check 6, `npm audit` (`logs/00_fe-audit.log`): vitest 3.2.7 Critical (@vitest/mocker path traversal <4.1.11; tinypool 1.1.1 prototype pollution to RCE <2.1.2); jscpd 4.3.0 High (braces 3.0.3 stack-exhaustion DoS via fast-glob/micromatch). Real advisories, not identifier mismatches. All are dev/test-only and not shipped: `npm audit --omit=dev` reports 0. Fixes exist only as major upgrades (vitest/@vitest/coverage-v8 5.0.3, jscpd 5.4.0).
- Options: 1. (default) Keep the pins; accept the findings as dev-only tooling that never ships and only runs locally on project code; phase 6 gates on `npm audit --omit=dev` and reports the full audit. 2. Approve upgrading vitest and @vitest/coverage-v8 to 5.0.3 and jscpd to 5.4.0 (agent checks compatibility with @stryker-mutator/vitest-runner 10.0.0 first). 3. Approve dropping jscpd and using PMD CPD for frontend duplication, keeping vitest at 3.2.7 under option 1.
- Human response: none
- Resolution:
