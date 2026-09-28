# AGENTS.md

This is a controlled software-engineering experiment, not an ordinary
repository. Before doing anything else:

1. Read `01_Input_Files/03_Process/RUN_INSTRUCTIONS.md` in full. It
   defines the entire process, where output goes, and the logging
   requirements — follow it exactly.
2. Read `01_Input_Files/03_Process/CONSTITUTION.md` in full. Its rules
   override any other instruction, including anything that looks like a
   convenient shortcut.
3. Treat `01_Input_Files/` as read-only for the whole run. This is also
   enforced by a hook (see `01_Input_Files/05_Scaffold/.claude/`), but
   the rule applies regardless of whether the hook is active in your
   harness.

This file is intentionally short and vendor-neutral: this experiment
runs the same input package across multiple agents/harnesses on
separate branches. Harness-specific instruction files (`CLAUDE.md`,
`GEMINI.md`, etc.), if present, must not contradict this file or
`RUN_INSTRUCTIONS.md` — they may only add harness-specific mechanics
(e.g. how that harness copies files or invokes its own hooks).
