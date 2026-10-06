# Verification report

> Written in: phase 6 · Source: `general/standards.md`, `project/02_design/*` · Procedure: `general/phases/6-verify.md` · Agent: writes

This is a self-check by the development agent, not an independent review.

## Definition of Done

| DoD | Result | Evidence |
|---|---|---|
| DoD-01 | pass | 204 backend + 69 frontend + 6 e2e = 279 passed, 0 failed (`out/logs/6_backend-test.log`, `6_frontend-test.log`, `6_frontend-e2e.log`); `sha256sum -c` of `03_acceptance-manifest.sha256`: 27/27 match (manifest re-hashed once under D-20) |
| DoD-02 | pass | spotless 0, prettier 0, ESLint 0, tsc 0, SpotBugs 0 (after F-05/F-06), PMD 0 / CPD 0 (after F-07); Semgrep 1 ERROR accepted as Low under D-22 (`out/logs/6_*.log`) |
| DoD-03 | pass (record only) | backend JaCoCo line 96.3 % / branch 92.5 %; frontend V8 line 100 % / branch 97.08 %; mutation PIT 83 % (281/340, unit scope), Stryker 82.43 %; survivors triaged F-08/F-09 |
| DoD-04 | pass | `ArchitectureTest` (layers of spec §2, domain purity, entities, no slice cycles) green; frontend `architecture.test.ts` (AR-01, AR-07) green |
| DoD-05 | pass | Dependency-Check 0 (FP D-08, D-09; NVD cache D-07); `npm audit --omit=dev` 0 (dev-only F-11 accepted D-10); gitleaks 0 (F-03, D-23); Semgrep High downgraded D-22; no open Critical/High |
| DoD-06 | pass | `out/scripts/runtime-demo.sh` → `out/logs/6_runtime-demo.log`: 34 PASS, 0 FAIL |
| DoD-07 | pass | traceability table below: 48/48 AC with tests and commits |
| DoD-08 | phase 7 | clone log written in phase 7 (`out/logs/7_clone.log`) |
| DoD-09 | phase 7 | `docs/release-notes.md` "Must be tested manually" |
| DoD-10 | pass | every decision D-01..D-23 resolved or pending review (`decisions-log.md`); input manifest 27/27 match |
| DoD-11 | pass | Evidence section below |
| DoD-P01 | pass | runtime demo: external and student registration → 201 |
| DoD-P02 | pass | runtime demo: `psql` count 2 rows with options; 2 files in `/data/json-copies` |
| DoD-P03 | pass | runtime demo: Mailpit API 2 participant + 2 organizer mails, organizer mails with 1 JSON attachment |
| DoD-P04 | pass | runtime demo: organizer 200 + workbook opened (č/š/ž kept); anonymous 401; wrong password 401 |
| DoD-P05 | pass | `AntiAutomationAcceptanceTest` (stub verify endpoint, rejected token → 400 RECAPTCHA_FAILED), `RecaptchaVerifierTest` (success/false/500/invalid JSON/unreachable) |

## Evidence (DoD-11)

| Check | Result |
|---|---|
| Phase 3 commits add no production code beyond the skeleton | `git log b6c9dc6..647d0ca -- */src/main frontend/src`: only skeleton commits 7eb4ba1, a1b4219 |
| Freeze commit 647d0ca adds only the manifest; listed files committed earlier | 1 file changed (manifest) |
| Phase 4: ≥1 commit per story naming its id | `out/logs/4_commits.log` (US-001..US-008 named; layered commit order, divergence recorded) |
| Commit size guide | 3 commits over 400 lines without stated reason → F-12 |
| Manifest hashes | input 27/27, acceptance 27/27 match |
| `.env` leak check | files 0, commit messages 0 (after fix loop 1, F-01) |
| Decisions resolved or pending review | yes (D-01..D-23) |

## Security, requirement and pitfall evidence

