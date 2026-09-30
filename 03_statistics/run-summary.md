# Run summary

> Written in: phase 7 · Source: `run-log.json` · Agent: writes

## Run

- Run id: `05_conference-registration_opus5.5_sdd_template-1.2`
- Model: claude-opus-5-5, effort medium, template version 1.2
- Branch: `kyuhi/single-agent/sdd/v002`
- Start commit: `30047fcf688c0ebaf965c9863185bd1b7cd11f2d` (2026-09-30T14:10:00Z)
- End commit: see `finalCommit` in `run-log.json` (the commit that adds this summary is followed by no further work)

## Timeline

Agent wall-clock time per phase, including waits for the human (UTC):

| Phase | Start | End | Duration |
|---|---|---|---|
| 0 Preflight and bootstrap | 14:10:00 | 14:23:43 | 13.7 min (incl. 1.2 min waiting for D-04) |
| 1 Requirements | 14:23:43 | 14:26:12 | 2.5 min |
| 2 Design | 14:26:12 | 14:30:27 | 4.3 min |
| 3 Test design | 14:30:27 | 14:43:17 | 12.8 min |
| 4 Build | 14:43:17 | 15:15:09 | 31.9 min (incl. 5.5 min waiting for D-12) |
| 5 Unit tests | 15:15:09 | 15:21:49 | 6.7 min |
| 6 Verify | 15:21:49 | 15:46:28 | 24.7 min |
| 7 Release | 15:46:28 | see `run-log.json` | — |

## Outcome

- Tests, first full run (phase 5, before any fix): 272 passed, 0 failed.
- Tests, final run (phase 6): 281 passed, 0 failed (105 frozen backend acceptance, 123 backend unit/integration/architecture, 45 frontend unit/component, 8 frozen end-to-end).
- Coverage (backend combined): 96.6 % lines, 95.9 % branches; frontend 92.4 % lines, 99.2 % branches. Mutation score: backend 84 %, frontend 84.2 %.
- Findings: 0 Critical, 0 High, 6 Medium, 4 Low. Four fix loops (F-01, F-09, F-05, F-06); the other findings are accepted with reasons in `02_output/docs/06_verification-report.md`.
- Code size (cloc): backend 1815 production / 3534 test lines of Java; frontend 565 production / 813 test lines of TypeScript. No duplication found by CPD or jscpd.
- READMEs verified from a clean checkout (`02_output/logs/07_clean-checkout.log`).

## Decisions and human interventions

- 12 decisions: 2 blocking (D-04, D-12), 10 non-blocking; 6 pending review (D-06 … D-11, the open questions OQ-01 … OQ-06).
- Human interventions: 2.
  - D-04 (phase 0): approve CVE-2025-7962 on `angus-activation` 2.0.3 as a Dependency-Check false positive: approved.
  - D-12 (phase 4): a frozen acceptance test cast JDBC `TIMESTAMPTZ` values to `OffsetDateTime`: correction approved; manifest line updated in its own commit.

## Not measured by the agent

Cost, tokens, API time and tool calls are added by a human from the harness usage report.
