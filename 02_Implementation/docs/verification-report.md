# Verification Report — Conference Registration System

Phase 5 artefact. Verification was performed after implementation and
tests, by re-reading all original inputs (User Stories, Business Rules,
Form Schema, Acceptance Criteria, Specification, Technical Constraints,
Security Requirements, Deployment Constraints, Tech Stack) and executing
every Definition of Done (DoD) check. Every failure went through
Verify → Fix → Re-verify; each loop is recorded in
`03_Run-Statistics/run-log.json` (`fixLoops`).

> This is a **self-verification** by the development agent. It is not an
> independent external security review.

Environment: Windows 11, Java 21.0.10, Node 24.13, Docker 29.8 (Docker
Desktop), Maven Wrapper 3.9.9. Date: 2026-09-28 (UTC).

## 1. Summary

| DoD area | Result |
| --- | --- |
| 1. Static checks | Pass (after loops 1–3) |
| 2. Tests | Pass — 164 passed, 0 failed |
| 3. Security | Pass — 0 unresolved Critical/High (after loop 4; triage documented) |
| 4. Architecture | Pass — 0 ArchUnit violations, 0 cycles |
| 5. Container execution | Pass — all 9 demonstrations |
| 6. Documentation | Present |
| 7. Experiment record | `run-log.json` present; `run-summary.md` created in Finalization |

Findings: **1 Critical, 1 Major, 7 Minor** — all fixed or dispositioned
(§8). Fix loops: **6**.

## 2. Static checks (DoD 1)

| Check | Command | Final result |
| --- | --- | --- |
| Backend formatting | `./mvnw spotless:check` | BUILD SUCCESS (failed before loop 1: VF-01) |
| SpotBugs | `./mvnw compile spotbugs:spotbugs` | Executed (could not execute before loop 2: VF-02). 36 bugs, all priority 2 (Medium): 15 `EI_EXPOSE_REP`, 17 `EI_EXPOSE_REP2`, 4 `CT_CONSTRUCTOR_THROW` — VF-03 |
| PMD | `./mvnw pmd:pmd` (PMD 7.7.0, plugin default rules) | Executed. 2 violations, priority 3: `TooManyStaticImports` in `ExternalRegistrationRequest`, `StudentRegistrationRequest` |
| Backend duplication | `./mvnw pmd:cpd` | Measured: 1 duplication (18 lines / 134 tokens) between the two request DTOs' shared field annotations |
| ESLint | `npm run lint` | 0 errors, 0 warnings on sources (a local, git-ignored `coverage/` report adds 3 warnings when present) |
| TypeScript | `npm run typecheck` | 0 errors |
| Frontend duplication | `npm run duplication` (jscpd) | Measured: 2 clones, 18 lines (1.26 %), both in `App.test.tsx` |
| Frontend formatting | `npm run format` | "All matched files use Prettier code style!" (failed before loop 3: VF-04) |

## 3. Tests (DoD 2)

| Suite | Scope | Result | Coverage |
| --- | --- | --- | --- |
| Backend unit (`-Dtest='!*IntegrationTest*'`) | Service, adapters, filters, DTO validation, exception handler, ArchUnit (7 rules), vulnerable-feature guard (3) | **69 passed, 0 failed** | JaCoCo: instructions 78.1 %, branches 77.7 %, lines 75.3 % |
| Backend integration (`-Dtest='*IntegrationTest*'`, Testcontainers PostgreSQL 16) | Full Spring context, Flyway, HTTP → DB → backup → mail | **64 passed, 0 failed** | JaCoCo: instructions 89.9 %, branches 68.6 %, lines 88.2 % |
| — of which API/contract (MockMvc) | `RegistrationApiIntegrationTest` (60), `BackupFailureIntegrationTest` (1), `RateLimitIntegrationTest` (2) | **63 passed, 0 failed** | (included above) |
| Frontend component/unit (Vitest + RTL) | `App.test.tsx` (15), `validation.test.ts` (11) | **26 passed, 0 failed** | v8: statements 92.74 %, branches 88.98 %, functions 86.95 %, lines 92.74 % |
| E2E (Playwright, Chromium) | Browser → Vite proxy → backend container (captcha test mode) → PostgreSQL → Mailpit | **5 passed, 0 failed** | n/a |
| **Total (final complete run)** | | **164 passed, 0 failed** | |

Coverage was measured on separate unit and integration runs (JaCoCo
reports copied to `backend/target/jacoco-unit` and `target/jacoco-it`).

## 4. Security (DoD 3)

| Tool | Configuration | Final result |
| --- | --- | --- |
| Semgrep 1.177.0 (Docker `semgrep/semgrep`) | Frozen `.semgrep.yml` (contains no rules) | 0 findings |
| Semgrep | Additionally `p/default` registry ruleset (398 rules, 93 files) | 2 WARNING (`detect-non-literal-regexp`) in test code (`e2e/registration.spec.ts:18`, `src/App.test.tsx:14`): RegExp built from constant field labels, not reachable by user input — false positives, no action. 0 ERROR/High |
| OWASP Dependency-Check 12.1.0 | NVD data updated during the run | Initial: 22 Critical / 23 High (VF-06). Final: **0 Critical, 0 High** active; 24 Medium + 2 Low active (VF-09); 14 CVEs (30 jar matches) suppressed with justification in `backend/dependency-check-suppressions.xml` |
| npm audit | all dependencies | 0 Critical, 0 High, 3 Moderate — all in dev-only Vitest (VF-05); production dependencies (`--omit=dev`): 0 |

