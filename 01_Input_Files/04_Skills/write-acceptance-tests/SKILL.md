---
name: write-acceptance-tests
description: Write and freeze the black-box acceptance test suite, before implementation, for Classical SDD.
---

# Procedure

This phase happens **before** Implementation. Its whole purpose is
that these tests are written from what the system must do, not from
how it ends up being built — see `CONSTITUTION.md` §2.

## Sources

Derive every acceptance test from:

- `01_Business/` (User Stories, Business Rules, Form Schema);
- `docs/acceptance-criteria.md` (Phase 1 output);
- `docs/contracts/` (the API contract produced in Phase 2 — endpoints,
  request/response shapes, status codes).

Do not read production source code that doesn't exist yet — there
isn't any beyond the frozen scaffold. Do not guess at internal
structure; test through the API contract and the UI the Specification
describes, not through internals.

## Placement (so the freeze can be enforced automatically)

Put every acceptance test under a path segment literally named
`acceptance` in the backend and frontend test trees (e.g.
`.../test/.../acceptance/...`), and every end-to-end test under `e2e/`
as already structured by the scaffold. This convention is what the
`.claude` hook in `05_Scaffold/.claude/` recognises to protect these
files once frozen.

## What to write

For every Acceptance Criterion, at least one test that:

- exercises the real HTTP/API surface described in `docs/contracts/`
  (a running or in-process instance, not a unit-level mock of your own
  future code);
- asserts the observable outcome the AC describes — status code,
  response shape, a side effect verifiable from outside the
  implementation (e.g. a row appearing, a file appearing, a mail being
  captured);
- for a negative/edge-case AC, asserts the rejection and that nothing
  was persisted, exactly as the AC states.

## Confirm red for the right reason

Run the suite before writing a single line of production behaviour.
Every test must fail — but check *why*:

- **Acceptable red**: the endpoint doesn't exist yet (404), a table
  doesn't exist yet, an assertion fails because the feature isn't
  built. This is the expected, informative failure.
- **Not acceptable**: a compilation error, a missing test
  configuration, a wiring mistake in the test itself. Fix the test
  harness until every failure is the first kind, not the second.

Record this red run's result (pass/fail count and a one-line reason
per failure) in `docs/test-strategy.md` §1–§3 before moving to Phase 4.

## Freeze

Once the red run is confirmed clean (every failure behavioural, none
mechanical), compute a hash of every acceptance-test file and record
it in:

`<IMPLEMENTATION_ROOT>/docs/acceptance/MANIFEST.sha256`

(one line per file: `<sha256>  <path>`, the same format `sha256sum`
produces). Commit the acceptance tests and the manifest together. From
this commit onward, these files are frozen — see `CONSTITUTION.md` §2
for what to do if one later turns out to be wrong.

## Output

- The acceptance-test suite, in the placement described above.
- `<IMPLEMENTATION_ROOT>/docs/acceptance/MANIFEST.sha256`
- `<IMPLEMENTATION_ROOT>/docs/test-strategy.md` §1–§3 (levels and
  sources; the red-run evidence).
