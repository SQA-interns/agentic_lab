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

Only a human can read these; fill them right after the run.

| Item | Value |
|---|---|
| API time | |
| Approval prompts | |
| Cost shown | |
| Difference to `usage.costUsd` | |
