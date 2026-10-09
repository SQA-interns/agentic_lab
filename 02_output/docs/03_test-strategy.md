# Test strategy (as executed)

> Written in: phases 3, 5 and 6 · Source: `general/standards.md` ("Tests"), phase cards 3 and 5 · Agent: writes

## Test levels as executed

| Level | Location | Interface | Substitutes (environments.md) | Run |
|---|---|---|---|---|
| Acceptance | `backend/src/test/java/si/konferenca/registration/acceptance/` | REST API over HTTP; backend started from its entry point with the §3 settings by name; observed through the export, the JSON copy directory and the Mailpit API | Testcontainers PostgreSQL (one database per backend instance), Mailpit container, reCAPTCHA test mode, JDK HTTP server as mocked Google verify endpoint (DoD-P05) | `verify.sh <phase> backend_test` |
| End-to-end | `frontend/e2e/` | Browser (Playwright Chromium) on the UI contract `ui-form.json` | Fresh stack per run on free ports (KP-08): PostgreSQL and Mailpit containers, backend jar, Vite server proxying `/api` | `verify.sh <phase> frontend_e2e` |
| Unit (backend) | `backend/src/test/java/si/konferenca/registration/{domain,config,api,application,adapter}` | classes directly; Spring `MockEnvironment`, mock servlet objects, Mockito for ports, JDK HTTP server for the verify endpoint | none | `verify.sh <phase> backend_test` |
| Integration (backend) | `adapter/persistence/JpaRegistrationStoreIntegrationTest`, `architecture/ArchitectureTest` | Spring context with Flyway on Testcontainers PostgreSQL; ArchUnit on compiled classes (AR-01..AR-03) | Testcontainers PostgreSQL | `verify.sh <phase> backend_test` |
| Unit / component (frontend) | `frontend/src/*.test.ts(x)` | functions and React components (Testing Library, jsdom), `fetch` stubbed | none | `verify.sh <phase> frontend_test` |

Coverage: all 50 ACs (68 acceptance tests, 17 end-to-end tests); NFR-01 in the US-001 end-to-end test, NFR-03 and KP-03 in US-001 end-to-end tests, SR-01/DoD-P05 in `Us001RecaptchaVerificationAcceptanceTest`.

Harness constraints: the backend reads its settings through the Spring environment (environment variables or properties of the same name); AC-004-03 and AC-005-03 make `JSON_COPY_DIR` read-only to simulate a storage failure (the backend must not run as root in tests); AC-004-03 end-to-end simulates the 503 with a Playwright route.

## Red run before any production code (2026-10-09T11:41:28Z, bootstrap skeletons)

| Suite | Passed | Failed | Reason (one line per group) |
|---|---|---|---|
| Acceptance (backend) | 0 | 68 (67 failures, 1 class-setup error) | All `/api` endpoints missing: the bootstrap app answers 401 (default security) instead of 200/201/400/409/503; `Us008ExportAcceptanceTest` setup cannot register its fixtures (1 error for its 5 tests) |
| End-to-end | 0 | 17 | The skeleton page has no type choice (`type-external`/`type-student` not found) |

Tests that would pass on bootstrap code: AC-008-03 and AC-008-04 (Spring Security's default already answers 401 with `WWW-Authenticate: Basic`); they fail now only because their class setup cannot register fixtures.

## First complete run (before any fix)

Run at the end of phase 5 writing (2026-10-09T12:18:33Z recorded in `run-log.json` as `firstTestRun`), all levels, no compile errors. Logs: `out/logs/5_backend-test-first-run.log`, `5_frontend-test.log`, `5_frontend-e2e.log`.

| Suite | Passed | Failed |
|---|---|---|
| Backend (unit, integration, acceptance) | 176 | 12 (5 failures, 7 errors) |
| Frontend unit and component | 33 | 0 |
| End-to-end | 17 | 0 |
| Total | 226 | 12 |

| Failure group | Tests | Class | Action |
|---|---|---|---|
| Default `MAIL_FROM` (`registration@localhost`) fails the startup address check, so the context cannot start without `MAIL_FROM` | 6 `AppSettingsTest`, 4 `JpaRegistrationStoreIntegrationTest` (context) , `es07_toStringShowsNoSecret` | Implementation defect (spec §3 had the same default) | Default changed to `registration@localhost.localdomain`; spec §3 corrected |
| A blank `APP_ENVIRONMENT` is refused instead of meaning the default | `es01_defaultsAreTheSafeValues` | Implementation defect | Blank optional settings fall back to their default |

Found after those fixes: `JpaRegistrationStoreIntegrationTest` used `WebEnvironment.NONE`, where the security configuration has no `HttpSecurity` bean (4 errors). Class: defect in a non-frozen test; fixed by using the default mock web environment. No frozen test failed.

## Final run (phase 6)

| Suite | Passed | Failed | Log |
|---|---|---|---|
| Backend (unit, integration, architecture, acceptance) | 192 | 0 | `out/logs/6c_backend-test.log` |
| Frontend unit and component | 33 | 0 | `out/logs/6_frontend-test.log` |
| End-to-end | 17 | 0 | `out/logs/6_frontend-e2e.log` |

Coverage (record only): backend line 95.9 %, branch 86.1 %; frontend line 97.4 %, branch 88.5 %. Mutation (record only): backend 69 % (unit tests; acceptance and integration excluded because they need containers), frontend 65.3 %. One frozen test (AC-006-04) failed in the 6b run; root cause F-01 in production code, fixed; the test was not changed.
