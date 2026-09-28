# Definition of Done — authoritative completion contract

Each gate has `pass`, `fail`, `blocked`, or `not-run` status and evidence in
`03_Metrics`. Missing evidence is never pass. This is system self-verification,
not independent audit. Agent-written tests are necessary evidence, not proof
of completeness. Trace fixed product cases to checks; do not remove assertions,
exclude failing tests, weaken thresholds, or mark required cases optional to
obtain a pass. A genuinely incorrect test may be corrected with its old/new
assertion and input-contract justification recorded. Read this file before
specification and at finalization.

| Gate | Required evidence |
|---|---|
| D-01 Product | All US-001…US-008 behaviors and applicable data rules demonstrated, with acceptance IDs mapped to tests/probes. Include valid and invalid external/student forms, Unicode/whitespace, active/unknown/inactive options, applicable unchecked mandatory consent, confirmation ordering, storage, emails, and export. |
| D-02 Build/tests | Backend and frontend production builds pass. Required unit, PostgreSQL integration/API, component, and Playwright journeys pass. Report pass/fail/error/skip separately; skipped required checks do not satisfy a gate. |
| D-03 Static quality | Backend Spotless and frontend Prettier checks pass; ESLint and TypeScript have zero errors. SpotBugs, PMD/CPD and jscpd run; record findings and duplication. Do not invent a coverage/complexity threshold. |
| D-04 Architecture | All AR/ST rules checked against actual implementation; ArchUnit checks declared module boundaries and cycles. No unresolved violation of mandatory rules. |
| D-05 Security | Semgrep, OWASP Dependency-Check and npm audit execute with identified rules/database state; no unresolved Critical/High finding. A scanner error, missing database or unavailable credentials is blocked, never zero findings. Additional scanners do not replace required ones. |
| D-06 Container runtime | Build/start containers and demonstrate both registration forms, PostgreSQL rows, matching JSON backups, participant email, organizer email plus JSON attachment, authorized Excel export and unauthorized rejection, readiness, and retained data after container recreation. Capture actual browser/API/DB/SMTP evidence. |
| D-07 Failure/security behavior | Backend rejects invalid input/options/captcha; production cannot activate test-mode bypass. Demonstrate partial storage failure handling, retry/idempotency, and email retry without losing an accepted registration. Inspect error/privacy controls. |
| D-08 Reproduction/reporting | Generated specification and implementation README match reality. Run record, event log, summary and referenced evidence exist; measured values have sources; unavailable values have reasons; unresolved issues are explicit. |

All gates must pass for `done`. Otherwise report `incomplete` with failed,
blocked or unrun gates. An unavoidable dependency advisory within the fixed
stack remains a blocked completion gate; document triage, do not silently
upgrade across the input contract. Separate functional completion from the
overall verdict so this limitation remains visible in comparisons.

Coverage, LOC, duplication, test counts and time are diagnostics, not targets
to maximize or minimize at the expense of behavior. Functional tests cannot
replace architecture, security or live-runtime verification.
