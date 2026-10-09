---
name: release
description: Write and check the READMEs from a clean checkout, write the release notes, close the run log with usage figures, write the run summary and end the run (phase 7).
---

> Owner: Team lead · Read in: phase 7 · Agent: read-only

## Do

1. Write the READMEs (ES-06): commands only, each one run and working.
2. Follow them from a clean checkout in a short path, the stack stopped first: run only the build, test, check and run commands the READMEs give (through `verify.sh`), not mutation testing or the scanners, which phase 6 already ran on the same sources. Save the log to `out/logs/`. Fix what fails. Do not copy `.env` into the checkout: start its stack with the original repository's `secrets.sh run … -- docker compose -f <checkout>/02_output/docker-compose.yml …`.
3. Write the release notes into their skeleton, section by section:
   - "Delivered": one row per story.
   - "Known limitations": behaviour the product does not have, from decisions and accepted findings.
   - "Decisions pending review": every decision whose resolution is "pending review", with its choice. They are already applied in the run; the human reviews them before production use.
   - "Before production": the "Must be tested manually" rows of `project/stack.md` ("External services"), and accepted findings whose fix lies outside this run. Nothing else; these are not part of the run.
4. Add the DoD-08 and DoD-09 evidence to `docs/06_verification-report.md`.
5. Close the run log: `end` and `finalCommit` (HEAD before the closing commit). Then fill `usage` (`metrics.md`, section 2): `node 01_input/00_general/tools/usage-from-transcript.mjs --write` (one `--transcript` per session if the run was resumed).
6. Write the run summary into its skeleton from `run-log.json` and `docs/03_test-strategy.md`, including the test measures table.
7. End the run with one message of at most ten lines: the outcome (tests, findings, decisions), and the only task left for the human: fill the usage-panel values in `03_statistics/usage.md`. Ask nothing else.

## Size

Each README at most about 80 lines; release notes at most about 80 lines.
