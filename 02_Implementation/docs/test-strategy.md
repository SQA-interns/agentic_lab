# Test Strategy

§1–§3 were written in Phase 3 (acceptance tests, before implementation).
§4 onward is added in Phase 5 (unit tests, after implementation).

## 1. Levels and sources

| Level | Location | Written in | Derived from | Runs against |
| --- | --- | --- | --- | --- |
| Backend acceptance (black-box HTTP) | `backend/src/test/java/si/konferenca/registration/acceptance/` | Phase 3, frozen | `docs/acceptance-criteria.md`, `docs/contracts/openapi.yaml`, the two JSON schemas, specification §3 (table names), §9 (property names), §11 (email subjects) | In-process application on a random port (`@SpringBootTest(RANDOM_PORT)`), real HTTP via `java.net.http.HttpClient`, PostgreSQL 16 and Mailpit in Testcontainers, reCAPTCHA in test mode |
| End-to-end UI acceptance | `frontend/e2e/registration.spec.ts` | Phase 3, frozen | Acceptance criteria, specification §12 / §12.1 UI contract, `GET /api/options` | Browser (Playwright/Chromium) against Vite dev server (`/api` proxied to a running backend in test mode) |
| Backend unit / slice | `backend/src/test/java/...` outside `acceptance/` | Phase 5 | Implementation | JUnit 5 + Mockito, MockMvc, ArchUnit |
| Frontend unit | `frontend/src/**/*.test.tsx` | Phase 5 | Implementation | Vitest + React Testing Library (jsdom) |

The acceptance tests use only the published surface: HTTP endpoints,
status codes and bodies from the contract; the database tables named in
the specification (to observe persisted rows, simulate data loss, and
check that nothing was stored); the backup directory and file format
from `registration-backup.schema.json`; the options file format from
`conference-options.schema.json`; the Mailpit HTTP API to observe sent
mail. They reference no production class except the scaffold's
`Application` entry point.

Harness support that is not itself a test but is frozen with the suite
(listed in the manifest): `backend/src/test/resources/docker-java.properties`
(decision D-7) and `frontend/playwright.config.ts` (starts Vite, one
worker).

Test isolation: every test uses a unique email address; the options
file is reset to a known set before every backend test; tests that need
different application settings (unreachable SMTP, low rate limits) run
in their own Spring context.

### Criterion → test map

| Criterion | Backend acceptance test(s) | E2E test |
| --- | --- | --- |
| AC-001-01 | `ExternalRegistration…ac_001_01` | `AC-001-01 / AC-004-01` |
| AC-001-02 | `…ac_001_02` (×4 fields) | — |
| AC-001-03 | `…ac_001_03` (×3 fields + email) | — |
| AC-001-04 | `…ac_001_04` (×5 values) | `AC-004-02` (client-side) |
| AC-001-05 | `…ac_001_05` | — |
| AC-001-06 | `…ac_001_06` | `AC-001-01` (Živa Čepič) |
| AC-001-07 | `…ac_001_07` (missing, false) | — |
| AC-001-08 | — (UI only) | `AC-001-08` |
| AC-001-09 | `…ac_001_09` | `AC-001-09 / AC-003-01` |
| AC-001-10 | `…ac_001_10` | — |
| AC-001-11 | `…ac_001_11` | — |
| AC-001-12 | `…ac_001_12` | — |
| AC-001-13 | `…ac_001_13` (×2) | — |
| AC-002-01 | `StudentRegistration…ac_002_01` | `AC-002-01 / AC-004-01` |
| AC-002-02 | `…ac_002_02` (×6 missing, ×3 blank) | — |
| AC-002-03 | `…ac_002_03` | — |
| AC-002-04 | `…ac_002_04` | — |
| AC-002-05 | `…ac_002_05` | `AC-002-05` |
| AC-002-06 | `…ac_002_06` | — |
| AC-002-07 | `…ac_002_07` (×2) | — |
| AC-002-08 | `…ac_002_08` (×3) | `AC-002-08` |
| AC-003-01 | `ConfigurableOptions…ac_003_01` | `AC-001-09 / AC-003-01` |
| AC-003-02 | `…ac_003_02` | — |
| AC-003-03 | `…ac_003_03` (×2) | — |
| AC-003-04 | `…ac_003_04` | — |
| AC-003-05 | `…ac_003_05` | — |
| AC-004-01 | `Confirmation…ac_004_01` | `AC-001-01 / AC-004-01`, `AC-002-01 / AC-004-01` |
| AC-004-02 | `…ac_004_02` (×2) | `AC-004-02` (×2) |
| AC-004-03 | `…ac_004_03` | `AC-004-03` |
| AC-005-01 | `DurableStorage…ac_005_01` (restart, JSON backup) | — |
| AC-005-02 | `…ac_005_02` (×2) | — |
| AC-005-03 | `…ac_005_03` (restore, auth) | — |
| AC-006-01 | `ParticipantEmail…ac_006_01` | — |
| AC-006-02 | `…ac_006_02` (×3) | — |
| AC-006-03 | `…ac_006_03` | — |
| AC-006-04 | `MailFailure…ac_006_04_and_ac_007_03` | — |
| AC-007-01 | `OrganizerNotification…ac_007_01` | — |
| AC-007-02 | `…ac_007_02` | — |
| AC-007-03 | `MailFailure…ac_006_04_and_ac_007_03` | — |
| AC-008-01 | `Export…ac_008_01` | — |
| AC-008-02 | `…ac_008_02` (×2) | — |
| AC-008-03 | `…ac_008_03` | — |
| AC-008-04 | `…ac_008_04` | — |
| AC-008-05 | `…ac_008_05` | — |
| AC-008-06 | `…ac_008_06` (×2) | — |

