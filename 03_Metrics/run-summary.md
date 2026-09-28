# Run summary — conference-registration-v3 / single-agent-sdd-tjan

**Verdict: `done`, under a researcher-authorized condition change.** All eight DoD gates pass
on the final revision, and functional completion is complete.

The first report (17:12Z) was `incomplete` because D-05 Security was blocked by 7 Critical and
8 High advisories in `spring-core 6.2.19` and `spring-security-core 6.5.11`. Those have no
public fix for the Spring Boot 3.x line. At 17:17Z the researcher authorized the stack
change. The backend now runs on **Spring Boot 4.1.1** (Spring Framework 7.0.9, Spring Security
7.1.1, Jackson 3.1.5). This is a deliberate deviation from ST-01: comparisons against runs that
kept Boot 3.x must account for it.

Final implementation revision: **`18ec8d4a5d0333c4cd45d14d54fdf1384b100390`**. The previous
Boot-3 final revision was `53b6074` (tagged locally `pre-boot4-checkpoint`). Nothing was pushed.

## 1. Configuration and provenance

| Item | Value (source) |
|---|---|
| Run ID | `conference-registration-v3__single-agent-sdd-tjan__20260928T155535Z` |
| Task / label | `conference-registration-v3` / `single-agent-sdd-tjan` |
| Actors | 1 (agent-1). No sub-agents were spawned. |
| Model / harness | `claude-opus-5-5` (stated by the harness system prompt), Claude Code 2.1.283 CLI (tool: `claude --version`) |
| Sampling, context | Sampling not exposed. The harness summarizes context automatically. The token counter started at 15,000,000. |
| Input hashes | SHA-256 of all seven `01_Input_files/*` in `run.json.identity.inputHashes` (tool) |
| Host | Ubuntu 24.04, kernel 7.0.0, 8 CPUs, 33.5 GB RAM, no cgroup limits (tool) |
| Toolchain | OpenJDK 21.0.12.1, Maven Wrapper 3.3.4 / Maven 3.9.16, Node 22.23.3 + npm 10.9.9, Docker 29.5.0 client / 29.3.1 server, Compose v5.1.1, Semgrep 1.178.0, Dependency-Check 13.0.0, Playwright 1.63.0 (tool, `evidence/preflight.txt`) |
| Environment actions during preflight | Started Docker Desktop, which was stopped. Installed Node 22 with nvm (system Node is 24). Installed Semgrep with `uv tool`. Installed Playwright Chromium. |
| Baseline / final revision | `f67ba6d` → `53b6074` (Boot 3, first report) → **`18ec8d4`** (Boot 4, final). Metrics-only commits follow each. |
| Observability limits | No provider token/cost/call accounting was available. Phase boundaries come from event and check timestamps. Two early repairs were recorded late (see §7). |

## 2. Verdict per gate (final revision `18ec8d4`, suite `final-boot4`, 17:23–17:29Z)

| Gate | Status | Basis |
|---|---|---|
| D-01 Product | pass | US-001…008 demonstrated. The AC→test map is in `02_Implementation/docs/specification.md` §14. |
| D-02 Build/tests | pass | Both production builds pass. Backend: 33 unit and 53 integration tests, 0 failures, 0 errors, 0 skipped. Frontend: 23 component tests. Playwright: 5 journeys + 5 after container recreation + 4 with the changed catalog, 0 skipped. |
| D-03 Static quality | pass | Spotless, Prettier, ESLint (0) and tsc (0) are clean. SpotBugs found 0 bugs, PMD 0 violations, CPD 0 duplications, jscpd 0 clones in 15 files / 1,240 lines. |
| D-04 Architecture | pass | ArchUnit passes 5 rules (module dependency matrix, no cycles, export uses only the query API, controllers are not used by other code, no field injection). One authorized deviation (ST-01); nothing unresolved (§6). |
| D-05 Security | pass (was blocked on Boot 3) | Semgrep: 0 findings, 0 errors. npm audit: 0. Dependency-Check: **0 findings** across 64 dependencies. CPEs were identified for spring-core 7.0.9, spring-security-core 7.1.1, tomcat 11.0.26 and pgjdbc 42.7.13, so this is a real scan, not an empty one. |
| D-06 Container runtime | pass | Browser runs for both forms. PostgreSQL rows match the JSON files (SHA-256). Mailpit received the participant email and the organizer email with the JSON attachment. Export works with credentials and returns 401 without them. Readiness is UP. After `down`/`up`, 51/51 registrations and their JSON hashes are unchanged. |
| D-07 Failure/security | pass | Storage failures (read-only backup directory, PostgreSQL stopped) return 503 with no partial acceptance. Idempotent replay and conflict work. The SMTP outage is retried. Production cannot enable the test captcha (4 fail-closed startups). Errors do not echo input or leak details. |
| D-08 Reproduction/reporting | pass (self-assessed) | Specification, README, run.json, events.jsonl, this summary and the evidence exist. Missing measurements are listed. |

