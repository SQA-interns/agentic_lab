# Experiment Input Bundle

This directory is `INPUT_ROOT`.

It is a self-contained, portable, read-only experiment input bundle.

```text
01_Input_Files/
├── 01_Business/
├── 02_Technical/
├── 03_Process/
│   ├── RUN_INSTRUCTIONS.md
│   ├── CONSTITUTION.md          ← non-negotiable rules, read first
│   ├── SEVERITY_TAXONOMY.md     ← one severity scale, used everywhere
│   ├── PREFLIGHT_CHECKLIST.md   ← human step, before experimentStart
│   ├── HUMAN_INPUTS_MANIFEST.md ← secrets/keys this run might need
│   ├── ACCEPTANCE_CRITERIA_RULES.md
│   ├── DEFINITION_OF_DONE.md
│   ├── METRICS.md
│   └── run-log.template.json
├── 04_Skills/
│   ├── derive-acceptance-criteria/
│   ├── write-specification/
│   ├── write-acceptance-tests/  ← NEW — before implementation, frozen
│   ├── implement/
│   ├── write-unit-tests/        ← renamed from write-tests — additive only
│   ├── verify/
│   └── finalize-run/
└── 05_Scaffold/
    ├── backend/ frontend/ docker-compose.yml
    ├── VERSION_PINS.md          ← pinned versions, last verified date
    └── .claude/                 ← enforcement hook, copied to WORKSPACE_ROOT
```

## Portability

To reuse this experiment setup in another repository or workspace,
copy the entire `01_Input_Files/` directory there, and also copy
`AGENTS.md` (at the repository root, one level above `01_Input_Files/`)
alongside it.

The workspace must also contain, or allow creation of:

- `02_Implementation/`
- `03_Run-Statistics/`
- `04_External-Audit/` (optional at first — filled in after the run,
  never by the agent)

This portability step, and completing `03_Process/PREFLIGHT_CHECKLIST.md`,
are workspace setup operations. Neither is part of the development
agent's timed run.

## During an experimental run

Everything under `INPUT_ROOT` is immutable and read-only.

The agent must not copy `01_Business/`, `02_Technical/`,
`03_Process/`, or `04_Skills/` into the implementation workspace.

At run start, copy only the **contents** of:

`<INPUT_ROOT>/05_Scaffold/`

into:

`<IMPLEMENTATION_ROOT>/`

Expected result:

```text
<IMPLEMENTATION_ROOT>/
├── backend/
├── frontend/
├── docker-compose.yml
└── ...
```

Not this:

```text
<IMPLEMENTATION_ROOT>/05_Scaffold/
```

and not this:

```text
<IMPLEMENTATION_ROOT>/
├── 01_Business/
├── 02_Technical/
├── 03_Process/
├── 04_Skills/
└── 05_Scaffold/
```

After initialization, all application development takes place only
inside `IMPLEMENTATION_ROOT`.

Experimental logs and statistics are written only to `STATISTICS_ROOT`.

Path conventions are defined in `03_Process/RUN_INSTRUCTIONS.md`.

Do not assume a repository name or absolute filesystem path.
