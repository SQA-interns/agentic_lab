# Test strategy (as executed)

> Written in: the test phases of the selected workflow, and phase 6 · Source: `general/quality/test-strategy.md`, workflow `rules.md` · Agent: writes

## Test levels as executed

| Level | Location (relative to `02_output/`) | Frozen | Tool | Interface used |
|---|---|---|---|---|
| Backend acceptance | `backend/src/test/java/si/konferenca/registration/acceptance/` (11 classes, 67 tests incl. parameterised cases; `support/` harness) | yes (phase 3) | JUnit Jupiter 6, Testcontainers (PostgreSQL, Mailpit), JDK `HttpClient`, JDK `HttpServer` (mock reCAPTCHA verify endpoint), POI (reading the workbook) | real HTTP to the backend on a random port; SQL contract of spec §6; JSON copy directory; Mailpit HTTP API; application startup for SR-02/AR-04 |
| Frontend acceptance | `frontend/tests/acceptance/` (10 tests) | yes (phase 3) | Vitest, Testing Library, jsdom | the rendered `App` (public UI); `fetch` stubbed at the network boundary with contract-shaped responses |
| End-to-end | `frontend/tests/e2e/` (3 tests) | yes (phase 3) | Playwright (Chromium) | browser against the running stack (`E2E_BASE_URL`, default the compose frontend), Mailpit API, export over HTTP |
| Unit / integration | other test paths | no | JUnit, ArchUnit, Vitest | phase 5 |

The UI contract the frontend tests pin (spec §11): radio buttons "External participant" / "Student"; fields labelled "First name", "Last name", "Email", "Organization / institution", "Study institution", "Study programme", "Student ID"; option groups "Workshops", "Events", "Meals", "Other activities"; consent checkboxes named by their wording; test-mode captcha checkbox "I am not a robot (test mode)"; button "Register"; confirmation heading "Registration received"; field errors linked with `aria-invalid` and `aria-describedby`; a general error in `role="alert"`.

### AC → tests

| AC | Tests |
|---|---|
| AC-001-01 | `ParticipantRegistrationAcceptanceTest.ac00101…`, `CaptchaVerificationAcceptanceTest.sr01ValidToken…`, e2e NFR-01 test |
| AC-001-02 | `ac00102MissingExternalField…` (4 fields × empty/blank), `ac00102AbsentOrganization…` |
| AC-001-03 | `ac00103InvalidEmail…` (5 cases) |
| AC-001-04 | `ac00104WhitespaceIsTrimmed` |
| AC-001-05 | `ac00105UnicodeIsStoredUnchanged`, e2e NFR-01 test |
| AC-001-06 | `ac00106UnknownOrInactiveOption…` (2 cases) |
| AC-001-07 | `ac00107MissingMandatoryConsent…`, `ac00107UnknownConsent…` |
| AC-001-08 | frontend `AC-001-08 …` |
| AC-002-01 | `ac00201ValidStudentRegistration…`, e2e `AC-002-01 …` |
| AC-002-02 | `ac00202MissingStudentField…` (6 fields × empty/blank), `ac00202StudentWithExternalField…` |
| AC-002-03 | frontend `AC-002-03 …` |
| AC-003-01 | `ConferenceOptionsAcceptanceTest.ac00301…` (2) |
| AC-003-02 | `OptionsConfigurationChangeAcceptanceTest.ac00302…` |
| AC-003-03 | frontend `AC-003-03 …` |
| AC-003-04 | `ac00304…` (3), frontend `AC-003-04 …` |
| AC-003-05 | `ac00305…` (3) |
| AC-004-01 | frontend `AC-004-01 …`, e2e tests 1 and 2 |
| AC-004-02 | frontend `AC-004-02 …` (2) |
| AC-004-03 | frontend `AC-004-03 …` (3) |
| AC-005-01 | `RegistrationStorageAcceptanceTest.ac00501…` |
| AC-005-02 | `ac00502JsonCopyIsWritten` |
| AC-005-03 | `JsonCopyFailureAcceptanceTest.ac00503…` |
| AC-006-01 | `EmailAcceptanceTest.ac00601…`, e2e NFR-01 test |
| AC-006-02 | `ac00602MarkupIsNotInterpreted`, `ac00602HeaderInjectionIsPrevented` |
| AC-006-03 | `ac00603MailIsRetriedAfterFailure` (Mailpit chaos: every RCPT fails with 451, then recovers) |
| AC-007-01 | `ac00701…`, e2e NFR-01 test |
| AC-007-02 | `ac00702…` |
| AC-008-01 | `RegistrationExportAcceptanceTest.ac00801…`, e2e test 3 |
| AC-008-02 | `ac00802…` (2), e2e test 3 |
| AC-008-03 | `ac00803…`, e2e NFR-01 test |
| AC-008-04 | `ac00804…` |

