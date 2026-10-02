# Verification report

> Written in: phase 6 · Source: `general/standards.md`, `project/02_design/*` · Procedure: `general/phases/6-verify.md` · Agent: writes

This is a self-check by the development agent, not an independent review.

Final run: 2026-10-02T13:41:41Z, every tool through `scripts/verify.sh 6`; logs are `logs/6_<tool>.log`.

## Definition of Done

| DoD | Result | Evidence |
|---|---|---|
| DoD-01 | Pass | backend 229 passed, 0 failed (`6_backend-test.log`); frontend 79 passed (`6_frontend-test.log`); end-to-end 3 passed against the compose stack (`6_e2e.log`); 20 frozen-test hashes and 25 input hashes recomputed, 0 mismatches (`6_manifests.log`) |
| DoD-02 | Pass | backend: Spotless, PMD, SpotBugs clean (`6_backend-check.log`); frontend: Prettier, ESLint, `tsc` clean (`6_frontend-check.log`); Semgrep 398 rules, 0 findings (`6_semgrep.log`) |
| DoD-03 | Pass (thresholds: record only) | backend lines 97.8 %, branches 95.0 % (`6_backend-coverage.log`); frontend lines 95.61 %, branches 92.34 % (`6_frontend-test.log`); mutation score backend 98 % (214 of 219, `6_backend-mutation.log`), frontend 96.22 % (`6_frontend-mutation.log`); survivors classified in F-04 |
| DoD-04 | Pass | `ArchitectureTest`, 8 rules: layers of specification section 3 (AR-02), no package cycles (AR-03), JPA only in the persistence adapter (AR-06); AR-01 and AR-07: `api.test.ts`, `RecaptchaIntegrationTest`; AR-04: `Us003ConfigurableOptions`; AR-05: `SubmitRegistrationTest`, `Us005ReliableStorage` |
| DoD-05 | Pass | no open Critical or High: F-01 lowered by human decision D-16, F-02 fixed; Dependency-Check 0 findings (`6_backend-depscan.log`), `npm audit` 0 Critical, 0 High (`6_frontend-audit.log`), Semgrep 0, gitleaks 0 (`6_gitleaks.log`) |
| DoD-06 | Pass | runtime demonstration, 48 checks passed, 0 failed (`6_demo.log`); see "Runtime demonstration" |
| DoD-07 | Pass | "Traceability": 52 of 52 AC have at least one test and one commit |
| DoD-08 | Pass (added in phase 7, D-19) | `logs/7_clean-checkout.log`: clean clone of commit `e4db016` in a short path; every command of the root, backend and frontend README run once, 15 steps passed, 0 failed (build, check, test of both components, compose stack healthy, form, API and Mailpit reachable, development server with `/api`, stop) |
| DoD-09 | Pass (added in phase 7, D-19) | `docs/release-notes.md`, "Must be tested manually by a human": six items, including the three external services of `environments.md` |
| DoD-10 | Pass | `decisions-log.md`: blocking D-05 and D-16 resolved by the human; D-01, D-03, D-04 accepted; all others marked pending review; inputs and protected files unchanged (`6_manifests.log`) |
| DoD-11 | Pass, with F-06 | phase 3 changed no production code (`git diff 7d3883d ffdd967` over `backend/src/main` and `frontend/src` outside `acceptance`: empty); freeze commit `ffdd967` holds only the manifest and every listed file was committed before it; every story has a phase 4 commit naming it (`4_commits.log`); commit sizes: F-06; hashes match; every decision resolved or pending review |
| DoD-P01 | Pass | `6_demo.log`: one external and one student registration answered 201 |
| DoD-P02 | Pass | `6_demo.log`: 2 rows added in PostgreSQL (query output), 2 JSON copies added (file listing), Slovenian characters intact |
| DoD-P03 | Pass | `6_demo.log`: Mailpit API shows the participant confirmation and the organizer notification with `registration-<id>.json` attached |
| DoD-P04 | Pass | `6_demo.log`: export 200 with a workbook that unzips and contains both registrations; 401 without and with wrong credentials |
| DoD-P05 | Pass | `RecaptchaIntegrationTest` (6 tests) and `RecaptchaCaptchaVerifierTest` in `6_backend-test.log`: test mode off, mocked verification endpoint, accepted and rejected token, failing endpoint |

