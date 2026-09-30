# Verification report

> Written in: phase 6 · Source: `general/quality/*`, `general/security/*`, `project/04_security/*`, `project/05_quality/*` · Procedure: `general/skills/verify-release`, `general/skills/security-review` · Agent: writes

This is a self-check by the development agent, not an independent review.

Logs referenced below are in `out/logs/`. Run date 2026-09-30 (UTC).

## Definition of Done

| DoD | Result | Evidence |
|---|---|---|
| DoD-01 | Pass | Final run: backend 184/184 (67 frozen acceptance + 117 unit/ArchUnit, `phase6-final-backend.log`), frontend 37/37 (10 frozen acceptance + 27 unit, `phase6-final-frontend-test.log`), e2e 3/3 against the compose stack (`phase6-final-e2e.log`). Both manifests recomputed and matching (`phase6-hash-check.log`) |
| DoD-02 | Pass | Backend `spotless:check pmd:check pmd:cpd-check spotbugs:check` BUILD SUCCESS (`phase6-final-backend-check.log`); frontend `prettier --check`, `eslint`, `tsc --noEmit` clean (`phase6-final-frontend-check.log`); Semgrep: 2 Medium accepted (F-03, F-04) |
| DoD-03 | Pass (record only) | Coverage and mutation under "Measures" below; no project thresholds (`quality-requirements.md`) |
| DoD-04 | Pass | `ArchitectureTest` A-1..A-7 incl. slice cycle check (AR-02, AR-03) in the unit suite; AR-01 by design (frontend calls only `/api`, `api.ts`); AR-05 `JsonCopyFailureAcceptanceTest`; AR-06 Flyway `V1` + `ddl-auto=validate`; AR-07 `ar07ClientConfigurationExposesSiteKeyOnly`; AR-04 `OptionsConfigurationChangeAcceptanceTest` |
| DoD-05 | Pass | No open Critical or High: F-07 lowered to Low by the human (D-18/D-19) and suppressed narrowly; re-scan 0 Critical, 0 High (`phase6-dependency-check-report-d18.json`); npm audit 0 High/Critical; Semgrep, Gitleaks, SpotBugs, PMD without Critical/High |
| DoD-06 | Pass | Runtime demonstration below (`phase6-runtime-demo.log`, `phase6-final-e2e.log`) |
| DoD-07 | Pass | Traceability table below: every AC has ≥ 1 test and an implementing commit |
| DoD-08 | Pass (phase 7) | Fresh clone of commit 1a3cea5, READMEs followed exactly: backend build, test 184/184, check; frontend `npm ci`, check, test 37/37, build; stack healthy; e2e 3/3 (`phase7-clean-checkout.log`). First attempt failed on Windows path length during checkout; README now says to clone with `core.longpaths` |
| DoD-09 | Pass (phase 7) | `docs/release-notes.md` "Must be tested manually by a human": reCAPTCHA production, SMTP delivery, TLS/reverse proxy, production export, start-up refusals, backups, accessibility, consent/retention wording |
| DoD-10 | Pass | D-01..D-19 resolved or pending review (D-18 resolved by D-19). Input manifest matches; `git diff 33175c2 HEAD -- 01_input` empty |
| DoD-11 | Pass | Phase 3 commit `5d68e66` contains the manifest and all 20 files it lists with matching hashes and no production code beyond the skeleton; every manifest hash still matches (`phase6-hash-check.log`) |
| DoD-P01 | Pass | External and student registrations through the running stack → 201 (runtime demo §3; e2e tests 1 and 2 through the browser) |
| DoD-P02 | Pass | Both rows in PostgreSQL with options; both JSON copies on the volume (runtime demo §4, §5) |
| DoD-P03 | Pass | Participant emails and organizer emails with 1 attachment each in Mailpit (runtime demo §6) |
| DoD-P04 | Pass | Export 200 `.xlsx`, opened with Apache POI (sheet, header, rows with č/š/ž); no credentials 401, wrong password 401 (runtime demo §7, §7b) |
| DoD-P05 | Pass | `CaptchaVerificationAcceptanceTest`: production verifier against a mocked siteverify endpoint, valid token accepted, rejected/forged/missing/test-mode tokens refused; `RecaptchaVerifierTest` (non-200, bad JSON, unreachable) |

## Runtime demonstration

