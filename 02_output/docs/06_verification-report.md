# Verification report

> Written in: phase 6 · Source: `standards/`, `project/constraints.md` · Procedure: `skills/verify-release` · Agent: writes

This is a self-check by the development agent, not an independent review.

## Definition of Done

| DoD | Result | Evidence |
|---|---|---|
| DoD-01 | pass | `verify.sh 6final`: backend 239/0, frontend 62/0 (`logs/6final_be-test.log`, `6final_fe-test.log`), end-to-end 6/0 (`6final_e2e.log`); both manifests recomputed, 27/27 and 30/30 match (`logs/6_manifest-check.txt`) |
| DoD-02 | pass | Spotless 0, PMD 0, SpotBugs 0, Prettier 0, ESLint 0, `tsc` 0, Semgrep 0 results (`logs/6final_be-*.log`, `6final_fe-*.log`, `6final_semgrep.log`) |
| DoD-03 | pass | thresholds "record only": backend line 97.9 % / branch 90.3 % (JaCoCo), frontend 100 % / 95.5 % (V8); mutation backend 98 % (Pitest, `6final_be-mutation.log`; Spring-wired scope 60 %, phase 5), frontend 97.2 % (Stryker, phase 5, frontend unchanged since); survivors classified in `03_test-strategy.md` |
| DoD-04 | pass | `ArchitectureTest`: declared layers (AR-02), no slice cycles (AR-03), API without persistence, mail/POI/HTTP client only in infrastructure; AR-01 by nginx proxy and relative `/api` URLs; AR-05 by AC-005-03/04 and `RegistrationServiceTest`; AR-06 by Flyway `V1` = `database.sql` with `ddl-auto: validate`; AR-04 by AC-003-04; AR-07 by the form data (`siteKey` only, `ProductionModeIntegrationTest`) |
| DoD-05 | pass | findings below: 1 High (F-02) fixed and re-verified; dependency scans 0 Critical/High (`6final_be-depscan.log`, `6final_fe-audit.log`); gitleaks 0 (tree and history); `secrets.sh leak-check` clean (`6final_leak-check.log`) |
| DoD-06 | pass | runtime demonstration below (`logs/6_runtime.log`, `6_runtime-restart.log`, `6final_runtime-headers-users.log`) |
| DoD-07 | pass | traceability table below: 63/63 ACs with at least one test and one commit (`logs/6_traceability.txt`) |
| DoD-08 | pass | clean checkout `git clone` of `f145b0f` to `C:\rc7`, no `.env`, stack stopped first: README build, test, check commands through the checkout's `verify.sh` all exit 0 (backend 239/0, frontend 62/0; `logs/7clone_*.log`); README run command with the original `secrets.sh`: all four containers healthy, page, form data and Mailpit answer 200 (`logs/7clone_run.log`); the frontend run command `npm run dev` was fixed first (F-11: dev proxy changed the Host header, so POSTs were refused as cross-origin; `3c3f27e`, `logs/7_fe-dev-check.log`) |
| DoD-09 | pass | `docs/release-notes.md` "Before production": real reCAPTCHA submission, real SMTP delivery, HTTPS and routing through the external proxy, Stryker upgrade (F-05); kept apart from the run's results |
| DoD-10 | pass | decisions D-01..D-20 each with a resolution: 14 pending review (D-01, D-02, D-05, D-06, D-09..D-18), D-03/D-04/D-19 answered by the human in D-07/D-08/D-20; inputs unchanged: input manifest 27/27, `git diff 6166314 -- 01_input AGENTS.md README.md 03_statistics/metrics.md 03_statistics/run-log.template.json` empty |
| DoD-11 | pass with F-04 | phase 3 added no production code (`git diff 90c71d4^ 510f4b9 -- */src/main frontend/src`, empty); freeze commit `510f4b9` holds only the manifest; every manifest file committed before it; phase 4 has a commit per story (`logs/4_git-log-since-freeze.txt`); 11 commits exceed the size guide without a stated reason (F-04, `logs/6_evidence-checks.txt`); manifests match; decisions resolved |
| DoD-P01 | pass | `6_runtime.log`: external and student registrations through nginx answered 201; a forged token answered 400 `captcha_failed` |
| DoD-P02 | pass | `6_runtime.log`: both rows in PostgreSQL with options, Slovenian characters intact; both `registration-<id>.json` files on the volume, owned by `app`; after `docker compose down` and `up` both rows and copies remain (`6_runtime-restart.log`, NFR-02) |
| DoD-P03 | pass | `6_runtime.log`: one confirmation per participant ("Registration confirmation: Konferenca 2026"); one organizer email per registration with `registration-<id>.json` (`application/json`) whose `registrationId` matches |
| DoD-P04 | pass | `6_runtime.log`: 401 without and with a wrong password; organizer 200, xlsx content type, `registrations.xlsx`, zip signature; workbook opened (`xl/sharedStrings.xml`): header and both registrations with č, š, ž present |
| DoD-P05 | pass | `RecaptchaCaptchaVerifierTest` (local stub: accepted, rejected, missing `success`, 500, malformed, unreachable, timeout, blank token) and `ProductionModeIntegrationTest` (test mode off, mocked endpoint: human token 201, rejected token 400 `captcha_failed`, unreachable 503) |

