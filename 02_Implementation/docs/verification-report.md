# Verification Report — Conference Registration System

Phase 5 artefact. This is a **self-verification** by the development
agent against the original inputs and the Definition of Done. It is not
an independent external review, and in particular not an independent
security review.

Verification window: 2026-09-26 17:33 – see run log for exact phase
timestamps. Raw evidence (tool output, exported workbook, screenshots) is
stored under `03_Run-Statistics/raw/`.

## 1. Summary

| DoD area | Result |
| --- | --- |
| 1. Static checks | Pass (after fix loop 5); SpotBugs executed with documented exception V-01 |
| 2. Tests | Pass — final complete run 140 passed / 0 failed (on Spring Boot 3.5.16) |
| 3. Security | **Not met — documented exception.** Semgrep and npm audit: 0 Critical/High. OWASP Dependency-Check: 22 Critical / 27 High (per jar) remain in Spring/Tomcat/log4j/Angus after upgrading to the newest Spring Boot 3.x (V-09, §4.3) |
| 4. Architecture | Pass — 7 ArchUnit rules incl. cycle check, 0 violations |
| 5. Container execution | Pass — all 9 demonstrations plus restart persistence and configuration change |
| 6. Documentation | All required documents present |
| 7. Experiment record | `run-log.json` and `run-summary.md` (finalized in Phase 6) |

Findings: **1 Critical, 0 Major, 8 Minor** (§8). Four Minor findings were
fixed through Verify → Fix → Re-verify loops (loops 5–8 in the run log);
the Critical finding V-09 was reduced but not eliminated in loop 9 after a
human decision and remains open as a documented exception; the other
Minor findings are accepted with justification or cannot be fixed without
modifying frozen inputs.

Dependency change during verification: the backend parent was upgraded
from the scaffold's Spring Boot 3.4.4 to 3.5.16 (still "Spring Boot 3.x")
on the human's instruction. All static checks, the complete test suite
and the container demonstrations were repeated on 3.5.16.

## 2. Requirements re-read and review

All inputs were re-read: `USER_STORIES.md`, `BUSINESS_RULES.md`,
`FORM_SCHEMA.md`, `docs/acceptance-criteria.md`, `docs/specification.md`,
`TECH_STACK.md`, `TECHNICAL_CONSTRAINTS.md`, `SECURITY_REQUIREMENTS.md`,
`DEPLOYMENT_CONSTRAINTS.md`.

| Requirement | Evidence | Status |
| --- | --- | --- |
| US-001 external registration (fixed fields, options, consents) | API/component/E2E tests; container demo 1 | Met |
| US-002 student registration | API/component/E2E tests; container demo 2 | Met |
| US-003 configurable options (workshops, events, meals, other; id/name/status; only active selectable; no admin UI) | Catalog/validator tests; runtime check with a mounted external `conference.yml` (new option shown, deactivated option hidden, consent label changed, no code change) | Met |
| US-004 confirmation only after successful processing | Component tests (pending/201/400/500); storage-failure integration test | Met |
| US-005 reliable storage (DB + JSON backup, survives restart) | Integration tests; container demos 3–4; `docker compose down` + `up` kept 2/2 registrations and 2/2 backup files | Met |
| US-006 participant email | Integration/unit tests; Mailpit shows both participant emails | Met |
| US-007 organizer notification with submitted data + raw JSON attachment | Unit/integration tests; Mailpit shows organizer emails with `registration-<id>.json` (`application/json`) | Met |
| US-008 Excel export, organizer-only | Integration tests; container demo 8 (200 with credentials, 401 without) | Met |
| FS general data rules (required, email, Unicode, whitespace, unknown/inactive options) | Validator tests (incl. Unicode whitespace after fix loop 3); DB/JSON/export/email contain `Žiga Šušteršič`, `Čeč`, `—` unchanged | Met |
| FS consent mandatory, not preselected | Component/E2E tests | Met |
| TC REST, confirmation after backend success | Frontend uses REST only; confirmation only on 201 | Met |
| TC runtime health | `/actuator/health`, `/liveness`, `/readiness` = `UP`; Compose healthcheck | Met |
| TC architecture defined and justified | Specification §2; ArchUnit | Met |
| Tech stack frozen (Java 21, Spring Boot 3.4, PostgreSQL 16, Flyway, React/TS/Vite, reCAPTCHA v2) | `pom.xml`, `package.json` unchanged apart from documented additions | Met |
| reCAPTCHA test mode only via environment; never default in production | `app.recaptcha.test-mode` defaults to `false`; startup fails in production mode without secret; unit tests | Met |
| Deployment: env-based secrets, persistent volumes, HTTP inside containers | Compose volumes `postgres_data`, `registration_backups`; all secrets via env | Met |

