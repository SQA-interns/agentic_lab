# Human Inputs Manifest

Every secret, key, account, or external service this run might need,
decided **before** the run starts, as part of `PREFLIGHT_CHECKLIST.md`.
Resolve every row before preflight is considered complete.

| Input | Needed for | Status | Notes |
| --- | --- | --- | --- |
| reCAPTCHA (or the anti-automation provider named in `02_Technical/SECURITY_REQUIREMENTS.md`) site key + secret key | Backend verification of automated-submission protection | ☐ Provided&nbsp;&nbsp;☐ Test-mode only | If test-mode only, production-mode behaviour must still be exercised with a mocked verification endpoint in Verification (DoD §3) — not skipped. A blank site key must never be allowed to start in production mode (this exact defect was found and fixed in an earlier run). |
| SMTP credentials | Participant/organizer email | ☐ Provided&nbsp;&nbsp;☐ Local catcher only (e.g. Mailpit/GreenMail) | If local-only, real external delivery is an open item in `RELEASE_NOTES.md`, not a silent gap. |
| Organizer export credentials | Excel export access control | ☐ Provided&nbsp;&nbsp;☐ Generated locally by the agent | If generated locally, record where (env file only) — never in chat, logs, or committed source. |
| NVD API key | OWASP Dependency-Check | ☐ Provided&nbsp;&nbsp;☐ Not provided | If not provided, `DEFINITION_OF_DONE.md` §3 permits a documented substitute scanner (e.g. Trivy) — this is not a blocker and does not need escalation. |
| Container registry credentials | Pulling private base images, if any | ☐ Provided&nbsp;&nbsp;☐ N/A — public images only | |
| Anything else `01_Business/` or `02_Technical/` implies but does not name here | | ☐ N/A&nbsp;&nbsp;☐ Escalated per `CONSTITUTION.md` §3 | |

Add rows as new inputs are frozen into a future copy of this package;
never remove a row mid-run. A row marked "Escalated" must have a
matching entry in `docs/decisions-log.md` once the run starts.