| Component | Environment | Flows exercised | Result |
|---|---|---|---|
| postgres, mailpit, backend, frontend | local (`docker compose --env-file ../.env up -d --build`) | health: compose health checks; `/actuator/health/liveness`, `/readiness`; frontend `/healthz` | all `healthy`/`UP` (NFR-04, ES-09) |
| frontend (nginx) + backend | local | security headers on `/` and `/api` | CSP, `X-Frame-Options: DENY`, `nosniff`, referrer policy present (SB-10) |
| full stack | local | external (č/š/ž) and student registration via `/api` through nginx; invalid submission | 201, 201, 400 with field errors |
| full stack | local | PostgreSQL rows, options, Flyway history; JSON copy listing and content | present, UTF-8 intact |
| full stack | local | Mailpit: participant and organizer emails | 2 participant, 2 organizer (1 attachment each) |
| full stack | local | export with / without / wrong credentials; workbook opened | 200 (valid workbook), 401, 401 |
| full stack | local | `docker compose down` then `up` | 8 rows and 8 copies before and after (NFR-02) |
| backend, frontend images | local | container user | uid 10001 / nginx (uid 101), `read_only`, `no-new-privileges` (SB-11) |
| backend logs | local | demo names, emails, student ID searched in `docker compose logs backend` | 0 occurrences (ES-07, SB-07) |
| browser | local | Playwright: form → confirmation → Mailpit → export (NFR-01), student form, DoD-P04 pair | 3/3 passed |

## Traceability

Commits: `5d68e66` phase 3 (frozen tests), `deeb6bf` backend, `3af26d7` frontend and deployment, `4fcfbb6` unit tests. Test names below are backend acceptance methods unless marked FE (frontend acceptance) or E2E.

| AC | Tests | Commits |
|---|---|---|
| AC-001-01 | `ac00101…`, `sr01ValidToken…`, E2E NFR-01 | 5d68e66, deeb6bf, 3af26d7 |
| AC-001-02 | `ac00102MissingExternalField…` (8 cases), `ac00102AbsentOrganization…` | 5d68e66, deeb6bf |
| AC-001-03 | `ac00103InvalidEmail…` (5) | 5d68e66, deeb6bf |
| AC-001-04 | `ac00104WhitespaceIsTrimmed` | 5d68e66, deeb6bf |
| AC-001-05 | `ac00105UnicodeIsStoredUnchanged`, E2E NFR-01 | 5d68e66, deeb6bf |
| AC-001-06 | `ac00106UnknownOrInactiveOption…` (2) | 5d68e66, deeb6bf |
| AC-001-07 | `ac00107MissingMandatoryConsent…`, `ac00107UnknownConsent…` | 5d68e66, deeb6bf |
| AC-001-08 | FE `AC-001-08` | 5d68e66, 3af26d7 |
| AC-002-01 | `ac00201…`, E2E `AC-002-01` | 5d68e66, deeb6bf, 3af26d7 |
| AC-002-02 | `ac00202MissingStudentField…` (12 cases), `ac00202StudentWithExternalField…` | 5d68e66, deeb6bf |
| AC-002-03 | FE `AC-002-03` | 5d68e66, 3af26d7 |
| AC-003-01 | `ac00301…` (2) | 5d68e66, deeb6bf |
| AC-003-02 | `ac00302ConfigurationChangeIsVisibleAfterRestart` | 5d68e66, deeb6bf |
| AC-003-03 | FE `AC-003-03` | 5d68e66, 3af26d7 |
| AC-003-04 | `ac00304…` (3), FE `AC-003-04` | 5d68e66, deeb6bf, 3af26d7 |
| AC-003-05 | `ac00305…` (3) | 5d68e66, deeb6bf |
| AC-004-01 | FE `AC-004-01`, E2E 1 and 2 | 5d68e66, 3af26d7 |
| AC-004-02 | FE `AC-004-02` (2) | 5d68e66, 3af26d7 |
| AC-004-03 | FE `AC-004-03` (3) | 5d68e66, 3af26d7 |
| AC-005-01 | `ac00501…` | 5d68e66, deeb6bf |
| AC-005-02 | `ac00502…` | 5d68e66, deeb6bf |
| AC-005-03 | `ac00503FailedJsonCopyRollsBack` | 5d68e66, deeb6bf |
| AC-006-01 | `ac00601…`, E2E NFR-01 | 5d68e66, deeb6bf |
| AC-006-02 | `ac00602MarkupIsNotInterpreted`, `ac00602HeaderInjectionIsPrevented` | 5d68e66, deeb6bf |
| AC-006-03 | `ac00603MailIsRetriedAfterFailure` | 5d68e66, deeb6bf |
| AC-007-01 | `ac00701…`, E2E NFR-01 | 5d68e66, deeb6bf |
| AC-007-02 | `ac00702…` | 5d68e66, deeb6bf |
| AC-008-01 | `ac00801…`, E2E 3 | 5d68e66, deeb6bf |
| AC-008-02 | `ac00802…` (2), E2E 3 | 5d68e66, deeb6bf |
| AC-008-03 | `ac00803…`, E2E NFR-01 | 5d68e66, deeb6bf |
| AC-008-04 | `ac00804…` | 5d68e66, deeb6bf |

