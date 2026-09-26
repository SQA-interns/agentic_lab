# Agent input kit

This is a compact, phase-loaded classic-SDD input
package. It contains requirements, frozen stack, deployment and
security constraints, experimental process, measurement rules, and scaffold.

 Requirements that must be reasoned about together
are consolidated, phase-local instructions are loaded only at their gate, and
`AGENTS.md` is the small always-on entry point.

## Layout

| File                    | Read when                  | Purpose                                             |
| ----------------------- | -------------------------- | --------------------------------------------------- |
| `AGENTS.md`             | Automatically / first      | Short execution contract and navigation             |
| `01_PRODUCT.md`         | Before acceptance criteria | User stories, rules, schema, scope                  |
| `02_ENGINEERING.md`     | Before specification       | Stack, runtime, security, deployment                |
| `03_WORKFLOW.md`        | Before starting            | Phases, outputs, Git and measurement rules          |
| `04_VERIFICATION.md`    | Test/verify phases         | Test layers, Definition of Done, evidence           |
| `run-log.template.json` | Start of run               | Experimental record template                        |
| `scaffold/`             | Implementation setup       | Frozen tooling bootstrap; copy, never edit in place |

## Portable workspace

```text
WORKSPACE_ROOT/
├── agentic-input-kit/       # this folder: immutable INPUT_ROOT
├── 02_Implementation/       # generated implementation
└── 03_Run-Statistics/       # generated measurements
```

Do not give the agent every file as prompt context. Start it at the workspace
root and let `AGENTS.md` direct progressively relevant reading.

## Source coverage map

| Original source area                                              | Consolidated location                      |
| ----------------------------------------------------------------- | ------------------------------------------ |
| Business rules, form schema, user stories                         | `01_PRODUCT.md`                            |
| Tech stack, technical, security and deployment constraints        | `02_ENGINEERING.md`                        |
| Run instructions, acceptance rules, six skill procedures, metrics | `03_WORKFLOW.md` + `run-log.template.json` |
| Definition of Done and verification skill                         | `04_VERIFICATION.md`                       |
| Tooling scaffold and pinned dependencies                          | `scaffold/` (unchanged)                    |
