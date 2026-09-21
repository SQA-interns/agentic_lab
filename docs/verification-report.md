# Verification Report

**Phase 5 artefact.** Independent self-verification of the implementation against
`USER_STORIES.md`, `PROJECT_CONSTRAINTS.md`, `FORM_SCHEMA.md`, `docs/acceptance-criteria.md`
and `docs/specification.md`.

* Verification started: **2026-09-21T11:17:31Z**
* Verification completed: **2026-09-21T11:28:18Z**
* Fix loops: **1** (Verification → Fix → Verification)
* Final status: **PASS**, with three criteria recorded as *not verifiable in this environment*
  (§ 6).

---

## 1. Method

All User Stories, Acceptance Criteria, the specification and the project constraints were
re-read before inspecting the implementation. The following were then executed or performed:

| Check | Tool | Result (final) |
| --- | --- | --- |
| Linting, backend | ESLint 9 + typescript-eslint (type-checked rules) | 0 errors, 0 warnings |
| Linting, frontend | ESLint 9 + typescript-eslint (type-checked rules) | 0 errors, 0 warnings |
| Type checking, backend (src + tests) | `tsc --noEmit`, strict | 0 errors |
| Type checking, frontend (src + tests) | `tsc --noEmit`, strict | 0 errors |
| Type checking, e2e | `tsc --noEmit`, strict | 0 errors |
| Build, backend | `tsc -p tsconfig.build.json` | success |
| Build, frontend | `vite build` | success |
| Unit/component/integration/API/acceptance/security tests | Vitest 5 | 277 passed, 0 failed |
| Frontend tests | Vitest 5 (jsdom) | 42 passed, 0 failed |
| End-to-end tests | Playwright, Chromium, 1280×900 and 375×812 | 28 passed, 0 failed |
| Coverage, backend | Vitest v8 | 95.21% statements, 82.82% branches, 96.02% functions |
| Dependency advisories | `npm audit` | 0 vulnerabilities |
| Architecture conformance | dependency-cruiser, 6 rules | 0 violations, 34 modules, 87 dependencies |
| Code duplication | jscpd (≥5 lines, ≥50 tokens) | production code 0.00% |
| Cyclomatic complexity | ESLint `complexity` rule, reported per function | 211 functions, average 2.41, max 12 |
| Acceptance-criteria traceability | manual review + mechanical AC-id cross-check | 84 of 87 verified automatically |
| Security review | manual, plus the automated security suite | see § 5 |
| Missing-requirement review | manual | see § 4 |
| Unrequested-functionality review | manual | see § 4 |
| Regression review | full suite re-run after every fix | no regressions |

---

## 2. Findings

Severity is assigned as: **Critical** — an Acceptance Criterion is not met, data can be
lost, or a security control is absent. **Major** — a specification or architecture rule is
broken, or a requirement is only partly met. **Minor** — quality, traceability or
maintainability defect with no behavioural impact.

### F-01 — Infrastructure depended inwards on the application layer

* **Severity:** Major
* **Affected:** specification § 2.2 (layering rules); AC-G-17 (architecture documented and
  respected)
* **Description:** `backend/src/infrastructure/mail/mailer.ts` imported the mail port from
  `backend/src/application/ports.ts`. The specification states that infrastructure never
  imports the application layer. dependency-cruiser reported this as
  `infrastructure-not-inward: src/infrastructure/mail/mailer.ts → src/application/ports.ts`.
  The rule existed only as prose before this phase, so nothing had caught it.
* **Fix performed:** The port moved to `backend/src/domain/ports/mailPort.ts`. Both sides of
  a port point at it — the use case that calls it and the adapter that implements it — so
  the domain layer is its correct home; placing it in the application layer necessarily
  forces infrastructure to depend inwards. `docs/specification.md` § 2.2 and § 2.4 were
  updated to state this explicitly, and `backend/.dependency-cruiser.cjs` now enforces all
  six layering rules mechanically, including "only `infrastructure/db` may import the
  database driver".
