# Test Strategy — Conference Registration System

Phase 4 artefact. The suite was written after the implementation
(Classical SDD). Behavioural/acceptance tests derive from the User Stories
and Acceptance Criteria, API and integration tests from the Specification,
and unit tests from the Specification and implementation.

## 1. Test levels and tooling

| Level | Tooling (frozen stack) | Location |
| --- | --- | --- |
| Backend unit | JUnit 5, Mockito, AssertJ | `backend/src/test/java/.../application`, `.../infrastructure`, `.../web/filter` |
| Backend API / integration | Spring Boot Test, MockMvc, Testcontainers PostgreSQL 16, Spring Security Test | `backend/src/test/java/.../web/*IntegrationTest.java` |
| Architecture | ArchUnit | `backend/src/test/java/.../ArchitectureTest.java` |
| Frontend unit | Vitest | `frontend/src/validation.test.ts`, `frontend/src/api/client.test.ts` |
| Frontend component | Vitest + React Testing Library (jsdom) | `frontend/src/components/RegistrationPage.test.tsx` |
| End-to-end | Playwright (Chromium) against Vite dev server → Docker Compose backend, Mailpit | `frontend/e2e/registration.spec.ts` |
| Coverage | JaCoCo (backend, `mvn test`), `@vitest/coverage-v8` (`npm run test:coverage`) | reports under `backend/target/site/jacoco`, `frontend/coverage` |

## 2. How to run

```bash
# Backend: unit + integration + ArchUnit (Docker required for Testcontainers)
cd backend && ./mvnw test

# Frontend unit + component
cd frontend && npm ci && npm test

# End-to-end (stack + dev server)
cd backend && ./mvnw package          # builds the jar used by the backend image
docker compose up -d --build          # from IMPLEMENTATION_ROOT; wait for backend "healthy"
cd frontend && npx playwright install chromium
npx vite --port 5173 &                # Playwright baseURL, proxies /api to :8080
npx playwright test
```

E2E environment: `RECAPTCHA_TEST_MODE=true` (deterministic token
`test-mode-token`, no call to Google), Mailpit API on `:8025`, organizer
credentials from `ORGANIZER_USERNAME`/`ORGANIZER_PASSWORD` (Compose
defaults `organizer` / `local-dev-organizer`).

Test infrastructure notes:

- `backend/src/test/resources/docker-java.properties` sets
  `api.version=1.44`, because Docker Engine 29+ rejects the default API
  version that Testcontainers' docker-java client uses.
- Integration tests share one PostgreSQL container (static singleton) and
  replace `JavaMailSender` with a Mockito bean so sent MIME messages can be
  inspected. Each test starts from an empty database and backup directory.
- `frontend/vitest.config.ts` excludes `e2e/**` so Vitest does not execute
  Playwright specs.

## 3. Test design by area

### Validation (unit, `RegistrationValidatorTest`, `validation.test.ts`)
Required fields per registration type, whitespace-only = missing,
trimming incl. Unicode whitespace, Slovenian characters preserved, email
format (valid/invalid equivalence classes), length boundaries (max and
max+1), control characters, unknown/inactive/null option ids, >50 options,
required vs optional consents, unknown consents, fields of the other type,
missing type, error ordering.

### Configuration (unit, `ConferenceCatalogTest`)
Active-only listing in configuration order, changed configuration is
reflected, identifier/name/status per option, fail-fast on duplicate ids,
invalid ids, missing name/category, duplicate consents.

### Use case (unit, `RegistrationServiceTest`)
Order validate → captcha → DB → JSON backup → commit → notify; identical
JSON for backup and email without the captcha token; nothing stored/sent
on validation or captcha failure; rollback and no email when the backup
fails; no backup/email when the DB fails.

### Adapters (unit)
`RecaptchaVerifierTest` — deterministic test mode, blank token, secret
required in production mode, success/failure/unreachable against a local
HTTP stub (no live Google call). `FileRegistrationBackupTest` — UTF-8 JSON
file, directory creation, file name without personal data.
`SmtpRegistrationNotifierTest` — participant and organizer recipients,
subjects, body content, JSON attachment, student fields, one failing email
does not block the other, subject without user input.
`PoiRegistrationWorkbookWriterTest` — header-only empty export, one row per
registration, type-specific columns, options per category, Unicode, formula-
like input stored as text. `FixedWindowRateLimiterTest` — limit, per-client
isolation, window reset, Retry-After seconds.

### API / integration (`RegistrationApiIntegrationTest`, `ExportApiIntegrationTest`, `RateLimitIntegrationTest`, `StorageFailureIntegrationTest`)
Against real PostgreSQL + Flyway schema: external and student happy paths
(DB rows, option/consent rows, JSON backup file, two emails), no-option
registration, every rejection path returns the specified status/code and
stores/sends nothing (missing fields, invalid email, unknown/inactive
option, missing consent, captcha failure, malformed JSON, unknown
property, invalid enum, wrong content type, oversized body), SQL/HTML-like
input stored verbatim, error bodies without internals, security headers,
denied unknown endpoints, health/readiness/liveness, export 401 without or
with wrong credentials (no data disclosed), header-only empty export,
current data with Unicode after new registrations, rate limit 429 with
`Retry-After` per client IP, backup failure → 500 with DB rollback and no
email.

### Architecture (`ArchitectureTest`)
The rules declared in Specification §2.2: the layer access table,
web ↛ persistence/infrastructure, application ↛ web/infrastructure,
domain independent, config → domain only, no package cycles, controllers
only in `web`.