## Security controls

| Item | Implemented in | Checked by |
|---|---|---|
| SB-01 | `RegistrationValidator`, `RegistrationController` (type, list sizes), Jackson unknown properties off | AC-001/002 rejection tests, `RegistrationValidatorTest`, `ApiIntegrationTest`, `RequestLimitsIntegrationTest` |
| SB-02 | `SecurityConfiguration` (export `ORGANIZER`, everything else not listed denied) | AC-008-02, `ApiIntegrationTest.unknownPathsAreDeniedWithJson`, runtime 401 |
| SB-03 | organizer password kept only as BCrypt hash; secrets only from the environment | inspection `SecurityConfiguration.organizerUsers`; `ConfigurationGuardTest`; gitleaks; leak-check |
| SB-04 | TLS by the external proxy; SMTP STARTTLS required when `SMTP_TLS=true` | inspection `AppConfiguration.mailSender`; HTTPS and SMTP TLS are manual checks before production (release notes) |
| SB-05 | parameterised JPQL, plain-text emails, string workbook cells, React escaping | Semgrep 0; AC-006-03; AC-008-05 (formula stays text); `PoiWorkbookWriterTest` |
| SB-06 | `RateLimitFilter` on registration, form and export | `FiltersTest`, `FilterDetailsTest`, `EncodedPathTest`, `ProductionModeIntegrationTest` (429), runtime probe (`logs/6fix_encoded-path-probe.log`) |
| SB-07 | `ApiExceptionHandler` (generic message, error id, class names only); Hibernate error detail off; PostgreSQL terse errors | AC-004-03; runtime log scan: 0 emails, 0 names (`6_runtime.log`); `logs/6fix2_log-scan.txt` (F-03) |
| SB-08 | Dependency-Check, npm audit | 0 Critical/High (`6final_be-depscan.log`, `6final_fe-audit.log`); F-05, F-07 |
| SB-09 | Semgrep, gitleaks (tree and branch history) | `6final_semgrep.log` 0, `6final_gitleaks.log` 0/0 |
| SB-10 | Spring Security headers (API), nginx headers (page) | `ApiIntegrationTest.apiResponsesCarrySecurityHeaders`; runtime `curl -D` (`6final_runtime-headers-users.log`) |
| SB-11 | backend user `app`, frontend user `nginx`, no published backend or database port | runtime `id -un` (`6final_runtime-headers-users.log`); inspection `docker-compose.yml` |
| SB-12 | only BR-01 fields, options and consents stored; client IP not stored | inspection `database.sql`, `registration-copy.schema.json`; AC-005-02 (copy keys), AC-007-03, AC-008-05 |
| SB-13 | purposes in `project/constraints.md`; retention D-14 (no automatic deletion, operator decides) | inspection; listed for the release notes |
| SB-14 | consent checkboxes never preselected; consent text and time stored | AC-001-13, AC-002-11, AC-005-01 |
| SR-01 | `RecaptchaCaptchaVerifier`, `TestModeCaptchaVerifier`; checked before storage | AC-001-14, AC-002-12, DoD-P05 tests |
| SR-02 | `ConfigurationGuard` | `ConfigurationGuardTest`, `ProductionModeIntegrationTest.missingKeysWithoutTestModePreventStartup` |
| SR-03 | `RateLimitFilter`, `RequestSizeFilter`, Tomcat swallow size | `FiltersTest`, `FilterDetailsTest`, `EncodedPathTest`, `ApiIntegrationTest.oversizedBodyIsRefused`, `RequestLimitsIntegrationTest` |
| SR-04 | `RegistrationValidator.checkOptions` against the configured active set | AC-001-07..10, AC-002-07..09 |
| SR-05 | plain-text bodies, fixed subjects, only the validated address in headers | AC-006-03, `SmtpRegistrationNotifierTest` |
| SR-06 | `OrganizerHttpsFilter` before authentication, decoded path (F-02) | `ApiIntegrationTest.organizerAccessOverPlainHttpThroughAProxyIsRefused`, `EncodedPathIntegrationTest`, `FiltersTest` |
| SR-07 | export columns of `export-workbook.json`, organizer email of `emails.json` | AC-007-03, AC-008-05 |

