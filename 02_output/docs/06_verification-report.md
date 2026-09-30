# Verification report

> Written in: phase 6 · Source: `general/quality/*`, `general/security/*`, `project/02_design/*` · Procedure: `general/skills/verify-release` · Agent: writes

This is a self-check by the development agent, not an independent review.

Verified on 2026-09-30 at commit `45d1ce3` plus this report. Raw outputs are in `logs/06_*`.

## Summary

- Frozen tests: both manifests match (`logs/06_hash-check.log`): input manifest 25/25 files, acceptance manifest 40/40 files. The only change to a frozen file after the freeze is the human-approved D-12 correction (`37753fa`, manifest `7413cf0`).
- Final full run: **281 passed, 0 failed** (backend 228 = 105 acceptance + 123 unit/integration/architecture; frontend 45 unit/component; 8 end-to-end).
- Findings: 0 Critical, 0 High, 6 Medium, 4 Low. Fixed: F-01, F-05, F-06, F-09. Accepted with reasons: F-02, F-03, F-04, F-07, F-08, F-10. No open Critical or High finding.
- Runtime demonstration on the local compose stack: all core flows work; data survives container recreation.

## Definition of Done

| DoD | Result | Evidence |
|---|---|---|
| DoD-01 | Met | Final run 281/281 (`logs/06_final-run-backend.log`, `06_final-run-frontend.log`, `06_final-run-e2e.log`); manifest hashes match (`logs/06_hash-check.log`) |
| DoD-02 | Met | Backend `./mvnw verify`: Spotless check, PMD (0 violations), CPD (0 duplications ≥ 100 tokens), SpotBugs (`BugInstance size is 0`), BUILD SUCCESS (`logs/06_final-run-backend.log`). Frontend `npm run check`: Prettier, ESLint (0 problems), `tsc --noEmit` clean (`logs/06_final-check-frontend.log`). Semgrep: 1 remaining Medium, accepted (F-02) |
| DoD-03 | Met (record only) | Backend line/branch coverage: unit 81.7 % / 92.7 %, acceptance 91.3 % / 77.2 %, combined 96.6 % / 95.9 % (`logs/06_coverage.log`, `06_jacoco-*.csv`). Frontend (Vitest): lines 92.4 %, branches 99.2 % (`logs/06_frontend-coverage.log`). Mutation: backend PIT 84 % (273/325, test strength 96 %) after F-05 (`logs/06_pitest-rerun-F05.log`); frontend Stryker 84.2 % after F-06 (`logs/06_stryker-rerun-F06.log`). Survivors classified in F-05 and F-06 |
| DoD-04 | Met | `ArchitectureTest` (ArchUnit) checks rules A1–A6 of `02_specification.md` §2, including the slice cycle check (AR-03): 9 rules pass |
| DoD-05 | Met | No open Critical/High: Dependency-Check 0 open vulnerabilities (2 approved suppressions, D-04, D-05), npm audit 0 high/critical, Semgrep 0 ERROR, gitleaks: no real secret (F-04), `.env` leak check clean |
| DoD-06 | Met | Runtime demonstration below (`logs/06_runtime-demo.log`) and e2e against the running stack |
| DoD-07 | Met | Traceability table below: every AC has at least one test and one commit |
| DoD-08 | Checked in phase 7 | Clean-checkout run of the READMEs (`logs/07_clean-checkout.log`) |
| DoD-09 | Checked in phase 7 | `docs/release-notes.md`, "Must be tested manually by a human" |
| DoD-10 | Met | Every decision D-01 … D-12 has a resolution or is marked pending review (D-06 … D-11, open questions); input manifest unchanged |
| DoD-11 | Met with exception F-07 | Phase 3 commits add no production code (`git log 6f7c372..7e5e2b4 -- backend/src/main frontend/src` is empty); the freeze commit `7e5e2b4` adds only the manifest and every listed file was committed earlier in phase 3; every story has a build commit naming it; manifest hashes match. Ten commits exceed the size guide without a stated reason (F-07, `logs/06_commit-size-check.log`) |
| DoD-P01 | Met | Demo §4–5: external and student registration via the frontend proxy, both 201 |
| DoD-P02 | Met | Demo §7–8: rows in `registration`, `registration_option`, `registration_consent`; files `<id>.json` on volume `jsondata` |
| DoD-P03 | Met | Demo §9: participant emails to both addresses; organizer emails with `registration-<id>.json` (`application/json`) in Mailpit |
| DoD-P04 | Met | Demo §10: 200 `.xlsx` with header and both rows; 401 without and with wrong credentials. HTTPS-only refusal (403) is covered by `ExportHttpsOnlyAcceptanceTest` because the local stack runs plain HTTP |
| DoD-P05 | Met | `RecaptchaVerificationAcceptanceTest` (accepted token, rejected token, HTTP 500, test token outside test mode) and `RecaptchaUnreachableAcceptanceTest` against a mocked siteverify endpoint; `CaptchaVerifierTest` (unit) |

