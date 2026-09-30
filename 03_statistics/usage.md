# Usage (human)

> Owner: Experiment lead · Filled: prices before the post-run session, the rest after the run · Agent: reads prices only

## Prices used for `usage.costUsd`

Source and date: Anthropic first-party API list prices for `claude-opus-5-5`, from the Claude Code `claude-api` skill model table (cached 2026-09-25), read 2026-09-30. Prices are in USD. Cache write uses the 1-hour TTL rate (2× input), which this harness uses. The 5-minute TTL rate is 5.00 (1.25× input).

| Model | Input / MTok | Output / MTok | Cache read / MTok | Cache write / MTok |
|---|---|---|---|---|
| Opus 5.5 (`claude-opus-5-5`) | 4.00 | 20.00 | 0.20 | 8.00 |

## From the harness usage panel

| Item | Value |
|---|---|
| API time | 54m 11s |
| Approval prompts | 4 |
| Cost shown | $29.51 / raw cost $18.25 - $21.55 |
| Token totals shown | input 384, output 1.6k, cache read 63.6M, cache write 1.1M |
| Difference to `usage.costUsd` | pending: fill in after the post-run session sets `usage.costUsd` (cost shown − `usage.costUsd`) |

Worth knowing before that session: the panel's own token totals at these prices come to about $21.55, or about $18.25 at the 5-minute cache rate. Neither matches the $29.51 it shows. Almost all of the cost is cache reads (63.6M) and cache writes. The post-run session will probably find a real gap here. Possible reasons are rounding in the panel, cache reads being charged differently, or subagent calls. That's worth writing up when the difference is filled in, rather than treating it as an error.

Idling does cost something indirectly. If the pause is longer than the cache lifetime (1 hour here, 5 minutes on the short setting), the cached context expires. The next call then writes the whole context to the cache again at the write rate ($8/MTok) instead of reading it at $0.20/MTok. With about 1M tokens of context, one expired cache costs roughly $8 instead of $0.20.