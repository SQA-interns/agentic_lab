---
name: finalize-run
description: Produce the final experimental record and release artefacts.
---

# Procedure

1. Confirm Definition of Done passes.
2. Finalize verification report.
3. Create RELEASE_NOTES.md from actual implementation changes.
4. Finalize run-log.json.
5. Create run-summary.md.
6. Record final Git SHA.
7. Confirm immutable experiment inputs were not modified.

Do not claim results that were not measured or demonstrated.

## Output

Create exactly:

- `02_Implementation/RELEASE_NOTES.md`
- `03_Run-Statistics/run-summary.md`

Finalize:

`03_Run-Statistics/run-log.json`
