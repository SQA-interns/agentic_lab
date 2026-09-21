# Methodology

How every figure in this folder was obtained, and the rules applied to values that could
not be obtained.

---

## Provenance markers

Each value in `run-statistics.json` carries one of these:

| Marker | Meaning |
| --- | --- |
| `environment-reported` | Read from an API the harness exposes. Exact value as returned, with the reading timestamp. |
| `tool-measured` | Output of a deterministic tool run against the repository. Reproducible by re-running the command. |
| `git-derived` | Computed from the repository history, which is durable and independently checkable. |
| `recorded-during-run` | Written into `experiment/run-log.json` as the event occurred, with a timestamp taken at that moment. |
| `unavailable` | Not obtainable. Recorded as `null` with the reason. **Never estimated.** |

---

## The non-estimation rule

`PROMPT.md` forbids estimating token consumption, monetary cost, tool-call counts and
architecture scores with no defined calculation. That rule is applied strictly here.

Where a plausible-looking number could have been produced by inference, it was left
`null` instead. In particular:

* **Cumulative tokens** could have been guessed from turn counts. It was not.
* **Tool calls** could have been reconstructed from memory of the session. It was not —
  recollection is not measurement.
* **Cohesion and modularity** could have been scored subjectively. They were not; the raw
  module graph was recorded instead, for external evaluation.

---

## Tokens — what was read and what it means

**Source:** `mcp__ccd_session_mgmt__get_usage` with `session_id: "self"`.

This returns two distinct things:

### 1. `context` — the session's context-window occupancy

```json
{ "tokensUsed": 545695, "contextWindow": 1000000, "percentUsed": 55,
  "autoCompactsAtPercent": 97, "categories": [...] }
```

This is **how much of the window the retained conversation currently fills**. It is a
point-in-time snapshot and is *not* cumulative consumption. Cumulative consumption counts
every token sent and received across all turns, and is much larger because each turn
re-sends the accumulated context.

Two readings were taken 80 seconds apart (531,970 → 545,695) specifically to demonstrate
that the figure moves as work continues, so no reader mistakes it for a run total.

`autoCompactsAtPercent: 97` was never reached, so no compaction occurred — the entire run
was carried in one unbroken context. That is itself a recorded measurement.

### 2. `plan` — account-level rolling windows

```json
{ "label": "5-hour limit", "percentUsed": 56 }
{ "label": "Weekly · all models", "percentUsed": 95 }
```

These are **account-wide**, covering any other work on the account in the same windows.
They are recorded for completeness but explicitly marked as **not attributable to this run**.

### What is not available

No cumulative token counter and no per-run cost figure is exposed. The account is on a
subscription plan with extra usage disabled, so there is no monetary figure to read either.

---

## Time

**Source:** `date -u +%Y-%m-%dT%H:%M:%SZ` executed at each phase boundary during the run
and written into `experiment/run-log.json`; cross-checked against git commit timestamps,
which are independent and durable.

**Active vs wall-clock.** The run contains a 92-minute gap between the first merge and the
start of verification loop 2, during which the agent was idle waiting for the human to
install Docker. Reporting wall-clock time alone would overstate the work by more than
double, so both are given and the idle period is stated explicitly.

Phase share percentages are computed against the 75.7-minute **active** total.

Commit timestamps in `commit-log.csv` are in local time with offset (`+02:00`) exactly as
git records them; the narrative figures are UTC. `12:35:16+02:00` and `10:35:16Z` are the
same instant.

---

## Iterations

Counts were taken only where a durable artefact supports them:

| Count | Source |
| --- | --- |
| Phases, fix loops, findings | `experiment/run-log.json`, written as events occurred |
| Commits, merges, reverts, conflicts | `git rev-list`, `git log` |
| Correction rounds (lint, type check, smoke) | Recorded in the run log's `developmentFeedbackChecks` at the time |
| Container builds, stack cycles, suite executions | Recorded during loop 2 as each was run |
| e2e stability runs | Tool output captured directly from the three-times-repeated runs |

**Tool-call count is `null`.** No counter is exposed. Counting from recollection would be an
estimate dressed as a measurement.

---

## Code and test measurements

Every figure here is reproducible by running the command shown.

| Metric | Command |
| --- | --- |
| Lines of code | `wc -l` over git-tracked files, grouped by directory |
| Test counts | `vitest run --reporter=json`, counting `assertionResults` — this counts **executed** cases, so a parameterised `it.each` with 7 cases counts as 7, not 1 |
| Coverage | `vitest run --coverage` (v8), read from `coverage/coverage-summary.json` |
| Cyclomatic complexity | `eslint` with the `complexity` rule set to threshold 0, which makes it report every function's value; parsed from JSON output |
| Duplication | `jscpd --min-lines 5 --min-tokens 50`, production and test trees measured separately |
| Architecture | `dependency-cruiser` with the project's 6 rules; module and dependency counts from its JSON output |
| Dependency advisories | `npm audit` |
| Container facts | `docker images`, `docker inspect`, `docker compose exec` against the running stack |

### A note on test counts

Declared `it(...)` blocks and executed test cases differ, because `it.each` expands. The
backend has 213 declared blocks but **281 executed cases**. The executed count is what is
reported, since that is what actually ran and passed.

---

## Acceptance-criteria counts

The 87 criteria come from `docs/acceptance-criteria.md`. "Passed on first evaluation" (84)
and "passed finally" (87) are from the verification report.

Two criteria — AC-G-14 and AC-G-16 — are counted as **not passing on first evaluation**,
because when they first became executable (once Docker was installed) they failed, exposing
findings F-06 and F-07. Counting them as passing because the configuration "looked correct"
before it could be run would have been exactly the error the run is meant to avoid.

---

## Rework percentage

`percentOfProductionRewritten` = (production lines added + production lines deleted in the
fix commit) ÷ (production lines present at the end of the preceding phase) × 100.

For loop 1: (43 + 25) ÷ 4,047 = **1.68%**. Measured from `git show --numstat` restricted to
`backend/src` and `frontend/src`, and `git ls-tree` at the pre-fix commit.

`package-lock.json` is excluded from every line-count figure, because it is generated and
its thousands of lines would dominate and distort the totals. Both the raw and
lockfile-excluded figures are present in `commit-log.csv` so either can be used.

---

## Deliberately not calculated

Per `PROMPT.md`, these are cross-run metrics and must not be derived from a single run:

* similarity between runs
* time variance, token variance, quality variance, architecture variance between runs
* cost per successful acceptance criterion
* successful acceptance criteria per 10,000 tokens

The raw inputs needed to compute them externally are present in `run-statistics.json` —
except the token denominator, which is not exposed by this environment.

---

## Reproducing these statistics

From `E:\13 - Lab Projects\agentic_lab`:

```bash
npm run lint
npm run typecheck
npm test
npm run test:coverage --workspace backend
npm run build
npm audit
npx depcruise src --config .dependency-cruiser.cjs        # from backend/
npx jscpd backend/src frontend/src --min-lines 5 --min-tokens 50
```

For the container figures, `docker compose up -d` first, then
`npm run test:e2e:container` (which needs `RATE_LIMIT_REGISTRATION_MAX` raised in `.env`;
see the README).

Token figures cannot be reproduced retrospectively — context-window occupancy is a live
value that changes with every turn. The readings here are timestamped for that reason.
