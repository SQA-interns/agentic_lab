# Test strategy (as executed)

> Written in: phases 3, 5 and 6 · Source: `general/standards.md` ("Tests"), phase cards 3 and 5 · Agent: writes

## Test levels as executed

| Level | Tool | Location | Public interface used | Substitutes |
|---|---|---|---|---|
| Acceptance, backend | JUnit 6 + AssertJ + Awaitility, Testcontainers | `backend/src/test/java/.../acceptance/Us00n*AcceptanceTest.java` | HTTP to `/api` (`api.openapi.yaml`); observes `database.sql` tables, JSON copy files, Mailpit API, log output | PostgreSQL and Mailpit containers (pinned images), JDK HTTP server as reCAPTCHA verify endpoint (production code path, DoD-P05) |
| Acceptance, frontend | Vitest + Testing Library (jsdom) | `frontend/tests/acceptance/us00n-*.test.tsx` | rendered `<App/>`, test ids of `ui-form.json` | `fetch` stubbed with `api.openapi.yaml` responses |
| End-to-end | Playwright (bundled Chromium) | `frontend/tests/e2e/us00n-*.spec.ts` | browser on the frontend nginx, `/api` through the proxy, Mailpit API | fresh compose project per run with generated credentials, reCAPTCHA test mode (KP-08) |
| Unit / integration | phase 5 | any other test path | — | — |

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

## Final run (phase 6)
