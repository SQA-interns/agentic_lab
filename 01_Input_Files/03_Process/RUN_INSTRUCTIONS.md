# Run Instructions — Single Agent / Classical SDD

You are the only development agent responsible for this run.

## Path conventions

This experiment input bundle is portable.

Define:

- `INPUT_ROOT` as the `01_Input_Files/` directory containing this
  experiment package.
- `WORKSPACE_ROOT` as the parent directory of `INPUT_ROOT`.
- `IMPLEMENTATION_ROOT` as `<WORKSPACE_ROOT>/02_Implementation`.
- `STATISTICS_ROOT` as `<WORKSPACE_ROOT>/03_Run-Statistics`.

All paths inside the input bundle are relative to `INPUT_ROOT`.

All generated application artefacts are written under
`IMPLEMENTATION_ROOT`.

All experimental logs and statistics are written under
`STATISTICS_ROOT`.

Do not assume any repository name or absolute filesystem path.

Example layout after copying this package into any project:

```
<WORKSPACE_ROOT>/
├── 01_Input_Files/          ← INPUT_ROOT
├── 02_Implementation/       ← IMPLEMENTATION_ROOT
└── 03_Run-Statistics/       ← STATISTICS_ROOT
```

## Immutable inputs

Everything under `INPUT_ROOT` is read-only.

Do not create, modify or delete experiment inputs.

## Output locations

Required artefacts:

- `<IMPLEMENTATION_ROOT>/docs/acceptance-criteria.md`
- `<IMPLEMENTATION_ROOT>/docs/specification.md`
- `<IMPLEMENTATION_ROOT>/docs/test-strategy.md`
- `<IMPLEMENTATION_ROOT>/docs/verification-report.md`
- `<IMPLEMENTATION_ROOT>/RELEASE_NOTES.md`
- `<STATISTICS_ROOT>/run-log.json`
- `<STATISTICS_ROOT>/run-summary.md`

## Frozen tooling scaffold

`IMPLEMENTATION_ROOT` starts as a **tooling scaffold**, not a solution.

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

Create exactly:

`<IMPLEMENTATION_ROOT>/docs/acceptance-criteria.md`

Do not create production code or tests.

Commit the completed phase.

---

## Phase 2 — Specification

Read:

- all business inputs;
- generated Acceptance Criteria;
- all files under `02_Technical/`;
- `04_Skills/write-specification/SKILL.md`.

Create exactly:

`<IMPLEMENTATION_ROOT>/docs/specification.md`

Do not create production code or tests.

Commit the completed phase.

---

## Phase 3 — Implementation

Read:

`04_Skills/implement/SKILL.md`

Implement the approved Specification under `IMPLEMENTATION_ROOT`.

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

Create exactly:

`<IMPLEMENTATION_ROOT>/docs/test-strategy.md`

Commit the completed phase.

---

## Phase 5 — Verification

Read:

- `03_Process/DEFINITION_OF_DONE.md`;
- `04_Skills/verify/SKILL.md`.

Re-read all original requirements.

Perform the full verification independently from the implementation
phase.

Create exactly:

`<IMPLEMENTATION_ROOT>/docs/verification-report.md`

For every failure execute:

Verification → Fix → Re-verification

Log every loop.

Commit the completed phase.

---

## Phase 6 — Finalization

Read:

`04_Skills/finalize-run/SKILL.md`

Create exactly:

- `<IMPLEMENTATION_ROOT>/RELEASE_NOTES.md`
- `<STATISTICS_ROOT>/run-summary.md`

Finalize:

`<STATISTICS_ROOT>/run-log.json`

Record the final commit.

Do not modify the shared baseline or another experiment branch.

---

## Logging

At run start copy:

`03_Process/run-log.template.json`

to:

`<STATISTICS_ROOT>/run-log.json`

Update it during the run.

Record `experimentStart` immediately when the run begins and
`experimentEnd` when finalization completes. Phase timestamps must
remain consistent with those two values.

Never reconstruct known timestamps after the fact.

Never invent unavailable measurements.

## Experimental fairness

Do not read instructions from files outside `INPUT_ROOT` unless they
are explicitly listed by these Run Instructions.

Do not optimize the implementation for the metrics.
