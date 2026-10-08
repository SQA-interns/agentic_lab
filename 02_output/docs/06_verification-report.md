# Verification report

> Written in: phase 6 · Source: `general/standards.md`, `project/02_design/*` · Procedure: `general/phases/6-verify.md` · Agent: writes

This is a self-check by the development agent, not an independent review.

Run: `scripts/verify.sh 06` (all tools, `logs/06_*`), `scripts/runtime-demo.sh` (`logs/06_runtime-demo.log`), hash check `logs/06_hash-check.log`, leak check `logs/06_env-leak-check.log`.

## Definition of Done

| DoD | Result | Evidence |
|---|---|---|
| DoD-01 | pass | backend 231/231 (`logs/06_be-test.log`), frontend 56/56 (`logs/06_fe-test.log`), e2e 6/6 (`logs/06_fe-e2e.log`); acceptance manifest 34/34 and input manifest 27/27 match (`logs/06_hash-check.log`) |
| DoD-02 | pass | spotless, PMD, CPD, SpotBugs 0; prettier, ESLint, tsc 0; semgrep see F-01, F-04 (`logs/06_*`) |
| DoD-03 | pass | coverage backend line 97.3%, branch 93.0%, frontend line 97.07%, branch 91.26%; mutation backend 323/423 killed (76%, unit and integration tests), frontend 347/466 killed (74.46%, command runner, D-27); thresholds: record only; validation survivors classified (F-08, F-12) |
| DoD-04 | pass | `ArchitectureTest`: declared layers (AR-02), no cycles (AR-03), domain framework-free |
| DoD-05 | pass | F-01 downgraded by the human (D-26); F-02 High accepted by the human (D-10); Dependency-Check 0 after D-08/D-09; `npm audit --omit=dev` 0; gitleaks 0 after D-25 |
| DoD-06 | pass | `logs/06_runtime-demo.log`: fresh local stack healthy, both registrations, storage, emails, export, recreate |
| DoD-07 | pass | traceability table below: 46/46 ACs have tests and commits |
| DoD-08 | phase 7 | READMEs and clean-checkout log are produced in phase 7 (D-24) |
| DoD-09 | phase 7 | release notes are produced in phase 7 (D-24) |
| DoD-10 | pass | D-01..D-27 resolved or pending review; input manifest matches, protected files unchanged since `b6c9dc6` |
| DoD-11 | pass with F-07 | phase 3 added no production source and the freeze commit holds only the manifest (`logs/06_phase3-evidence.log`); every story has a phase 4 commit (`logs/04_commits-since-freeze.txt`); 7 commits exceed the size guide without a stated reason (F-07, `logs/06_commit-sizes.log`) |
| DoD-P01 | pass | external and student registration 201 on the running stack |
| DoD-P02 | pass | 2 rows in PostgreSQL with options; 2 JSON copies on the volume |
| DoD-P03 | pass | 2 participant and 2 organizer emails (1 attachment each) in Mailpit |
| DoD-P04 | pass | export 200 xlsx (zip magic); without credentials 401; wrong password 401 |
| DoD-P05 | pass | `RecaptchaVerifierTest` (accepted, rejected, 500, malformed, unreachable) and backend acceptance tests against the mocked verify endpoint (`AC-001-14`, `AC-002-10`) |

## Runtime demonstration

| Component | Environment | Flows exercised | Result |
|---|---|---|---|
| all (compose) | local | fresh `up --build --wait`; all 4 services healthy; liveness and readiness UP (NFR-04) | pass |
| backend | local | registrations external and student 201, invalid token 400, duplicate email 409; rows, options and copies present; export 200 / 401 / 401 | pass |
| backend | local | processes: backend UID 10001, frontend UID 101 (SB-11); backend log has 0 lines with participant data (ES-07) | pass |
| frontend | local | `/` and `/api/form` through nginx; 4 security headers once each, no duplicates (KP-02) | pass |
| all | local | `down` then `up`: 2 rows, 2 copies, export 200, same email 409 (NFR-02) | pass |
| Mailpit | local | 4 emails: subjects, recipients, attachments as in `emails.schema.json` | pass |

## Security and pitfall evidence

