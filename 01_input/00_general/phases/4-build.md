# Phase 4: Build

> Owner: Architect · Read in: phase 4 · Agent: read-only

- Read: `docs/02_specification.md`, `docs/02_contracts/`, `project/00_setup/tech-stack.md`, `project/00_setup/environments.md`, `project/02_design/architecture.md`, `general/standards.md` ("Engineering standards")
- Write: source code

## Do

1. Build story by story, in the priority order of `project/01_requirements/scope.md`. After each story: run its frozen tests and the check command, then commit.
2. Follow the specification and contracts. Where they turn out wrong, record a decision and correct the document in its own commit.
3. A frozen test that appears wrong is a blocking decision; continue with the other stories first.

## Do not

- Do not write unit tests.
- Do not edit a frozen test, its helpers or its configuration.
- Do not add a dependency that `tech-stack.md` does not list, except under its own rule.

## Gate

- All frozen acceptance and end-to-end tests pass.
- Format, lint and type checks are clean (`verify.sh`).
- At least one commit per user story, each naming its `US` or `AC` id. Check with `git log --oneline <freeze commit>..HEAD` and save the list to `out/logs/`.

## Commits

One per user story, or per coherent group of ACs of one story: the code plus only the wiring it needs.
