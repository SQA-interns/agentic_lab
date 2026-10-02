# Progress

> Written in: every phase · Agent: writes

Updated at every gate and before any stop, so a fresh session can resume from here.

- Current phase: 3 (test design), stopped on a blocking decision before the freeze
- Last gate result: phase 3 gate not passed yet. Done: 79 backend acceptance, 29 frontend acceptance and 3 end-to-end tests written, formatted, linted and committed per user story; all 111 fail for behavioural reasons (`03_test-strategy.md`); every AC has a test. Open: Semgrep finding on HTTP Basic (D-16); `03_acceptance-manifest.sha256` not written. Phase 2 passed at 2026-10-02T10:50:51Z, phase 1 at 2026-10-02T10:44:49Z, phase 0 at 2026-10-02T10:43:27Z. Decisions D-01..D-16.
- Next step: after the answer to D-16: option 1: add the suppression (`# nosemgrep` with D-16) to `openapi.yaml`, re-run `verify.sh 3 semgrep contracts`; option 2 or 3: change `openapi.yaml`, specification section 6, `Api.getAsOrganizer`, `Us008ExportAcceptanceTest`, `Us005...ac_005_05` and `frontend/e2e/` accordingly and re-run the three suites. Then write `docs/03_acceptance-manifest.sha256` (files under `02_output/` whose path contains `acceptance` or `e2e`, LF-normalised) and commit it alone (freeze), record the phase 3 end, start phase 4 (`general/phases/4-build.md`).
- Waiting for the human on: D-16
- Notes for later phases: a failure caused by the host Node version (D-02) is a blocking decision, never a workaround. Add pitest, Stryker, jscpd and CPD to `verify.sh` when runnable. `verify.sh <phase> e2e` needs the compose stack with project name `registration` (network `registration_default`, services `frontend` on 8080 and `mailpit` on 8025) and the example options file. Implementation notes from the frozen tests: `acceptedAt` must be the same instant in the answer, the row and the JSON copy; only the confirmation uses role `status`; only the failure view uses role `alert`; field errors are linked with `aria-describedby` and `aria-invalid`; the form uses its own validation (`noValidate`).
