---
name: write-unit-tests
description: Add unit and implementation-level tests after implementation, additively, for Classical SDD.
---

# Procedure

This phase happens after Implementation, on top of the acceptance
tests that were already written and frozen in Phase 3. It is
**additive only** — see `CONSTITUTION.md` §2. You may not edit,
delete, or weaken anything under an `acceptance/` path or `e2e/`.

## Sources

- Specification and implementation, for unit and
  implementation-specific tests;
- Specification and Acceptance Criteria, for any additional
  API/contract/integration coverage the acceptance suite didn't reach
  (e.g. failure paths, internal edge cases not observable purely
  black-box).

## Include appropriate

- unit tests;
- component tests;
- additional API/integration tests (beyond the frozen acceptance
  suite);
- negative/edge-case tests at the implementation level;
- relevant security tests.

ArchUnit tests, if used, must verify the architecture declared in the
Specification. Do not introduce generic unused layering rules.

## First complete run

Run the **full** suite — the frozen acceptance tests plus the new unit
tests together — and record the result before repairing any failure,
as `firstTestRun` in `<STATISTICS_ROOT>/run-log.json`.

For each failure, classify it honestly before fixing anything:

- **Implementation defect** — the code is wrong; fix the code.
- **Unit-test defect** — the new unit test itself is wrong (e.g. wrong
  mock setup, wrong assumption about a library's behaviour); fix the
  unit test, and say so in `docs/test-strategy.md`.
- **A frozen acceptance test appears wrong** — this is not something
  you fix here. File a test-defect request per `CONSTITUTION.md` §2 and
  `docs/decisions-log.md`, and escalate. Do not touch the file.

Never weaken a correct test — acceptance or unit — merely to make an
incorrect implementation pass.

## Output

Append to `<IMPLEMENTATION_ROOT>/docs/test-strategy.md`:

- §4 onward: unit/component/additional-integration test rationale;
- the first-complete-run result and failure classification table;
- the final complete-run result after repairs.
