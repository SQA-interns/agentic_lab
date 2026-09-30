# Test strategy (as executed)

> Written in: the test phases of the selected workflow, and phase 6 · Source: `general/quality/test-strategy.md`, workflow `rules.md` · Agent: writes

## Test levels as executed

| Level | Written | Frozen | Location | Runner / command | Environment |
|---|---|---|---|---|---|
| Acceptance (backend, black-box) | phase 3 | yes | `backend/src/test/java/lab/conference/acceptance/**`, `backend/src/test/resources/acceptance/**` | Maven Failsafe: `./mvnw -B verify` | The backend is started in-process via `ConferenceApplication` with the documented environment names; PostgreSQL 16.14 and Mailpit v1.24.1 run as digest-pinned Testcontainers; the JSON store is a temp directory |
| End-to-end (browser + API) | phase 3 | yes | `frontend/e2e/**`, `frontend/playwright.config.ts` | Playwright 1.55.1 Chromium: `npm run test:e2e` | Running Compose stack (`http://127.0.0.1:18080`, Mailpit API `:18025`) |
| Unit / integration | phase 5 | no | `backend/src/test/java/**` outside `acceptance` (`*Test` = unit via Surefire, `*IT` = integration via Failsafe); `frontend/src/**/*.test.tsx` | `./mvnw -B verify`; `npm test` | JVM / jsdom; `*IT` tests use Testcontainers |
| Contract validation | phase 2 | n/a | `frontend/scripts/validate-contracts.mjs` | `npm run contracts` | Node |

Observation points used by the acceptance tests, all documented in the specification:
- the REST API (`openapi.yaml`);
- the database tables `registration` and `notification_outbox` (spec section 5);
- the JSON store directories `registrations/`, `staging/` and `orphaned/` (spec section 4);
- the Mailpit HTTP API;
- the parsed export workbook (`export-workbook.json`).

No production class other than the entry point `ConferenceApplication` is referenced.

Fault injection:
- The JSON store is made read-only (`chmod`; requires a non-root test user).
- A dedicated PostgreSQL container on a fixed port is stopped and started.
- SMTP starts as a closed port, and a Mailpit container is then started on that port.
- A stale orphan JSON file is placed in the store.
- The backend and database are restarted with the same volume and database.

Measures recorded per test-strategy.md: JaCoCo line/branch coverage (unit `target/site/jacoco`, integration + acceptance `target/site/jacoco-it`); Vitest v8 coverage (`frontend/coverage`); PIT mutation score (`backend/target/pit-reports`) and Stryker (`frontend/reports/mutation`); test counts per level.

## Phase 3 red run (acceptance tests against the bootstrap skeleton)

Run 2026-09-30, bootstrap skeleton plus configuration-only completion (D-21). No production behaviour exists.

| Suite | Tests | Passed | Failed | Log |
|---|---|---|---|---|
| Backend acceptance (Failsafe) | 109 | 0 | 109 (35 failures, 74 errors) | `logs/phase3-acceptance-red-run.log` |
| End-to-end (Playwright) | 19 | 0 | 19 | `logs/phase3-e2e-red-run.log` |

Failure groups, all behavioural (the harness, the containers and the app startup work):

| Count | Reason |
|---|---|
| 74 acceptance | The specified tables (`registration`, `notification_outbox`) do not exist: there are no migrations or persistence behaviour yet |
| 28 acceptance | The API endpoints answer 401 (Spring Security default) instead of catalog/registration/export behaviour |
| 5 acceptance | The backend starts although the catalog is invalid or missing (AC-003-03) |
| 2 acceptance | The JSON store directories are not created; required-field rejection is missing |
| 15 e2e | Forms, links and headings do not exist (empty skeleton page); actions time out on the missing elements |
| 4 e2e | The API returns 401 instead of catalog, registration or validation responses |

A first e2e attempt ran while the backend container was still starting (502), which was a setup error. It was fixed in the harness before freezing: `e2e/global-setup.ts` waits for the stack, and actions time out after 15 s. The run above is the rerun.

Format and lint before freezing: Spotless (google-java-format 1.25.2) applied to the Java tests, and the tests compile with `-Xlint:all -Werror`; Prettier, ESLint (strict type-checked) and `tsc` are clean for `e2e/` and `playwright.config.ts`.

## First complete run (before any fix)

Phase 5, 2026-09-30, after the unit and integration tests were written and before any fix. Logs: `logs/phase5-first-full-run-{backend,frontend,e2e}.log`.

| Suite | Level | Tests | Passed | Failed |
|---|---|---|---|---|
| Backend Surefire | unit | 141 | 138 | 3 |
| Backend Failsafe | acceptance (109) + integration (5) | 114 | 114 | 0 |
| Frontend Vitest | unit | 41 | 41 | 0 |
| Playwright | e2e | 19 | 19 | 0 |
| **Total** | | **315** | **312** | **3** |

Classification:

| Failing test | Class | Reason | Action |
|---|---|---|---|
| `RequestGuardFilterTest.registrationPostsAreRateLimitedPerAddress` | Defect in a non-frozen test | The exact content-type comparison missed the `;charset=UTF-8` suffix that the filter correctly adds | Test changed to check the media type prefix |
| `RequestGuardFilterTest.declaredOversizedBodyIsRejectedAndStreamedBodyIsCapped` | Defect in a non-frozen test | The byte-by-byte check reused a mock stream the first read had already drained past the limit | Test uses a fresh request for each read style |
| `TokenBucketLimiterTest.allowsBurstThenLimitsAndRefills` | Implementation defect | `Retry-After` was computed with `ceil` on a floating-point value and reported 2 s where 1 s was due (`ceil(1.0000000000000009)`) | `TokenBucketLimiter` rounds with a 1 µs tolerance |

No frozen test failed.

## Final run (phase 6)
