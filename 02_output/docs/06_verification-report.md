# Verification report

> Written in: phase 6 · Source: `standards/`, `project/constraints.md` · Procedure: `skills/verify-release` · Agent: writes

This is a self-check by the development agent, not an independent review.

## Definition of Done

| DoD | Result | Evidence |
|---|---|---|
| DoD-01 | pass | `logs/6_be-test.log` 213/0, `logs/6_fe-test.log` 69/0, `logs/6_e2e.log` 10/0; acceptance manifest 32 of 32 hashes match |
| DoD-02 | pass | `logs/6_be-format.log`, `6_be-lint.log` (PMD 0), `6_be-static.log` (SpotBugs 0; exclusion D-13), `6_fe-format.log`, `6_fe-lint.log` (0), `6_fe-typecheck.log` (0), `6_semgrep.log` (0) |
| DoD-03 | pass (record only) | coverage backend 96.4% line / 92.2% branch, frontend 100% / 97.24%; mutation backend 95.8%, frontend 98.1%; surviving mutants classified in `03_test-strategy.md` |
| DoD-04 | pass | `ArchitectureTest` 5/0 (AR-02 layers, AR-03 no cycles) in `logs/6_be-test.log`; AR-01, AR-04 to AR-07: "Security controls" and traceability below |
| DoD-05 | pass | open Critical 0, High 0: F-01 fixed (loop 1); `6_be-depscan.log` 0/0, `6_fe-depscan.log` 0/0, `6_semgrep.log` 0, `6_gitleaks.log` 0 |
| DoD-06 | pass | `logs/6_runtime-demo.log`, `logs/6-demo_e2e.log`, `logs/6-demo_workbook.log` (runtime demonstration below) |
| DoD-07 | pass | traceability table below: 51 of 51 ACs have tests and commits |
| DoD-08 | phase 7 | |
| DoD-09 | phase 7 | |
| DoD-10 | pass | `decisions-log.md` D-01 to D-19 all resolved or pending review; input manifest 26 of 26 hashes match |
| DoD-11 | pass | "Evidence" below |
| DoD-P01 | pass | `6_runtime-demo.log`: external `185b6493-…` and student `0017fcd8-…` registrations answered 201 through the frontend proxy |
| DoD-P02 | pass | `6_runtime-demo.log`: both rows with options and consents in PostgreSQL; both JSON copies on the `jsoncopies` volume |
| DoD-P03 | pass | `6_runtime-demo.log`: two participant emails and two organizer emails with `registration-<id>.json` (application/json) in Mailpit |
| DoD-P04 | pass | `6-demo_e2e.log` 200 with organizer access; `6-demo_workbook.log`: sheet "Registrations", 15 columns, demo rows with č, š, ž, 0 formula cells; `6_runtime-demo.log`: 401 without access |
| DoD-P05 | pass | `Us001LiveCaptchaAcceptanceTest` 5/0 against a mocked verification endpoint: accepted, rejected and unavailable tokens |

## Evidence

| Check | Result | Evidence |
|---|---|---|
| Phase 3 adds no production code beyond the skeleton | pass | `git diff --stat 7ad420e 2a543e5 -- backend/src/main frontend/src` (non-test): empty |
| Freeze commit holds only the manifest; listed files committed before | pass | `git show --name-only 2a543e5`: `docs/03_acceptance-manifest.sha256` only; 32 of 32 hashes match |
| At least one phase 4 commit per story, naming its id | pass | `logs/4_commits-since-freeze.log`: US-001 4, US-002 2, US-003 2, US-004 2, US-005 3, US-006 1, US-007 1, US-008 1 |
| No commit above the size guide without a reason | pass | only `35c3c77` (441 lines), reason D-04; D-12 corrected by D-19 |
| Manifest hashes | pass | acceptance 32/32, input 26/26 (LF-normalised SHA-256) |
| Decisions resolved or pending review | pass | D-01 to D-19 |
| No secret value in any file, log or commit message | pass | `secrets.sh leak-check`: no file, 0 commit-message lines |

## Security controls

