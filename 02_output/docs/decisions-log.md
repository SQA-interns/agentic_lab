# Decisions log

> Written in: every phase · Format: `general/rules.md` ("Decision log format") · Agent: appends only

Standing instruction from the human at session start: "do not ask for my permission for anything. do what you feel is recommended and record divergence in metrics." Blocking decisions below are resolved under it (Human response cites it) and listed as divergences in `03_statistics/run-log.json`.

## Non-blocking

| D | Timestamp | Phase | Trigger | Choice | Status |
|---|---|---|---|---|---|
| D-01 | 2026-10-06T18:03:47Z | 0 | Starting commit b6c9dc6 is a detached HEAD; commits would be unreferenced | Work on branch `run/tanej-01_opus5.5_sdd_tanej-1.0` created at b6c9dc6 | pending review |
| D-02 | 2026-10-06T18:03:47Z | 0 | `node`/`npm` not on PATH (tech-stack `node` 24.13.0, npm 11.6.2); found already installed under `~/.nvm/versions/node/v24.13.0` (reports v24.13.0 / 11.6.2) | Use that installation by PATH in `verify.sh` and READMEs; nothing installed | pending review |
| D-03 | 2026-10-06T18:03:47Z | 0 | Tech-stack `java` Temurin 21.0.10+7; host has Ubuntu OpenJDK 21.0.12.1 (runs) | Build and test with host JDK 21; runtime image `eclipse-temurin:21.0.10_7-jre-alpine` reports Temurin-21.0.10+7 exactly; pin stays authoritative | pending review |
| D-04 | 2026-10-06T18:03:47Z | 0 | Tech-stack `docker` 29.8.0 / `compose` 5.5.1; host reports Engine 29.3.1, Compose v5.1.1 (both run) | Use host versions; no host upgrade | pending review |
| D-05 | 2026-10-06T18:03:47Z | 0 | `aldanial/cloc:2.10` image reports `1.98` | Keep the pinned tag | pending review |
| D-08 | 2026-10-06T18:03:47Z | 0 | Dependency-Check CVE-2025-7962 (CVSS 7.5) matched `pkg:maven/org.eclipse.angus/angus-activation@2.0.3`; CVE is Jakarta Mail before 2.0.2 SMTP injection (KP-07). Shipped mail implementation is `angus-mail` 2.0.5 (fixed) | False positive (identifier mismatch); suppressed in `backend/dependency-check-suppressions.xml`; raw report `out/logs/0_backend-depscan.log` | pending review |
| D-09 | 2026-10-06T18:03:47Z | 0 | Dependency-Check CVE-2025-15104 (CVSS 5.3) matched `hibernate-validator@9.1.3.Final`; CVE is for Nu Html Checker (validator.nu) | False positive (product-name match); suppressed | pending review |
| D-11 | 2026-10-06T18:03:47Z | 0 | gitleaks over full history reports 80 `generic-api-key`/`curl-auth-user` matches, all in files of commits before start commit b6c9dc6 (earlier runs' logs and tests; none in this tree) | Scan scope `<startCommit>..HEAD` (this run's commits) | pending review |
| D-12 | 2026-10-06T18:05:33Z | 1 | OQ-01 (US-002 "activities available to students") unanswered | Each option's configuration states which registration types it is offered to; an option not offered to a type is not shown to it and is rejected for it. Shipped configuration offers every option to both types | pending review |
| D-13 | 2026-10-06T18:05:33Z | 1 | OQ-02 unanswered (consents) | One mandatory consent: processing of the submitted personal data for organising the conference; wording from configuration; shown unchecked; registration rejected without it | pending review |
| D-14 | 2026-10-06T18:05:33Z | 1 | OQ-03 unanswered (storage ok, email fails) | Storage first (scope priority 1): the registration stays accepted and stored and the confirmation is shown; the failed email is logged without personal data for the organizer to follow up | pending review |
| D-15 | 2026-10-06T18:05:33Z | 1 | OQ-04 unanswered (options per category) | No per-category limit: any number of distinct active options in each category; each option counts once | pending review |
| D-16 | 2026-10-06T18:05:33Z | 1 | OQ-05 unanswered (same email twice) | Reject a second registration whose email equals a stored one (trimmed, case-insensitive), with a message to contact the organizers | pending review |
| D-17 | 2026-10-06T18:05:33Z | 1 | OQ-06 unanswered (retention) | Database rows and JSON copies kept 12 months after the conference, then deleted by the operator (manual procedure in the backend README); no automated deletion (no story requires it) | pending review |
| D-18 | 2026-10-06T18:11:13Z | 2 | Phase 2 needs parsers for contracts; none listed in tech-stack | Dev-only validators: container `redocly/cli:2.57.0` (OpenAPI lint) and host `python3-jsonschema` 4.10.3 (already installed, JSON Schema 2020-12); SQL applied to `postgres:16.15-alpine`. Run via `verify.sh <phase> contracts`; not shipped | pending review |
| D-19 | 2026-10-06T18:11:13Z | 2 | Backend image must build from a clean checkout; tech-stack lists only the JRE image | Build stage `eclipse-temurin:21.0.10_7-jdk-alpine` (same pinned JDK release, jdk variant); runtime stays `eclipse-temurin:21.0.10_7-jre-alpine`; scanned in phase 6 | pending review |