* **Result after re-verification:** `✔ no dependency violations found (34 modules, 87
  dependencies cruised)`. All tests still pass.

### F-02 — Duplicated validation-error formatting in the configuration layer

* **Severity:** Minor
* **Affected:** specification § 12, § 3.4 (startup failure messages)
* **Description:** jscpd found one clone in production code: six identical lines formatting
  Zod issues into an operator-readable message, in `config/env.ts` and
  `config/optionsConfig.ts`. Duplicated message formatting drifts apart over time, so the
  two failure paths would eventually read differently.
* **Fix performed:** Extracted into `backend/src/config/formatIssues.ts`, used by both.
* **Result after re-verification:** jscpd reports **0.00% duplication in production code**
  (36 files). The remaining clones are all test fixtures, where a per-file fixture is
  preferable to shared indirection.

### F-03 — Dead production code

* **Severity:** Minor
* **Affected:** "do not add functionality that is not justified"
* **Description:** Three exported members had no production caller:
  `OptionCatalogue.displayNameOf`, `isOptionGroupId` and `LATEST_SCHEMA_VERSION`. Worse,
  the doc comment on `displayNameOf` claimed the export used it to keep historical
  registrations readable (AC-003-07) — an inaccurate justification. AC-003-07 is in fact
  satisfied by capturing each option's display name at submission time and storing it in
  `registration_options.display_name`, which is independently tested.
* **Fix performed:** All three removed, together with the one unit test that existed solely
  to exercise `displayNameOf`. AC-003-07 remains covered by the acceptance test that
  reloads the application against a changed programme and asserts that a registration
  stored beforehand still resolves to its original identifier and display name.
* **Result after re-verification:** 277 backend tests pass; coverage rose to 95.21%
  statements.

### F-04 — Two repository methods with no production caller

* **Severity:** Minor
* **Affected:** "do not add functionality that is not justified"
* **Description:** `RegistrationRepository.count()` and `.findByReference()` are used only by
  the test suite. The API deliberately exposes no endpoint returning a single registration
  or a count.
* **Fix performed:** Kept, and documented as such in the source. Removing them would push
  raw SQL into the test suite, coupling the tests to the schema and weakening the very
  assertions that prove AC-005-01 and AC-005-04. The trade-off is recorded here rather than
  hidden: these are deliberate test-support query methods on a repository, not accidental
  API surface.
* **Result after re-verification:** No behavioural change; recorded as an accepted,
  documented deviation.

### F-05 — Acceptance-criteria traceability gaps in the test suite

* **Severity:** Minor
* **Affected:** AC-002-11, AC-003-01, AC-004-07, AC-006-03, AC-007-04, AC-G-01, AC-G-02,
  AC-G-07
* **Description:** A mechanical cross-check of every AC identifier against the test sources
  found eight criteria with no test naming them. Seven were covered by existing tests that
  simply did not cite the identifier. One — **AC-002-11** (the Unicode and whitespace rules
  apply to the student-only fields) — was genuinely untested: the equivalent rules were
  only exercised on the external variant.
* **Fix performed:** Two new acceptance tests for AC-002-11 (trimming and Unicode
  preservation across `studyInstitution`, `studyProgramme` and `studentId`; and a
  whitespace-only student field rejected as empty). The seven other criteria now cite their
  identifiers in the test names that already covered them.
* **Result after re-verification:** Every Acceptance Criterion except AC-G-14, AC-G-16 and
  AC-G-17 is now named by at least one automated test. The backend suite ends at 277 tests:
  278 after the two new ones, less the one removed with F-03.

### Findings summary

| Severity | Count | Resolved |
| --- | --- | --- |
| Critical | 0 | — |
| Major | 1 | 1 |
| Minor | 4 | 4 (one as a documented accepted trade-off, F-04) |

**Fix loop count: 1.** All findings were raised in the first verification pass, fixed
together, and the complete check set was re-executed; the second pass produced no new
findings.