Unrequested functionality check: no accounts, payment, editing, admin UI
or dashboard exist (AC-001-14). Additions beyond the stories are the
security controls required by `SECURITY_REQUIREMENTS.md` (rate limiting,
size limit, headers, CORS switch) and the registration id in the
confirmation; none adds business behaviour.

Not verifiable in this environment (documented, not assumed):

- Production-mode reCAPTCHA against Google's live service (tests use a
  local HTTP stub; automated checks must not call Google). The widget
  script loading is covered by the CSP allowing Google's reCAPTCHA hosts,
  but the live widget was not rendered.
- External SMTP with authentication/STARTTLS (configuration only).
- The external production nginx / Let's Encrypt setup (outside the
  application containers).

## 3. Static checks (DoD §1)

| Check | Command | Result |
| --- | --- | --- |
| Backend formatting | `./mvnw spotless:check` | Pass |
| SpotBugs | `./mvnw compile com.github.spotbugs:spotbugs-maven-plugin:4.10.4.1:check` | Executed; 24 Medium-rank patterns, 0 High (V-01, V-02); unchanged after the upgrade |
| PMD | `./mvnw pmd:check` (frozen default ruleset) | 0 violations |
| PMD CPD | `./mvnw pmd:cpd-check` | 0 duplications |
| ESLint | `npm run lint` | 0 errors, 0 warnings |
| TypeScript | `npm run typecheck` | 0 errors |
| jscpd | `npm run duplication` | 0 clones (0 %) |
| Prettier | `npm run format` | Pass after fix loop 5 (V-03) |

## 4. Security (DoD §3)

### 4.1 Semgrep
Run from the `semgrep/semgrep` Docker image (not installed locally) with
the frozen `.semgrep.yml` (empty rule list) plus the registry rulesets
`p/default`, `p/owasp-top-ten`, `p/secrets` — 408 rules, 96 files.

- Initial: 2 findings, both `WARNING` (medium): V-04 (fixed, loop 6) and
  V-05 (false positive, test code). 0 Critical/High.
- Re-verification of `frontend/nginx.conf` after the fix: 0 findings.

### 4.2 npm audit
`npm audit`: 0 critical, 0 high, 3 moderate, 0 low. `npm audit --omit=dev`:
**0 vulnerabilities**. The 3 moderate advisories are in the Vitest
toolchain (`vitest`, `@vitest/mocker`, `@vitest/coverage-v8`,
dev-only, versions from the frozen scaffold lockfile) — V-06.

### 4.3 OWASP Dependency-Check
`./mvnw org.owasp:dependency-check-maven:12.1.0:check` (frozen plugin
version), NVD data downloaded 2026-09-26 without an API key. The Sonatype
OSS Index analyzer now requires credentials (HTTP 401) and was disabled
(`-DossindexAnalyzerEnabled=false`); all other analyzers ran.

| Build | Critical | High | Medium | Low | Counting |
| --- | --- | --- | --- | --- | --- |
| Frozen scaffold parent Spring Boot 3.4.4 | 35 | 77 | 77 | 10 | per affected jar |
| After upgrade to Spring Boot 3.5.16 (fix loop 9) | 22 | 27 | 25 | 2 | per affected jar |
| After upgrade, unique CVE ids | 15 | 19 | 19 | 1 | unique CVE |

The upgrade (decided by the human, see run log `humanInterventions`)
removed all Critical/High matches for `jackson-databind`, `postgresql`,
`jakarta.mail`/Angus Mail and the Spring Boot jars. **Remaining
Critical/High (unresolved, V-09):** `tomcat-embed-core` 10.1.55
(8 Critical, 6 High), `spring-core` / `spring-web` 6.2.19 (6 Critical,
6 High each), `spring-security-core` / `-web` 6.5.11 (1 Critical, 2 High
each), `log4j-api` 2.24.3 (4 High), `angus-activation` 2.0.3 (1 High).
Spring Boot 3.5.16 is the newest 3.x release; the remaining fixes are
only available on the 4.x line, which `TECH_STACK.md` ("Spring Boot 3.x")
does not permit. The findings were not individually triaged for
exploitability in this application (no false-positive suppression was
applied). Reports: `raw/owasp-dependency-check-report.{json,html}`.

