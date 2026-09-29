# Run configuration

> Owner: Human operator · Read in: phase 0 · Agent: read-only

- Workflow: sdd.
- Model: Opus 5.5 (the team's selected model label). Record the actual provider/model
  identifier from the harness/transcript; do not invent an identifier or substitute
  another model. If unavailable, this is an environment blocker.
- Effort: medium; verify provider support rather than guessing an equivalent.
- Template version: 1.0 (root template unchanged).
- Project-input revision: conference-human-1.
- Run ID: conference-single-sdd-001. A repeat must receive a new ID before launch.
- System: one development agent; no spawning/delegation. Ordinary available tools
  allowed; no application feature requires MCP. Record actual harness/tool settings.
- Starting commit: clean HEAD after the human-input preparation commit; record the
  actual SHA as startCommit at phase 0. Do not use the ZIP's old SHA for changed inputs.
- Source template commit: ab4fc3fa093fb1d52fec99ec7f43db6a22690c00,
  observed in the uploaded archive's 00_File_Structure branch reference.
- Intended run branch: run/single-sdd/conference-001; operator creates/selects it
  and commits the filled inputs before launching. This preparation has not been executed.
- Repository: https://github.com/SQA-interns/agentic_lab
- Workspace: /home/tjan-kazar/storage/Work/lab/agentic_lab
- Goal: complete all SDD phases and US-001…008, including verification and handoff;
  no demo-only stopping. Current turn prepares inputs only.
- Budget: no project-imposed token/time cap; actual provider quotas still apply.
  Avoid unchanged retries. Preserve progress if execution is interrupted.
- Data/deployment: synthetic local experiment. No real participant data, production
  deployment, external mail, remote push or publication authorized.

Pre-run operator tasks: copy this input overlay, provide ignored .env values, make
the preparation commit and supply the selected model/tooling. No secrets are
included. Exact pins are decisions, not claims of installed or secure packages.
Phase 0 must check them; missing host software still needs operator installation
under the unchanged general rules. Capture runtime/harness facts in generated
preflight evidence rather than modifying these frozen inputs during the run.

Root README, AGENTS.md and statistics contract from the full archive are retained.
This preparation does not create implementation, run-log, acceptance tests or results.
