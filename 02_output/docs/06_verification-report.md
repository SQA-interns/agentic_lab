# Verification report

> Written in: phase 6 · Source: `general/quality/*`, `general/security/*`, `project/04_security/*`, `project/05_quality/*` · Procedure: `general/skills/verify-release`, `general/skills/security-review` · Agent: writes

This is a self-check by the development agent, not an independent review.

Run `conference-single-sdd-001`, branch `run/single-sdd/conference-001`, 2026-09-30. Raw evidence is in `out/logs/`; logs referenced below are relative to `02_output/`.

## Definition of Done

| DoD | Result | Evidence |
|---|---|---|
| DoD-01 Full suite passes; frozen hashes match | PASS | Final run (below): backend 165 unit + 114 acceptance/integration, frontend 41 unit, 19 e2e, 0 failures (`logs/phase6-final-run2-*.log`). `tools/manifests.sh verify`: acceptance manifest 37/37 and input manifest 32/32 match, no unlisted files under frozen paths. One manifest line (Infra.java) was changed with human approval (D-22). |
| DoD-02 Format, lint, type check, static analysis clean | PASS | `./mvnw -Pquality verify` (Spotless, PMD 7.7.0, CPD, SpotBugs 4.9.3, javac `-Xlint:all -Werror`) and `npm run check` (Prettier, ESLint strict-type-checked, tsc) exit 0 (`logs/phase6-final-run2-backend.log`, `logs/phase6-final-run2-frontend-check.log`); semgrep: F-01…F-06 below |
| DoD-03 Coverage and mutation recorded (thresholds: record only) | PASS | Backend JaCoCo unit: line 83.2%, branch 88.6%; integration + acceptance: line 85.4%, branch 67.9% (`logs/coverage/*.csv`). Frontend Vitest v8: statements/lines 100%, branches 98.0%. Mutation: PIT 84% (397/472, test strength 93%); Stryker 79.6% (validation 90.6%, api 86.4%). Every survivor classified in `logs/phase6-mutant-classification.md` (F-10) |
| DoD-04 Architecture constraints checked automatically; no cycles | PASS | `ArchitectureTest` (ArchUnit 1.4.1): no slice cycles; platform → no feature module; options/notifications → platform only; registration ↛ export (AR-02); D-11 unused-feature rules. AR-03 (`ddl-auto=validate` + Flyway V1), AR-06/AR-08/AR-09 by acceptance tests and inspection |
| DoD-05 No open Critical/High | PASS | F-01 (High) closed by D-24 (approved); F-02 (Critical/High dependency CVEs) closed by D-11 (approved), enforced by `ArchitectureTest.vulnerableFrameworkFeaturesAreNotUsed`/`featureStacksAreNotOnTheClasspath`, re-scan shows the identical set |
| DoD-06 Runs in target environment; core flows at runtime | PASS | Compose stack (`logs/phase6-runtime-demo.log`, script `tools/runtime-demo.sh`) and 19 browser e2e tests against it |
| DoD-07 Every AC traces to ≥1 test and ≥1 commit | PASS | Traceability table below: 43/43 ACs |
| DoD-08 READMEs work from a clean checkout | PASS | Fresh `git clone` of the branch into an empty directory, app images and volumes removed first; root README quick start (`.env` from the secrets example, `tools/hash-password.sh`, `docker compose up -d --build`, `tools/runtime-demo.sh`) and every component README command (backend build/check/test: 165 + 114 pass; frontend `npm ci`, build, check, test 41, contracts, e2e 19) succeeded (`logs/phase7-clean-checkout.log`, `logs/phase7-clean-checkout-components.log`). The main application README is `02_output/README.md` (Overrides: ES-06, DoD-08) |
| DoD-09 Release notes list manual tests | PASS | `docs/release-notes.md` "Must be tested manually by a human" (8 areas: TLS/proxy, reCAPTCHA, SMTP, export in the browser, screen reader, backup/restore, legal/privacy, load) |
| DoD-10 Decisions resolved or pending review; inputs unchanged | PASS | D-01…D-24: every blocking decision approved; non-blocking ones "pending review". Input manifest 32/32 match |
| DoD-11 Workflow evidence (SDD) | PASS | Phase 3 commit `d714ce8` contains the manifest and all 37 listed files; production code at `d714ce8` is the skeleton main class plus configuration only (D-21). Hashes match (see DoD-01). Inventory: 46/46 frozen acceptance methods (109 cases) and 19/19 e2e executed, 0 skipped, no `@Disabled`/`skip`/`only` markers (`logs/phase6-frozen-test-inventory.txt`) |
| DoD-P01 Both forms: Unicode/NBSP; each required field, email, captcha rejection; direct unknown/inactive option rejection | PASS | AC-001-02…06 and AC-002-02…06 acceptance tests (parameterised per field, including NBSP, U+2007, U+202F); e2e keyboard submission with Unicode; after each rejection, `Checks.assertNothingStored` confirms no DB row, JSON file, outbox row or mail |
| DoD-P02 Catalog restart; unchecked mandatory synthetic consent; absent rejected, given accepted | PASS | AC-003-02 (restart with edited file), AC-001-07/AC-002-07, e2e "unchecked consent", runtime demo catalog change |
| DoD-P03 Persistence/duplicate/SMTP failures recover; authorized Excel covers both forms; unauthorized reveals none | PASS | AC-005-02/03/04/05, AC-004-03/04, AC-006-02/AC-007-02 (real Mailpit on the recovered port), AC-008-01…05 (parsed workbook), runtime demo (outbox PENDING → SENT after Mailpit restart) |
| DoD-P04 Contracts validate semantically; protected helpers/config and executed cases cannot bypass frozen tests | PASS | `npm run contracts` (OpenAPI validity, examples, schema cross-checks); e2e `api-contract.spec.ts` validates live responses against the schemas; acceptance support classes, test resources and `playwright.config.ts` are in the manifest; inventory as in DoD-11 |
| DoD-P05 Scoped commits with AC/check IDs; history preserved | PASS | Scoped commits per phase document set, feature slice and fix (`git log 52eb3ed..run/single-sdd/conference-001`), none rewritten; fixes carry F/D IDs, features carry AC IDs |
| DoD-P06 Guides distinguish local substitutes from real TLS/captcha/SMTP | PASS | `README.md` "Local substitutes vs production"; backend README configuration table; release notes manual checklist; clean-checkout run |

