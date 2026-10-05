---
name: preflight
description: Check that everything the run needs is present and working, ask the human for what is missing, and bootstrap one skeleton per component and the verify script (phase 0).
---

> Owner: Team lead · Read in: phase 0 · Agent: read-only

First action: copy `03_statistics/run-log.template.json` to `run-log.json` and record the start, including the path of this session's transcript (`03_statistics/metrics.md`).

## Preflight

Check on the human's behalf that everything the run needs is present and working. Do not install, upgrade or substitute anything yourself.

1. `project/00_setup/run-config.md` is complete.
2. Every `platforms` and `tooling` entry in `project/stack.md`: run its `version_check` (if none, the tool's own version command); the output contains the listed version. Runs but reports another version: non-blocking decision. Does not resolve or run: failure.
3. Every environment and service in `project/stack.md` ("Environments") needed locally is running or reachable.
4. Every key in `project/secrets.env.example` not marked test-only or not needed has a value in `.env`: `bash 01_input/00_general/tools/secrets.sh check`. It prints present or missing per key and never a value. Never read `.env` any other way.
5. Every `dependencies` entry resolves at its exact version from its `source`.
6. Scan the listed dependency set with the `dependency-scan` tools, using each tool's `options`; a scanner that needs a key gets it through `secrets.sh run`. Classify each Critical or High result: a false positive or a real one, as `skills/decisions` says. An analyser that fails for missing credentials not listed in `project/secrets.env.example` is disabled with a non-blocking decision; the scan must complete with at least one analyser.
7. The working tree is clean (apart from an untracked `.env` and `03_statistics/run-log.json`) and on the intended starting commit.
8. Write `docs/00_input-manifest.sha256`: SHA-256 of every file under `01_input/` and every protected root file (`README.md` sections 1 and 2), LF-normalised, `sha256sum` format.
9. Before reporting a failure, run that check a second time in a fresh shell; report only failures both confirm. Write every result to the report with the method used, never a value.
10. Failures only a human can fix are blocking decisions, asked together in one message: what to install or start with the exact version, which key to add to `.env`.

## Bootstrap

1. One skeleton per component as described in `project/constraints.md` ("Components"), with manifests and lock files that match `project/stack.md` exactly, and the build, test, check and run commands (ES-05). A skeleton prints no generated credential (for example a framework's default user password) into any log, because test logs are committed.
2. `out/scripts/verify.sh`: runs the tools that exist so far, writes each tool's full output to `out/logs/<phase>_<tool>.log` and prints one line per tool (tool, exit code, key numbers, log path). It takes the phase, an optional list of tools, and a filter for a subset of tests. Extend it whenever a tool becomes runnable; do not run a check or scanner any other way.
   - The secret scanner covers the working tree and the history of the current branch only (SB-09).
   - Tools that need a running stack (end-to-end, runtime demonstration) are not in the default list.
   - On Windows with Git Bash, give Docker Windows-style paths (`pwd -W`) and set `MSYS_NO_PATHCONV=1` on Docker calls only, never globally.
