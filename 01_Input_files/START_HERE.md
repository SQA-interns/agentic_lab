# Single-agent specification-driven development

One development agent performs this run. Do not spawn or delegate to other
agents. Tools execute work; their normal use is allowed. This is the
single-agent baseline for later comparisons of complete system configurations.

The project root has four directories. `00_Documentation` is researcher-only:
do not read it. `01_Input_files` is immutable. Write application code and
design only to `02_Implementation`, measurements and evidence only to
`03_Metrics`. Those two output directories are writable by you.

At launch, read `METRICS.md` initialization instructions and record the clock
before project work. Then read `PRODUCT.md`, `TECH_STACK.md`,
`ARCHITECTURE.md`, `WORKFLOW.md`, and `DEFINITION_OF_DONE.md`. These are short
core contracts; understand completion requirements before designing the app.
Revisit relevant sections when needed rather than repeatedly dumping all files.

Product, stack, and accepted architecture define required intent. Generated
configuration/code must conform. Report conflicting fixed inputs; do not
silently pick a winner. Choose ordinary implementation details autonomously;
ask only when a missing decision materially blocks correctness. Record
material choices in the generated specification, not a separate decision log.

For this task, create separate `02_Implementation/backend` and `frontend`
applications and shared runtime files directly under `02_Implementation`.
There is no scaffold. Never copy a previous run's implementation or metrics.