## Security review (SB / SR)

Standard: OWASP ASVS 5.0.0, Level 2 applicable controls (control-based self-check, not certification; mapping in the specification section 13).

| Control | Implemented | Checked by |
|---|---|---|
| SB-01 server-side validation | `RegistrationValidator`, `CatalogLoader` | AC-001/002 acceptance, `RegistrationValidatorTest`, `EdgeCasesTest` |
| SB-02 authN/authZ for non-public operations | `SecurityConfig`: only `/api/organizer/**` requires `ROLE_ORGANIZER`; actuator other than health denied | AC-008-02, `ApiHardeningIT.onlyHealthProbesAreExposed` |
| SB-03 salted slow hashes; secrets from config | `{bcrypt}` cost ≥ 10 or `{pbkdf2}` enforced at startup; secrets only from the environment | `GuardsTest`, `PlatformEdgeTest.organizerBeanRejectsWeakHashes`, inspection of `application.yml` (no defaults for secrets) |
| SB-04 TLS outside localhost | External proxy (production); SMTP TLS mandatory in production (`MailConfig`) | `NotificationDispatcherTest.mailSettingsAreValidated`; **TLS termination is a manual production check** |
| SB-05 output encoding; parameterised queries | JPA/Spring Data only; mail `text/plain`; React escaping; string-only xlsx cells | AC-006-03, AC-008-03, semgrep (no injection findings) |
| SB-06 rate limits | `RequestGuardFilter` + `TokenBucketLimiter` (registration 20/min default, export 10/min) | AC-001-10, AC-008-05, `TokenBucketLimiterTest`, `RequestGuardFilterTest` |
| SB-07 no internals in errors/logs | `ProblemHandler` generic problems; logs contain IDs and error classes only | `ApiHardeningIT`, AC-005-02/03 body assertions, log scan (`logs/phase5-gate-backend.log`, `logs/phase6-compose-app.log`: 0 emails/names/hashes/tokens) |
| SB-08 dependency scan | dependency-check 12.1.3, npm audit | F-02, F-07, F-08 |
| SB-09 SAST + secret scan | semgrep 1.120.0, gitleaks 8.24.3 | F-01, F-03…F-06, F-11 |
| SB-10 security headers | backend: CSP `default-src 'none'`, DENY, nosniff, no-referrer, no-store; nginx: SPA CSP | `ApiHardeningIT.securityHeadersArePresent`, runtime demo (after F-09 one header each) |
| SB-11 least privilege | backend UID 10001, frontend `nginx` (UID 101), non-root ports | `docker compose exec … id` (UID 10001 and 101) |
| SB-12 data minimisation | only the business-rules "Data" fields | contracts (`additionalProperties: false`), `UNKNOWN_FIELD` rejection |
| SB-13 purpose/retention | synthetic data until `docker compose down -v` (README, spec 14) | inspection; the real policy is open (OQ-03, blocks production) |
| SB-14 explicit consent, never preselected | checkbox unchecked, server enforces | e2e AC-001-09/AC-002-08, AC-001-07/AC-002-07 |
| SR-01 server captcha; stub impossible in production; fail closed | `RegistrationConfig.stub` guard, `RecaptchaVerifier` fail-closed | `RegistrationServiceTest.captchaGuardsFollowTheProfile`, `RecaptchaVerifierTest` (local fake endpoint); **real Google verification is a manual check** |
| SR-02 generated paths; JSON not web-reachable | UUID file names; JSON volume mounted only into the backend; nginx serves only `dist` | `JsonStoreTest`, compose inspection, runtime demo (`/actuator` and other paths via the proxy return the SPA, not files) |
| SR-03 size limits and rate limits; Unicode usable | 16 KiB body, per-field code-point limits, ≤ 50 options/group | AC-001-08, AC-001-04/AC-002-04, `RequestGuardFilterTest` |
| SR-04 formula-safe Excel; safe mail; no header/recipient override | quote-prefixed cells; plain-text mail; control characters rejected; fixed subjects | AC-008-03, AC-006-03, `WorkbookWriterTest` |
| SR-05 isolated SMTP; synthetic identities only | Mailpit on 127.0.0.1; `example.test` addresses; production consent/retention open | compose inspection; release notes |
| SR-06 keep raw scanner findings | raw reports unchanged in `logs/phase0-*`, `logs/phase6-*` | F-01/F-02 downgrades only through D-24/D-11 |

