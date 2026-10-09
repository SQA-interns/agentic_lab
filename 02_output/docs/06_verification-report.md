# Verification report

> Written in: phase 6 · Source: `general/standards.md`, `project/02_design/*` · Procedure: `general/phases/6-verify.md` · Agent: writes

This is a self-check by the development agent, not an independent review.

## Definition of Done

| DoD | Result | Evidence |
|---|---|---|
| DoD-01 | pass | backend 192/192 (`out/logs/6c_backend-test.log`), frontend 33/33 (`out/logs/6_frontend-test.log`), e2e 17/17 (`out/logs/6_frontend-e2e.log`); 23 frozen hashes match (`out/logs/6_hashes-and-leaks.log`) |
| DoD-02 | pass | Spotless, PMD, CPD, SpotBugs 0 (`out/logs/6c_backend-check.log`); Prettier, ESLint, tsc 0 (`out/logs/6_frontend-check.log`); Semgrep: F-03, F-04 |
| DoD-03 | pass (record only) | backend line 95.9 %, branch 86.1 % (`out/logs/6_backend-coverage.log`); frontend line 97.4 %, branch 88.5 %; mutation backend 69 % unit-only (`out/logs/6b_backend-mutation.log`), frontend 65.3 % (`out/logs/6_frontend-mutation.log`); survivors classified: F-02 |
| DoD-04 | pass | `ArchitectureTest` (AR-01..AR-03 rules, two slice cycle checks) in the backend run |
| DoD-05 | pass | F-03 accepted by the human (D-24); F-01 fixed; no other Critical/High (`out/logs/6_dependency-check.log`, `out/logs/6_npm-audit.log`, `out/logs/6c_gitleaks.log`) |
| DoD-06 | pass | local compose stack, section "Runtime demonstration" (`out/logs/6_runtime-demo.log`) |
| DoD-07 | pass | section "Traceability": every AC has tests and commits |
| DoD-08 | pass | READMEs followed from a clean clone: build, check, 192 + 33 + 17 tests, compose up/down all exit 0; longest path 119 characters (`out/logs/7_clean-checkout.log`) |
| DoD-09 | pass | `docs/release-notes.md`, "Must be tested manually by a human" (7 items) |
| DoD-10 | pass | every decision resolved or pending review; 27 input hashes match (`out/logs/6_hashes-and-leaks.log`) |
| DoD-11 | pass with F-07, F-09 | `out/logs/6_evidence-commits.log`: phase 3 changed no production code; freeze commit = manifest only, all 23 files committed before; each US has a phase 4 commit (`out/logs/4_commits-since-freeze.log`); size guide exceeded by 8 commits (F-07) |
| DoD-P01 | pass | external 201 and student 201 through the frontend `/api` |
| DoD-P02 | pass | both rows in PostgreSQL; both JSON copies on the `json-copies` volume |
| DoD-P03 | pass | 2 participant confirmations and 2 organizer notifications (1 attachment each) in Mailpit |
| DoD-P04 | pass | export 200 valid workbook with both rows; no credentials 401; wrong password 401 |
| DoD-P05 | pass | `Us001RecaptchaVerificationAcceptanceTest` (mock verify endpoint, rejected and accepted token), `CaptchaVerifierTest` |

## Runtime demonstration

| Component | Environment | Flows exercised | Result |
|---|---|---|---|
| backend, frontend, PostgreSQL, Mailpit | local (`02_output/scripts/runtime-demo.sh`) | stack start with health checks; liveness and readiness (NFR-04) | all 4 containers healthy; both probes UP |
| frontend (nginx) | local | page load; SB-10 headers on `/`; KP-02 headers once on `/api` | 200; every header present exactly once |
| backend via frontend `/api` | local | external and student registration with Slovenian text (NFR-01); duplicate; bad anti-automation token | 201, 201, 409, 400 |
| PostgreSQL, volume | local | rows and JSON copies (DoD-P02) | 2 rows, 2 files owned by the non-root `app` user |
| Mailpit | local | participant and organizer emails (DoD-P03) | 4 messages, organizer ones with the JSON attachment |
| backend export | local | organizer export, missing and wrong credentials (DoD-P04) | 200 valid xlsx with both rows and Slovenian text; 401; 401 |
| all | local | `down` then `up` (NFR-02) | 2 rows and 2 JSON copies still present |

## Security and pitfall evidence

