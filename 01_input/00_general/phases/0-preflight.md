# Phase 0: Preflight and bootstrap

> Owner: Team lead · Read in: phase 0 · Agent: read-only

- Read: `project/00_setup/*`, `project/02_design/architecture.md` ("Components"), `general/standards.md` ("Engineering standards", "Security baseline", "Severity scale")
- Write: `docs/00_preflight-report.md`, `docs/00_input-manifest.sha256`, `docs/00_progress.md`, one skeleton per component, repository files (ES-03), `out/scripts/verify.sh`

First action: copy `03_statistics/run-log.template.json` to `run-log.json` and record the start, including the path of this session's transcript (`03_statistics/metrics.md`).

## Preflight

Check on the human's behalf that everything the run needs is present and working. Do not install, upgrade or substitute anything yourself.

1. `run-config.md` is complete.
2. Every `platforms` and `tooling` entry in `tech-stack.md`: run its `version_check` (if none, the tool's own version command); the output contains the listed version. Runs but reports another version: non-blocking decision. Does not resolve or run: failure.
3. Every environment and service in `environments.md` needed locally is running or reachable.
4. Every key in `secrets.env.example` not marked test-only or not needed has a non-empty value in `.env` (repository root). Check with `sed -n 's/^KEY=//p' .env | tr -d ' \r"'"'"'' | grep -q .`, one key per command exactly as written, never with a read loop and never by displaying the value. The second way (step 9) lists key names only: `sed -n 's/=.*//p' .env`. If the harness refuses either command, follow "Blocking decisions" in `rules.md`.
5. Every `dependencies` entry resolves at its exact version from its `source`.
6. Scan the listed dependency set with the `dependency-scan` tools, using each tool's `options`. Classify each Critical or High result: a false positive follows "Non-blocking decisions" in `rules.md`; a real one is a blocking decision with the proposed alternatives. An analyser that fails for missing credentials not listed in `secrets.env.example` is disabled with a non-blocking decision; the scan must complete with at least one analyser.
7. The working tree is clean (apart from an untracked `.env` and `03_statistics/run-log.json`) and on the intended starting commit.
8. Write `docs/00_input-manifest.sha256`: SHA-256 of every file under `01_input/` and every protected root file (`README.md` sections 1 and 2), LF-normalised, `sha256sum` format.
9. Before reporting a failure, re-run that check a second, different way; report only failures both confirm. Write every result to the report with the method used, never a value.
10. Failures only a human can fix: follow "Blocking decisions" in `rules.md`, all in one message. Name what to install or start with the exact version, and which key to add to `.env`.

## Bootstrap

1. One skeleton per component as described in `architecture.md`, with manifests and lock files that match `tech-stack.md` exactly, and the build, test, check and run commands (ES-05). A skeleton prints no generated credential (for example a framework's default user password) into any log; configure it so it does not, because test logs are committed.
2. `out/scripts/verify.sh`: runs the tools that exist so far, writes each tool's full output to `out/logs/<phase>_<tool>.log` and prints one line per tool (tool, exit code, key numbers, log path). It takes the phase and an optional list of tools, and a filter for a subset of tests. Extend it whenever a tool becomes runnable; do not run a check or scanner any other way.
   - The secret scanner covers the working tree and the history of the current branch only (SB-09).
   - Tools that need a running stack (end-to-end, runtime demonstration) are not in the default list.
   - On Windows with Git Bash, give Docker Windows-style paths (`pwd -W`) and set `MSYS_NO_PATHCONV=1` on Docker calls only, never globally.

## Gate

- Every preflight check passes.
- Every component builds; manifests and lock files match `tech-stack.md` exactly (as amended by approved decisions).
- Every listed tool runs through `verify.sh`.
- Input manifest written.

## Commits

One per component skeleton; one for the repository files (ES-03); one for `verify.sh`; one for the preflight and manifest documents.
