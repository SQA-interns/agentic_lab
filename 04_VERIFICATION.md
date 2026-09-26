# Verification contract and Definition of Done

Verification is independent evidence, not a restatement of implementation.
Re-read the product and engineering contracts, acceptance criteria, and
specification. Inspect for missing/incorrect/unrequested behavior, security
failures, architecture violations, regressions, and runtime failures. Classify
findings Critical, Major, or Minor; for every failure, execute and log
Verify → Fix → Re-verify.

## Required checks

| Area | Evidence required |
|---|---|
| Static quality | Backend formatting, SpotBugs, PMD and duplication; frontend ESLint with 0 errors, `tsc --noEmit` with 0 errors, formatting and duplication |
| Automated tests | Complete suite passes; report unit, integration, frontend/component, API/contract and E2E results separately, plus unit/integration coverage |
| Security | Semgrep, OWASP Dependency-Check and `npm audit`; no unresolved Critical/High finding. Report this accurately as self-verification, not an external security audit. |
| Architecture | Agent-written ArchUnit rules verify the declared architecture; dependency-cycle check runs; report violations |
| Container runtime | Build/start the defined deployment and demonstrate external + student registration, PostgreSQL persistence, raw JSON backup, participant + organizer email in local SMTP, organizer JSON attachment, protected Excel export, and health/readiness |
| Documentation | Acceptance criteria, specification, test strategy, verification report, release notes, run log and run summary exist; unknown/unverified behavior is explicit |

Passing tests alone is not runtime correctness or complete requirements
coverage. The verification report must map each acceptance criterion to its
evidence, list commands/results and environment, describe known limitations,
and name the final Git SHA.

The run is done only when all mandatory checks pass, or an explicitly permitted
exception is documented with its scope, risk, and reason.
