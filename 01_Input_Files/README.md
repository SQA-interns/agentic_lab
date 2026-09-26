# Experiment Input Bundle

This directory is `INPUT_ROOT`.

It is a self-contained, portable, read-only experiment input bundle.

```text
01_Input_Files/
├── 01_Business/
├── 02_Technical/
├── 03_Process/
├── 04_Skills/
└── 05_Scaffold/
```

## Portability

To reuse this experiment setup in another repository or workspace,
copy the entire `01_Input_Files/` directory there.

The workspace must also contain, or allow creation of:

- `02_Implementation/`
- `03_Run-Statistics/`

This portability step is a workspace setup operation. It is not part of
the development agent's run.

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
