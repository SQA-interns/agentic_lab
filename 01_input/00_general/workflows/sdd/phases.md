# Phases: SDD (spec-driven, acceptance tests first)

> Owner: Team lead · Read in: every phase · Agent: read-only · Workflow: `sdd`

Run the phases in order. A phase starts only after the previous gate has passed.
Aliases: see `01_input/README.md`; `wf/` = `general/workflows/sdd/`.

| # | Phase | Reads | Writes | Gate (must be true to continue) |
|---|---|---|---|---|
| 0 | Preflight & bootstrap | `project/01_setup/*`, `project/03_technical/*`, `general/engineering-standards.md`, skill `general/skills/preflight` | `docs/00_preflight-report.md`, `docs/00_input-manifest.sha256`, `docs/00_progress.md`, one skeleton per component, repo files (ES-03) | Every preflight check passes; every component builds; manifests and lock files match `tech-stack.md` exactly; every listed tool runs; input manifest written |
| 1 | Requirements | `project/02_business/*` | `docs/01_acceptance-criteria.md` | Every story has ≥ 1 AC; every AC traces to a story; every open question is answered or has a decision record |
| 2 | Design | `docs/01_*`, `project/03_technical/*`, `project/04_security/*`, `project/05_quality/*`, `general/security/*` | `docs/02_specification.md`, `docs/02_contracts/` | Every AC, SR, SB and NFR maps to the spec; contracts validate with a parser |
| 3 | Test design | `docs/01_*`, `docs/02_contracts/`, `general/quality/test-strategy.md`, skill `wf/skills/write-acceptance-tests` | acceptance tests, `docs/03_test-strategy.md`, `docs/03_acceptance-manifest.sha256` | Every AC has a test; every test fails for a behavioural reason (no build or setup errors); manifest committed with the tests |
| 4 | Build | `docs/02_*`, `project/03_technical/*`, `general/engineering-standards.md` | source code | All frozen acceptance tests pass; format, lint and type checks are clean |
| 5 | Unit tests | source code, `general/quality/test-strategy.md` | unit tests, `docs/03_test-strategy.md` | First full run recorded and classified before any fix; full suite passes |
| 6 | Verify | all of `01_input/`, all of `docs/`, skills `general/skills/security-review`, `general/skills/verify-release` | `docs/06_verification-report.md` | Every general and project DoD item has evidence; no open Critical/High finding; manifest hashes match |
| 7 | Release | `general/quality/definition-of-done.md`, `general/engineering-standards.md`, `project/03_technical/architecture.md` | `out/README.md`, one README per component, `docs/release-notes.md` | Every README works when followed from a clean checkout; manual-test list present; every decision has a resolution or is listed as pending review |

Any phase may write `docs/decisions-log.md`, `docs/00_progress.md` and `out/logs/`.

## On a failed gate

- Fix within the same phase, then re-check the gate.
- If the gate needs a change to an input or a frozen test, stop and raise a decision record.
