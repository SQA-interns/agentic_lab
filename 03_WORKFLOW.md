# Workflow, outputs, Git, and measurement

## Setup and boundaries

`INPUT_ROOT` is this folder and is immutable. `WORKSPACE_ROOT` is its parent.
Generated application files belong in `WORKSPACE_ROOT/02_Implementation`
(`IMPLEMENTATION_ROOT`); run records belong in
`WORKSPACE_ROOT/03_Run-Statistics` (`STATISTICS_ROOT`). Never assume an
absolute path, repository name, or OS.

Before Phase 1: record `experimentStart`; confirm a clean baseline working
tree; copy `scaffold/` into `IMPLEMENTATION_ROOT`; copy
`run-log.template.json` to `STATISTICS_ROOT/run-log.json`; then begin.
Every run starts from the same baseline and fresh agent context.

## Fixed phase sequence

| Gate | Do | Do not | Commit |
|---|---|---|---|
| 1. Acceptance | Create `docs/acceptance-criteria.md` from `01_PRODUCT.md` | Code or tests | Yes |
| 2. Specification | Create `docs/specification.md` from product + engineering contract | Code or tests | Yes |
| 3. Implementation | Build the approved specification; use build/format/lint/type feedback; record added dependencies, important implementation decisions, requirement traceability, and first executable primary-registration happy path | Feature tests or unrelated refactors | Yes |
| 4. Tests | Write relevant unit, component, API, integration, acceptance, E2E, negative/edge/security tests from AC, specification, and implementation; create `docs/test-strategy.md`; record first complete test run before repairs | Weaken valid tests or add generic unused architecture rules | Yes |
| 5. Verify | Run `04_VERIFICATION.md`; create `docs/verification-report.md`; log every Verify → Fix → Re-verify loop | Claim unmeasured results | Yes |
| 6. Finalize | Create `RELEASE_NOTES.md` and `run-summary.md`; complete `run-log.json`; record final SHA | Modify inputs or another experiment branch | Yes |

## Git operating rules

Work on a dedicated experiment branch. A phase commit must be coherent,
reviewable, and revertible; do not mix unrelated refactors. Keep commits local
until their gate passes; push the branch after each completed gate or at any
collaboration/backup boundary. Never rewrite shared history. To undo a shared
change, use a new revert commit and document the reason; count reverts and
merge conflicts in the run log.

## Measurement rules

`run-log.json` is authoritative. Record timestamps during work, never
reconstruct them. Record only defined metrics: phase durations, first happy
path, fix loops; human interventions/questions/manual fixes; first and final
test results; verifier findings; commits/reverts/conflicts; deterministic LOC,
complexity, duplication, lint/type/coverage/security/architecture measures;
and externally exposed provider metrics. Use `null` plus a reason when a
defined measurement cannot be observed. Do not add or estimate metrics.

Final artefacts: acceptance criteria, specification, test strategy,
verification report, release notes, run log, and run summary.