## 3. Timeline (UTC, 2026-09-28; tool-measured from event/check timestamps)

| Phase | Start | End | Duration |
|---|---|---|---|
| Setup, preflight, contracts, specification, initial code¹ | 15:55:35.9 | 16:10:26.6 | 14m51s |
| M0 foundation → verified | 16:10:26.6 | 16:25:11.6 | 14m45s |
| M1 + M2 verified | 16:25:11.6 | 16:29:17.1 | 4m06s |
| M3 catalog/consent verified | 16:29:17.1 | 16:33:50.9 | 4m34s |
| M4 notifications verified | 16:33:50.9 | 16:39:10.9 | 5m20s |
| M5 export verified | 16:39:10.9 | 16:40:28.9 | 1m18s |
| M6 hardening (tests, static repairs, scripts) | 16:40:28.9 | 16:49:49.0 | 9m20s |
| First complete suite | 16:49:49.0 | 16:56:37.8 | 6m49s |
| Security triage/repairs, docs | 16:56:37.8 | 17:02:19.0 | 5m41s |
| Final complete suite | 17:02:19.0 | 17:08:58.0 | 6m39s |
| Reporting (first report, D-05 blocked) | 17:08:58.0 | 17:12:55.7 | 3m57s |
| Waiting for researcher decision | 17:12:55.7 | 17:17:25.9 | 4m30s² |
| Authorized Boot 4 migration + affected checks | 17:17:25.9 | 17:23:16.7 | 5m51s |
| Final complete suite (Boot 4) | 17:23:16.7 | 17:29:26.9 | 6m10s |
| Reporting update | 17:29:26.9 | see `run.json.timestamps.end` | — |

² This runs to when the agent recorded the authorization event; the exact arrival time of the user message cannot be observed.

¹ This boundary is approximate. Writing the specification and backend code overlapped with the
background NVD download. The phases are sequential and do not overlap.

- **Time to first verified external registration (M1):** 33m41s (16:29:17.085).
- **Application checks finished:** 17:29:26.892, which is 1h33m51s after start. The first finish, with D-05 blocked, was 17:08:58.012 (1h13m22s).
- **Waiting:** the NVD database update took 2m25s after two failed attempts. No human waiting occurred.

Milestones: M0–M5 are `pass`. They were re-verified in the Boot 4 suite, and no regressions
were observed. M6 was `blocked` at 17:08:58 and passed at 17:29:26.892 after the authorized
change.

## 4. Delivered behavior (US → acceptance → evidence)

| US | Acceptance IDs | Evidence (under `03_Metrics/evidence/`) |
|---|---|---|
| US-001 external form | AC-001-01…06 (P-01, P-03, P-04) | `commands/*final__e2e.log`, `*final__backend-verify.log` (RegistrationApiIT), `screenshots/01,02` |
| US-002 student form | AC-002-01…04 (P-02) | e2e student journey; RegistrationApiIT covers 6 fields × missing/empty/space/NBSP; `screenshots/03,04` |
| US-003 configurable options | AC-003-01…05 (P-04, P-05, P-06) | `runtime/m3/catalog-switch.txt` (same image digest, new catalog), `runtime/m3/playwright-catalog-changed.txt`, CatalogChangeIT, CatalogLoaderTest |
| US-004 in-app confirmation | AC-004-01…03 | e2e holds the backend response and asserts that no success panel appears before it arrives; RegistrationForm tests (400/429/500/503/network show no success) |
| US-005 durable storage/recovery | AC-005-01…06 (P-07, P-10) | `runtime/storage.json`, `runtime/idempotency.json`, `runtime/recreate.json`, StorageFailureIT (fault injection, reconciliation) |
| US-006 participant email | AC-006-01…02 (P-08) | `runtime/mail.json` (Mailpit outage → PENDING → SENT), NotificationIT (GreenMail), `screenshots/05` |
| US-007 organizer notification | AC-007-01…02 (P-08) | `runtime/mail.json` (attachment SHA-256 equals the stored JSON), NotificationIT |
| US-008 Excel export | AC-008-01…02 (P-09) | `runtime/export.json` (401 ×3 with no data; workbook parsed and equal to the DB, both forms), ExportIT |