---

## 3. Acceptance-criteria traceability

87 Acceptance Criteria are defined. Each was checked against the implementation and against
the test suite.

| Group | Criteria | Verified by | Status |
| --- | --- | --- | --- |
| AC-001-01 … 13 (US-001) | 13 | API, acceptance, integration, e2e, unit | **Pass** |
| AC-002-01 … 12 (US-002) | 12 | API, acceptance, integration, e2e, unit | **Pass** |
| AC-003-01 … 09 (US-003) | 9 | unit (catalogue), API, acceptance (programme change + reload) | **Pass** |
| AC-004-01 … 07 (US-004) | 7 | frontend component, frontend contract, e2e (both viewports) | **Pass** |
| AC-005-01 … 08 (US-005) | 8 | integration, component, failure/recovery | **Pass** |
| AC-006-01 … 05 (US-006) | 5 | integration, unit (templates), security | **Pass** |
| AC-007-01 … 07 (US-007) | 7 | integration, component (mailer), unit (templates) | **Pass** |
| AC-008-01 … 09 (US-008) | 9 | API, component (workbook read back), acceptance | **Pass** |
| AC-G-01 … 08 | 8 | API, contract, frontend component, e2e | **Pass** |
| AC-G-09 … 11 | 3 | security suite | **Pass** |
| AC-G-12, AC-G-13 | 2 | e2e at 375 px and 1280 px, frontend component | **Pass** |
| AC-G-14, AC-G-16 | 2 | static review only — see § 6 | **Not verifiable here** |
| AC-G-15 | 1 | unit (`loadConfig` rejects every missing required variable) | **Pass** |
| AC-G-17 | 1 | manual artefact inventory — see below | **Pass (manual)** |

**AC-005-04 and AC-005-07** deserve a note, because they are the strongest claims in the
system. AC-005-04 is verified by breaking the backup directory and observing that the API
returns `500`, no database row exists, and no partial file remains; and by a repository test
in which a throwing side effect inside the transaction leaves zero rows and zero option
rows. AC-005-07 is verified by closing the application and rebuilding it against the same
data directory, then reading the registration back and exporting it.

**AC-G-17 (documentation completeness)** — artefact inventory, all present:
`docs/acceptance-criteria.md`, `docs/specification.md`, `docs/test-strategy.md`,
`docs/verification-report.md`, `README.md`, `RELEASE_NOTES.md`, `experiment/run-log.json`,
`experiment/run-summary.md`, `.env.example`. Architecture, validation strategy, security
controls, testing strategy and deployment decisions are documented and justified in the
specification (ADR-001 … ADR-005, § 5, § 11, § 13) and in the test strategy.

**Acceptance Criteria passed on first evaluation: 84 of 87.** The three exceptions were
AC-002-11 (genuinely untested, F-05), AC-G-14 and AC-G-16 (not verifiable in this
environment). After the fix loop: **85 of 87 verified**, 2 not verifiable here.

---

## 4. Requirements review

### Missing requirements

None found. Every constraint in `PROJECT_CONSTRAINTS.md` and every field rule in
`FORM_SCHEMA.md` maps to implemented, tested behaviour. Specifically re-checked:

* two registration variants with the exact fixed field sets — present, and the field lists
  are asserted element-by-element;
* configurable workshops, events, meals and other activities that change without touching
  participant fields — the four groups are fixed, their contents are configuration;
* REST submission, confirmation only after a success response — the confirmation is rendered
  only in the `201` branch, proven in a real browser;
* relational database **and** JSON on persistent storage — both, written atomically;
* participant confirmation email and organizer notification with the JSON attached — both,
  with the attachment proven byte-identical to the stored file;
* Excel export — authenticated, real `.xlsx`, both variants' columns;
* frontend and backend validation, error messages, malicious-input and anti-automation
  protection, preventive controls, secure data handling — § 5 below;
* responsive frontend — no horizontal overflow at 375 px or 1280 px;
* containerized deployment — defined, statically reviewed, see § 6.

