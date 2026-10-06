# Verification report

> Written in: phase 6 · Source: `standards/`, `project/constraints.md` · Procedure: `skills/verify-release` · Agent: writes

This is a self-check by the development agent, not an independent review.

Full run: `verify.sh 06 all`, 2026-10-06T16:36Z–16:45Z, 19/19 tools exit 0 (`logs/06_*.log`); after the fixes `verify.sh 06-fix` / `06-final` (logs `06-fix_*`, `06-final_*`).

## Definition of Done

| DoD | Result | Evidence |
|---|---|---|
| DoD-01 | pass | backend 184/0 (`06_be-test`, re-run on final code `06-final_be-test`), frontend 48/0 (`06_fe-test`), e2e 10/0 (`06-fix_e2e`); 34/34 acceptance and 26/26 input hashes match (`06_manifests.log`) |
| DoD-02 | pass | spotless 0, PMD 0, CPD 0, SpotBugs 0 (`06_be-*`); prettier 0, eslint 0, tsc 0 (`06_fe-*`); semgrep high=0 (`06_semgrep`, Mediums F-03) |
| DoD-03 | pass | thresholds "record only": coverage backend 95.3% line / 90.4% branch, frontend 97.68% / 91.09%; mutation backend 94% (D-23 scope), frontend 85.1% (`06_be-mutation`, `06_fe-mutation`); survivors classified in `03_test-strategy.md` |
| DoD-04 | pass | `ArchitectureTest` 9/9: layers of spec §2, no package cycles (AR-03), placement rules, Flyway-only schema (AR-06) |
| DoD-05 | pass | Dependency-Check 0/0/0/0 (D-08, D-09 suppressions), npm audit critical 0 high 0 (F-04), semgrep high 0 (D-19), gitleaks 0 in history and tree; findings table: no open Critical or High |
| DoD-06 | pass | runtime demonstration below (`06_runtime-demo.log`, DEMO PASS) and e2e on the compose stack |
| DoD-07 | pass | traceability below: 57/57 ACs have ≥ 1 test and ≥ 1 commit |
| DoD-08 | pass | `logs/07_clean-checkout.log`: clone of 7ba4437 without `.env`; backend build, check, test 184/0; frontend `npm ci`, build, test 48/0, check; stack from the checkout's compose file: page, form config, Mailpit 200, export 401; e2e 10/0. Fixed on the way: frontend README unformatted (fixed `6fbd69f`); first Quick-start attempt returned HTTP 000 (transient, compose re-run exit 0, `07_co-compose.log`) |
| DoD-09 | pass | `docs/release-notes.md` "Before production": real reCAPTCHA keys, real SMTP delivery, TLS/reverse proxy/`/api` routing, product-owner inputs, F-03, F-04 — separate from what this run verified |
| DoD-10 | pass | D-01..D-23 each have a resolution or "pending review" (`decisions-log.md`); input manifest 26/26 match |
| DoD-11 | pass | evidence checks below |
| DoD-P01 | pass | external `e2e14cac-…` and student `a53ac4ac-…` registrations HTTP 201 (`06_runtime-demo.log`) |
| DoD-P02 | pass | both rows in `registration` with their `json_copy_file`; both files listed on the `json-copies` volume, before and after recreation |
| DoD-P03 | pass | Mailpit API: participant email per registration; organizer email with attachment `registration-<id>.json` per registration |
| DoD-P04 | pass | export HTTP 401 without credentials; HTTP 200 with organizer credentials, workbook opened: sheet "Registrations", demo rows present, no formula cells |
| DoD-P05 | pass | `AntiAutomationLiveAcceptanceTest` 5/5 against `MockVerificationServer` incl. rejected token and unavailable service (after D-21) |

### Evidence checks (DoD-11)

| Check | Result |
|---|---|
| Phase 3 commits add no production code; freeze commit holds only the manifest; its files were committed earlier in phase 3 | pass: `git diff fd43d11 bf86f29` touches no `src/main` or production `frontend/src` file; `bf86f29` = `docs/03_acceptance-manifest.sha256` only (re-frozen once with approval, D-21, `dad715c`) |
| Phase 4 has ≥ 1 commit per story naming its id | pass: `logs/04_commits-since-freeze.log`, US-001..US-008 each ≥ 1 |
| No commit exceeds the size guide without a stated reason | fail → F-07 (Low) |
| Every manifest hash matches | pass (60/60) |
| Every decision has a resolution or is pending review | pass |

