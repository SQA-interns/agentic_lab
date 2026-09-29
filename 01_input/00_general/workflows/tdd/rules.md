# Workflow rules: TDD

> Owner: Team lead · Read in: every phase · Agent: read-only · Workflow: `tdd`

Adds to `general/working-rules.md`; does not replace it.

## Test levels

| Level | Written | Driven by | Frozen | Location |
|---|---|---|---|---|
| Acceptance | red step of each AC's first cycle, before its code | acceptance criterion + contracts | yes, once green | path contains `acceptance` |
| End-to-end | red step of the AC it covers | acceptance criterion | yes, once green | path contains `e2e` |
| Unit / integration | red step of each inner cycle | the next behaviour to build | no | any other test path |

## Cycle commits

| Step | Commit message starts with | Must be true at the commit |
|---|---|---|
| Red | `red: AC-nnn-nn` | the new test fails for a behavioural reason; all other tests pass |
| Green | `green: AC-nnn-nn` | all tests pass; only code needed for the new test was added |
| Refactor (optional) | `refactor: AC-nnn-nn` | all tests pass; no test changed |

## Constraints

- Do not write production code without a failing test that requires it.
- Do not change a test in a green or refactor commit.
- Do not edit, delete or weaken a test listed in `docs/03_acceptance-manifest.sha256`; add each acceptance and end-to-end test to it in that AC's green commit.

## Evidence (checked in phase 6)

- For every AC, a `red:` commit precedes its `green:` commit, and the red test run is logged in `out/logs/`.
- Every hash in the manifest still matches.
