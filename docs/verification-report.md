# Verification Report

**Phase 5 artefact.** Independent self-verification of the implementation against
`USER_STORIES.md`, `PROJECT_CONSTRAINTS.md`, `FORM_SCHEMA.md`, `docs/acceptance-criteria.md`
and `docs/specification.md`.

* Verification started: **2026-09-21T11:17:31Z**
* First verification completed: **2026-09-21T11:28:18Z**
* Container verification (loop 2) started: **2026-09-21T13:05:13Z**
* Container verification completed: **2026-09-21T13:24:40Z**
* Fix loops: **2** (Verification → Fix → Verification, twice)
* Final status: **PASS**. AC-G-14 and AC-G-16 are now verified against a running
  containerized deployment; see § 6.

> **Loop 2 (2026-09-21T13:05Z).** Docker became available after the first verification, so
> the two acceptance criteria that had been recorded as unverifiable were executed for
> real. Doing so uncovered two further findings — **F-06** (Major) and **F-07** (Major) —
> that no in-process test could have reached. Both are fixed and re-verified below. This
> is the clearest result of the run: the deployment artefact had defects that every
> native check passed over.

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
| Unit/component/integration/API/acceptance/security tests | Vitest 5 | 281 passed, 0 failed |
| Frontend tests | Vitest 5 (jsdom) | 42 passed, 0 failed |
| End-to-end tests, native | Playwright, Chromium, 1280×900 and 375×812 | 28 passed, 0 failed |
| End-to-end tests, **containerized stack** | Playwright against `docker compose` on :8080 | 46 passed, 0 failed, three consecutive runs |
| Container build | `docker compose build` | both images built |
| Container run | `docker compose up`, healthchecks | backend healthy, frontend started after it |
| Container hardening | `docker exec` inspection | runs as uid 1000 (node); no dev dependencies in the runtime image |
| Container fail-fast | `docker run` with a missing variable / invalid programme | exits 1 with a message naming the problem |
| Coverage, backend | Vitest v8 | 95.21% statements, 82.82% branches, 96.02% functions |
| Dependency advisories | `npm audit` | 0 vulnerabilities |
| Architecture conformance | dependency-cruiser, 6 rules | 0 violations, 34 modules, 87 dependencies |
| Code duplication | jscpd (≥5 lines, ≥50 tokens) | production code 0.00% |
| Cyclomatic complexity | ESLint `complexity` rule, reported per function | 211 functions, average 2.41, max 12 |
| Acceptance-criteria traceability | manual review + mechanical AC-id cross-check | 86 of 87 verified automatically |
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

### F-06 — Security headers absent on every HTML document in the container

* **Severity:** Major
* **Affected:** AC-G-11 (preventive security controls); specification § 11, § 13
* **Description:** Found by requesting `/` from the running frontend container: the
  registration documents were served with **no** Content-Security-Policy,
  `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy` or
  `Permissions-Policy`. The headers were declared once at nginx `server` level, which
  reads correctly, but nginx's `add_header` is not additive across levels: a `location`
  block that declares any `add_header` of its own **discards every inherited one**. Both
  document locations set a `Cache-Control` header, so both silently dropped the whole
  security set. The policy was therefore missing from exactly the responses it exists to
  protect, while the proxied API — whose location sets no `add_header` — still looked
  correct, which is why the static review of the configuration did not catch it.
* **Fix performed:** The headers were extracted into `frontend/security-headers.conf` and
  `include`d explicitly in each document location, with `always` so they survive the
  `try_files` 404. The snippet is deliberately **not** included in the API location: the
  backend sets its own, stricter policy there, and applying the document policy as well was
  emitting two `Content-Security-Policy` headers on every API response.
* **Result after re-verification:** All five headers present on `/`,
  `/studentska-prijava/`, hashed assets and a 404; exactly one CSP on API responses, the
  backend's. Covered from now on by `e2e/container-specs/deployment.container.spec.ts`,
  which is the regression test for this finding.

### F-07 — Read-path rate limits deny the form to participants sharing one address

* **Severity:** Major
* **Affected:** AC-G-10, AC-001-01, AC-002-01; specification § 11
* **Description:** Surfaced as intermittent end-to-end failures against the container —
  the same suite passing in 6 seconds on one run and failing 15 tests over 4 minutes on the
  next. The cause was not flakiness in the tests: `GET /api/registration-config` was
  limited to 60 requests per 5 minutes per address, and every page load costs one such
  request. Once exhausted, the form could not load at all and every assertion timed out.
  The production consequence is worse than the test symptom: participants behind a shared
  public address — a university, a company, conference wifi — consume one budget together,
  so roughly 60 people opening the form within five minutes would lock out everyone else
  behind that address. For a system whose whole purpose is a registration rush following an
  announcement email, that is a self-inflicted outage. The global backstop of 300 per 15
  minutes had the same defect.