| Item | Implemented in | Checked by |
|---|---|---|
| SB-01 | `domain/RegistrationValidator`, `api/RegistrationRequestParser` | `RegistrationValidatorTest`, `RegistrationRequestParserTest`, US-001/002 acceptance |
| SB-02 | `config/SecurityConfig` (export needs role ORGANIZER) | AC-008-03/04 acceptance, DoD-P04 runtime |
| SB-03 | BCrypt in `SecurityConfig`; secrets only from environment (`AppSettings`) | `AppSettingsTest` (no default, length), inspection |
| SB-04 | `OrganizerHttpsFilter`; external TLS proxy | `FiltersTest` (sr06), manual production test in release notes |
| SB-05 | JPA parameters; React escaping; plain-text email; string cells | `PoiWorkbookWriterTest.sb05`, Semgrep 0 code findings |
| SB-06 | `api/RateLimitFilter` | `FiltersTest` (sr03) |
| SB-07 | `api/Problems`, `ApiExceptionHandler.causeChain` | AC-004-03 acceptance, `FiltersTest.es07`, runtime log scan (0 personal data) |
| SB-08 | Dependency-Check, npm audit | 0 Critical/High after D-11, D-12 (`out/logs/6_dependency-check.log`, `out/logs/6_npm-audit.log`) |
| SB-09 | Semgrep, Gitleaks | `out/logs/6_semgrep.log` (F-03, F-04), `out/logs/6c_gitleaks.log` (0) |
| SB-10 | `SecurityConfig` headers; `frontend/nginx.conf` | runtime header checks |
| SB-11 | non-root users in both Dockerfiles | runtime: JSON copies owned by `app`; inspection |
| SB-12, SB-13 | only the fields of `security-requirements.md`; retention D-19 in consent text | `ArchitectureTest` n/a; inspection of `database.sql`, `config/conference.json` |
| SB-14 | consents unticked; stored with wording and time | AC-001-13 e2e, `FileJsonCopyStoreTest`, `JpaRegistrationStoreIntegrationTest` |
| SR-01 | `RegisterParticipant`, `GoogleCaptchaVerifier` | `Us001RecaptchaVerificationAcceptanceTest`, `CaptchaVerifierTest` |
| SR-02 | `AppSettings` guards, `TestModeCaptchaVerifier` | `AppSettingsTest.sr02_*` |
| SR-03 | `RateLimitFilter`, `RequestSizeFilter`, nginx `client_max_body_size` | `FiltersTest` |
| SR-04 | `RegistrationValidator.validateOptions` | AC-001-10/11, AC-002-06 |
| SR-05 | `RegistrationMailer` (body only), email pattern, `CONFERENCE_NAME` guard | `RegistrationMailerTest`, `RegistrationValidatorTest` (header injection input), `AppSettingsTest.sr05` |
| SR-06 | `OrganizerHttpsFilter`, `AppSettings` guard | `FiltersTest.sr06_*`, `AppSettingsTest.sr06_*` |
| SR-07 | `PoiWorkbookWriter` columns | AC-008-01 acceptance, `PoiWorkbookWriterTest` |
| KP-01 | `nginx.conf`: fixed `Host backend`, `absolute_redirect off` | inspection, Semgrep 0 nginx findings |
| KP-02 | headers in each static `location`, none on `/api` | runtime header counts |
| KP-03 | `Text.trim`, JS `trim()` | `TextTest`, AC-001-05 acceptance and e2e |
| KP-04 | `.gitattributes`; LF hashing | manifest checks |
| KP-05 | short paths (longest under `02_output/` checked in phase 7) | phase 7 README |
| KP-06 | not needed: Testcontainers reached Docker | backend runs |
| KP-07 | `dependency-check-suppressions.xml` (D-03, D-04) | `out/logs/6_dependency-check.log` |
| KP-08 | e2e global setup starts its own stack on free ports | `out/logs/6_frontend-e2e.log` |

## Traceability

