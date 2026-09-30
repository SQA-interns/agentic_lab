# Test strategy (as executed)

> Written in: the test phases of the selected workflow, and phase 6 · Source: `general/quality/test-strategy.md`, workflow `rules.md` · Agent: writes

## Test levels as executed

| Level | Phase | Location (relative to `02_output/`) | Interface used | Frozen | Command |
|---|---|---|---|---|---|
| Acceptance (backend) | 3 | `backend/src/test/java/si/konferenca/registration/acceptance/`, fixtures `backend/src/test/resources/acceptance/` | HTTP on a random port, SQL schema (JDBC), JSON copy directory, Mailpit API; Testcontainers `postgres:16.15-alpine` and `axllent/mailpit:v1.31.1`; local reCAPTCHA mock (`RecaptchaMock`) | yes | `cd backend && ./mvnw test` |
| End-to-end | 3 | `frontend/e2e/` | Browser (Playwright Chromium) against the running local stack on `http://127.0.0.1:8081`, Mailpit API, export API | yes | `cd frontend && npm run e2e` (stack running, `E2E_ORGANIZER_USERNAME` / `E2E_ORGANIZER_PASSWORD` set from `.env`) |
| Unit / integration (backend) | 5 | `backend/src/test/java/si/konferenca/registration/` outside `acceptance` | Java classes, ArchUnit | no | `cd backend && ./mvnw test` |
| Unit / component (frontend) | 5 | `frontend/src/**/*.test.ts(x)` | Vitest + Testing Library | no | `cd frontend && npm test` |

Design rules applied:

- Every test name (`@DisplayName` or Playwright title) starts with the AC id it verifies.
- Rejection criteria also assert that nothing was stored: no database row with the submitted email and no JSON copy mentioning it.
- Tests are isolated by unique email addresses and distinct `X-Forwarded-For` client addresses (rate-limit buckets).
- Contexts with special settings (reCAPTCHA production path, unreachable endpoint, small rate limits, HTTPS-only, SMTP down, unwritable JSON directory) are separate test classes; startup refusal is observed by starting the application with command-line settings (`support/Startup`).
- No test calls Google or an external SMTP server; the real services are listed for manual testing in the release notes.

AC → test classes:

| AC | Tests |
|---|---|
| AC-001-01 … 07, 09, 11, 13, 14 | `ExternalRegistrationAcceptanceTest` |
| AC-001-08 | `RecaptchaVerificationAcceptanceTest`, `RecaptchaUnreachableAcceptanceTest` |
| AC-001-10 | `RegistrationRateLimitAcceptanceTest` |
| AC-001-12, AC-001-13 (NFR-01 end to end) | `us001-external-form.e2e.spec.ts` |
| AC-001-15 | `StartupConfigurationAcceptanceTest` |
| AC-002-01, 02 | `StudentRegistrationAcceptanceTest` |
| AC-002-03 | `us002-student-form.e2e.spec.ts` |
| AC-003-01 | `OptionsAcceptanceTest` |
| AC-003-02, 03 | `OptionsConfigurationAcceptanceTest` |
| AC-003-04 | `us003-options.e2e.spec.ts` |
| AC-004-01, 02, 03 | `us004-confirmation.e2e.spec.ts` |
| AC-005-01, 02 | `StorageAcceptanceTest` |
| AC-005-03 | `JsonCopyFailureAcceptanceTest` |
| AC-006-01, 02 | `ParticipantEmailAcceptanceTest` |
| AC-006-03 | `SmtpFailureAcceptanceTest` |
| AC-007-01, 02 | `OrganizerNotificationAcceptanceTest` |
| AC-008-01, 02 | `ExportAcceptanceTest` |
| AC-008-03 | `ExportHttpsOnlyAcceptanceTest` |
| AC-008-04 | `ExportRateLimitAcceptanceTest` |

### Phase 3 run against the bootstrap skeleton (before any production code)

Backend (`logs/03_backend-acceptance-run.log`): 105 run, 3 passed, 102 failed, 0 errors.

