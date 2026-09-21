# Experiment Run Summary

## Run identifier

`opus5-medium-single-agent-classic-sdd-2026-09-21`

## Agent, model and environment

| | |
| --- | --- |
| Model identifier (as exposed) | `claude-opus-5` |
| Harness | Claude Code (Claude Agent SDK, desktop app Code tab) |
| Harness version | **not exposed to the agent** — recorded as `null` |
| Agents | 1 (single-agent run by design; no subagent was invoked) |
| OS | Windows 11 Pro 10.0.26200 (win32) |
| Node.js / npm / git | v24.13.0 / 11.6.2 / 2.53.0.windows.1 |
| Python | not installed |
| Docker | **not available** — affects two acceptance criteria, see § Unavailable measurements |
| npm registry | reachable |

## Commits

| | |
| --- | --- |
| Starting commit | `d4a6f12668f88487c8e12ad1a4bc3b62bf9f5513` (2026-09-21T10:20:05Z) |
| Starting branch | `opus5_medium_single_agent_classic_sdd` |
| Experiment branch | `experiment/conference-registration-opus5` |
| Final commit | `6c2af9f6a8b1c8b5b916267be61189eff43fa951` (merge commit) |
| Commits | 12 (11 plus one `--no-ff` merge commit) |
| Reverts | 0 |
| Merge conflicts | 0 |
| Baseline overwritten | No. `main` was never touched. |

## Process followed

**User Stories → Acceptance Criteria → Specification → Implementation → Tests →
Verification → Merge**, in that order, with no phase skipped, reordered, combined or
anticipated.

| Phase | Start (UTC) | End (UTC) | Duration |
| --- | --- | --- | --- |
| Start state recorded | 2026-09-21T10:35:03Z | — | — |
| 1 — Acceptance Criteria | 10:35:22 | 10:36:45 | 1.4 min |
| 2 — Specification | 10:36:54 | 10:41:19 | 4.4 min |
| 3 — Implementation | 10:41:27 | 11:00:19 | 18.9 min |
| 4 — Tests | 11:01:08 | 11:16:53 | 15.8 min |
| 5 — Verification | 11:17:31 | 11:28:18 | 10.8 min |
| 6 — Merge | 11:28:18 | 11:31:19 | 3.0 min |
| **Total** | **10:35:03** | **11:31:19** | **56.3 min** |

Phase boundaries held: no production code was written before the specification was
complete, no feature test was written before the implementation was complete, and
verification ran as a separate pass after the test suite existed.

## Generated artefacts

| Artefact | Purpose |
| --- | --- |
| `docs/acceptance-criteria.md` | Phase 1 — 87 observable criteria traceable to the 8 User Stories and the constraints |
| `docs/specification.md` | Phase 2 — architecture, data model, REST API, validation, security, deployment, ADR-001…005 |
| `docs/test-strategy.md` | Phase 4 — why each test level was selected, and what is not covered |
| `docs/verification-report.md` | Phase 5 — findings with severity, fix and re-verification result |
| `RELEASE_NOTES.md` | Phase 6 — derived from the implemented User Stories and the actual changes |
| `experiment/run-log.json` | Objective events and measurements recorded as they occurred |
| `experiment/run-summary.md` | This document |
| `README.md`, `.env.example` | Operating instructions and configuration reference |
| Application | `backend/` (34 modules), `frontend/`, `config/`, `e2e/`, `docker-compose.yml` |

## Key results

| | |
| --- | --- |
| First working backend happy path | 2026-09-21T10:52:49Z (17.8 min from start) |
| First working full-stack happy path in a browser | 2026-09-21T10:59:04Z (24.0 min from start) |
| First complete test run | 313 tests, 313 passed, 0 failed |
| Final test suite | 347 tests, 100% passing (277 backend, 42 frontend, 28 e2e) |
| Backend coverage | 95.21% statements, 82.82% branches, 96.02% functions |
| Acceptance Criteria passed on first evaluation | 84 of 87 |
| Acceptance Criteria passed finally | 85 of 87 (2 not verifiable here) |
| Verification findings | 0 Critical, 1 Major, 4 Minor |
| Fix loops | 1 |
| Security findings in the implementation | 0 |
| Lint / type-check errors | 0 / 0 |
| Dependency advisories | 0 |
| Architecture violations, dependency cycles | 0, 0 |
| Production code duplication | 0.00% |
| Cyclomatic complexity | average 2.41, max 12 |
| Production LOC / test LOC | 4,065 / 4,059 |
| Implementation rewritten after first verification | 1.68% of production lines |
| Human interventions / clarifying questions | 0 / 0 |