## Security review

Standard: OWASP ASVS 5.0 Level 1 for both components (`security-requirements.md`).

| Item | Implemented in | Checked by |
|---|---|---|
| SB-01 | `domain/RegistrationValidator`, `OptionsFileLoader` | acceptance AC-001/002/003 rejections; `RegistrationValidatorTest` |
| SB-02 | `SecurityConfig` (`/api/admin/**` role ORGANIZER; rest intentionally public) | `ac00802…`, runtime demo §7 |
| SB-03 | BCrypt hash of the organizer password at startup; secrets only from environment (`EnvironmentAliases`) | inspection; `StartupChecksTest`; Gitleaks |
| SB-04 | TLS at the external proxy (production); HSTS on secure requests; SR-06 filter | inspection; manual test in release notes |
| SB-05 | JPA bound parameters; React escaping; plain-text email; POI string cells | Semgrep; `ac00602…`; `PoiWorkbookWriterTest` (formula-like input stays a string) |
| SB-06 | `RateLimitFilter` on registration and admin paths (before authentication) | `AbuseProtectionAcceptanceTest`, `FiltersTest` |
| SB-07 | `ApiExceptionHandler`, `server.error.*` never, logs carry references only | `assertNoInternals` in acceptance; runtime demo §8 |
| SB-08 | Dependency-Check (backend), npm audit (frontend) | F-07, F-08 (backend Medium/High false positives); npm: 5 Moderate dev-only, 0 runtime |
| SB-09 | SpotBugs, PMD, Semgrep, Gitleaks | logs `phase6-*`; F-01, F-03..F-05 |
| SB-10 | `SecurityConfig.headers`, `frontend/nginx.conf` | `sb10SecurityHeaders`; runtime demo §2 |
| SB-11 | non-root images, `read_only`, `no-new-privileges` | runtime demo §10 |
| SB-12 | only the fields of `business-rules.md` are collected | inspection of contracts and entity |
| SB-13 | purposes in `security-requirements.md`; retention D-14 (pending review) | inspection; manual item in release notes |
| SB-14 | consents unchecked, stored with wording and timestamp | FE `AC-001-08`; `ac00501…` |
| SR-01 | `RecaptchaVerifier`, captcha verified on the backend last | `CaptchaVerificationAcceptanceTest` (DoD-P05) |
| SR-02 | `StartupChecks` S-1, S-2; test mode off by default | `StartupSafetyAcceptanceTest` |
| SR-03 | `RateLimitFilter`, `RequestSizeFilter` (413/411), nginx `client_max_body_size` | `AbuseProtectionAcceptanceTest` |
| SR-04 | `RegistrationValidator.options` against the active configured set | `ac00106…`, `ac00304…`, `ac00305…` |
| SR-05 | control characters rejected; subjects without user input; plain text | `ac00602…` (2), `RegistrationValidatorTest`, `SmtpMailerTest` |
| SR-06 | `OrganizerTransportFilter` + trusted-proxy headers; S-3 | `sr06CredentialsOverPlainHttp…`, `sr06ProductionRefuses…`, `FiltersTest` |
| SR-07 | `MailComposer`, `FileJsonCopyStore.document`, `PoiWorkbookWriter.COLUMNS` | `ac00702…`, `ac00804…` |

Error responses and logs produced during testing were checked for internals and personal data: acceptance tests assert no exception or package names in error bodies; the backend container log of the runtime demo contains none of the demo names, emails or student ID.

## Measures

| Measure | backend | frontend |
|---|---|---|
| Line coverage, unit | 78.9 % (750/951) | 92.3 % (407/441) |
| Branch coverage, unit | 90.9 % (271/298) | 87.5 % (126/144) |
| Line coverage, integration/acceptance | 91.9 % (874/951) | 87.1 % (384/441) |
| Branch coverage, integration/acceptance | 77.5 % (231/298) | 78.5 % (102/130) |
| Mutation score (PIT / Stryker) | 77.2 % first, 83.9 % after loop 1 (339/404; domain, application, config, infrastructure; unit tests only) | 73.1 % all `src` (validation.ts 97.3 % → 100 % after loop 2) |
| Tests, final run | 184 passed (67 acceptance, 117 unit) | 37 passed (10 acceptance, 27 unit); e2e 3 passed |
| Production / test lines of code (cloc) | 2312 / 3343 | 657 / 810 |
| Duplication | 0 (CPD, 100 tokens) | 0 % (jscpd, 50 tokens) |
| Cyclomatic complexity | 196 methods, average 1.66, max 14 (`StartupChecks.problems`) | — |

