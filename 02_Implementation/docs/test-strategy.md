# Test Strategy — Conference Registration System

Phase 4 artefact. Tests were written after the implementation (Classical
SDD) from the User Stories / Acceptance Criteria (behaviour), the
Specification (API, integration, security, architecture) and the
implementation (unit tests).

## 1. Test levels and tools

| Level | Tooling | Location | What it covers |
| --- | --- | --- | --- |
| Backend unit | JUnit 5, Mockito, AssertJ | `backend/src/test/java/.../service`, `infrastructure`, `security`, `api/dto` | Registration use case (validation, captcha, transaction/backup/rollback, email isolation), form config ordering, option catalog loading, JSON backup store, Excel writer, mail composition, reCAPTCHA adapters, rate-limit and body-size filters, DTO trimming/validation |
| Backend API + integration + acceptance | Spring Boot Test, MockMvc, Testcontainers PostgreSQL 16, Spring Security Test | `RegistrationApiIntegrationTest`, `BackupFailureIntegrationTest`, `RateLimitIntegrationTest`, `RestartAndReconfigurationIntegrationTest` | Full HTTP → DB → backup → mail flow against a real PostgreSQL with Flyway; error format; security headers; export access control; restart persistence; configuration changes |
| Architecture | ArchUnit | `ArchitectureTest` | The seven rules declared in specification §2.1 |
| Frontend component | Vitest, React Testing Library, jsdom | `frontend/src/*.test.ts(x)` | Forms per type, option grouping, consent not preselected, client validation, request payload, confirmation only after 201, server errors, failure handling, reCAPTCHA mode, output encoding |
| End-to-end | Playwright (Chromium) | `frontend/e2e/registration.spec.ts` | Browser → Vite dev proxy → backend (captcha test mode) → PostgreSQL → Mailpit; export over HTTP |

External systems:

- **PostgreSQL**: real database via Testcontainers (integration) and
  Docker Compose (E2E). No in-memory substitute.
- **SMTP**: `JavaMailSender` mocked at the boundary in integration tests
  (messages captured and parsed); real Mailpit in E2E.
- **reCAPTCHA**: deterministic test mode in integration/E2E; production
  adapter tested with `MockRestServiceServer`. No test calls Google.

## 2. How to run

```bash
# backend: unit + integration + ArchUnit (needs Docker for Testcontainers)
cd backend && ./mvnw test            # JaCoCo report: target/site/jacoco/

# frontend: component/unit tests, lint, types
cd frontend && npm ci && npm test && npm run lint && npm run typecheck
npm run test:coverage                 # coverage via @vitest/coverage-v8

# E2E: start postgres, Mailpit and the backend, then Vite, then Playwright
docker compose up -d postgres smtp backend   # from IMPLEMENTATION_ROOT (backend jar built first)
cd frontend && npx vite --port 5173 &        # baseURL of playwright.config.ts
npx playwright test
```

E2E environment variables (defaults match `docker-compose.yml`):
`E2E_MAILPIT_URL` (`http://localhost:8025`), `E2E_ORGANIZER_USERNAME`
(`organizer`), `E2E_ORGANIZER_PASSWORD` (`local-dev-organizer`).

Vitest excludes `e2e/**` (added to `vitest.config.ts`) so Playwright
specs run only under Playwright.

## 3. Test data

- Slovenian/Unicode values in all layers (`Žiga`, `Čeh`, `Špela`,
  `Institut Jožef Stefan`, `Računalništvo in informatika`).
- Bundled sample option configuration (`conference-options.json`)
  includes an inactive option (`ws-legacy`) for negative tests.
- Integration tests clean the tables and backup directory before each
  test; E2E uses unique email addresses per run.

## 4. Negative, edge-case and security tests

- Missing / whitespace-only required fields for every fixed field of
  both types; invalid email formats; length boundaries (100 / 101);
  control characters; NBSP and other Unicode space separators trimmed.
- Unknown, inactive, malicious (`../../etc/passwd`, SQL fragments) and
  too many option ids; duplicate ids collapsed.
- Missing / false consent; consent not preselected in the UI.
- Missing / forged captcha token; Google unavailable → rejected;
  production mode without secret fails fast.
- Unknown JSON properties (mass assignment across types), malformed JSON,
  wrong content type, wrong method, unknown path — uniform error body
  without stack traces or exception details.
- Oversized body (declared and undeclared length) → 413 / read failure.
- Rate limiting per IP for registration and organizer export (429 +
  `Retry-After`).
- Export: no credentials / wrong password → 401 with no data; formula
  injection neutralised; string cells only.
- Security headers on API responses; non-API paths denied; health
  endpoints expose no details.
- HTML/script input rendered as text (React) and returned only as JSON.
- Backup write failure → 500, rollback, no email; commit failure →
  backup deleted, no email; email failure → registration still
  accepted.

## 5. Acceptance-criteria traceability

