# Test strategy (as executed)

> Written in: phases 3, 5 and 6 · Source: `standards/testing.md` · Procedure: `skills/write-acceptance-tests`, `skills/unit-tests`, `skills/verify-release` · Agent: writes

## Test levels as executed

| Level | Component | Location | Public interface used | Run with |
|---|---|---|---|---|
| Acceptance | backend | `backend/src/test/java/si/konferenca/registration/acceptance/` | REST API over HTTP on a random port; PostgreSQL (`db-schema.sql`), JSON copy files (`registration-copy.schema.json`) and Mailpit's HTTP API as the observable outputs | `verify.sh <phase> be-test` |
| Acceptance | frontend | `frontend/tests/acceptance/` | the rendered page (`App`), accessible names of `ui-registration-form.json`; `fetch` mocked with `api.openapi.yaml` responses | `verify.sh <phase> fe-test` |
| End-to-end | frontend + backend | `frontend/tests/e2e/` | browser against the running local stack (`http://frontend:8080`), Mailpit API (`http://mailpit:8025`) | `verify.sh <phase> e2e` (needs `docker compose up`) |
| Architecture | backend | `backend/src/test/java/si/konferenca/registration/architecture/` | compiled classes (ArchUnit): layers of `02_specification.md` §2, no cycles (AR-02, AR-03) | `verify.sh <phase> be-test` |
| Unit | backend | `backend/src/test/java/si/konferenca/registration/{domain,application,infrastructure,config,web}/` | classes directly, with fakes; local HTTP server for the reCAPTCHA client; Spring mock requests for filters | `verify.sh <phase> be-test` |
| Integration | backend | `backend/src/test/java/si/konferenca/registration/integration/` | running backend over HTTP (same environment as the acceptance tests, used read-only): headers, malformed bodies, size and rate limits, HTTPS-only access, health | `verify.sh <phase> be-test` |
| Unit | frontend | `frontend/src/*.test.ts(x)` | modules and components with `fetch` mocked; reCAPTCHA widget mocked on `window.grecaptcha` | `verify.sh <phase> fe-test` |

Harness change after the freeze: `frontend/src/test-setup.ts` unmounts each test's page (D-14, human-approved); no frozen file changed.

Harness rules fixed by the freeze:

- Backend acceptance tests configure the backend only through the settings of `02_specification.md` §9 (environment-variable names as Spring properties). They also set `spring.datasource.*` to the same values, so the context starts on code that does not map `DB_URL` yet.
- Test configuration: `backend/src/test/resources/acceptance/conference-config.json`, a copy of `conference-config.example.json`. Test-mode token `test-pass`. Organizer test credentials live only in `TestEnvironment`; none is a real secret.
- Fault injection uses only the environment. JSON copy failure: the copy directory is replaced by a regular file. Database failure: a deferred constraint trigger fails the commit after the JSON copy was written. SMTP failure: SMTP points at a closed port. reCAPTCHA live path: a local mock of the verification endpoint (`CaptchaMock`, DoD-P05).
- "Nothing stored" means no database row and no file in the copy directory. "No email" means Mailpit is empty after a 3-second quiet period.
- End-to-end tests read options and consents from `GET /api/form-config` and use unique email addresses per run, so they work with any local configuration and with data from earlier runs.
- The helpers were exercised once against real data with temporary probes (database queries and triggers, Mailpit with a UTF-8 multipart mail and attachment, JSON copy files, POI workbook, reCAPTCHA mock, stand-in page). The probes were deleted before the freeze.

## Freeze run (phase 3)

Run on the bootstrap code (skeleton backend with Spring Security defaults; frontend page with a heading only). Logs: `02_output/logs/3_be-test.log`, `3_fe-test.log`, `3_e2e.log`.

| Component | Level | Tests | Passed | Failed |
|---|---|---|---|---|
| backend | acceptance | 86 | 0 | 86 |
| frontend | acceptance | 20 | 0 | 20 |
| frontend + backend | end-to-end | 9 | 0 | 9 |

Failure groups:

- backend, 86: `AssertionFailedError` on the first status assertion (expected 200/201/400/409/500/503, was 401: every endpoint is still behind the default security and none exists).
- frontend acceptance, 20: `Unable to find role="button" and name "Register"`: the form does not exist yet.
- end-to-end, 9: `getaddrinfo ENOTFOUND frontend`: the local stack (phase 4) does not exist yet. The run used a temporary empty `registration_default` network, removed afterwards.

Passing on bootstrap code: none. The skeleton's own unit test `src/App.test.tsx` (1, passing) is not an acceptance test.

Every AC has at least one test whose name contains its id (51 of 51 ACs).

## First complete run (phase 5, before any fix)

All levels together, after the phase 5 unit and integration tests were written: 263 passed, 0 failed (backend 203 in `logs/5_be-test.log` at that run, frontend 51, end-to-end 9 in `logs/5_e2e.log`). No compile errors and no failures, so nothing was classified or fixed.

Tests added after the first run were added only to kill surviving mutants (below); no test was changed to pass.

## Measures (phase 5)

Coverage: JaCoCo 0.8.12 (backend), Vitest v8 3.2.7 (frontend). Mutation: PIT 1.30.0 with the JUnit 5 plugin (backend), Stryker 10.0.0 with the Vitest runner (frontend).

