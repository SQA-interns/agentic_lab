# Run summary

> Written in: phase 7 · Source: `run-log.json` · Agent: writes

## Run

| Field | Value |
|---|---|
| Run id | tanej-04_conference-registration_opus5.5_sdd_template-tanej-2.0 |
| Model, effort | claude-opus-5-5, medium |
| Template version | tanej-2.0 |
| Start commit | 20c720214a74a65b082724063222be2ee748caa3 (2026-10-06T07:39:51Z) |
| Final commit | 85ee2258fdba383e12b7946024324fea082b28f7 (2026-10-06T17:10:08Z) |

## Timeline

Wall-clock per phase from the run log; it includes time spent waiting for the human and for interrupted sessions.

| Phase | Start | End | Duration | Notes |
|---|---|---|---|---|
| 0 preflight and bootstrap | 07:39:51Z | 11:45:02Z | 4 h 05 min | about 3 h waiting on D-01 (Node) and D-02 (`.env`) |
| 1 requirements | 11:45:19Z | 11:47:32Z | 2 min | |
| 2 design | 11:47:49Z | 12:21:57Z | 34 min | |
| 3 test design | 12:22:09Z | 12:44:45Z | 23 min | |
| 4 build | 12:44:45Z | 13:36:21Z | 52 min | includes D-21 wait |
| 5 unit tests | 13:36:21Z | 16:33:18Z | 2 h 57 min | sessions ended during long mutation runs; Docker stopped twice |
| 6 verify | 16:33:18Z | 16:52:43Z | 19 min | |
| 7 release | 16:52:43Z | 17:10:08Z | 17 min | |

## Outcome

| Measure | Value |
|---|---|
| First complete run | 233 passed, 0 failed |
| Final run | 242 passed, 0 failed (backend 184, frontend 48, e2e 10) |
| Findings | critical 0, high 0, medium 3, low 6 (F-01..F-09); 2 fixed (F-01, F-02), rest accepted or recorded |
| Fix loops | 2 (F-01 semgrep scope, F-02 personal data in logs) |
| Frozen-test changes | 1, human-approved (D-21, manifest re-frozen) |

## Test measures

| Component | Level | Tests passed / failed | Line coverage | Branch coverage | Mutation score (scope) | Tools |
|---|---|---|---|---|---|---|
| backend | acceptance + unit + integration + architecture | 184 / 0 (92 acceptance, 9 architecture, 83 unit/integration) | 95.3% | 90.4% | 94% (164/174, D-23 scope) | JUnit 6, Testcontainers 2.0.5, ArchUnit 1.3.2, JaCoCo 0.8.12, Pitest 1.30.0 |
| frontend | acceptance + unit/component | 48 / 0 (19 acceptance incl. bootstrap, 29 unit) | 97.68% | 91.09% | 85.1% (383/451) | Vitest 4.1.11, @vitest/coverage-v8 4.1.11, Stryker 10.0.0 |
| stack | end-to-end | 10 / 0 | — | — | — | Playwright 1.63.0 (container) |

## Decisions and human interventions

- 23 decisions: 6 blocking (D-01 Node.js install, D-02 `.env`, D-07 vitest upgrade, D-08 false-positive High, D-19 Basic-auth High, D-21 frozen-harness fix), 17 non-blocking; 16 are pending review (`docs/release-notes.md`).
- 5 human interventions in the run log: phase 0 preflight (twice), dependency-scan decisions, the D-19 contract-scan decision, D-21. Docker Desktop was restarted by the human twice (phases 5 and 6), outside the run log.

## Not measured by the agent

Cost, tokens, API time and tool calls are added by a human from the harness usage report. The transcript estimate in `run-log.json` ("usage"): 349 model calls, 594 tool calls, cost estimate USD 42.72 (price table 2026-09-25); the usage panel in `03_statistics/usage.md` is authoritative.