## 5. Verification table

Commands are run by `02_Implementation/tools/verify-all.sh <suite>`. Each check's
stdout/stderr and exit code are in `evidence/commands/<ts>_<suite>__<id>.log`, and all checks
are indexed in `evidence/checks.jsonl`.

| Check (exact command) | First complete (16:49–16:56) | Final, Boot 3 (17:02–17:09) → Final, Boot 4 (17:23–17:29) |
|---|---|---|
| `./mvnw -B clean verify` (unit + Testcontainers IT + ArchUnit + JaCoCo) | pass (33 unit, 53 IT, 0 F/E/S) | pass (33, 53, 0 F/E/S) |
| `./mvnw spotless:check compile spotbugs:check pmd:check pmd:cpd-check` | pass | pass |
| `npm ci`, `format:check`, `lint`, `typecheck`, `test:coverage`, `build`, `cpd` | pass (23 tests) | pass (23 tests) |
| `semgrep scan --metrics=off --config p/default,p/java,p/typescript,p/react,p/secrets,p/dockerfile` | 0 findings | 0 findings |
| `dependency-check-maven:13.0.0:check` (NVD feeds modified 2026-09-28T08:00-04) | 15 C / 15 H / 17 M / 1 L | 7 C / 8 H / 7 M / 1 L → **0** |
| `npm audit --audit-level=high` | 0 high; 3 moderate | 0 → 0 |
| `tools/security_summary.py` (gate: no Critical/High) | **fail** | **fail (blocked)** → **pass** |
| `docker compose build` / `up -d --wait` | pass | pass |
| `npm run e2e` (Playwright, real stack) | 5 passed | 5 passed |
| `tools/m3-catalog-restart.sh` | 4 passed | 4 passed |
| `runtime_probe.py mail / idempotency / storage / export / prod-isolation / recreate` | all pass | all pass |
| `npm run e2e` after recreation | 5 passed | 5 passed |

Diagnostics only (not targets):
- JaCoCo backend unit tests: lines 225/1,071 (21.0%).
- JaCoCo backend integration tests: lines 934/1,071 (87.2%), branches 233/326.
- Vitest v8 frontend: lines 77.3%. The first-suite frontend coverage number came from Vitest 3; Vitest 4 counts differently.
- Security scanners keep their own units: Dependency-Check counts vulnerability × dependency; npm audit counts vulnerable packages.

## 6. Decisions and deviations

**AR/ST checklist (actual implementation):**

| Rule | How it is met |
|---|---|
| AR-01 | Separate React SPA and one Spring Boot backend talking over REST. All validation and storage happen in the backend. |
| AR-02 | One deployable with modules `shared`, `catalog`, `captcha`, `notification`, `registration`, `export`. ArchUnit enforces the dependency matrix and checks for cycles. |
| AR-03 | PostgreSQL is the index. Flyway V1 owns the schema and Hibernate is set to `validate`. JSON is stored on the `backups` named volume. |
| AR-04 | Stage and fsync → atomic publish → DB transaction. If the DB commit fails, the file is removed. Startup and periodic reconciliation clean up leftovers. |
| AR-05 | Outbox rows are written in the registration transaction. An async dispatcher retries with backoff and marks rows `FAILED` after 12 attempts. |
| AR-06 | HTTP Basic is used only for `/api/organizer/**`. The credential comes from the environment (bcrypt in memory), and startup fails without it. |
| AR-07 | The YAML catalog is read at startup. A restart applies changes without a rebuild, and invalid catalogs fail fast. |
| AR-08 | Containers are HTTP-only on the internal network. `deploy/nginx-external.conf` shows TLS, Let's Encrypt, HSTS and the overwrite of X-Forwarded-*. RemoteIpValve trusts only private ranges. |
| ST-01…09 | Boot 3.5.16 / Java 21 / wrapper; PG 16.15 + Flyway; React 19.3, TS 5.9, Vite 6.4.3, Node 22, lockfile; Mailpit/external SMTP; reCAPTCHA v2 with a deterministic local/test mode that fails closed; JUnit 5, Mockito, Spring Boot Test, MockMvc, Testcontainers, JaCoCo, Vitest, RTL, Playwright; Spotless, SpotBugs, PMD/CPD, ArchUnit, ESLint/typescript-eslint, Prettier, tsc, jscpd; Semgrep, Dependency-Check, npm audit; three app containers plus Mailpit, all pinned by digest. |