## Security controls

| Item | Implemented in | Checked by |
|---|---|---|
| SR-01 | `integration/AntiAutomationVerifier` (siteverify, token checked before validation) | `AntiAutomationLiveAcceptanceTest`, AC-001-13, AC-002-11 tests, `AntiAutomationVerifierTest` |
| SR-02 | `config/StartupGuard` (test mode only in local/test; keys required) | `StartupGuardTest`; `AntiAutomationLive…` test-mode token refused |
| SR-03 | `web/RequestLimitsFilter`, `FixedWindowRateLimiter`, `RegistrationController.readLimited` | `SecurityControlsIntegrationTest` (429 + Retry-After, 413), `RegistrationRequestParserTest` |
| SR-04 | `service/RegistrationValidator` options checks | AC-001-08..10, AC-002-08..09 tests, `RegistrationValidatorTest` |
| SR-05 | `integration/MailNotifier` (text/plain, strict recipient parsing), D-17 control-character rule | AC-006-03, AC-001-16 tests |
| SR-06 | `web/HttpsOnlyFilter`, `server.forward-headers-strategy=native`, `StartupGuard` | `SecurityControlsIntegrationTest` (403 over HTTP, 200 with X-Forwarded-Proto https), `StartupGuardTest` |
| SR-07 | `service/ExportService` columns, `MailNotifier` fields | AC-007-03, AC-008-04 tests |
| SB-01 | `RegistrationValidator`, `RegistrationRequestParser` | rejection ACs, unit tests |
| SB-02 | `web/SecurityConfig` (organizer role on export, deny all else) | AC-008-02/03 tests, `SecurityControls…` unknown paths 404 |
| SB-03 | `SecurityConfig` BCrypt(12), secrets only from environment | inspection; gitleaks 0 |
| SB-04 | TLS by the external reverse proxy (production); HTTPS-only organizer access | `HttpsOnlyFilter` tests; TLS itself not testable locally → release notes "Before production" |
| SB-05 | JPA parameter binding; text cells in export; React text nodes | AC-008-07 test; semgrep 0 high |
| SB-06 | `RequestLimitsFilter` | `SecurityControlsIntegrationTest` |
| SB-07 | `ApiExceptionHandler`, `server.error.include-*=never`, id-only logs | AC-004-03 test; log scan (F-02 fixed: 0 personal-data lines in `06-fix_be-test.log`, `06_backend-container.log`) |
| SB-08 | Dependency-Check, npm audit | `06_be-depscan`, `06_fe-depscan` |
| SB-09 | semgrep, gitleaks (working tree + branch history) | `06_semgrep`, `06_gitleaks` |
| SB-10 | `SecurityConfig` headers; `frontend/nginx/default.conf.template` CSP | `SecurityControls…` header test; `curl -D` on the stack (phase 4) |
| SB-11 | non-root users in both Dockerfiles; nginx unprivileged | `docker exec … id` (uid 100 / 101, phase 4) |
| SB-12 | only BR-01 fields stored (`registration-storage.sql`) | contract validation, inspection |
| SB-13 | purposes in `project/constraints.md`; retention D-16 | decisions log; release notes |
| SB-14 | consent never preselected, stored with wording and time | AC-001-11, AC-005-01 tests |

## Runtime demonstration

| Component | Environment | Flows exercised | Result |
|---|---|---|---|
| frontend + backend + PostgreSQL + Mailpit | local (`docker compose`, images rebuilt from HEAD) | health of every container (NFR-04); form config via frontend `/api` proxy; external and student registration (DoD-P01); DB rows and JSON copies (DoD-P02); participant and organizer emails (DoD-P03); export with and without organizer access (DoD-P04) | pass (`06_runtime-demo.log`, 16:46:42Z–16:47:24Z) |
| same | local, after `docker compose down` + `up` | rows and copies still present; both emails refused as duplicates (409); export still valid (NFR-02) | pass |
| frontend in a browser | local | 10 e2e flows (Chromium) | pass (`06-fix_e2e.log`) |

## Traceability

Tests: backend `…/acceptance/<Name>AcceptanceTest`, frontend `src/acceptance/<name>.test.tsx`, e2e `e2e/<name>.spec.ts`. Commits: up to four feature/test commits naming the AC or its story.

