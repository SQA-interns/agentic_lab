# Run Statistics — Conference Registration System

**Run:** `opus5-medium-single-agent-classic-sdd-2026-09-21`
**Repository:** `E:\13 - Lab Projects\agentic_lab`
**Generated:** 2026-09-21T13:57:17Z

Every figure below carries a provenance marker. Nothing here is an estimate: where a value
is not obtainable it is recorded as unavailable with the reason, per the experiment
protocol. See [`methodology.md`](methodology.md) for exactly how each number was obtained.

---

## 1. Headline

| | |
| --- | --- |
| Active working time | **96.0 min** |
| Wall-clock start to final commit | 187.9 min (91.9 min of it idle, waiting for the human to install Docker) |
| Time to first working end-to-end registration | **24.0 min** |
| Time to first merge | 56.3 min |
| Context window at end of run | **545,695 / 1,000,000 tokens (55%)** — occupancy, *not* cumulative spend |
| Cumulative tokens consumed | **not exposed by the environment** |
| Process phases | 7 (6 planned + 1 extra verification loop) |
| Verification fix loops | **2** |
| Commits | 17 (15 + 2 merges), 0 reverts, 0 conflicts |
| Files authored | 106 tracked (excluding the lockfile) |
| Lines authored | **11,937** (4,099 production / 4,243 test / 3,192 docs / 403 deploy) |
| Tests | **397, all passing** |
| Backend coverage | 95.21% statements |
| Acceptance criteria | **87 / 87 verified** (84 passed on first evaluation) |
| Findings | 0 Critical, 3 Major, 4 Minor — all resolved or documented |
| Human interventions | 1 (installed Docker); 0 clarifying questions; 0 manual code fixes |

---

## 2. Tokens

### The important distinction

The environment exposes the session's **context-window occupancy**, not cumulative token
consumption. These are different quantities and conflating them would overstate or
understate the run badly:

* **Context-window occupancy** — how much of the 1,000,000-token window the retained
  conversation currently fills. This is a *point-in-time snapshot*.
* **Cumulative consumption** — every token sent and received across all API turns. This is
  much larger, because each turn re-sends the accumulated context. **No counter for this is
  exposed**, and it cannot be derived from occupancy.

### What was measured (environment-reported)

| Reading | Time (UTC) | Tokens in window | % of window |
| --- | --- | --- | --- |
| During statistics gathering | 13:55:30 | 531,970 | 53% |
| ~80 seconds later | 13:56:50 | **545,695** | **55%** |

The 13,725-token difference between two readings 80 seconds apart is the cost of the
statistics work itself. It is shown here to make clear these are live snapshots, not a
fixed total for the run.

**Breakdown of the final reading:**

| Category | Tokens | Share of window |
| --- | --- | --- |
| Messages (the conversation itself) | 485,899 | 49% |
| System tools | 33,322 | 3% |
| MCP tools | 17,576 | 2% |
| System prompt | 4,552 | <1% |
| Skills | 4,364 | <1% |
| **Total** | **545,695** | **55%** |

Auto-compaction threshold is 97%; it was never reached, so **no context compaction occurred**
and the whole run was carried in a single unbroken context.

### Account plan usage (environment-reported, but *not* attributable to this run)

| Window | Used | Resets |
| --- | --- | --- |
| 5-hour limit | 56% | 2026-09-21T16:20Z |
| Weekly, all models | 95% | 2026-09-26T03:00Z |

These are **account-level rolling windows** covering any other work on the account in the
same period. They cannot be attributed to this run alone and should not be read as its cost.

### Not available

* **Cumulative tokens consumed** — no counter exposed.
* **Monetary cost** — not exposed; subscription plan, extra usage disabled.

---

## 3. Time

### Active vs idle

```
10:34:21  session created
10:35:03  experiment start state recorded
          ├─ 56.3 min ─────────────────────────────► 11:31:19  first merge
11:33:20  last commit before the pause; agent idle, waiting for Docker
          ├─ 91.9 min idle ───────────────────────►
13:05:13  verification loop 2 begins
13:42:56  final commit
```

**Active working time: 96.0 min**, in two segments: 58.3 min from the start to the last
commit before the pause, and 37.7 min from the resumption to the final commit. The
91.9-minute gap is waiting for a human action and is excluded. 96.0 + 91.9 = 187.9 min wall
clock, which reconciles exactly.

### Per phase

