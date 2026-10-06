# Release notes

> Written in: phase 7 · Agent: writes

## Delivered

| Story | Delivered |
|---|---|
| US-001 | External participant form: required fields, Unicode trim (incl. NBSP), email check, active options by category, mandatory unchecked consent, reCAPTCHA, duplicate email refused (D-16) |
| US-002 | Student form (institution, programme, student ID); options per registration type (D-12) |
| US-003 | Options and consent text in a YAML file (`OPTIONS_FILE`), validated at startup; change + restart, no code change |
| US-004 | Confirmation only after the backend accepted; field errors at the fields, values kept; general error without details |
| US-005 | PostgreSQL row (Flyway V1) and durable raw JSON copy in one unit; neither or both; copies never overwritten |
| US-006 | Plain-text participant confirmation email after storage; mail failure does not undo the registration (D-14) |
| US-007 | Organizer email with all data and the JSON copy attached (byte-identical) |
| US-008 | Organizer-only Excel export (HTTP Basic, HTTPS-only outside localhost, rate limited), text-only cells |

Verification: 279 automated tests pass (79 + 29 frozen acceptance, 6 end-to-end); runtime demonstration 34/34 (`docs/06_verification-report.md`).

## Known limitations

- Rate limiting is in-process per backend instance; several instances need a shared limiter.
- Data retention (12 months, D-17) is a manual procedure (backend README); no automated deletion.
- The organizer uses HTTP Basic (accepted D-22); there is one organizer account.
- Emails are sent asynchronously without retry; a failure is only logged with the registration id (D-14).
- UI text is English only.
- Vulnerability data for the backend scan is the local NVD cache of 2026-10-06 (no `NVD_API_KEY`, D-07); re-run `scripts/verify.sh <p> backend-depscan` with a key.
- Dev-only npm tooling has open advisories (vitest 3.2.7 Critical, jscpd 4.3.0 High; not shipped, D-10).
- Container images were not scanned (no image scanner in `tech-stack.md`).

## Must be tested manually by a human

| What | How |
|---|---|
| Google reCAPTCHA v2 with production keys | set `RECAPTCHA_SITE_KEY`/`RECAPTCHA_SECRET_KEY`, test mode off; one real submission succeeds, one without solving the widget is refused; widget renders under the CSP |
| SMTP delivery | production SMTP with STARTTLS; participant and organizer mails reach real mailboxes, JSON attachment opens |
| TLS and reverse proxy | external nginx: HTTPS only, HTTP→HTTPS redirect, `/` → frontend, `/api` → backend with `X-Forwarded-For`/`X-Forwarded-Proto`; export refused over plain HTTP, works over HTTPS |
| Excel in a desktop spreadsheet | open the export in Excel/LibreOffice: č/š/ž and formula-like values shown as text |
| Production startup | without profile and with production values the backend starts; with test mode on it refuses |

## Decisions pending review

Every decision was taken without a human answer under the standing instruction "do not ask for my permission … record divergence"; all are pending review (`docs/decisions-log.md`).

| D | Subject |
|---|---|
| D-01 | run branch `run/tanej-01_opus5.5_sdd_tanej-1.0` (start commit was a detached HEAD) |
| D-02, D-03, D-04, D-05 | Node from nvm; host JDK 21.0.12.1, Docker 29.3.1, Compose 5.1.1 instead of the pins; cloc tag reports 1.98 |
| D-06 (blocking) | agent generated local `.env` values (rotated in phase 6, F-01) |
| D-07 (blocking) | no NVD API key: cached NVD data |
| D-10 (blocking) | dev-only npm Critical/High advisories accepted |
| D-12 … D-17 | open questions OQ-01 … OQ-06 answered conservatively |
| D-18, D-19 | contract validators; JDK build image `eclipse-temurin:21.0.10_7-jdk-alpine` |
| D-20 (blocking) | frozen test helper fixed (timestamptz) and manifest re-hashed |
| D-22 (blocking) | Semgrep Basic-auth High accepted as Low |

Suppressed false positives (tool, decision): Dependency-Check CVE-2025-7962 on `angus-activation` (D-08), CVE-2025-15104 on `hibernate-validator` (D-09); SpotBugs DI/Servlet patterns (D-21); gitleaks generated test passwords in phase 3 logs (D-23); gitleaks history before the start commit (D-11).
