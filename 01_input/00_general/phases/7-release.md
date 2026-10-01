# Phase 7: Release

> Owner: Team lead · Read in: phase 7 · Agent: read-only

- Read: `project/02_design/architecture.md`, `project/00_setup/environments.md` ("External services"), `general/standards.md` (ES-05, ES-06, "Definition of Done"), `docs/decisions-log.md`
- Write: `out/README.md`, one README per component, `docs/release-notes.md`, `03_statistics/run-summary.md`

## Do

1. Write the READMEs (ES-06): commands only, each one run and working.
2. Follow them from a clean checkout in a short path; save the log to `out/logs/`. Fix what fails.
3. Write the release notes from their skeleton: what was delivered per story, known limitations, everything a human must test manually, every non-blocking decision and suppressed false positive for review.
4. Write the run summary from `run-log.json` and close the run log (`finalCommit`, `end`).

## Size

Each README at most about 80 lines; release notes at most about 80 lines.

## Gate

- Every README works when followed from a clean checkout.
- The manual-test list is present.
- Every decision has a resolution or is listed as pending review.

## Commits

One per README; one for the release notes; one for the run summary and run log.
