# Run summary

> Written in: phase 7 · Source: `run-log.json` · Agent: writes

## Run

| Item | Value |
|---|---|
| Run ID | conference-single-sdd-001 |
| Workflow | sdd |
| Model | claude-opus-5-5 (label "Opus 5.5"); single agent, no delegation |
| Effort | medium (run-config label; the harness exposes no effort setting to the agent) |
| Template version | 1.0 |
| Branch | run/single-sdd/conference-001 (created locally at the start commit, D-07; not pushed) |
| Start commit | 52eb3eda302372a75e2178e5e347d85aeaa39b19 |
| End commit | see `finalCommit` in `run-log.json` (the last commit only records the run log) |

## Timeline

Wall-clock from `run-log.json`. "Agent time" subtracts the recorded human-wait intervals inside each phase.

| Phase | Wall-clock | Human wait | Agent time |
|---|---|---|---|
| 0 Preflight & bootstrap | 40:41 | 10:20 (D-01…D-08, D-11) | 30:21 |
| 1 Requirements | 2:06 | – | 2:06 |
| 2 Design | 4:25 | – | 4:25 |
| 3 Test design | 28:44 | – | 28:44 |
| 4 Build | 41:45 | 6:02 (D-22) | 35:43 |
| 5 Unit tests | 17:45 | – | 17:45 |
| 6 Verify | 8:18:09 | 7:45:15 (D-24) | 32:54 |
| 7 Release | see `phases[7]` | – | – |

## Outcome

| Measure | Value |
|---|---|
| Acceptance criteria | 43 (US-001…US-008), each traced to tests and commits |
| Phase 3 red run | 109 acceptance + 19 e2e, all failing for behavioural reasons |
| First full run (phase 5, before fixes) | 312 passed / 3 failed: 1 implementation defect (Retry-After rounding), 2 defects in non-frozen tests |
| Final run (phase 6) | 339 passed / 0 failed: 165 backend unit, 109 acceptance, 5 integration, 41 frontend unit, 19 e2e |
| Coverage | backend unit line 83.2% / branch 88.6%; backend integration + acceptance 85.4% / 67.9%; frontend unit 100% / 98.0% |
| Mutation | PIT 84% (72% before fix loop 4); Stryker 79.6% |
| Findings as found | Critical 1, High 1, Medium 3, Low 8 |
| Critical/High resolution | F-02 accepted under D-11 (not reachable, human-approved); F-01 downgraded under D-24 (human-approved) |
| Fix loops | 8 (see `docs/06_verification-report.md`) |
| Frozen-test changes | 1 approved harness-only fix (D-22); manifest otherwise unchanged |
| Clean checkout | all READMEs verified from a fresh clone with fresh volumes |
| Code size | backend 2 680 production / 4 421 test lines; frontend 698 / 1 124 |

## Decisions and human interventions

- **24 decisions**, 11 of them blocking (D-01…D-08, D-11, D-22, D-24), all approved by the human. The 13 non-blocking decisions are pending review (see the release notes).
- **4 human interventions:**
  1. Preflight: host tools, secrets, branch, vitest Critical, Playwright browser (6 min).
  2. Spring Boot 3.5.16 Critical/High CVEs: Tomcat override plus reachability downgrade (4 min).
  3. Frozen-test harness defect (6 min).
  4. Semgrep High on the ignored `.env` (7 h 45 min, the human was away).
- **Deviations from the pinned stack:** Docker 29.3.1 / Compose v5.1.1 (D-03); vitest 3.2.7 instead of 3.2.4 (D-05); tomcat-embed 10.1.60 override (D-11); added @types/node, contract validators and google-java-format pins (D-13, D-14, D-20).

## Not measured by the agent

Cost, tokens, API time and tool calls are added by a human from the harness usage report. Per-loop fix timestamps were not captured while the loops ran and are left `null` in `run-log.json`.
