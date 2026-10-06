# Test strategy (as executed)

> Written in: phases 3, 5 and 6 · Source: `general/standards.md` ("Tests"), phase cards 3 and 5 · Agent: writes

## Test levels as executed

| Level | Location | Runner | Interface under test | Substitutes |
|---|---|---|---|---|
| Acceptance (backend) | `backend/src/test/java/si/konferenca/registration/acceptance/`, `backend/src/test/resources/acceptance/` | JUnit 6 via `verify.sh <p> backend-test` | REST API over real HTTP (`openapi.yaml`); storage via `database.sql` and `json-copy.schema.json`; mail via Mailpit API (`emails.schema.json`); startup with configuration (AR-04) | Testcontainers `postgres:16.15-alpine`, `axllent/mailpit:v1.31.1`; reCAPTCHA test mode; JDK `HttpServer` stub for the production verify path (DoD-P05) |
| Acceptance (frontend) | `frontend/tests/acceptance/` | Vitest + Testing Library via `verify.sh <p> frontend-test` | rendered `App` (UI contract `registration-form.schema.json`) | `fetch` stubbed at `/api` |
| End-to-end | `frontend/tests/e2e/` | Playwright Chromium via `verify.sh <p> frontend-e2e` | browser against a fresh compose stack `regtest-e2e` on 18090/18025 (KP-08) | Mailpit, reCAPTCHA test mode; test-only credentials in `tests/e2e/e2e.env` |
| Unit (backend) | `backend/src/test/java/.../{domain,application,infrastructure,web,config}/` | JUnit 6, Mockito, Spring mocks | classes directly; reCAPTCHA production path against a JDK `HttpServer` stub (DoD-P05) | fake transaction manager, Mockito mail sender |
| Architecture (backend) | `backend/src/test/java/.../architecture/ArchitectureTest.java` | ArchUnit | compiled classes: layers of spec §2, domain purity, entity location, no slice cycles (AR-02, AR-03) | — |
| Integration (backend) | `backend/src/test/java/.../integration/` | JUnit 6 on the acceptance harness | running backend: security headers once per response (SB-10, KP-02), 401/415/429 paths, health, production startup guard (SR-02), Flyway V1 = `database.sql` (AR-06) | Testcontainers as above |
| Unit / component (frontend) | `frontend/src/**/*.test.ts(x)` | Vitest + Testing Library | validation rules, API client status mapping, form edge paths, Google widget loading, AR-01/AR-07 source scan | `fetch`/`grecaptcha` stubs |

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

Phase 5 first complete run (all levels, before any fix; 2026-10-06T18:49Z): **266 passed, 2 failed** of 268 (`out/logs/5_first-run_*.log`).

| Failure | Class | Action |
|---|---|---|
| `FileJsonCopyStoreTest.writingTheSameRegistrationTwiceFails`: a second write replaced the existing copy (`ATOMIC_MOVE` overwrites on Linux) | implementation defect | `FileJsonCopyStore` publishes via hard link, which never replaces an existing copy (commit 4edfcc6) |
| `FiltersTest.streamedBodyOverLimitFailsWhileReading`: expected a read after end of stream to throw | defect in a non-frozen test | test corrected to check the single-byte path, bulk path split into its own test (commit 822a0d3) |

After the fixes: 197 backend + 66 frontend + 6 end-to-end = 269 passed, 0 failed (`out/logs/5_*.log`). Coverage (record only): backend line 96.4 %, branch 91.7 % (JaCoCo); frontend line 100 %, branch 97.05 % (V8).

## Final run (phase 6)

Phase 6 (2026-10-06), after fixes F-05..F-10, all levels: **279 passed, 0 failed** — backend 204 (unit, architecture, integration, 79 frozen acceptance), frontend 69 (unit + 29 frozen acceptance), end-to-end 6 (`out/logs/6_*-test.log`, `6_frontend-e2e.log`).

| Measure (record only) | Backend | Frontend |
|---|---|---|
| Line / branch coverage | 96.3 % / 92.5 % (JaCoCo, all backend tests) | 100 % / 97.08 % (V8) |
| Mutation score | 83 % (PIT 281/340; unit-level tests only, Spring wiring classes excluded because acceptance/integration tests start whole applications) | 82.43 % (Stryker, unit + acceptance) |

Test fixes in phase 6: added tests for catchable survivors (F-08, F-09); extracted a shared submit helper in `RegistrationForm.test.tsx` (F-10). No frozen test changed in phase 6.