### 4.1 Triage of Spring Framework / Spring Security CVEs (VF-06)

Spring Boot 3.5.16 is the newest Spring Boot 3.x release and manages
Spring Framework 6.2.19 and Spring Security 6.5.11. The fixed versions
(Framework 6.2.20, Security 6.5.12) are not published as open-source
artifacts on Maven Central; the next OSS releases are Spring Framework
7 / Security 7, which require Spring Boot 4 — outside the frozen
technology stack ("Spring Boot 3.x"). Tomcat (→ 10.1.60) and pgjdbc
(→ 42.7.13) were upgraded to fixed releases.

The remaining CVEs were checked one by one against this application:

| CVE | Affected feature | Why not applicable here |
| --- | --- | --- |
| CVE-2026-47884 | XsltView view rendering | REST/JSON only; no views or ViewResolver |
| CVE-2026-47890, CVE-2026-59313 | Server-Sent Events (view fragments / functional MVC) | No SSE, no functional endpoints |
| CVE-2026-47891, -47892, -47885, -47889, -47893 | WebFlux (Aalto XML, functional endpoints, multipart parts, Jetty 12 adapter, WebSocket) | Spring MVC on Tomcat; WebFlux, Jetty, WebSocket not on the classpath |
| CVE-2026-59283, CVE-2026-47886 | SpEL evaluation | No SpEL expressions evaluated, none from user input |
| CVE-2026-47888 | RSocket | Not on the classpath |
| CVE-2026-59282 | Data binding of user-supplied property paths | Request bodies are Jackson-deserialized records; no `@ModelAttribute`/parameter binding onto objects |
| CVE-2026-59270, -41707, -47841 | Spring Security embedded LDAP server, DPoP, WebAuthn | HTTP Basic with in-memory user only; LDAP/OAuth2 modules absent |

These preconditions are enforced by `VulnerableFeatureGuardTest`
(ArchUnit usage rules + classpath checks), so adding one of these
features breaks the build and forces re-evaluation. Residual risk: the
triage relies on the CVE descriptions; upgrading to a fixed release
remains recommended as soon as one is available for the stack.

## 5. Architecture (DoD 4)

`ArchitectureTest` implements the seven rules declared in specification
§2.1 (domain independent; service depends only on domain; api not on
infrastructure/security; infrastructure not on api/security; controllers
only in `api`; entities only in `domain`; slices free of cycles).

Result: **7/7 rules pass — 0 architecture violations, 0 layer
violations, 0 dependency cycles.** The scaffold supplied no layering
rules; all rules were written for the declared architecture.

## 6. Container execution (DoD 5)

`./mvnw -DskipTests package && docker compose up -d --build` — services
`postgres` (healthy), `smtp` (Mailpit, healthy), `backend` (healthy,
Tomcat 10.1.60 / pgjdbc 42.7.13 verified inside the jar), `frontend`
(nginx). Requests went through the frontend nginx (`http://localhost/api/...`).

| # | Demonstration | Evidence (final run, 2026-09-28 22:39 UTC) |
| --- | --- | --- |
| 1 | External registration | `POST /api/registrations/external` → 201, id `f0735f48-d69e-41fd-a7a7-22e7927add1b` |
| 2 | Student registration | `POST /api/registrations/student` → 201, id `4a80c8ca-b55e-4530-9800-6afe9c66a37f` |
| 3 | Persistence in PostgreSQL | `psql`: `EXTERNAL|Ana|Novak|Institut Jožef Stefan`, `STUDENT|Žiga|Čeh|63210001` (+ `registration_option` rows) |
| 4 | Raw JSON backup | `/app/data/registrations/20260928T223914Z_f0735f48-….json`, `20260928T223915Z_4a80c8ca-….json` on the `registration_backups` volume (files owned by non-root `app` user) |
| 5 | Participant email in local SMTP | Mailpit: "Conference registration confirmed" to `dod.ext.…@example.si` and `dod.stu.…@student.uni-lj.si` |
| 6 | Organizer email in local SMTP | Mailpit: "New conference registration (EXTERNAL)" / "(STUDENT)" to `organizers@conference.local` |
| 7 | Organizer JSON attachment | `registration-<id>.json` (`application/json`) on both notifications; parsed `registrationId` matches |
| 8 | Excel export | `GET /api/organizer/registrations.xlsx` with Basic auth → 200, XLSX containing both new ids; without credentials → 401 |
| 9 | Health/readiness | `/actuator/health/readiness` → `{"status":"UP"}` 200; liveness 200; Docker healthcheck `healthy` |