## Runtime demonstration

| Component | Environment | Flows exercised | Result |
|---|---|---|---|
| backend, frontend, PostgreSQL, Mailpit | local: `docker compose` stack built from the verified code (`6_stack-up.log`), four services healthy | health and readiness (NFR-04); form configuration and 7 active options; external and student registration through the frontend proxy; rejection for an invalid email and for a failed anti-automation check | passed (`6_demo.log`) |
| backend, PostgreSQL, JSON copy volume | local | rows and option rows by SQL query; JSON copy files by listing; characters č, š, ž in both | passed |
| backend, Mailpit | local | participant confirmation, organizer notification, JSON attachment, read through the Mailpit API | passed |
| backend | local | organizer export with credentials and its workbook content; refusal without and with wrong credentials | passed |
| frontend, backend | local | security headers on page and API, no cookie, non-root processes, ports bound to 127.0.0.1 only, request-size limit, logs without personal data or stack traces | passed |
| whole stack | local | `docker compose down`, then `up`: rows, JSON copies and export unchanged (NFR-02) | passed; 48 checks in total, 0 failed |
| browser and whole stack | local | Playwright: external and student registration through the form to the confirmation, both emails, JSON attachment, export; rejection with an empty field | 3 tests passed (`6_e2e.log`) |
| external services | production only | live reCAPTCHA with production keys, delivery through the real SMTP server, HTTPS and `/api` routing at the external reverse proxy | not exercisable locally; manual tests for a human (`environments.md`), listed in the release notes |

## Security controls

| ID | Implemented in | Checked by |
|---|---|---|
| SB-01 | `RegistrationRequestParser`, `RegistrationValidator`; database constraints in `V1__registration.sql` | test: `RegistrationValidatorTest`, `RegistrationRequestParserTest`, `Us001`, `Us002` acceptance |
| SB-02 | `SecurityConfig` (the export requires the organizer role; every other operation is public by design) | test: `Us008Export` AC-008-02, AC-008-03; `SecurityIntegrationTest`; runtime: `6_demo.log` |
| SB-03 | `SecurityConfig.organizerAccount` (bcrypt hash in memory); secrets only from the environment (`application.properties`, no defaults) | inspection; test: `Us008Export`; scan: gitleaks, `6_env-leak.log` |
| SB-04 | TLS at the external reverse proxy (production); `HttpsRequiredFilter`, `StartupChecks` | test: `SecurityIntegrationTest` sr06, `StartupChecksTest` sr06; TLS itself: manual test (release notes) |
| SB-05 | JSON by Jackson; SQL through JPA parameters only (`JpaRegistrationStore`); plain-text mail (`SmtpMailNotifier`); `PoiRegistrationExporter.neutralised`; React text rendering | test: `PoiRegistrationExporterTest` sb05, `FileJsonCopyStoreTest`, `ArchitectureTest` ar06; scan: Semgrep |
| SB-06 | `RateLimitFilter` (registration, export including failed logins, read) | test: `RateLimitFilterTest`, `RateLimitFilterChainTest`, `SecurityIntegrationTest` sr03 |
| SB-07 | `Problems` (fixed texts), `ApiExceptionHandler`, logging of ids and class names only | test: `SecurityIntegrationTest` sb07, `Us005` (503 body), `SubmitRegistrationTest`; runtime: `6_demo.log` (logs) |
| SB-08 | `verify.sh backend-depscan`, `frontend-audit` | scan: 0 Critical, 0 High; F-05 |
| SB-09 | `verify.sh semgrep`, `gitleaks`; SpotBugs and PMD in `backend-check` | scan: 0 findings; F-01, F-02, F-03 |
| SB-10 | `SecurityConfig` headers; `frontend/nginx.conf` headers | test: `SecurityIntegrationTest` sb10; runtime: `6_demo.log` (8 header checks) |
| SB-11 | `backend/Dockerfile` (user `app`), `frontend/Dockerfile` (user `nginx`), read-only root file systems in `docker-compose.yml` | runtime: `6_demo.log` (non-root, file owner) |
| SB-12 | only the fields of `TextField`; the verification call sends secret and token only (`RecaptchaCaptchaVerifier`) | test: `RegistrationRequestParserTest` (unknown fields), `RecaptchaCaptchaVerifierTest` sr01; inspection of `registration-schema.sql` |
| SB-13 | purposes: `security-requirements.md`; retention: specification section 6 (D-13, proposed 12 months) | inspection; the period is listed in the release notes for human confirmation |
| SB-14 | one unticked consent checkbox (`RegistrationForm`); consent text and time stored (`Registration.Consent`) | test: AC-001-09, AC-001-14, AC-002-08, AC-002-11, `Us005` AC-005-01 |
| SR-01 | `SubmitRegistration` (verification before storage), `RecaptchaCaptchaVerifier` | test: `RecaptchaIntegrationTest`, `RecaptchaCaptchaVerifierTest`, `SubmitRegistrationTest` sr01, AC-001-12 |
| SR-02 | `AppProperties` (test mode off by default), `StartupChecks` | test: `StartupChecksTest` sr02, `SecurityIntegrationTest` sr02 |
| SR-03 | `RateLimitFilter`; body limit in `RegistrationController`; `client_max_body_size` in nginx | test: `SecurityIntegrationTest` sr03 (429, 413); runtime: `6_demo.log` (413) |
| SR-04 | `RegistrationValidator.validateOptions` against `FileOptionsCatalogue` | test: AC-001-07, AC-001-08, AC-002-07, AC-003-04 |
| SR-05 | `RegistrationValidator` (no control characters), `SmtpMailNotifier`, `MailTexts` | test: `RegistrationValidatorTest` sr05, `SecurityIntegrationTest` sr05, `Us006Us007Email` (plain text, no HTML part) |
| SR-06 | `HttpsRequiredFilter`, `ClientRequests`, `StartupChecks` | test: `SecurityIntegrationTest` sr06 (403 without a challenge; allowed behind the trusted proxy), `StartupChecksTest` sr06 |
| SR-07 | `PoiRegistrationExporter` (fixed columns), `MailTexts` | test: `PoiRegistrationExporterTest` sr07, `Us008Export`, `Us006Us007Email` |

