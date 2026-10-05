---
name: decisions
description: Record a decision in the decisions log; ask the human about a blocking one and end the turn, or apply the proposed default in an unattended run (any phase).
---

> Owner: Team lead · Read in: whenever a decision is needed · Agent: read-only

Which decisions block is in `rules.md` ("Decisions"). Every decision is recorded; none is made silently.

## Non-blocking

Decide, record one row, continue. Common cases:

- A requirement allows two behaviours a user would notice: choose the more conservative one.
- A pinned tool runs but reports another version; a scanner analyser is disabled for lack of credentials; dev-only tooling is added under `standards/engineering.md` ("Versions").
- Scanner false positive: the finding names a component that is not the shipped artifact (identifier mismatch), or the fixed version is already shipped. Record the tool, the finding id, the matched and the actual artifact with versions, and the evidence; add it to the tool's suppression file with the decision id; keep the raw report. Never for a real vulnerability in a shipped dependency, reachable or not: that is blocking.

## Blocking, attended run

1. First finish every piece of work in the phase that does not depend on the answer.
2. Write one full record per question, each with numbered options and a proposed default.
3. Update `docs/00_progress.md`, log the intervention start in the run log, commit.
4. Ask all open questions in one message and end the turn. Do not wait, poll or sleep.

When the human answers, in this or a new session: read `docs/00_progress.md`, record the answer and its time, re-run only what the answer affects, continue. An answer that is only pasted text needs one line typed by the human confirming it.

## Blocking, unattended run

When `project/00_setup/run-config.md` says `Unattended: yes`, nobody answers during the run:

1. Write the full record with numbered options. Choose as default the most conservative option the agent may apply on its own (step 3).
2. Apply it at once and record `Human response: none (unattended run)` and `Resolution: option N, applied without a human answer at <time>`. In `run-log.json` the decision is `blocking`, its `end` is that time; no human intervention is logged.
3. A default never installs or upgrades software on the host, changes an entry of `project/stack.md` or `project/secrets.env.example`, edits `01_input/`, a frozen test or a manifest, shows a secret, retries a refused command in another form, or lowers a Critical or High finding. When every real option needs one of these: leave the item open, record a finding at the severity found, and continue with all work that does not depend on it.
4. A gate that fails only because of such an open item counts as passed with an exception: name it in `docs/00_progress.md` and in the gate commit message.

Unattended runs need every command the skills prescribe allowed beforehand (README, "Running").

## Log format

`docs/decisions-log.md` has two parts. Never delete or rewrite an entry; add a follow-up.

Non-blocking, one row each; the choice is applied when it is recorded:

```
| D-nn | Timestamp | Phase | Trigger (IDs, files) | Choice |
```

Blocking, one record each:

```
## D-nn: <short title>
- Timestamp:
- Phase:
- Trigger: <what happened; cite IDs and files>
- Options: <numbered alternatives; mark the proposed default>
- Human response: <answer and time, or "none (unattended run)">
- Resolution: <chosen option>
```