The same checks were also run before the fix loops (22:29 UTC) with the
same outcome. Playwright E2E (5/5) ran against the rebuilt backend
container. Persistence across container recreation: the stack was
recreated (`up --build`) several times during verification and earlier
registrations remained in PostgreSQL and on the backup volume.

## 7. Requirement inspection

All Acceptance Criteria were re-checked against the implementation and
test evidence (traceability in `docs/test-strategy.md` §5): 42/42 ACs
are covered by at least one passing automated test; the container
demonstrations additionally cover AC-001-01, AC-002-01, AC-005-01,
AC-006-01, AC-007-01 and AC-008-01/04 at runtime.

Technical constraints: REST communication ✔; confirmation only after
201 ✔; DB + raw JSON persistence ✔; participant email, organizer email
with submitted data and JSON attachment ✔; organizer-only Excel export ✔;
options configurable without schema change ✔; health/readiness ✔;
architecture defined and justified ✔.

Security requirements (SECURITY_REQUIREMENTS list): backend validation ✔;
frontend validation ✔; malicious input (control chars, unknown JSON
properties, option-id validation, parameterized SQL) ✔; automated
submissions (reCAPTCHA v2 + rate limit) ✔; backend reCAPTCHA
verification with deterministic test mode, off by default ✔; rate
limiting ✔; secure DB access ✔; output encoding (React, JSON, plain-text
mail, string cells + formula neutralisation) ✔; security headers
(backend + nginx) ✔; request-size limits ✔; secure error handling ✔
(VF-07 fixed); secret/configuration management ✔ (VF-08 fixed);
dependency vulnerabilities ✔ (VF-06); safe email content ✔; no
personal-data logging ✔ (container logs checked: no names/emails);
option-identifier validation ✔.

Unrequested functionality: none beyond small UX helpers directly serving
the stories ("Register another participant" button after confirmation).

Known limitations / unverified behaviour (documented, not assumed):

- Production reCAPTCHA against Google was verified only with a mocked
  siteverify endpoint (DoD forbids live Google calls).
- External production SMTP and the external nginx/TLS proxy were not
  available; only local Mailpit and the compose nginx were exercised.
- Emails are sent synchronously without a retry queue; if SMTP is down
  the registration is still accepted and the failure is logged.
- Option configuration changes take effect after a backend restart.
- Rate limiting is in-memory per backend instance.

## 8. Findings and fix loops

| ID | Severity | Finding | Disposition | Loop |
| --- | --- | --- | --- | --- |
| VF-01 | Minor | `spotless:check` failed on `RestartAndReconfigurationIntegrationTest.java` | Fixed (`spotless:apply`); check passes | 1 |
| VF-02 | Major | SpotBugs could not execute: scaffold pins `spotbugs-maven-plugin` 10.12.15, which does not exist on Maven Central (404) | Fixed: version 4.10.4.1 (latest release) in the copied `pom.xml`; SpotBugs runs | 2 |
| VF-03 | Minor | SpotBugs: 36 priority-2 findings (`EI_EXPOSE_REP/REP2` on records and injected Spring beans; `CT_CONSTRUCTOR_THROW` on fail-fast startup constructors) | Accepted: records hold immutable copies or Spring singletons; constructor exceptions are intentional fail-fast configuration checks on Spring-managed beans | — |
| VF-04 | Minor | Frontend formatting check failed on 11 files (CRLF in scaffold config files on Windows checkout; generated `dist/`, `test-results/` checked) | Fixed: `.prettierignore`, `.gitattributes` (`eol=lf`), normalised | 3 |
| VF-05 | Minor | npm audit: 3 Moderate in dev-only Vitest 3.x (path traversal in `@vitest/mocker`, fix only in Vitest 4+ — major upgrade of frozen test tooling) | Accepted: not shipped to production; production audit clean | — |
| VF-06 | Critical | Dependency-Check: 22 Critical / 23 High CVEs | Fixed/triaged: Tomcat 10.1.60, pgjdbc 42.7.13; 14 Spring CVEs triaged not-applicable with justification and enforced by `VulnerableFeatureGuardTest`; 0 Critical/High remain | 4 |
| VF-07 | Minor | Unexpected-error logging included exception messages that may echo personal data | Fixed: ERROR log contains only exception types and code locations | 5 |
| VF-08 | Minor | Production mode started with blank `RECAPTCHA_SITE_KEY`, leaving the form unusable | Fixed: fail-fast at startup | 6 |
| VF-09 | Minor | Dependency-Check residual 24 Medium + 2 Low (e.g. log4j-api via log4j-to-slf4j, jackson-databind, commons-lang3, spring-data-jpa, Tomcat Medium) | Accepted for this release: below the DoD threshold; recommended to track with Spring Boot patch releases | — |

Loop details (trigger, change, start/end) are in `run-log.json`.
Regression check after all loops: full backend suite (133), frontend
(26), E2E (5), static checks, Semgrep, npm audit and container
demonstrations were re-executed on the final code (sections 2–6).