| Group | Result | Reason |
|---|---|---|
| All HTTP criteria (US-001 … US-008) | fail | skeleton security denies every non-health request: `expected 201/400/409/413/429/500/200/401/403 but was 403`; behaviour missing |
| AC-001-15 refusal cases, AC-003-03 invalid-file cases | fail | the skeleton starts although it should refuse (`Expecting value to be false but was true`) |
| AC-001-15 `defaultReportsTestModeOff` | fail | `/api/config` missing (403) |
| AC-001-15 `startsWithKeysAndTestModeOff`, `productionStartsWhenConfigured`; AC-003-03 `validConfigurationStarts` | **pass on bootstrap** | control cases: a correctly configured backend starts, which the skeleton already does; they guard the refusal tests against passing for the wrong reason |

End-to-end (`logs/03_e2e-skeleton-run.log`, stack from `docker compose up` with the skeleton): 8 run, 0 passed, 8 failed. Reasons: `/api/options` answers 403 (expected 200) in 6 tests; the form (`Register` button) is missing in 2 tests. All behavioural; no build or harness error.

## First complete run (before any fix)

Run on 2026-09-30 after phase 5 tests were written, before any fix; all levels together: **272 passed, 0 failed**.

| Level | Tests | Passed | Failed | Log |
|---|---|---|---|---|
| Backend acceptance (frozen) | 105 | 105 | 0 | `logs/05_first-full-run-backend.log` |
| Backend unit and architecture (`application`, `domain`, `infrastructure`, `web`, `config`, `architecture` test packages) | 116 | 116 | 0 | `logs/05_first-full-run-backend.log` |
| Frontend unit and component (Vitest) | 43 | 43 | 0 | `logs/05_first-full-run-frontend.log` |
| End-to-end (frozen, Playwright) | 8 | 8 | 0 | `logs/05_first-full-run-e2e.log` |

Classification: no failures, so nothing to classify. Earlier, during phase 4, one frozen test was found wrong (D-12, human-approved correction of a JDBC type cast); no other frozen or non-frozen test was changed.

Unit test scope (phase 5): validation rules (all field, option, consent and token branches), the registration use case with mocked ports (order of storage, copy, commit and emails; rollback and copy removal on failure; email failures swallowed), options file validation (17 invalid variants), JSON copy writing, Google verifier against a local HTTP stub (encoding, failure modes), email building (headers, charset, attachment bytes), workbook cells, rate-limit windows, request-size stream counting, HTTPS filter, error mapping, startup checks, and ArchUnit rules A1 to A6. Frontend: validation, API client status mapping, and each component (options, consents, reCAPTCHA both modes, form, confirmation, app loading).

## Final run (phase 6)

Run on 2026-09-30 after the phase 6 fix loops (F-01, F-05, F-06, F-09): **281 passed, 0 failed**.

| Level | Passed | Failed | Log |
|---|---|---|---|
| Backend acceptance (frozen) | 105 | 0 | `logs/06_final-run-backend.log` |
| Backend unit, integration and architecture | 123 | 0 | `logs/06_final-run-backend.log` |
| Frontend unit and component | 45 | 0 | `logs/06_final-run-frontend.log` |
| End-to-end (frozen) | 8 | 0 | `logs/06_final-run-e2e.log` |

Measures:

| Measure | Backend | Frontend |
|---|---|---|
| Line / branch coverage, unit (and integration) tests | 81.7 % / 92.7 % | 92.4 % / 99.2 % |
| Line / branch coverage, acceptance tests | 91.3 % / 77.2 % | e2e not instrumented |
| Line / branch coverage, all backend tests | 96.6 % / 95.9 % | — |
| Mutation score (unit tests; validation, security, persistence and business-rule code included) | 84 % (PIT, 273/325, test strength 96 %) | 84.2 % (Stryker) |

Changes to non-frozen tests in phase 6: unit tests strengthened for F-05 and F-06 (no assertion weakened); one integration test added for F-09.
