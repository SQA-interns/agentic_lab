# Verification report

> Written in: phase 6 · Source: `general/standards.md`, `project/02_design/*` · Procedure: `general/phases/6-verify.md` · Agent: writes

This is a self-check by the development agent, not an independent review.

Run: 2026-10-05, `verify.sh 6` (all default tools) plus `verify.sh 6 e2e`; summary in `out/logs/6_verify-summary.log`, tool logs `out/logs/6_*.log`.

## Definition of Done

| DoD | Result | Evidence |
|---|---|---|
| DoD-01 | met | backend 183/183 (65 acceptance), frontend 42/42, end-to-end 15/15 (`6_be-test.log`, `6_fe-test.log`, `6_e2e.log`); 26/26 frozen hashes match (`6_hashes-and-leak-check.log`) |
| DoD-02 | met | Spotless, PMD 0, CPD 0, SpotBugs 0 (`6_be-check.log`); Prettier, ESLint, tsc clean (`6_fe-check.log`); Semgrep 0 High (F-01 Medium accepted) |
| DoD-03 | met (record only) | backend 97.1% lines / 91.8% branches, frontend 100% / 95.6%; mutation PIT 87%, Stryker 91% (`03_test-strategy.md`, `6_be-mutation.log`, `6_fe-mutation.log`) |
| DoD-04 | met | `ArchitectureTest`: 5 rules (layers, no slice cycles, no web in core, domain libraries, external libraries in infrastructure) pass in `6_be-test.log` |
| DoD-05 | met | Dependency-Check 0, npm audit 0 Critical/High, Semgrep 0 High, gitleaks no leaks; no open Critical/High in Findings |
| DoD-06 | met | local stack: every container healthy and non-root, core flows at runtime (`6_runtime-demo.log`, `6_e2e.log`) |
| DoD-07 | met | Traceability below: 48/48 AC → test → commit |
| DoD-08 | phase 7 | READMEs from a clean checkout |
| DoD-09 | phase 7 | `docs/release-notes.md` |
| DoD-10 | met | D-01 … D-17: D-15 resolved by the human, all others pending review; 27/27 input hashes match |
| DoD-11 | met with finding | phase 3 adds no production code, freeze commit 5ee3c71 holds only the manifest, all its files existed before; every US has a phase 4 commit (`4_commits-since-freeze.log`); 8 commits exceed the size guide (F-05) (`6_evidence-checks.log`) |
| DoD-P01 | met | external and student registration through the frontend `/api`: 201 each (`6_runtime-demo.log`) |
| DoD-P02 | met | both rows in PostgreSQL with options; both JSON copies in `/data/json-copies`; still present after `compose down`/`up` (NFR-02) |
| DoD-P03 | met | Mailpit: participant mail per registration, organizer mail with `registration-<id>.json` (`application/json`) |
| DoD-P04 | met | refused without access, with forged token, with wrong credentials: 401 (`6_runtime-demo.log`); download and opened workbook with organizer token: e2e AC-001-06 against the same stack (`6_e2e.log`), AC-008-01 |
| DoD-P05 | met | `RecaptchaVerifierTest`: accepted token, rejected token, server error, bad body, timeout, unreachable, against a JDK `HttpServer` implementing `recaptcha-siteverify.openapi.yaml` |

## Runtime demonstration

| Component | Environment | Flows exercised | Result |
|---|---|---|---|
| all | local (`docker compose`, 127.0.0.1) | `docker ps`: 4 containers healthy; `/actuator/health/liveness` and `/readiness` UP (NFR-04) | pass |
| frontend | local | `/` 200 with CSP, `X-Frame-Options`, `nosniff`, `Referrer-Policy`; runs as uid 101 | pass |
| backend | local | runs as uid 10001; form config (7 active options, test mode); 2 registrations 201; failed captcha 400 | pass |
| backend + PostgreSQL + volume | local | rows and JSON copies present; same after recreating every container | pass |
| backend + Mailpit | local | participant and organizer mails, JSON attachment | pass |
| backend | local | export refused without access (401 ×3); authorized export in e2e AC-001-06 | pass |
| backend | production profile, unsafe settings (dummy values) | refuses to start: "RECAPTCHA_TEST_MODE …; ORGANIZER_HTTPS_ONLY …; CONFERENCE_OPTIONS_FILE …"; without an options file it stops at the options loader | pass (F-04) |
| browser | local, Playwright container | 15 end-to-end tests incl. Slovenian characters through form, mails, JSON copy, export (NFR-01) | pass |

## Security controls

