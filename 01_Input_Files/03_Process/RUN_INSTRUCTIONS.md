# Run Instructions — Single Agent / Classical SDD

You are the only development agent responsible for this run.

## Path conventions

This experiment package is portable.

`INPUT_ROOT` is the directory containing:

- `01_Business/`
- `02_Technical/`
- `03_Process/`
- `04_Skills/`
- `05_Scaffold/`

This file lives at `03_Process/RUN_INSTRUCTIONS.md` inside that bundle.

All input paths such as:

- `01_Business/...`
- `02_Technical/...`
- `03_Process/...`
- `04_Skills/...`
- `05_Scaffold/...`

are relative to `INPUT_ROOT`.

`WORKSPACE_ROOT` is the parent directory of `INPUT_ROOT`.

Generated application artefacts are written under:

`<WORKSPACE_ROOT>/02_Implementation/`

(`IMPLEMENTATION_ROOT`)

Experimental records are written under:

`<WORKSPACE_ROOT>/03_Run-Statistics/`

(`STATISTICS_ROOT`)

Do not assume a repository name, absolute filesystem path or operating
system-specific path.

The same package may be copied into any project as:

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

The immutable tooling scaffold lives at:

`05_Scaffold/`

It is part of `INPUT_ROOT` and must not be modified.

## Start-of-run setup

Execute this sequence before Phase 1. Do not skip or reorder it.

1. Record `experimentStart`.
2. Confirm a clean baseline working tree.
3. Copy `05_Scaffold/*` to `IMPLEMENTATION_ROOT/`.
4. Copy `03_Process/run-log.template.json` to
   `<STATISTICS_ROOT>/run-log.json`.
5. Read `03_Process/METRICS.md` completely.
6. Begin Phase 1 — Acceptance Criteria.

`experimentStart` is recorded immediately before any other run work.
Copying the scaffold is included in total run time. The cost is
negligible; the rule must be identical for every agent.

Work only under `IMPLEMENTATION_ROOT` after the copy.

The scaffold is tooling only. The agent must implement business
behaviour on top of the copied scaffold. The agent must not replace the
frozen technology stack or discard the configured measurement tools.

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

## Experiment measurement

`METRICS.md` is the authoritative definition of what is measured and
how it is measured. It is read in the start-of-run sequence above.

Do not introduce additional metrics during the run.

Do not estimate values that cannot be observed.

Update `<STATISTICS_ROOT>/run-log.json` during the run. It must already
exist from the start-of-run setup.

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

Before completing the run, ensure every metric required by
`03_Process/METRICS.md` has either:

- a measured value; or
- `null` with an explicit reason.

Record the final commit.

Do not modify the shared baseline or another experiment branch.

---

## Logging

Record `experimentStart` immediately when the run begins and
`experimentEnd` when finalization completes. Phase timestamps must
remain consistent with those two values.

Never reconstruct known timestamps after the fact.

Never invent unavailable measurements.

## Experimental fairness

Do not read instructions from files outside `INPUT_ROOT` unless they
are explicitly listed by these Run Instructions.

Do not optimize the implementation for the metrics.
