---
name: decisions
description: Record a decision in the decisions log, and for a blocking one ask the human and wait (any phase).
---

> Owner: Team lead · Read in: whenever a decision is needed · Agent: read-only

Which decisions block is in `rules.md` ("Decisions"). Every decision is recorded; none is made silently.

## Ask the human (blocking)

Record a decision in `docs/decisions-log.md`, ask, and wait. Ask all open questions of a phase in one message. While waiting, finish work that does not depend on the answer.

## Decide and record (non-blocking)

When a requirement allows two behaviours a user would notice, choose the more conservative one, record it as "pending review", and continue.

Also non-blocking, record and continue: a pinned tool that runs but reports another version; a scanner analyser disabled for lack of credentials; dev-only tooling added under `standards/engineering.md` ("Versions").

## Decision record

Append each decision to `docs/decisions-log.md` in this format:

```
## D-nn: <short title>
- Timestamp:
- Phase:
- Type: blocking | non-blocking
- Trigger: <what happened; cite IDs and files>
- Options: <numbered alternatives; mark the proposed default>
- Human response: <answer and time, or "none">
- Resolution: <chosen option> | pending review
```

- Never delete or rewrite a record; add a follow-up record instead.
- The phase 7 gate fails while any record lacks a resolution.