## Blocking

## D-06: `.env` missing
- Timestamp: 2026-10-06T18:03:47Z
- Phase: 0
- Trigger: preflight step 4: `.env` does not exist in the repository root (checked with `ls` and with the `sed … | grep -q .` presence check); keys POSTGRES_PASSWORD, ORGANIZER_USERNAME, ORGANIZER_PASSWORD, ORGANIZER_EMAILS, NVD_API_KEY are marked "provided".
- Options: 1. (default) Agent generates `.env` from `secrets.env.example` with random local values (32/24-char random passwords, username `organizer`, recipient `organizer@konferenca.local` caught by Mailpit), never displayed; reCAPTCHA and SMTP left empty (test-mode only / not needed). 2. Stop until the human creates `.env`.
- Human response: standing instruction at session start (2026-10-06T18:03:47Z): do not ask, do what is recommended.
- Resolution: option 1. Divergence: `.env` is not in README section 3; it is git-ignored and local only.

## D-07: `NVD_API_KEY` not available
- Timestamp: 2026-10-06T18:03:47Z
- Phase: 0
- Trigger: preflight step 4/6: `NVD_API_KEY` empty; the agent cannot obtain one. The local Dependency-Check NVD database (`~/.m2/repository/org/owasp/dependency-check-data/11.0`) was last updated 2026-10-06T16:44:34Z.
- Options: 1. (default) Run Dependency-Check against the cached database with `-DautoUpdate=false` (phases 0 and 6); `verify.sh` switches to the key automatically if one is added later. 2. Run without a key and let it update over the public rate limit (hours, KP-07). 3. Stop until the human adds the key.
- Human response: standing instruction at session start (2026-10-06T18:03:47Z).
- Resolution: option 1. Vulnerability data is as of 2026-10-06T16:44:34Z.

## D-10: Critical/High npm advisories in dev-only tooling
- Timestamp: 2026-10-06T18:03:47Z
- Phase: 0
- Trigger: preflight step 6, `npm audit` (full tree): vitest 3.2.7 Critical (tinypool GHSA-5gmw-xhrv-c9v3, GHSA-85c8-ppgw-ccpr; @vitest/mocker GHSA-82fw-gwwq-j7x9), jscpd 4.3.0 High (braces GHSA-vfj7-8cjw-p6xm via micromatch/fast-glob). Fixes only in major versions (vitest 5.0.3, jscpd 5.4.0). `npm audit --omit=dev` (shipped dependencies): 0 vulnerabilities. Neither package is in the shipped artifact (nginx serves only `dist/`).
- Options: 1. (default) Keep the pins; accept for dev/test tooling run locally on own code only; gate SB-08 on `npm audit --omit=dev`; keep the full audit in `out/logs/<phase>_frontend-depscan-all.json`; list in release notes. 2. Upgrade vitest to 5.0.3 (+ @vitest/coverage-v8, Stryker runner compatibility unknown) and jscpd to 5.4.0 (tech-stack change).
- Human response: standing instruction at session start (2026-10-06T18:03:47Z).
- Resolution: option 1.