| Item | Implemented in | Checked by |
|---|---|---|
| SB-01 | `RegistrationValidator`, `RegistrationRequestReader`, `JsonOptionsFile` | unit tests, acceptance US-001/US-002, mutation |
| SB-02 | `OrganizerController` (token, bearer), `SecurityConfig` (deny by default) | AC-008-02/03, `HttpSecurityIntegrationTest` |
| SB-03 | `OrganizerAccessService` (BCrypt 12, SHA-256 token hashes); secrets only from environment | `OrganizerAccessServiceTest`, gitleaks, leak-check |
| SB-04 | SMTP STARTTLS required (`MailConfig`); HTTPS at the external proxy; SR-06 check | inspection, `HttpSecurityIntegrationTest`; real TLS: manual (release notes) |
| SB-05 | JPA parameters, React escaping, string-only Excel cells | `PoiRegistrationExporterTest` (formula text stays a string), Semgrep |
| SB-06 | `RequestLimitsFilter` | `RequestLimitsFilterTest`, `HttpSecurityIntegrationTest` |
| SB-07 | `ApiExceptionHandler`, `Problem`, logging of ids only | AC-004-03, `6_log-privacy-check.log` (0 emails, names, stack traces) |
| SB-08 | Dependency-Check, npm audit | `6_be-depcheck.log`, `6_fe-audit.log`; images not scanned (F-03) |
| SB-09 | Semgrep, gitleaks (branch history and working tree) | `6_semgrep.log`, `6_gitleaks.log` |
| SB-10 | `SecurityConfig` headers, nginx template | `HttpSecurityIntegrationTest`, runtime headers |
| SB-11 | non-root images (uid 10001, nginx uid 101) | runtime `id` |
| SB-12, SB-13 | only BR-01 fields + options + consent; purposes in `security-requirements.md`, retention D-11 | inspection of `registration-storage.sql`, `registration-copy.schema.json` |
| SB-14 | consent unchecked, required | AC-001-11/12, AC-002-07, component test |
| SR-01 | `RecaptchaVerifier` on the backend for every registration | AC-001-13, AC-002-11, `RecaptchaVerifierTest` (DoD-P05) |
| SR-02 | `StartupGuards`, test mode off by default | `StartupGuardsTest`, runtime refusal |
| SR-03 | `RequestLimitsFilter`, nginx `client_max_body_size` | `RequestLimitsFilterTest`, `HttpSecurityIntegrationTest` (413, 429) |
| SR-04 | `RegistrationValidator.options` | AC-001-07/08, AC-002-10, unit tests |
| SR-05 | `MailRegistrationNotifier` (text/plain, no user input in headers) | `MailRegistrationNotifierTest` (CR/LF in a name adds no header), AC-006-01 |
| SR-06 | `OrganizerController.requireHttps` | `HttpSecurityIntegrationTest` (403 via proxy over HTTP, 200 with HTTPS) |
| SR-07 | `PoiRegistrationExporter`, organizer mail text | AC-008-01, AC-007-01, `PoiRegistrationExporterTest` |
| leak check | `secrets.sh leak-check` | no file, 0 commit-message lines (`6_hashes-and-leak-check.log`) |

## Traceability

| AC | Tests | Commits |
|---|---|---|
| AC-001-01 … AC-001-14 | `Us001ExternalRegistrationAcceptanceTest.ac001_nn`; e2e `us001-external-registration.spec.ts` (-01, -02, -03, -05, -06, -11, -12, -14) | tests b95cf96; code 62f39d3, d31ad3a, 3342e47, 9d44a34, 6533aa4, f8656d2, c5179a4 |
| AC-002-01 … AC-002-11 | `Us002StudentRegistrationAcceptanceTest.ac002_nn`; e2e `us002-student-registration.spec.ts` (-01, -02, -08) | tests 8a7bf39; code d31ad3a, 3342e47, 9d44a34, 6533aa4 |
| AC-003-01 … AC-003-04 | `Us003ConfigurableOptionsAcceptanceTest.ac003_nn`; e2e `us003-configurable-options.spec.ts` (-01, -02) | tests 6bf583e; code 62f39d3, 49bb0ec, 6533aa4 |
| AC-004-01 … AC-004-03 | `Us004ConfirmationAcceptanceTest.ac004_nn`; e2e `us004-confirmation.spec.ts` (-01, -02) | tests 6115164; code 3342e47, 6533aa4 |
| AC-005-01 … AC-005-04 | `Us005StorageAcceptanceTest.ac005_nn` (AC-005-03 twice) | tests 60b1208, 941b362 (D-15); code 49bb0ec, d31ad3a |
| AC-006-01 … AC-006-03 | `Us006ParticipantEmailAcceptanceTest.ac006_nn`; e2e AC-001-06 | tests bc2aedb; code ff91898 |
| AC-007-01 … AC-007-03 | `Us007OrganizerNotificationAcceptanceTest.ac007_nn`; e2e AC-001-06 | tests 46c978f; code ff91898 |
| AC-008-01 … AC-008-06 | `Us008ExportAcceptanceTest.ac008_nn`; e2e AC-001-06 | tests 19bc319; code 0a2be66 |

## Findings

| F | Severity | Source | Finding | Resolution |
|---|---|---|---|---|
| F-01 | Medium | Semgrep `npm-missing-minimum-release-age` | `frontend/.npmrc` sets no minimum release age for new package versions | accepted: every version is pinned exactly with a committed lock file and `npm ci`; listed for review in the release notes |
| F-02 | Medium | npm audit | 5 moderate advisories in dev-only tools: vitest 3.2.7 via `@vitest/mocker` (GHSA-82fw-gwwq-j7x9), jscpd 5.4.0 via `typed-rest-client` → `qs` (GHSA-q8mj-m7cp-5q26, -x5fp-wj9c-mxmx, -4mjr-xmp4-gh2g) | accepted: not in the shipped image (nginx serves static files); a fix changes `tech-stack.md` versions (human decision); in release notes |
| F-03 | Medium | inspection (SB-08) | no container image scanner in `tech-stack.md`; OS packages of the shipped `eclipse-temurin` and `nginx` images are not scanned | accepted: release notes ask for an image scan before production |
| F-04 | Low | runtime demonstration | startup guards run after the options file is loaded and the database is migrated; an unsafe production start fails on those first | accepted: the application never starts with unsafe settings |
| F-05 | Low | evidence checks | 8 commits exceed the commit size guide without a stated reason (62f39d3, d141ad5, de30ec4, d31ad3a, 6533aa4, cd72bd7, c280895, f865b7d) | accepted: history is not rewritten (`rules.md`) |

## Fix loops

| Loop | Findings | Change | Re-verification |
|---|---|---|---|
| — | none: no Critical or High finding in phase 6 | — | — |
