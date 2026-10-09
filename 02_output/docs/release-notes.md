# Release notes

> Written in: phase 7 · Agent: writes

Version 0.1.0, run `tanej-01_conference-registration_opus5.5_sdd_template-tanej-1.0`. Evidence: `docs/06_verification-report.md`.

## Delivered

| Story | Delivered |
|---|---|
| US-001 | External participant form: fixed fields, active options, mandatory consents, reCAPTCHA v2 (test mode locally), field errors next to the fields |
| US-002 | Student form with study institution, programme and student ID; options limited to those available to students (D-14) |
| US-003 | Options and consents in a JSON file (`CONFERENCE_CONFIG_PATH`), validated at startup; per-category limits (D-17) |
| US-004 | Confirmation shown only after the backend accepted and stored the registration |
| US-005 | PostgreSQL row and raw JSON copy written in one transaction (AR-05); one registration per email (D-18) |
| US-006 | Plain-text confirmation email to the participant after commit |
| US-007 | Notification to every organizer address with the JSON copy attached |
| US-008 | Excel export for the organizer (HTTP Basic over HTTPS, D-24) |

Tests: 192 backend (unit, integration, architecture, acceptance), 33 frontend, 17 end-to-end, all passing.

## Known limitations

- Emails are not resent after a failure; the failure is logged with the registration id (D-16).
- Registrations and JSON copies are deleted 12 months after the conference by an operations procedure; there is no deletion feature (D-19).
- Rate limits are in memory, per backend instance.
- The backend image needs the jar built on the host first (`./mvnw -DskipTests package`); no JDK build image is pinned.
- Container images and the production nginx reverse proxy were not scanned or configured here (no tool or configuration in the inputs).
- User interface texts are English; data may contain any Unicode text.
- Mutation scores (record only): backend 69 % (unit tests), frontend 65 % (F-02).

## Must be tested manually by a human

| What | How | Why not automated |
|---|---|---|
| Google reCAPTCHA v2 | Production keys in `.env`, `RECAPTCHA_TEST_MODE` off: one real registration succeeds; a forged token is refused | live Google service (environments.md) |
| SMTP delivery | Real SMTP account (`SMTP_*`): both emails arrive in a real mailbox with correct Slovenian characters and the JSON attachment | external service |
| TLS and reverse proxy | External nginx with Let's Encrypt: HTTPS, HTTP→HTTPS redirect, `/` to the frontend, `/api` to the backend, `X-Forwarded-Proto` set, security headers once (KP-02) | production infrastructure |
| Organizer HTTPS-only | Export over plain HTTP from another host returns 403; over HTTPS with credentials returns the workbook (SR-06) | needs the proxy |
| Workbook in Excel | Open the export in Microsoft Excel; text and Slovenian characters display correctly | desktop application |
| Startup guards in production | `APP_ENVIRONMENT=production` with test mode on, empty keys or `ORGANIZER_HTTPS_ONLY=false` refuses to start | production configuration |
| Retention procedure | The organizer has a documented procedure to delete data 12 months after the conference (D-19) | organisational |

## Decisions pending review

| D | Phase | Decision |
|---|---|---|
| D-01 | 0 | Gitleaks scans only this run's commits; earlier history had 142 matches from earlier runs |
| D-02 | 0 | OSS Index analyser disabled (needs Sonatype credentials) |
| D-03, D-04 | 0 | Suppressed false positives: CVE-2025-7962 on angus-activation (KP-07), CVE-2025-15104 on hibernate-validator |
| D-05..D-08 | 0 | Host Java 21.0.12.1, Docker 29.3.1, Compose 5.1.1 and the cloc image report other versions than pinned |
| D-10 | 0 | Work committed on local branch `run/tanej-01_…`; nothing pushed |
| D-13 | 0 | 2 Moderate `qs` advisories in Stryker's dependencies accepted (dev only) |
| D-14..D-19 | 1 | Open questions OQ-01..OQ-06 answered conservatively (options per type, one configurable mandatory consent, registration kept when email fails, limit 1 per category by default, one registration per email, 12-month retention) |
| D-20 | 2 | Redocly CLI 2.62.0 and host Python `jsonschema` used only to validate contracts |
| D-21 | 4 | Suppressed SpotBugs false positives (`backend/spotbugs-exclude.xml`) |
| D-22 | 4 | Commit 45da222 does not build alone; history not rewritten |
| D-23 | 4 | `.env` line 2 is not `KEY=value`; `scripts/compose.sh` passes the needed keys |
| D-25 | 6 | Suppressed Gitleaks false positive on a test value (`02_output/.gitleaksignore`) |

Resolved by the human: D-09 (Node.js already installed), D-11 and D-12 (vitest 4.1.11, coverage-v8 4.1.11, jscpd 5.4.0 instead of the pinned versions), D-24 (HTTP Basic accepted; Semgrep rule suppressed on the contract line).