## Runtime demonstration

Local Compose stack (`agenticlab`: PostgreSQL 16.14, Mailpit v1.24.1, backend, frontend/nginx), `logs/phase6-runtime-demo.log`.

| Component | Environment | Flows exercised | Result |
|---|---|---|---|
| frontend + backend | local Compose, http://127.0.0.1:18080 | 19 Playwright tests in Chromium: both forms by mouse and keyboard only, visible focus, field errors, confirmation only after acceptance, retry with the same request ID, organizer page | PASS |
| backend via proxy | local Compose | catalog; external and student registration (NBSP and Slovenian names, formula-like organization); replay → 200 same ID; changed content → 409; invalid option/consent/captcha → 400; foreign Origin → 403 | PASS |
| backend + Mailpit | local Compose | participant mail and organizer mail with `registration-<id>.json`; rejected submission → 0 mails | PASS |
| backend + JSON volume | local Compose | file `/data/registrations/registrations/<id>.json` exists; the process runs as UID 10001 | PASS |
| backend export | local Compose | 401 without or with wrong credentials; 200 with the organizer's credentials; 23 rows | PASS |
| persistence | `docker compose restart backend`, then `down` + `up` with retained volumes | 23 rows before and after; JSON file still present | PASS |
| SMTP outage | `docker compose stop mailpit` → register → `start mailpit` | 201 while down; outbox PENDING (2 attempts) → SENT (3rd attempt); both mails and attachment delivered | PASS |
| catalog change | `CONFERENCE_CONFIG_FILE=<edited copy> docker compose up -d backend` | new option visible, deactivated option rejected, restored afterwards; no rebuild | PASS |