| Item | Implemented in | Checked by |
|---|---|---|
| SR-01 | `RecaptchaVerifier`, `RegistrationService` (token before storage), `Captcha.tsx` | acceptance AC-001-14, AC-002-09, `Us001LiveCaptchaAcceptanceTest`, `CaptchaVerifierTest` |
| SR-02 | `StartupGuard`, `RegistrationConfig` (test mode only by setting) | `StartupGuardTest`; local stack log shows test mode only with `APP_ENVIRONMENT=local` |
| SR-03 | `RateLimitFilter`, `RequestSizeLimitFilter`, nginx `client_max_body_size` | `RequestLimitFiltersTest`, `RateLimitIntegrationTest`, `WebIntegrationTest` (413) |
| SR-04 | `RegistrationValidator` (catalog check) | AC-001-08, AC-001-09, AC-002-06, AC-002-07, `RegistrationValidatorTest` |
| SR-05 | `RegistrationValidator` (control characters), `SmtpNotifier` (strict addresses, plain text) | `RegistrationValidatorTest`, `RegistrationEmailsTest`, inspection of `emails.json` |
| SR-06 | `OrganizerHttpsFilter`, `StartupGuard`, `server.forward-headers-strategy=native` | `OrganizerHttpsIntegrationTest`, `OrganizerHttpsFilterTest`, `StartupGuardTest` |
| SR-07 | `PoiExportWriter` fixed columns, `RegistrationEmails` | AC-008-01, `PoiExportWriterTest`, `RegistrationEmailsTest` |
| SB-01 | `RegistrationValidator` on the backend | backend acceptance tests send invalid input directly to the API |
| SB-02 | `SecurityConfig` (export `ORGANIZER`, deny all else) | AC-008-02, AC-008-03, `WebIntegrationTest` (401 on unknown paths) |
| SB-03 | `SecurityConfig` BCrypt in memory; secrets only from the environment | inspection; `StartupGuardTest`; `secrets.sh leak-check` |
| SB-04 | production proxy TLS; `SMTP_TLS` required in production | `StartupGuardTest`; "Before production" check in the release notes |
| SB-05 | JPA bound parameters, React escaping, plain-text email, string cells | Semgrep 0; `PoiExportWriterTest` (formula input stays text) |
| SB-06 | `RateLimitFilter` on registration, export and form config | `RateLimitIntegrationTest` |
| SB-07 | `ApiExceptionHandler`, `Problems`, log statements with registration id only | AC-004-03, `WebIntegrationTest`, `logs/6_log-review.log` |
| SB-08 | Dependency-Check, npm audit | `6_be-depscan.log` 0/0, `6_fe-depscan.log` 0/0 after F-01 |
| SB-09 | Semgrep, gitleaks (tree and HEAD history) | `6_semgrep.log` 0, `6_gitleaks.log` 0 |
| SB-10 | `SecurityConfig` headers, nginx headers | `WebIntegrationTest`; `6_runtime-demo.log` (frontend headers) |
| SB-11 | non-root users in both images | `6_runtime-demo.log`: `uid=100(app)`, `uid=101(nginx)` |
| SB-12 | fields of `project/constraints.md` only | `db-schema.sql`, `registration-copy.schema.json` (inspection) |
| SB-13 | purposes in `project/constraints.md`; retention D-10 | release notes "Before production" |
| SB-14 | unchecked consent boxes; consents stored with wording and time | AC-001-12, AC-001-13, AC-005-01 |

## Runtime demonstration

| Component | Environment | Flows exercised | Result |
|---|---|---|---|
| backend | local (`docker compose`, project `registration-tanej04`) | health and readiness; external and student registration; storage in PostgreSQL and JSON copies; emails; export with and without organizer access | pass (`6_runtime-demo.log`, `6-demo_e2e.log`, `6-demo_workbook.log`) |
| frontend | local | page served with security headers; full form flows for both types, emails and export through the page (10 end-to-end tests) | pass (`6_runtime-demo.log`, `6_e2e.log`) |
| all | local | `docker compose down` and `up`: 11 registrations and 11 JSON copies before and after; all four containers healthy again (NFR-02, NFR-04) | pass (`6_runtime-demo.log`) |

## Traceability

Implementation commits per story: US-001 `b76d471` `69aa085` `2bfdd59` `09263b6` `362ea1a`; US-002 `b76d471` `09263b6` `362ea1a`; US-003 `a253962` `63dad44`; US-004 `09263b6` `362ea1a`; US-005 `69aa085` `4759ee9` `2bfdd59`; US-006, US-007 `21ef318`; US-008 `b888cb9`.

