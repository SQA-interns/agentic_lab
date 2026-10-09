# Test strategy (as executed)

> Written in: phases 3, 5 and 6 · Source: `standards/testing.md` · Procedure: `skills/write-acceptance-tests`, `skills/unit-tests`, `skills/verify-release` · Agent: writes

## Test levels as executed

| Level | Component | Location | Interface | Substitutes | Run with |
|---|---|---|---|---|---|
| Acceptance | backend | `backend/src/test/java/si/konferenca/registration/acceptance/`, `backend/src/test/resources/acceptance/` | REST over HTTP on a random port; stored state read from PostgreSQL (`database.sql`), the JSON copy directory and Mailpit's API | Testcontainers `postgres:16.15-alpine`, `axllent/mailpit:v1.31.1`; reCAPTCHA test mode; failure injection: copy directory replaced by a file, own database container stopped, SMTP on a closed port; separate application instances for restart and startup-refusal cases | `verify.sh <phase> be-test` |
| Acceptance | frontend | `frontend/src/acceptance/` | the rendered page (`<App />`, Testing Library, jsdom); `fetch` stubbed with `openapi.yaml` responses | none | `verify.sh <phase> fe-test` |
| End-to-end | all | `frontend/e2e/`, `frontend/playwright.config.ts` | Chromium against the local stack through the frontend's nginx; Mailpit API; export over HTTP | local stack (`docker-compose.yml`, test mode on) | `verify.sh <phase> stack-up`, then `e2e` |
| Unit | backend | `backend/src/test/java/…/{application,domain,infrastructure,api,config}/` | classes directly; fakes for ports, Mockito for the mail sender and transaction manager, Spring mock servlet objects, a local JDK HTTP server as the reCAPTCHA endpoint (DoD-P05) | none | `verify.sh <phase> be-test` |
| Integration | backend | `backend/src/test/java/…/integration/` | real HTTP against the application; repository and transaction beans; separate instances with test mode off against a mocked verification endpoint and a low rate limit | the acceptance `TestStack` (PostgreSQL, Mailpit), reused unchanged | `verify.sh <phase> be-test` |
| Architecture | backend | `backend/src/test/java/…/ArchitectureTest.java` | ArchUnit over production classes: layers of the specification section 2, no slice cycles, persistence and mail/POI/HTTP client confined | none | `verify.sh <phase> be-test` |
| Unit / component | frontend | `frontend/src/*.test.ts`, `frontend/src/components/*.test.tsx` | modules and components with Testing Library; `fetch` and `window.grecaptcha` stubbed | none | `verify.sh <phase> fe-test` |

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

2026-10-09, all levels, after the phase 5 tests were written and compiled (`logs/5first_*`): **257 passed, 5 failed** (backend 206/5, frontend 45/0, end-to-end 6/0).

| Failing test | Class | Action |
|---|---|---|
| `ApiIntegrationTest.malformedJsonUnknownPropertiesAndUnknownTypesAreInvalidRequests`: a body with an unknown property got 201 | implementation defect: Boot's Jackson setting ignores unknown properties, the class-level annotation cannot re-enable failing (specification section 4) | fixed `de3e3e7` (`spring.jackson.deserialization.fail-on-unknown-properties: true`) |
| `RegistrationValidatorTest` × 2: `NullPointerException` | defect in a non-frozen test: null ids in the test data, which the command record cannot hold | test data fixed; the validator's unreachable null checks removed (`8da3e1f`) |
| `SmtpRegistrationNotifierTest` × 2: content type not set | defect in a non-frozen test: the mocked sender never calls `saveChanges()` | the test calls it before inspecting |

Defects in further non-frozen tests found while closing mutation gaps (never in a complete run): mock input stream finishes only after reading -1; nested `related` multipart; reCAPTCHA callback outside `act`; an untyped mock call tuple. All fixed in the tests.

## Measures (phase 5)

Final phase 5 run, all levels (`logs/5final_*`), stack rebuilt from the phase 5 code. Coverage is for all levels together (JaCoCo and V8 report one figure per run).