| AC | Tests |
| --- | --- |
| AC-001-01 | `ExternalRegistration.acceptsValidRegistrationAndStoresIt`; `App` "submits trimmed data…"; E2E external participant |
| AC-001-02 | `App` "shows the external form…" |
| AC-001-03 | `ExternalRegistration.rejectsMissingRequiredField` (×4); `App` "does not submit an invalid form…"; `validation.test`; E2E invalid submission |
| AC-001-04 | `ExternalRegistration.rejectsWhitespaceOnlyRequiredField` (×4); `validation.test` |
| AC-001-05 | `ExternalRegistration.rejectsInvalidEmail` (×5); `RegistrationRequestValidationTest`; `validation.test`; E2E invalid submission |
| AC-001-06 | `acceptsValidRegistrationAndStoresIt`; `App` payload `optionIds`; E2E external |
| AC-001-07 | `ExternalRegistration.acceptsRegistrationWithoutOptions` |
| AC-001-08 | `rejectsMissingMandatoryConsent`, `rejectsAbsentConsentObject`; `RegistrationServiceTest.rejectsMissingMandatoryConsent`; E2E invalid submission |
| AC-001-09 | `trimsLeadingAndTrailingWhitespace`; `RegistrationRequestValidationTest.trims*`; `App` payload trimmed |
| AC-001-10 | `preservesUnicodeIncludingSlovenianCharacters`; export test; E2E student |
| AC-002-01 | `StudentRegistration.acceptsValidStudentRegistration`; `App` student endpoint; E2E student |
| AC-002-02 | `App` "switches to the student form…" |
| AC-002-03 | `StudentRegistration.rejectsMissingOrBlankRequiredField` (×6) |
| AC-002-04 | `StudentRegistration.rejectsInvalidEmail` |
| AC-002-05 | `acceptsValidStudentRegistration` (options recorded) |
| AC-002-06 | `StudentRegistration.rejectsMissingMandatoryConsent` |
| AC-002-07 | `acceptsAnyNonEmptyStudentIdWithoutExternalVerification` |
| AC-002-08 | `OptionValidation.rejectsUnknownOption`, `rejectsMaliciousOptionIdentifier`; `RegistrationServiceTest` |
| AC-002-09 | `OptionValidation.rejectsInactiveOption`; `RegistrationServiceTest`; `App` server errors |
| AC-002-10 | `App` "renders active options… consent unchecked"; E2E options/consent |
| AC-003-01 | `FormConfiguration.returnsOnlyActiveOptionsGroupedByCategory`; `FormConfigServiceTest`; E2E options |
| AC-003-02 | same as AC-003-01; `App` option groups |
| AC-003-03 | `RestartAndReconfigurationIntegrationTest` (rename, deactivate, add via file) |
| AC-003-04 | `FileConferenceOptionCatalogTest.skipsEntriesMissingRequiredAttributes` |
| AC-003-05 | `RestartAndReconfigurationIntegrationTest` (stored id after rename); export `name [id]` |
| AC-004-01 | `App` "confirmation only after success"; E2E external/student |
| AC-004-02 | `App` client and server error tests; E2E invalid submission |
| AC-004-03 | `BackupFailureIntegrationTest`; `RegistrationServiceTest.backupFailure*`, `commitFailure*`; `App` 500/429/network |
| AC-005-01 | `acceptsValidRegistrationAndStoresIt`; export tests |
| AC-005-02 | `RestartAndReconfigurationIntegrationTest` |
| AC-005-03 | `assertNothingStored` in every rejection test |
| AC-005-04 | `Export.exportReflectsCurrentList` |
| AC-006-01 | `BackupAndEmail.sendsParticipantConfirmationEmail`; `MailRegistrationNotifierTest`; E2E Mailpit |
| AC-006-02 | `assertNothingStored` (no mail) in rejection tests |
| AC-007-01 | `sendsOrganizerNotificationWithDataAndJsonAttachment`; `MailRegistrationNotifierTest`; E2E Mailpit |
| AC-007-02 | `assertNothingStored` (no mail) in rejection tests |
| AC-008-01 | `Export.organizerExportsAllRegistrationsAsExcel`; E2E export |
| AC-008-02 | `organizerExportsAllRegistrationsAsExcel`; `PoiRegistrationExportWriterTest` |
| AC-008-03 | `organizerExportsAllRegistrationsAsExcel` (Unicode cells); writer test |
| AC-008-04 | `exportWithoutCredentialsIsDenied`, `exportWithWrongPasswordIsDenied`; E2E export |
| AC-008-05 | `exportReflectsCurrentList` |
| AC-008-06 | `emptyExportContainsOnlyHeader`; writer test |

## 6. First complete test execution (before repairs)

Recorded in `run-log.json` → `firstTestRun`: **153 passed, 4 failed**
(backend 122/126, frontend Vitest 26/26, Playwright E2E 5/5).

| Failure | Cause | Classification | Repair |
| --- | --- | --- | --- |
| `trimsLeadingAndTrailingWhitespace` | `String.strip()` does not remove U+00A0 (no-break space) | Implementation defect (AC-001-09) | `ValidationCodes.trim` also strips Unicode space separators |
| `RestartAndReconfigurationIntegrationTest` | Test passed settings as default properties (overridden by `application.yml`); then lacked a servlet context for Spring Security | Test defect | Pass settings as command-line args; start with `SERVLET` + random port |
| `CaptchaVerifierTest.productionModeAcceptsSuccessfulGoogleVerification` | Adapter replaced the builder's request factory, bypassing `MockRestServiceServer` | Testability defect | Package-private constructor taking a `RestClient`; Spring still uses the builder + timeouts |
| `MailRegistrationNotifierTest.sendsOrganizerNotification…` | Content type read before `saveChanges()` | Test defect | Call `saveChanges()` as `JavaMailSender` does |

Additionally, Vitest collected `e2e/registration.spec.ts` (a Playwright
file) and reported it as a failed suite with 0 tests; `e2e/**` is now
excluded in `vitest.config.ts`.

After repairs: backend 127/127, frontend 26/26 (E2E re-executed in
Verification).