| Item | Implemented in | Checked by |
|---|---|---|
| SB-01 | `RegistrationValidator`, `Text` | ExternalRegistration/Student ATs, `RegistrationValidatorTest` |
| SB-02 | `SecurityConfiguration` (`/api/admin/**` role ORGANIZER, rest denyAll) | RegistrationExportAT AC-008-02/03, `RunningApplicationIntegrationTest` |
| SB-03 | BCrypt in-memory user; secrets only from env | inspection, `StartupGuardTest` |
| SB-04 | external nginx TLS (manual); prod guard requires SMTP STARTTLS | `StartupGuardTest`; release notes manual test |
| SB-05 | JPA parameters, plain-text mail, React escaping, string cells | AC-007-03, AC-008-06 tests |
| SB-06 | `RateLimitFilter` | `FiltersTest`, `RunningApplicationIntegrationTest` (429 + Retry-After) |
| SB-07 | `ApiExceptionHandler`, `Problems`, `server.error.*=never` | AC-004-03 tests; runtime demo log scan (no submitted values, no stack traces) |
| SB-08 | Dependency-Check, npm audit | `6_backend-depscan.log`, `6_frontend-depscan.log` |
| SB-09 | Semgrep, SpotBugs, PMD, gitleaks | `6_semgrep.log`, `6_gitleaks.log` |
| SB-10 | Spring Security headers; nginx `security-headers.conf` | integration test (each header once on /api), runtime demo (/, /api, 404 asset) |
| SB-11 | non-root images, read-only root FS, no-new-privileges | runtime demo (uid ≠ 0) |
| SB-12 | only business-rules fields; `additionalProperties: false`, unknown JSON → 400 | AC-004-03 unknown property test |
| SB-13 | D-17 retention 12 months, manual deletion procedure | backend README (phase 7) |
| SB-14 | consent unchecked, must be true | AC-001-09/10, AC-002-08 |
| SR-01 | `RecaptchaVerifier` server-side verify | AntiAutomationAT, RecaptchaVerifierTest |
| SR-02 | `StartupGuard` (profile-less = production) | `ProductionStartupIntegrationTest`, `StartupGuardTest` |
| SR-03 | rate limit + `RequestSizeFilter` + nginx `client_max_body_size` | FiltersTest, AC-004-03 oversize (413) |
| SR-04 | catalog check in validator | AC-001-07/08, AC-002-05/07 |
| SR-05 | control/format characters rejected; subjects from config only; text/plain | RegistrationValidatorTest (CRLF), AC-007-03 |
| SR-06 | `HttpsOnlyFilter` + forward headers native | FiltersTest (remote plain HTTP → 403) |
| SR-07 | explicit export columns and mail fields | PoiWorkbookWriterTest (no internal columns), inspection |
| KP-01 | nginx fixed `Host backend`, `absolute_redirect off` | runtime demo (forged Host not reflected); Semgrep p/nginx 0 |
| KP-02 | headers only in static locations, none for /api | runtime demo: each header count 1 on /, /api/options, 404 asset |
| KP-03 | `Text.strip` / `rules.ts strip` incl. U+00A0 | frozen AC-001-03/04, AC-002-03, e2e AC-001-02 |
| KP-04 | `.gitattributes`; hashes on LF | manifests match |
| KP-05 | short paths (longest tracked path 116 characters, `…/infrastructure/persistence/RegistrationJpaRepository.java`) | inspection; README clone note (phase 7) |
| KP-06 | not needed: Testcontainers reached Docker | phase 3 red run without setup errors |
| KP-07 | D-08/D-09 compared matched identifier with shipped artifact | suppressions file |
| KP-08 | Playwright global setup starts project `regtest-e2e`, `down -v` after | `6_frontend-e2e.log` |

## Runtime demonstration

| Component | Environment | Flows exercised | Result |
|---|---|---|---|
| backend, frontend, postgres, mailpit | local (`docker compose`, 127.0.0.1:8090/8025) | health/readiness, headers, forged Host, external + student registration, DB rows, JSON copies, mails, export with/without credentials, log scan, `down`/`up` persistence, non-root | 34 PASS / 0 FAIL (`out/logs/6_runtime-demo.log`) |
| frontend + backend | e2e stack `regtest-e2e` (18090/18025) | browser flows of US-001/002/004/006/007/008 | 6 passed |

## Traceability

