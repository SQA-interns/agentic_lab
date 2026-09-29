# Workflow rules: SDD

> Owner: Team lead · Read in: every phase · Agent: read-only · Workflow: `sdd`

Adds to `general/working-rules.md`; does not replace it.

## Test levels

| Level | Written in phase | Written from | Frozen | Location |
|---|---|---|---|---|
| Acceptance | 3 | acceptance criteria + contracts | yes, from the phase 3 commit | path contains `acceptance` |
| End-to-end | 3 | acceptance criteria | yes, from the phase 3 commit | path contains `e2e` |
| Unit / integration | 5 | implementation | no | any other test path |

## Constraints

- Do not read or write production source code in phase 3.
- Do not write unit tests before phase 5.
- Do not edit, delete or weaken a test listed in `docs/03_acceptance-manifest.sha256`.

## Evidence (checked in phase 6)

- The phase 3 commit contains the manifest and every file it lists, and no production code beyond the bootstrap skeleton.
- Every hash in the manifest still matches.
