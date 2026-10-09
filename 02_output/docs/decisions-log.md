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
| D-13 | 2026-10-09T11:01:38Z | 0 | `npm audit` (npm 11.6.2): 2 Moderate, `qs` 6.15.1 (GHSA-q8mj-m7cp-5q26, GHSA-x5fp-wj9c-mxmx, GHSA-4mjr-xmp4-gh2g) via `typed-rest-client` 2.3.1 from `@stryker-mutator/core` 10.0.0 (mutation tooling, not shipped) | Recorded, not blocking (Medium); re-checked in phase 6 | pending review |
| D-14 | 2026-10-09T11:08:47Z | 1 | OQ-01 unanswered: options for students | Each option states in configuration which registration types it is available to; a form offers only options available to its type and a registration selecting any other option is rejected (stricter than all-for-all) | pending review |
| D-15 | 2026-10-09T11:08:47Z | 1 | OQ-02 unanswered: mandatory consents and wording | One mandatory consent: processing of the entered personal data for organising the conference; the set of consents and their wording come from configuration, each consent is mandatory unless configured otherwise, never preselected | pending review |
| D-16 | 2026-10-09T11:08:47Z | 1 | OQ-03 unanswered: storage succeeds, email fails | The registration stays accepted and stored (scope priority 1) and the confirmation is shown; the email failure is recorded for operators without personal data; no automatic resend | pending review |
| D-17 | 2026-10-09T11:08:47Z | 1 | OQ-04 unanswered: options per category | Selecting options is optional; the maximum per category comes from configuration and defaults to 1; more than the maximum is rejected | pending review |
| D-18 | 2026-10-09T11:08:47Z | 1 | OQ-05 unanswered: second registration with the same email | Rejected: at most one registration per email address across both types, compared after trimming and ignoring letter case | pending review |
| D-19 | 2026-10-09T11:08:47Z | 1 | OQ-06 unanswered: retention | Registrations and JSON copies are kept until 12 months after the conference, then deleted by the organizer through an operations procedure (no deletion feature; editing and admin UI are out of scope); stated in the consent text and release notes (SB-13) | pending review |
| D-20 | 2026-10-09T11:14:38Z | 2 | Phase 2 must validate contracts with a parser; no OpenAPI or JSON Schema validator is pinned in `tech-stack.md` | Dev-only tooling: `@redocly/cli` 2.62.0 run by `npx` (not installed into a project), host Python `jsonschema` 4.10.3 (already present) and the pinned `postgres:16.15-alpine` for SQL; all through `verify.sh contracts` | pending review |
| D-21 | 2026-10-09T12:06:48Z | 4 | SpotBugs (threshold Low) reported 19 rank 18-19 findings: EI_EXPOSE_REP/REP2 on Spring-injected beans and on records whose lists are already `List.copyOf`, and THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION on `SecurityConfig` (`HttpSecurity.build()` throws Exception) | Defensive copies added where missing; the remaining false positives excluded per class in `backend/spotbugs-exclude.xml` (pom `excludeFilterFile`); 0 findings after | pending review |
| D-22 | 2026-10-09T12:06:48Z | 4 | Commit 45da222 (US-003) does not compile on its own: `api/Problems.java` imports `domain/ValidationError`, which the next commit 9c91e44 adds; every later commit compiles (checked in clean worktrees) | History is not rewritten (rules.md "Commits"); recorded here instead | pending review |
| D-23 | 2026-10-09T12:06:48Z | 4 | `docker compose --env-file .env` cannot parse the repository-root `.env` (line 2 is not `KEY=value`); `.env` is the human's file and is not read or edited | `02_output/scripts/compose.sh` exports only POSTGRES_PASSWORD, ORGANIZER_EMAILS, ORGANIZER_USERNAME, ORGANIZER_PASSWORD with the `sed` method of rules.md and runs compose; the human may add `#` to that line | pending review |
| D-25 | 2026-10-09T18:29:24Z | 6 | Gitleaks `generic-api-key` on the test-only value in `AppSettingsTest.java` line 16 (commit d4bd454); matched artifact is a unit-test fixture, not a credential | Inline `gitleaks:allow`; fingerprint in `02_output/.gitleaksignore`, passed by `verify.sh`; raw report `out/logs/6_gitleaks.json` | pending review |
| D-26 | 2026-10-09T21:47:14Z | 6 | `03_statistics/usage.md` is in `docs/00_input-manifest.sha256` (phase 0 included every README section 1 file), but README section 1 says it is filled after the run; the human asked the agent to fill the panel values now | Filled on the human's request; its manifest hash is expected to differ and is excluded from the hash comparison (the manifest itself stays frozen) | pending review |

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

## D-09 follow-up
- Timestamp: 2026-10-09T11:01:38Z
- Human response: "i already had the proposed default node version. i select defaults" (received 2026-10-09T10:58:38Z)
- Resolution: option 1. Node.js v24.13.0 / npm 11.6.2 were installed under nvm (`~/.nvm/versions/node/v24.13.0`), not on the non-interactive PATH; the preflight search missed it because a zsh glob error aborted the command. Versions confirmed (`out/logs/0_node.log`); `verify.sh` prepends that nvm path.

