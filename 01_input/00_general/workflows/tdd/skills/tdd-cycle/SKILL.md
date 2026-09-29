---
name: tdd-cycle
description: Build one acceptance criterion by red-green-refactor cycles, committing each step (TDD workflow, phase 4).
---

> Owner: QA lead · Read in: phase 4 · Agent: read-only · Workflow: `tdd`

- Reads: `docs/01_acceptance-criteria.md`, `docs/02_contracts/`, `general/quality/test-strategy.md`, `../../rules.md`
- Writes: tests, source code, `docs/03_acceptance-manifest.sha256`, red-run logs in `out/logs/`

## Procedure

Take the ACs in priority order (`project/02_business/scope.md`). For each AC:

1. **Red (outer):** write an acceptance test through the component's public interface, with the AC id in its name. Run it; it must fail because the behaviour is missing. Save the run to `out/logs/`, then commit `red: AC-…`.
2. **Inner cycles:** for each piece of logic the acceptance test needs, write a failing unit test, then the minimal code, then refactor; commit each step with the same AC id.
3. **Green (outer):** when the acceptance test passes and the full suite passes, add the acceptance test to the manifest and commit `green: AC-…`.
4. **Refactor:** clean up without changing any test; run the full suite; commit `refactor: AC-…`.

Do not start the next AC while any test fails.
