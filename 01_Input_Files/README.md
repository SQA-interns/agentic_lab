# Experiment input package

This directory is `INPUT_ROOT`. It is a self-contained portable bundle.

```
01_Input_Files/
├── 01_Business/
├── 02_Technical/
├── 03_Process/
├── 04_Skills/
└── 05_Scaffold/
```

Copy this directory (together with empty `02_Implementation/` and
`03_Run-Statistics/` siblings, or create those siblings) into any
project.

Path names are defined in `03_Process/RUN_INSTRUCTIONS.md`.

At run start the agent copies `05_Scaffold/` to `IMPLEMENTATION_ROOT`.

Do not assume a repository name or an absolute filesystem path.
