# Run summary

> Written in: phase 7 · Source: `run-log.json` · Agent: writes

## Run

| Item | Value |
|---|---|
| Run ID | tanej-05_conference-registration_opus5.5_sdd_template-tanej-3.0 |
| Model, effort | claude-opus-5-5, medium |
| Template version | tanej-3.0 |
| Start commit | `61663142f170039022547f4af58474acc7d742b6` (2026-10-09T08:07:25Z) |
| Final commit | `f676b0f67231ddf5a44ca11e8e8e35a335e11406` (2026-10-09T15:31:10Z) |
| Branch | `tanej/single-agent/sdd/v005` |

## Timeline

Phase durations from `run-log.json`. Waiting for the human after the phase 4, 5 and 6 gates lies between phases and is not counted; the usage-limit stop (30 min) is inside phase 5.

| Phase | Start | End | Minutes |
|---|---|---|---|
| 0 Preflight and bootstrap | 08:07:25Z | 08:41:38Z | 34 (incl. 7 waiting for D-03/D-04) |
| 1 Requirements | 08:42:22Z | 08:44:35Z | 2 |
| 2 Design | 08:44:46Z | 09:09:44Z | 25 (incl. 2 waiting for D-19) |
| 3 Test design | 09:09:54Z | 09:36:14Z | 26 |
| 4 Build | 09:36:31Z | 10:44:23Z | 68 |
| 5 Unit tests | 11:21:05Z | 13:12:06Z | 111 (incl. 30 usage-limit stop) |
| 6 Verify | 13:36:50Z | 14:24:12Z | 47 |
| 7 Release | 15:15:41Z | 15:31:10Z | 15 |
| Agent time (phases minus waits) | | | about 289 |
| Wall clock, start to end | | | 444 |

## Outcome

| Item | Value |
|---|---|
| First complete run (phase 5, before fixes) | 257 passed, 5 failed (1 implementation defect, 4 non-frozen test defects) |
| Final run (phase 6) | 307 passed, 0 failed (backend 239, frontend 62, end-to-end 6) |
| Frozen tests | 135 (113 backend acceptance, 16 frontend acceptance, 6 end-to-end); manifest 30/30 matches |
| Findings as found | Critical 0, High 1, Medium 3, Low 7 (F-01..F-11) |
| Fix loops | 2: F-02 encoded paths skipped the rate-limit, HTTPS-only and size filters (13:59–14:04Z); F-03 email in database error logs (14:04–14:07Z) |
| Open findings | none Critical or High; F-04 (commit size, cannot be rewritten) open; F-05..F-08 accepted |
| Code | backend 2,969 production / 4,309 test lines; frontend 954 / 1,430 (cloc); duplication 2.67 % (frontend, test files only; backend CPD 0) |

## Test measures

From the "Final run" table of `02_output/docs/03_test-strategy.md`.

| Component | Level | Tests passed / failed | Line coverage | Branch coverage | Mutation score (scope) | Tools |
|---|---|---|---|---|---|---|
| backend | acceptance | 113 / 0 | 97.9 % (all levels) | 90.3 % (all levels) | — | JUnit 6, Testcontainers, JaCoCo |
| backend | unit | 104 / 0 | | | 98 % (292/297), unit-tested scope | Pitest 1.30.0 |
| backend | integration | 18 / 0 | | | 60 % (37/62), Spring-wired scope; survivors classified | Pitest 1.30.0 |
| backend | architecture | 4 / 0 | | | — | ArchUnit 1.3.2 |
| frontend | acceptance | 16 / 0 | 100 % (unit + acceptance) | 95.5 % | — | Vitest 4.1.11, Testing Library, V8 |
| frontend | unit / component | 46 / 0 | | | 97.2 % | Stryker 10.0.0 |
| all | end-to-end | 6 / 0 | — | — | — | Playwright 1.63.0 |

## Decisions and human interventions

| Item | Value |
|---|---|
| Decisions | 20: 6 blocking records (D-03, D-04, D-19 asked; D-07, D-08, D-20 their answers), 14 non-blocking, all pending review |
| Questions to the human | 2 messages (phase 0: D-03 + D-04; phase 2: D-19), each answered with the proposed option within 8 minutes |
| Other human actions | "continue" after the phase 4, 5 and 6 gates and once in phase 6; resume after the usage-limit stop in phase 5 |
| Model calls, tool calls (transcript) | 326 calls, 586 tool calls (Bash 223, Write 188, Edit 157, Read 14, AskUserQuestion 2, Grep 1, ToolSearch 1) |
| Tokens (transcript) | input 668, output 528,848, cache read 140,419,744, cache write 737,635 |
| Cost (API-price equivalent, price table 2026-09-25) | USD 44.56 |

## Not measured by the agent

Cost, tokens, API time and tool calls are added by a human from the harness usage report.
