# Rules

> Owner: Team lead · Read in: every phase · Agent: read-only

## Phase map

Run the phases in order. A phase starts only after the previous gate has passed. Each card lists what to read, what to write, the gate and the commit unit.

| # | Phase | Card | Main output |
|---|---|---|---|
| 0 | Preflight and bootstrap | `phases/0-preflight.md` | preflight report, input manifest, component skeletons |
| 1 | Requirements | `phases/1-requirements.md` | acceptance criteria |
| 2 | Design | `phases/2-design.md` | specification, contracts |
| 3 | Test design | `phases/3-test-design.md` | frozen acceptance and end-to-end tests |
| 4 | Build | `phases/4-build.md` | source code |
| 5 | Unit tests | `phases/5-unit-tests.md` | unit and integration tests |
| 6 | Verify | `phases/6-verify.md` | verification report |
| 7 | Release | `phases/7-release.md` | READMEs, release notes, run summary |

On a failed gate: fix within the same phase and re-check. If the gate needs a change to an input or a frozen test, raise a blocking decision.

Any phase may write `docs/decisions-log.md`, `docs/00_progress.md` and `out/logs/`.

## Blocking decisions

Blocking, and only these:

- something checked in phase 0 is missing or wrong and only a human can fix it;
- the harness refuses a command that a card prescribes (a permission denial): do not retry it in another form; ask for the permission;
- a technology, version or service from `project/00_setup/tech-stack.md` does not work or needs changing;
- a frozen acceptance test appears wrong (only a human may approve the manifest change);
- a real Critical or High finding would be downgraded or accepted.

Procedure:

1. First finish every piece of work in the phase that does not depend on the answer.
2. Write one full record per question (format below), each with numbered options and a proposed default.
3. Update `docs/00_progress.md`, log the intervention start, commit.
4. Ask all open questions in one message and end the turn.

When the human answers, in this or a new session: read `docs/00_progress.md`, record the answer and its time, re-run only what the answer affects, continue.

## Non-blocking decisions

Decide, record one table row, continue. The human reviews the rows in the release notes.

- A requirement allows two behaviours a user would notice: choose the more conservative one.
- A pinned tool runs but reports another version; a scanner analyser is disabled for lack of credentials; dev-only tooling is added under the `tech-stack.md` rule.
- Scanner false positive: the finding names a component that is not the shipped artifact (identifier mismatch), or the fixed version is already shipped. Record the tool, the finding id, the matched and the actual artifact with versions, and the evidence; add it to the tool's suppression file with the decision id; keep the raw report. This is never used for a real vulnerability in a shipped dependency, reachable or not: that is blocking.

## Decision log format

`docs/decisions-log.md` has two parts. Never delete or rewrite an entry; add a follow-up.

Non-blocking, one row each:

```
| D-nn | Timestamp | Phase | Trigger (IDs, files) | Choice | pending review |
```

Blocking, one record each:

```
## D-nn: <short title>
- Timestamp:
- Phase:
- Trigger: <what happened; cite IDs and files>
- Options: <numbered alternatives; mark the proposed default>
- Human response: <answer and time, or "none">
- Resolution: <chosen option>
```

## Identifiers

| Prefix | Meaning | Defined in |
|---|---|---|
| `US-nnn` / `AC-nnn-nn` | User story / acceptance criterion | `project/01_requirements/user-stories.md` / `docs/01_acceptance-criteria.md` |
| `BR-nn` / `OQ-nn` | Business rule / open question | `project/01_requirements/` |
| `AR-nn` | Architecture constraint | `project/02_design/architecture.md` |
| `SR-nn` / `NFR-nn` / `DoD-Pnn` | Project security / non-functional requirement / done criterion | `project/02_design/` |
| `ES-nn` / `SB-nn` / `DoD-nn` | Engineering standard / security baseline / done criterion | `general/standards.md` |
| `F-nn` / `D-nn` | Finding / decision | `docs/06_verification-report.md` / `docs/decisions-log.md` |

Tests, commits and findings reference these IDs.

## Commits

Commit as a human developer would: small, finished steps, never half a project.

- One commit = one logical change that can be reverted alone. Commit when it is done and its checks pass; do not batch.
- At most about 15 files or 400 changed lines, not counting logs, lock files and generated files; otherwise split, or state in the message why it cannot be. Never a whole phase or component. Check before every commit with `git diff --cached --numstat`; tests of one area can be split by class or file.
- Keep code, tests, documents, dependency or build changes, and each fix (`F-nn`) in separate commits.
- The build passes at every commit and no previously passing test fails. Frozen tests of behaviour not yet built may fail.
- Message: `<type>: <summary> (IDs)`; type `feat`, `test`, `fix`, `refactor`, `docs`, `build` or `chore`; imperative, at most 72 characters.
- The commit unit of each phase is on its card. Working tree clean at every gate.
- Do not squash, rebase or rewrite history.

## Time

Every timestamp you write anywhere is the output of `date -u +%Y-%m-%dT%H:%M:%SZ`, run at the moment of the event. Never write a time from memory, and never compute a duration by estimate.

## Output size

- Send command output longer than 50 lines to `out/logs/`; read back only the summary lines you need.
- Run checks and scanners through `out/scripts/verify.sh` (created in phase 0): it writes each tool's full output to `out/logs/` and prints one line per tool.
- Documents are tables and lists that cite IDs. Do not restate an input; do not narrate. Each card gives a size limit.
- Do not print or re-read a file you have just written.

## Files

- Create and change files with the file tools (write, edit). Do not produce source, test, configuration or document files with shell here-documents, `sed` or generated scripts: quoting and escape sequences break silently there, and every such failure costs a build.
- A small one-off script that edits structured data (for example JSON) is fine; write the script itself with the file tool.

## Secrets (`.env`)

You never need to see a secret value; the tools that run need it, not you.

- Do not read `.env` with a file tool, and do not print, copy or log its values: no `cat`, `echo`, `env`, `printenv` or `docker compose config` on it, in commands, files, logs, commit messages or the conversation.
- Pass values to tools without showing them: `docker compose --env-file .env`, or `export NAME="$(sed -n 's/^NAME=//p' .env | tr -d '\r')"` inside the command that needs it.
- Check presence and emptiness only, as in the phase 0 card.
- Before saving output from a command that may print an environment, filter it so no value is written.

## Progress

Update `docs/00_progress.md` at every gate and before any stop, so a fresh session can resume from it alone.

## Statistics

At the start and end of each phase, and for every fix loop, human intervention and decision, update `03_statistics/run-log.json` as defined in `03_statistics/metrics.md`, and write `03_statistics/run-summary.md` in phase 7. Do nothing else in `03_statistics/`.

- Take every timestamp at the moment of the event, including the moment a scanner or test reports a finding (it starts a fix loop) and the moment a question to the human is sent.
- Section 2 of `metrics.md` (`usage`) is filled at the end of phase 7 with `general/tools/usage-from-transcript.mjs`, not by estimate; see the phase 7 card.