| AC | Tests | Commits |
|---|---|---|
| AC-001-01 | us001-external-form | 82b50e2, 6d659de, 5f3880e, 7c10de0 |
| AC-001-02 | Us001ExternalRegistration, us001-external-form, us001-us002-registration | 82b50e2, 6d659de, 3f6eb27, 5f3880e |
| AC-001-03 | Us001ExternalRegistration | 82b50e2, 6d659de, 3f6eb27, 5f3880e |
| AC-001-04 | Us001ExternalRegistration | 82b50e2, 6d659de, a45e2f6, 5f3880e |
| AC-001-05 | Us001ExternalRegistration | 82b50e2, 6d659de, 3f6eb27, a45e2f6 |
| AC-001-06 | Us001ExternalRegistration | 82b50e2, 6d659de, a45e2f6, 5f3880e |
| AC-001-07 | Us001ExternalRegistration, us001-us002-registration | 82b50e2, 6d659de, 3f6eb27, a45e2f6 |
| AC-001-08 | Us001ExternalRegistration | 82b50e2, 6d659de, a45e2f6, 5f3880e |
| AC-001-09 | Us001ExternalRegistration | 82b50e2, 6d659de, a45e2f6, 5f3880e |
| AC-001-10 | Us001ExternalRegistration | 82b50e2, 6d659de, a45e2f6, 5f3880e |
| AC-001-11 | us001-external-form | 82b50e2, 6d659de, a45e2f6, 5f3880e |
| AC-001-12 | Us001ExternalRegistration, us001-external-form | 82b50e2, 6d659de, a45e2f6, 5f3880e |
| AC-001-13 | AntiAutomationLive, Us001ExternalRegistration, us001-external-form | 82b50e2, 6d659de, 6ebd025, a45e2f6 |
| AC-001-14 | Us001ExternalRegistration, us001-external-form, us001-us002-registration | 82b50e2, 6d659de, 3f6eb27, a45e2f6 |
| AC-001-15 | us001-external-form, us003-us004-form | 82b50e2, 6d659de, a45e2f6, 5f3880e |
| AC-001-16 | Us001ExternalRegistration | 82b50e2, 6d659de, a45e2f6, 5f3880e |
| AC-002-01 | us002-student-form | 82b50e2, 5f3880e |
| AC-002-02 | Us002StudentRegistration, us001-us002-registration, us002-student-form | 82b50e2, 574ed50, 5f3880e |
| AC-002-03 | Us002StudentRegistration | 82b50e2, 574ed50, 5f3880e |
| AC-002-04 | Us002StudentRegistration, us002-student-form | 82b50e2, 574ed50, 5f3880e |
| AC-002-05 | Us002StudentRegistration | 82b50e2, 574ed50, 5f3880e |
| AC-002-06 | Us002StudentRegistration | 82b50e2, 574ed50, 5f3880e |
| AC-002-07 | Us002StudentRegistration | 82b50e2, 574ed50, 5f3880e |
| AC-002-08 | Us002StudentRegistration | 82b50e2, 574ed50, 5f3880e |
| AC-002-09 | Us002StudentRegistration | 82b50e2, 574ed50, 5f3880e |
| AC-002-10 | Us002StudentRegistration | 82b50e2, 574ed50, 5f3880e |
| AC-002-11 | AntiAutomationLive, Us002StudentRegistration | 82b50e2, 574ed50, 5f3880e |
| AC-002-12 | Us002StudentRegistration | 82b50e2, 574ed50, 5f3880e |
| AC-003-01 | Us003ConferenceOptions, us003-options, us003-us004-form | 82b50e2, 4e3b1a6, 381cd0e, 02dd9c4 |
| AC-003-02 | Us003ConferenceOptions, us003-us004-form | 82b50e2, 4e3b1a6, 381cd0e, 02dd9c4 |
| AC-003-03 | Us003ConferenceOptions | 82b50e2, 381cd0e, 02dd9c4 |
| AC-003-04 | Us003ConferenceOptions, us003-options, us003-us004-form | 82b50e2, 4e3b1a6, 381cd0e, 02dd9c4 |
| AC-004-01 | Us004Confirmation, us004-confirmation | 9357219, 381cd0e |
| AC-004-02 | Us004Confirmation, us003-us004-form, us004-confirmation | 7fa076c, 9357219, 381cd0e |
| AC-004-03 | Us004Confirmation, us004-confirmation | 9357219, 381cd0e |
| AC-005-01 | Us005Storage | f71093c, a377103 |
| AC-005-02 | Us005Storage | f71093c, a377103 |
| AC-005-03 | Us005Storage | f71093c, a377103 |
| AC-005-04 | Us005Storage | f71093c, a377103 |
| AC-005-05 | Us005Storage | f71093c, a377103 |
| AC-006-01 | Us006ParticipantEmail, us006-us007-emails | 68b473a, 03884fe, 01f708f |
| AC-006-02 | Us006ParticipantEmail, us006-us007-emails | 68b473a, 03884fe, 01f708f |
| AC-006-03 | Us006ParticipantEmail | 68b473a, 03884fe, 01f708f |
| AC-006-04 | Us006ParticipantEmailFailure | 68b473a, 03884fe, 01f708f |
| AC-006-05 | Us006ParticipantEmail | 68b473a, 03884fe, 01f708f |
| AC-007-01 | Us007OrganizerNotification, us006-us007-emails | c8980cf, 03884fe |
| AC-007-02 | Us007OrganizerNotification | c8980cf, 03884fe |
| AC-007-03 | Us007OrganizerNotification | c8980cf, 03884fe |
| AC-007-04 | Us007OrganizerNotification, us006-us007-emails | c8980cf, 03884fe |
| AC-007-05 | Us007OrganizerNotificationFailure | c8980cf, 03884fe |
| AC-008-01 | Us008Export, us008-export | 191f3c6, 0671868 |
| AC-008-02 | Us008Export, us008-export | 191f3c6, 0671868 |
| AC-008-03 | Us008Export | 191f3c6, 0671868 |
| AC-008-04 | Us008Export | 191f3c6, 0671868 |
| AC-008-05 | Us008Export | 191f3c6, 0671868 |
| AC-008-06 | Us008Export | 191f3c6, 0671868 |
| AC-008-07 | Us008Export | 191f3c6, 0671868 |