## Traceability

Tests: class or file, with the number of tests that name the AC. Commits: the phase 4 commits of the AC's story.

| AC | Tests | Commits |
|---|---|---|
| AC-001-01 | Us001ExternalRegistration 1, registration(e2e) 1, us001-external-registration(ui) 1 | `df15882` `2cb41bd` `d5738b4` `a4533a8` `52d848d` |
| AC-001-02 | us001-external-registration(ui) 1 | `df15882` `2cb41bd` `d5738b4` `a4533a8` `52d848d` |
| AC-001-03 | Us001ExternalRegistration 1, registration(e2e) 1, us001-external-registration(ui) 2 | `df15882` `2cb41bd` `d5738b4` `a4533a8` `52d848d` |
| AC-001-04 | Us001ExternalRegistration 1 | `df15882` `2cb41bd` `d5738b4` `a4533a8` `52d848d` |
| AC-001-05 | Us001ExternalRegistration 1, us001-external-registration(ui) 1 | `df15882` `2cb41bd` `d5738b4` `a4533a8` `52d848d` |
| AC-001-06 | Us001ExternalRegistration 1 | `df15882` `2cb41bd` `d5738b4` `a4533a8` `52d848d` |
| AC-001-07 | Us001ExternalRegistration 1, us001-external-registration(ui) 1 | `df15882` `2cb41bd` `d5738b4` `a4533a8` `52d848d` |
| AC-001-08 | Us001ExternalRegistration 1, us001-external-registration(ui) 1 | `df15882` `2cb41bd` `d5738b4` `a4533a8` `52d848d` |
| AC-001-09 | Us001ExternalRegistration 1, us001-external-registration(ui) 1 | `df15882` `2cb41bd` `d5738b4` `a4533a8` `52d848d` |
| AC-001-10 | Us001ExternalRegistration 1, us001-external-registration(ui) 1 | `df15882` `2cb41bd` `d5738b4` `a4533a8` `52d848d` |
| AC-001-11 | Us001ExternalRegistration 1 | `df15882` `2cb41bd` `d5738b4` `a4533a8` `52d848d` |
| AC-001-12 | RecaptchaIntegrationTest 2, Us001ExternalRegistration 3, us001-external-registration(ui) 1 | `df15882` `2cb41bd` `d5738b4` `a4533a8` `52d848d` |
| AC-001-13 | Us001ExternalRegistration 1 | `df15882` `2cb41bd` `d5738b4` `a4533a8` `52d848d` |
| AC-001-14 | us001-external-registration(ui) 1 | `df15882` `2cb41bd` `d5738b4` `a4533a8` `52d848d` |
| AC-002-01 | Us002StudentRegistration 1, registration(e2e) 1, us002-student-registration(ui) 1 | `1539cfe` `a4533a8` `52d848d` |
| AC-002-02 | Us002StudentRegistration 1, us002-student-registration(ui) 1 | `1539cfe` `a4533a8` `52d848d` |
| AC-002-03 | Us002StudentRegistration 1, us002-student-registration(ui) 1 | `1539cfe` `a4533a8` `52d848d` |
| AC-002-04 | Us002StudentRegistration 1 | `1539cfe` `a4533a8` `52d848d` |
| AC-002-05 | Us002StudentRegistration 1, us002-student-registration(ui) 1 | `1539cfe` `a4533a8` `52d848d` |
| AC-002-06 | Us002StudentRegistration 1 | `1539cfe` `a4533a8` `52d848d` |
| AC-002-07 | Us002StudentRegistration 1 | `1539cfe` `a4533a8` `52d848d` |
| AC-002-08 | Us002StudentRegistration 1, us002-student-registration(ui) 1 | `1539cfe` `a4533a8` `52d848d` |
| AC-002-09 | Us002StudentRegistration 2, us002-student-registration(ui) 1 | `1539cfe` `a4533a8` `52d848d` |
| AC-002-10 | Us002StudentRegistration 1, us002-student-registration(ui) 1 | `1539cfe` `a4533a8` `52d848d` |
| AC-002-11 | us002-student-registration(ui) 1 | `1539cfe` `a4533a8` `52d848d` |
| AC-002-12 | us002-student-registration(ui) 1 | `1539cfe` `a4533a8` `52d848d` |
| AC-003-01 | Us003ConfigurableOptions 1, registration(e2e) 1, us003-configurable-options(ui) 1 | `82c33f0` `52d848d` |
| AC-003-02 | Us003ConfigurableOptions 1, us003-configurable-options(ui) 1 | `82c33f0` `52d848d` |
| AC-003-03 | Us003ConfigurableOptions 2, us003-configurable-options(ui) 1 | `82c33f0` `52d848d` |
| AC-003-04 | Us003ConfigurableOptions 1 | `82c33f0` `52d848d` |
| AC-003-05 | Us003ConfigurableOptions 1, us003-configurable-options(ui) 1 | `82c33f0` `52d848d` |
| AC-004-01 | registration(e2e) 2, us004-confirmation(ui) 2 | `76614a6` |
| AC-004-02 | registration(e2e) 1, us004-confirmation(ui) 2 | `76614a6` |
| AC-004-03 | Us005ReliableStorage 2, us004-confirmation(ui) 2 | `76614a6` |
| AC-005-01 | Us005ReliableStorage 2 | `fff964c` |
| AC-005-02 | Us005ReliableStorage 2 | `fff964c` |
| AC-005-03 | Us005ReliableStorage 2 | `fff964c` |
| AC-005-04 | Us005ReliableStorage 1 | `fff964c` |
| AC-005-05 | Us005ReliableStorage 1 | `fff964c` |
| AC-006-01 | Us006Us007Email 2, registration(e2e) 2 | `0e72241` |
| AC-006-02 | Us006Us007Email 1, registration(e2e) 1 | `0e72241` |
| AC-006-03 | Us006Us007Email 1 | `0e72241` |
| AC-007-01 | Us006Us007Email 2, registration(e2e) 1 | `0e72241` |
| AC-007-02 | Us006Us007Email 1, registration(e2e) 1 | `0e72241` |
| AC-007-03 | Us006Us007Email 1 | `0e72241` |
| AC-007-04 | Us006Us007Email 1 | `0e72241` |
| AC-008-01 | Us008Export 1, registration(e2e) 2 | `9470019` |
| AC-008-02 | Us008Export 1, registration(e2e) 1 | `9470019` |
| AC-008-03 | Us008Export 1 | `9470019` |
| AC-008-04 | Us008Export 1 | `9470019` |
| AC-008-05 | Us008Export 1, registration(e2e) 1 | `9470019` |
| AC-008-06 | Us008Export 1 | `9470019` |

