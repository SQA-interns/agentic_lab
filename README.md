# Agentic development experiment archetype

Portable package:

```
<WORKSPACE_ROOT>/
├── 01_Input_Files/          INPUT_ROOT (immutable, includes 05_Scaffold)
├── 02_Implementation/       IMPLEMENTATION_ROOT (created/copied at run start)
└── 03_Run-Statistics/       STATISTICS_ROOT
```

At run start copy `01_Input_Files/05_Scaffold/` to `02_Implementation/`.

Path names are defined in `01_Input_Files/03_Process/RUN_INSTRUCTIONS.md`.