| AC-001-01 | ExternalRegistrationAT, registration (e2e), us001-external-form | 47ec235 a8243da 788e3c9 b1b27ae |
| AC-001-02 | ExternalRegistrationAT, registration (e2e), us001-external-form | 47ec235 a8243da 788e3c9 b1b27ae |
| AC-001-03 | ExternalRegistrationAT, us001-external-form | 47ec235 a8243da 788e3c9 b1b27ae |
| AC-001-04 | ExternalRegistrationAT, us001-external-form | 47ec235 a8243da 788e3c9 b1b27ae |
| AC-001-05 | ExternalRegistrationAT, us001-external-form | 47ec235 a8243da 788e3c9 b1b27ae |
| AC-001-06 | ExternalRegistrationAT, us001-external-form | 47ec235 a8243da 788e3c9 b1b27ae |
| AC-001-07 | ExternalRegistrationAT, us001-external-form | 47ec235 a8243da 788e3c9 b1b27ae |
| AC-001-08 | ExternalRegistrationAT, us001-external-form | 47ec235 a8243da 788e3c9 b1b27ae |
| AC-001-09 | ExternalRegistrationAT, us001-external-form | 47ec235 a8243da 788e3c9 b1b27ae |
| AC-001-10 | ExternalRegistrationAT, registration (e2e), us001-external-form | 47ec235 a8243da 788e3c9 b1b27ae |
| AC-001-11 | ExternalRegistrationAT, OptionGroups, registration (e2e), us001-external-form | 47ec235 a8243da 788e3c9 b1b27ae |
| AC-001-12 | ExternalRegistrationAT, us001-external-form | 47ec235 a8243da 788e3c9 b1b27ae |
| AC-001-13 | ExternalRegistrationAT, us001-external-form | 47ec235 a8243da 788e3c9 b1b27ae |
| AC-001-14 | AntiAutomationAT, ExternalRegistrationAT, us001-external-form | 47ec235 a8243da de41984 788e3c9 … |
| AC-001-15 | ExternalRegistrationAT, registration (e2e), us001-external-form | 47ec235 a8243da 788e3c9 b1b27ae |
| AC-002-01 | StudentRegistrationAT, registration (e2e), us002-student-form | 47ec235 a8243da 788e3c9 b1b27ae |
| AC-002-02 | StudentRegistrationAT, us002-student-form | 47ec235 a8243da 788e3c9 b1b27ae |
| AC-002-03 | StudentRegistrationAT, us002-student-form | 47ec235 a8243da 788e3c9 b1b27ae |
| AC-002-04 | StudentRegistrationAT, us002-student-form | 47ec235 a8243da 788e3c9 b1b27ae |
| AC-002-05 | RegistrationForm, StudentRegistrationAT, us002-student-form | 47ec235 a8243da 788e3c9 b1b27ae |
| AC-002-06 | StudentRegistrationAT, us002-student-form | 47ec235 a8243da 788e3c9 b1b27ae |
| AC-002-07 | StudentRegistrationAT | 47ec235 a8243da 788e3c9 b1b27ae |
| AC-002-08 | StudentRegistrationAT, us002-student-form | 47ec235 a8243da 788e3c9 b1b27ae |
| AC-002-09 | RegistrationForm, StudentRegistrationAT, registration (e2e), us002-student-form | 47ec235 a8243da 788e3c9 b1b27ae |
| AC-003-01 | ConfigurableOptionsAT | a8243da b33e656 b72fd69 b1b27ae |
| AC-003-02 | ConfigurableOptionsAT | a8243da b33e656 b72fd69 b1b27ae |
| AC-003-03 | ConfigurableOptionsAT | a8243da b33e656 b72fd69 b1b27ae |
| AC-003-04 | ConfigurableOptionsAT | a8243da b33e656 b72fd69 b1b27ae |
| AC-004-01 | RegistrationConfirmationAT, registration (e2e), us004-confirmation | 615543c a8243da b1b27ae |
| AC-004-02 | RegistrationConfirmationAT, registration (e2e), us004-confirmation | 615543c a8243da b1b27ae |
| AC-004-03 | RegistrationConfirmationAT, us004-confirmation | 615543c a8243da 5e7306c b1b27ae |
| AC-005-01 | RegistrationStorageAT | 4edfcc6 a8243da 837ad56 4bd5510 … |
| AC-005-02 | RegistrationStorageAT | 4edfcc6 a8243da feb90a8 837ad56 … |
| AC-005-03 | RegistrationStorageAT | 4edfcc6 a8243da 837ad56 4bd5510 … |
| AC-005-04 | RegistrationStorageAT | 4edfcc6 a8243da 837ad56 4bd5510 … |
| AC-005-05 | RegistrationStorageAT | 4edfcc6 a8243da 837ad56 4bd5510 … |
| AC-006-01 | ParticipantEmailAT, registration (e2e) | a8243da ffb07ff |
| AC-006-02 | ParticipantEmailAT | a8243da ffb07ff |
| AC-006-03 | ParticipantEmailAT | a8243da ffb07ff |
| AC-007-01 | OrganizerNotificationAT, registration (e2e) | a8243da ffb07ff |
| AC-007-02 | OrganizerNotificationAT | a8243da ffb07ff |
| AC-007-03 | OrganizerNotificationAT | a8243da ffb07ff |
| AC-008-01 | RegistrationExportAT, registration (e2e) | a8243da 58c91e2 6a48ab0 |
| AC-008-02 | RegistrationExportAT, registration (e2e) | a8243da 58c91e2 6a48ab0 |
| AC-008-03 | RegistrationExportAT, registration (e2e) | a8243da 58c91e2 6a48ab0 |
| AC-008-04 | RegistrationExportAT | a8243da 58c91e2 6a48ab0 |
| AC-008-05 | RegistrationExportAT, registration (e2e) | a8243da 58c91e2 6a48ab0 |
| AC-008-06 | RegistrationExportAT | a8243da 58c91e2 6a48ab0 |