## Runtime demonstration

Stack started with `docker compose --env-file ../.env up -d --build` from `02_output/` after `./mvnw clean package -DskipTests`. Script output: `logs/06_runtime-demo.log`.

| Component | Environment | Flows exercised | Result |
|---|---|---|---|
| all | local compose | Container health checks; `/api/health`, `/liveness`, `/readiness` via nginx (NFR-04, ES-09); processes run as `app` and `nginx` (SB-11) | all healthy, 200 `UP` |
| frontend (nginx) | local compose | `GET /` headers: CSP, `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy` (SB-10) | present |
| backend | local compose | `GET /api/options`, `/api/config` (test mode, empty site key) | 200 |
| backend | local compose | External registration with č, š, ž (DoD-P01, NFR-01) | 201, stored unchanged |
| backend | local compose | Student registration (DoD-P01) | 201 |
| backend | local compose | Wrong reCAPTCHA token 400; duplicate email 409; inactive option 400; 70 kB body 413 (nginx); 20 kB body direct 413 (backend) | as specified |
| PostgreSQL | local compose | Rows, options with names and positions, consents with `given_at`; Flyway history V1 (DoD-P02, AR-06) | present |
| backend volume | local compose | `/data/json/<id>.json`, UTF-8, schema order, owner `app`, mode 600 (DoD-P02) | present |
| Mailpit | local compose | Participant and organizer emails, JSON attachment (DoD-P03) | delivered |
| backend | local compose | Export: 401 without / wrong credentials; 200 with credentials, `Content-Disposition`, `Cache-Control: no-store`; workbook opened and rows read (DoD-P04) | as specified |
| backend logs | local compose | Participant emails or names in logs: 0; `.env` values in logs: 0; only `Registration <id> accepted` (ES-07) | clean |
| all | local compose | `docker compose down` then `up -d`: both rows and both JSON copies still present, readiness UP (NFR-02) | persisted |
| frontend (browser) | local compose | Playwright e2e: forms, options, consents, confirmation, errors, NFR-01 end to end (UI → DB → JSON → emails → export) | 8/8 passed |

## Security (SB and SR)

| Item | Where implemented | How checked |
|---|---|---|
| SB-01 | `RegistrationValidator` (server side) | Acceptance US-001/US-002 rejection tests; `RegistrationValidatorTest` |
| SB-02 | `SecurityConfig` (organizer role for `/api/organizer/**`, deny all else) | AC-008-02 tests; demo §10 |
| SB-03 | Organizer password BCrypt-hashed at startup (`SecurityConfig.organizerAccount`); secrets only from environment, no defaults | Inspection; `StartupChecksTest` (empty secrets refused; no values in messages or `toString`) |
| SB-04 | TLS at the production reverse proxy (environments.md); SMTP STARTTLS required by default in production (F-09) | `ProductionProfileIntegrationTest`; manual test listed in release notes |
| SB-05 | JPA parameter binding; React text rendering only; string-only workbook cells; plain-text emails | AC-006-02, AC-008-01 (formula text kept as text), `MailAndWorkbookTest`; Semgrep 0 injection findings |
| SB-06 | `RateLimitFilter` (registration, export incl. failed logins, read) | AC-001-10, AC-008-04; `WebLayerTest` |
| SB-07 | `ApiExceptionHandler`, security handlers, id-only logging | AC-005-03 (`assertNoInternals`); demo §11 log scan |
| SB-08 | Dependency-Check (NVD) and npm audit | `logs/06_dependency-check.log` (0 open), `logs/06_npm-audit.json` (F-03); images not scanned (F-08) |
| SB-09 | Semgrep, SpotBugs, PMD, gitleaks | `logs/06_semgrep*.json`, `06_final-run-backend.log`, `06_gitleaks*.json` |
| SB-10 | Spring Security headers (backend), nginx headers (frontend) | Demo §2 |
| SB-11 | Non-root users in both images | Demo §1 (`id -un`) |
| SB-12 | Only the fields of BR-01 are collected; request rejects unknown fields | AC-001-14; `RegistrationRequest` |
| SB-13 | Purpose per `security-requirements.md`; retention D-11 (pending review) | Decisions log; release notes |
| SB-14 | Consent checkboxes unchecked; `given_at` stored per consent | AC-001-12, AC-002-03, AC-005-01 |
| SR-01 | `GoogleCaptchaVerifier` on every registration; frontend widget | AC-001-07, AC-001-08; DoD-P05 |
| SR-02 | `StartupChecks`, `app.recaptcha.test-mode` default false, production profile | AC-001-15 (8 tests) |
| SR-03 | `RateLimitFilter`, `RequestSizeFilter` (16 KiB), nginx 64 KiB | AC-001-10, AC-001-11, AC-008-04; demo §6 |
| SR-04 | `RegistrationValidator.options` against the active catalog | AC-001-05, `OptionsConfigurationAcceptanceTest` |
| SR-05 | Control characters rejected in every field; plain-text emails; no user input in headers except the validated address | AC-006-02 (5 tests) |
| SR-06 | `OrganizerHttpsFilter` before authentication; `request.isSecure()` via trusted-proxy valve | AC-008-03; F-10 (deployment condition) |
| SR-07 | `RegistrationCopy`, workbook columns, organizer email body | AC-007-02, AC-008-01 |

