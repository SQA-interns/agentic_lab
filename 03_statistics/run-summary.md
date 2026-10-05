# Run summary

> Written in: phase 7 · Source: `run-log.json` · Agent: writes

## Run

| Item | Value |
|---|---|
| Run id | tanej-03_conference-registration_opus5.5_sdd_template-tanej-1.2 |
| Model, effort | claude-opus-5-5, medium |
| Template | tanej-1.2, attended (`Unattended: no`) |
| Start | 2026-10-04T19:46:26Z, commit a4272e3 |
| End | 2026-10-05T14:44:46Z, final commit e0bec70 |
| Transcript | one session, `d74117c0-f133-4ca2-8d25-ceb9eb750e24.jsonl` |

## Timeline

Agent time per phase from the recorded timestamps, human waiting excluded. Wall time 18 h 58 min includes the D-15 wait and idle time between sessions before phases 5, 6 and 7.

| Phase | Agent time | Note |
|---|---|---|
| 0 Preflight and bootstrap | 23 min | D-01 … D-05 |
| 1 Requirements | 2 min | 48 AC; D-06 … D-11 |
| 2 Design | 13 min | 7 contracts; Semgrep High on HTTP Basic designed out; D-12 |
| 3 Test design | 23 min | 65 acceptance + 15 end-to-end tests frozen in 5ee3c71 |
| 4 Build | 38 min | plus 12 h 25 min waiting for the D-15 answer; D-13, D-14, D-16 |
| 5 Unit tests | 42 min | D-17 |
| 6 Verify | 22 min | runtime demonstration, F-01 … F-05 |
| 7 Release | 11 min | READMEs from a clean checkout (one fix) |
| Total | 2 h 53 min | |

## Outcome

| Measure | Value |
|---|---|
| First complete run (phase 5, all levels) | 216 passed, 1 failed (defect in a new non-frozen test) |
| Final run (phase 6) | 240 passed, 0 failed: backend 183 (65 acceptance, 105 unit, 8 integration, 5 architecture), frontend 42, end-to-end 15 |
| Coverage | backend 97.1% lines / 91.8% branches (acceptance alone 83.0% / 62.1%); frontend 100% / 95.6% |
| Mutation | backend PIT 87% (352 mutants, unit-test scope); frontend Stryker 90.97% |
| Findings | Critical 0, High 0, Medium 3 (F-01 … F-03), Low 2 (F-04, F-05), all accepted |
| Fix loops (phase 6) | none; phase 5 fixed one test defect and one rule defect (D-17) |
| Code (cloc) | backend 2 093 production / 3 338 test lines; frontend 635 production / 1 057 test lines (incl. 556 end-to-end); duplication 0%; complexity not measured (no tool in `tech-stack.md`) |

## Decisions and human interventions

| Item | Value |
|---|---|
| Decisions | 17: 1 blocking (D-15), 16 non-blocking, all pending review except D-15 |
| D-15 | frozen test AC-005-01 cast a `timestamptz` value to the wrong type (harness defect missed by the phase 3 probe); asked 2026-10-04T21:18:37Z, answered 2026-10-05T09:43:35Z (option 1), applied in 941b362 and 8a51803 |
| Human interventions | 1 (D-15), 12 h 25 min |
| Process notes | 8 commits exceed the size guide (F-05); `secrets.sh` refused a demo argument containing `export` in phase 6, not worked around |

## Usage (from the transcript, `usage-from-transcript.mjs`)

| Item | Value |
|---|---|
| Model calls | 252 |
| Tokens | input 506, output 446 737, cache read 84 775 767, cache write 1 613 726 |
| Tool calls | 525 (Bash 189, Write 168, Edit 159, Read 9) |
| Cost (API-price equivalent) | USD 38.80, prices of 2026-09-25 |
| Model time (estimate) | 31 to 63 min; permission denials 0 |

## Not measured by the agent

Cost, tokens, API time and tool calls are added by a human from the harness usage report.