Contract/security tests not tied to one criterion:
`ApiContractAcceptanceTest` (client config, malformed JSON, unknown
property, invalid type, 413, 415, too long, control characters, missing
reCAPTCHA token, API security headers, health) and
`RateLimitAcceptanceTest` (registration and organizer 429).

## 2. How to run

Backend acceptance suite (needs Docker):

```bash
cd backend && ./mvnw test -Dtest='*AcceptanceTest'
```

E2E suite: start PostgreSQL and Mailpit (`docker compose up -d postgres smtp`),
start the backend in test mode with an options file and organizer
settings (see `README`/release notes for the exact environment), then:

```bash
cd frontend && npx playwright test
```

Playwright starts the Vite dev server itself (`playwright.config.ts`).

## 3. Red run — before any production code (Phase 3)

Executed 2026-09-28 against the frozen scaffold only (no production
behaviour). Every failure was checked to be behavioural, not
mechanical.

### 3.1 Mechanical problems found and fixed in the harness before the red run was accepted

1. Every backend test errored in class initialisation:
   `client version 1.32 is too old` — the Spring-Boot-managed
   Testcontainers could not talk to Docker Engine 29. Fixed by
   configuration only (`docker-java.properties`, `api.version=1.44`);
   escalated and recorded as D-7.
2. POI (needed by the tests to read the `.xlsx` export) was not on the
   test classpath → added `poi-ooxml` 5.5.1 (test scope).
3. The two classes that override settings (unreachable SMTP, low rate
   limits) did not actually get their overrides — the superclass
   `@DynamicPropertySource` wins in Spring. Found by the harness
   self-checks `harnessPointsTheApplicationAtAClosedSmtpPort` and
   `harnessAppliesTheLowLimits`; fixed by splitting the harness
   (`AcceptanceHarness` with `registerProperties(registry, overrides)`,
   `AcceptanceTestBase` for the default configuration).

### 3.2 Backend acceptance red run (final, after the harness fixes)

`./mvnw test -Dtest='*AcceptanceTest'` → **90 tests: 3 passed, 82
failures, 5 errors.**

| Group | Count | One-line reason |
| --- | --- | --- |
| `POST /api/registrations` expectations (201/400/413/415/429/500) | 69 | `404 Not Found` — registration endpoint does not exist yet |
| `GET /api/options` expectations | 6 | `404 Not Found` — options endpoint does not exist yet |
| `GET /api/config` | 1 | `404 Not Found` — config endpoint does not exist yet |
| Organizer export / restore auth expectations (200/401/429) | 5 | `404 Not Found` — organizer endpoints do not exist yet |
| Export workbook parsing | 4 | response is a 404 JSON body, not an `.xlsx` (`NotOfficeXmlFile`) |
| Export with no registrations | 1 | `relation "registration_option" does not exist` — schema not created yet |
| API security headers | 1 | `X-Content-Type-Options` absent — security configuration not built yet |
| **Passed:** `healthEndpointIsUp` | 1 | Legitimately green: the frozen scaffold already includes Actuator health with probes |
| **Passed:** `harnessPointsTheApplicationAtAClosedSmtpPort`, `harnessAppliesTheLowLimits` | 2 | Harness self-checks (they verify the test configuration, not the product) |

Per-test reasons: `docs/acceptance/red-run-backend.txt`.

### 3.3 E2E red run