## Traceability

| AC | Tests | Commits |
|---|---|---|
| AC-001-01 … AC-001-07, AC-001-09 | `ExternalRegistrationAcceptanceTest` (ac001_01 … ac001_07, ac001_09) | `fd1c085`, `25cfd61`, `bd4b352` |
| AC-001-08 | `RecaptchaVerificationAcceptanceTest`, `RecaptchaUnreachableAcceptanceTest` | `fd1c085`, `bd4b352`, `25cfd61` |
| AC-001-10 | `RegistrationRateLimitAcceptanceTest` | `fd1c085`, `5c9c276` |
| AC-001-11 | `ExternalRegistrationAcceptanceTest.ac001_11` | `fd1c085`, `5c9c276` |
| AC-001-12 | `us001-external-form.e2e.spec.ts` | `fd1c085`, `fecf1af` |
| AC-001-13 | `ExternalRegistrationAcceptanceTest.ac001_13`, `us001-external-form.e2e.spec.ts` (NFR-01) | `fd1c085`, `25cfd61`, `fecf1af`, `8beecc7` |
| AC-001-14 | `ExternalRegistrationAcceptanceTest.ac001_14` | `fd1c085`, `25cfd61` |
| AC-001-15 | `StartupConfigurationAcceptanceTest` | `fd1c085`, `bd4b352` |
| AC-002-01, AC-002-02 | `StudentRegistrationAcceptanceTest` | `1824a44`, `25cfd61` |
| AC-002-03 | `us002-student-form.e2e.spec.ts` | `1824a44`, `fecf1af` |
| AC-003-01 | `OptionsAcceptanceTest` | `2fa0316`, `b7de8df` |
| AC-003-02 | `OptionsConfigurationAcceptanceTest.ac003_02` | `2fa0316`, `b7de8df`, `25cfd61` |
| AC-003-03 | `OptionsConfigurationAcceptanceTest.ac003_03` | `2fa0316`, `b7de8df` |
| AC-003-04 | `us003-options.e2e.spec.ts` | `2fa0316`, `fecf1af` |
| AC-004-01 … AC-004-03 | `us004-confirmation.e2e.spec.ts` | `d690e8b`, `fecf1af`, `8beecc7` |
| AC-005-01, AC-005-02 | `StorageAcceptanceTest` | `352a98a`, `37753fa`, `87eb3d4`, `25cfd61` |
| AC-005-03 | `JsonCopyFailureAcceptanceTest` | `352a98a`, `25cfd61` |
| AC-006-01, AC-006-02 | `ParticipantEmailAcceptanceTest` | `52317e5`, `1f01b39`, `25cfd61` |
| AC-006-03 | `SmtpFailureAcceptanceTest` | `52317e5`, `1f01b39` |
| AC-007-01, AC-007-02 | `OrganizerNotificationAcceptanceTest` | `bf95d80`, `b767bc1` |
| AC-008-01, AC-008-02 | `ExportAcceptanceTest` | `2845805`, `db0abbd` |
| AC-008-03 | `ExportHttpsOnlyAcceptanceTest` | `2845805`, `db0abbd` |
| AC-008-04 | `ExportRateLimitAcceptanceTest` | `2845805`, `5c9c276`, `db0abbd` |

## Findings