## Runtime demonstration

| Component | Environment | Flows exercised | Result |
|---|---|---|---|
| all | local (`docker compose up`, `verify.sh 6 stack-up`) | containers healthy (`pg_isready`, `mailpit readyz`, backend readiness incl. database, nginx); form data through nginx 200 | pass (`6_runtime.log`, NFR-04) |
| backend + frontend | local | external and student registration with č, š, ž through nginx; forged token refused | pass (DoD-P01, NFR-01) |
| backend + PostgreSQL + volume | local | rows, options and JSON copies present; `docker compose down` and `up`; data still present | pass (DoD-P02, NFR-02) |
| backend + Mailpit | local | participant confirmations and organizer notifications with the JSON attachment | pass (DoD-P03) |
| backend | local | organizer export: refused without or with wrong credentials, workbook downloaded and opened | pass (DoD-P04) |
| backend | local | backend log free of the demonstration's names and addresses | pass (ES-07) |
| frontend | local, Chromium | the 6 end-to-end tests through the page | pass (`6final_e2e.log`) |

## Traceability

Tests list the files holding the AC id; commits are the feature commits after the freeze that name the story or AC.

| AC | Tests | Commits |
|---|---|---|
| AC-001-01 | ExternalRegistrationAcceptanceTest.java, external-registration.test.tsx, registration.e2e.ts | f122605, c3442f5, 04c65ee, d69a030 |
| AC-001-02 | external-registration.test.tsx | f122605, c3442f5, 04c65ee, d69a030 |
| AC-001-03 | ExternalRegistrationAcceptanceTest.java | f122605, c3442f5, 04c65ee, d69a030 |
| AC-001-04 | ExternalRegistrationAcceptanceTest.java | f122605, c3442f5, 04c65ee, d69a030 |
| AC-001-05 | ExternalRegistrationAcceptanceTest.java | f122605, c3442f5, 04c65ee, d69a030 |
| AC-001-06 | ExternalRegistrationAcceptanceTest.java | f122605, c3442f5, 04c65ee, d69a030 |
| AC-001-07 | ExternalRegistrationAcceptanceTest.java | f122605, c3442f5, 04c65ee, d69a030 |
| AC-001-08 | ExternalRegistrationAcceptanceTest.java | f122605, c3442f5, 04c65ee, d69a030 |
| AC-001-09 | ExternalRegistrationAcceptanceTest.java | f122605, c3442f5, 04c65ee, d69a030 |
| AC-001-10 | ExternalRegistrationAcceptanceTest.java | f122605, c3442f5, 04c65ee, d69a030 |
| AC-001-11 | ExternalRegistrationAcceptanceTest.java | f122605, c3442f5, 04c65ee, d69a030 |
| AC-001-12 | ExternalRegistrationAcceptanceTest.java | f122605, c3442f5, 04c65ee, d69a030 |
| AC-001-13 | external-registration.test.tsx | f122605, c3442f5, 04c65ee, d69a030 |
| AC-001-14 | ExternalRegistrationAcceptanceTest.java | f122605, c3442f5, 04c65ee, d69a030 |
| AC-001-15 | ExternalRegistrationAcceptanceTest.java | f122605, c3442f5, 04c65ee, d69a030 |
| AC-001-16 | ExternalRegistrationAcceptanceTest.java | f122605, c3442f5, 04c65ee, d69a030 |
| AC-001-17 | external-registration.test.tsx, registration.e2e.ts | f122605, c3442f5, 04c65ee, d69a030 |
| AC-001-18 | ExternalRegistrationAcceptanceTest.java | f122605, c3442f5, 04c65ee, d69a030 |
| AC-002-01 | StudentRegistrationAcceptanceTest.java, student-registration.test.tsx, registration.e2e.ts | f122605, c3442f5, 04c65ee, d69a030 |
| AC-002-02 | student-registration.test.tsx | f122605, c3442f5, 04c65ee, d69a030 |
| AC-002-03 | StudentRegistrationAcceptanceTest.java | f122605, c3442f5, 04c65ee, d69a030 |
| AC-002-04 | StudentRegistrationAcceptanceTest.java | f122605, c3442f5, 04c65ee, d69a030 |
| AC-002-05 | StudentRegistrationAcceptanceTest.java | f122605, c3442f5, 04c65ee, d69a030 |
| AC-002-06 | StudentRegistrationAcceptanceTest.java | f122605, c3442f5, 04c65ee, d69a030 |
| AC-002-07 | StudentRegistrationAcceptanceTest.java | f122605, c3442f5, 04c65ee, d69a030 |
| AC-002-08 | StudentRegistrationAcceptanceTest.java | f122605, c3442f5, 04c65ee, d69a030 |
| AC-002-09 | StudentRegistrationAcceptanceTest.java | f122605, c3442f5, 04c65ee, d69a030 |
| AC-002-10 | StudentRegistrationAcceptanceTest.java | f122605, c3442f5, 04c65ee, d69a030 |
| AC-002-11 | student-registration.test.tsx | f122605, c3442f5, 04c65ee, d69a030 |
| AC-002-12 | StudentRegistrationAcceptanceTest.java | f122605, c3442f5, 04c65ee, d69a030 |
| AC-002-13 | StudentRegistrationAcceptanceTest.java | f122605, c3442f5, 04c65ee, d69a030 |
| AC-002-14 | StudentRegistrationAcceptanceTest.java | f122605, c3442f5, 04c65ee, d69a030 |
| AC-002-15 | student-registration.test.tsx | f122605, c3442f5, 04c65ee, d69a030 |
| AC-002-16 | StudentRegistrationAcceptanceTest.java | f122605, c3442f5, 04c65ee, d69a030 |
| AC-003-01 | ConferenceOptionsAcceptanceTest.java, conference-options.test.tsx | f122605, 884c8e5, 19e2db0 |
| AC-003-02 | ConferenceOptionsAcceptanceTest.java | f122605, 884c8e5, 19e2db0 |
| AC-003-03 | ConferenceOptionsAcceptanceTest.java, conference-options.test.tsx, registration.e2e.ts | f122605, 884c8e5, 19e2db0 |
| AC-003-04 | ConferenceOptionsAcceptanceTest.java | f122605, 884c8e5, 19e2db0 |
| AC-003-05 | ConferenceOptionsAcceptanceTest.java, conference-options.test.tsx | f122605, 884c8e5, 19e2db0 |
| AC-003-06 | ConferenceOptionsAcceptanceTest.java | f122605, 884c8e5, 19e2db0 |
| AC-004-01 | RegistrationConfirmationAcceptanceTest.java, registration-confirmation.test.tsx, registration.e2e.ts | f122605, 04c65ee |
| AC-004-02 | RegistrationConfirmationAcceptanceTest.java, registration-confirmation.test.tsx | f122605 |
| AC-004-03 | RegistrationConfirmationAcceptanceTest.java, RegistrationStorageAcceptanceTest.java, registration-confirmation.test.tsx | f122605 |
| AC-005-01 | RegistrationStorageAcceptanceTest.java | b5c623b |
| AC-005-02 | RegistrationStorageAcceptanceTest.java | b5c623b |
| AC-005-03 | RegistrationStorageAcceptanceTest.java | b5c623b |
| AC-005-04 | DatabaseUnavailableAcceptanceTest.java | b5c623b |
| AC-005-05 | RegistrationStorageAcceptanceTest.java | b5c623b |
| AC-006-01 | ParticipantEmailAcceptanceTest.java, registration.e2e.ts | 5ec4276 |
| AC-006-02 | ParticipantEmailAcceptanceTest.java | 5ec4276 |
| AC-006-03 | ParticipantEmailAcceptanceTest.java | 5ec4276 |
| AC-006-04 | MailFailureAcceptanceTest.java | 5ec4276 |
| AC-006-05 | ParticipantEmailAcceptanceTest.java | 5ec4276 |
| AC-007-01 | OrganizerNotificationAcceptanceTest.java | 5ec4276 |
| AC-007-02 | OrganizerNotificationAcceptanceTest.java, registration.e2e.ts | 5ec4276 |
| AC-007-03 | OrganizerNotificationAcceptanceTest.java | 5ec4276 |
| AC-007-04 | OrganizerNotificationAcceptanceTest.java | 5ec4276 |
| AC-007-05 | MailFailureAcceptanceTest.java | 5ec4276 |
| AC-008-01 | RegistrationExportAcceptanceTest.java, registration.e2e.ts | e1093c9 |
| AC-008-02 | RegistrationExportAcceptanceTest.java, registration.e2e.ts | e1093c9 |
| AC-008-03 | RegistrationExportAcceptanceTest.java | e1093c9 |
| AC-008-04 | RegistrationExportAcceptanceTest.java | e1093c9 |
| AC-008-05 | RegistrationExportAcceptanceTest.java | e1093c9 |