| AC | Tests | Commits |
|---|---|---|
| AC-001-01 | RegistrationForm.test.tsx, Us001ExternalRegistrationAcceptanceTest, us001-external.e2e.spec.ts | f1888bf, 9f7466f, 9c91e44, d7d31ee |
| AC-001-02 | RegisterParticipantTest, RegistrationValidatorTest, Us001ExternalRegistrationAcceptanceTest, us001-external.e2e.spec.ts, validation.test.ts | f1888bf, 9f7466f, 9c91e44, d7d31ee |
| AC-001-03 | Us001ExternalRegistrationAcceptanceTest | f1888bf, 9f7466f, 9c91e44, d7d31ee |
| AC-001-04 | RegistrationRequestParserTest, RegistrationValidatorTest, Us001ExternalRegistrationAcceptanceTest, us001-external.e2e.spec.ts, validation.test.ts | f1888bf, 9f7466f, 9c91e44, d7d31ee |
| AC-001-05 | Us001ExternalRegistrationAcceptanceTest, us001-external.e2e.spec.ts, validation.test.ts | f1888bf, 9f7466f, 9c91e44, d7d31ee |
| AC-001-06 | RegistrationValidatorTest, Us001ExternalRegistrationAcceptanceTest | f1888bf, 9f7466f, 9c91e44, d7d31ee |
| AC-001-07 | RegistrationValidatorTest, Us001ExternalRegistrationAcceptanceTest, validation.test.ts | f1888bf, 9f7466f, 9c91e44, d7d31ee |
| AC-001-08 | RegistrationValidatorTest, Us001ExternalRegistrationAcceptanceTest | f1888bf, 9f7466f, 9c91e44, d7d31ee |
| AC-001-09 | ConferenceCatalogueTest, RegistrationForm.test.tsx, Us001ExternalRegistrationAcceptanceTest, us001-external.e2e.spec.ts | f1888bf, 9f7466f, 9c91e44, d7d31ee |
| AC-001-10 | RegistrationValidatorTest, Us001ExternalRegistrationAcceptanceTest | f1888bf, 9f7466f, 9c91e44, d7d31ee |
| AC-001-11 | Us001ExternalRegistrationAcceptanceTest | f1888bf, 9f7466f, 9c91e44, d7d31ee |
| AC-001-12 | RegistrationForm.test.tsx, RegistrationValidatorTest, Us001ExternalRegistrationAcceptanceTest, validation.test.ts | f1888bf, 9f7466f, 9c91e44, d7d31ee |
| AC-001-13 | RegistrationForm.test.tsx, Us001ExternalRegistrationAcceptanceTest, us001-external.e2e.spec.ts | f1888bf, 9f7466f, 9c91e44, d7d31ee |
| AC-001-14 | RegistrationValidatorTest, Us001ExternalRegistrationAcceptanceTest, us001-external.e2e.spec.ts, validation.test.ts | f1888bf, 9f7466f, 9c91e44, d7d31ee |
| AC-001-15 | RegisterParticipantTest, Us001ExternalRegistrationAcceptanceTest | f1888bf, 9f7466f, 9c91e44, d7d31ee |
| AC-001-16 | RegisterParticipantTest, Us001ExternalRegistrationAcceptanceTest, Us001RecaptchaVerificationAcceptanceTest, us001-external.e2e.spec.ts, validation.test.ts | f1888bf, 9f7466f, 9c91e44, d7d31ee |
| AC-002-01 | Us002StudentRegistrationAcceptanceTest, us002-student.e2e.spec.ts | f1888bf, 9f7466f, 9c91e44, 3447366 |
| AC-002-02 | RegistrationValidatorTest, Us002StudentRegistrationAcceptanceTest, us002-student.e2e.spec.ts | f1888bf, 9f7466f, 9c91e44, 3447366 |
| AC-002-03 | Us002StudentRegistrationAcceptanceTest, us002-student.e2e.spec.ts | f1888bf, 9f7466f, 9c91e44, 3447366 |
| AC-002-04 | Us002StudentRegistrationAcceptanceTest | f1888bf, 9f7466f, 9c91e44, 3447366 |
| AC-002-05 | Us002StudentRegistrationAcceptanceTest, us002-student.e2e.spec.ts | f1888bf, 9f7466f, 9c91e44, 3447366 |
| AC-002-06 | RegistrationValidatorTest, Us002StudentRegistrationAcceptanceTest | f1888bf, 9f7466f, 9c91e44, 3447366 |
| AC-002-07 | Us002StudentRegistrationAcceptanceTest | f1888bf, 9f7466f, 9c91e44, 3447366 |
| AC-002-08 | Us002StudentRegistrationAcceptanceTest | f1888bf, 9f7466f, 9c91e44, 3447366 |
| AC-002-09 | Us002StudentRegistrationAcceptanceTest | f1888bf, 9f7466f, 9c91e44, 3447366 |
| AC-003-01 | Us003ConfigurableOptionsAcceptanceTest, us003-options.e2e.spec.ts | 45da222, e8d3fc7 |
| AC-003-02 | Us003ConfigurableOptionsAcceptanceTest | 45da222, e8d3fc7 |
| AC-003-03 | Us003ConfigurableOptionsAcceptanceTest | 45da222, e8d3fc7 |
| AC-003-04 | Us003ConfigurableOptionsAcceptanceTest, us003-options.e2e.spec.ts | 45da222, e8d3fc7 |
| AC-004-01 | App.test.tsx, RegistrationForm.test.tsx, Us004ConfirmationAcceptanceTest, us001-external.e2e.spec.ts, us002-student.e2e.spec.ts, us004-confirmation.e2e.spec.ts | f1888bf, 17a60ff |
| AC-004-02 | RegistrationForm.test.tsx, Us004ConfirmationAcceptanceTest, us004-confirmation.e2e.spec.ts | f1888bf, 17a60ff |
| AC-004-03 | RegistrationForm.test.tsx, Us004ConfirmationAcceptanceTest, api.test.ts, us004-confirmation.e2e.spec.ts | f1888bf, 17a60ff |
| AC-005-01 | Us005StorageAcceptanceTest | 2332f2a, 65879fa |
| AC-005-02 | Us005StorageAcceptanceTest | 2332f2a, 65879fa |
| AC-005-03 | FileJsonCopyStoreTest, RegisterParticipantTest, Us005StorageAcceptanceTest | 2332f2a, 65879fa |
| AC-005-04 | Us005StorageAcceptanceTest | 2332f2a, 65879fa |
| AC-005-05 | Us005StorageAcceptanceTest | 2332f2a, 65879fa |
| AC-006-01 | Us006ParticipantEmailAcceptanceTest | e7cc7c9, 93efb27, 602ecba |
| AC-006-02 | Us006ParticipantEmailAcceptanceTest | e7cc7c9, 93efb27, 602ecba |
| AC-006-03 | Us006ParticipantEmailAcceptanceTest | e7cc7c9, 93efb27, 602ecba |
| AC-006-04 | Us006ParticipantEmailAcceptanceTest | e7cc7c9, 93efb27, 602ecba |
| AC-007-01 | Us007OrganizerNotificationAcceptanceTest | e7cc7c9, 4ad93e5, 602ecba |
| AC-007-02 | Us007OrganizerNotificationAcceptanceTest | e7cc7c9, 4ad93e5, 602ecba |
| AC-007-03 | Us007OrganizerNotificationAcceptanceTest | e7cc7c9, 4ad93e5, 602ecba |
| AC-007-04 | Us007OrganizerNotificationAcceptanceTest | e7cc7c9, 4ad93e5, 602ecba |
| AC-008-01 | JpaRegistrationStoreIntegrationTest, PoiWorkbookWriterTest, Us008ExportAcceptanceTest | dd398f8, b9f52f3, 2ba5bbf |
| AC-008-02 | PoiWorkbookWriterTest, Us008ExportAcceptanceTest | dd398f8, b9f52f3, 2ba5bbf |
| AC-008-03 | Us008ExportAcceptanceTest | dd398f8, b9f52f3, 2ba5bbf |
| AC-008-04 | Us008ExportAcceptanceTest | dd398f8, b9f52f3, 2ba5bbf |
| AC-008-05 | Us008ExportAcceptanceTest | dd398f8, b9f52f3, 2ba5bbf |

