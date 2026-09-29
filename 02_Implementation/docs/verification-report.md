# Verification Report

> **This is a self-scan by the development agent, not an independent
> review** (CONSTITUTION §4). Every result below was produced by the same
> agent that wrote the code, using the tools and judgements described
> here. Severity reclassifications are the agent's own and are flagged
> for human review where they decide a gate (D-8, D-9).

Phase 6, 2026-09-29. Scope: the complete delivery in `02_Implementation/`
at the end of the phase, re-read against USER_STORIES, BUSINESS_RULES,
FORM_SCHEMA, `docs/acceptance-criteria.md`, `docs/specification.md` and
all `02_Technical/` inputs.

Severity levels: exactly the four of `03_Process/SEVERITY_TAXONOMY.md`.

## 1. Summary

| Gate | Result |
| --- | --- |
| Final complete test run | **275 passed, 0 failed** (backend 227, frontend 39, e2e 9) |
| Acceptance-test freeze | **Held** — 18/18 hashes in `docs/acceptance/MANIFEST.sha256` match (working tree and a fresh clone); no test-defect request was needed |
| Unresolved Critical / High findings | **None** — after two fix loops; two residuals were reclassified by the agent with written justification and escalated (D-8, D-9, pending human review of D-9) |
| Findings (as discovered) | Critical 1, High 1, Medium 5, Low 8 |
| Fix loops | 2 (both closed and re-verified) |
| Container execution (DoD §5) | All 9 checks demonstrated, plus persistence across container recreation |
| Escalations accounted for | D-1 … D-9 all in `docs/decisions-log.md`, each with a resolution |

## 2. Definition of Done checks

### 2.1 Static checks (DoD §1)

| Check | Command | Result |
| --- | --- | --- |
| Backend formatting | `./mvnw spotless:check` | Pass |
| SpotBugs 4.10.4.1 | `./mvnw compile com.github.spotbugs:spotbugs-maven-plugin:spotbugs` | Executed — 17 findings, all rank 18 (→ Low, V-09); 3 rank-13 null-path findings fixed (V-08) |
| PMD 7.7 (plugin default ruleset) | `./mvnw pmd:check` | Executed — 0 violations |
| Backend duplication (PMD CPD) | `./mvnw pmd:cpd-check` | Measured — 0 duplications |
| ESLint | `npm run lint` | 0 errors, 0 warnings |
| TypeScript | `npm run typecheck` | 0 errors |
| Frontend duplication (jscpd) | `npm run duplication` | Measured — 1 clone, 5 lines (0.31 %) (V-12) |
| Frontend formatting | `npm run format` | Pass (after V-05 fix) |

Tool-severity mapping used (SEVERITY_TAXONOMY "Mapping tool output"):
SpotBugs rank 1–4 → High, 5–9 → Medium, 10–20 → Low; PMD priority
1 → High, 2 → Medium, 3–5 → Low; Semgrep `ERROR` → High, `WARNING` →
Medium, `INFO` → Low; npm audit `moderate` → Medium; Dependency-Check
uses CVSS Critical/High/Medium/Low directly; surviving mutants are
classified individually.

### 2.2 Tests (DoD §2)

Final complete run (2026-09-29T06:48Z–06:49Z), plus a full re-run from
a fresh clone while following the README (262/262, before fix loop 2
added 13 tests).

