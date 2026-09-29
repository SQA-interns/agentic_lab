---
name: write-acceptance-tests
description: Write and freeze the black-box acceptance tests from the acceptance criteria and contracts, before any production code exists (SDD workflow, phase 3).
---

> Owner: QA lead · Read in: phase 3 · Agent: read-only · Workflow: `sdd`

- Reads: `docs/01_acceptance-criteria.md`, `docs/02_contracts/`, `general/quality/test-strategy.md`, `../../rules.md`
- Writes: acceptance and end-to-end tests, `docs/03_acceptance-manifest.sha256`, `docs/03_test-strategy.md`
- Does not read: production source code

## Procedure

1. For every AC, write at least one test through the component's public interface that checks the observable outcome the AC states. For a rejection AC, also check that nothing changed.
2. Put the AC id in each test name.
3. Run the suite. Every test must fail because the behaviour is missing, not because of a build, configuration or harness error. Fix the harness until that holds.
4. Record the run (pass/fail counts and a one-line reason per group of failures) in `docs/03_test-strategy.md`.
5. Format and lint the tests now; after freezing they cannot change.
6. Write the manifest (normalised line endings) and commit it together with the tests.
