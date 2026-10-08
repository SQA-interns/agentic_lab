# Test strategy (as executed)

> Written in: phases 3, 5 and 6 · Source: `general/standards.md` ("Tests"), phase cards 3 and 5 · Agent: writes

## Test levels as executed

| Level | Tool | Location | Public interface used | Substitutes |
|---|---|---|---|---|
| Acceptance, backend | JUnit 6 + AssertJ + Awaitility, Testcontainers | `backend/src/test/java/.../acceptance/Us00n*AcceptanceTest.java` | HTTP to `/api` (`api.openapi.yaml`); observes `database.sql` tables, JSON copy files, Mailpit API, log output | PostgreSQL and Mailpit containers (pinned images), JDK HTTP server as reCAPTCHA verify endpoint (production code path, DoD-P05) |
| Acceptance, frontend | Vitest + Testing Library (jsdom) | `frontend/tests/acceptance/us00n-*.test.tsx` | rendered `<App/>`, test ids of `ui-form.json` | `fetch` stubbed with `api.openapi.yaml` responses |
| End-to-end | Playwright (bundled Chromium) | `frontend/tests/e2e/us00n-*.spec.ts` | browser on the frontend nginx, `/api` through the proxy, Mailpit API | fresh compose project per run with generated credentials, reCAPTCHA test mode (KP-08) |
| Unit / integration, backend | JUnit 6 + AssertJ + Mockito, ArchUnit, Spring MockMvc (standalone), JDK HTTP server | `backend/src/test/java/si/konferenca/registration/{domain,config,service,infrastructure,api,architecture}` | classes and filters directly; MockMvc for controller and problem mapping | mocked collaborators, temporary directories, local HTTP server for the verify endpoint |
| Unit / component, frontend | Vitest + Testing Library | `frontend/src/*.test.ts(x)` | modules and components | `fetch` and `window.grecaptcha` stubbed |

- Backend instances are started by the harness with only the environment settings of specification section 9 (`AcceptanceStack`); several instances cover restart, configuration change, broken storage and SMTP failure.
- Rejection tests also check that nothing changed: registration row count and JSON copy count (`Storage.Snapshot`), and no email.
- Frozen at the freeze commit by `docs/03_acceptance-manifest.sha256`: every file under `backend/src/test/java/si/konferenca/registration/acceptance/`, `frontend/tests/acceptance/`, `frontend/tests/e2e/`, and `frontend/playwright.config.ts`.
- Commands: `scripts/verify.sh <phase> be-test fe-test fe-e2e`.

## Phase 3 run (no production behaviour yet)

Run 2026-10-08T21:42:59Z, `verify.sh 03` (logs `logs/03_be-test.log`, `logs/03_fe-test.log`, `logs/03_fe-e2e.log`).

| Level | Tests | Passed | Failed |
|---|---|---|---|
| Acceptance, backend | 74 | 0 | 74 |
| Acceptance, frontend | 19 | 0 | 19 |
| End-to-end | 6 | 0 | 6 |

| Failure group | Count | Reason (behavioural) |
|---|---|---|
| backend: `relation "registration" does not exist` while snapshotting or reading storage | 46 | storage schema (Flyway `V1`) not built |
| backend: `HTTP 401` instead of 200/201/400/409 | 28 | no `/api` endpoints; skeleton security denies everything |
| frontend: `registration-page` not found | 19 | skeleton `App` renders no form |
| e2e: form elements not found | 5 | no form in the served page |
| e2e: export returned 401 instead of 200 | 1 | no export endpoint |

Tests passing on bootstrap code: none. Harness checks done: containers start, the app starts with the test settings, Mailpit and the reCAPTCHA mock answer, the e2e stack builds, becomes healthy and is removed with its volumes.

## First complete run (before any fix)

Run 2026-10-08T22:14:51Z, `verify.sh 05-first be-test fe-test fe-e2e` (logs `logs/05-first_*`). No compile fixes were needed.

| Level | Tests | Passed | Failed |
|---|---|---|---|
| Backend unit + integration + acceptance | 229 | 228 | 1 |
| Frontend unit + acceptance | 56 | 56 | 0 |
| End-to-end | 6 | 6 | 0 |
| All | 291 | 290 | 1 |

| Failure | Class | Action |
|---|---|---|
| `RegistrationValidatorTest.repeatedOptionIdCountsOnceAndNullIdIsUnknown`: `NullPointerException` in `RegistrationCommand` (`List.copyOf` rejects a null element, while the validator is written to report a null option id as `unknown_option`) | Implementation defect (Low: the REST controller already maps null elements to strings, so not reachable over HTTP) | fix the code: copy lists so null elements reach the validator |

Fix: commit "fix: keep null option and consent ids for validation" maps a null element to an empty id, which the validator reports as `unknown_option`. No test was changed.

Phase 5 gate run 2026-10-08T22:22:14Z: backend 229/229 (`logs/05_be-test.log`), frontend 56/56 and e2e 6/6 unchanged since the first run (no frontend change).

## Final run (phase 6)

Run 2026-10-08T22:22:57Z (`verify.sh 06`, all tools) and 2026-10-08T22:47:42Z (backend after two added unit tests).

| Level | Tests | Passed | Failed |
|---|---|---|---|
| Backend unit + integration + acceptance | 231 | 231 | 0 |
| Frontend unit + acceptance | 56 | 56 | 0 |
| End-to-end | 6 | 6 | 0 |

| Measure | Backend | Frontend |
|---|---|---|
| Line / branch coverage | 97.3% / 93.0% (JaCoCo) | 97.07% / 91.26% (v8) |
| Mutation | 323/423 killed, 76% (PIT, unit and integration tests; acceptance tests excluded because they start containers) | not measurable with the pinned Stryker runner under vitest 5 (F-05, D-27) |

Tests added in phase 6: `EmailAddressTest.boundariesOfTotalAndLabelLength`, `FiltersTest.staleWindowsAreEvicted` (F-08).