## Findings

| F | Severity | Source | Finding | Resolution |
|---|---|---|---|---|
| F-01 | High | backend-test (AC-006-04 red in 6b) | SMTP read timeout 10 s drops emails when the server greets slowly (5 s stalls observed) | fixed: 60 s read/write, 10 s connect; loop 1 |
| F-02 | Low | Pitest, Stryker | surviving mutants a unit test could catch (filter pass-through, parser and loader boundaries, client max length) | partly fixed (filters, parser); rest accepted: behaviour covered by acceptance tests |
| F-03 | High → Low (D-24) | Semgrep `use-of-basic-authentication` (ERROR) | organizer export uses HTTP Basic (contract line 127) | accepted by the human (D-24, option 1): HTTPS enforced, BCrypt, rate limit; rule suppressed on that line only (`out/logs/6d_semgrep.log`) |
| F-04 | Medium | Semgrep `npm-missing-minimum-release-age` | `.npmrc` sets no minimum release age | accepted: exact pins, committed lock file, `npm ci` |
| F-05 | Medium | npm audit | 2 Moderate in `qs` via Stryker (dev only, D-13) | accepted: not shipped |
| F-06 | Low | Gitleaks | test-only value in `AppSettingsTest` | false positive, suppressed (D-25) |
| F-07 | Low | commit check | 8 commits exceed 15 files / 400 lines without a stated reason | accepted: history not rewritten |
| F-08 | Low | jscpd | 1 clone (0.58 %) in frontend test code | accepted |
| F-09 | Low | commit check | commit 45da222 does not build alone (D-22) | accepted |

## Fix loops

| Loop | Findings | Change | Re-verification |
|---|---|---|---|
| 1 | F-01 | `application.yml` SMTP timeouts | US-006/007 3 runs 8/8 (`out/logs/6_f01-rerun-*.log`); backend 192/192 (`out/logs/6c_backend-test.log`) |
