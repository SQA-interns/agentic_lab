---
name: finalize-run
description: Produce the final experimental record and release artefacts.
---

# Procedure

1. Confirm Definition of Done passes.
2. Finalize verification report.
3. Confirm `docs/decisions-log.md` is complete: every escalation raised
   during the run has an entry, and every entry has a `resolution`.
4. Create RELEASE_NOTES.md from actual implementation changes.
5. Finalize run-log.json against `03_Process/METRICS.md`, including the
   `escalations` array (mirroring `docs/decisions-log.md`).
6. Create run-summary.md.
7. Record final Git SHA.
8. Confirm immutable experiment inputs were not modified
   (`git diff <startCommit>..HEAD -- 01_Input_Files` is empty).
9. Confirm every required metric has a measured value or `null` with
   an explicit reason.

Note in run-summary.md that tokens, cost, tool calls, and approval
prompts remain `null` pending the external audit pass
(`04_External-Audit/`, at `WORKSPACE_ROOT`), which is not part of this
run.

Do not claim results that were not measured or demonstrated.

## Output

Create exactly:

- `<IMPLEMENTATION_ROOT>/RELEASE_NOTES.md`
- `<STATISTICS_ROOT>/run-summary.md`

Finalize:

`<STATISTICS_ROOT>/run-log.json`
