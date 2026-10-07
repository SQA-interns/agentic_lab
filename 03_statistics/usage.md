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
| API time | 19m 8s (wall 19h 28m 47s) |
| Approval prompts | not shown in the panel |
| Cost shown | $12.28 (claude-opus-5-5: 2.3k input, 65.0k output, 49.5m cache read, 135.1k cache write) |
| Difference to `usage.costUsd` | −$30.44 ($12.28 vs $42.72) |

Read 2026-10-07 from the `/cost` panel ("Session"). The panel covers only the last Claude Code process of the run: the run was resumed several times, and the panel's 65.0k output and 49.5m cache-read tokens are a fraction of the transcript totals (458.6k output, 118.2m cache read). Its 19m 8s API time is also below the transcript's model-time bounds (64–95 min). The transcript figure in `run-log.json` therefore stays the whole-run value; the panel value is a partial cross-check, not the run total.