| Item | Implemented in | Checked by |
|---|---|---|
| SB-01 | `RegistrationValidator`, strict JSON in `RegistrationController` | `RegistrationValidatorTest`, `RegistrationControllerTest`, `Us001`/`Us002` acceptance |
| SB-02 | `SecurityConfig` (permit list, deny all else) | `Us008` AC-008-02/03, runtime 401 |
| SB-03 | BCrypt in `SecurityConfig`, secrets from environment, `StartupChecks` | `StartupChecksTest`, inspection |
| SB-04 | external TLS proxy; `HttpsOnlyFilter` | `FiltersTest.httpsOnly`; production TLS is a manual test (release notes) |
| SB-05 | JPA parameters, React escaping, plain-text emails, POI string cells | `ExcelWriterTest.formulaLikeTextStaysText`, AC-008-04, semgrep |
| SB-06 | `RateLimitFilter` (before authentication) | `FiltersTest` (limit, reset, eviction) |
| SB-07 | `ProblemHandler`, `server.error.*`, id-only logging | `RegistrationControllerTest.failuresHideInternals`, AC-004-02, AC-006-03, runtime log check |
| SB-08 | Dependency-Check, `npm audit` | `logs/06_be-depcheck.log` 0; `logs/06_fe-audit-prod.log` 0; F-02, F-03 dev only |
| SB-09 | semgrep, gitleaks, SpotBugs, PMD | `logs/06_semgrep.log`, `logs/06_gitleaks.log`, F-01, F-04, F-06 |
| SB-10 | `SecurityConfig` headers; `frontend/security-headers.conf` | runtime header count; e2e |
| SB-11 | Dockerfiles `USER 10001` / `USER nginx` | runtime `id -u` |
| SB-12 | fixed fields only (`database.sql`, `registration-copy.schema.json`) | inspection, contracts |
| SB-13 | `RetentionService` (D-16) | `RetentionServiceTest`, AC-005-05 |
| SB-14 | consent never preselected, stored with wording and time | AC-001-12/13, AC-005-01 |
| SR-01 | `RecaptchaVerifier` (fail closed), every registration | AC-001-14, AC-002-10, `RecaptchaVerifierTest` |
| SR-02 | `StartupChecks` (production refuses test mode and empty keys) | `StartupChecksTest` |
| SR-03 | `RateLimitFilter`, `RequestSizeFilter` | `FiltersTest` |
| SR-04 | `RegistrationValidator` options against the active catalogue | AC-001-09/10, AC-002-07/08 |
| SR-05 | control characters rejected; fixed subjects; plain text | `TextTest`, `RegistrationValidatorTest.controlCharactersAreRejected`, `NotificationServiceTest` |
| SR-06 | `HttpsOnlyFilter`, `StartupChecks` | `FiltersTest.httpsOnly`, `StartupChecksTest` |
| SR-07 | `ExcelWriter` columns, copy and email schemas | `ExcelWriterTest`, AC-005-02 (no token in copy) |
| KP-01 | `frontend/nginx.conf` `proxy_set_header Host backend` | inspection, semgrep (no nginx host finding) |
| KP-02 | headers only in static locations via include | runtime header count |
| KP-03 | `Text.trim` (White_Space + U+FEFF), `clean` in frontend | `TextTest`, AC-001-04/05, e2e NBSP journey |
| KP-04 | `.gitattributes` LF; hashes on LF content | hash check |
| KP-05 | longest path under `02_output/` is 115 (from the repository root) characters | inspection |
| KP-06 | not needed: Testcontainers reached Docker without settings | test runs |
| KP-07 | identifier check before classifying | D-08, D-09 |
| KP-08 | e2e global setup starts and removes its own compose project | `logs/06_fe-e2e.log`, `tests/e2e/global-setup.ts` |

## Traceability