## Traceability

A = backend acceptance test methods (`backend/src/test/java/lab/conference/acceptance/*`), E = e2e spec files (`frontend/e2e/*`). Commits: `d714ce8` frozen tests; `f719144` backend; `3bcf553` frontend; `2025fe0` D-22 harness fix; `9a1a315` rate-limit fix; `f390f47` added unit tests.

| AC | Tests (A: class (test methods); E: spec files) | Commits |
|---|---|---|
| AC-001-01 | A: ExternalRegistration (2); E: api-contract.spec.ts, external-form.spec.ts | d714ce8 (tests), f719144 |
| AC-001-02 | A: ExternalRegistration (1) | d714ce8 (tests), f719144 |
| AC-001-03 | A: ExternalRegistration (1); E: external-form.spec.ts | d714ce8 (tests), f719144 |
| AC-001-04 | A: ExternalRegistration (1); E: external-form.spec.ts | d714ce8 (tests), f719144 |
| AC-001-05 | A: ExternalRegistration (1) | d714ce8 (tests), f719144 |
| AC-001-06 | A: ExternalRegistration (1) | d714ce8 (tests), f719144 |
| AC-001-07 | A: ExternalRegistration (2); E: external-form.spec.ts | d714ce8 (tests), f719144 |
| AC-001-08 | A: ExternalRegistration (3) | d714ce8 (tests), f719144 |
| AC-001-09 | E: external-form.spec.ts | d714ce8 (tests), 3bcf553 |
| AC-001-10 | A: RateLimit (1) | d714ce8 (tests), f719144, 9a1a315 |
| AC-002-01 | A: StudentRegistration (1); E: student-form.spec.ts | d714ce8 (tests), f719144 |
| AC-002-02 | A: StudentRegistration (1); E: api-contract.spec.ts | d714ce8 (tests), f719144 |
| AC-002-03 | A: StudentRegistration (1) | d714ce8 (tests), f719144 |
| AC-002-04 | A: StudentRegistration (1); E: student-form.spec.ts | d714ce8 (tests), f719144 |
| AC-002-05 | A: StudentRegistration (2) | d714ce8 (tests), f719144 |
| AC-002-06 | A: StudentRegistration (1); E: student-form.spec.ts | d714ce8 (tests), f719144 |
| AC-002-07 | A: StudentRegistration (1) | d714ce8 (tests), f719144 |
| AC-002-08 | E: student-form.spec.ts | d714ce8 (tests), 3bcf553 |
| AC-003-01 | A: Catalog (1); E: api-contract.spec.ts, external-form.spec.ts | d714ce8 (tests), f719144, 3bcf553 |
| AC-003-02 | A: Catalog (1) | d714ce8 (tests), f719144 |
| AC-003-03 | A: Catalog (2) | d714ce8 (tests), f719144 |
| AC-003-04 | A: Catalog (1) | d714ce8 (tests), f719144 |
| AC-004-01 | E: confirmation.spec.ts, external-form.spec.ts, student-form.spec.ts | d714ce8 (tests), 3bcf553 |
| AC-004-02 | E: confirmation.spec.ts | d714ce8 (tests), 3bcf553 |
| AC-004-03 | A: Idempotency (2); E: api-contract.spec.ts | d714ce8 (tests), f719144, f390f47 |
| AC-004-04 | A: Idempotency (1) | d714ce8 (tests), f719144 |
| AC-004-05 | A: Idempotency (1) | d714ce8 (tests), f719144 |
| AC-005-01 | A: DurableStorage (1) | d714ce8 (tests), f719144 |
| AC-005-02 | A: DurableStorage (1) | d714ce8 (tests), f719144 |
| AC-005-03 | A: DatabaseOutage (1) | d714ce8 (tests), f719144 |
| AC-005-04 | A: DurableStorage (1) | d714ce8 (tests), f719144 |
| AC-005-05 | A: DurableStorage (1) | d714ce8 (tests), f719144 |
| AC-006-01 | A: Notification (2); E: notifications.spec.ts | d714ce8 (tests), f719144 |
| AC-006-02 | A: SmtpOutage (1) | d714ce8 (tests), f719144, 2025fe0 |
| AC-006-03 | A: Notification (2) | d714ce8 (tests), f719144 |
| AC-007-01 | A: Notification (1); E: notifications.spec.ts | d714ce8 (tests), f719144 |
| AC-007-02 | A: SmtpOutage (1) | d714ce8 (tests), f719144, 2025fe0 |
| AC-008-01 | A: Export (1) | d714ce8 (tests), f719144 |
| AC-008-02 | A: Export (1); E: organizer-export.spec.ts | d714ce8 (tests), f719144 |
| AC-008-03 | A: Export (1) | d714ce8 (tests), f719144 |
| AC-008-04 | A: Export (1) | d714ce8 (tests), f719144 |
| AC-008-05 | A: RateLimit (1) | d714ce8 (tests), f719144, 9a1a315 |
| AC-008-06 | E: organizer-export.spec.ts | d714ce8 (tests), 3bcf553 |

