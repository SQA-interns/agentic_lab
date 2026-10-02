# Release notes

> Written in: phase 7 · Agent: writes

Version 0.1.0. Verification: `06_verification-report.md` (a self-check, not an independent review).

## Delivered

| Story | Delivered |
|---|---|
| US-001 | External participant form (first name, last name, email, organization), validated in the browser and again in the backend; anti-automation check verified by the backend. |
| US-002 | Student form (adds study institution, study programme, student ID). |
| US-003 | Workshops, events, meals and other activities come from a JSON options file; only active options are offered and accepted; a change needs a backend restart, no code change. |
| US-004 | Confirmation in the application only after the registration is stored; clear messages for rejections and failures. |
| US-005 | Each accepted registration is one database row plus one JSON file, written together or not at all; both survive recreation of the containers. |
| US-006 | Plain-text confirmation email to the participant. |
| US-007 | Plain-text notification to every organizer address, with the JSON copy attached. |
| US-008 | Excel export of all registrations for the one organizer account; refused without it. |

Also: rate limits and a request-size limit, security headers, health and readiness endpoints, non-root container images, a local Docker Compose stack.

## Known limitations

- The form and the emails are in Slovenian only (the inputs name no language).
- One organizer account with HTTP Basic over HTTPS (D-16, F-01); no organizer user interface, the export is a download link.
- Changing options or the consent wording needs a backend restart.
- Rate limits are kept in memory per backend instance; run a single backend instance.
- Emails are sent while the participant waits; a failed email does not undo the registration and is only logged (D-10). Nothing resends it.
- Registrations are never deleted automatically (D-13); the organizer deletes them by hand (`backend/README.md`, "Operations").
- A second registration with the same email is accepted as a separate one (D-12).
- The frontend container needs a host named `backend` on its network when it starts.
- Development and checks ran on Oracle JDK 21.0.11, Node 24.10.0 and npm 10.9.4, not the pinned versions (D-01, D-02, D-03); `npm ci` warns that 37 development packages want Node 24.11 or later.
- Five Moderate advisories remain in development-only test tooling (F-05); nothing of it is in the shipped images.
- Complexity was not measured: `tech-stack.md` lists no tool for it.

## Must be tested manually by a human

| What | Why it could not be automated | How |
|---|---|---|
| Google reCAPTCHA v2 with production keys | no call to Google in tests; only a mocked verification endpoint | set `RECAPTCHA_SITE_KEY` and `RECAPTCHA_SECRET_KEY`, `RECAPTCHA_TEST_MODE=false`; submit one real registration; check that the widget appears (the page's Content-Security-Policy must not block it) and that a registration without solving it is refused |
| Email delivery through the real SMTP server | tests use Mailpit | register with a real mailbox; check the participant confirmation and the organizer notification with its attachment, including č, š, ž in the subject and body |
| HTTPS, redirects and `/api` routing at the external reverse proxy | no TLS locally | check that HTTP redirects to HTTPS, that `/` and `/api` reach the right container, that the proxy sets `X-Forwarded-Proto` and `X-Forwarded-For`, and that the export is refused with 403 over plain HTTP and works over HTTPS |
| Production start-up | needs the production settings | start with `APP_ENVIRONMENT=production`; check that it refuses to start with the test mode on or with empty keys |
| The exported workbook in Excel | tests read it with a library | open it in Excel; check columns, characters and that no cell is treated as a formula |
| Wording | product decision | review the Slovenian texts of the form and emails and the consent text (D-09) |

## Decisions pending review

Blocking decisions D-05 and D-16 were answered by the human. D-01, D-03 and D-04 were accepted. The rest are non-blocking and await review; details in `decisions-log.md`.

| D | Decision |
|---|---|
| D-02 | Work continued on Node 24.10.0 instead of the pinned 24.13.0; no step failed because of it. |
| D-06 | OSS Index analyser of Dependency-Check disabled (needs credentials that are not listed); the NVD analyser runs. |
| D-07 | Suppressed false positives in Dependency-Check: CVE-2025-7962 matched on `angus-activation` (the fix is in the shipped `angus-mail` 2.0.5), CVE-2025-15104 matched on Hibernate Validator (it concerns the Nu Html Checker). |
| D-08 | Every active option is offered to both registration types (OQ-01). |
| D-09 | One mandatory consent, for personal-data processing, with a default Slovenian wording (OQ-02). |
| D-10 | A failed email never rejects a stored registration (OQ-03). |
| D-11 | No limit per category; an option may be chosen once (OQ-04). |
| D-12 | A second registration with the same email is accepted (OQ-05). |
| D-13 | No automatic deletion; proposed retention 12 months after the conference (OQ-06). **Needs a confirmed period (SB-13).** |
| D-14 | Development-only tools added: Redocly CLI container, `ajv` from the lock file (contract validation). |
| D-15 | Development-only tools added: Playwright container image, `@types/node` 24.19.1. |
| D-17 | Suppressed false positive in gitleaks: generated start-up password of the bootstrap application in a phase 3 test log (F-03). |
| D-18 | Specification corrected: stricter start-up checks; fixed backend host name in nginx (F-02, fixed). |
| D-19 | DoD-08 and DoD-09 evidenced in phase 7, because their files are written there. |

Also suppressed, by human decision D-16: Semgrep rule `use-of-basic-authentication` on one line of `02_contracts/openapi.yaml` (F-01). SpotBugs pattern `EI_EXPOSE_REP2` is excluded for constructor injection (`backend/spotbugs-exclude.xml`). Nine commits exceed the size guide (F-06).

The secret scan covers this branch only (D-04); 54 matches in other branches of the repository, including one credential-like match in `03_statistics/claude-desktop-transcript.md`, were left untouched and may deserve a look.
