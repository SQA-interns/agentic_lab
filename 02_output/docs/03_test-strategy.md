# Test strategy (as executed)

> Written in: phases 3, 5 and 6 · Source: `general/standards.md` ("Tests"), phase cards 3 and 5 · Agent: writes

## Test levels as executed

| Level | Location | Interface | Substitutes (environments.md) | Run |
|---|---|---|---|---|
| Acceptance | `backend/src/test/java/si/konferenca/registration/acceptance/` | REST API over HTTP; backend started from its entry point with the §3 settings by name; observed through the export, the JSON copy directory and the Mailpit API | Testcontainers PostgreSQL (one database per backend instance), Mailpit container, reCAPTCHA test mode, JDK HTTP server as mocked Google verify endpoint (DoD-P05) | `verify.sh <phase> backend_test` |
| End-to-end | `frontend/e2e/` | Browser (Playwright Chromium) on the UI contract `ui-form.json` | Fresh stack per run on free ports (KP-08): PostgreSQL and Mailpit containers, backend jar, Vite server proxying `/api` | `verify.sh <phase> frontend_e2e` |
| Unit / integration | phase 5 | | | |

Coverage: all 50 ACs (68 acceptance tests, 17 end-to-end tests); NFR-01 in the US-001 end-to-end test, NFR-03 and KP-03 in US-001 end-to-end tests, SR-01/DoD-P05 in `Us001RecaptchaVerificationAcceptanceTest`.

Harness constraints: the backend reads its settings through the Spring environment (environment variables or properties of the same name); AC-004-03 and AC-005-03 make `JSON_COPY_DIR` read-only to simulate a storage failure (the backend must not run as root in tests); AC-004-03 end-to-end simulates the 503 with a Playwright route.

## Red run before any production code (2026-10-09T11:41:28Z, bootstrap skeletons)

| Suite | Passed | Failed | Reason (one line per group) |
|---|---|---|---|
| Acceptance (backend) | 0 | 68 (67 failures, 1 class-setup error) | All `/api` endpoints missing: the bootstrap app answers 401 (default security) instead of 200/201/400/409/503; `Us008ExportAcceptanceTest` setup cannot register its fixtures (1 error for its 5 tests) |
| End-to-end | 0 | 17 | The skeleton page has no type choice (`type-external`/`type-student` not found) |

Tests that would pass on bootstrap code: AC-008-03 and AC-008-04 (Spring Security's default already answers 401 with `WWW-Authenticate: Basic`); they fail now only because their class setup cannot register fixtures.

## First complete run (before any fix)

## Final run (phase 6)