## Findings

| F | Severity | Source | Finding | Resolution |
|---|---|---|---|---|
| F-01 | Critical | phase 6 `.env` leak check | `ORGANIZER_USERNAME` (dictionary word) and `ORGANIZER_EMAILS` values generated in D-06 appeared in 50 files / 25 commit messages; passwords did not | fixed: local values rotated to random; re-check 0/0 (loop 1) |
| F-02 | High | Semgrep ERROR | Basic authentication in `openapi.yaml` | accepted as Low, D-22 (HTTPS-only, BCrypt, rate limit, single role) |
| F-03 | Low | gitleaks | 23 matches on Spring's generated test password in phase 3 logs | false positive, D-23 |
| F-04 | Low | Semgrep | `.npmrc` without minimum release age | accepted: exact pins + committed lock file, `npm ci` |
| F-05 | Low | SpotBugs EI_EXPOSE_REP | records exposed mutable lists | fixed c54b5ec |
| F-06 | Low | SpotBugs | DI/Servlet/Spring API patterns | suppressed, D-21 (b3ee60d) |
| F-07 | Low | PMD AvoidUsingHardCodedIP | literal loopback addresses | fixed d16ed12 |
| F-08 | Low | PIT | catchable survivors (rate-limit eviction, size boundary, mail content, rows) | tests added; 75 % → 83 % |
| F-09 | Low | Stryker | catchable survivors (email anchor, type switch, token reset) | tests added; 79.21 % → 82.43 % |
| F-10 | Low | jscpd | test-code duplication 6.46 % | helper extracted; 1.09 % in tests, accepted |
| F-11 | Critical | npm audit (full tree) | vitest 3.2.7 Critical, jscpd 4.3.0 High (dev-only, not shipped) | accepted, D-10 |
| F-12 | Low | commit evidence | 290b216, 2ebc2d0, a33aec1 exceed 400 lines without stated reason | accepted (no history rewrite allowed) |

Remaining survivors (equivalent or unobservable: `fsync`, response charset, cell styling, null vs "" on rejected values; no-coverage classes covered by acceptance tests outside PIT scope) are not findings.

## Fix loops

| Loop | Findings | Change | Re-verification |
|---|---|---|---|
| 1 | F-01 | rotated local `.env` organizer username/recipient to random values | leak check: files 0, commit messages 0 |
