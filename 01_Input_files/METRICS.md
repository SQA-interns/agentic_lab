# System-written research record

You write `03_Metrics/run.json`, `events.jsonl`, `run-summary.md`, and
`evidence/`. The unit is the **whole system configuration on one task**, not an
individual model or agent. This run is single-agent. Future comparisons must
retain the same product, architecture, completion gates and measurement rules.

## Initialize and record with little overhead

Read the actual system clock and create `run.json` before design or coding.
Record the run ID, task ID (`conference-registration-v3`), supplied configuration
label, observable model/harness/tool identities, actor count (1), input-file
hashes, budgets, environment versions and UTC start. Include available CPU/RAM
limits, network/cache state, tool permissions, context/compaction and sampling
settings; for local inference record runtime, model artifact/quantization and
hardware. Unknown fields are null
with reasons. Do not infer model identity from a folder name.

Use a small generated script to append events, time commands and collect
machine-readable test outputs. Store the script under `02_Implementation/tools`
and label instrumentation separately from application code. Do not manually
log every read or tool invocation. Capture phase boundaries, verified milestones,
substantive repairs, deviations, human interventions and final checks.

Each event is JSON with `id`, `timestampUtc`, `kind`, `actor`, `relatedIds`,
`description`, and `evidencePaths`. For repair episodes include `start`, `end`,
`category` (implementation/test/specification/environment/coordination/unknown),
`trigger`, `change`, and `outcome`. Amend a prior event by appending a correction
with its ID; preserve earlier observations. Never reconstruct precise times
from memory. Record a missed observation as unavailable.

## Core comparison measures

| Measure | Definition and source |
|---|---|
| Outcome | DoD gate results plus evidence for each of the eight fixed user stories; `done` only if all gates pass. Report functional completion separately. |
| Architecture adherence | AR/ST IDs violated, evidence, first observed time, resolved/final state and authorization. Count unique violated rules, not affected files. |
| Elapsed time | Run start to final reporting end, measured from clock; separately report time to verified external-registration slice and time final application checks finished. Include setup, failures, waiting and reporting in total. |
| Resource use | Actual input/output/cached/reasoning tokens, cost and model/tool calls only if exposed by provider/harness. Preserve provider accounting; cached/reasoning tokens may be subsets, so do not blindly add them twice. Null with reason otherwise. |
| Rework | Substantive failed check → change → rerun episodes, category, result, elapsed span and evidence. Also record a previously passing check regressing and repeated identical failure attempts. |
| Human effort | Clarifications, approvals, hints and manual edits with timestamps, reasons and observed waiting duration. Distinguish required approval from help correcting the system. |
| Reporting completeness | Which required fields/evidence are missing; self-reported versus tool-measured versus externally supplied provenance. A recorded null explains missingness but does not make the quantity measured. |

Do not calculate a 'rumination score': long reasoning and no file diff do not
establish waste. Report observable repeated failures and rework; interpret
them after the run. Do not use lines of code, commits, tests written or token
count alone as productivity. Counted tests vary with test granularity and are
not a fair quality denominator across configurations.

## Compact run.json shape

Use these top-level keys; every measurement with a numeric value must include
`source` (tool/self-report/provider/harness) and `evidencePaths`. A missing
value uses `value: null, reason: ...`. No guessed zero values.

```json
{
  "schemaVersion": "3.0",
  "identity": {},
  "timestamps": {},
  "phases": [],
  "milestones": [],
  "outcome": {"status": "in-progress", "stories": [], "gates": []},
  "architectureDeviations": [],
  "repairs": [],
  "humanInterventions": [],
  "usage": {},
  "checks": [],
  "git": {},
  "missingMeasurements": []
}
```

`identity`: configuration/task/run IDs, actors, model/harness/tool versions,
input hashes, environment and budgets. `timestamps`: start, implementation
checks finished, end. `phases`: name/start/end; phase duration includes its
waits, and overlapping intervals must not be summed into wall time.
`milestones`: one entry per M0–M6 from WORKFLOW, with ID, status
(not-started/in-progress/blocked/pass/regressed), startedAt, firstPassedAt,
lastVerifiedAt, revision and evidencePaths; unavailable times are null with reason.
Pass requires all milestone criteria, not one successful command. Preserve the
first pass time after a later regression; record regression and recovery.
Generated sub-slices do not change the fixed milestone denominator. M1 defines
time to first verified external registration. An unreached milestone has no
completion time; never substitute the run's timeout for it.
`stories`/`gates`: ID/status/evidencePaths/reason.
`checks`: ID, command, start/end, exit code, tool version, first/final suite,
test counts, diagnostics and evidence. "First complete suite" means the first
full finalization suite after incremental testing, not first-attempt coding
success. Preserve earlier failing checks and specify suite membership.
`git`: baseline/final implementation
revision, checkpoints, reverts/conflicts or unavailability reason.

Store report-producing commands, stdout/stderr and screenshots in `evidence/`.
Redact secrets and use synthetic participants. Security findings remain
separate per scanner; retain native severity and count units. Record unit and
integration coverage separately with the tool's actual denominator. These
are supporting diagnostics, not a synthesized performance score.

## Required detailed run-summary.md

1. **Configuration and provenance:** run/task/input hash, complete system
   setup, environment, baseline/final revision, and observability limits.
2. **Verdict:** functional behavior and each DoD gate, not merely test totals.
3. **Timeline:** phases, verified milestones, build/setup, waiting and reporting;
   identify unavailable or approximate self-reported boundaries.
4. **Delivered behavior:** eight US IDs and acceptance references with evidence.
5. **Verification table:** first complete versus final suite, exact commands,
   failures/errors/skips, static/security/architecture/container results.
6. **Decisions and deviations:** fixed-rule departures and their dispositions;
   permitted implementation choices must not be counted as violations.
7. **Repair episodes:** trigger, cause category, change, rerun outcome, evidence;
   regressions and repeated failures. No invented private-reasoning analysis.
8. **Resources and human effort:** measured usage/time, interventions and missing
   values; reporting/instrumentation is part of configuration cost.
9. **Limitations and reproduction:** remaining issues, all missing measurements,
   startup/check commands via the implementation README, raw evidence links.

Finish the summary, write end time and final elapsed value, then only perform
mechanical report validation. If more substantive work follows, update the end.
For future multi-agent runs aggregate usage across every actor, report end-to-end
wall time separately from summed actor durations, and record delegation,
integration failures and handoffs where observable. Do not penalize parallel
work by treating summed actor time as elapsed runtime. One agent's unavailable
usage makes the aggregate partial, not complete.