Surviving mutants after the fix loops, classified individually (security, validation and persistence code): `RegistrationValidator` return values replaced by `""` when an error is already recorded (equivalent: value discarded); `parsesStrictly` `validate()` removal and `return true` (equivalent: the strict constructor already validates; no input found that parses but differs); `OptionsFileLoader` `require` calls whose violation is caught by a later rule, and `limits` returning an empty map (equivalent); `RateLimitFilter` purge-threshold boundary (off by one entry of 50 000, no behavioural effect); `FileJsonCopyStore.writable` final `return` (unreachable on the test platform after `createDirectories` succeeds); `NotificationService` abandoned-log condition, `JsonErrors.setCharacterEncoding`, `toString` redaction returning `""` (log-only or equivalent). NO_COVERAGE mutants are Spring wiring (`ApplicationConfig`, `SecurityConfig`) and the JPA adapter, exercised only by the integration suite. Frontend `App.tsx`/`Captcha.tsx` survivors are presentation strings and render conditions (F-06).

## Findings

| F | Severity | Source | Finding | Resolution |
|---|---|---|---|---|
| F-07 | High → Low (D-18/D-19) | Dependency-Check | CVE-2025-7962 (Jakarta/Angus Mail SMTP injection, affects Angus Mail < 2.0.4) reported on `angus-activation-2.0.3.jar`, identified as `cpe:…:angus_mail:2.0.3`. The shipped SMTP implementation is `angus-mail` 2.0.5 (fixed) with `jakarta.mail-api` 2.1.5; `angus-activation` contains no SMTP code; user input with CR/LF is rejected and never reaches headers (SR-05 tests). No GA `angus-activation` > 2.0.3 exists (only 2.1.0-M1). Phase 0 had recorded this CVE as Medium from the tool's label instead of the CVSS mapping. | Lowered to Low as a false positive by the human (D-19); suppressed for `angus-activation` only in `backend/dependency-check-suppressions.xml`; re-scan confirms (loop 4) |
| F-08 | Medium | Dependency-Check | CVE-2025-15104 (Nu Html Checker SSRF) matched to `hibernate-validator` 9.1.3 via `cpe:…:validator:validator`; CVSS 5.3 | Accepted: false positive (different product); pending review |
| F-03 | Medium | Semgrep | `.npmrc` sets no `min-release-age` | Accepted: needs npm ≥ 11.10, npm 11.6.2 is pinned (tech-stack change); mitigated by exact versions, committed lock file with integrity hashes and `npm ci`; listed for human review |
| F-04 | Medium | Semgrep | nginx `/api/` location with `proxy_pass` lacks `internal` (SSRF pattern) | Accepted: false positive — upstream is the constant `http://backend:8080`; `internal` would make the public API unreachable |
| F-05 | Medium | Semgrep | nginx forwarded the client `Host` header to the backend | Fixed (loop 2): header no longer forwarded; Semgrep re-run clean for this rule; e2e 3/3 |
| F-01 | Low | Gitleaks | Phase 3 red-run log contained Spring Boot's generated throwaway passwords of the bootstrap skeleton (11 lines); no project credential | Fixed in the working tree (loop 3, redacted; Gitleaks dir scan clean); remains in commit `5d68e66` history (no rewrite, working rules) |
| F-02 | Low | PIT, Stryker | Unit tests did not pin several rules in validation, security and persistence code (strict email parsing, rate-limit purge, options-file single rules, JSON copy fields, temp-file cleanup, client email anchor) | Fixed (loops 1 and 2): tests added; PIT 77.2 → 83.9 %, `validation.ts` 97.3 → 100 % |
| F-06 | Low | Stryker | `App.tsx` 65 %, `Captcha.tsx` 68 % mutation score: surviving mutants in UI strings and render conditions | Accepted: presentation code, behaviour covered by frontend acceptance and e2e tests; thresholds are record-only |

Informational (not part of this deliverable): the Gitleaks history scan also reports 21 generic-key matches in commits of other branches of this repository (`02_Implementation/`, `03_Run-Statistics/`), which this run did not create.

## Fix loops

| Loop | Findings | Change | Re-verification |
|---|---|---|---|
| 1 | F-02 | backend unit tests added (validator, options loader, filters, JSON copy store, mail composer, workbook, startup checks) | unit 117/117; PIT 83.9 % (`phase6-pit-summary-loop1.txt`) |
| 2 | F-05, F-02 | nginx: client `Host` not forwarded; frontend validation tests | Semgrep re-run (`phase6-semgrep-loop2.json`): rule gone; Stryker `validation.ts` 100 %; e2e 3/3 on the rebuilt stack |
| 3 | F-01 | redacted generated passwords in agent logs | Gitleaks dir scan: no leaks (`phase6-gitleaks-dir-loop3.log`) |
| 4 | F-07 | human-approved suppression (D-19) | Dependency-Check re-run: 0 Critical, 0 High, 1 Medium (`phase6-dependency-check-d18.log`) |
