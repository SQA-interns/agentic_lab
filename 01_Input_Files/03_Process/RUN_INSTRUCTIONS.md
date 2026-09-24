# Run Instructions — Single Agent / Classical SDD

You are the only development agent responsible for this run.

## Immutable inputs

Everything under:

`01_Input_Files/`

is read-only.

Do not create, modify or delete experiment inputs.

## Output locations

Application, generated development artefacts and the frozen tooling
scaffold:

`02_Implementation/`

Experimental records:

`03_Run-Statistics/`

Required artefact paths:

- `02_Implementation/docs/acceptance-criteria.md`
- `02_Implementation/docs/specification.md`
- `02_Implementation/docs/test-strategy.md`
- `02_Implementation/docs/verification-report.md`
- `02_Implementation/RELEASE_NOTES.md`
- `03_Run-Statistics/run-log.json`
- `03_Run-Statistics/run-summary.md`

## Frozen scaffold

`02_Implementation/` starts as a **tooling scaffold**, not a solution.

The agent must implement business behaviour on top of this scaffold.
The agent must not replace the frozen technology stack or discard the
configured measurement tools.

The scaffold does **not** prescribe software architecture, package
boundaries, REST design or persistence mapping.

Every experimental run must start from the same frozen Git baseline
commit and a fresh agent context. One run must not continue from the
working tree or commits of another run.

## Required process

Follow exactly:

User Stories
→ Acceptance Criteria
→ Specification
→ Implementation
→ Tests
→ Verification
→ Finalization

Do not skip, reorder, combine or anticipate phases.

---

## Phase 1 — Acceptance Criteria

Read:

- all files under `01_Business/`;
- `03_Process/ACCEPTANCE_CRITERIA_RULES.md`;
- `04_Skills/derive-acceptance-criteria/SKILL.md`.

Create:

`02_Implementation/docs/acceptance-criteria.md`

Do not create production code or tests.

Commit the completed phase.

---

## Phase 2 — Specification

Read:

- all business inputs;
- generated Acceptance Criteria;
- all files under `02_Technical/`;
- `04_Skills/write-specification/SKILL.md`.

Create:

`02_Implementation/docs/specification.md`

Do not create production code or tests.

Commit the completed phase.

---

## Phase 3 — Implementation

Read:

`04_Skills/implement/SKILL.md`

Implement the approved Specification.

Do not create the feature test suite.

Build, formatting, linting and type-check feedback are permitted.

Record the first time the primary registration happy path becomes
executable.

Commit the completed phase.

---

## Phase 4 — Tests

Read:

`04_Skills/write-tests/SKILL.md`

Only now create the test suite.

Record the result of the first complete test execution before repairing
failures.

Create:

`02_Implementation/docs/test-strategy.md`

Commit the completed phase.

---

## Phase 5 — Verification

Read:

- `03_Process/DEFINITION_OF_DONE.md`;
- `04_Skills/verify/SKILL.md`.

Re-read all original requirements.

Perform the full verification independently from the implementation
phase.

Create:

`02_Implementation/docs/verification-report.md`

For every failure execute:

Verification → Fix → Re-verification

Log every loop.

Commit the completed phase.

---

## Phase 6 — Finalization

Read:

`04_Skills/finalize-run/SKILL.md`

Create:

- `02_Implementation/RELEASE_NOTES.md`
- `03_Run-Statistics/run-summary.md`

Finalize:

`03_Run-Statistics/run-log.json`

Record the final commit.

Do not modify the shared baseline or another experiment branch.

---

## Logging

At run start copy:

`01_Input_Files/03_Process/run-log.template.json`

to:

`03_Run-Statistics/run-log.json`

Update it during the run.

Record `experimentStart` immediately when the run begins and
`experimentEnd` when finalization completes. Phase timestamps must
remain consistent with those two values.

Never reconstruct known timestamps after the fact.

Never invent unavailable measurements.

## Experimental fairness

Do not read instructions from files outside the frozen experiment input
unless they are explicitly listed by these Run Instructions.

Do not optimize the implementation for the metrics.