## Findings

Severities as found (general severity scale; tool mappings as in `severity-scale.md`).

| F | Severity | Source | Finding | Resolution |
|---|---|---|---|---|
| F-01 | High → Low (D-24, approved) | semgrep `detected-bcrypt-hash` (ERROR) | bcrypt hash in `02_output/.env`, the git-ignored local secret store | Accepted as a false positive for shipped source: never committed on any branch, gitleaks clean; raw report unchanged |
| F-02 | Critical → Low (D-11, approved) | dependency-check 12.1.3 | 14 Critical / 17 High CVE matches in spring-core/web 6.2.19, spring-security 6.5.11 and pgjdbc 42.7.11 (the same set as phase 0; Tomcat fixed by the 10.1.60 override) | Not reachable (features unused); enforced by ArchUnit; re-scan unchanged. Production must upgrade once patched OSS versions exist (release notes) |
| F-03 | Low | gitleaks | Spring's generated skeleton passwords in the committed phase 3 log | Fixed forward `d2483f8`; the ephemeral values stay in history (no rewrites) |
| F-04 | Low | gitleaks `curl-auth-user` | Literal deliberately-wrong password in `tools/runtime-demo.sh` | Fixed `d454f77`; tracked-file re-scan: no leaks |
| F-05 | Medium | semgrep `npm-missing-minimum-release-age` | `.npmrc` sets no minimum release age | Accepted: the setting needs npm ≥ 11.10, and npm 10.9.9 is pinned; mitigated by exact pins, the committed lockfile with integrity hashes, and npm audit |
| F-06 | Low | semgrep parser | `mvnw` (bash arithmetic) and `openapi.yaml` (non-ASCII example) not parsed | Accepted: scanner coverage gap; OpenAPI validated by swagger-parser and PyYAML; `mvnw` is the checksum-verified upstream script |
| F-07 | Medium | dependency-check | Medium: commons-lang3 CVE-2025-48924, jackson-databind CVE-2026-54515, log4j-api CVE-2026-34477/-49844/-34479, spring CVE-2026-47883/-47887/-59281/-59280/-59314, spring-data-jpa CVE-2026-47834 (Sort), spring-security CVE-2026-47842/-59276 | Accepted with reasons: features unused (UrlHandlerFilter, UrlFileNameViewController, FreeMarker, Sort/Pageable, AesBytesEncryptor, Log4j layouts), or no fixed version within the pinned Boot BOM; open item for the production upgrade |
| F-08 | Medium | npm audit | 6 Moderate / 4 Low in dev-only tooling (vitest mocker, stryker → ajv/@babel, eslint plugin-kit) | Accepted: not shipped (`npm audit --omit=dev`: 0) |
| F-09 | Low | runtime demo | API responses through nginx carried two CSP headers and duplicate nosniff | Fixed `0ea751e`; re-verified: one header each |
| F-10 | Low | PIT / Stryker | Surviving mutants in security/validation/persistence code (68 survivors + NO_COVERAGE at first) | Tests added (`f390f47`): PIT 72% → 84%; every remaining survivor classified individually (`logs/phase6-mutant-classification.md`); none indicates a defect |
| F-11 | Low | gitleaks (full history) | 11 `generic-api-key` hits in commits made before this run (paths of another project) | Open item for the repository owner; out of run scope; no history rewrite |
| F-13 | Low | agent self-check (phase 7) | The traced clean-checkout log committed in `a22ab2c` recorded the throwaway organizer password and hash of the temporary clone stack (ES-02/ES-07) | Fixed forward (log redacted); the clone stack and its volumes were destroyed before the commit, so the credential is dead; history is not rewritten |
| F-12 | Low | preflight | Docker Engine/Compose run on 29.3.1/v5.1.1, not the pinned 28.1.1/2.35.1 | Accepted deviation D-03 (approved); Compose file uses only features available in 2.35.1 |