| AC | Tests | Commits |
|---|---|---|
| AC-001-01 | BE `Us001ExternalRegistrationAcceptanceTest`, E2E `us001-external`, FE `us001-external-form` | 39db43f, d216b7f, dab9619, e9650ca |
| AC-001-02 | FE `us001-external-form` | 39db43f, d216b7f, dab9619, e9650ca |
| AC-001-03 | BE `Us001ExternalRegistrationAcceptanceTest`, FE `us001-external-form` | 39db43f, d216b7f, dab9619, e9650ca |
| AC-001-04 | BE `Us001ExternalRegistrationAcceptanceTest`, E2E `us001-external`, FE `us001-external-form` | 39db43f, d216b7f, dab9619, e9650ca |
| AC-001-05 | BE `Us001ExternalRegistrationAcceptanceTest`, E2E `us001-external`, FE `us001-external-form` | 39db43f, d216b7f, dab9619, e9650ca |
| AC-001-06 | BE `Us001ExternalRegistrationAcceptanceTest`, FE `us001-external-form` | 39db43f, d216b7f, dab9619, e9650ca |
| AC-001-07 | BE `Us001ExternalRegistrationAcceptanceTest`, FE `us001-external-form` | 39db43f, d216b7f, dab9619, e9650ca |
| AC-001-08 | BE `Us001ExternalRegistrationAcceptanceTest`, FE `us001-external-form` | 39db43f, d216b7f, dab9619, e9650ca |
| AC-001-09 | BE `Us001ExternalRegistrationAcceptanceTest` | 39db43f, d216b7f, dab9619, e9650ca |
| AC-001-10 | BE `Us001ExternalRegistrationAcceptanceTest` | 39db43f, d216b7f, dab9619, e9650ca |
| AC-001-11 | BE `AcceptanceStack`, BE `Us001ExternalRegistrationAcceptanceTest` | 39db43f, d216b7f, dab9619, e9650ca |
| AC-001-12 | BE `Us001ExternalRegistrationAcceptanceTest`, E2E `us001-external`, FE `us001-external-form` | 39db43f, d216b7f, dab9619, e9650ca |
| AC-001-13 | BE `Us001ExternalRegistrationAcceptanceTest`, FE `us001-external-form` | 39db43f, d216b7f, dab9619, e9650ca |
| AC-001-14 | BE `Us001ExternalRegistrationAcceptanceTest`, FE `us001-external-form` | 39db43f, d216b7f, dab9619, e9650ca |
| AC-001-15 | BE `Us001ExternalRegistrationAcceptanceTest`, FE `us001-external-form` | 39db43f, d216b7f, dab9619, e9650ca |
| AC-002-01 | BE `Us002StudentRegistrationAcceptanceTest`, E2E `us002-student`, FE `us002-student-form` | 39db43f, dab9619, d64df2b |
| AC-002-02 | FE `us002-student-form` | 39db43f, dab9619, d64df2b |
| AC-002-03 | BE `Us002StudentRegistrationAcceptanceTest`, FE `us002-student-form` | 39db43f, dab9619, d64df2b |
| AC-002-04 | BE `Us002StudentRegistrationAcceptanceTest` | 39db43f, dab9619, d64df2b |
| AC-002-05 | BE `Us002StudentRegistrationAcceptanceTest` | 39db43f, dab9619, d64df2b |
| AC-002-06 | BE `Us002StudentRegistrationAcceptanceTest`, FE `us002-student-form` | 39db43f, dab9619, d64df2b |
| AC-002-07 | BE `Us002StudentRegistrationAcceptanceTest` | 39db43f, dab9619, d64df2b |
| AC-002-08 | BE `Us002StudentRegistrationAcceptanceTest` | 39db43f, dab9619, d64df2b |
| AC-002-09 | BE `Us002StudentRegistrationAcceptanceTest` | 39db43f, dab9619, d64df2b |
| AC-002-10 | BE `Us002StudentRegistrationAcceptanceTest` | 39db43f, dab9619, d64df2b |
| AC-003-01 | BE `Us003ConfigurableOptionsAcceptanceTest` | f261470, 3d9001f |
| AC-003-02 | BE `Us003ConfigurableOptionsAcceptanceTest` | f261470, 3d9001f |
| AC-003-03 | BE `Us003ConfigurableOptionsAcceptanceTest` | f261470, 3d9001f |
| AC-004-01 | BE `Us004ConfirmationAcceptanceTest`, FE `us004-confirmation` | 3f77e55, dab9619, d7dd172 |
| AC-004-02 | BE `Us004ConfirmationAcceptanceTest`, FE `us004-confirmation` | 3f77e55, dab9619, d7dd172 |
| AC-005-01 | BE `Us005StorageAcceptanceTest` | dea133d, 968b272, a3c1e26 |
| AC-005-02 | BE `Us005StorageAcceptanceTest` | dea133d, 968b272, a3c1e26 |
| AC-005-03 | BE `JsonCopyStoreTest`, BE `RegistrationServiceTest`, BE `Us005StorageAcceptanceTest` | dea133d, 968b272, a3c1e26 |
| AC-005-04 | BE `Us005StorageAcceptanceTest`, E2E `us005-storage` | dea133d, 968b272, a3c1e26 |
| AC-005-05 | BE `Db`, BE `Us005StorageAcceptanceTest` | dea133d, 968b272, a3c1e26 |
| AC-006-01 | BE `Us006ParticipantEmailAcceptanceTest`, E2E `us001-external` | 42e9c2b, 3b46a0f |
| AC-006-02 | BE `Us006ParticipantEmailAcceptanceTest` | 42e9c2b, 3b46a0f |
| AC-006-03 | BE `Us006ParticipantEmailAcceptanceTest` | 42e9c2b, 3b46a0f |
| AC-007-01 | BE `Us007OrganizerNotificationAcceptanceTest`, E2E `us001-external` | 42e9c2b, f55808f |
| AC-007-02 | BE `Us007OrganizerNotificationAcceptanceTest`, E2E `us001-external` | 42e9c2b, f55808f |
| AC-007-03 | BE `Us007OrganizerNotificationAcceptanceTest` | 42e9c2b, f55808f |
| AC-007-04 | BE `Us007OrganizerNotificationAcceptanceTest` | 42e9c2b, f55808f |
| AC-008-01 | BE `Us008ExportAcceptanceTest`, E2E `us008-export` | 58ebbc9, 52ea111 |
| AC-008-02 | BE `Us008ExportAcceptanceTest`, E2E `us008-export` | 58ebbc9, 52ea111 |
| AC-008-03 | BE `Us008ExportAcceptanceTest` | 58ebbc9, 52ea111 |
| AC-008-04 | BE `Us008ExportAcceptanceTest` | 58ebbc9, 52ea111 |

