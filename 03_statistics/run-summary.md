# Run summary

> Written in: phase 7 · Source: `run-log.json` · Agent: writes

## Run

| Item | Value |
|---|---|
| Run id | tanej-01_conference-registration_opus5.5_sdd_template-tanej-1.0 |
| Model | claude-opus-5-5 |
| Effort | medium |
| Template version | tanej-1.0 |
| Start commit | `5375170` |
| Final commit | `2fc8f9c` (HEAD before the closing commit with this summary and the run log) |
| Commits made by the run before the closing commit | 81 |
| Start | 2026-10-02T10:09:55Z |
| End | 2026-10-02T13:52:30Z |
| Elapsed, including the waits for the human | 222 min 35 s |

## Timeline

Computed from the timestamps in `run-log.json`. Phases 0 and 3 include the waits for the human answers listed below.

| Phase | Name | Start | End | Duration |
|---|---|---|---|---|
| 0 | preflight-and-bootstrap | 2026-10-02T10:09:55Z | 2026-10-02T10:43:27Z | 33 min 32 s |
| 1 | requirements | 2026-10-02T10:43:27Z | 2026-10-02T10:44:49Z | 1 min 22 s |
| 2 | design | 2026-10-02T10:44:49Z | 2026-10-02T10:50:51Z | 6 min 02 s |
| 3 | test-design | 2026-10-02T10:50:51Z | 2026-10-02T11:35:29Z | 44 min 38 s |
| 4 | build | 2026-10-02T11:35:29Z | 2026-10-02T12:43:09Z | 67 min 40 s |
| 5 | unit-tests | 2026-10-02T12:43:09Z | 2026-10-02T12:57:38Z | 14 min 29 s |
| 6 | verify | 2026-10-02T12:57:38Z | 2026-10-02T13:43:51Z | 46 min 13 s |
| 7 | release | 2026-10-02T13:43:51Z | 2026-10-02T13:52:30Z | 8 min 39 s |

## Outcome

| Item | Value |
|---|---|
| First complete test run (phase 5) | 280 passed, 3 failed |
| Final test run (phase 6) | 311 passed, 0 failed (backend 229, frontend 79, end-to-end 3) |
| Findings as found | Critical 0, High 2, Medium 3, Low 2 |
| Open Critical or High findings | 0 (F-01 lowered by human decision D-16, F-02 fixed) |
| Fix loops | 2 (F-02 nginx proxy destination; F-04 tests after mutation testing) |
| Production lines of code | backend 1985, frontend 678 |
| Test lines of code | backend 3204, frontend 1650 |
| Duplication | 0 (PMD CPD, jscpd) |
| Complexity | not measured: `tech-stack.md` lists no tool for it |
| Gates | phases 0 to 7 passed |

## Decisions and human interventions

19 decisions: 2 blocking (D-05, D-16), both answered by the human; 17 non-blocking, of which D-01, D-03 and D-04 were accepted by the human and the rest are pending review (`02_output/docs/release-notes.md`).

| Human intervention | Asked | Answered | Wait |
|---|---|---|---|
| D-05: permission for the .env presence check and the NVD dependency scan | 2026-10-02T10:24:13Z | 2026-10-02T10:37:13Z | 13 min 00 s |
| D-16: Semgrep High finding on HTTP Basic for the organizer export | 2026-10-02T11:23:02Z | 2026-10-02T11:28:29Z | 5 min 27 s |

In both cases the human's answer arrived as pasted text and was acted on only after the human confirmed it in their own words; the times above are when the answer was acted on.

## Not measured by the agent

Cost, tokens, API time and tool calls are added by a human from the harness usage report.