| AC | Tests | Commits |
|---|---|---|
| AC-001-01 | `Us001ExternalRegistrationAcceptanceTest`, `us001-external.test.tsx`, `us001-external.spec.ts` | tests `cf89ef9` `6b21c9b`; code US-001 |
| AC-001-02 | `Us001ExternalRegistrationAcceptanceTest`, `Us001LiveCaptchaAcceptanceTest`, `us001-external.test.tsx`, `us001-external.spec.ts` | tests `cf89ef9` `8aa8468` `6b21c9b`; code US-001 |
| AC-001-03 | `Us001ExternalRegistrationAcceptanceTest`, `us001-external.test.tsx`, `us001-external.spec.ts` | tests `cf89ef9` `6b21c9b`; code US-001 |
| AC-001-04 | `Us001ExternalRegistrationAcceptanceTest`, `us001-external.test.tsx` | tests `cf89ef9` `6b21c9b`; code US-001 |
| AC-001-05 | `Us001ExternalRegistrationAcceptanceTest` | tests `cf89ef9`; code US-001 |
| AC-001-06 | `Us001ExternalRegistrationAcceptanceTest`, `us001-external.test.tsx` | tests `cf89ef9` `6b21c9b`; code US-001 |
| AC-001-07 | `Us001ExternalRegistrationAcceptanceTest`, `us001-external.spec.ts` | tests `cf89ef9` `6b21c9b`; code US-001 |
| AC-001-08 | `Us001ExternalRegistrationAcceptanceTest` | tests `cf89ef9`; code US-001 |
| AC-001-09 | `Us001ExternalRegistrationAcceptanceTest` | tests `cf89ef9`; code US-001 |
| AC-001-10 | `Us001ExternalRegistrationAcceptanceTest` | tests `cf89ef9`; code US-001 |
| AC-001-11 | `Us001ExternalRegistrationAcceptanceTest` | tests `cf89ef9`; code US-001 |
| AC-001-12 | `us001-external.test.tsx`, `us001-external.spec.ts` | tests `6b21c9b`; code `362ea1a` |
| AC-001-13 | `Us001ExternalRegistrationAcceptanceTest`, `us001-external.test.tsx` | tests `cf89ef9` `6b21c9b`; code US-001 |
| AC-001-14 | `Us001ExternalRegistrationAcceptanceTest`, `Us001LiveCaptchaAcceptanceTest`, `us001-external.test.tsx` | tests `cf89ef9` `8aa8468` `6b21c9b`; code US-001 |
| AC-001-15 | `Us001ExternalRegistrationAcceptanceTest`, `us001-external.test.tsx` | tests `cf89ef9` `6b21c9b`; code US-001 |
| AC-002-01 | `Us002StudentRegistrationAcceptanceTest`, `us002-student.test.tsx`, `us002-student.spec.ts` | tests `729fe79`; code US-002 |
| AC-002-02 | `Us002StudentRegistrationAcceptanceTest`, `us002-student.test.tsx`, `us002-student.spec.ts` | tests `729fe79`; code US-002 |
| AC-002-03 | `Us002StudentRegistrationAcceptanceTest`, `us002-student.test.tsx` | tests `729fe79`; code US-002 |
| AC-002-04 | `Us002StudentRegistrationAcceptanceTest`, `us002-student.test.tsx` | tests `729fe79`; code US-002 |
| AC-002-05 | `Us002StudentRegistrationAcceptanceTest`, `us002-student.spec.ts` | tests `729fe79`; code US-002 |
| AC-002-06 | `Us002StudentRegistrationAcceptanceTest` | tests `729fe79`; code US-002 |
| AC-002-07 | `Us002StudentRegistrationAcceptanceTest` | tests `729fe79`; code US-002 |
| AC-002-08 | `Us002StudentRegistrationAcceptanceTest` | tests `729fe79`; code US-002 |
| AC-002-09 | `Us002StudentRegistrationAcceptanceTest` | tests `729fe79`; code US-002 |
| AC-002-10 | `Us002StudentRegistrationAcceptanceTest` | tests `729fe79`; code US-002 |
| AC-003-01 | `Us003ConfigurableOptionsAcceptanceTest`, `us003-options.test.tsx` | tests `ca77de8`; code US-003 |
| AC-003-02 | `Us003ConfigurableOptionsAcceptanceTest`, `us003-options.test.tsx` | tests `ca77de8`; code US-003 |
| AC-003-03 | `Us003ConfigurableOptionsAcceptanceTest`, `us003-options.test.tsx`, `us002-student.spec.ts` | tests `ca77de8` `729fe79`; code US-003 |
| AC-003-04 | `Us003OptionsChangeByConfigurationAcceptanceTest` | tests `ca77de8`; code US-003 |
| AC-003-05 | `Us003ConfigurableOptionsAcceptanceTest` | tests `ca77de8`; code US-003, `4759ee9` |
| AC-004-01 | `Us004ConfirmationAcceptanceTest`, `us004-confirmation.test.tsx`, `us001-external.spec.ts` | tests `156806c` `6b21c9b`; code US-004 |
| AC-004-02 | `Us004ConfirmationAcceptanceTest`, `us004-confirmation.test.tsx` | tests `156806c`; code US-004 |
| AC-004-03 | `Us004ConfirmationAcceptanceTest`, `us004-confirmation.test.tsx` | tests `156806c`; code US-004 |
| AC-005-01 | `Us005StorageAcceptanceTest` | tests `3639037`; code US-005 |
| AC-005-02 | `Us005StorageAcceptanceTest` | tests `3639037`; code US-005 |
| AC-005-03 | `Us005StorageAcceptanceTest` | tests `3639037`; code US-005 |
| AC-005-04 | `Us005StorageAcceptanceTest` | tests `3639037`; code US-005 |
| AC-005-05 | `Us005StorageAcceptanceTest` | tests `3639037`; code US-005 |
| AC-006-01 | `Us006ParticipantEmailAcceptanceTest`, `us006-us007-emails.spec.ts` | tests `c82c15d` `5de329e`; code `21ef318` |
| AC-006-02 | `Us006ParticipantEmailAcceptanceTest` | tests `c82c15d`; code `21ef318` |
| AC-006-03 | `Us006Us007EmailFailureAcceptanceTest` | tests `c82c15d`; code `21ef318` |
| AC-006-04 | `Us006ParticipantEmailAcceptanceTest`, `us006-us007-emails.spec.ts` | tests `c82c15d` `5de329e`; code `21ef318` |
| AC-007-01 | `Us007OrganizerNotificationAcceptanceTest`, `us006-us007-emails.spec.ts` | tests `5de329e`; code `21ef318` |
| AC-007-02 | `Us007OrganizerNotificationAcceptanceTest`, `us006-us007-emails.spec.ts` | tests `5de329e`; code `21ef318` |
| AC-007-03 | `Us007OrganizerNotificationAcceptanceTest` | tests `5de329e`; code `21ef318` |
| AC-007-04 | `Us006Us007EmailFailureAcceptanceTest` | tests `c82c15d`; code `21ef318` |
| AC-008-01 | `Us008ExportAcceptanceTest`, `us008-export.spec.ts` | tests `4c4cfb0`; code `b888cb9` |
| AC-008-02 | `Us008ExportAcceptanceTest`, `us008-export.spec.ts` | tests `4c4cfb0`; code `b888cb9`, `3bdd2bb` |
| AC-008-03 | `Us008ExportAcceptanceTest`, `us008-export.spec.ts` | tests `4c4cfb0`; code `b888cb9`, `3bdd2bb` |
| AC-008-04 | `Us008ExportAcceptanceTest` | tests `4c4cfb0`; code `b888cb9` |
| AC-008-05 | `Us008ExportAcceptanceTest` | tests `4c4cfb0`; code `b888cb9` |

