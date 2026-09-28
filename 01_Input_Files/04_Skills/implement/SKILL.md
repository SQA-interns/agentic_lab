---
name: implement
description: Implement the approved specification.
---

# Procedure

Use the Specification as the primary technical source of truth. The
frozen acceptance-test suite from Phase 3
(`<IMPLEMENTATION_ROOT>/docs/acceptance/MANIFEST.sha256` and the tests
it lists) is your executable definition of "working" — you are
building until those tests pass, not writing tests to match what you
build.

Requirements:

- implement only justified functionality;
- follow the frozen technology stack;
- extend the copied tooling scaffold in `IMPLEMENTATION_ROOT`
  (copied from `05_Scaffold/` at run start);
- do not replace configured measurement tools;
- do not import a pre-written layered architecture from the scaffold;
- avoid unrelated refactoring;
- record added dependencies;
- record important implementation decisions;
- preserve requirement traceability;
- do not edit, delete, or weaken anything the acceptance-test manifest
  lists (`CONSTITUTION.md` §2) — if one of those tests seems wrong,
  stop and follow the test-defect-request procedure there instead of
  changing it yourself;
- if a pinned scaffold version does not resolve, or the primary tool
  named in `TECH_STACK.md` cannot be used as specified, stop and
  escalate per `CONSTITUTION.md` §3 rather than silently substituting
  a different version or tool.

You may use:

- build;
- compiler;
- formatter;
- linter;
- type checker;
- the frozen acceptance-test suite, run as often as you like, purely
  as a green/red signal — you may read its output, you may not edit
  it.

Do not create the unit-test suite in this phase; that is Phase 5.

## Output

Implement the solution under `IMPLEMENTATION_ROOT`.

Record the first executable registration happy path timestamp in:

`<STATISTICS_ROOT>/run-log.json`