## Deviations from the required process

**None.** The prescribed order was followed exactly, and the required artefacts were all
produced. Three points are worth stating explicitly so they are not mistaken for
deviations:

1. **The specification was edited during Phase 5.** Two corrections were made — where
   outbound ports live (the fix for finding F-01) and one module-list entry (F-02). Both are
   recorded as findings in the verification report rather than changed silently. The User
   Stories, the project constraints and `FORM_SCHEMA.md` were never modified.
2. **Compile, lint and type checks were used inside Phase 3** as development feedback, as
   PROMPT.md permits, and are logged separately from the test phase under
   `phases[2].developmentFeedbackChecks`.
3. **One Phase 3 smoke check** exercised the registration happy path to establish the
   "first working version" timestamp. It was a throwaway script in the scratchpad, not part
   of the repository, and no feature test was created before Phase 4.

## Unavailable measurements

Recorded as `null` in `experiment/run-log.json`, with the reason, rather than estimated:

| Measurement | Reason |
| --- | --- |
| Tokens consumed | The environment exposes no token count to the agent. The protocol forbids estimating it. |
| Monetary cost | Not exposed by the environment. |
| Tool calls | No tool-call counter is exposed. A figure reconstructed from memory would be an estimate. |
| Harness/tool version | Claude Code does not expose a version string to the agent. |
| Failed CI runs | No CI system is configured in this repository; no CI run occurred. |
| Human time | Not externally supplied for this run. |
| Unit vs integration coverage, separately | The suites share one instrumentation run; no per-suite coverage was configured, so splitting the figure would be an estimate. |
| Maximum dependency depth | No depth calculation was defined; dependency-cruiser was configured for rule conformance, not path-length reporting. |
| Change-experiment metrics (files, modules, LOC, time) | PROMPT.md defines no maintainability/change experiment for this run, so there is nothing to measure. |
| Cohesion, modularity, architectural consistency, pattern consistency as scores | No deterministic calculation is defined for these concepts. The raw module graph (per-module Ca, Ce and instability, 34 modules, 87 dependencies, 0 cycles) is recorded instead for external evaluation. |

### Acceptance Criteria that could not be verified here

**AC-G-14** (`docker compose up --build` starts the whole system) and **AC-G-16**
(persistent data survives a container restart) require Docker, which is not installed in
this environment. The container definitions were written and reviewed statically; the same
processes were run natively and the complete end-to-end suite passes against them; and the
process-level equivalent of AC-G-16 — closing the application and rebuilding it against the
same data directory — is covered by an automated test. Both criteria are recorded as
**unverified**, not as passing.

## Raw metric references

| Data | Location |
| --- | --- |
| All phase events, timestamps and measurements | `experiment/run-log.json` |
| Per-module coupling (Ca, Ce, instability) | `experiment/run-log.json` → `phases[4].architecture` |
| Findings with severity, fix and re-verification result | `docs/verification-report.md` § 2 |
| Acceptance Criteria traceability | `docs/acceptance-criteria.md` § Traceability matrix; `docs/verification-report.md` § 3 |
| Test level justifications | `docs/test-strategy.md`, plus a header comment in every test file |
| Commit sizes | `experiment/run-log.json` → `phases[5].commitSizes` |

## Final verification status

**PASS.** Lint, type check, build, 347 tests, coverage, dependency audit, architecture
conformance, duplication and complexity all pass on the merged commit. One Major and four
Minor findings were raised, fixed and re-verified in a single fix loop; no Critical finding
was found. Two acceptance criteria are recorded as unverified because this environment
cannot demonstrate them.

Merged into `opus5_medium_single_agent_classic_sdd` at **2026-09-21T11:31:19Z** as
`6c2af9f6a8b1c8b5b916267be61189eff43fa951`.