* **Fix performed:** The limits are now asymmetric by intent and documented as such. The
  write path keeps its strict 5 per 10 minutes — that is the control that actually caps
  abuse. The read paths were raised to 300 per 5 minutes (configuration) and 1200 per 15
  minutes (global API backstop), and all of them became environment-tunable
  (`RATE_LIMIT_CONFIG_MAX`, `RATE_LIMIT_CONFIG_WINDOW_MINUTES`, `RATE_LIMIT_GLOBAL_MAX`,
  `RATE_LIMIT_GLOBAL_WINDOW_MINUTES`). Specification § 11 and § 12 and `.env.example`
  now state the reasoning, so the values are not silently re-tightened later.
* **Result after re-verification:** Three consecutive container suite runs at 46/46 in
  ~6.4 s each, with no throttling. The shipped write limit was separately confirmed still
  enforced in the container: submissions 1–5 returned `201`, submission 6 returned
  `429`. Three new backend tests pin the behaviour: 120 consecutive configuration reads
  are not throttled, a deliberately low limit still throttles, and the read defaults are
  asserted to exceed the write default.

### Findings summary

| Severity | Count | Resolved |
| --- | --- | --- |
| Critical | 0 | — |
| Major | 3 | 3 |
| Minor | 4 | 4 (one as a documented accepted trade-off, F-04) |

**Fix loop count: 2.**

* **Loop 1** (native checks): F-01 … F-05 raised, fixed together, complete check set
  re-executed, no new findings.
* **Loop 2** (containerized deployment, once Docker became available): F-06 and F-07
  raised, fixed, and the complete check set plus the container suite re-executed three
  times consecutively with no new findings.

Both Major findings in loop 2 were in the deployment artefact rather than in the
application code, and neither was reachable by any check that does not run the containers.
That is the substantive lesson of this run: a statically reviewed Dockerfile and nginx
configuration is not a verified one.

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
| AC-G-14, AC-G-16 | 2 | built and ran the compose stack; acceptance suite against the containers; restart and recreation | **Pass** |
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
AC-002-11 (genuinely untested, F-05), AC-G-14 and AC-G-16 (Docker unavailable at the time).
After loop 1: 85 of 87 verified. After loop 2, in which AC-G-14 and AC-G-16 were executed
against real containers and two Major defects were found and fixed: **87 of 87 verified.**

AC-G-14 and AC-G-16 did **not** pass on first evaluation once they became executable: the
containerized deployment was serving every HTML document without security headers (F-06)
and throttling legitimate form loads (F-07). Both are fixed and re-verified.

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
| Rate limiting | Present (retuned in loop 2) | Asymmetric by intent: registration 5 / 10 min (the anti-abuse control), configuration 300 / 5 min, global 1200 / 15 min, export 10 / 15 min, each on its own exact path and all tunable. The read limits were raised under F-07 because the earlier values denied the form to participants sharing one public address |
| Request size limit | Present | 32 KB, returns `413` before parsing |
| Security headers | Present (fixed in loop 2) | API: `nosniff`, `X-Frame-Options: DENY`, `no-referrer`, CSP `default-src 'none'`, HSTS in production, `X-Powered-By` removed. Documents: CSP `default-src 'self'`, `nosniff`, `DENY`, `no-referrer`, `Permissions-Policy` — absent until F-06 was found and fixed against the running container, now regression-tested |
| CORS | Present | Exact origin allowlist, credentials disabled, never a wildcard |
| Export authentication | Present | HTTP Basic with constant-time comparison; wrong username, wrong password and non-Basic headers all rejected with no data |
| Personal data disclosure | Mitigated | No unauthenticated endpoint returns registration data; the export sets `Cache-Control: no-store`; no personal data in URLs |
| Personal data in logs | Mitigated | pino redaction of names, email, student id, authorization and cookie headers; application logs identify registrations by reference |
| Secret handling | Present | Secrets only from environment variables; `.env` git-ignored and confirmed untracked; `.env.example` contains placeholders only; no credential literal in production source |
| Container hardening | Present (executed) | Verified inside the running container: `uid=1000(node)`, no dev dependencies in the runtime image, healthcheck reporting healthy |
| Dependency advisories | Clean | `npm audit`: 0 vulnerabilities |

**Security findings: 0 Critical, 2 High, 0 Medium, 0 Low — 0 unresolved.**

The two High findings are F-06 (no security headers on any HTML document in the deployed
container) and F-07 (read-path rate limits causing denial of service to legitimate
participants behind a shared address). Both were found only by running the containers, and
both are fixed and regression-tested. Neither existed in the application code: F-06 was in
the nginx configuration and F-07 in the limit values.

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

## 6. Containerized deployment (AC-G-14, AC-G-16)

Docker was not installed during the first verification pass, so these two criteria were
recorded as unverified rather than claimed. Docker became available afterwards and they
were executed for real. What follows is what was run and observed, not a review of the
configuration.

### Build and start

`docker compose build` produced both images on the first attempt. `docker compose up -d`
started the stack: the backend came up, its `HEALTHCHECK` reported healthy, and only then
did the frontend start — `depends_on: service_healthy` behaving as specified. The single
documented command therefore starts the full system (**AC-G-14**).

