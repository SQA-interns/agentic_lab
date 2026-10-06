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

2026-10-06T13:40:31Z, all levels, after the phase 5 tests were written and compiled: **233 passed, 0 failed** (backend 180, frontend 43, e2e 10; logs `05-first_be-test.log`, `05-first_fe-test.log`, `05-first_e2e.log`). No failure to classify.

Defects found later in non-frozen tests (fixed, no production change):

| Test | Defect | Fix |
|---|---|---|
| `SecurityControlsIntegrationTest.registrationsAboveTheLimitAreRefusedWithRetryAfter` | failed under Pitest's coverage run: the one-minute rate-limit window rolled over mid-loop | loop up to 2 × limit + 1 requests; assert at most 2 × limit accepted |

## Measures (phase 5)

| Component | Level | Tests passed / failed | Line coverage | Branch coverage | Mutation score (scope) | Tools | Log |
|---|---|---|---|---|---|---|---|
| backend | acceptance (frozen) | 92 / 0 | — | — | — | JUnit 6, Testcontainers | `05-first_be-test.log` |
| backend | unit + integration | 79 / 0 (180 − 92 − 9) | — | — | — | JUnit 6, AssertJ | `05_be-test.log` |
| backend | architecture | 9 / 0 | — | — | — | ArchUnit 1.3.2 | `05-first_be-test.log` |
| backend | all levels | 180 / 0 (+4 added after mutation: 184) | 95.3% | 90.1% | 94% (164/174; D-23 scope: StartupGuard, ConferenceCatalogLoader, ConferenceCatalog, RegistrationValidator, RegistrationRequestParser, FixedWindowRateLimiter, JsonCopyStore, AntiAutomationVerifier) | JaCoCo 0.8.12, Pitest 1.30.0 | `05_be-mutation.log` |
| frontend | acceptance (frozen) + bootstrap | 19 / 0 | — | — | — | Vitest 4.1.11 | `05_fe-test.log` |
| frontend | unit / component | 29 / 0 | — | — | — | Vitest, Testing Library | `05_fe-test.log` |
| frontend | all levels | 48 / 0 | 97.68% | 91.09% | 84.9% (383/451; all `src` except tests, `main.tsx`, frozen helpers) | @vitest/coverage-v8 4.1.11, Stryker 10.0.0 | `05_fe-mutation.log` |
| stack | end-to-end (frozen) | 10 / 0 | — | — | — | Playwright 1.63.0 | `05-first_e2e.log` |

Coverage is the full-suite JaCoCo / v8 figure of the run in the Log column.

Surviving mutants in validation, security, persistence and business-rule code:

| Mutant | Classification |
|---|---|
| ConferenceCatalogLoader 78, 94 (unknown property check), 147 (length boundary) | should have been caught → tests added, killed |
| AntiAutomationVerifier 92 (form encoding), 51 (4096 token boundary) | should have been caught → test added, killed |
| JsonCopyStore 28 (startup writability check) | should have been caught → test added, killed |
| RegistrationValidator 155, 159 (empty list on early return) | equivalent: the list is discarded because the collected errors are thrown |
| AntiAutomationVerifier 86, 87 (interrupt handling) | not observable: thread-interrupt path |
| FixedWindowRateLimiter 26, 27 (5 mutants, cleanup above 10 000 keys) | not observable: memory hygiene only |
| JsonCopyStore 44 (`FileChannel.force`) | not observable: durability only on power loss |
| frontend `api.ts` 72 (swallowed network error) | should have been caught → test added |
| frontend `api.ts` 57, 69 (`Accept` headers) | not observable: the backend answers JSON regardless |
| frontend `validation.ts` 68 (undefined maximum) | equivalent: `count > undefined` is false |
| frontend `AntiAutomation.tsx`, `RegistrationForm.tsx`, `App.tsx` (61 mutants) | UI rendering details (spacing text nodes, cleanup flags, effect dependencies); behaviour covered by acceptance and e2e tests; not in the required scope |

## Final run (phase 6)

| Component | Level | Tests passed / failed | Line coverage | Branch coverage | Mutation score (scope) | Tools | Log |
|---|---|---|---|---|---|---|---|
| backend | acceptance + unit + integration + architecture | 184 / 0 (92 acceptance, 9 architecture, 83 unit/integration) | 95.3% | 90.4% | 94% (164/174, D-23 scope) | JUnit 6, Testcontainers 2.0.5, ArchUnit 1.3.2, JaCoCo 0.8.12, Pitest 1.30.0 | `06-final_be-test.log`, `06_be-mutation.log` |
| frontend | acceptance + unit/component | 48 / 0 (19 acceptance incl. bootstrap, 29 unit) | 97.68% | 91.09% | 85.1% (383/451) | Vitest 4.1.11, @vitest/coverage-v8 4.1.11, Stryker 10.0.0 | `06_fe-test.log`, `06_fe-mutation.log` |
| stack | end-to-end | 10 / 0 | — | — | — | Playwright 1.63.0 (container) | `06-fix_e2e.log` |
| all | all levels | 242 / 0 | | | | | |
