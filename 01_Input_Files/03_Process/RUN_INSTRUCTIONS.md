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

A human may copy the entire `01_Input_Files/` directory into another
workspace as a setup step. That is not part of the agent's run.

Expected workspace layout:

```
<WORKSPACE_ROOT>/
├── AGENTS.md                ← read first
├── .claude/                 ← copied from 05_Scaffold/.claude/ at setup
├── 01_Input_Files/          ← INPUT_ROOT (read-only)
├── 02_Implementation/       ← IMPLEMENTATION_ROOT (work here)
├── 03_Run-Statistics/       ← STATISTICS_ROOT (logs)
└── 04_External-Audit/       ← not yours; filled in after the run
```

## Immutable inputs

Everything under `INPUT_ROOT` is read-only.

Do not create, modify or delete experiment inputs. This is also
enforced by a hook — see the start-of-run setup below — but the rule
applies even if that hook is not active in your harness. Read
`CONSTITUTION.md` now; it governs the whole run and is not repeated in
full here.

## Output locations

Required artefacts:

- `<IMPLEMENTATION_ROOT>/docs/acceptance-criteria.md`
- `<IMPLEMENTATION_ROOT>/docs/specification.md`
- `<IMPLEMENTATION_ROOT>/docs/contracts/` (the API contract)
- `<IMPLEMENTATION_ROOT>/docs/acceptance/MANIFEST.sha256` (the frozen
  acceptance-test hash manifest)
- `<IMPLEMENTATION_ROOT>/docs/test-strategy.md`
- `<IMPLEMENTATION_ROOT>/docs/verification-report.md`
- `<IMPLEMENTATION_ROOT>/docs/decisions-log.md`
- `<IMPLEMENTATION_ROOT>/RELEASE_NOTES.md`
- `<STATISTICS_ROOT>/run-log.json`
- `<STATISTICS_ROOT>/run-summary.md`

## Frozen tooling scaffold

The immutable tooling scaffold lives at:

`05_Scaffold/`

It is part of `INPUT_ROOT` and must not be modified.

## Before start-of-run setup: preflight (human step, untimed)

`03_Process/PREFLIGHT_CHECKLIST.md` must be complete, and every row of
`03_Process/HUMAN_INPUTS_MANIFEST.md` resolved, before the sequence
below begins. This is a human step against the workspace, not part of
the agent's timed run — it exists so a missing prerequisite (a
container runtime that isn't installed, a version pin that doesn't
resolve) is caught before `experimentStart`, not discovered mid-run.

## Start-of-run setup

Execute this sequence before Phase 1. Do not skip or reorder it.

1. Record `experimentStart`.
2. Confirm a clean baseline working tree.
3. Copy only the contents of `05_Scaffold/` into
   `IMPLEMENTATION_ROOT/`, so that `backend/`, `frontend/` and
   `docker-compose.yml` appear directly under `IMPLEMENTATION_ROOT/`.
   Do not copy `01_Business/`, `02_Technical/`, `03_Process/` or
   `04_Skills/`. Do not create `IMPLEMENTATION_ROOT/05_Scaffold/`.
4. Separately, copy `05_Scaffold/.claude/` to
   `<WORKSPACE_ROOT>/.claude/` — a sibling of `INPUT_ROOT`, not inside
   `IMPLEMENTATION_ROOT`. This activates the input-protection and
   acceptance-test-freeze hook (if your harness supports Claude
   Code-style hooks) for the rest of the run.
5. Copy `03_Process/run-log.template.json` to
   `<STATISTICS_ROOT>/run-log.json`.
6. Read `03_Process/METRICS.md`, `03_Process/CONSTITUTION.md`, and
   `03_Process/SEVERITY_TAXONOMY.md` completely.
7. Create an empty `<IMPLEMENTATION_ROOT>/docs/decisions-log.md` with
   just its heading — every escalation from here on gets an entry.
8. Begin Phase 1 — Acceptance Criteria.

`experimentStart` is recorded immediately before any other run work.
Copying the scaffold and the hook is included in total run time. The
cost is negligible; the rule must be identical for every agent.

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
→ Specification (+ API contract)
→ Acceptance Tests (frozen, before implementation)
→ Implementation (build until the frozen tests pass)
→ Unit Tests (additive only)
→ Verification
→ Finalization

Do not skip, reorder, combine or anticipate phases. The Acceptance
Tests phase exists specifically so tests are never written by looking
at the implementation they're meant to check — see `CONSTITUTION.md`
§2 for why this is non-negotiable in this experiment.

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

- `<IMPLEMENTATION_ROOT>/docs/specification.md`
- `<IMPLEMENTATION_ROOT>/docs/contracts/` (the API contract — concrete
  enough that Phase 3 can write a real HTTP test against it)

Do not create production code or tests.

Commit the completed phase.

---

