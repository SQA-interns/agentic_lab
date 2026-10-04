# Phase 7: Release

> Owner: Team lead · Read in: phase 7 · Agent: read-only

- Read: `project/02_design/architecture.md`, `project/00_setup/environments.md` ("External services"), `general/standards.md` (ES-05, ES-06, "Definition of Done"), `docs/decisions-log.md`, `03_statistics/metrics.md`
- Write: `out/README.md`, one README per component, `docs/release-notes.md`, the DoD-08 and DoD-09 rows of `docs/06_verification-report.md`, `03_statistics/run-summary.md`, `03_statistics/run-log.json`

## Do

1. Write the READMEs (ES-06): commands only, each one run and working.
2. Follow them from a clean checkout in a short path (through `verify.sh`, the stack stopped first); save the log to `out/logs/`. Fix what fails. Do not copy `.env` into the checkout; point `--env-file` at the original.
3. Write the release notes from their skeleton: what was delivered per story, known limitations, everything a human must test manually, every non-blocking decision and suppressed false positive for review.
4. Add the DoD-08 and DoD-09 evidence to `docs/06_verification-report.md`.
5. Close the run log: `end` and `finalCommit` (HEAD before the closing commit). Then fill `usage` (`metrics.md`, section 2): `node 01_input/00_general/tools/usage-from-transcript.mjs --write`. It counts the transcript up to `end` and prices it with `03_statistics/usage.md`.
6. Write the run summary from `run-log.json`, including the usage figures and the coverage and mutation measures.

## Size

Each README at most about 80 lines; release notes at most about 80 lines.

## Gate

- Every README works when followed from a clean checkout.
- The manual-test list is present.
- DoD-08 and DoD-09 have evidence.
- Every decision has a resolution or is listed as pending review.
- `usage` in `run-log.json` is filled (the cost may stay `null` only when `usage.md` has no price row for the model).

## Commits

One per README; one for the release notes; one for the verification report rows; one for the run summary and run log.
