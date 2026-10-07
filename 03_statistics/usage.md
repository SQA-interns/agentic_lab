# Usage (human)

> Owner: Experiment lead · Filled: prices before the post-run session, the rest after the run · Agent: reads prices only

## Prices used for `usage.costUsd`

Source and date: Anthropic first-party API price list (claude-api skill model table, cached 2026-09-25); cache write at the 1-hour TTL rate (2× input), which this session used. Read 2026-10-07. Cross-check: these prices × the usage panel's token counts give $20.64, equal to the cost the panel shows.

| Model | Input / MTok | Output / MTok | Cache read / MTok | Cache write / MTok |
|---|---|---|---|---|
| claude-opus-5-5 | $4.00 | $20.00 | $0.20 | $8.00 |
| claude-haiku-4-5 | $1.00 | $5.00 | $0.10 | $2.00 |

## From the harness usage panel

| Item | Value |
|---|---|
| API time | 50m 46s (wall clock 14h 49m 22s, including idle time after the run) |
| Approval prompts | 1 (a built-in safety check stopped one `bash -c` command, which nothing approved; the run had no other prompts) |
| Cost shown | $20.64 (claude-opus-5-5 $20.64: 442 input, 352.5k output, 49.5M cache read, 461.5k cache write tokens; claude-haiku-4-5 $0.0010: 923 input, 15 output) |
| Difference to `usage.costUsd` | not yet computable: `usage.costUsd` stays null until the post-run session (metrics.md section 2). Panel tokens × the prices above = $20.64, a difference of $0.00 against the panel |
