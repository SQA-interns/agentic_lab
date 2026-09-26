---
name: write-tests
description: Create the test suite after implementation for Classical SDD.
---

# Procedure

Tests are created only after Implementation is complete.

Use:

- User Stories and AC for behavioural/acceptance tests;
- Specification and AC for API and integration tests;
- Specification and implementation for unit tests.

Include appropriate:

- unit tests;
- component tests;
- API tests;
- integration tests;
- acceptance tests;
- E2E tests;
- negative/edge-case tests;
- relevant security tests.

ArchUnit tests, if used, must verify the architecture declared in the
Specification. Do not introduce generic unused layering rules.

Record the first complete test run before fixing failures in
`<STATISTICS_ROOT>/run-log.json` as `firstTestRun`.

Never weaken a correct test merely to make incorrect implementation pass.

## Output

Create the test suite under `IMPLEMENTATION_ROOT` and create exactly:

`<IMPLEMENTATION_ROOT>/docs/test-strategy.md`
