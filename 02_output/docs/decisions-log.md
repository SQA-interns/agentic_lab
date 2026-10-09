# Decisions log

> Written in: every phase · Format: `general/rules.md` ("Decision log format") · Agent: appends only

## Non-blocking

| D | Timestamp | Phase | Trigger | Choice | Status |
|---|---|---|---|---|---|
| D-01 | 2026-10-09T10:54:09Z | 0 | Gitleaks (`verify.sh`) found 142 `generic-api-key`/`curl-auth-user` matches, all in commits before the starting commit b6c9dc6 (earlier runs' logs and tests, none in the current tree) | Gitleaks scans only this run's commits (`startCommit..HEAD`); the unscoped report was overwritten by the scoped re-run (0 leaks); its counts are recorded in this row | pending review |
| D-02 | 2026-10-09T10:54:09Z | 0 | Dependency-Check 12.1.0 OSS Index analyser: HTTP 401 on all 123 requests; it needs Sonatype credentials not in `secrets.env.example` | `ossindexAnalyzerEnabled=false` in `backend/pom.xml`; the NVD analyser completes | pending review |
| D-03 | 2026-10-09T10:54:09Z | 0 | Dependency-Check CVE-2025-7962 (CVSS 7.5) on `angus-activation-2.0.3.jar`; matched cpe `eclipse:angus_mail:2.0.3`; actual artifacts: `org.eclipse.angus:angus-activation:2.0.3` and `angus-mail:2.0.5` (CVE affects < 2.0.2) (KP-07) | False positive; suppressed in `backend/dependency-check-suppressions.xml`; raw report `out/logs/0_dependency-check-raw.json` | pending review |
| D-04 | 2026-10-09T10:54:09Z | 0 | Dependency-Check CVE-2025-15104 (CVSS 5.3) on `hibernate-validator-9.1.3.Final.jar`; matched cpe `validator:validator:9.1.3` (Nu Html Checker), actual `org.hibernate.validator:hibernate-validator:9.1.3.Final` | False positive; suppressed in the same file; same raw report | pending review |
| D-05 | 2026-10-09T10:54:09Z | 0 | Platform `java` pinned Temurin 21.0.10+7; host `java -version` reports OpenJDK 21.0.12.1 (Ubuntu build); runs and builds the backend | Use the host JDK; the pin stays authoritative and the backend image stays `eclipse-temurin` per tech-stack | pending review |
| D-06 | 2026-10-09T10:54:09Z | 0 | Platform `docker` pinned 29.8.0; Engine reports 29.3.1 (client 29.5.0); runs containers | Use as is; pin stays authoritative | pending review |
| D-07 | 2026-10-09T10:54:09Z | 0 | Platform `compose` pinned 5.5.1; `docker compose version` reports v5.1.1 | Use as is; pin stays authoritative | pending review |
| D-08 | 2026-10-09T10:54:09Z | 0 | Tool `aldanial/cloc:2.10` image reports `--version` 1.98 | Use the pinned tag; pin stays authoritative | pending review |
| D-10 | 2026-10-09T10:54:09Z | 0 | Starting commit b6c9dc6 was checked out as a detached HEAD | Commits go to a new local branch `run/tanej-01_conference-registration_opus5.5_sdd_template-tanej-1.0` at b6c9dc6; nothing pushed | pending review |

## Blocking

## D-09: Node.js 24.13.0 (npm 11.6.2) is not installed on the host
- Timestamp: 2026-10-09T10:54:09Z
- Phase: 0
- Trigger: `tech-stack.md` platform `node` 24.13.0. `node --version` and `npm --version`: command not found; confirmed with a login shell (`command -v node npm`) and by looking for nvm/fnm/volta and /usr/local/bin/node: none. Blocks the frontend skeleton and its `package-lock.json` (architecture.md), `npm audit` (dependency scan of the npm set) and every frontend tool. The image `node:24.13.0-alpine` runs (reports v24.13.0).
- Options:
  1. (proposed default) The human installs Node.js 24.13.0 on the host (it bundles npm 11.6.2), e.g. `nvm install 24.13.0` or the nodejs.org tarball; `node --version` must print v24.13.0 and `npm --version` 11.6.2. Then answer "D-09: 1, installed".
  2. Approve running all frontend tooling inside the pinned `node:24.13.0-alpine` container instead of a host install. Caveat: Playwright's bundled Chromium does not run on Alpine (musl), so end-to-end tests would need an extra image (e.g. `mcr.microsoft.com/playwright:v1.63.0-noble`), which is itself a tech-stack addition to approve.
  3. Other (state it).
- Human response: none
- Resolution: open
