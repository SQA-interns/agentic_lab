# Usage (human)

> Owner: Experiment lead · Filled: prices before the post-run session, the rest after the run · Agent: reads prices only

## Prices used for `usage.costUsd`

Source and date:

| Model | Input / MTok | Output / MTok | Cache read / MTok | Cache write / MTok |
|---|---|---|---|---|

## From the harness usage panel

| Item | Value |
|---|---|
| API time | not shown in the panel (panel shows "Active 1s" only) |
| Approval prompts | not shown in the panel |
| Cost shown | $24.32 (this session, read 2026-10-09T21:47:14Z, run paused at phase 6 waiting for D-24) |
| Difference to `usage.costUsd` | computed in the post-run session (`usage.costUsd` not yet calculated) |

Panel token breakdown at the same time (Opus 5.5, this session): input 56, output 292, cache read 12.6M, cache write 452k, cache hit 97 %. Plan limits at the same time: session 31 % (resets Oct 10, 00:59 Europe/Ljubljana), week 23 % (resets Oct 14, 02:59). The earlier session limit reached 100 % during phase 6 and the run resumed after the reset.