## Findings

| F | Severity | Source | Finding | Resolution |
|---|---|---|---|---|
| F-01 | Critical | npm audit (`6_fe-depscan.log`, first run) | `tinypool` 1.1.1 under `vitest` 3.2.7: GHSA-5gmw-xhrv-c9v3, GHSA-85c8-ppgw-ccpr (published after phase 0) | fixed: override to 2.1.2 (D-18), loop 1; re-scan Critical 0 |
| F-02 | Low | gitleaks (`6_gitleaks.log`, first run) | `generic-api-key` on two test-only password constants (tree and history) | accepted: constant test input, `leak-check` clean; reviewed fingerprints in `scripts/.gitleaksignore` |
| F-03 | Medium | npm audit | 5 moderate in dev-only tooling (`@vitest/mocker` path traversal in mock redirects; `qs` DoS under `jscpd`) | accepted: not in the shipped bundle; fix needs major versions outside `stack.md` (release notes) |
| F-04 | Medium | Dependency-Check | CVE-2025-15104 on `hibernate-validator` 9.1.3 (Nu Html Checker CVE matched by product name) | accepted: false positive, the vulnerable product is not used |
| F-05 | Low | Dependency-Check, phase 0 | CVE-2025-7962 on `angus-activation` 2.0.3, found High | accepted by the human as a false positive (D-03), suppressed for that artifact |
| F-06 | Low | Semgrep, phase 2 | HTTP Basic authentication on the export, found High | accepted by the human (D-11), mitigations in SR-06, SB-03, SB-06 |
| F-07 | Low | log review (`6_log-review.log`) | committed test logs contain the operator's local user name and paths from Spring startup lines | accepted: no participant data or secret; developer environment only |

## Fix loops

| Loop | Findings | Change | Re-verification |
|---|---|---|---|
| 1 | F-01 | npm `overrides` pins `tinypool` to 2.1.2 (`f4182f5`, D-18) | `verify.sh 6 fe-test` 69/0, `fe-mutation` 98.1%, `fe-build` ok, `fe-depscan` Critical 0, High 0 |