### Component (`RegistrationPage.test.tsx`)
Fields per type and type switching, options grouped by category, consents
unchecked initially, client-side errors block submission, only fields of
the selected type are sent, confirmation only after a 201 (not while
pending), server field errors shown without confirmation and captcha
reset, general error keeps the entered data, server text rendered as text
(no markup injection), retry when configuration loading fails, no
account/login/payment/edit UI.

### End-to-end (`registration.spec.ts`)
Browser → nginx-equivalent proxy → backend → PostgreSQL/Mailpit: external
registration with Unicode and options shows confirmation and produces the
participant email and the organizer email with one attachment; student
registration; invalid input shows errors and no confirmation; inactive
option not offered; export 401 without credentials; organizer receives an
`.xlsx` (ZIP signature) with credentials.

Not automated (verified manually in Phase 5): data survival across
container recreation (AC-005-02) and production-mode reCAPTCHA widget
rendering against Google.

## 4. Acceptance-criteria coverage

| AC | Tests |
| --- | --- |
| AC-001-01 | Component: external fields by default |
| AC-001-02 | API: external happy path; E2E external |
| AC-001-03 | API: option rows stored; component: option sent; E2E external |
| AC-001-04 | API: `registrationWithoutOptionsIsAccepted`; unit validator |
| AC-001-05 | API: `missingRequiredFieldsAreRejected`; unit validator; component client errors |
| AC-001-06 | Unit: `treatsWhitespaceOnlyValueAsMissing`; API missing fields (whitespace first name) |
| AC-001-07 | Unit: `rejectsInvalidEmail`; API `invalidEmailIsRejected`; E2E invalid input |
| AC-001-08 | Unit: `trimsLeadingAndTrailingWhitespace`; API happy path stores trimmed first name |
| AC-001-09 | Unit validator/export/mail; API happy path Unicode in DB and backup |
| AC-001-10 | Unit: `rejectsUnknownOption`; API `unknownOptionIsRejected` |
| AC-001-11 | Unit: `rejectsInactiveOption`; API `inactiveOptionIsRejected` |
| AC-001-12 | Component: consents not preselected; E2E external |
| AC-001-13 | Unit: `rejectsMissingRequiredConsent`; API `missingMandatoryConsentIsRejected` |
| AC-001-14 | Component: no account/login/payment/edit UI |
| AC-002-01 | Component: student fields after switching type |
| AC-002-02 | API: `studentRegistrationIsAccepted`; E2E student |
| AC-002-03 | API/E2E student with option; component submitted body |
| AC-002-04 | Unit: `reportsEveryMissingStudentField`; API `missingStudentFieldsAreRejected` |
| AC-002-05 | Unit email cases (type-independent); API invalid email |
| AC-002-06 | API: inactive option (student); unit validator |
| AC-002-07 | API: missing consent (student); unit validator |
| AC-002-08 | Unit: type-specific requirements, other-type fields rejected; component body excludes organization |
| AC-002-09 | API/unit: student accepted with any non-empty Student ID, no external check |
| AC-003-01 | API: conference endpoint; component grouped options |
| AC-003-02 | API: inactive not listed; E2E inactive not offered; unit catalog |
| AC-003-03 | Unit: `reflectsChangedConfiguration` |
| AC-003-04 | Unit: `everyOptionHasIdentifierNameAndStatus`, fail-fast rules |
| AC-003-05 | Component: fixed fields per type independent of options; unit catalog |
| AC-004-01 | Component: confirmation after 201 only; E2E |
| AC-004-02 | Component: server errors without confirmation; E2E invalid input |
| AC-004-03 | API: `StorageFailureIntegrationTest`; component general error |
| AC-005-01 | API: DB rows for registration, options, consents |
| AC-005-02 | Phase 5 manual container-recreation check |
| AC-005-03 | API: every rejection asserts empty DB and no backup |
| AC-005-04 | API/unit: JSON backup file written with full data |
| AC-006-01 | API: participant email recipient; unit notifier; E2E Mailpit |
| AC-006-02 | API: no email on every rejection; unit service |
| AC-006-03 | Unit: participant body content |
| AC-007-01 | API: organizer email to all recipients; E2E Mailpit |
| AC-007-02 | API/unit: no email on rejection |
| AC-007-03 | Unit: organizer body and JSON attachment; E2E attachment count |
| AC-008-01 | API: export rows; E2E xlsx download |
| AC-008-02 | API: export grows after new registration |
| AC-008-03 | Unit/API: columns incl. type, fields, consents, options |
| AC-008-04 | API/unit: Unicode in export |
| AC-008-05 | API/unit: header-only empty export |
| AC-008-06 | API: 401 without credentials; E2E |
| AC-008-07 | API: 401 with wrong password or user |

## 5. Security test coverage

Input validation and malicious input (control characters, SQL/HTML-like
payloads, unknown JSON properties, enum abuse), reCAPTCHA enforcement,
rate limiting, request-size limit, security headers, secure error bodies,
access control on export (no data disclosure), formula-injection-safe
export, email subjects without user input, frontend rendering of server
text as text.

## 6. First complete test run (before repairs)

Recorded in `03_Run-Statistics/run-log.json` (`firstTestRun`):
140 tests, 112 passed, 28 failed.

| Suite | Passed | Failed | Cause |
| --- | --- | --- | --- |
| Backend (JUnit) | 73 | 27 | 25 errors: all integration tests, because Testcontainers could not negotiate an API version with Docker Engine 29 (test infrastructure); 1 error: test stubbing defect in `RegistrationServiceTest`; 1 assertion failure: implementation defect, no-break space not trimmed |
| Frontend (Vitest) | 33 | 1 | Implementation defect: test-mode captcha reset applied one render late |
| E2E (Playwright) | 6 | 0 | — |

Fix loops 1–4 in the run log address these. After repair: backend 100/100,
Vitest 34/34, Playwright 6/6.
