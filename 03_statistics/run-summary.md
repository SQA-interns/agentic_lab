# Run summary

> Written in: phase 7 · Source: `run-log.json` · Agent: writes

## Run

| Field | Value |
|---|---|
| Run id | tanej-01_conference-registration_opus5.5_sdd_template-tanej-1.0 |
| Model / effort | claude-opus-5-5 / medium |
| Template version | tanej-1.0 |
| Start commit | `b6c9dc6` (2026-10-08T20:49:53Z) |
| Final commit | `ab7962d` (2026-10-08T23:41:52Z); the run-summary commit follows it |

## Timeline

| Phase | Start | End | Duration (wall clock, includes waits for the human) |
|---|---|---|---|
| 0 preflight-and-bootstrap | 2026-10-08T20:49:53Z | 2026-10-08T21:12:14Z | 22 min |
| 1 requirements | 2026-10-08T21:12:14Z | 2026-10-08T21:13:28Z | 1 min |
| 2 design | 2026-10-08T21:13:28Z | 2026-10-08T21:19:49Z | 6 min |
| 3 test-design | 2026-10-08T21:19:49Z | 2026-10-08T21:43:17Z | 23 min |
| 4 build | 2026-10-08T21:43:17Z | 2026-10-08T22:09:19Z | 26 min |
| 5 unit-tests | 2026-10-08T22:09:19Z | 2026-10-08T22:22:14Z | 12 min |
| 6 verify | 2026-10-08T22:22:14Z | 2026-10-08T23:35:15Z | 73 min |
| 7 release | 2026-10-08T23:35:15Z | 2026-10-08T23:41:52Z | 6 min |
| Total | 2026-10-08T20:49:53Z | 2026-10-08T23:41:52Z | 171 min |

## Outcome

| Measure | Value |
|---|---|
| First complete test run | 290 passed, 1 failed |
| Final test run | 293 passed, 0 failed (backend 231, frontend 56, e2e 6) |
| Findings as found | Critical 0, High 2, Medium 3, Low 7; none open at release (F-01 downgraded D-26, F-02 accepted D-10) |
| Fix loops | 2 (F-01 semgrep suppression after D-26; F-05 Stryker command runner after D-27) |
| Coverage | backend line 97.3% / branch 93.0%; frontend line 97.07% / branch 91.26% |
| Mutation | backend 76% (PIT, unit tests); frontend 74.46% (Stryker) |
| Code size (cloc) | backend 2279 production / 3306 test lines; frontend 556 / 1017 |

## Decisions and human interventions

27 decisions: 21 non-blocking, 6 blocking (D-04, D-05, D-06, D-10, D-26, D-27).

| Asked | Answered | Reason |
|---|---|---|
| 2026-10-08T20:55:53Z | 2026-10-08T21:03:29Z | phase 0 preflight: D-04 Docker Engine not running, D-05 .env missing, D-06 npm dev-tooling Critical/High |
| 2026-10-08T21:07:07Z | 2026-10-08T21:10:48Z | phase 0: D-10 jscpd 4.3.0 High (braces) remains after vitest upgrade |
| 2026-10-08T22:50:01Z | 2026-10-08T22:55:08Z | phase 6: D-26 semgrep High on Basic authentication, D-27 frontend mutation runner |

## Not measured by the agent

Cost, tokens, API time and tool calls are added by a human from the harness usage report.