## D-11: vitest 3.2.7 has Critical and High vulnerabilities
- Timestamp: 2026-10-09T11:01:38Z
- Phase: 0
- Trigger: `npm audit` (`out/logs/0_npm-audit.json`) on the pinned set: `vitest` 3.2.7 Critical (tinypool <2.1.2: GHSA-5gmw-xhrv-c9v3, GHSA-85c8-ppgw-ccpr, prototype pollution to RCE) and `@vitest/mocker` <4.1.11 (GHSA-82fw-gwwq-j7x9, path traversal / file read); `@vitest/coverage-v8` 3.2.7 inherits it. Real advisories on the pinned artifacts, not an identifier mismatch; test tooling only, not in the shipped bundle. No override possible: vitest 3.2.7 pins `@vitest/mocker` 3.2.7.
- Options:
  1. (proposed default) Change `vitest` and `@vitest/coverage-v8` to 4.1.11, the first fixed release. Trial in a scratch copy: installs, `tsc` passes, vitest runs, Critical/High = 0; peers fit (vite `^6`, node `>=24`, `@stryker-mutator/vitest-runner` 10.0.0 `vitest >=2`).
  2. Change both to 5.0.3 (latest, the version `npm audit fix` suggests).
  3. Keep 3.2.7 and accept the risk as dev-only tooling (needs your written acceptance; the finding stays in the release notes).
- Human response: none
- Resolution: open

## D-12: jscpd 4.3.0 has High vulnerabilities
- Timestamp: 2026-10-09T11:01:38Z
- Phase: 0
- Trigger: `npm audit`: `jscpd` 4.3.0 High via `@jscpd/finder` → `fast-glob` → `micromatch` → `braces` <=3.0.3 (GHSA-vfj7-8cjw-p6xm, stack-exhaustion DoS). All of jscpd 3.3.0-alpha.2 – 4.3.0 is affected. Duplication tooling only, not shipped.
- Options:
  1. (proposed default) Change `jscpd` to 5.4.0 (the fixed version `npm audit` names). Trial: installs, `jscpd src` runs (0 clones), no High left.
  2. Keep 4.3.0 and accept the risk as dev-only tooling.
  3. Drop jscpd and use PMD CPD (already pinned for the backend) for frontend duplication too.
- Human response: none
- Resolution: open

## D-11 and D-12 follow-up
- Timestamp: 2026-10-09T11:08:04Z
- Human response: "d11 default d12 default" (received 2026-10-09T11:07:30Z)
- Resolution: D-11 option 1 (`vitest`, `@vitest/coverage-v8` 4.1.11); D-12 option 1 (`jscpd` 5.4.0). These replace the tech-stack entries for this run. Re-audit: Critical 0, High 0, Moderate 2 (D-13); frontend build, check, test pass (`out/logs/0_*`).

## D-24: Semgrep reports HTTP Basic for the organizer export as High (F-03)
- Timestamp: 2026-10-09T18:29:24Z
- Phase: 6
- Trigger: Semgrep 1.177.0 rule `use-of-basic-authentication` (ERROR → High per `standards.md`) on `docs/02_contracts/registration-api.openapi.yaml` line 127 (`organizerBasic`). Not an identifier mismatch, so it is not a false positive under `rules.md`; accepting or downgrading it needs a human. Design and mitigations (spec §6): one organizer and one read-only operation (ASVS L1, `security-requirements.md`); credentials accepted only over HTTPS or from localhost (SR-06, enforced before authentication); BCrypt hash, password ≥ 16 characters, constant-time check (SB-03); export rate limit 10/min per client (SR-03); no session or cookie, so no CSRF surface. Evidence: `FiltersTest.sr06_*`, `AppSettingsTest.sr06_*`, AC-008-03/04, runtime DoD-P04.
- Options:
  1. (proposed default) Accept HTTP Basic over enforced HTTPS as designed; record F-03 as Low with this evidence and add a Semgrep exclusion for this rule on the contract file only.
  2. Replace Basic with a login endpoint issuing a short-lived HttpOnly, Secure, SameSite=Strict session cookie (plus CSRF protection on the login); adds a login form for organizers, about one day of work, and new tests.
  3. Other (state it, e.g. mTLS for the organizer at the reverse proxy).
- Human response: none
- Resolution: open

## D-24 follow-up
- Timestamp: 2026-10-09T21:57:29Z
- Human response: "i chose the proposed fixes" (received 2026-10-09T21:56:41Z)
- Resolution: option 1. HTTP Basic over enforced HTTPS kept; F-03 recorded as Low with the evidence above; `# nosemgrep` for this rule on line 127 of `registration-api.openapi.yaml` only. Re-scan: `out/logs/6d_semgrep.log` (remaining: F-04, accepted).