## Findings

| F | Severity | Source | Finding | Resolution |
|---|---|---|---|---|
| F-01 | High → Low | semgrep `use-of-basic-authentication` | `api.openapi.yaml:104` organizer export uses HTTP Basic | downgraded with evidence, approved by the human (D-26); suppression names D-26 (`58a6001`) |
| F-02 | High | npm audit GHSA-vfj7-8cjw-p6xm | braces 3.0.3 via jscpd 4.3.0 (dev tooling, not shipped) | accepted by the human (D-10) |
| F-03 | Medium | npm audit (qs) | qs via @stryker-mutator/core → typed-rest-client (dev tooling, not shipped) | accepted: dev only, `npm audit --omit=dev` 0 |
| F-04 | Medium | semgrep `npm-missing-minimum-release-age` | `frontend/.npmrc` sets no minimum release age | accepted: npm 11.6.2 has no such setting; exact pins and lock file integrity hashes |
| F-05 | Medium | Stryker | frontend mutation score not measurable: vitest-runner 10.0.0 does not activate mutants under vitest 5.0.3 | fixed: command runner (D-27), 60 s timeout (`99ccb7f`, `61d3704`); score 74.46% |
| F-06 | Low | gitleaks | 15 matches: test constant and ephemeral generated passwords in phase 3 logs | false positive, suppressed by fingerprint (D-25) |
| F-07 | Low | commit history | 7 commits exceed the 15-file / 400-line guide without a stated reason | accepted: history is not rewritten (`logs/06_commit-sizes.log`) |
| F-08 | Low | PIT | surviving mutants in validation/security: email length limits (254, 63) and rate-limit eviction untested | fixed `f24bf67`; remaining 2 survivors are equivalent |
| F-09 | Low | PIT | `RecaptchaVerifier` read timeout not covered by a unit test | accepted: fail-closed path covered (unreachable endpoint); a timeout test adds 5 s |
| F-10 | Low | runtime demo | first demo run printed the organizer address from `.env` into an uncommitted log | fixed: log deleted, script masks non-test addresses, leak check clean |
| F-12 | Low | Stryker | 21 survivors in `frontend/src/validation.ts`: message strings, regex anchors, email length boundaries, option counting | accepted: browser-side duplicates of backend rules, which are authoritative and tested (SB-01) |
| F-11 | Low | test harness | frozen `AcceptanceStack.newTempDir` depends on an earlier test starting the stack | mitigated by alphabetical run order (D-22); frozen file unchanged |

Other surviving mutants: equivalent or logging only (`RetentionService` count log, `JsonCopyStore` `fsync`, header and encoding writes), or killed only by acceptance tests that PIT excludes (`RegistrationValidator` empty-string returns, persistence, `SecurityConfig`).

## Fix loops

| Loop | Findings | Change | Re-verification |
|---|---|---|---|
| 1 | F-01 | D-26 approved: `# nosemgrep` with the decision id in `api.openapi.yaml` | `verify.sh 06 semgrep contracts`: semgrep only F-04 (Medium); contracts 7/7 |
| 2 | F-05 | Stryker command runner; then timeout 60 s, 3 workers after a first run where all 466 mutants timed out | `verify.sh 06 fe-mutation`: 347 killed, 119 survived, 0 timeouts |
