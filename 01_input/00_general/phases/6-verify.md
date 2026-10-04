# Phase 6: Verify

> Owner: QA lead · Read in: phase 6 · Agent: read-only

- Read: `project/01_requirements/*`, `project/02_design/*`, `docs/01_acceptance-criteria.md`, `docs/02_specification.md`, `general/standards.md` (all sections)
- Write: `docs/06_verification-report.md` (findings `F-nn`), `docs/03_test-strategy.md` ("Final run")

Verify the finished system with evidence. Fill the tables of the report skeleton; add no prose beyond one line per finding. State at the top that it is a self-check, not an independent review.

## Do

1. Run `out/scripts/verify.sh` in full: test suite, every check command (ES-05), coverage, mutation, architecture tests, every scanner in `tooling` (dependency, static analysis, secrets), duplication and code metrics. Read the summary lines; open a log only for a tool that reports a problem. Mutation testing was classified in phase 5; here it only confirms the score, unless code changed since.
2. Recompute every hash in `docs/03_acceptance-manifest.sha256` and `docs/00_input-manifest.sha256`. Any mismatch is a Critical finding, whatever the reason.
3. Start every component as described for its target environment and exercise the core user flows and every `DoD-Pnn` at runtime; save what was run and observed to `out/logs/`.
4. For every `SB` and `SR` item, record where it is implemented and how it was checked (test, scan or inspection). An item without evidence is a finding.
5. Check logs and error responses produced during testing for personal data, secrets and internals. Check that no `.env` value leaked, listing file names only (no output means clean; any name is a Critical finding):
   - files: `git ls-files -co --exclude-standard -z | xargs -0 grep -lF -f <(sed -n 's/^[A-Z_]*=//p' .env | tr -d '\r "'"'"'' | awk 'length>=6')`
   - commit messages: `git log --all --format=%B | grep -cF -f <(sed -n 's/^[A-Z_]*=//p' .env | tr -d '\r "'"'"'' | awk 'length>=6')` (must print `0`)
6. Classify every finding with the severity scale. Scanner false positives and proposed downgrades follow `rules.md`.
7. Fix loops: for every Critical or High finding, fix, re-run only the affected tools, and log the loop. Triage Medium and Low as fixed, accepted with a reason, or open.
8. Build the traceability table: AC → tests → commits.
9. For each `DoD` and `DoD-Pnn` item, record evidence or a finding. DoD-08 (READMEs from a clean checkout) and DoD-09 (release notes) concern files that phase 7 writes: mark them "phase 7"; the phase 7 card adds their evidence.
10. Record `findings`, `finalTestRun` and `codeMetrics` in the run log.

## Evidence

- The phase 3 commits add no production code beyond the bootstrap skeleton; the freeze commit adds only the manifest, and every file it lists was committed earlier in phase 3.
- Phase 4 has at least one commit per user story, each naming its id; no commit exceeds the size guide in `rules.md` without a stated reason.
- Every manifest hash matches.
- Every decision has a resolution or is marked pending review.

## Gate

- Every general and project DoD item except DoD-08 and DoD-09 has evidence.
- No open Critical or High finding.
- Manifest hashes match.
- No secret value in any file, log or commit message.

## Commits

One per fix with its re-run evidence; the report in its own commit.