| Observation | Result |
| --- | --- |
| Images built | `agentic_lab-backend` 413 MB, `agentic_lab-frontend` 73.7 MB |
| Backend user | `uid=1000(node) gid=1000(node)` — non-root as specified |
| Runtime image contents | `vitest`, `typescript`, `eslint`, `supertest`, `@playwright` all absent; `express`, `better-sqlite3`, `exceljs`, `nodemailer`, `helmet`, `zod`, `pino` all present |
| Healthcheck | `health=healthy`, `failing_streak=0` |
| Documents served | `/` and `/studentska-prijava/` return 200 through nginx |
| API same-origin | `/api/health` returns 200 through the nginx proxy |
| Server banner | `Server: nginx`, no version |

### Behaviour against the running stack

* A student registration submitted through `:8080` was accepted, stored, and written to
  the volume, with Slovenian characters intact (`Čenčič` stored as the bytes
  `c4 8c 65 6e c4 8d 69 c4 8d`) and leading/trailing whitespace trimmed.
* The organizer export downloaded through the proxy: `200`, correct spreadsheet content
  type, 16 columns, one row per registration.
* The export without credentials: `401` with `WWW-Authenticate: Basic`.
* SMTP was deliberately pointed at a host that does not resolve. Both emails failed, both
  failures were logged, and both registrations remained stored and exportable — **AC-005-05
  demonstrated live** rather than only with a stubbed transport.
* No participant email address appeared anywhere in the container logs (log redaction).
* The full acceptance suite was run against the containers at both viewports: **46 of 46
  passing, three consecutive runs**, including the browser submitting a real registration
  under the strict CSP with no console errors.

### Persistence across restart and recreation (AC-G-16)

Starting from an empty volume, one registration was created, then:

| Step | Result |
| --- | --- |
| `docker compose restart` | registration still present; export returns 200 |
| `docker compose down` (containers removed) then `up` | registration still present; JSON backup intact with correct Unicode; export returns 200 |

The named volume — not the container filesystem — holds the database and the JSON backups,
so the data survives container recreation, which is the stronger of the two cases.

### Fail-fast inside the container (AC-G-15)

| Scenario | Observed |
| --- | --- |
| Required variable missing | `Startup aborted. Invalid environment configuration: FORM_TOKEN_SECRET: ...`, container exit code **1** |
| Conference programme with a duplicate option id | `Startup aborted. Invalid conference options configuration (/tmp/bad-options.json): groups: duplicate option identifier "dup"` |

### What this pass cost

Executing these two criteria found two Major defects (F-06, F-07) that every native check
had passed. Both lived in the deployment artefact — the nginx configuration and the rate
limit values — which is precisely the part that a static review reads as correct.

### Still not verified

| Item | Reason |
| --- | --- |
| Real SMTP delivery | No mail server or mailbox is available. The adapter is exercised against Nodemailer's `json` transport, which composes the real message including the attachment, and the container run demonstrated the failure path against an unreachable SMTP host. Delivery to a real inbox remains an operational check. |

## 7. Metrics recorded in this phase

| Metric | Value |
| --- | --- |
| Lint errors / warnings | 0 / 0 |
| Type-check errors | 0 |
| Tests, final | 397 (281 backend + 42 frontend + 28 native e2e + 46 containerized e2e) |
| Test pass rate, final | 100% |
| Coverage, backend | 95.21% statements, 82.82% branches, 96.02% functions, 95.12% lines |
| Production LOC (TypeScript + CSS) | 4,099 |
| Test LOC | 4,243 |
| Cyclomatic complexity | 211 functions, average 2.41, max 12, 3 functions above 10 |
| Code duplication, production | 0.00% |
| Modules (backend `src`) | 34 |
| Container images | backend 413 MB, frontend 73.7 MB |
| Internal dependencies | 87 |
| Dependency cycles | 0 |
| Architecture rule violations | 0 |
| Runtime dependencies | 9 |
| `npm audit` findings | 0 |

Full raw measurements, including per-module coupling and instability, are in
`experiment/run-log.json`.

---

## 8. Conclusion

The implementation satisfies the User Stories, the project constraints and all 87 derived
Acceptance Criteria. Real SMTP delivery to a real mailbox remains the only item not
demonstrated, and it is recorded as such rather than assumed.

Three Major and four Minor findings were raised, fixed and re-verified across two fix
loops. The full check set — lint, type check, build, 397 tests across four levels,
coverage, dependency audit, architecture conformance, duplication and complexity — passes,
as does the acceptance suite run against the containerized deployment three times
consecutively.

The second loop is worth stating plainly: once the containers could actually be run, two
Major security defects appeared immediately in the deployment artefact, in configuration
that had been reviewed and read as correct. The first verification pass was right to record
those criteria as unverified rather than to infer them from the configuration files.

**Verification status: PASS.** The work is ready for merge.
