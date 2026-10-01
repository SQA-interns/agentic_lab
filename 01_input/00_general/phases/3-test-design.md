# Phase 3: Test design

> Owner: QA lead · Read in: phase 3 · Agent: read-only

- Read: `docs/01_acceptance-criteria.md`, `docs/02_contracts/`, `general/standards.md` ("Tests"), `project/02_design/known-pitfalls.md`
- Write: acceptance and end-to-end tests, `docs/03_test-strategy.md`, `docs/03_acceptance-manifest.sha256`

Write and freeze the black-box tests before any production code exists.

## Do

1. For every AC, write at least one test through the component's public interface that checks the observable outcome the AC states. For a rejection AC, also check that nothing changed.
2. Put the AC id in each test name.
3. Run the suite. Every test fails because the behaviour is missing, not because of a build, configuration or harness error. Fix the harness until that holds. A test that passes only on bootstrap code is listed in step 4.
4. Record the run in `docs/03_test-strategy.md`: pass and fail counts, one line per group of failures.
5. Format and lint the tests now; after the freeze they cannot change.
6. Check each test once more against its AC and contract (types, formats, status codes): a wrong frozen test costs a blocking decision later.
7. Write `docs/03_acceptance-manifest.sha256`: `sha256sum` format, paths relative to `02_output/`, hashed after LF normalisation.

## Do not

- Do not read or write production source code.
- Do not write unit tests.

## Gate

- Every AC has a test.
- Every test fails for a behavioural reason, or is listed as passing on bootstrap code.
- The manifest is committed last, alone.

## Commits

One per user story's acceptance and end-to-end tests; then the freeze commit (manifest only).
