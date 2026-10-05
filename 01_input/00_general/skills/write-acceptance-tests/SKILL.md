---
name: write-acceptance-tests
description: Write and freeze the black-box acceptance and end-to-end tests from the acceptance criteria and contracts, before any production code exists (phase 3).
---

> Owner: QA lead · Read in: phase 3 · Agent: read-only

## Do

1. For every AC, write at least one test through the component's public interface that checks the observable outcome the AC states. For a rejection AC, also check that nothing changed.
2. Put the AC id in each test name.
3. Run the suite. Every test fails because the behaviour is missing, not because of a build, configuration or harness error. Fix the harness until that holds. A test that passes only on bootstrap code is listed in step 4.
   - The first assertion fails on bootstrap code, so the helpers behind it (database queries, mail and file readers, type conversions, failure injection, output parsers) have not run yet. Exercise each of them once with a temporary probe test against real data of the same kind, then delete the probe. A harness defect found after the freeze needs a human decision.
4. Record the run in `docs/03_test-strategy.md`: pass and fail counts, one line per group of failures.
5. Format and lint the tests now; after the freeze they cannot change.
6. Write `docs/03_acceptance-manifest.sha256` (format in `standards/testing.md`) and commit it alone as the freeze commit.

## Do not

- Do not read or write production source code.
- Do not write unit tests.
