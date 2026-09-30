# Phase rules

> Owner: Team lead · Read in: every phase · Agent: read-only

Adds to `general/working-rules.md`; does not replace it.

## Test levels

| Level | Written in phase | Written from | Frozen | Location |
|---|---|---|---|---|
| Acceptance | 3 | acceptance criteria + contracts | yes, from the freeze commit | path contains `acceptance` |
| End-to-end | 3 | acceptance criteria | yes, from the freeze commit | path contains `e2e` |
| Unit / integration | 5 | implementation | no | any other test path |

## Commit units

| Phase | One commit per |
|---|---|
| 0 | component skeleton; repository files (ES-03); preflight and manifest documents |
| 1, 2 | document (acceptance criteria; specification; each contract) |
| 3 | user story's acceptance and end-to-end tests, then the freeze commit (manifest only) |
| 4 | user story (or coherent group of AC of one story): code, plus only the wiring it needs |
| 5 | component area or layer under test (for example domain, API, UI screen) |
| 6 | fix of one finding, with its re-run evidence; the report in its own commit |
| 7 | README, release notes |

## Constraints

- Do not read or write production source code in phase 3.
- Do not write unit tests before phase 5.
- Do not edit, delete or weaken a test listed in `docs/03_acceptance-manifest.sha256`.

## Evidence (checked in phase 6)

- The phase 3 commits add no production code beyond the bootstrap skeleton; the freeze commit adds the manifest, and every file it lists was committed earlier in phase 3.
- Build has at least one commit per user story, each naming its `US`/`AC` ID; no commit exceeds the size guide in `working-rules.md` without a stated reason.
- Every hash in the manifest still matches.
