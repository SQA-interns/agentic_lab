# Test strategy (as executed)

> Written in: phases 3, 5 and 6 · Source: `standards/testing.md` · Procedure: `skills/write-acceptance-tests`, `skills/unit-tests`, `skills/verify-release` · Agent: writes

## Test levels as executed

| Level | Component | Location | Interface | Substitutes | Run with |
|---|---|---|---|---|---|
| Acceptance | backend | `backend/src/test/java/si/konferenca/registration/acceptance/`, `backend/src/test/resources/acceptance/` | REST over HTTP on a random port; stored state read from PostgreSQL (`database.sql`), the JSON copy directory and Mailpit's API | Testcontainers `postgres:16.15-alpine`, `axllent/mailpit:v1.31.1`; reCAPTCHA test mode; failure injection: copy directory replaced by a file, own database container stopped, SMTP on a closed port; separate application instances for restart and startup-refusal cases | `verify.sh <phase> be-test` |
| Acceptance | frontend | `frontend/src/acceptance/` | the rendered page (`<App />`, Testing Library, jsdom); `fetch` stubbed with `openapi.yaml` responses | none | `verify.sh <phase> fe-test` |
| End-to-end | all | `frontend/e2e/`, `frontend/playwright.config.ts` | Chromium against the local stack through the frontend's nginx; Mailpit API; export over HTTP | local stack (`docker-compose.yml`, test mode on) | `verify.sh <phase> stack-up`, then `e2e` |
| Unit / integration | both | phase 5 | | | |

AC coverage: every AC has at least one test; the AC id is in each test name (backend method `ac_nnn_nn_…`, shown as `AC-nnn-nn …`; frontend and end-to-end test titles). Rejection tests also assert that nothing was stored or sent. Harness helpers were each exercised once by temporary probe tests against real data (PostgreSQL rows from `database.sql`, a UTF-8 email with attachment through Mailpit, files, a POI workbook, a local HTTP server, a hand-built form, Mailpit's send API) before the freeze; the probes were deleted.

## Freeze run (phase 3)

Bootstrap code (phase 0 skeletons), 2026-10-09. Every test fails for a missing behaviour; no test passes on bootstrap code.

| Suite | Passed | Failed | Log |
|---|---|---|---|
| backend acceptance | 0 | 113 | `logs/3_be-test.log` |
| frontend acceptance | 0 | 16 | `logs/3_fe-test.log` |
| end-to-end | 0 | 6 | `logs/3_e2e.log` |

| Failure group | Tests | Reason (behavioural) |
|---|---|---|
| API status 401 instead of 201/400/409/500/200 | 109 backend | no registration, form or export endpoint yet; bootstrap security answers 401 |
| startup succeeds with an invalid options file | 4 backend (AC-003-05, AC-003-06) | no options configuration loading yet |
| element `type-external` / `type-student` not found | 16 frontend, 5 end-to-end | bootstrap page has only a heading |
| `/api/export` answers 200 (page fallback) instead of 401 | 1 end-to-end | no `/api` routing in the frontend's nginx and no export yet |

## First complete run (phase 5, before any fix)

## Measures (phase 5)

| Component | Level | Tests passed / failed | Line coverage | Branch coverage | Mutation score (scope) | Tools | Log |
|---|---|---|---|---|---|---|---|

## Final run (phase 6)

| Component | Level | Tests passed / failed | Line coverage | Branch coverage | Mutation score (scope) | Tools | Log |
|---|---|---|---|---|---|---|---|
