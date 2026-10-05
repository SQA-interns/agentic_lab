# Test strategy (as executed)

> Written in: phases 3, 5 and 6 · Source: `general/standards.md` ("Tests"), phase cards 3 and 5 · Agent: writes

## Test levels as executed

| Level | Location | Interface | Environment | Run with |
|---|---|---|---|---|
| Acceptance (frozen) | `backend/src/test/java/si/konferenca/registration/acceptance/` | REST API over HTTP on a random port; read-only checks of database rows, JSON copy files, Mailpit API, export workbook | Testcontainers `postgres:16.15-alpine` and `axllent/mailpit:v1.31.1` (chaos enabled), JSON copies in a temp directory, options file = `docs/02_contracts/conference-options.example.json`, reCAPTCHA test mode; configured only through the environment names of specification section 5 | `verify.sh <phase> be-test` |
| End-to-end (frozen) | `frontend/e2e/` | browser on the page (roles and labels of `registration-form.ui.json`), Mailpit API, organizer export over `/api` | local stack (`docker compose`), Playwright container in the compose network; organizer keys via `secrets.sh run` | `verify.sh <phase> e2e` |
| Unit (backend) | `backend/src/test/java/.../{application,infrastructure,api,config}/` | classes directly; Mockito for ports; JDK `HttpServer` as reCAPTCHA mock (DoD-P05); temp directories | no containers | `verify.sh <phase> be-test` |
| Integration (backend) | `backend/src/test/java/.../integration/` | a separate app instance over HTTP with low limits and CORS | Testcontainers PostgreSQL and Mailpit (shared with acceptance) | `verify.sh <phase> be-test` |
| Architecture (backend) | `backend/src/test/java/.../ArchitectureTest.java` | ArchUnit over production classes (specification section 2) | — | `verify.sh <phase> be-test` |
| Unit / component (frontend) | `frontend/src/*.test.ts(x)` | Vitest + Testing Library in jsdom; `fetch` and `grecaptcha` stubbed | — | `verify.sh <phase> fe-test` |

Harness choices: failure injection without production hooks: JSON copy directory replaced by a plain file (AC-004-03, AC-005-03), a `BEFORE INSERT` trigger that raises (AC-005-03), Mailpit chaos refusing every recipient (AC-006-03, AC-007-03); restarts and other configurations start a second application instance from `RegistrationApplication` (AC-003-03, AC-003-04, AC-005-04, AC-008-04 on a fresh database).

Harness probes (phase 3, deleted before the freeze): backend helpers (`Mailpit`, `Database`, `JsonCopies`, `Workbook`, `RunningApp`, `ApiClient`, base-class problem parser) 6 tests passed (`out/logs/3_be-harness-probe.log`); end-to-end helpers (`contract`, `mailpit`, `xlsx` on a POI-written workbook, `organizer` against a stub server, page object on the served bootstrap page) 1 test passed (`out/logs/3_e2e-harness-probe.log`, workbook from `out/logs/3_be-xlsx-probe-writer.log`).

## Coverage of acceptance criteria

| Story | Acceptance tests (backend) | End-to-end tests |
|---|---|---|
| US-001 | AC-001-01 … AC-001-14 (all) | AC-001-01, -02, -03, -05, -06 (NFR-01), -11, -12, -14 |
| US-002 | AC-002-01 … AC-002-11 (all) | AC-002-01, -02, -08 |
| US-003 | AC-003-01 … AC-003-04 | AC-003-01, -02 |
| US-004 | AC-004-01 … AC-004-03 | AC-004-01, -02 |
| US-005 | AC-005-01, -02, -03 (JSON and database failure), -04 | — |
| US-006 | AC-006-01 … AC-006-03 | (participant email in AC-001-06) |
| US-007 | AC-007-01 … AC-007-03 | (organizer email and attachment in AC-001-06) |
| US-008 | AC-008-01 … AC-008-06 | (export in AC-001-06) |

## Phase 3 run on bootstrap code (before any production code)

Acceptance: 65 run, 0 passed, 65 failed, 0 errors (`out/logs/3_be-test.log`). End-to-end: 15 run, 0 passed, 15 failed (`out/logs/3_e2e.log`; run against the bootstrap page served by nginx in a probe network). No test passes on bootstrap code.

| Failures | Tests | Reason (behavioural) |
|---|---|---|
| 33 | AC-001-02, -03, -05, -07, -08, -10, -12, -13, AC-002-02, -03, -05, -07, -10, -11, AC-004-02, AC-006-02, AC-007-02 (with parameter variants) | expected 400, got 403: registration endpoint not built (bootstrap denies all) |
| 20 | AC-001-01, -04, -06, -09, -14, AC-002-01, -04, -06, -09, AC-003-04, AC-004-01, AC-005-01, -02, -04, AC-006-01, AC-007-01, AC-008-01, -02, -03, -05 | expected 201, got 403: registration endpoint not built |
| 5 | AC-001-11, AC-002-08, AC-003-01, -02, -03 | expected 200, got 403: form configuration endpoint not built |
| 2 | AC-004-03, AC-005-03 (JSON copy failure) | expected 503, got 403: storage not built |
| 3 | AC-005-03 (database failure), AC-006-03, AC-007-03 | precondition "application serves the form": expected 200, got 403 |
| 2 | AC-008-04, AC-008-06 | organizer token: expected 200, got 403: token endpoint not built |
| 12 | end-to-end form tests | registration type choice not on the page (timeout waiting for the radio group) |
| 3 | end-to-end AC-001-14, AC-002-08, AC-003-01 | `/api/form-config` 404: not built |

