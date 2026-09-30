# Run summary

> Written in: phase 7 · Source: `run-log.json` · Agent: writes

## Run

| Item | Value |
|---|---|
| Run id | 04_conference-registration_opus5.5_sdd_template-1.0 |
| Workflow / model / effort | sdd / claude-opus-5-5 / medium |
| Template version | 1.0 |
| Start commit | 8b50617 (2026-09-29T23:10:30Z) |
| Final commit | 9f00233 (2026-09-30T07:56:29Z); this summary and the final run log follow in one statistics commit |

## Timeline

Wall-clock UTC from `run-log.json`. Human waiting time is taken from `humanInterventions` and subtracted to give agent time.

| Phase | Wall clock | Waiting for the human | Agent time |
|---|---|---|---|
| 0 Preflight and bootstrap | 21 min | 10 min (D-01, D-05 → D-08) | ~11 min |
| 1 Requirements | 2 min | 0 | 2 min |
| 2 Design | 4 min | 0 | 4 min |
| 3 Test design | 20 min | 0 | 20 min |
| 4 Build | 19 min | 0 | 19 min |
| 5 Unit tests | 7 min | 0 | 7 min |
| 6 Verify | 446 min | ~424 min (D-18) | ~22 min |
| 7 Release | 7 min | 0 | 7 min |
| Total | 526 min | ~434 min | ~92 min |

## Outcome

- First complete run (phase 5, before fixes): frontend 36 passed, e2e 3 passed; backend not executed because a non-frozen unit test did not compile (`firstTestRun.failed` = null with reason).
- Final run: 224 passed, 0 failed (backend 184, frontend 37, e2e 3); frozen acceptance tests 77 (67 backend + 10 frontend) plus 3 e2e, unchanged since the phase 3 commit.
- Findings as found in phase 6: 0 Critical, 1 High (F-07, false positive, lowered by the human in D-19), 4 Medium (F-03, F-04, F-05 fixed, F-08), 3 Low (F-01 and F-02 fixed, F-06).
- Fix loops: 4 (F-02 unit-test gaps; F-05 nginx Host header plus frontend validation tests; F-01 log redaction; F-07 suppression).
- Measures: backend line coverage 78.9 % unit / 91.9 % integration, PIT 83.9 %; frontend line coverage 92.3 % unit / 87.1 % acceptance, Stryker 73.1 % (validation 100 %); duplication 0 %; backend average cyclomatic complexity 1.66 (max 14). Production/test code lines: backend 2312/3343, frontend 657/810.

## Decisions and human interventions

- 19 decision records: 7 blocking (D-01, D-04, D-05, D-07, D-08, D-18, D-19), 12 non-blocking. D-01, D-05, D-07 and D-18 were resolved by follow-up records, since records are never rewritten.
- Human interventions (3):
  1. D-01: `NVD_API_KEY` reported as missing. It was present; the preflight check skipped the last line of `.env` because the file has no trailing newline (agent error).
  2. D-05 / D-07: Critical and High vulnerabilities in the Spring Boot 3.5.16 stack. The human moved `tech-stack.md` to Spring Boot 4.1.1 with Tomcat 11.0.26.
  3. D-18: approval to lower the false-positive High CVE-2025-7962 on `angus-activation`.
- Pending review: D-02, D-03, D-06, D-09 to D-17, and the accepted Medium findings F-03, F-04, F-08 (see `02_output/docs/release-notes.md`).
- Agent corrections recorded in the documents: in phase 0, CVE-2025-7962 was first labelled Medium using the tool's own label instead of the CVSS mapping, and corrected in phase 6. Several early decision timestamps were written from estimates and corrected against the UTC clock when noticed.

## Not measured by the agent

Cost, tokens, API time and tool calls are added by a human from the harness usage report (`usage.md`, and the post-run session for `run-log.json` → `usage`).
