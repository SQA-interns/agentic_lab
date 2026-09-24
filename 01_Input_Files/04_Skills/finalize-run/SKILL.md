---
name: finalize-run
description: Produce the final experimental record and release artefacts.
---

# Procedure

1. Confirm Definition of Done passes.
2. Finalize verification report.
3. Create RELEASE_NOTES.md from actual implementation changes.
4. Finalize run-log.json against `03_Process/METRICS.md`.
5. Create run-summary.md.
6. Record final Git SHA.
7. Confirm immutable experiment inputs were not modified.
8. Confirm every required metric has a measured value or `null` with
   an explicit reason.

Do not claim results that were not measured or demonstrated.

## Output

Create exactly:

- `<IMPLEMENTATION_ROOT>/RELEASE_NOTES.md`
- `<STATISTICS_ROOT>/run-summary.md`

Finalize:

`<STATISTICS_ROOT>/run-log.json`
