# Decision record format

> Owner: Team lead · Read in: every phase · Agent: read-only

Append each decision to `02_output/docs/decisions-log.md` in this format:

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
- A phase 7 gate fails while any record lacks a resolution.