`npx playwright test` (Vite dev server with the scaffold `App`, no
backend) → **9 tests: 0 passed, 9 failed.**

| Test | Reason |
| --- | --- |
| AC-001-08, AC-002-05, AC-002-08, AC-002-01/AC-004-01, AC-004-02 (×2), AC-004-03 | `locator.check` timed out — the registration form (type radios) does not exist yet |
| AC-001-09/AC-003-01, AC-001-01/AC-004-01 | `GET /api/options` did not return 200 — backend endpoint not available yet |

### 3.4 Freeze

All acceptance-test files and the two harness-support files are hashed
in `docs/acceptance/MANIFEST.sha256` (sha256sum format, paths relative
to `02_Implementation/`) and committed with the suite. From that commit
they are frozen (CONSTITUTION §2).

## 4. Unit, component and additional integration tests (Phase 5)

Written after the implementation, from the specification and the code.
Additive only: no file listed in `docs/acceptance/MANIFEST.sha256` was
changed (hashes re-checked after the phase).

### 4.1 Backend (`backend/src/test/java`, outside `acceptance/`)

| Test class | Level | What it covers and why |
| --- | --- | --- |
| `service.RegistrationValidatorTest` | unit | Every §6 rule at the edges the black-box suite cannot cheaply enumerate: code-point length limits (emoji), Cc/Cf rejection (NUL, ZWSP, TAB, NEL), Unicode whitespace stripping (NBSP, U+2028), email corner cases, type handling, duplicate/null option ids with indices, >50 options, error aggregation. |
| `service.RegistrationServiceTest` | unit (Mockito) | Write-path ordering and failure semantics of §10: reCAPTCHA checked before validation/storage, nothing stored on invalid input, backup failure → rollback + backup removal, **commit failure → the already-written backup is deleted** (not reachable black-box), mail failures never undo the registration (D-3), attachment bytes = backup bytes. |
| `service.OptionCatalogServiceTest` | unit (Mockito) | §4 sync: insert/update/deactivate-unlisted, startup failure on invalid file, no re-read without a change, invalid or unreadable file at runtime keeps the previous catalog and is not retried until the next change. |
| `service.OrganizerServiceTest` | unit (Mockito) | Restore accounting (restored / alreadyPresent / failed), options missing from the catalog recreated **inactive**, per-entry failure isolation; empty export is a valid workbook. |
| `integration.OptionsFileReaderTest` | unit | Options-file schema enforcement (14 invalid shapes, duplicate ids, 200-code-point name limit, missing file), change stamp. |
| `integration.JsonBackupStoreTest` | unit | Atomic write via temp file, exact bytes, round trip, µs timestamps, unreadable/mismatched/wrong-version files reported as failed, `deleteQuietly` never throws. |
| `integration.RecaptchaVerifierTest` | unit + local HTTP stub | Test-mode determinism; production mode posts `secret`/`response`/`remoteip` form-encoded and accepts only `success: true`; fails closed on HTTP 500, invalid JSON, unreachable provider, blank or oversized tokens. |
| `integration.MailNotifierTest` | unit (mocked sender, real MimeMessage) | Plain-text UTF-8 bodies, fixed subjects, organizer recipients, JSON attachment name and bytes, "no options" text. |
| `integration.ExcelExportWriterTest` | unit | Header-only workbook, per-category option columns in display order, formula-like text stays a string cell. |
| `config.RateLimitFilterTest` | unit (mock servlet) | Per-IP and per-bucket budgets, window reset, `Retry-After` countdown, bounded key map. |
| `config.RequestSizeLimitFilterTest` | unit (mock servlet) | Declared and undeclared (chunked) oversize bodies → 413 without passing the request on; in-limit body replayed intact; non-API paths untouched. |
| `ArchitectureTest` | ArchUnit | Exactly the seven rules declared in specification §2.1 (no generic layering rules). |
| `security.RecaptchaProductionModeTest` | integration (Spring Boot + Testcontainers PostgreSQL + local siteverify stub) | **Production-mode reCAPTCHA exercised end to end against a mocked verification endpoint** (HUMAN_INPUTS_MANIFEST row 1, DoD §3): provider-accepted token registers, rejected token and the test-mode token are refused; startup refuses production mode without keys, production mode is the default, short organizer password and missing recipients refuse to start. |
| `security.HttpSecurityTest` | integration | Restore requires JSON (cross-site form POST → 415), non-empty restore body → 400, unknown path → problem without internals, only health/info actuator endpoints, 413 responses carry security headers, no session cookie for organizers, no CORS allowance, validation errors never echo input. |