Requirement tests beyond the AC: SR-01/DoD-P05 and AR-07 (`CaptchaVerificationAcceptanceTest`, 5), SR-02/SR-06/ES-01 startup (`StartupSafetyAcceptanceTest`, 6), SR-03/SB-06 (`AbuseProtectionAcceptanceTest`, 3), SR-06 (`RegistrationExportAcceptanceTest.sr06…`), NFR-04/SB-10/ES-07 (`OperationsAcceptanceTest`, 3; `JsonCopyFailureAcceptanceTest.nfr04…`).

### Phase 3 run against the bootstrap skeleton (red)

Logs: `out/logs/phase3-backend-acceptance-red.log`, `phase3-frontend-acceptance-red.log`, `phase3-e2e-red.log`.

| Suite | Run | Failed | Reason (per group) |
|---|---|---|---|
| Backend acceptance | 67 | 66 | 57 HTTP tests: endpoints do not exist, the skeleton's default Spring Security answers 401 where 200/201/400/403/413/429/500/503 is expected; 5 startup tests: the skeleton starts although the configuration is unsafe (no startup checks); 1 safe-startup test: `/api/config` missing (401); `nfr04ReadinessReportsUnwritableCopyDirectory`: readiness has no JSON-copy check (200, expected 503) |
| Frontend acceptance | 10 | 10 | the form does not exist (no "External participant" radio) |
| End-to-end | 3 | 3 | against the skeleton frontend (Vite) with the skeleton backend behind the proxy: form missing (2); export with organizer credentials answers 401 (1) |

One backend test passed on the skeleton: `OperationsAcceptanceTest.nfr04HealthAndReadiness` — Spring Boot Actuator, added in phase 0, already serves liveness and readiness. The unmet part of NFR-04 (readiness reflects the JSON copy directory) is covered by the red test above. Recorded in D-17. No failure came from compilation, configuration or the harness; the harness was checked separately (Mailpit search, message fields, parts and chaos API against the listed image; the e2e zip reader on a POI-generated workbook).

Before freezing, the e2e export test was changed from "refused without credentials" (it passed on the skeleton because of Spring Security's default 401) to the DoD-P04 pair "organizer gets a workbook, the same request without credentials is refused".

## First complete run (before any fix)

Phase 5, all levels together, before any fix (logs: `out/logs/phase5-first-run-backend.log`, `phase5-first-run-frontend.log`, `phase5-first-run-e2e.log`).

| Suite | Result | Failure class |
|---|---|---|
| Backend (unit, ArchUnit, frozen acceptance) | not executed: test sources did not compile | Defect in a non-frozen test: `NotificationServiceTest.FakeStore` was declared `final` but subclassed in `unknownReferenceAndStoreErrorsNeverThrow`. Fixed the test (removed `final`); no production code changed. |
| Frontend (unit and frozen acceptance, Vitest) | 36 passed, 0 failed | — |
| End-to-end (Playwright against the compose stack) | 3 passed, 0 failed | — |

Re-run after the fix: backend 167 passed, 0 failed (67 frozen acceptance, 100 unit/integration including 8 ArchUnit rules). The frontend type check then flagged an untyped mock in the non-frozen `src/api.test.ts` (defect in a non-frozen test, fixed by typing `vi.fn`); tests unchanged at 36 passed. No implementation defect and no frozen test was involved.

### Unit and integration tests written in phase 5

| Component | Tests | Covers |
|---|---|---|
| backend | `ArchitectureTest` | A-1 to A-7 (AR-02, AR-03, ES-01, DoD-04) |
| backend | `RegistrationValidatorTest`, `RegistrationTest` | validation rules BR-01..BR-05, SR-04, SR-05; mail status and abandonment (D-11) |
| backend | `OptionsFileLoaderTest` | options configuration and its rejection rules (AR-04, S-5) |
| backend | `FileJsonCopyStoreTest`, `PoiWorkbookWriterTest` | JSON copy shape, atomic write and cleanup; workbook columns, string cells (formula-like input) |
| backend | `StartupChecksTest`, `FiltersTest`, `EnvironmentAliasesTest` | S-1..S-4, request size, rate limit windows, SR-06 loopback rule, environment mapping |
| backend | `RegistrationServiceTest`, `NotificationServiceTest`, `MailComposerTest` | AR-05 ordering and rollback cleanup, captcha last, retry/abandon, mail content (SR-05, SR-07) |
| backend | `RecaptchaVerifierTest`, `SmtpMailerTest` | SR-01 adapter edge cases (non-200, bad JSON, unreachable), MIME structure |
| frontend | `validation.test.ts`, `api.test.ts`, `Captcha.test.tsx`, `App.test.tsx` | client rules (NFR-03), error handling, reCAPTCHA widget loading/reset, type switching |

## Final run (phase 6)
