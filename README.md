# Agentic development experiment archetype

```
<WORKSPACE_ROOT>/
├── 01_Input_Files/          INPUT_ROOT (read-only)
├── 02_Implementation/       IMPLEMENTATION_ROOT (agent works here)
└── 03_Run-Statistics/       STATISTICS_ROOT
```

Copying `01_Input_Files/` into another project is a human setup step.
It is not part of an agent run.

At run start the agent copies only the contents of
`01_Input_Files/05_Scaffold/` into `02_Implementation/`.

Path names are defined in `01_Input_Files/03_Process/RUN_INSTRUCTIONS.md`.
