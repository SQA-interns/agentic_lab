# Test strategy (as executed)

> Written in: phases 3, 5 and 6 · Source: `general/standards.md` ("Tests"), phase cards 3 and 5 · Agent: writes

## Test levels as executed

| Level | Location | Runner | Interface under test | Substitutes |
|---|---|---|---|---|
| Acceptance (backend) | `backend/src/test/java/si/konferenca/registration/acceptance/`, `backend/src/test/resources/acceptance/` | JUnit 6 via `verify.sh <p> backend-test` | REST API over real HTTP (`openapi.yaml`); storage via `database.sql` and `json-copy.schema.json`; mail via Mailpit API (`emails.schema.json`); startup with configuration (AR-04) | Testcontainers `postgres:16.15-alpine`, `axllent/mailpit:v1.31.1`; reCAPTCHA test mode; JDK `HttpServer` stub for the production verify path (DoD-P05) |
| Acceptance (frontend) | `frontend/tests/acceptance/` | Vitest + Testing Library via `verify.sh <p> frontend-test` | rendered `App` (UI contract `registration-form.schema.json`) | `fetch` stubbed at `/api` |
| End-to-end | `frontend/tests/e2e/` | Playwright Chromium via `verify.sh <p> frontend-e2e` | browser against a fresh compose stack `regtest-e2e` on 18090/18025 (KP-08) | Mailpit, reCAPTCHA test mode; test-only credentials in `tests/e2e/e2e.env` |
| Unit / integration | other test paths | phase 5 | — | — |

- Every test name contains the AC id(s) it verifies; all 48 ACs have at least one test (checked by grep of AC ids against `01_acceptance-criteria.md`).
- Rejection tests also assert that nothing was stored (database contract rows, JSON copies) and, where relevant, that no email was sent.
- `tests/e2e` is excluded from `tsc` (Node built-ins without `@types/node`, which is not in `tech-stack.md`); Playwright transpiles it and ESLint/Prettier check it.
- The harness passes configuration only by the environment names of the specification §5, plus the standard `spring.datasource.*`/`spring.mail.*` keys with the same values.

## First complete run (before any fix)

Phase 3 red run on bootstrap code (2026-10-06): 0 passed, 114 failed. No test passes on bootstrap code; no build, configuration or harness errors.

| Group | Tests | Failed | Reason (behaviour missing) | Log |
|---|---|---|---|---|
| Backend acceptance | 79 | 79 | 74 wrong status code (endpoints absent → default security 401); 3 startup with invalid options succeeds (AC-003-04); 2 option lists empty (AC-003-01/02) | `out/logs/3_backend-acceptance-red.log` |
| Frontend acceptance | 29 | 29 | form element `registration-form` not rendered | `out/logs/3_frontend-acceptance-red.log` |
| End-to-end | 6 | 6 | stack not defined yet (`docker-compose.yml` absent) → `ERR_CONNECTION_REFUSED` | `out/logs/3_frontend-e2e-red.log` |

## Final run (phase 6)
