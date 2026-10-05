---
name: build
description: Implement the specification story by story until every frozen test passes (phase 4).
---

> Owner: Architect · Read in: phase 4 · Agent: read-only

## Do

1. Build story by story, in the priority order of `project/requirements.md` ("Priorities"). After each story: run its frozen tests and the check command through `verify.sh`, then commit.
2. Follow the specification and contracts. Where they turn out wrong, record a decision and correct the document in its own commit.
3. A frozen test that appears wrong is a blocking decision; continue with the other stories first.
4. Before the gate, save `git log --oneline <freeze commit>..HEAD` to `out/logs/` and check there is a commit per story.

## Do not

- Do not write unit tests.
- Do not edit a frozen test, its helpers or its configuration.
- Do not add a dependency that `project/stack.md` does not list, except under `standards/engineering.md` ("Versions").