## Phase 3 — Acceptance Tests

Read:

`04_Skills/write-acceptance-tests/SKILL.md`

Write the acceptance-test suite from the Acceptance Criteria and the
API contract only — production code does not exist yet beyond the
frozen scaffold. Confirm every test fails for a behavioural reason
(the feature doesn't exist), not a mechanical one (a compile or
wiring error).

Freeze the suite: write
`<IMPLEMENTATION_ROOT>/docs/acceptance/MANIFEST.sha256` and commit it
together with the tests. From this commit onward these files are
protected by `CONSTITUTION.md` §2.

Commit the completed phase.

---

## Phase 4 — Implementation

Read:

`04_Skills/implement/SKILL.md`

Implement the approved Specification under `IMPLEMENTATION_ROOT`,
building until the frozen acceptance-test suite from Phase 3 passes.

Do not create the unit-test suite. Do not edit anything the
acceptance-test manifest lists — if one seems wrong, follow the
test-defect-request procedure in `CONSTITUTION.md` §2 instead.

Build, formatting, linting and type-check feedback are permitted, and
you may run the frozen acceptance tests as often as you like as a
green/red signal.

Record the first time the primary registration happy path becomes
executable.

Commit the completed phase.

---

## Phase 5 — Unit Tests

Read:

`04_Skills/write-unit-tests/SKILL.md`

Only now add unit and implementation-level tests, additively, on top
of the frozen acceptance suite.

Record the result of the first complete test execution (acceptance +
unit together) before repairing any failure. Classify each failure per
`04_Skills/write-unit-tests/SKILL.md` before touching anything.

Create/extend exactly:

`<IMPLEMENTATION_ROOT>/docs/test-strategy.md`

Commit the completed phase.

---

## Phase 6 — Verification

Read:

- `03_Process/DEFINITION_OF_DONE.md`;
- `03_Process/SEVERITY_TAXONOMY.md`;
- `04_Skills/verify/SKILL.md`.

Re-read all original requirements.

Perform the full verification independently from the implementation
phase, including confirming the acceptance-test freeze held (every
hash in the manifest still matches) and that
`docs/decisions-log.md` accounts for every escalation raised so far.

Create exactly:

`<IMPLEMENTATION_ROOT>/docs/verification-report.md`

For every Critical or High finding execute:

Verification → Fix → Re-verification

Log every loop. Medium/Low findings are recorded and triaged but do
not require a loop by themselves.

Commit the completed phase.

---

## Phase 7 — Finalization

Read:

`04_Skills/finalize-run/SKILL.md`

Create exactly:

- `<IMPLEMENTATION_ROOT>/RELEASE_NOTES.md`
- `<STATISTICS_ROOT>/run-summary.md`

Confirm `<IMPLEMENTATION_ROOT>/docs/decisions-log.md` is complete —
every escalation has an entry and every entry has a `resolution`.

Finalize:

`<STATISTICS_ROOT>/run-log.json`, including its `escalations` array.

Before completing the run, ensure every metric required by
`03_Process/METRICS.md` has either:

- a measured value; or
- `null` with an explicit reason.

Record the final commit.

Do not modify the shared baseline or another experiment branch.

`04_External-Audit/` (a sibling of `INPUT_ROOT`) is filled in after
this run, by a human, never by the agent — mention it exists in
run-summary.md, but do not populate it.

---

## Logging — mandatory, non-negotiable

A run that finishes without a complete
`<STATISTICS_ROOT>/run-log.json` and `<STATISTICS_ROOT>/run-summary.md`
is not a completed run.

`03_Process/METRICS.md` defines the metric set.
`03_Process/run-log.template.json` is the schema. Copy the template to
`<STATISTICS_ROOT>/run-log.json` at the start of the run and fill it in
as you go.

* **Commit after every phase. Never squash, never rebase.** Each
  commit is a timing checkpoint independent of the self-report — this
  is what lets timing be reconstructed even if the written log is
  incomplete.
* Record `experimentStart` immediately when the run begins and
  `experimentEnd` when finalization completes. Phase timestamps must
  remain consistent with those two values.
* Record phase start/end timestamps as each phase happens, not
  reconstructed afterward.
* Log every human intervention as `{start, end, reason}`, not just a
  count.
* Log every fix loop separately: trigger, what changed, when it
  closed.
* Never fabricate or estimate a value you cannot observe. Use `null`
  and state why. A missing field is a bug; a `null` with a reason is
  not.
* Do not optimize the implementation for the metrics being recorded.

Fields below `_selfReportNote` (tokens, cost, tool calls, approval
prompts, code quality, security, architecture) stay `null` with a
reason. They are filled afterward by external audit, not by the
development agent.

## Experimental fairness

Do not read instructions from files outside `INPUT_ROOT` unless they
are explicitly listed by these Run Instructions.