## Findings

| F | Severity | Source | Finding | Resolution |
|---|---|---|---|---|
| F-01 | Low | semgrep | duplicate `npm-missing-minimum-release-age` on Stryker's sandbox copy `.stryker-tmp/…/.npmrc` | fixed `37db329` (sandbox excluded) |
| F-02 | Medium | log scan | Hibernate JDBC error logger wrote PostgreSQL "Failing row contains (…)" with name and email on constraint violations (ES-07, SB-07) | fixed `535919e`; re-run 62/62, 0 personal-data lines |
| F-03 | Medium | semgrep | `npm-missing-minimum-release-age` on `frontend/.npmrc` | accepted (D-20): exact pins + committed lock file; pinned npm 11.6.2 predates the setting |
| F-04 | Medium | npm audit | `qs` 6.15.1 advisories via `@stryker-mutator/core` → `typed-rest-client` | accepted (D-10): mutation tooling only, not in any image |
| F-05 | Low | Dependency-Check | CVE-2025-7962 on angus-activation (was High) and CVE-2025-15104 on hibernate-validator: CPE false positives | accepted, suppressed with evidence (D-08 human-approved, D-09) |
| F-06 | Low | semgrep | Basic authentication on the export (was High) | accepted with mitigations (D-19 human-approved) |
| F-07 | Low | commit history | `3f6eb27` (457 lines), `4e3b1a6` (18 files, 819 lines), `7c10de0` (473 lines) exceed the size guide without a stated reason | accepted: history is not rewritten (`rules.md`) |
| F-08 | Low | commit history | `68b473a` mixes `decisions-log.md` (D-21 record) into a feat commit | accepted: history is not rewritten |
| F-09 | Low | Stryker | 63 surviving frontend mutants, mostly UI rendering details in `AntiAutomation.tsx`, `RegistrationForm.tsx`, `App.tsx`; some (e.g. token reset limited to CAPTCHA_FAILED) a component test could catch | open, record only (threshold "record only") |

## Fix loops

| Loop | Findings | Change | Re-verification |
|---|---|---|---|
| 1 | F-01 | `scripts/verify.sh`: `--exclude .stryker-tmp`; stale sandbox removed | `verify.sh 06 semgrep`: medium 2 → 1 |
| 2 | F-02 | `application.yml`: `org.hibernate.orm.jdbc.error` and `SqlExceptionHelper` off | `verify.sh 06-fix be-test` Us001/Us002/Us005 62/0, log scan 0; stack rebuilt, `06-fix e2e` 10/0; full `06-final` run |
