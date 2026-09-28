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
finalization, classified per `SEVERITY_TAXONOMY.md`.

This remains a self-verification result and must not be represented as
an independent external security review.

## 4. Architecture

Execute architecture checks that verify the architecture declared in
`<IMPLEMENTATION_ROOT>/docs/specification.md`.

At minimum:

- ArchUnit rules written by the agent for that declared architecture;
- a dependency-cycle check.

The scaffold must not supply pre-written layering rules that force a
controller → service → repository design.

Architecture violations must be recorded.

## 4a. Test integrity and mutation testing

- The acceptance-test freeze held: every file listed in
  `docs/acceptance/MANIFEST.sha256` still hashes to the recorded value.
  A mismatch is a Critical finding regardless of the reason.
- Run a mutation-testing pass (a tool matching the frozen toolchain in
  `02_Technical/TECH_STACK.md`, e.g. PIT for the backend, Stryker for
  the frontend) against security- and domain-critical code — at
  minimum, validation, authentication/anti-automation, and
  persistence/backup logic. This is **measured and recorded**, not
  gated on a threshold; a surviving mutant that should plausibly have
  been caught is a finding, classified per `SEVERITY_TAXONOMY.md`, not
  an automatic failure.
- Coverage alone (statement/branch %) is not sufficient evidence of
  test quality; report it alongside the mutation result, not instead
  of it.

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

- `<IMPLEMENTATION_ROOT>/docs/acceptance-criteria.md`
- `<IMPLEMENTATION_ROOT>/docs/specification.md`
- `<IMPLEMENTATION_ROOT>/docs/contracts/`
- `<IMPLEMENTATION_ROOT>/docs/acceptance/MANIFEST.sha256`
- `<IMPLEMENTATION_ROOT>/docs/test-strategy.md`
- `<IMPLEMENTATION_ROOT>/docs/verification-report.md`
- `<IMPLEMENTATION_ROOT>/docs/decisions-log.md`
- `<IMPLEMENTATION_ROOT>/RELEASE_NOTES.md`
- `<IMPLEMENTATION_ROOT>/README.md` — a run guide: how to build and run
  the delivered system, and where to find each of the above. Verify it
  by actually following it, not by inspection alone.

Unknown or unverified behaviour must be documented rather than assumed.

## 7. Experiment record

The following must exist:

- `<STATISTICS_ROOT>/run-log.json`
- `<STATISTICS_ROOT>/run-summary.md`

A run without the required experimental record is incomplete.