**Deviations from fixed rules:** one. **ST-01** now uses Spring Boot 4.1.1 instead of 3.x. The researcher authorized it explicitly ("i authorise the stack change", recorded 17:17:25.879Z) after the agent reported D-05 blocked. It was first observed in code at 17:20Z and kept in the final revision.

Migration changes:
- Modular starters: `webmvc`, `flyway`, `restclient` and the matching test starters
- Jackson 3 packages (`tools.jackson`); `JsonNode` accessors renamed
- Actuator health API package
- `HttpClientSettings`
- Testcontainers 2 artifacts and class
- Removed the Boot-3 patch overrides that Boot 4.1.1 already covers; kept Tomcat 11.0.26, because Boot 4.1.1 manages 11.0.24, which is affected by CVEs fixed in 11.0.25

Everything else (Java 21, PostgreSQL 16, React/Vite/Node, tests, scanners, containers) is unchanged.

**Process notes:**
- Code for M1/M2 was drafted before M0 was verified.
- The explicit PMD ruleset excludes two style rules; the rationale is in `pmd-ruleset.xml`. These are ordinary choices, not weakened gates.

**Version changes after the baseline** (all within the allowed ranges; reasons are in `docs/resolved-versions.md`):
- Tomcat 10.1.60, pgjdbc 42.7.13, Jackson 2.21.7, log4j-api 2.25.5, commons-lang3 3.20.0
- Vitest 4.1.11
- ESLint 10, adopted before any feature work

**D-05 triage from the Boot 3 run (historical; resolved by the authorized change, nothing was suppressed):**

| Component | Advisories | Relevance to this app |
|---|---|---|
| spring-core 6.2.19 | CVE-2026-47884 (XsltView)<br>47890 and 59313 (SSE stream corruption)<br>47891 (WebFlux Aalto XML)<br>47892 (WebFlux functional endpoints)<br>59283 and 47886 (SpEL)<br>47885 (WebFlux multipart)<br>47888 (RSocket)<br>47889 (Jetty reactive)<br>47893 (WebFlux WebSocket)<br>59282 (data binding with user-supplied property paths) | The app does not use XsltView, SSE, WebFlux, RSocket, Jetty, WebSocket or SpEL on user input. JSON is bound through Jackson `@RequestBody` with unknown properties rejected. |
| spring-security-core 6.5.11 | CVE-2026-59270 (embedded UnboundID LDAP)<br>41707 (DPoP JWT replay)<br>47841 (WebAuthn) | None of these features are used. |

My assessment was that these advisories were probably not exploitable here. That was my own
analysis, not a scanner result, so they stayed unresolved until the authorized stack change
removed them.

**Policy gaps that block real deployment:**
- Consent/legal wording (only synthetic fixtures are used)
- Retention period
- Policy for repeated email addresses
- Production secrets (SMTP, reCAPTCHA keys, organizer credential)
- TLS domain

## 7. Repair episodes

All 13 are in `run.json.repairs` and `events.jsonl`. Categories and outcomes:

| # | Trigger | Category | Change | Outcome |
|---|---|---|---|---|
| 1–2 | NVD update failed twice ("Invalid API Key length 0"; keyless NVD API returned 503) | environment | Used the official NVD JSON 2.0 data feeds | pass |
| 3 | FoundationIT: CHAR(64) vs String; Path-typed property (late-recorded) | implementation/test | VARCHAR(64); String property | pass on 3rd run |
| 4 | `metrics.py` crashed on `--cwd`; ESLint irregular whitespace and useless assignment (late-recorded, times unavailable) | implementation (instrumentation) | Split argv on `--`; escaped `​` | pass |
| 5 | Blank email returned EMAIL_INVALID alongside REQUIRED | implementation | One error code per field, REQUIRED first | 32/32 |
| 6 | CatalogChangeIT: base-class property overrode the subclass value | test | Moved the default into `application-test.yml` | pass |
| 7 | M3 e2e `exact` label selector did not match | test | Substring label match | 4/4 |
| 8 | NotificationIT: nested multipart text; earlier compile error | test | Recursive text extraction | 3/3 |
| 9 | Runtime mail probe: transient connection reset in the polling loop | test (tooling) | Tolerate OSError; record tracebacks | pass |
| 10 | SecurityIT health assertion was too strict (group names) | test | Old: body == `{"status":"UP"}`. New: no components/details. Intent unchanged (AC-X-04/05). | pass |
| 11 | SpotBugs 18 / PMD 31 / CPD 1 | implementation | Immutable copies, null-safe paths, cause chaining, composed constraints, explicit PMD ruleset | 0/0/0 |
| 12 | Dependency-Check 30 Critical/High | implementation | In-line patch overrides | 15 remaining (blocked) |
| 13 | npm audit 3 moderate; npm 10 arborist crash | environment | Vitest 4.1.11; one lockfile resolution with npm 11 on Node 22 | 0 vulnerabilities |
| 14 | D-05 blocked → authorized stack change | specification (condition change) | Boot 4.1.1 migration. The first compile attempt failed because my edit script stopped part-way on a formatting mismatch; it was re-applied and then compiled cleanly. | All 24 checks pass |

- **Regressions:** none observed. Every check that passed earlier also passed in the final suite.
- **Repeated identical failures:** none after the second NVD attempt; the cause was investigated before retrying.
- **Compile errors in new tests:** NotificationIT and ExportIT each had one, fixed at once.

## 8. Resources and human effort

- **Wall clock:** start 15:55:35.917Z. The end time and final elapsed time are in `run.json.timestamps.end` (tool). The first report ended at 17:12:55.694Z (1h17m19.8s).
- **Provider usage** (input/output/cached/reasoning tokens, cost, model calls, tool calls): **unavailable (null)**. The harness does not expose it.
- **Harness token counter:** first turn 15,000,000 → 14,631,214; second turn restarted at 15,000,000 → about 14.96M. Its meaning is undocumented, so it is not provider accounting.
- **Human interventions:** 1 required approval: the stack-change authorization at 17:17:25.879Z. About 4m30s of observed waiting, which includes reading time. There were no hints and no manual edits.
- **Instrumentation cost:** `tools/metrics.py`, `security_summary.py`, `runtime_probe.py` and the reporting phase are part of the configuration's cost.

## 9. Limitations and reproduction

**Remaining issues:**
- The final configuration deviates from ST-01 (authorized).
- Email delivery is at-least-once, so a crash can produce a duplicate.
- The rate limiter keeps state in memory and only works for a single instance.
- Restoring after database loss relies on a database backup. The JSON files are preserved, but there is no import tool.
- The production reCAPTCHA and SMTP paths are only tested with mocks and fail-closed checks; real Google and SMTP were never called.
- Runtime probe JSON files hold the results of the Boot 4 final suite. The first-suite runtime outputs were overwritten, but their logs remain in `evidence/commands/*first-complete*`, and the first-suite security reports are kept as `evidence/security/first-complete-*`.

**Missing measurements:**
- Provider tokens, cost and calls
- Sampling settings
- Runner budgets (none were given)
- M0 start time
- Timing of two late-recorded repairs
- Exact end of the first phase

**Reproduce:** see `02_Implementation/README.md`. In short: `tools/init-local-env.sh`, then
`docker compose up -d --build --wait`, then `tools/verify-all.sh <label>` (requires Node 22,
Java 21, Docker, Semgrep and Playwright Chromium).

**Raw evidence:**
- `03_Metrics/evidence/`: `commands/`, `checks.jsonl`, `runtime/`, `security/`, `reports/`, `screenshots/`, `preflight.txt`
- Suite logs: `first-complete-suite.txt`, `final-suite.txt` (Boot 3), `final-boot4-suite.txt` (final)