| Component | Level | Tests passed / failed | Line coverage | Branch coverage | Mutation score (scope) | Tools | Log |
|---|---|---|---|---|---|---|---|
| backend | acceptance | 113 / 0 | 97.9 % (all levels) | 90.3 % (all levels) | — | JUnit 6, Testcontainers, JaCoCo | `logs/5final_be-test.log` |
| backend | unit | 99 / 0 | | | 98 % (292/297 killed): application, domain, infrastructure except the JPA adapter, filters, startup guard | Pitest 1.30.0 | `logs/5b_be-mutation.log` |
| backend | integration | 17 / 0 | | | 60 % (37/62): JPA adapter, security configuration, controllers, error handler against 4 Spring-context test classes; survivors below | Pitest 1.30.0 | `logs/5_be-mutation-wiring.log` |
| backend | architecture | 4 / 0 | | | — | ArchUnit 1.3.2 | `logs/5final_be-test.log` |
| frontend | acceptance | 16 / 0 | 100 % (unit + acceptance) | 95.5 % | — | Vitest 4.1.11, Testing Library, V8 | `logs/5final_fe-test.log` |
| frontend | unit / component | 46 / 0 | | | 97.2 % (415 killed + 69 timeout of 498): all `src` except tests and `main.tsx` | Stryker 10.0.0 | `logs/5_fe-mutation.log` |
| all | end-to-end | 6 / 0 | — | — | — | Playwright 1.63.0 | `logs/5final_e2e.log` |

Mutation runs: first run backend 82 %, frontend 93.0 %; the survivors in validation, security, persistence and business-rule code were closed with tests (commits `78c1d5e`, `9d83bd8`, `eed81f6`, `c1ea5bc`, `278643e`, `b3b1e96`), giving the scores above.

### Surviving mutants (validation, security, persistence, business rules)

| Mutant | Classification |
|---|---|
| `RegistrationService` l.115 negation, no coverage | equivalent: copy of the `finally` block on the normal return path, where `committed` is always true |
| `RequestSizeFilter` l.93 `n > 0` → `n >= 0` | equivalent: counting 0 bytes |
| `RequestSizeFilter.isReady` → true | not observable: delegation for asynchronous reads, which the application does not use |
| `FileRegistrationCopyStore` `force()` removed | not observable in a test: durability against power loss |
| `PoiWorkbookWriter` `setColumnWidth` removed | cosmetic, no requirement |
| `SecurityConfiguration` bean methods returning null, CORS setters removed | not detectable by the tool: the bean methods run only at context start, and Pitest reuses the cached Spring context within a minion; the behaviour is tested: every 401/200 export test needs the filter chain, encoder and user, CORS by `RequestLimitsIntegrationTest` |
| `SecurityConfiguration` access-denied handler, no coverage | unreachable: the only user has the organizer role |
| `ApiExceptionHandler` handlers returning null, no coverage in the targeted run | covered by acceptance and integration classes outside the targeted selection (captcha, duplicate, 413, 415, 405, 404 tests) |
| `ApiExceptionHandler.rootType` | logging only |
| `ApiExceptionHandler.unreadable` cause check | covered by `RequestLimitsIntegrationTest.oversizedBodyWithoutContentLengthIsRefusedWhileRead` (added after the targeted run) |
| `RegistrationController` list-size limits | covered by `RequestLimitsIntegrationTest.fiftyOptionIdsAndTwentyConsentIds…` (added after the targeted run) |
| `RegistrationFormController` returning null | caught by AC-003-01 (outside the targeted selection) |
| `JpaRegistrationRepository.violates` returning true, no coverage | not observable without a database fault other than the unique constraint |
| frontend `RegistrationForm` `toggle` (l.57) | killed when applied by hand ("unchecks an option on a second click" fails); Stryker's per-test coverage did not attribute it |
| frontend `RegistrationForm` `?? ""` (l.86, 95), `errorCode !== null` (l.115) | equivalent: unreachable after client validation / same generic message |
| frontend `Captcha` effect dependencies and reset guard | equivalent: the site key never changes at runtime; reset without a widget is guarded by the remaining conditions |
| frontend `RegistrationPage` `active` flag | not observable: guards state updates after unmount |

## Final run (phase 6)

| Component | Level | Tests passed / failed | Line coverage | Branch coverage | Mutation score (scope) | Tools | Log |
|---|---|---|---|---|---|---|---|
