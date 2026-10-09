# Usage (human)

> Owner: Experiment lead · Filled: prices before the post-run session, the rest after the run · Agent: reads prices only

## Prices used for `usage.costUsd`

Source: Anthropic first-party API prices, as listed in the Claude Code `claude-api` reference (model table cached 2026-09-25). Check them against https://www.anthropic.com/pricing before each run and change the date if they changed.

Price table date: 2026-09-25

USD per million tokens. Claude Code writes 1-hour cache entries, so the cache-write column holds the 1-hour price (5-minute writes would be $5.00).

| Model | Input / MTok | Output / MTok | Cache read / MTok | Cache write / MTok |
|---|---|---|---|---|
| claude-opus-5-5 | 4.00 | 20.00 | 0.20 | 8.00 |

Tokens, model calls, tool calls and `usage.costUsd` are filled from the transcript by
`node 01_input/00_general/tools/usage-from-transcript.mjs --write` (phase 7, last step). The cost is
an API-price equivalent; a subscription bills differently.

## From the harness usage panel

Only a human can read these; fill them right after the run. `usage.estimates` in `run-log.json` holds what the transcript allows instead (model time as a lower and upper bound, permission refusals) as a cross-check; approved commands are not in a transcript.

| Item | Value |
|---|---|
| API time | not shown: subscription plan; the usage panel shows only plan-limit percentages (transcript estimate in `usage.estimates`: 40 to 77 minutes of model time) |
| Approval prompts | not counted (Auto permission mode). Decision answers by the human: 3 (D-07 vitest 4.1.11, D-08 CVE-2025-7962 false positive, D-20 Basic auth Low); the other human interventions in `run-log.json` are "continue" after the agent stopped at passed gates and one resume after the session usage limit |
| Cost shown | not shown: subscription plan, no per-session cost in the usage panel |
| Difference to `usage.costUsd` | n/a (no panel cost; `usage.costUsd` is the API-price equivalent from the transcript) |

Threat to validity: the run agent worked in the same folder as the operator's Claude Code chat and therefore shared its project memory. Operator notes from other runs (including the confreg experiment and notes on this run's expected decisions) were visible to it from the start; it also wrote its own memory file there. The notes were moved out of the shared memory during phase 5. Host JDK was Oracle 21.0.11 instead of the pinned Temurin 21.0.10+7 (D-01, non-blocking).
