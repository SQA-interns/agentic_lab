# Severity Taxonomy

Use exactly these four levels everywhere a finding is classified:
verification reports, `run-log.json` (`verificationFindings`),
security-scanner triage, and `docs/decisions-log.md`. Do not use any
other label ("Major", "Minor", "Blocker", or similar) anywhere in this
run's artefacts, and do not introduce a fifth level.

| Severity | Definition | Example |
| --- | --- | --- |
| **Critical** | Exploitable now, or a total loss of a core user story's function, with no workaround. | Unauthenticated access to the organizer export; a registration that silently fails to persist. |
| **High** | Exploitable under realistic conditions, or a core user story degraded for a plausible subset of users. | Missing security headers on served HTML; a rate limit that locks out every participant sharing one address. |
| **Medium** | A real defect with limited blast radius, or a control that is present but weaker than the requirement. | A dependency CVE with no reachable exploit path in this application's actual usage; a duplicated validation rule. |
| **Low** | Cosmetic, stylistic, or a theoretical/false-positive finding worth recording but not worth fixing before merge. | A lint warning; a scanner match on constant, non-user-controlled input. |

## Mapping tool output onto this table

Semgrep, OWASP Dependency-Check, npm audit, and Trivy already report in
Critical/High/Medium/Low — use their output directly. For a tool that
reports differently (e.g. PMD's Priority 1–5, SpotBugs' rank), map onto
this table explicitly in the verification report the first time that
tool is used, and reuse the same mapping for the rest of the run.

## Where this is a gate vs. a measurement

`DEFINITION_OF_DONE.md` §3 gates on this table directly: no unresolved
Critical or High finding before finalization. Medium and Low findings
are recorded, triaged (fixed, accepted-with-justification, or open
item), and reported — they do not block finalization by themselves.
