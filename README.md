# Agentic development experiment archetype

This workspace is a portable experiment package:

```
<WORKSPACE_ROOT>/
├── 01_Input_Files/          INPUT_ROOT   (immutable experiment inputs)
├── 02_Implementation/       IMPLEMENTATION_ROOT  (tooling scaffold)
└── 03_Run-Statistics/       STATISTICS_ROOT      (run measurements)
```

Copy these three directories into any project. Do not hard-code a
repository name or an absolute path. Path names are defined in:

`01_Input_Files/03_Process/RUN_INSTRUCTIONS.md`
