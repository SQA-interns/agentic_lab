`EXTERNAL_AUDIT_ROOT`.

Filled in **after** the run, by a human, never by the agent — the
agent must not read from or write to this directory during the run
(see `01_Input_Files/03_Process/RUN_INSTRUCTIONS.md`, Phase 7).

Expected contents once a run has been audited:

- `usage-report.md` — the harness's own usage/cost report for this
  run's session, saved verbatim (tokens, cost, wall time, API time).
- `tool-call-count.md` — a tool-call and approval-prompt count parsed
  from the harness transcript, if the harness makes one available.
- `code-quality.json` (optional) — output of a frozen-toolchain rerun
  (coverage, duplication, complexity, mutation score) done
  independently of the agent's own self-measured figures in
  `03_Run-Statistics/`.

This folder is intentionally allowed to start empty and stay empty for
a while — a run whose `03_Run-Statistics/run-log.json` correctly
records `tokensConsumed: null` with a reason is still a complete run.
It stops being complete only if this folder is never filled in at all
and the run's efficiency is still reported as if it had been.
