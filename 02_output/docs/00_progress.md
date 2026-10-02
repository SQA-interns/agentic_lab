# Progress

> Written in: every phase · Agent: writes

Updated at every gate and before any stop, so a fresh session can resume from here.

- Current phase: 4 (build), starting
- Last gate result: phase 3 passed at 2026-10-02T11:35:29Z. 111 tests frozen (79 backend acceptance, 29 frontend acceptance, 3 end-to-end) in `03_acceptance-manifest.sha256`; all fail for behavioural reasons; D-16 resolved (F-01). Phase 2 passed at 2026-10-02T10:50:51Z, phase 1 at 2026-10-02T10:44:49Z, phase 0 at 2026-10-02T10:43:27Z. Decisions D-01..D-16; D-02 and D-06..D-15 pending review (list them in the release notes).
- Next step: read `general/phases/4-build.md` and do what it says.
- Waiting for the human on: nothing
- Notes for later phases: a failure caused by the host Node version (D-02) is a blocking decision, never a workaround. Add pitest, Stryker, jscpd and CPD to `verify.sh` when runnable. `verify.sh <phase> e2e` needs the compose stack with project name `registration` (network `registration_default`, services `frontend` on 8080 and `mailpit` on 8025) and the example options file. Implementation notes from the frozen tests: `acceptedAt` must be the same instant in the answer, the row and the JSON copy; only the confirmation uses role `status`; only the failure view uses role `alert`; field errors are linked with `aria-describedby` and `aria-invalid`; the form uses its own validation (`noValidate`).