| Component | Level | Tests passed / failed | Line coverage | Branch coverage | Mutation score (scope) | Tools | Log |
|---|---|---|---|---|---|---|---|
| backend | acceptance | 86 / 0 | — | — | — | JUnit 6, Testcontainers | `logs/5_be-test.log` |
| backend | integration | 15 / 0 | — | — | — | JUnit 6, Testcontainers | `logs/5_be-test.log` |
| backend | architecture | 5 / 0 | — | — | — | ArchUnit | `logs/5_be-test.log` |
| backend | unit | 107 / 0 | 67.0% | 82.4% | 95.8%, 298 of 311 (scope below) | JUnit 6, JaCoCo, PIT | `logs/5-unit_be-test.log`, `logs/5_be-mutation.log` |
| backend | all levels | 213 / 0 | 96.4% | 92.2% | — | JaCoCo | `logs/5_be-test.log` |
| frontend | acceptance | 20 / 0 | — | — | — | Vitest | `logs/5_fe-test.log` |
| frontend | unit | 49 / 0 | — | — | — | Vitest | `logs/5_fe-test.log` |
| frontend | all levels (unit + acceptance) | 69 / 0 | 100% | 97.24% | 98.1%, 361 of 368 (all of `src` except `main.tsx`) | Vitest, v8, Stryker | `logs/5_fe-test.log`, `logs/5_fe-mutation.log` |
| frontend + backend | end-to-end | 9 / 0 | — | — | — | Playwright 1.63.0 | `logs/5_e2e.log` |

Frontend coverage is reported for unit and acceptance tests together: both run in one Vitest suite, and `verify.sh` filters tests by name, not by folder.

Backend mutation scope: `domain`, `application`, `infrastructure.config`, `infrastructure.copy`, `infrastructure.captcha`, `infrastructure.export`, `web.RateLimitFilter`, `web.RequestSizeLimitFilter`, `config.StartupGuard`, `config.AppProperties`, `config.OrganizerHttpsFilter`, mutated against the unit tests. Excluded: controllers, `SecurityConfig`, the JPA adapter, the SMTP notifier and Spring wiring. They only run inside a Spring context with Testcontainers, where each mutant would need a fresh context. The acceptance and integration tests cover them: AC-005-01 reads every stored column, and the integration tests check access control.

### Surviving mutants (validation, security, persistence, business rules)

| Mutant | Class | Reason it survives |
|---|---|---|
| `RegistrationValidator.email` returns `""` instead of `null` for an invalid email | validation | equivalent: with any field error no registration is built |
| `RequestSizeLimitFilter.LimitedStream.count`: `n > 0` → `n >= 0` | security | equivalent: consuming 0 bytes changes nothing |
| `RateLimitFilter` cleanup above 10,000 windows (2 survived, 4 without coverage) | security | not observable: it only frees memory of expired windows; an expired window is replaced anyway |
| `RequestSizeLimitFilter.LimitedStream.isFinished`, `isReady`, `setReadListener` (5 without coverage) | security | delegation for asynchronous reads, which Spring MVC does not use; not reachable |
| frontend `RegistrationForm` `?? ""` request fallbacks (4 without coverage) | validation | not reachable: the request is built only after the checks require these fields |
| frontend `api.ts` `Array.isArray` guard | security | equivalent: a non-array makes `filter` throw, which the same function catches and returns no errors |
| frontend `App` unmount guard | — | not observable: it only prevents state updates after unmount |
| frontend `Captcha` `script.async` | — | not observable in jsdom; loading order only |

Killed by tests added after the first run: config file (consent not an object, exactly 20 entries), startup limits at their minimum, rate-limit pass-through, organizer email lines, export with two rows, size limit with byte-wise reads and the 413 content type, reCAPTCHA interrupt handling, and the frontend form, captcha, API and loading behaviour (`src/FormBehaviour.test.tsx`).

## Final run (phase 6)

All levels, after fix loop 1 (`f4182f5`, frontend test tooling only); 292 passed, 0 failed. The end-to-end suite includes `runtime-demo.spec.ts` (DoD-P04), added in phase 6.

| Component | Level | Tests passed / failed | Line coverage | Branch coverage | Mutation score (scope) | Tools | Log |
|---|---|---|---|---|---|---|---|
| backend | acceptance | 86 / 0 | — | — | — | JUnit 6, Testcontainers | `logs/6_be-test.log` |
| backend | integration | 15 / 0 | — | — | — | JUnit 6, Testcontainers | `logs/6_be-test.log` |
| backend | architecture | 5 / 0 | — | — | — | ArchUnit | `logs/6_be-test.log` |
| backend | unit | 107 / 0 | 67.0% | 82.4% | 95.8%, 298 of 311 (scope in "Measures") | JUnit 6, JaCoCo, PIT | `logs/5-unit_be-test.log`, `logs/6_be-mutation.log` |
| backend | all levels | 213 / 0 | 96.4% | 92.2% | — | JaCoCo | `logs/6_be-test.log` |
| frontend | acceptance | 20 / 0 | — | — | — | Vitest | `logs/6_fe-test.log` |
| frontend | unit | 49 / 0 | — | — | — | Vitest | `logs/6_fe-test.log` |
| frontend | all levels (unit + acceptance) | 69 / 0 | 100% | 97.24% | 98.1%, 361 of 368 (all of `src` except `main.tsx`) | Vitest, v8, Stryker | `logs/6_fe-test.log`, `logs/6_fe-mutation.log` |
| frontend + backend | end-to-end | 10 / 0 | — | — | — | Playwright 1.63.0 | `logs/6_e2e.log` |