### Incorrectly implemented requirements

None found.

### Unrequested functionality

| Item | Assessment |
| --- | --- |
| `GET /api/health` | Justified: the containerized-deployment constraint requires a healthcheck, and both container images use it. |
| Optional `description` on a conference option | Within `FORM_SCHEMA.md`, which requires *at least* identifier, display name and active status. Optional and unused by the shipped configuration. |
| `count()` / `findByReference()` on the repository | Test-support query methods; kept and documented (F-04). |
| Everything else | Traces to a User Story, a constraint or the specification. |

No feature was added beyond the derived specification. Out-of-scope items listed in
`docs/acceptance-criteria.md` (participant login, editing or cancelling a registration,
payments, an admin UI, multi-conference support, a public participant list) are absent.

### Specification violations

One, F-01, fixed. The specification itself was corrected in two places during this phase:
§ 2.2/§ 2.4 to state where outbound ports live (the fix for F-01), and the module list to
include `config/formatIssues.ts` (F-02). Both changes are recorded here rather than made
silently.

### Regressions

None. The complete suite was re-run after every fix; no test that passed before a fix failed
after it.

---

## 5. Security review

| Control | Status | Evidence |
| --- | --- | --- |
| Backend validation independent of the frontend | Present | Every rule is enforced on requests sent directly to the API, bypassing the browser entirely |
| Aggregated, non-leaking validation messages | Present | Every offending field returned in one response; responses asserted to contain no stack trace, SQL or path |
| SQL injection | Mitigated | Only parameterised statements; dependency-cruiser forbids any module outside `infrastructure/db` from importing the driver; a `'; DROP TABLE registrations; --` payload is stored verbatim and the table survives |
| Cross-site scripting | Mitigated | Frontend renders every dynamic value through `textContent` (no `innerHTML` anywhere in the source); emails HTML-escape every participant value; nginx CSP forbids inline and third-party script |
| Email header injection | Mitigated | CR/LF and all C0/C1 control characters rejected during normalisation |
| Spreadsheet formula injection | Mitigated | Cells beginning `=`, `+`, `-`, `@` or a tab are written as explicit text |
| Mass assignment | Mitigated | Strict schema rejects unknown properties; fields of the non-submitted variant are dropped before the domain object is built |
| Path traversal | Mitigated | Backup file names are built only from a reference validated against its own pattern; a traversal string in an option identifier is rejected as an unknown option |
| Anti-automation | Present | Honeypot, HMAC-signed single-use form token bound to the variant with a TTL and a minimum fill time, and a per-IP rate limit; every rejection returns one identical opaque message |
| Rate limiting | Present | Registration 5 / 10 min, config 60 / 5 min, export 10 / 15 min, global 300 / 15 min, each on its own exact path |
| Request size limit | Present | 32 KB, returns `413` before parsing |
| Security headers | Present | `nosniff`, `X-Frame-Options: DENY`, `no-referrer`, API CSP `default-src 'none'`, HSTS in production, `X-Powered-By` removed; nginx adds the document CSP and `Permissions-Policy` |
| CORS | Present | Exact origin allowlist, credentials disabled, never a wildcard |
| Export authentication | Present | HTTP Basic with constant-time comparison; wrong username, wrong password and non-Basic headers all rejected with no data |
| Personal data disclosure | Mitigated | No unauthenticated endpoint returns registration data; the export sets `Cache-Control: no-store`; no personal data in URLs |
| Personal data in logs | Mitigated | pino redaction of names, email, student id, authorization and cookie headers; application logs identify registrations by reference |
| Secret handling | Present | Secrets only from environment variables; `.env` git-ignored and confirmed untracked; `.env.example` contains placeholders only; no credential literal in production source |
| Container hardening | Present (static review) | Non-root `node` user, multi-stage build, build toolchain removed after install, no dev dependencies in the runtime image |
| Dependency advisories | Clean | `npm audit`: 0 vulnerabilities |