| Phase | Start | End | Minutes | Share of active |
| --- | --- | --- | --- | --- |
| 1 — Acceptance Criteria | 10:35:22 | 10:36:45 | 1.4 | 1.5% |
| 2 — Specification | 10:36:54 | 10:41:19 | 4.4 | 4.6% |
| 3 — Implementation | 10:41:27 | 11:00:19 | **18.9** | 19.7% |
| 4 — Tests | 11:01:08 | 11:16:53 | **15.8** | 16.5% |
| 5 — Verification (loop 1) | 11:17:31 | 11:28:18 | 10.8 | 11.3% |
| 6 — Merge | 11:28:18 | 11:31:19 | 3.0 | 3.1% |
| 7 — Verification loop 2 (containers) | 13:05:13 | 13:24:40 | **19.4** | 20.2% |
| Post-phase documentation, commit, merge | 13:24:40 | 13:42:56 | 18.3 | 19.1% |
| Gaps between phases and post-merge commits | — | — | 4.0 | 4.2% |

The seven numbered phases account for 73.7 min; the remaining 22.3 min of active time is
inter-phase gaps and the documentation, commit and merge work that closes each loop.

**Verification and testing together consumed 47.9% of active time** (phases 4, 5 and 7).
Implementation was 19.7%. Specification and acceptance criteria together were 6.0%. The
remaining 26.4% was merging and documentation.

### Milestones

| Milestone | Time (UTC) | From start |
| --- | --- | --- |
| Backend registration happy path returns 201 | 10:52:49 | 17.8 min |
| Full stack working in a real browser | 10:59:04 | 24.0 min |
| First merge | 11:31:19 | 56.3 min |
| All 87 acceptance criteria verified | 13:24:40 | 77.7 min active |

**Time in fix loops: 28.1 min** (loop 1: 8.5 min; loop 2: 19.6 min) — 29.3% of active time.

---

## 4. Iterations

### Process-level