| Category | Where | Result |
| --- | --- | --- |
| Unit tests (backend, no Spring context) | `backend/src/test/java` (service, integration, config) | 112 passed, 0 failed |
| Architecture tests (ArchUnit) | `ArchitectureTest` | 9 passed, 0 failed |
| Integration tests (Spring Boot + Testcontainers) | `security.RecaptchaProductionModeTest`, `security.HttpSecurityTest` | 16 passed, 0 failed |
| API / contract acceptance tests (frozen) | `backend/.../acceptance/` (incl. `ApiContractAcceptanceTest`, `RateLimitAcceptanceTest`) | 90 passed, 0 failed |
| Frontend / component tests | Vitest + RTL, `frontend/src/**/*.test.*` | 39 passed, 0 failed |
| E2E (frozen) | Playwright, `frontend/e2e/` | 9 passed, 0 failed — against the Vite dev server and against the nginx container |
| Unit coverage (backend, JaCoCo, unit tests only) | | instructions 76.9 %, branches 82.4 %, lines 77.4 % |
| Integration coverage (backend, JaCoCo, acceptance + integration only) | | instructions 93.0 %, branches 73.4 %, lines 91.6 % |
| Combined backend coverage (final run) | | instructions 97.0 %, branches 88.0 %, lines 96.4 % |
| Frontend coverage (V8) | `npm run test:coverage` | statements 97.9 %, branches 95.6 %, functions 90 % |

### 2.3 Security (DoD §3)

| Tool | Result at the end of the phase |
| --- | --- |
| Semgrep (`p/default`, `p/owasp-top-ten`, `p/java`, `p/react`, `p/typescript`, `p/secrets` + project `.semgrep.yml`; 408 rules, 94 files) | 1 finding: `use-of-basic-authentication` (V-02, residual Medium after fix loop 2, D-9). `request-host-used` (V-04) fixed. Report: `docs/verification/semgrep-report.json` |
| OWASP Dependency-Check 12.1.0 (NVD API key from the operator, NVD data 2026-09-28T22:17Z) | Initially 35 Critical / 77 High / 77 Medium / 10 Low (V-01). After fix loop 1: 14 Critical / 17 High / 16 Medium / 2 Low CVE matches = 16 unique Critical/High CVEs with **no reachable exploit path** (§4, V-01) → Medium (15) and Low (1 false positive). Report: `docs/verification/dependency-check-report.json` |
| npm audit | Production dependencies (`--omit=dev`): **0**. Dev tooling: 5 moderate (V-07). Report: `docs/verification/npm-audit.json` |

### 2.4 Architecture (DoD §4)

`ArchitectureTest` encodes exactly the seven rules of specification §2.1
(domain independent; persistence → domain only; integration → domain
only; web ↛ persistence/integration; service ↛ web/config; no package
cycles; controllers/entities/repositories in their packages).
**0 violations**; the dependency-cycle check (`slices().beFreeOfCycles()`)
passes. The scaffold supplied no layering rules.

### 2.5 Test integrity and mutation testing (DoD §4a)

- **Freeze held**: `sha256sum -c docs/acceptance/MANIFEST.sha256` →
  18/18 OK in the working tree and in a fresh clone (the
  `.gitattributes` rules reproduce the frozen bytes on checkout).
- **Backend — PIT 1.30.0** (`./mvnw test-compile org.pitest:pitest-maven:mutationCoverage`),
  scoped to validation (`RegistrationValidator`), anti-automation
  (`RecaptchaVerifier`), authentication transport
  (`OrganizerTransportFilter`), abuse controls (`RateLimitFilter`,
  `RequestSizeLimitFilter`), configuration guards (`AppProperties`),
  persistence/backup (`RegistrationService`, `JsonBackupStore`,
  `OrganizerService`) and option catalog (`OptionCatalogService`,
  `OptionsFileReader`), run with the unit tests:
  **262 mutants — 222 killed, 13 survived, 27 without unit coverage;
  mutation score 84.7 %, test strength 94.5 %.** First pass (before test
  strengthening): 245 mutants, 192 killed, 29 survived, 24 no coverage
  (78.4 %). The 27 no-coverage mutants are in code exercised only by the
  Spring-context tests (startup guards in `AppProperties`, the replayed
  body wrapper in `RequestSizeLimitFilter`, the interrupt path of
  `RecaptchaVerifier`). Report: `docs/verification/pit-mutations.xml`.
