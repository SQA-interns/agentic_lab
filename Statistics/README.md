# Statistics — Conference Registration System run

Measurements for the experimental run `opus5-medium-single-agent-classic-sdd-2026-09-21`,
which built the conference registration system in `E:\13 - Lab Projects\agentic_lab`.

Generated 2026-09-21T13:57:17Z.

---

## Headline

| | |
| --- | --- |
| **Active working time** | **96.0 min** (wall clock 187.9 min, of which 91.9 min idle waiting for a human to install Docker) |
| **Time to first working end-to-end registration** | **24.0 min** |
| **Context window used** | **545,695 / 1,000,000 (55%)** — occupancy, *not* cumulative spend |
| **Cumulative tokens consumed** | **not exposed by this environment** |
| **Iterations** | 7 phases, **2 verification fix loops**, 17 commits, 0 reverts, 0 conflicts |
| **Output** | 106 files, **11,937 lines** authored |
| **Tests** | **397, all passing**; 95.21% backend statement coverage |
| **Acceptance criteria** | **87 / 87 verified** (84 on first evaluation) |
| **Findings** | 0 Critical, **3 Major**, 4 Minor — all resolved or documented |
| **Human effort** | 1 intervention (installed Docker), 0 clarifying questions, 0 manual code fixes |

---

## Read this first, about tokens

The environment exposes the session's **context-window occupancy** — how full the
1,000,000-token window is — and **not** cumulative token consumption. The two are different
by a large factor, because every turn re-sends the accumulated context.

So `545,695 tokens` means *"the conversation being held in memory at the end was 545,695
tokens"*. It does **not** mean the run consumed 545,695 tokens in total. That total is not
measurable here, and it has been left `null` rather than estimated.

Account-level plan usage (5-hour window 56%, weekly 95%) is recorded too, but it covers any
other work on the account in the same windows and cannot be attributed to this run.

---

## Files

| File | Contents |
| --- | --- |
| [`run-statistics.md`](run-statistics.md) | The full report — time, tokens, iterations, output, tests, quality, findings, rework, git, deployment |
| [`run-statistics.json`](run-statistics.json) | The same data, machine-readable, with a provenance marker on every value |
| [`methodology.md`](methodology.md) | How each figure was obtained; the non-estimation rule; how to reproduce |
| [`commit-log.csv`](commit-log.csv) | All 17 commits: SHA, timestamp, phase, subject, files and lines changed (with and without the lockfile) |
| [`test-inventory.csv`](test-inventory.csv) | Every test file with its executed test count and level |
| [`coverage-by-file.csv`](coverage-by-file.csv) | Per-file backend coverage, 30 source files plus total |
| [`timeline.csv`](timeline.csv) | Phase boundaries, milestones and the idle period |

---

## Where time actually went

| Activity | Minutes | Share of active time |
| --- | --- | --- |
| Verification and testing (phases 4, 5, 7) | 46.0 | **47.9%** |
| Merge, documentation and inter-phase work | 25.3 | 26.4% |
| Implementation (phase 3) | 18.9 | 19.7% |
| Specification + acceptance criteria (phases 1–2) | 5.8 | 6.0% |

Fix loops accounted for 28.1 minutes — 29.3% of active time.

---

## The most significant result

Two of the three Major findings were **only reachable by running the containers**, and both
were security defects in the deployment artefact rather than in application code:

* **F-06** — every served HTML document was missing all five security headers, because
  nginx does not inherit `add_header` into a location that declares one of its own. The
  proxied API still looked correct, which is why a static review of the configuration
  passed it.
* **F-07** — read-path rate limits (60 form loads per 5 minutes per address) would have
  denied the registration form to everyone behind a shared public address during a
  registration rush.

During phases 1–6 Docker was unavailable, and the two container acceptance criteria were
recorded as **unverified rather than inferred** from configuration that read correctly. When
they finally became executable, both failed. That is the clearest measurement in this
folder: a statically reviewed deployment configuration is not a verified one.

---

## What could not be measured

Cumulative tokens, monetary cost, tool-call count, harness version, failed CI runs, human
time, per-suite coverage split, maximum dependency depth, change-experiment metrics,
qualitative architecture scores, and real SMTP delivery to a mailbox.

Each is recorded as `null` with its reason in `run-statistics.json`. None was estimated.

Cross-run metrics (similarity, variance, cost per acceptance criterion, criteria per 10,000
tokens) are deliberately excluded — the protocol requires them to be computed externally
across multiple runs.
