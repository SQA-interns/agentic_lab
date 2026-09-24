# Run Instructions — Single Agent, One-Shot

You are the only development agent responsible for this run.

## Repository layout

This repository is organized as:

* `00_Documentation/` — background material. Reference only.
* `01_Input_Files/` — the frozen experiment inputs (this file included).
  **Read-only. Never create, edit, or delete anything here.**
* `02_Implementation/` — where you build the actual solution. Everything
  you create — specification, source code, tests, release notes — goes
  here, not at the repository root.
* `03_Run-Statistics/` — where the experimental record goes. Contains
  only logs and reports about the run, never application code.

## Inputs

Read completely, before making any changes to the repository:

* `01_Input_Files/USER_STORY.md`
* `01_Input_Files/PROJECT_CONSTRAINTS.md`
* `01_Input_Files/TECH_STACK.md`
* `01_Input_Files/FORM_SCHEMA.md`
* `01_Input_Files/ACCEPTANCE_CRITERIA_RULES.md`
* `01_Input_Files/DEFINITION_OF_DONE.md`
* any existing repository instructions such as `AGENTS.md` or `CLAUDE.md`

Everything in `01_Input_Files/` is immutable. Do not modify it during
the run.

## Process

Follow this order. Do not skip, reorder, combine, or anticipate phases.
All artefacts below are created under `02_Implementation/` unless
stated otherwise.

**User Story → Acceptance Criteria → Specification → Implementation →
Tests → Verification → Merge**

1. **Acceptance Criteria** — derive per
   `01_Input_Files/ACCEPTANCE_CRITERIA_RULES.md`. Save as
   `02_Implementation/docs/acceptance-criteria.md`. Record the
   completion timestamp.
2. **Specification** — technical solution sufficient for
   implementation: architecture, components, data model, REST API,
   validation, persistence, backup, email, export, frontend behavior,
   error handling, security controls, anti-automation controls,
   containerized deployment. Save as
   `02_Implementation/docs/specification.md`, traceable to Acceptance
   Criteria. Do not write production code or tests in this phase.
   Record the completion timestamp.
3. **Implementation** — build against the specification, inside
   `02_Implementation/`. Do not create the test suite yet. Record:
   start/end timestamps, files created, files modified, dependencies
   added, implementation decisions, and the first timestamp at which
   the primary registration happy path runs successfully.
4. **Tests** — only after implementation is complete. Use the
   appropriate source per test level (User Story/AC for behavioral
   tests, specification for contract/integration tests, implementation
   for unit tests). Do not modify tests merely to make an incorrect
   implementation pass. Record the result of the first complete test
   run before fixing anything.
5. **Verification** — re-read all inputs, then independently inspect
   the implementation and run every check in
   `01_Input_Files/DEFINITION_OF_DONE.md`, including container
   execution. Log every finding with severity, affected US/AC, fix
   performed, and re-verification result in
   `02_Implementation/docs/verification-report.md`. If verification
   fails, run Verification → Fix → Verification and log each loop
   separately.
6. **Merge** — only after `01_Input_Files/DEFINITION_OF_DONE.md` is
   fully satisfied. Use an isolated branch within this repository.
   Granular commits representing meaningful steps. Record
   starting/final commit SHA, commit count, commit sizes, reverts,
   merge conflicts, final merge timestamp.

## Logging — mandatory, non-negotiable

A run that finishes without a complete
`03_Run-Statistics/run-log.json` and `03_Run-Statistics/run-summary.md`
is not a completed run.

* **Commit after every phase. Never squash, never rebase before
  merge.** Each commit is a timing checkpoint independent of your
  self-report — this is what lets timing be reconstructed even if the
  written log is incomplete.
* Record phase start/end timestamps as each phase happens, not
  reconstructed afterward.
* Log every human intervention as `{start, end, reason}`, not just a
  count.
* Log every fix loop separately: trigger, what changed, when it
  closed.
* Never fabricate or estimate a value you cannot observe. Use `null`
  and state why in the same field. A missing field is a bug; a `null`
  with a reason is not.
* Do not optimize the implementation for the metrics being recorded.

Use `01_Input_Files/run-log.template.json` as the schema. Copy it to
`03_Run-Statistics/run-log.json` at the start of the run and fill it in
as you go. Fields you cannot observe (tokens consumed, raw tool-call
counts) stay `null` with a reason — these are filled in afterward by
external audit, not by you.

## Final report

At the end, create `03_Run-Statistics/run-summary.md` including: run
identifier, agent/model identifier, tool/environment version, starting
and final commit, process followed, generated artefacts (with their
paths under `02_Implementation/`), deviations from this process (if
any), unavailable measurements and reasons, raw metric references, and
final verification status against `01_Input_Files/DEFINITION_OF_DONE.md`.

## General rules

* Do not add functionality not justified by `USER_STORY.md`,
  `PROJECT_CONSTRAINTS.md`, or the derived specification.
* Work autonomously whenever the inputs provide enough information.
  Ask for clarification only when a missing decision genuinely
  prevents safe or correct progress.
* Nothing outside `02_Implementation/` and `03_Run-Statistics/` is
  yours to modify.