# Verification Report (Phase 5)

* Verification start: 2026-09-24T21:25:56+02:00 (after Phase 4 commit `45e1516`)
* Final re-verification completed: 2026-09-24T21:58:23+02:00
* Environment: Windows 11 Pro, Docker 29.8.0 / Compose v5.5.1, Temurin JDK 21.0.10, Node 24.13.0
  (local tooling; the frontend image builds with `node:22-alpine`), Maven 3.9.16 via wrapper.

> **This is a self-scan, not an independent review.** All checks below were chosen, run and
> interpreted by the same agent that wrote the code. No third party reviewed the implementation
> or the results.

## 0. Inputs re-read

All seven files in `01_Input_Files/` were re-read at the start of verification; the harness
confirmed each was unchanged since the start of the run. `01_Input_Files/` was not modified
during the run.

## 1. Definition of Done — final status

| DoD item | Check | Result |
|---|---|---|
| §1 Lint 0 errors | Backend: Spotless (google-java-format) `spotless:check`; Frontend: ESLint strict-type-checked | **0 / 0 errors** (0 warnings) |
| §1 Type check 0 errors | Frontend `tsc --noEmit`; backend `javac` via Maven compile | **0 / 0** |
| §1 Duplication measured | Backend PMD CPD (min 60 tokens); Frontend jscpd (min 50 tokens) | Backend **1.67 %** (2 clones, 30 of 1 794 lines — the two request DTOs' shared field annotations); Frontend **0.59 %** lines (1 clone, 6 lines) |
| §1 Complexity measured | Backend PMD `CyclomaticComplexity` (107 methods); Frontend ESLint `complexity` (53 functions) | Backend avg **2.01**, max **19** (`OptionCatalog.validate`); Frontend avg **2.11**, max **9** |
| §1 Bug patterns | SpotBugs (effort Max, threshold Low) + find-sec-bugs | **0** after V-04 |
| §1 Architecture | ArchUnit layered rules + cycle check | **0 violations, 0 cycles** |
| §2 Test suite 100 % pass | Backend unit / integration, frontend | **55/55, 38/38, 32/32** |
| §2 Coverage, separate | JaCoCo unit-only vs integration-only; Vitest v8 | Backend unit **72.3 %** lines (71.3 % branches); backend integration **91.0 %** lines (67.2 % branches); frontend unit **96.5 %** lines (85.1 % branches) |
| §3 Security scanner | Semgrep (`p/security-audit`, `p/java`, `p/typescript`, `p/react`, `p/secrets`, `p/dockerfile`, `p/nginx`; 237 rules, 91 files) | **0 findings** after V-05 |
| §3 Dependency audit | `npm audit` (all + prod); Trivy image scan of both built images (OS packages + `app.jar`) — OWASP Dependency-Check could not run, see V-03 | npm: **0**; Trivy backend: **0**; Trivy frontend: **0** (all severities) after V-06 |
| §3 0 unresolved Critical/High | — | **Met** (all Critical/High from V-06 fixed and re-scanned) |
| §4 Build containers | `docker compose ... up -d --build` | Built and healthy |
| §4 Run & exercise happy path on running containers | `scripts/smoke-test.mjs` (HTTP through Nginx container) + real browser (in-app Chromium) | **50/50 checks**; ≥1 external + ≥1 student registration per run; UI registration in browser succeeded |
| §4 Health/readiness endpoints | `/actuator/health`, `/liveness`, `/readiness` on the running backend container | all **200 UP** |
| §5 RELEASE_NOTES.md | `02_Implementation/RELEASE_NOTES.md` | present, derived from implementation |
| §5 Open items recorded | §5 below and release notes | recorded |
| §6 Experimental record | `03_Run-Statistics/run-log.json`, `run-summary.md` | written at merge |

## 2. Container execution evidence (DoD §4)

Stack: `docker compose -f docker-compose.yml -f docker-compose.verify.yml up -d --build`
(db `postgres:16`, backend, frontend, Mailpit SMTP catcher). Smoke test runs:

| Run | Time (+02:00) | Images | Result |
|---|---|---|---|
| loop 1 | 21:43:29 | before V-06 fixes | 50/50 |
| loop 2 | 21:52:36 | after V-06 (rate-limit part skipped) | 46/46 |
| final | 21:57:34 | final images | **50/50** |

What the smoke test verifies on the running containers: SPA + SPA-route fallback + assets with
frontend security headers (single CSP); health/liveness/readiness UP; `actuator/env` not
exposed; options per variant (inactive/audience filtering); too-fast submission rejected;
external and student registrations → 201 with API security headers; token replay rejected;
invalid input → 400 with field errors; malformed JSON → 400 without internals; 20 KB body → 413;
backup JSON file per registration with `0600` permissions and correct content; participant
confirmation (UTF-8 plain text, Slovenian characters) and organizer notification to both
organizers with `registration-<id>.json` attachment in Mailpit; export 401 without / with wrong
credentials and a valid `.xlsx` (`PK` signature, correct content type) with credentials; rate
limit enforced through Nginx even with spoofed `X-Forwarded-For`, 429 carries `Retry-After` and
security headers.

Additional manual checks on the running stack:

* **AC-005-05 persistence**: `docker compose down` + `up` (containers recreated, volumes kept):
  6 rows / 6 backup files before and after; UTF-8 values intact.
* **AC-PC-08 responsive**: browser at 375×812 (mobile emulation): no horizontal overflow
  (`scrollWidth` 375), inputs 301 px wide, submit button 44 px high; at 1280 px no overflow,
  centred 40 rem column.
* **UI happy path in a real browser** (mobile viewport): invalid email + missing consent blocked
  client-side with messages and focus on the first invalid field; after correction a
  registration (`b835ccfe-…`, "Nuša Žagar", 2 options) was confirmed in the UI only after the 201,
  and verified in PostgreSQL, the backup volume and Mailpit (2 mails, attachment on organizer
  mail). Browser console: no errors or CSP violations.
* Both application containers run as non-root (`uid=10001(app)`, `uid=101(nginx)`).

## 3. Findings

Severity scale: Critical / High / Medium / Low. "Loop" = fix loop in `run-log.json`.

| ID | Severity | Source | Affected US/AC | Finding | Fix | Re-verification |
|---|---|---|---|---|---|---|
| T1 | Medium | Phase 4 first test run | US-005, AC-005-02 | `BackupWriter` silently overwrote an existing backup (ATOMIC_MOVE replaces target) | Refuse to overwrite; `BackupFailedException` | `BackupWriterTest` passes (Phase 4, loop 1) |
| T3–T5 | Low | Phase 4 first test run | US-008, AC-008-01 | `.xlsx` response had `;charset=UTF-8` appended (forced response encoding) | Force request encoding only | `ExportIT` passes (Phase 4, loop 1) |
| V-01 | Low | ESLint | — (test code) | 2 errors in test helpers (`no-non-null-assertion`, `no-base-to-string`) | Test code rewritten without `!` and with `String()`/`Request.url` | ESLint 0 errors, 32/32 tests (loop 2) |
| V-02 | Low | Prettier | — | one test file not formatted | `prettier --write` | `prettier --check` clean (loop 2) |
| V-03 | Medium | OWASP Dependency-Check | PC §Validation and security | Plugin configuration passed an empty `nvdApiKey`; after switching to `nvdApiKeyEnvironmentVariable` the NVD client still refuses keyless updates. An NVD key requires account registration, which the agent must not do. | Config fixed; **substituted Trivy** (image scan incl. `app.jar`) + `npm audit` as dependency audit | Trivy 0 findings; **open item**: run `NVD_API_KEY=… ./mvnw dependency-check:check` (loop 3) |
| V-04 | Medium | SpotBugs + find-sec-bugs (16 bug instances) | PC §Validation and security | 5× `CT_CONSTRUCTOR_THROW` (Medium), 1× `HRS_REQUEST_PARAMETER_TO_HTTP_HEADER` (Medium) + `SERVLET_HEADER` (Low) in `RequestIdFilter`, 8× `CRLF_INJECTION_LOGS` (Low), 1× `IMPROPER_UNICODE` (Low) | `BackupWriter`, `FormTokenService`, `OptionCatalog` made `final`; `RequestIdFilter` no longer echoes the client header (always server-generated id). `CRLF_INJECTION_LOGS` / `IMPROPER_UNICODE` excluded as false positives with written justification in `spotbugs-exclude.xml` (only server-generated values are logged; NFC normalisation precedes validation) | SpotBugs 0 (loop 3) |
| V-05 | Medium | Semgrep `request-host-used` | PC §Validation and security | Nginx forwarded the client `Host` header to the backend | Removed `proxy_set_header Host $host` (default `$proxy_host`) | Semgrep 0 findings (loop 4) |
| V-06 | **Critical** | Trivy image scan | PC §Validation and security, §Deployment | Backend `app.jar`: tomcat-embed-core 10.1.55 (3× CRITICAL incl. CVE-2026-65182 security-constraint bypass), postgresql 42.7.11 (HIGH CVE-2026-54291), jackson-databind 2.21.4, commons-lang3 3.17.0, log4j-api 2.24.3 (MEDIUM); backend Alpine libexpat (HIGH). Frontend image `nginx-unprivileged:1.27-alpine` (Alpine 3.21.3): 2 CRITICAL, 36 HIGH OS packages | Pinned `tomcat.version` 10.1.60, `postgresql.version` 42.7.12, `jackson-bom.version` 2.21.5, `commons-lang3.version` 3.18.0, `log4j2.version` 2.25.5; runtime stages `apk upgrade`; frontend base → `nginx-unprivileged:1.29-alpine` | Trivy: backend 0, frontend 0 (all severities); backend 55/55 + 38/38 tests, smoke 50/50 on rebuilt images (loop 5) |

No finding was left unresolved at Critical or High severity.

### Unrequested functionality (per ACCEPTANCE_CRITERIA_RULES)

* `X-Request-Id` correlation header and log MDC — operational support, not traceable to a user
  story; kept because it carries no user data and aids incident analysis.
* `docker-compose.verify.yml` with Mailpit — verification tooling only; not part of the
  production compose file.

No other functionality beyond US-001…US-008 / PROJECT_CONSTRAINTS was added.

## 4. Fix loops (Verification → Fix → Verification)

| Loop | Trigger (observed) | Change | Closed |
|---|---|---|---|
| 1 | Phase 4 first complete test run 21:15–21:16 (T1–T8) | BackupWriter overwrite guard; encoding config; test isolation fixes | 21:23:43 (all suites green) |
| 2 | Frontend ESLint/Prettier run ≈21:27 (V-01, V-02) | test helper code | ≈21:30 (eslint/prettier/tsc/vitest green; `fetchMock.ts` saved 21:29:36) |
| 3 | Dependency-Check failure 21:26:53 / 21:29:15 (V-03); SpotBugs failure 21:31:53 (V-04) | pom config; final classes; RequestIdFilter; justified exclusions | 21:34:18 (static analysis BUILD SUCCESS); V-03 remains an open item with substitute audit |
| 4 | Semgrep 21:37:00 (V-05) | nginx.conf | 21:53:35 (Semgrep final 0 findings) |
| 5 | Trivy 21:43:10 (V-06) | version overrides, base image, `apk upgrade` | 21:51:19 (Trivy clean); regression 21:53:22 (tests) and 21:57:41 (smoke 50/50) |

Raw outputs: `03_Run-Statistics/raw/` (test, static-analysis, Trivy, Semgrep and smoke logs).

## 5. Open items (not demonstrated / residual risk)

1. **No live mail server**: delivery was demonstrated only to GreenMail (tests) and Mailpit
   (container run). Real SMTP delivery, TLS/auth against a provider, SPF/DKIM/DMARC
   deliverability were not demonstrated.
2. **OWASP Dependency-Check not executed** (NVD API key required, V-03). Trivy + npm audit were
   used instead; running Dependency-Check with a key is recommended before production.
3. **Email is not retried**: a failed confirmation/notification is logged (registration id only)
   but not re-sent; the JSON backup and DB row remain the record. An outbox/retry is not built.
4. **TLS termination** is expected in front of the frontend container (ingress/reverse proxy);
   the containers themselves serve plain HTTP. HSTS is therefore not emitted by these containers.
5. **Single-instance anti-automation state**: rate-limit counters and used form-token nonces
   are in memory; they reset on restart and are not shared across replicas.
6. The `.xlsx` export was validated by parsing with Apache POI and by file signature; it was not
   opened in Microsoft Excel.
7. Responsive behaviour was checked in one Chromium-based browser at 375 px and 1280 px only.
8. Frontend tooling ran locally on Node 24.13.0 (TECH_STACK: Node 22 LTS); the shipped image is
   built on `node:22-alpine`.
