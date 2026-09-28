# Frozen tooling scaffold

This directory is part of `INPUT_ROOT` and is read-only.

It is a **tooling scaffold**, not an application.

At run start, copy its contents to `IMPLEMENTATION_ROOT`:

`<WORKSPACE_ROOT>/02_Implementation/`

**Separately**, copy `.claude/` from this directory to
`<WORKSPACE_ROOT>/.claude/` (a sibling of `INPUT_ROOT`, not inside
`IMPLEMENTATION_ROOT`) — see `RUN_INSTRUCTIONS.md`'s start-of-run
setup. This is what activates the input-protection and
acceptance-test-freeze hook for the rest of the run, in a harness that
supports Claude Code-style hooks.

Do not implement inside `05_Scaffold/`.

Included:

- Maven / Spring Boot bootstrap and analysis plugins;
- React / Vite / TypeScript bootstrap and frontend tools;
- pinned Docker Compose services;
- Semgrep configuration stub;
- `VERSION_PINS.md` — the ledger of pinned versions and when each was
  last confirmed to actually resolve;
- `.claude/` — the enforcement hook described above.

Not included:

- domain controllers, services, repositories or entities;
- REST endpoints;
- database tables or Flyway domain migrations;
- registration forms;
- feature tests;
- ArchUnit rules that prescribe a layered architecture.