| F | Severity | Source | Finding | Resolution |
|---|---|---|---|---|
| F-01 | Medium | Semgrep `request-host-used` (WARNING) | `frontend/nginx.conf` forwarded the client-controlled `Host` header to the backend | Fixed in `e27d899`; Semgrep re-run clean for this rule, e2e 8/8 (`logs/06_semgrep-rerun-F01.json`, `06_e2e-rerun-F01.log`) |
| F-02 | Medium | Semgrep `npm-missing-minimum-release-age` | `.npmrc` sets no `min-release-age` | Accepted: the setting needs npm ≥ 11.10, the pinned npm is 11.6.2 (changing it needs human approval); every version is exact, `package-lock.json` is committed and the image build uses `npm ci`. Listed in release notes |
| F-03 | Medium | npm audit (moderate) | 5 moderate advisories, all in dev/test tooling: `vitest`/`@vitest/mocker` (GHSA-82fw-gwwq-j7x9), `@vitest/coverage-v8`, `qs` via `typed-rest-client` | Accepted: not part of the shipped bundle (`npm audit --omit=dev`: 0); fixing needs tech-stack version changes (human approval). Listed in release notes |
| F-04 | Low | gitleaks `generic-api-key` | 22 matches in committed `logs/03_backend-acceptance-run.log`: Spring Boot's random "Using generated security password" of the phase 3 skeleton inside test JVMs | Accepted: throwaway per-run values of a test process, not credentials of any environment; the `.env` leak check finds nothing (`logs/06_secret-leak-check.log`); history is not rewritten. Matches on other branches of this repository are outside this run |
| F-05 | Low | PIT | Surviving mutants that unit tests should have caught: validator email result, options-file consent field check and name boundary, `Registration` getters, organizer email options, sender, subject and body, workbook row order | Fixed in `6610957` (stronger unit tests); PIT re-run 84 % (`logs/06_pitest-rerun-F05.log`). Remaining 10 survivors accepted as equivalent or cosmetic: `return null` → `""` on paths that always throw (validator), rate-limit map purge and window boundary (same observable result), request stream `isFinished`/zero-length read, response charset (body is ASCII-escaped JSON), `toString` of settings (`logs/06_pitest-survivors-after-F05.txt`) |
| F-06 | Low | Stryker | Frontend email pattern without end anchor survived (validation code) | Fixed in `45d1ce3`; `validation.ts` 100 %. Remaining 52 survivors accepted: labels and literals checked by the frozen e2e tests, reCAPTCHA widget guards that only matter with the real Google script (manual test), radio switch-back wiring (`logs/06_stryker-survivors.txt`) |
| F-07 | Low | Commit guide (`working-rules.md`) | 10 commits exceed ~15 files / 400 lines and 10 subjects exceed 72 characters, without a stated reason (`logs/06_commit-size-check.log`) | Accepted: history is not rewritten. Reasons: vendored Maven wrapper script (`b2e5768`), one coherent test harness (`00cbfb9`), per-story test sets and features; long subjects are ID lists |
| F-08 | Medium | Security review (SB-08) | Container images (postgres, temurin, nginx, node, mailpit) are not scanned: `tech-stack.md` lists no image scanner | Accepted as open item: adding a scanner is a tech-stack change needing human approval; release notes require an image scan before production |
| F-09 | Medium | Security review (SB-04, configuration.md) | Production profile left SMTP STARTTLS off unless `SMTP_TLS` was set | Fixed in `b1cf717`; `ProductionProfileIntegrationTest` and AC-001-15 pass (`logs/06_rerun-F09.log`) |
| F-10 | Medium | Security review (SR-06, SB-06) | Tomcat trusts `X-Forwarded-For`/`-Proto` from all private-range addresses (default internal proxies). A client that can reach the backend port directly from a private network could spoof its address (rate limit) or HTTPS (organizer HTTPS-only) | Accepted with deployment condition: the backend port must be reachable only from the reverse proxy (local compose binds it to 127.0.0.1; production runs it only on the container network). Listed in release notes and backend README |

## Fix loops

| Loop | Findings | Change | Re-verification |
|---|---|---|---|
| 1 | F-01 | Removed `proxy_set_header Host $host` (`e27d899`) | Semgrep re-run: rule no longer reported; e2e 8/8 through nginx |
| 2 | F-09 | `application-production.yml`: `mail.smtp.starttls.enable/required: ${SMTP_TLS:true}` plus integration test (`b1cf717`) | `ProductionProfileIntegrationTest` 1/1, `StartupConfigurationAcceptanceTest` 8/8 |
| 3 | F-05 | Strengthened backend unit tests (`6610957`) | PIT 81 % → 84 %, test strength 93 % → 96 % |
| 4 | F-06 | Added unanchored-email cases (`45d1ce3`) | Stryker 83.9 % → 84.2 %, `validation.ts` 100 % |

After the loops: final full run 281/281, backend `verify` BUILD SUCCESS, frontend check clean, runtime demonstration repeated on the rebuilt images.

## Decisions

All records D-01 … D-12 have a resolution: D-04 and D-12 were answered by the human; D-06 … D-11 (open questions OQ-01 … OQ-06) are resolved as "pending review" with the implemented option named, and are listed in the release notes.
