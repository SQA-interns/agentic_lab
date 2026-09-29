# Phases: TDD (test-driven, red-green-refactor)

> Owner: Team lead · Read in: every phase · Agent: read-only · Workflow: `tdd`

Run the phases in order. A phase starts only after the previous gate has passed.
Phase numbers match the `sdd` workflow so outputs and run logs compare; phases 3 and 5 do not exist here, as their work happens inside phase 4.
Aliases: see `01_input/README.md`; `wf/` = `general/workflows/tdd/`.

| # | Phase | Reads | Writes | Gate (must be true to continue) |
|---|---|---|---|---|
| 0 | Preflight & bootstrap | `project/01_setup/*`, `project/03_technical/*`, `general/engineering-standards.md`, skill `general/skills/preflight` | `docs/00_preflight-report.md`, `docs/00_input-manifest.sha256`, `docs/00_progress.md`, one skeleton per component, repo files (ES-03) | Every preflight check passes; every component builds; manifests and lock files match `tech-stack.md` exactly; every listed tool runs; input manifest written |
| 1 | Requirements | `project/02_business/*` | `docs/01_acceptance-criteria.md` | Every story has ≥ 1 AC; every AC traces to a story; every open question is answered or has a decision record |
| 2 | Design | `docs/01_*`, `project/03_technical/*`, `project/04_security/*`, `project/05_quality/*`, `general/security/*` | `docs/02_specification.md` (components, contracts, controls only), `docs/02_contracts/` | Every AC is assigned to a component; every SR, SB and NFR maps to a control or target; contracts validate with a parser |
| 4 | Build by cycles | `docs/01_*`, `docs/02_*`, `general/quality/test-strategy.md`, `general/engineering-standards.md`, skill `wf/skills/tdd-cycle` | tests, source code, `docs/03_test-strategy.md`, `docs/03_acceptance-manifest.sha256` | Every AC has ≥ 1 passing test; every AC's red commit precedes its green commit; full suite passes; format, lint and type checks are clean |
| 6 | Verify | all of `01_input/`, all of `docs/`, skills `general/skills/security-review`, `general/skills/verify-release` | `docs/06_verification-report.md` | Every general and project DoD item has evidence; no open Critical/High finding; manifest hashes match |
| 7 | Release | `general/quality/definition-of-done.md`, `general/engineering-standards.md`, `project/03_technical/architecture.md` | `out/README.md`, one README per component, `docs/release-notes.md` | Every README works when followed from a clean checkout; manual-test list present; every decision has a resolution or is listed as pending review |

Any phase may write `docs/decisions-log.md`, `docs/00_progress.md` and `out/logs/`.

## On a failed gate

- Fix within the same phase, then re-check the gate.
- If the gate needs a change to an input or a frozen test, stop and raise a decision record.