### 4.4 Security controls verified at runtime
- Export: 401 without credentials and with wrong credentials; no data in
  the 401 body.
- Headers: page responses carry the frontend CSP (allowing only self and
  Google reCAPTCHA hosts), `nosniff`, `DENY`, referrer and permissions
  policies; `/api` responses carry the backend policy
  (`default-src 'none'`, `no-referrer`, `no-store`) — after fix loop 7
  without duplicates (V-07).
- Logs: after registrations, backend logs contain only registration
  UUID and type; no names, emails or student IDs.
- Invalid submission through the proxy → 400; nothing stored.
- The production build renders under its CSP (screenshot
  `raw/container-registration-form.png`).

## 5. Architecture (DoD §4)

`ArchitectureTest` (7 rules, written for Specification §2.2): layered
access table, web ↛ persistence/infrastructure, application ↛
web/infrastructure, domain independent, config → domain only, **no
package cycles** (`slices().beFreeOfCycles()`), controllers only in
`web`. Result: **0 violations**. No layering rules were inherited from
the scaffold.

## 6. Tests (DoD §2)

### 6.1 Final complete test run (2026-09-26 18:31:58 – 18:32:46, Spring Boot 3.5.16)

An identical result (140/0) was obtained on Spring Boot 3.4.4 at
17:51:26 – 17:52:11, before fix loop 9.

| Category | Passed | Failed |
| --- | --- | --- |
| Backend unit (incl. 7 ArchUnit) | 75 | 0 |
| Backend integration / API (MockMvc + Testcontainers PostgreSQL) | 25 | 0 |
| Frontend unit + component (Vitest/RTL) | 34 | 0 |
| E2E (Playwright, Chromium) | 6 | 0 |
| **Total** | **140** | **0** |

The API/contract tests are the MockMvc integration tests
(`RegistrationApiIntegrationTest`, `ExportApiIntegrationTest`,
`RateLimitIntegrationTest`, `StorageFailureIntegrationTest`); they are
counted once, in the integration row.

### 6.2 Coverage

| Scope | Tool | Line | Instruction | Branch |
| --- | --- | --- | --- | --- |
| Backend unit tests only | JaCoCo | 69.5 % | 69.9 % | 61.4 % |
| Backend integration tests only | JaCoCo | 88.7 % | 89.9 % | 64.7 % |
| Frontend (Vitest) | v8 | 82.4 % (statements) | — | 93.2 % |

Separate backend figures were produced by running
`-Dtest=!*IntegrationTest` and `-Dtest=*IntegrationTest` with a fresh
`jacoco.exec` each; CSVs in `raw/jacoco-unit.csv`, `raw/jacoco-integration.csv`.
These are self-measured values, measured before the Spring Boot upgrade
(application and test code unchanged by the upgrade); the authoritative
coverage metrics are filled by the external audit.

### 6.3 Test-environment incident during verification
The first attempts of the final E2E run (17:45 and 17:47) failed 6/6:
the long-running Vite dev server had re-optimised its dependencies
(17:46) and afterwards answered `/api/*` with the SPA `index.html`
instead of proxying (evidence: `text/html` response without backend
headers). Restarting the dev server restored the proxy; no code was
changed. Logged as fix loop 8 / finding V-08.

## 7. Container execution (DoD §5)

Fresh `docker compose down -v && docker compose up -d --build` from the
current code, then against `http://localhost` (frontend nginx → backend):

| # | Demonstration | Result |
| --- | --- | --- |
| 1 | External registration | 201, id `60a93ec1-…` |
| 2 | Student registration | 201, id `7c0274d6-…` |
| 3 | Persistence in PostgreSQL | 2 rows in `registration`, 4 in `registration_option`, 2 in `registration_consent`; Unicode intact |
| 4 | Raw JSON backup | 2 files in `/app/data/registrations` (named volume), full data, no token |
| 5 | Participant email in local SMTP | Mailpit: 2 × "Conference registration confirmation" to the participants, UTF-8 (`=C5=BD`, `=C4=8D` present) |
| 6 | Organizer email in local SMTP | Mailpit: "New conference registration (External participant)" and "(Student)" to `organizer@conference.local` with all submitted data |
| 7 | Organizer JSON attachment | 1 attachment each, `registration-<id>.json`, `application/json` |
| 8 | Excel export | 200, xlsx content type, `attachment; filename="registrations-20260926-154309.xlsx"`, 4170 bytes; shared strings contain `Šušteršič`, `Čeč`, `63219876`; 401 without credentials |
| 9 | Health/readiness | `/actuator/health/readiness` `{"status":"UP"}`, liveness `UP`, health `UP` |
| + | Restart persistence (AC-005-02) | `docker compose down` + `up`: 2 registrations and 2 backup files still present |
| + | Configuration change (AC-003-03) | Same image with mounted external `conference.yml`: new option offered, deactivated option hidden |