| | |
| --- | --- |
| Process phases | 7 (6 planned, 1 extra) |
| Verification → Fix → Verification loops | **2** |
| Loop 1 findings | 5 (1 Major, 4 Minor) → 0 new findings on re-verification |
| Loop 2 findings | 2 (2 Major) → 0 new findings on re-verification |
| Branches created | 2 |
| Commits | 17 (15 non-merge + 2 merges) |
| Reverts | 0 |
| Merge conflicts | 0 |
| Amended commits | 2 (both to correct a factual error in the commit's own content before merging) |

### Correction rounds during implementation

These are the feedback cycles inside Phase 3, logged separately from the test phase as the
protocol requires:

| Check | Rounds | Detail |
| --- | --- | --- |
| Backend type check | 2 | 5 errors → 0 |
| Frontend type check | 2 | 1 error → 0 |
| Backend lint | 3 | 5 errors + 2 warnings → 1 error + 1 warning → clean |
| Frontend lint | 2 | 2 errors + 1 warning → clean |
| Development smoke check | 3 | module-resolution failure → HTTP 500 (fsync EPERM on Windows) → 201 |

### Build and install cycles

| | |
| --- | --- |
| Dependency install rounds | 4 |
| Container image builds | 4 (initial, frontend fix, backend fix, final `--no-cache`) |
| Container stack start/stop cycles | 7 |
| Native e2e suite executions | 6 |
| Containerized e2e suite executions | 9 |

### The stability investigation

Three container suite runs **before** the rate-limit fix, then three **after** — this is the
evidence that led to finding F-07:

| Run | Before fix | After fix |
| --- | --- | --- |
| 1 | 31 passed, 15 failed, ~4.8 min | 46 passed, 6.4 s |
| 2 | 46 passed, 6.5 s | 46 passed, 6.5 s |
| 3 | 30 passed, 16 failed, ~4.3 min | 46 passed, 6.4 s |

The instability was **not** flaky tests: the form-configuration endpoint's rate limit was
being exhausted by a full suite run, which is a production defect, not a test defect.

### Not available

**Tool-call count** — the harness exposes no counter. Reconstructing one from memory would
be an estimate, which the protocol forbids.

---

## 5. Output produced

| Area | Files | Lines |
| --- | --- | --- |
| `backend/src` | 31 | 3,088 |
| `backend/tests` | 21 | 3,262 |
| `frontend/src` | 7 | 1,011 |
| `frontend/tests` | 3 | 583 |
| `e2e` | 5 | 421 |
| `docs` | 4 | 1,615 |
| `experiment` | 2 | 1,275 |
| `config` | 1 | 90 |
| **Tracked total (excl. lockfile)** | **106** | |

| Category | Lines |
| --- | --- |
| Production code (`.ts`, `.css`) | 4,099 |
| Test code | 4,243 |
| Documentation | 3,192 |
| Deployment and configuration | 403 |
| **Total authored** | **11,937** |

**Test-to-production line ratio: 1.04 : 1.**

---

## 6. Tests

**397 executed, 397 passing, 0 failing.**

| Suite | Files | Tests |
| --- | --- | --- |
| Backend (Vitest) | 18 | 281 |
| Frontend (Vitest + jsdom) | 3 | 42 |
| End-to-end, native (Playwright × 2 viewports) | 1 | 28 |
| End-to-end, containerized (Playwright × 2 viewports) | 2 | 46 |

### Backend by test level

| Level | Tests | Share |
| --- | --- | --- |
| Unit | 128 | 45.6% |
| API / contract | 51 | 18.1% |
| Component | 42 | 14.9% |
| Integration | 24 | 8.5% |
| Acceptance | 19 | 6.8% |
| Security | 17 | 6.0% |

### Largest test files

| File | Tests |
| --- | --- |
| `unit/registrationSchema.test.ts` | 45 |
| `api/registrations.api.test.ts` | 27 |
| `unit/optionsConfig.test.ts` | 20 |
| `acceptance/userStories.acceptance.test.ts` | 19 |
| `security/security.test.ts` | 17 |
| `integration/registrationFlow.test.ts` | 17 |

### First complete test run

Recorded **before** any change was made in response to it, as required:
**313 tests, 313 passed, 0 failed** at 11:11:37Z. No test failed, so nothing was changed
because of it. **Zero tests were modified to make an implementation pass.**

### Coverage (backend workspace)

| Metric | Covered / total | Percent |
| --- | --- | --- |
| Statements | 597 / 627 | **95.21%** |
| Branches | 270 / 326 | 82.82% |
| Functions | 145 / 151 | 96.02% |
| Lines | 585 / 615 | 95.12% |

18 of 30 source files are at 100% statement coverage. The lowest is
`infrastructure/logging/logger.ts` at 50% — a thin adapter over pino.

Per-suite (unit vs integration) coverage is **not available**: the suites share one
instrumentation run and no per-suite coverage was configured, so splitting it would be an
estimate.

---

## 7. Quality

| Metric | Value |
| --- | --- |
| Lint errors / warnings | 0 / 0 |
| Type-check errors (strict) | 0 |
| Build | success |
| `npm audit` | 0 vulnerabilities |
| Cyclomatic complexity, average | **2.41** |
| Cyclomatic complexity, max | 12 |
| Functions above CC 10 | 3 of 209 |
| Production code duplication | **0.00%** |
| Test code duplication | 1.67% (8 clones, all per-file fixtures) |
| Architecture rule violations | 0 |
| Dependency cycles | **0** |
| Modules / internal dependencies | 34 / 87 |

### Complexity distribution

| Range | Functions | Share |
| --- | --- | --- |
| 1–3 | 169 | 80.9% |
| 4–5 | 19 | 9.1% |
| 6–10 | 18 | 8.6% |
| 11+ | 3 | 1.4% |

---

## 8. Correctness and findings

| | |
| --- | --- |
| User stories | 8 |
| Acceptance criteria | 87 |
| Passed on first evaluation | **84** |
| Passed finally | **87** |
| Regressions | 0 |
| Edge-case failures | 0 |

Three criteria did not pass on first evaluation: AC-002-11 was genuinely untested (F-05),
and **AC-G-14 / AC-G-16 could not be executed at all** because Docker was absent — they were
recorded as unverified rather than inferred from configuration files that read correctly.
That caution proved justified: when they were finally executed, **both failed**, exposing two
Major security defects.

### All findings

| ID | Severity | Loop | Category | Summary |
| --- | --- | --- | --- | --- |
| F-01 | Major | 1 | architecture | Infrastructure imported the application layer, breaking the specified layering rule |
| F-02 | Minor | 1 | duplication | Zod issue formatting duplicated across two config modules |
| F-03 | Minor | 1 | unrequested code | Three exports with no production caller; one had a false justification comment |
| F-04 | Minor | 1 | unrequested code | Two repository methods used only by tests — kept, documented as a trade-off |
| F-05 | Minor | 1 | traceability | Eight criteria unnamed by any test; one genuinely untested |
| **F-06** | **Major** (High security) | 2 | security / deployment | Every served HTML document lacked all five security headers |
| **F-07** | **Major** (High security) | 2 | security / availability | Read-path rate limits would deny the form to participants sharing one address |

**0 Critical. 3 Major, 4 Minor. 6 resolved, 1 accepted as a documented trade-off.**

**Two of the three Major findings were reachable only by running the containers.** Both lived
in the deployment artefact — nginx configuration and rate-limit values — not in application
code, and both had passed a static review.

### Security findings

| Severity | Count | Resolved |
| --- | --- | --- |
| Critical | 0 | — |
| High | **2** | 2 |
| Medium | 0 | — |
| Low | 0 | — |

Separately, the first dependency install reported **1 high and 5 moderate third-party
advisories**, resolved during Phase 3 by version bumps and one override, before the affected
code paths were written. Final state: **0 advisories**.

---

## 9. Rework

| | Loop 1 | Loop 2 |
| --- | --- | --- |
| Files changed | 25 | 21 |
| Lines added | 289 | 754 |
| Lines deleted | 99 | 118 |
| Production files changed | 10 | — |
| Production lines changed | 68 (43 + / 25 −) | small, concentrated in 3 files |
| % of production rewritten | **1.68%** | — |

**Fix commits: 2.** Loop 2's larger line count is mostly new test and configuration files
rather than edits to existing code.

---

## 10. Git

| | |
| --- | --- |
| Baseline commit | `d4a6f12` (2026-09-21T10:20:05Z) |
| Final commit | `8b31571` (2026-09-21T13:42:56Z) |
| Merge commits | `6c2af9f`, `111290f` |
| Commits | 17 (15 non-merge) |
| Reverts / conflicts | 0 / 0 |
| `main` branch touched | **No** — still at `d4a6f12` |
| Experiment inputs modified | **No** |
| Average files per non-merge commit | 11.3 |
| Average lines added per non-merge commit (excl. lockfile) | 545 |
| Largest commit | `c639456` feat(backend) — 40 files, +3,320 |
| Smallest commit | `464be0a` chore — 2 files, +2 / −4 |

Full per-commit data: [`commit-log.csv`](commit-log.csv).

**One further commit exists after the run boundary.** While cross-checking the arithmetic
for this report, the active-time figure recorded during the run (75.7 min) was found to
omit 22.3 minutes of unphased active work. It was corrected to 96.0 min in
`experiment/run-log.json` and `experiment/run-summary.md` by commit `ae47e03`. That commit
appears in `commit-log.csv` marked `post-run correction` and is deliberately **not**
counted in the run totals above, which are bounded by `d4a6f12..8b31571`.

---

## 11. Dependencies and deployment

| | |
| --- | --- |
| Backend runtime dependencies | **9** |
| Backend dev dependencies | 14 |
| Frontend dev dependencies | 9 |
| Installed top-level packages | 428 |
| Advisories at first install | 1 high, 5 moderate |
| Advisories at end | **0** |
| Backend image | 413 MB, runs as `uid=1000(node)`, 0 dev dependencies present |
| Frontend image | 73.7 MB |
| API endpoints | 4 |
| Persistence verified across | `restart` **and** `down` + `up` (container recreation) |

---

## 12. What could not be measured

| Metric | Why |
| --- | --- |
| Cumulative tokens consumed | No counter exposed; not derivable from context-window occupancy |
| Monetary cost | Not exposed; subscription plan, extra usage disabled |
| Tool calls | No counter exposed; a count from memory would be an estimate |
| Harness version | Not exposed to the agent |
| Failed CI runs | No CI configured; no CI run occurred |
| Human time | Not externally supplied |
| Unit vs integration coverage separately | Shared instrumentation run |
| Maximum dependency depth | No depth calculation defined for this run |
| Change-experiment metrics | `PROMPT.md` defines no change experiment for this run |
| Cohesion / modularity / consistency as scores | No deterministic calculation defined; raw module graph recorded instead |
| Real SMTP delivery to a mailbox | No mail server available; composition and failure path verified instead |

### Deliberately excluded (cross-run metrics)

Per the experiment protocol these must **not** be computed from a single run and are left for
external analysis across runs: similarity, time variance, token variance, quality variance,
architecture variance, cost per successful acceptance criterion, and successful acceptance
criteria per 10,000 tokens.

---

## 13. Files in this folder

| File | Contents |
| --- | --- |
| `README.md` | Short index and headline figures |
| `run-statistics.md` | This report |
| `run-statistics.json` | The same data, machine-readable, with per-value provenance |
| `commit-log.csv` | Every commit: SHA, timestamp, subject, files, lines added/deleted |
| `test-inventory.csv` | Every test file with its executed test count and level |
| `coverage-by-file.csv` | Per-file backend coverage |
| `timeline.csv` | Phase and milestone timestamps with durations |
| `methodology.md` | How each figure was obtained, and the provenance rules |
