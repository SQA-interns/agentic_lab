# Definition of Done

A run is complete only when all mandatory checks below have passed or an
explicitly permitted exception has been documented.

## 1. Static checks

Backend:

- formatting check passes;
- SpotBugs executed;
- PMD executed;
- duplication measured.

Frontend:

- ESLint: 0 errors;
- TypeScript check: 0 errors;
- duplication measured;
- formatting check passes.

## 2. Tests

Execute the complete automated test suite.

Record separately:

- unit test result;
- integration test result;
- frontend/component test result;
- API/contract test result;
- E2E result;
- unit coverage;
- integration coverage.

The final required test suite must pass before finalization.

## 3. Security

Execute:

- Semgrep;
- OWASP Dependency-Check;
- npm audit.

There must be no unresolved Critical or High security findings before
finalization.

This remains a self-verification result and must not be represented as
an independent external security review.

## 4. Architecture

Execute architecture checks that verify the architecture declared in
`02_Implementation/docs/specification.md`.

At minimum:

- ArchUnit rules written by the agent for that declared architecture;
- a dependency-cycle check.

The scaffold must not supply pre-written layering rules that force a
controller → service → repository design.

Architecture violations must be recorded.

## 5. Container execution

Build and start the deployment using the defined container setup.

Against the running system demonstrate at minimum:

1. one successful external registration;
2. one successful student registration;
3. persistence in PostgreSQL;
4. creation of raw JSON backup;
5. participant email reaching the local SMTP environment;
6. organizer email reaching the local SMTP environment;
7. organizer JSON attachment;
8. Excel export;
9. backend health/readiness response.

Passing tests alone does not establish runtime correctness.

## 6. Documentation

The following must exist:

- `02_Implementation/docs/acceptance-criteria.md`
- `02_Implementation/docs/specification.md`
- `02_Implementation/docs/test-strategy.md`
- `02_Implementation/docs/verification-report.md`
- `02_Implementation/RELEASE_NOTES.md`

Unknown or unverified behaviour must be documented rather than assumed.

## 7. Experiment record

The following must exist:

- `03_Run-Statistics/run-log.json`
- `03_Run-Statistics/run-summary.md`

A run without the required experimental record is incomplete.