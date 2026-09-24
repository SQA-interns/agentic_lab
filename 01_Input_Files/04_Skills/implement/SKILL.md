---
name: implement
description: Implement the approved specification.
---

# Procedure

Use the Specification as the primary technical source of truth.

Requirements:

- implement only justified functionality;
- follow the frozen technology stack;
- extend the frozen tooling scaffold in `IMPLEMENTATION_ROOT`;
- do not replace configured measurement tools;
- do not import a pre-written layered architecture from the scaffold;
- avoid unrelated refactoring;
- record added dependencies;
- record important implementation decisions;
- preserve requirement traceability.

You may use:

- build;
- compiler;
- formatter;
- linter;
- type checker.

Do not create the feature test suite in this phase.

## Output

Implement the solution under `IMPLEMENTATION_ROOT`.

Record the first executable registration happy path timestamp in:

`<STATISTICS_ROOT>/run-log.json`