- **Frontend — Stryker 10.0.0** (`npm run test:mutation`) on
  `validate.ts`, `client.ts`, `Captcha.tsx`, `RegistrationPage.tsx`:
  **373 mutants — 266 killed, 100 survived, 7 no coverage; score 71.3 %**
  (`validate.ts` 86/87 killed). Report: `docs/verification/stryker-mutation.json`.
- Surviving-mutant triage: V-11.

### 2.6 Container execution (DoD §5)

`docker compose up -d --build` (project `conference-registration-testrun-latest`),
evidence in `docs/verification/container-demo.txt`:

| # | Check | Result |
| --- | --- | --- |
| 1 | External registration (via frontend nginx `/api`) | 201, Unicode preserved |
| 2 | Student registration | 201 |
| 3 | Persistence in PostgreSQL | both rows and option links via `psql` in the postgres container |
| 4 | Raw JSON backup | `/app/data/registrations/<id>.json` on the backup volume (owned by the non-root `app` user) |
| 5 | Participant email in local SMTP | Mailpit: "Registration confirmation" to each participant |
| 6 | Organizer email in local SMTP | Mailpit: "New registration <id>" to the organizer address |
| 7 | Organizer JSON attachment | `registration-<id>.json`, byte-identical to the backup file |
| 8 | Excel export | 200 `.xlsx` with Basic auth; 401 without |
| 9 | Health/readiness | `{"status":"UP"}`; compose health checks healthy |
| + | Persistence across container recreation | rows and backup files present after `--force-recreate` of postgres and backend |
| + | Fix loop 2 re-verification | organizer request over plain HTTP → 403 `HTTPS_REQUIRED`; via TLS proxy header → 200 |
| + | Frontend container headers | CSP, `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy` present; e2e suite passes against the container, so the CSP does not break the app |

### 2.7 Documentation (DoD §6)

All required documents exist. `README.md` was written in this phase and
verified by following it from a fresh clone (build, compose up, health
check, export, restore, the backend and frontend check commands, e2e);
following it exposed one defect (V-06), which was fixed.

## 3. Requirements re-check

| Area | Result |
| --- | --- |
| US-001 … US-008 / 45 ACs | All covered by passing frozen acceptance tests (traceability in `docs/test-strategy.md` §1) and demonstrated at runtime (§2.6) |
| Technical constraints (REST, DB + JSON backup, emails + JSON attachment, Excel export with access control, options without schema change, health) | Met |
| Security requirements checklist (backend/frontend validation, malicious input, anti-automation + backend reCAPTCHA, rate limiting, secure DB access, output encoding, headers, request-size limits, error handling, secrets, dependency vulnerabilities, safe email, no personal-data logging, option-id validation) | Met; see findings for residual risk. Personal data logging checked: 0 matches for names/emails/student IDs in 459 application log lines of a full acceptance run |
| Unrequested functionality | `POST /api/organizer/backups/restore` (implements US-005 "recovered if necessary") and `GET /api/config` (delivers the reCAPTCHA site key) — both justified by requirements |
| Architecture as declared | Met (§2.4) |
| Regressions | None — full suites green after every fix |

## 4. Findings