## Findings

| F | Severity | Source | Finding | Resolution |
|---|---|---|---|---|
| F-01 | Low | phase 6 test run | non-frozen test "resets the reCAPTCHA widget…" flaky: asserted before the passive effect ran | fixed `741b673`; 5 repeated runs green |
| F-02 | High | phase 6 review, runtime probe | rate-limit, HTTPS-only and size filters matched the raw URI, so `/api/%65xport` skipped them (SR-03, SR-06) | fixed `995170f` (loop 1); re-verified by tests and runtime probe |
| F-03 | Medium | phase 6 log scan | database error detail with the email address in the application log (Hibernate) and PostgreSQL log on the duplicate-key race (ES-07, SB-07) | fixed `daaa48b` (loop 2); re-verified by log scan |
| F-04 | Low | evidence check | 11 commits exceed the size guide of `rules.md` without a stated reason (largest 872 lines, `119508c`) | accepted: history may not be rewritten |
| F-05 | Medium | npm audit | `qs` 6.15.1 (3 Moderate advisories) via `@stryker-mutator/core`; mutation tool only, not shipped | accepted (D-06) |
| F-06 | Low | Semgrep | HTTP Basic authentication on the export, reported as ERROR | accepted by the human (D-19, D-20); mitigations SR-06, BCrypt, rate limit |
| F-07 | Low | Dependency-Check | CPE false positives CVE-2025-7962 (`angus-activation`) and CVE-2025-15104 (`hibernate-validator`); still false positives on re-check (`angus-mail` 2.0.5; Nu Html Checker) | accepted, scoped suppressions (D-05, D-08) |
| F-08 | Low | jscpd | 2.7 % duplication in frontend test files (7 clones); production code 0 % (CPD 0 in the backend) | accepted: repeated test set-up |
| F-09 | Low | runtime demonstration | harness defect: Git Bash rewrote container paths of `docker exec`, so the first run could not list the JSON copies | fixed in `runtime-demo.sh` and `verify.sh`; re-run passed |
| F-10 | Medium | phase 5 first run | unknown request properties accepted (specification section 4, SB-01) | fixed `de3e3e7` in phase 5 |
| F-11 | Low | phase 7 README check | `npm run dev`: the Vite proxy shorthand rewrote the Host header, so registrations from the dev server were refused as cross-origin (development only) | fixed `3c3f27e` |

## Fix loops

| Loop | Findings | Change | Re-verification |
|---|---|---|---|
| 1 (2026-10-09T13:59:22Z – 14:04:37Z) | F-02 | `RequestPath` (decoded path) used by the three filters; `EncodedPathTest`, `EncodedPathIntegrationTest` | be-format/lint/spotbugs 0; backend 239/0; stack rebuilt; e2e 6/0; probe: encoded path now 429 (`logs/6fix_*`) |
| 2 (2026-10-09T14:04:52Z – 14:07:19Z) | F-03 | logger `org.hibernate.orm.jdbc.error` off; PostgreSQL `log_error_verbosity=terse` | race test log: 0 emails, 0 key details; PostgreSQL log 0 `DETAIL` lines after a forced duplicate (`logs/6fix2_*`) |
