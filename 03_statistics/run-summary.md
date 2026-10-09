# Run summary

> Written in: phase 7 · Source: `run-log.json` · Agent: writes

## Run

| Item | Value |
|---|---|
| Run ID | tanej-01_conference-registration_opus5.5_sdd_template-tanej-1.0 |
| Model / effort | claude-opus-5-5 / medium |
| Template version | tanej-1.0 |
| Start commit | b6c9dc6eaafd0d43da18025f278d267c9fcb4a1c (2026-10-09T10:43:03Z) |
| Final commit | 15f7587c8df7e489fb6603107897ece04ffc4d81 (2026-10-09T22:03:12Z); the summary commit follows it |
| Branch | `run/tanej-01_conference-registration_opus5.5_sdd_template-tanej-1.0` (local, D-10) |

## Timeline

| Phase | Name | Start | End | Wall time |
|---|---|---|---|---|
| 0 | preflight-and-bootstrap | 2026-10-09T10:43:03Z | 2026-10-09T11:08:04Z | 0:25:01 |
| 1 | requirements | 2026-10-09T11:08:04Z | 2026-10-09T11:09:31Z | 0:01:27 |
| 2 | design | 2026-10-09T11:09:31Z | 2026-10-09T11:14:38Z | 0:05:07 |
| 3 | test-design | 2026-10-09T11:14:38Z | 2026-10-09T11:41:54Z | 0:27:16 |
| 4 | build | 2026-10-09T11:41:54Z | 2026-10-09T12:06:48Z | 0:24:54 |
| 5 | unit-tests | 2026-10-09T12:06:48Z | 2026-10-09T12:18:57Z | 0:12:09 |
| 6 | verify | 2026-10-09T12:18:57Z | 2026-10-09T21:57:29Z | 9:38:32 |
| 7 | release | 2026-10-09T21:57:29Z | 2026-10-09T22:03:12Z | 0:05:43 |
| | total | 2026-10-09T10:43:03Z | 2026-10-09T22:03:12Z | 11:20:09 |

Phase 6 wall time includes a pause after the plan's session limit was reached and the wait for D-24; neither is agent time. The pause boundaries were not recorded by the agent.

## Outcome

| Measure | Value |
|---|---|
| First complete run (phase 5) | 226 passed, 12 failed (2 implementation defects, 1 non-frozen test defect) |
| Final run (phase 6) | 242 passed, 0 failed (backend 192, frontend 33, e2e 17) |
| Findings as found | Critical 0, High 2, Medium 2, Low 5 |
| High findings | F-01 SMTP timeout dropped emails (fixed, loop 1); F-03 HTTP Basic (accepted by the human, D-24) |
| Fix loops | 1 |
| Coverage (line / branch) | backend 95.9 % / 86.1 %; frontend 97.4 % / 88.5 % |
| Mutation score | backend 69 % (unit tests), frontend 65.3 % |
| Code (production / test lines) | backend 2342 / 2899; frontend 644 / 943 |
| Duplication | frontend 0.58 % (test code), backend 0 (CPD) |
| Frozen tests changed after freeze | none (23 hashes match) |

## Decisions and human interventions

22 non-blocking and 4 blocking decisions (`02_output/docs/decisions-log.md`). Blocking: D-09 (Node.js, already installed), D-11/D-12 (vulnerable dev-tool pins upgraded), D-24 (HTTP Basic accepted).

| Asked | Answered | Waiting | Reason |
|---|---|---|---|
| 2026-10-09T10:54:42Z | 2026-10-09T10:58:38Z | 0:03:56 | D-09: Node.js 24.13.0 / npm 11.6.2 missing on host |
| 2026-10-09T11:01:38Z | 2026-10-09T11:07:30Z | 0:05:52 | D-11, D-12: approve pin changes for vulnerable dev tooling |
| 2026-10-09T18:29:24Z | 2026-10-09T21:56:41Z | 3:27:17 | D-24: accept or replace HTTP Basic for the organizer export (Semgrep High F-03) |

## Not measured by the agent

Cost, tokens, API time and tool calls are added by a human from the harness usage report.