**Security findings: 0 Critical, 0 High, 0 Medium, 0 Low — 0 unresolved.**

Two advisory-level items were resolved during implementation rather than here, and are
recorded for completeness: the first dependency install reported 1 high and 5 moderate
advisories (nodemailer SMTP/CRLF injection family; vitest path traversal; exceljs's
transitive `uuid`). They were resolved by raising nodemailer to ≥10.0.10 and vitest to
≥5.0.1 and by overriding `uuid` to ^11.1.1, before any of the affected code was written.

### Accepted limitations (documented, not defects)

1. **Single-instance anti-automation.** The replay cache for used form-token nonces and the
   rate-limit counters are per-process. Running more than one backend instance would weaken
   the single-use token guarantee and the per-IP limits until a shared store is introduced.
   This matches the single-instance deployment specified in § 13 and is stated in
   specification § 11.
2. **TLS terminates upstream.** The application serves plain HTTP inside the container
   network and expects a TLS-terminating reverse proxy. `TRUST_PROXY` must be enabled only
   when such a proxy really is in front, otherwise the rate limit can be evaded with a
   forged `X-Forwarded-For`; the default is `false`.
3. **No third-party CAPTCHA.** A determined, well-resourced attacker can still script
   submissions. ADR-005 records the reasoning and the extension point.

---

## 6. Not verifiable in this environment

These are recorded as unverified rather than claimed as passing.

| Criterion | Why | What *was* done |
| --- | --- | --- |
| **AC-G-14** — `docker compose up --build` starts the whole system | Docker is not installed in this environment (`docker: command not found`) | `backend/Dockerfile`, `frontend/Dockerfile`, `frontend/nginx.conf` and `docker-compose.yml` were written and statically reviewed: multi-stage builds, non-root runtime user, healthchecks on both services, `depends_on: service_healthy`, the API proxied same-origin through nginx, and the build context paths checked against `.dockerignore`. The same processes were run natively (backend on Node 24, frontend served by Vite preview behind an `/api` proxy) and the full end-to-end suite passes against them. |
| **AC-G-16** — persistent data survives a container restart | Same | The equivalent at process level is tested: the application is closed and rebuilt against the same data directory, and the registration is still readable and exportable. The compose file mounts a named volume at `/data`, which is where both the SQLite file and the JSON backups live. |
| Real SMTP delivery | No mail server or mailbox available | The adapter is exercised against Nodemailer's `json` transport, which composes the real message including the attachment; delivery is an operational concern. |

---

## 7. Metrics recorded in this phase

| Metric | Value |
| --- | --- |
| Lint errors / warnings | 0 / 0 |
| Type-check errors | 0 |
| Tests, final | 347 (277 backend + 42 frontend + 28 e2e) |
| Test pass rate, final | 100% |
| Coverage, backend | 95.21% statements, 82.82% branches, 96.02% functions, 95.12% lines |
| Production LOC (TypeScript + CSS) | 4,065 |
| Test LOC | 4,059 |
| Cyclomatic complexity | 211 functions, average 2.41, max 12, 3 functions above 10 |
| Code duplication, production | 0.00% |
| Modules (backend `src`) | 34 |
| Internal dependencies | 87 |
| Dependency cycles | 0 |
| Architecture rule violations | 0 |
| Runtime dependencies | 9 |
| `npm audit` findings | 0 |

Full raw measurements, including per-module coupling and instability, are in
`experiment/run-log.json`.

---

## 8. Conclusion

The implementation satisfies the User Stories, the project constraints and the derived
Acceptance Criteria, with the three exceptions in § 6 that this environment cannot
demonstrate and which are recorded as unverified rather than assumed.

One Major finding (a layering violation) and four Minor findings were raised, fixed and
re-verified in a single fix loop. The full check set — lint, type check, build, 347 tests,
coverage, dependency audit, architecture conformance, duplication and complexity — passes.

**Verification status: PASS.** The work is ready for merge.