| ID | Severity (as found) | Source | Finding | Resolution |
| --- | --- | --- | --- | --- |
| V-01 | **Critical** | OWASP Dependency-Check | 35 Critical / 77 High CVE matches in the scaffold-pinned Spring Boot 3.4.4 stack (Spring Framework 6.2.5, Spring Security 6.4.4, Tomcat 10.1.39, Jackson 2.18.3, log4j-api 2.24.3 via POI, PostgreSQL JDBC 42.7.5, Angus mail). | **Fix loop 1** after escalation D-8 (human: upgrade within 3.x + triage): parent → 3.5.16; `tomcat.version` 10.1.60, `log4j2.version` 2.26.1, `commons-lang3.version` 3.20.0. Remaining 16 unique CVEs have no published OSS fix for the 3.x line and were verified **unreachable** — the vulnerable feature is absent from the runtime dependency tree and the source (no WebFlux, RSocket, SSE, XsltView/view rendering, user-supplied SpEL, `DataBinder` property paths, embedded UnboundID LDAP, DPoP, WebAuthn): CVE-2026-47884, -47890, -47891, -47892, -59283, -59313 (Critical), -47885, -47886, -47888, -47889, -47893, -59282 (High) in Spring Framework; CVE-2026-59270 (Critical), -41707, -47841 (High) in Spring Security → **Medium** ("dependency CVE with no reachable exploit path", SEVERITY_TAXONOMY). CVE-2025-7962 on `angus-activation` 2.0.3 affects Jakarta Mail < 2.0.2; the application uses Jakarta Mail 2.0.5 and rejects CR/LF in all input → **Low** (false positive). Re-verified: re-scan, 207/207 backend tests. |
| V-02 | **High** | Semgrep `use-of-basic-authentication` | Organizer endpoints use HTTP Basic (credentials on every request; replayable if ever sent over plaintext). | **Fix loop 2**: `OrganizerTransportFilter` refuses non-HTTPS organizer requests with 403 `HTTPS_REQUIRED` before authentication (default on; loopback exempt; local compose opts out). Existing controls: ≥16-char password (startup-enforced), BCrypt, 10 req/min/IP, stateless, no CORS. The Basic scheme itself is fixed by the specification and the frozen acceptance tests, and the rule's alternatives (OAuth2/OIDC) are out of scope → residual **Medium** ("control present but weaker than best practice"), escalated as **D-9** (agent-default, pending human review). |
| V-03 | Medium | Semgrep parse error | `docs/contracts/openapi.yaml` was not valid YAML (unquoted descriptions containing `: `), and unquoted non-ASCII scalars were rejected by Semgrep's parser, so the contract was unreadable by tools and silently excluded from scanning. | Fixed: descriptions and Unicode example values quoted; validated with js-yaml and parsed by Semgrep (which then surfaced V-02). |
| V-04 | Medium | Semgrep `request-host-used` | `frontend/nginx.conf` forwarded the client-controlled `Host` header to the backend. | Fixed: header no longer forwarded (nginx sends the upstream host). |
| V-05 | Medium | DoD §1 gate | `npm run format` failed: scaffold config files checked out with CRLF (core.autocrlf) and generated directories were checked. | Fixed: files normalized, `.prettierignore` for generated output, `.gitattributes` `frontend/** eol=lf` so fresh clones pass (verified). |
| V-06 | Medium | DoD §6 | `02_Implementation/README.md` was still the scaffold's text, not a run guide; following the new guide showed the organizer curl commands used variables that were never loaded. | Fixed: README written; `set -a && . ./.env && set +a` step added; re-followed from a fresh clone. |
| V-07 | Medium | npm audit | 5 moderate advisories in dev-only tooling: `vitest`/`@vitest/mocker`/`@vitest/coverage-v8` (path traversal in the mocker), `qs` via `typed-rest-client` via Stryker (DoS). | Accepted: none is in the production bundle (`npm audit --omit=dev` → 0); fix needs a Vitest major upgrade (3 → 5) of scaffold tooling. Open item. |
| V-08 | Low | SpotBugs rank 13 | Possible null dereference of `Path.getFileName()` (2×) and of `TransactionTemplate.execute` result. | Fixed (`String.valueOf`, `Objects.requireNonNull`). |
| V-09 | Low | SpotBugs rank 18 | 17× `EI_EXPOSE_REP/REP2` (records holding lists; constructor-injected collaborators). | Accepted: collaborators are Spring singletons by design; the lists come from `List.of`/`toList()` or are request-scoped. |
| V-10 | Low | Code review | Two `LOG.error(..., e)` calls would print exception messages; a PostgreSQL CHECK violation message contains the rejected row (personal data). Unreachable today (validation precedes storage) but latent. | Fixed: only the exception type is logged. |
| V-11 | Low | PIT / Stryker | Backend: 13 survivors (e.g. return value of `text()` when an error is already recorded — equivalent; `FileChannel.force` removal — not observable in tests; export null-guard — covered by acceptance tests outside PIT's unit scope). Frontend: 100 survivors, mostly UI attributes (autocomplete hints, initial empty strings), header literals (caught by the e2e/415 path) and the email regex end anchor (backend is the boundary). | Partly fixed: unit tests strengthened (option-count boundary, per-field `FIELD_NOT_ALLOWED`, token-length boundary, rate-limiter pass-through and eviction, catalog refresh) → backend score 78.4 % → 84.7 %. Rest accepted. |
| V-12 | Low | jscpd | 1 clone, 5 lines (0.31 %) in the frontend. | Accepted. |
| V-13 | Low | Container check | `/api` responses through the frontend nginx carry `X-Content-Type-Options`/`X-Frame-Options` twice (backend + nginx `add_header`), and two CSPs (their intersection applies). | Accepted: identical values; the effective policy is stricter, not weaker. |
| V-14 | Low | Check re-run | `npm run lint` also linted generated `coverage/` output (3 warnings). | Fixed: generated directories ignored in `eslint.config.js`. |
| V-16 | Low | README verification | Cloning into a very deep Windows path fails (`Filename too long`) because of the Java package depth. | Documented in RELEASE_NOTES (use a short path or `git config core.longpaths true`). |

(ID V-15 is unused: the Angus false positive is part of V-01.)

Counted in `run-log.json` → `verificationFindings` by severity **as
found**: Critical 1, High 1, Medium 5, Low 8.

## 5. Fix loops

| Loop | Trigger | Change | Start | End | Re-verification |
| --- | --- | --- | --- | --- | --- |
| 1 | V-01 (Critical) | Spring Boot 3.5.16 + managed-version overrides; reachability triage of the rest (D-8) | 2026-09-29T06:19:10Z | 2026-09-29T06:22:05Z | Dependency-Check re-run; backend 207/207 |
| 2 | V-02 (High) | `OrganizerTransportFilter` (HTTPS-only organizer endpoints), contract 403, config, docs; residual escalated (D-9) | 2026-09-29T06:44:00Z | 2026-09-29T06:46:58Z | backend 227/227 incl. new unit tests; container: plain HTTP 403, TLS-forwarded 200 |

The time between the D-8 question (00:26Z) and the answer (≈06:16Z) is
recorded as a human intervention in `run-log.json`, not as fix-loop time.

## 6. Escalations accounted for

D-1 … D-5 (requirement ambiguities, agent defaults pending review), D-6
(human inputs — resolved by the operator), D-7 (Testcontainers ↔ Docker
29 API, configuration-only workaround pending review), D-8 (dependency
CVEs — resolved by the operator), D-9 (Basic-auth residual — agent
default pending review). None was resolved silently; each has an entry in
`docs/decisions-log.md` and in `run-log.json` → `escalations`.

## 7. Not verified / open

- **Real Google reCAPTCHA**: production mode was verified against a local
  siteverify stub only (no keys were provided, D-6).
- **External SMTP delivery**: verified against Mailpit only (D-6).
- **TLS, HSTS, Let's Encrypt and `/api` routing at the external nginx**:
  outside this workspace; the backend's HTTPS detection relies on that
  proxy setting `X-Forwarded-Proto` and on the backend port not being
  publicly reachable (compose publishes it on 127.0.0.1 only).
- **Container base-image OS packages** (`eclipse-temurin:21-jre-alpine`,
  `nginx:1.27-alpine`, `postgres:16-alpine`, Mailpit) were not scanned;
  DoD §3 does not require it, so their vulnerability state is unknown.
- Stryker/PIT numbers measure the unit-level suites only; acceptance and
  integration tests were not included in the mutation runs.
