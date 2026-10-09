---
name: unit-tests
description: Write unit and integration tests, record the first complete test run before any fix, classify failures, and measure coverage and mutation score (phase 5).
---

> Owner: QA lead · Read in: phase 5 · Agent: read-only

## Do

1. Write unit and integration tests for the logic the acceptance tests do not reach: validation, security, persistence, business rules, error paths.
2. Run the full suite, all levels. If it does not compile, fix compile errors only, changing no behaviour and no assertion, and run again. Record that result as the first complete run (`firstTestRun`) before any other fix.
3. Classify each failure, then fix:

| Class | Action |
|---|---|
| Implementation defect | fix the code |
| Defect in a non-frozen test | fix the test and say so in `docs/03_test-strategy.md` |
| Frozen test appears wrong | blocking decision; do not touch the test |

4. Once the suite passes, run coverage and mutation testing through `verify.sh`. Classify every surviving mutant in validation, security, persistence and business-rule code: add a test where one should have caught it, otherwise state why not (equivalent, logging only, not observable).
5. Fill `docs/03_test-strategy.md`: test levels as executed, the first run, and the measures table of `standards/testing.md`.
