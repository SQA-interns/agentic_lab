# Rules

> Owner: Team lead · Fill: once per organisation · Read in: every phase · Agent: read-only

Rules that hold in every phase. Procedures are in `skills/`; lookup tables in `standards/`.

## Decisions

Every decision is recorded and handled with `skills/decisions`. Ask the human and wait (blocking) when:

- anything checked in phase 0 is missing or wrong and only a human can fix it;
- a technology, version or service from `project/stack.md` does not work or needs changing;
- a frozen acceptance test appears wrong (only a human may update the manifest afterwards);
- a Critical or High finding would be downgraded or accepted.

Everything else is non-blocking: decide, record, continue.

## Identifiers

| Prefix | Meaning | Defined in |
|---|---|---|
| `US-nnn` / `BR-nn` / `OQ-nn` | User story / business rule / open question | `project/requirements.md` |
| `AC-nnn-nn` | Acceptance criterion | `docs/01_acceptance-criteria.md` |
| `AR-nn` / `SR-nn` / `NFR-nn` / `DoD-Pnn` | Architecture constraint / security requirement / non-functional requirement / project done criterion | `project/constraints.md` |
| `ES-nn` / `SB-nn` / `DoD-nn` | Engineering standard / security baseline / done criterion | `standards/engineering.md` / `standards/security.md` / `standards/done.md` |
| `F-nn` / `D-nn` | Finding / decision | `docs/06_verification-report.md` / `docs/decisions-log.md` |

Tests, commits and findings reference these IDs.

## Commits

Commit as a human developer would: small, finished steps, never half a project.

- One commit = one logical change that can be reverted alone. Commit when it is done and its checks pass; do not batch.
- At most about 15 files or 400 changed lines, not counting logs, lock files and generated files; otherwise split, or state in the message why it cannot be. Check before every commit with `git diff --cached --numstat`.
- Keep code, tests, documents, dependency or build changes, and each fix (`F-nn`) in separate commits.
- The build passes at every commit and no previously passing test fails. Frozen tests of behaviour not yet built may fail.
- Message: `<type>: <summary> (IDs)`; type `feat`, `test`, `fix`, `refactor`, `docs`, `build` or `chore`; imperative, at most 72 characters.
- The commit unit of each phase is in `phases.md`. Working tree clean at every gate.
- Do not squash, rebase or rewrite history.

## Time

Every timestamp you write is the output of `date -u +%Y-%m-%dT%H:%M:%SZ`, run at the moment of the event: phase boundaries, a finding that starts a fix loop, a question sent to the human. Never write a time from memory or compute a duration by estimate.

## Output size

- Send command output longer than 50 lines to `out/logs/`; read back only the summary lines you need.
- Run checks and scanners only through `out/scripts/verify.sh` (created in phase 0); it prints one line per tool.
- Documents are tables and lists that cite IDs. Do not restate an input; do not narrate. Skills give size limits.
- Do not print or re-read a file you have just written.

## Files

- Create and change files with the file tools. Do not produce source, test, configuration or document files with shell here-documents, `sed` or generated scripts: quoting breaks silently and every failure costs a build.
- A small one-off script that edits structured data (for example JSON) is fine; write the script itself with the file tool.

## Secrets (`.env`)

You never need to see a secret value; the tools that run need it. Only `general/tools/secrets.sh` reads `.env`, and the harness denies everything else.

- Never name `.env` in a command or file tool, and never print, copy or log a secret value anywhere.
- Check the keys: `bash 01_input/00_general/tools/secrets.sh check`.
- Give a tool the values it needs: `bash 01_input/00_general/tools/secrets.sh run KEY[,KEY...] -- <command>`. Allowed commands: `docker compose …`, `docker run …` (with `-e KEY`), `./mvnw …`, `npx playwright …`. `verify.sh` calls it the same way.
- A refused command is never worked around in another form; ask the human.
- Before saving output from a command that may print an environment, filter it so no value is written.

## Progress

Update `docs/00_progress.md` at every gate and before any stop, so a fresh session can resume from it alone.

## Statistics

At the start and end of each phase, and for every fix loop, human intervention and decision, update `03_statistics/run-log.json` as `03_statistics/metrics.md` defines. Phase 7 fills `usage` with `general/tools/usage-from-transcript.mjs` and writes `03_statistics/run-summary.md`. Do nothing else in `03_statistics/`.