## Fix loops

| Loop | Findings | Change | Re-verification |
|---|---|---|---|
| 1 (phase 5) | first-run implementation defect (Retry-After rounding) | `9a1a315` | full suite green (`logs/phase5-gate-backend.log`) |
| 2 | F-09 | nginx header inheritance (`0ea751e`) | `curl -I` via proxy: one CSP/nosniff; 19 e2e pass |
| 3 | F-03, F-04 | log redaction, script variable (`d2483f8`, `d454f77`) | gitleaks over tracked files: no leaks |
| 4 | F-10 | 22 unit tests added (`f390f47`) | PIT rerun 84%, test strength 93% |
| 5 | F-01 (High) | none possible (intended secret store); blocking decision D-24 | approved; raw report kept |
| 6 | F-02 (Critical/High) | Tomcat override in phase 0; ArchUnit feature guard | dependency-check re-scan: the approved set only |
| 7 | final-run check failure: Prettier flagged the new `frontend/README.md` | formatted | `npm run check` exit 0 |
| 8 | F-13 | redacted `logs/phase7-clean-checkout.log` | grep for credential patterns in `logs/`: none |

## Measures

| Measure | Backend | Frontend |
|---|---|---|
| Tests (final run) | 165 unit, 109 acceptance, 5 integration | 41 unit, 19 e2e |
| Line / branch coverage, unit | 83.2% / 88.6% | 100% / 98.0% |
| Line / branch coverage, integration + acceptance | 85.4% / 67.9% | e2e (not instrumented) |
| Mutation score | 84% (PIT, all production packages) | 79.6% (Stryker: validation, api, form, captcha) |
| Code size (cloc, code lines) | 2 680 production / 4 421 test | 698 production / 1 124 test |
| Complexity | cyclomatic 505 total over 287 methods (avg 1.76) | max function complexity 11 |
| Duplication | CPD: 0 blocks ≥ 100 tokens | jscpd over backend + frontend sources: 0% (≥ 50 tokens) |

## Decisions

All decision records have a resolution: blocking D-01…D-08, D-11, D-22 and D-24 approved by the human; non-blocking D-09, D-10, D-12…D-21 and D-23 "pending review" (listed in the release notes).
