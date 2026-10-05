---
name: verify-release
description: Verify the finished system with evidence - definition of done, frozen tests, security controls and scanners, secret leaks, runtime flows, traceability - and run verify, fix, re-verify loops (phase 6).
---

> Owner: QA lead · Read in: phase 6 · Agent: read-only

Fill the tables of the report skeleton; add no prose beyond one line per finding. State at the top that it is a self-check, not an independent review.

## Do

1. Run `out/scripts/verify.sh` in full: test suite, every check command (ES-05), coverage, mutation, architecture tests, every scanner in `tooling` (dependency, static analysis, secrets), duplication and code metrics. Read the summary lines; open a log only for a tool that reports a problem. Mutation testing was classified in phase 5; here it only confirms the score, unless code changed since.
2. Recompute every hash in `docs/03_acceptance-manifest.sha256` and `docs/00_input-manifest.sha256`. Any mismatch is a Critical finding, whatever the reason.
3. Start every component as described for its target environment and exercise the core user flows and every `DoD-Pnn` at runtime; save what was run and observed to `out/logs/`.
4. For every `SB` and `SR` item, record where it is implemented and how it was checked (test, scan or inspection). An item without evidence is a finding.
5. Check logs and error responses produced during testing for personal data, secrets and internals. Then `bash 01_input/00_general/tools/secrets.sh leak-check`: no output means clean; any file name is a Critical finding. A match of a value that is also an ordinary word is judged by the key name and recorded as a finding with that reasoning; never print the value to check it.
6. Classify every finding with `standards/security.md` ("Severity scale"). False positives and downgrades follow `skills/decisions`.
7. Fix loops: for every Critical or High finding, fix, re-run only the affected tools, and log the loop. Triage Medium and Low as fixed, accepted with a reason, or open.
8. Build the traceability table: AC → tests → commits.
9. For each DoD and DoD-P item, record evidence or a finding. DoD-08 and DoD-09 concern files phase 7 writes: mark them "phase 7".
10. Fill "Final run" in `docs/03_test-strategy.md` with the measures table of `standards/testing.md`. Record `findings`, `finalTestRun` and `codeMetrics` in the run log.

## Evidence

- The phase 3 commits add no production code beyond the bootstrap skeleton; the freeze commit adds only the manifest, and every file it lists was committed earlier in phase 3.
- Phase 4 has at least one commit per user story, each naming its id; no commit exceeds the size guide in `rules.md` without a stated reason.
- Every manifest hash matches.
- Every decision has a resolution.
