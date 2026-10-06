# Test strategy (as executed)

> Written in: phases 3, 5 and 6 · Source: `standards/testing.md` · Procedure: `skills/write-acceptance-tests`, `skills/unit-tests`, `skills/verify-release` · Agent: writes

## Test levels as executed

| Level | Component | Location | Interface used | Substitutes (`project/stack.md` Environments) | Run with |
|---|---|---|---|---|---|
| Acceptance | backend | `backend/src/test/java/si/konferenca/registration/acceptance/` | REST API over HTTP (random port); storage contract tables; JSON copy directory; delivered emails (Mailpit API) | Testcontainers `postgres:16.15-alpine`, `axllent/mailpit:v1.31.1`; reCAPTCHA test mode; mocked siteverify endpoint (JDK HttpServer) for the production path (DoD-P05) | `verify.sh <phase> be-test 'si.konferenca.registration.acceptance.**'` |
| Acceptance | frontend | `frontend/src/acceptance/` | rendered `<App/>`, roles and labels of `ui-registration-form.json` | `fetch` replaced by a fake backend answering per `openapi.yaml` | `verify.sh <phase> fe-test` |
| End-to-end | frontend + stack | `frontend/e2e/` | browser (Chromium, Playwright container) on the running stack; Mailpit API; export endpoint | local stack (`docker compose`), test-mode reCAPTCHA, Mailpit | `verify.sh <phase> e2e` (needs the stack) |
| Architecture | backend | `backend/src/test/java/si/konferenca/registration/architecture/` (phase 4) | ArchUnit over compiled classes | none | `be-test` |
| Unit / integration | both | other test paths (phase 5) | implementation | as above | `be-test`, `fe-test` |

Failure injection, only at public boundaries: read-only JSON copy directory (AC-004-03, AC-005-03); a `CHECK (false) NOT VALID` constraint added to `registration` (AC-005-03); an SMTP port with no listener (AC-006-04, AC-007-05); the mock verification endpoint answering 500 (CAPTCHA_UNAVAILABLE). Restart criteria (AC-003-03, AC-005-04) start a second application instance from `RegistrationApplication` with the same database and directory.

Harness check (skill step 3): each helper (database reads and failure injection, Mailpit message/header/attachment reads, JSON copy directory and read-only switch, workbook reader, mock verification server, application runner, fake backend, label and form helpers) was exercised once by a temporary probe against real data of the same kind (contract SQL applied to PostgreSQL, a UTF-8 multipart mail with attachment, a POI workbook, a stand-in form); all probes passed and were deleted.

## Freeze run (phase 3)

Run 2026-10-06 on bootstrap code (commit before the freeze). End-to-end ran against the bootstrap frontend served by `vite preview` on port 8088 (no backend: the stack is built in phase 4).

| Suite | Passed | Failed | Log |
|---|---|---|---|
| backend acceptance | 0 | 92 | `logs/03_be-test.log` |
| frontend acceptance (+ bootstrap `App.test.tsx`) | 1 | 18 | `logs/03_fe-test.log` |
| end-to-end | 0 | 10 | `logs/03_e2e.log` |

| Failure group | Count | Reason (behavioural) |
|---|---|---|
| backend: `HTTP 403` where 200/201/400/401/409/503 is expected | 92 | the bootstrap security configuration denies every API path; no endpoint exists |
| frontend: `Unable to find role="button" and name "Register"` | 18 | the bootstrap page has a heading only, no form |
| e2e: `element(s) not found` for the Register button | 9 | as above, in the browser |
| e2e: export returned 500, 401 expected | 1 | no backend behind `/api` (no export endpoint) |

Passing on bootstrap code: `frontend/src/App.test.tsx` "renders the page heading" (bootstrap smoke test, not an acceptance test).

## First complete run (phase 5, before any fix)

## Measures (phase 5)

| Component | Level | Tests passed / failed | Line coverage | Branch coverage | Mutation score (scope) | Tools | Log |
|---|---|---|---|---|---|---|---|

## Final run (phase 6)

| Component | Level | Tests passed / failed | Line coverage | Branch coverage | Mutation score (scope) | Tools | Log |
|---|---|---|---|---|---|---|---|