`support.PostgresIntegrationTest` is a small Phase 5 base class (its own
PostgreSQL container, SMTP pointed at a closed port) so these tests do
not depend on the frozen harness's package-private members.

### 4.2 Frontend (`frontend/src/**/*.test.ts(x)`, Vitest + React Testing Library)

| Test file | What it covers |
| --- | --- |
| `validation/validate.test.ts` | Client-side mirror of §6: per-type fields, whitespace, code-point limits, Cc/Cf characters, email rules, consent and robot check, server-code message mapping. |
| `api/client.test.ts` | Result mapping of `POST /api/registrations` (201 / 400 with errors / 400 without / 429 / 500 / network error), request body, non-OK GET. |
| `components/RegistrationPage.test.tsx` | Grouped options, empty groups hidden, unchecked consent, type switching, no request when client validation fails, trimmed type-specific request body, confirmation, server field-error mapping, reCAPTCHA reset after `RECAPTCHA_FAILED`, option errors shown generally with options reloaded, general failure, load failure. |
| `components/Captcha.test.tsx` | Test mode yields `test-pass` and loads no Google script; production mode renders the widget with the site key, wires token/expiry callbacks, resets on demand, injects the explicit-render script. |

`vitest.config.ts` restricts Vitest to `src/**/*.test.*` so the
Playwright specs in `e2e/` are not picked up.

## 5. First complete run (Phase 5, before any repair)

Run 2026-09-29T00:18Z: `./mvnw test` (acceptance + unit), `npx vitest run`,
`npx playwright test` (against the compose backend in test mode).

| Suite | Passed | Failed |
| --- | --- | --- |
| Backend (90 acceptance + 117 unit/integration) | 206 | 1 |
| Frontend unit (Vitest) | 39 | 0 |
| E2E acceptance (Playwright) | 9 | 0 |
| **Total** | **254** | **1** |

### Failure classification

| Test | Classification | Reason and action |
| --- | --- | --- |
| `RegistrationValidatorTest.acceptsValidExternalAndNormalizesWhitespace` | **Implementation defect** | A leading no-break space (U+00A0) was kept: `String.strip()` — named in the specification — deliberately does not treat no-break spaces as whitespace, although FORM_SCHEMA says leading/trailing whitespace is not significant and the frontend's `trim()` removes it (client and server disagreed). Fixed `RegistrationValidator.normalize` to strip `isWhitespace || isSpaceChar` code points; specification §6 wording corrected. The test was not changed. |

After the first run, test sources were only reformatted (Prettier and
Spotless) and two string literals were rewritten from literal invisible
characters (ZWSP, NUL, NBSP, NEL, U+2028 — the editor had materialised
the escapes) back into `\uXXXX` escapes; the tested values are identical.

## 6. Final complete run (end of Phase 5)

| Suite | Passed | Failed |
| --- | --- | --- |
| Backend (acceptance + unit/integration) | 207 | 0 |
| Frontend unit (Vitest) | 39 | 0 |
| E2E acceptance (Playwright) | 9 | 0 |
| **Total** | **255** | **0** |

Acceptance manifest re-checked: all 18 hashes match.

## 7. Verification additions (Phase 6)

Additive only; frozen files untouched (18/18 hashes still match).

- `config.OrganizerTransportFilterTest` (unit) — HTTPS-only organizer
  transport added in fix loop 2: remote plain HTTP → 403, HTTPS and
  loopback allowed, non-literal addresses not exempt, other paths
  unaffected, switchable.
- Strengthened after the first mutation run (DoD §4a): option-count
  boundary (50 allowed), per-field `FIELD_NOT_ALLOWED`, 4096-character
  token boundary, rate-limiter pass-through assertions and eviction/clear
  behaviour, catalog refresh through `catalog()`.
- Mutation testing tooling: PIT (`pitest-maven` 1.30.0 +
  `pitest-junit5-plugin` 1.2.3, not bound to the build lifecycle) and
  Stryker (`@stryker-mutator/core` + `vitest-runner` 10.0.0, dev
  dependencies, `npm run test:mutation`).

## 8. Final complete run (end of Phase 6)

| Suite | Passed | Failed |
| --- | --- | --- |
| Backend: acceptance 90, unit 112, ArchUnit 9, integration 16 | 227 | 0 |
| Frontend unit (Vitest) | 39 | 0 |
| E2E acceptance (Playwright) | 9 | 0 |
| **Total** | **275** | **0** |

Coverage and mutation results: `docs/verification-report.md` §2.2, §2.5.
