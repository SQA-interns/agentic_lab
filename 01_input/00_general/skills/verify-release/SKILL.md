---
name: verify-release
description: Check every Definition of Done item with evidence, confirm the frozen tests are intact, demonstrate the running system, and run verify → fix → re-verify loops (phase 6).
---

> Owner: QA lead · Read in: phase 6 · Agent: read-only

- Reads: all of `01_input/`, `general/quality/*`, `project/05_quality/*`, `docs/`
- Writes: `docs/06_verification-report.md`

## Procedure

1. Re-read the requirements (`project/02_business/*`, `docs/01_*`, `docs/02_*`) before checking anything.
2. Recompute every hash in `docs/03_acceptance-manifest.sha256` and `docs/00_input-manifest.sha256`. Any mismatch is a Critical finding, whatever the reason.
3. Run the full test suite and every check command (ES-05); record results, coverage and mutation score.
4. Start every component as described for its target environment and exercise the core user flows at runtime; record what was run and what was observed.
5. Build the traceability table: AC → tests → commits.
6. For each general and project DoD item, record evidence or a finding.
7. For every Critical or High finding, run verify → fix → re-verify and log each loop; triage Medium and Low as fixed, accepted with a reason, or open item.
8. Confirm every decision record has a resolution or is marked pending review.
9. State at the top of the report that it is a self-check, not an independent review.