## Findings

| F | Severity | Source | Finding | Resolution |
|---|---|---|---|---|
| F-01 | High as found (Semgrep ERROR); lowered to Low by D-16 | Semgrep 1.177.0, rule `yaml.openapi.security.use-of-basic-authentication`, `docs/02_contracts/openapi.yaml` (`organizerBasic`), found in phase 3, raw report `logs/3_semgrep-raw.log` | The organizer export uses HTTP Basic authentication. | Kept by human decision D-16 (2026-10-02T11:28:29Z). Evidence: credentials are refused over plain HTTP before they are read (SR-06); one account, stored only as a bcrypt hash; failed logins are rate limited; no session or cookie; identity providers are out of scope (`security-requirements.md`). That line only is suppressed with the D-16 id. |
| F-02 | High as found (Semgrep ERROR) | Semgrep 1.177.0, rule `generic.nginx.security.missing-internal`, `frontend/nginx.conf`, found in phase 4, `logs/4_semgrep.log` history | The `/api` route forwarded to a destination held in a variable, which the rule treats as possible request forgery. | Fixed in phase 4 (D-18): the destination is the fixed host name `backend`; re-scan 0 findings. |
| F-03 | Low (false positive) | gitleaks v8.30.1, rule `generic-api-key`, 5 matches in `logs/3_backend-test.log` at commit `7dec4b8`, raw report `logs/4_gitleaks-raw.log` | The generated start-up password of the bootstrap application's throwaway user was printed into a committed test log. | D-17: not a credential of the shipped system; fingerprints ignored with the decision id, current log redacted. |
| F-04 | Medium | PIT 1.30.0 and Stryker 10.0.0, first reports `logs/6_backend-mutation-first.xml`, `logs/6_frontend-mutation-first.json` (backend 195 of 218 killed, frontend 91.33 %) | Surviving mutants that a test should have caught, in validation, security and export code: limit boundaries (captcha token, options file, start-up limits, status 299), rate limit pass-through and eviction, export row order, unticking an option, new anti-automation check after a refusal, unknown error field, consent marking. | Fixed by tests (fix loop 2): backend 214 of 219, frontend 96.22 %. The remaining 5 backend and 17 frontend mutants are accepted as Low: two log calls, the disk flush of the JSON copy, an interrupt flag, one equivalent boundary, the page bootstrap (covered end-to-end), and reCAPTCHA widget lifecycle guards that need the real Google script. |
| F-05 | Medium | `npm audit` (npm 10.9.4), `logs/6_frontend-audit.log`: 5 Moderate; GHSA-82fw-gwwq-j7x9 (`@vitest/mocker`), GHSA-x5fp-wj9c-mxmx and GHSA-4mjr-xmp4-gh2g (`qs` through `@stryker-mutator/core`) | Known vulnerabilities in development-only test tooling. | Accepted: not in the shipped image, which holds only the built static files; `npm audit fix` changes nothing without a forced change of the pinned Vitest version (`logs/6_frontend-audit-fix.log`). |
| F-06 | Low | evidence check of the phase 6 card, `git log --numstat` | Nine commits exceed the 400-line guide (402 to 718 lines) without a reason in the message: `df2384a`, `82c33f0`, `df15882`, `2cb41bd`, `418afb9`, `3d54d0d`, `070433a`, `c029814`, `bb705fe`. | Accepted: each is one coherent slice or one test area; history is not rewritten (`rules.md`). |
| F-07 | Medium | phase 5 first complete run, `logs/5_backend-test-first-run.log` | A required field of no-break spaces only was accepted, and such spaces were not trimmed (BR-02). | Fixed in phase 5 (`RegistrationValidator.trim`, Unicode-aware email pattern); `RegistrationValidatorTest` br02. |

## Fix loops

| Loop | Findings | Change | Re-verification |
|---|---|---|---|
| 1 | F-02 | `frontend/nginx.conf`: fixed proxy destination (commit "fix: forward /api to a fixed backend host in nginx") | `verify.sh 4 semgrep`: 0 findings; `stack-up` and `e2e`: 3 passed |
| 2 | F-04 | 14 backend and 12 frontend tests added; the bound of `RateLimitFilter` made settable for tests | `verify.sh 6 backend-test frontend-test backend-mutation frontend-mutation`: 229 and 79 passed; mutation 98 % and 96.22 % |
