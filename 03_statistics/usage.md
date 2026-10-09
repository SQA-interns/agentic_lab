# Usage (human)

> Owner: Experiment lead · Filled: prices before the post-run session, the rest after the run · Agent: reads prices only

## Prices used for `usage.costUsd`

Source and date: Anthropic first-party API list prices for `claude-opus-5-5`, from the `claude-api` reference bundled with Claude Code 2.1.296 (model table cached 2026-10-06), read 2026-10-10. Cache-write rates follow the published multipliers (1.25× input for the 5-minute TTL, 2× for the 1-hour TTL). Not cross-checked against the live pricing page.

| Model           | Input / MTok | Output / MTok | Cache read / MTok | Cache write / MTok                  |
| --------------- | ------------ | ------------- | ----------------- | ----------------------------------- |
| claude-opus-5-5 | $4.00        | $20.00        | $0.20             | $5.00 (5 min TTL) / $8.00 (1 h TTL) |

## From the harness usage panel

| Item                          | Value                                                                                                                                                                                                                                                 |
| ----------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| API time                      | not shown in the panel (panel shows "Active 1s" only)                                                                                                                                                                                                 |
| Approval prompts              | not shown in the panel and not recorded in the transcript export (`chat1.md` holds no permission dialogs and no denied tool calls); blocking decisions the human answered in chat (phase 0 defaults, D-11/D-12, D-24) are in `run-log.json`, not here |
| Cost shown                    | $24.32 (this session, read 2026-10-09T21:47:14Z, run paused at phase 6 waiting for D-24)                                                                                                                                                              |
| Difference to `usage.costUsd` | computed in the post-run session (`usage.costUsd` not yet calculated)                                                                                                                                                                                 |

Panel token breakdown at the same time (Opus 5.5, this session): input 56, output 292, cache read 12.6M, cache write 452k, cache hit 97 %. Plan limits at the same time: session 31 % (resets Oct 10, 00:59 Europe/Ljubljana), week 23 % (resets Oct 14, 02:59). The earlier session limit reached 100 % during phase 6 and the run resumed after the reset.

Only one usage-panel reading was captured in the transcript (the $24.32 above); later `/usage` calls left no output in it, so there is no final cost reading after phase 7. Run span from `run-log.json`: 2026-10-09T10:43:03Z to 2026-10-09T22:03:12Z, transcript `e4fa88c4-16cb-5a0f-a8fa-d81592591495.jsonl`. The session was later continued from another machine as `f5dd31c2-469e-4828-a34e-4955106bd9a4`, whose transcript likely repeats the same history; count each model call once.