Evidence: `raw/container-verification.txt`, `raw/dod-export.xlsx`,
`raw/container-registration-form.png`, `raw/ac-003-03/conference.yml`.

Repeated after the Spring Boot 3.5.16 upgrade on fresh volumes
(`raw/container-verification-final.txt`): demonstrations 1–9 passed
again (201/201, 2 DB rows with intact Unicode, 2 backup files, 2
participant + 2 organizer emails with one JSON attachment each, export
200 / 401 unauthenticated, health/readiness/liveness `UP`), restart
persistence 2/2, `/api` headers single backend set, and no personal data
in the backend log after a further registration.

## 8. Findings

| ID | Severity | Area | Finding | Resolution |
| --- | --- | --- | --- | --- |
| V-01 | Minor | Static analysis / frozen scaffold | The scaffold `pom.xml` pins `spotbugs-maven-plugin` 10.12.15, which does not exist on Maven Central, so `spotbugs:*` cannot resolve | Not fixable without modifying frozen tooling configuration; SpotBugs executed with explicit coordinates 4.10.4.1 (latest release). Documented exception |
| V-02 | Minor | SpotBugs | 24 Medium-rank patterns: 21 × `EI_EXPOSE_REP/REP2` (Spring-injected collaborators, records holding lists), 3 × `CT_CONSTRUCTOR_THROW` (intentional fail-fast configuration validation) | Accepted: no mutable state is shared across trust boundaries; beans are singletons wired by Spring; constructor exceptions abort startup by design |
| V-03 | Minor | Formatting | `npm run format` failed: generated `dist/`, `test-results/` not ignored, and `core.autocrlf=true` checked out LF files as CRLF | Fixed (loop 5): `.prettierignore`, `.gitattributes` (`eol=lf`, CRLF for `.cmd`), working copy re-normalised; Prettier and Spotless pass |
| V-04 | Minor | Security (Semgrep) | `frontend/nginx.conf` forwarded the client `Host` header to the backend (`request-host-used`) | Fixed (loop 6): header no longer forwarded; Semgrep 0 findings; registration through proxy still 201 |
| V-05 | Minor | Security (Semgrep) | `detect-non-literal-regexp` in `RegistrationPage.test.tsx` | Accepted: test code, regex built from constant labels |
| V-06 | Minor | Security (npm audit) | 3 moderate advisories in dev-only Vitest packages from the frozen lockfile | Accepted: 0 production vulnerabilities, not Critical/High; upgrading would alter the frozen toolchain |
| V-07 | Minor | Security headers | `/api` responses through the frontend nginx carried duplicated, partly conflicting security headers (two CSPs, two referrer policies) | Fixed (loop 7): nginx headers scoped to `location /`; re-verified single backend header set on `/api` and nginx set on pages |
| V-08 | Minor | Test environment | E2E failed 6/6 because the long-running Vite dev server stopped proxying `/api` after dependency re-optimisation | Fixed (loop 8): dev server restarted, no code change; E2E 6/6 |
| V-09 | Critical | Security (OWASP Dependency-Check) | Critical/High CVEs in the backend dependency tree: 35 Critical / 77 High (per jar) on the frozen Spring Boot 3.4.4; DoD §3 forbids finalization with unresolved Critical/High | Partially fixed (loop 9, human decision): parent upgraded to Spring Boot 3.5.16, full re-verification passed; 22 Critical / 27 High (per jar) remain because fixes exist only outside "Spring Boot 3.x". **Open — documented exception authorized by the human** |

Observations (not findings): unknown API paths are answered with 401 and
a Basic challenge (they are denied as specified, but a browser may show a
login prompt); the development Compose file exposes PostgreSQL on host
port 5432 with development credentials (scaffold default, dev only).

## 9. Documentation (DoD §6)

Present: `docs/acceptance-criteria.md`, `docs/specification.md`,
`docs/test-strategy.md`, `docs/verification-report.md`; `RELEASE_NOTES.md`
is created in Phase 6.