## First complete run (before any fix)

Phase 5, 2026-10-05T09:59:49Z, all levels, after formatting only (no compile errors): 216 passed, 1 failed (`out/logs/5_first-run_*.log`).

| Component / level | Passed | Failed |
|---|---|---|
| backend acceptance | 65 | 0 |
| backend unit, integration, architecture | 106 | 1 |
| frontend unit / component | 30 | 0 |
| end-to-end | 15 | 0 |

| Failure | Class | Action |
|---|---|---|
| `MailRegistrationNotifierTest`: attachment content type read as `text/plain` | defect in a non-frozen test: a mocked sender never calls `MimeMessage.saveChanges()`, which writes part headers (Mailpit shows `application/json` in AC-007-01) | test calls `saveChanges()` as a real transport does (in c280895) |

Other fixes in phase 5 (from tests written against surviving mutants): the email rule accepted empty domain labels (`ab@example.si.`), implementation followed the specification; specification, backend and frontend corrected (D-17; a67b9f2, f8656d2, c5179a4).

## Measures (phase 5, after fixes)

| Component / level | Tests passed / failed | Line coverage | Branch coverage |
|---|---|---|---|
| backend acceptance only | 65 / 0 | 83.0% (771/929) | 62.1% (205/330) |
| backend unit + integration + architecture (105 + 8 + 5) | 118 / 0 | 94.9% (882/929) | 90.0% (297/330) |
| backend all levels | 183 / 0 | 97.1% (902/929) | 91.8% (303/330) |
| frontend unit / component | 42 / 0 | 100% (464/464) | 95.6% (130/136) |
| end-to-end (local stack) | 15 / 0 | not measured (browser against containers) | — |

Thresholds: record only (`quality-requirements.md`).

| Mutation | Scope | Result |
|---|---|---|
| backend (PIT, `verify.sh 5 be-mutation`) | `application`, `domain`, `infrastructure`, `api`, `config.StartupGuards`, run against unit tests; excluded: acceptance and integration tests (containers per mutant would take hours) and Spring wiring classes (`BackendConfig`, `MailConfig`, `SecurityConfig`, `WebConfig`, `AppProperties`, `RegistrationApplication`), which only those tests reach | 352 mutants, 305 killed (87%), 1 timed out, 8 survived, 39 no coverage; test strength 97% |
| frontend (Stryker, `verify.sh 5 fe-mutation`) | all of `src` except tests, `test-setup.ts`, `main.tsx` | score 90.97%: 356 killed, 27 timeout, 33 survived, 5 no coverage |

Surviving mutants in validation, security, persistence and business-rule code:

| Mutant(s) | Classification |
|---|---|
| backend `RegistrationValidator.text/email/options`: return value replaced in an error branch (5) | equivalent: an error is recorded, so `validate` always throws and the value is never used |
| backend `RequestLimitsFilter.doFilterInternal` line 82, `< 0` → `<= 0` | equivalent: an empty declared body is read as empty either way |
| backend `RequestLimitsFilter.allow` line 113 (2) and its clean-up lambda (no coverage) | not observable: memory clean-up after 10 000 tracked clients; limits behave the same |
| backend no coverage in `api` controllers, `ApiExceptionHandler`, `Problem`, `ExportService`, `CachedBodyRequest.isFinished/isReady` | covered by the acceptance and integration tests excluded from the mutation run; the two async-servlet methods are never called by synchronous controllers |
| frontend `Captcha.tsx` line 51 (5), line 75 | equivalent: the three render guards and the reset guards are redundant with each other (a rendered container is never empty; reset needs a widget id) |
| frontend `Captcha.tsx` `script.async/defer`, effect dependency array | not observable: load timing only; the props never change during a page's life |
| frontend `contract.ts` `inputType: 'text'` | equivalent: an empty `type` renders a text input |
| frontend `RegistrationForm.tsx` (21) and `App.tsx` (7) | UI state, not validation/security/persistence/business rules: focus moves, the submitting flag, `preventDefault` in jsdom, clearing errors on type switch, captcha reset counter in test mode, the `consentGiven` mapping of a backend error the client already prevents, option error codes the UI cannot produce, unmount guards; behaviour covered by the end-to-end tests |

Phase 5 additions that killed earlier survivors (first mutation run 80% / 85%): consent `false` read as false, startup-guard and options-file boundaries, export row order, organizer mail contents and sender, JSON copy organization/consent, temporary-file clean-up, reCAPTCHA read timeout, declared body at the limit; frontend email characters per part, incomplete API answers, captcha guards, unchecked options, cleared form errors.

## Final run (phase 6)
