# Phase 5: Unit tests

> Owner: QA lead · Read in: phase 5 · Agent: read-only

- Read: source code, `general/standards.md` ("Tests"), `project/02_design/quality-requirements.md` ("Thresholds")
- Write: unit and integration tests, `docs/03_test-strategy.md`

## Do

1. Write unit and integration tests for the logic the acceptance tests do not reach: validation, security, persistence, business rules, error paths.
2. Run the full suite, all levels. If it does not compile, fix compile errors only, changing no behaviour and no assertion, and run again. Record that result as the first complete run (`firstTestRun`) before any other fix.
3. Classify each failure, then fix:

| Class | Action |
|---|---|
| Implementation defect | fix the code |
| Defect in a non-frozen test | fix the test and say so in `docs/03_test-strategy.md` |
| Frozen test appears wrong | blocking decision; do not touch the test |

4. Record the test levels as executed and the first run in `docs/03_test-strategy.md`.

## Gate

- The first complete run is recorded and classified before any fix.
- The full suite passes.

## Commits

One per component area or layer under test (for example domain, API, UI screen); one per fix.
