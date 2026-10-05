# Definition of Done

> Owner: QA lead · Fill: once per organisation; review with the test and security standards · Read in: phases 6, 7 · Agent: read-only

Each item needs evidence in `docs/06_verification-report.md`. Project additions are `DoD-Pnn` in `project/constraints.md`.

| ID | Criterion | Evidence |
|---|---|---|
| DoD-01 | The full test suite passes; frozen test hashes match the manifest | test report, hash check |
| DoD-02 | Format, lint, type check and static analysis report no errors | tool output |
| DoD-03 | Coverage and mutation score are recorded and meet the project thresholds, if any | coverage and mutation reports |
| DoD-04 | Architecture constraints (`AR`) are checked automatically where possible; no dependency cycles | architecture test output |
| DoD-05 | No open Critical or High finding from the security review and scanners | scanner summaries, findings table |
| DoD-06 | Every component runs in its target environment and the core user flows work at runtime, not only in tests | runtime demonstration log |
| DoD-07 | Every AC traces to at least one test and one commit | traceability table |
| DoD-08 | Root and component READMEs (ES-06) work when followed from a clean checkout | clone log (phase 7) |
| DoD-09 | Release notes list the checks a human must do before production use, separate from what this run verified | `docs/release-notes.md` (phase 7) |
| DoD-10 | Every decision has a resolution; inputs and protected files are unchanged | decisions log, `docs/00_input-manifest.sha256` check |
| DoD-11 | The evidence checks of `skills/verify-release` hold | verification report, "Evidence" |
