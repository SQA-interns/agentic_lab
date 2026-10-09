# Phases

> Owner: Team lead · Fill: once per organisation · Read in: every phase · Agent: read-only

Run the phases in order; a phase starts only after the previous gate has passed. For each phase: the only files to read besides `AGENTS.md`, `rules.md` and this file, what it writes, how to do it (its steps below, or a skill for the longer procedures), its gate and its commits.

On a failed gate: fix within the same phase and re-check. If the gate needs a change to an input or a frozen test, raise a blocking decision. Any phase may write `docs/decisions-log.md`, `docs/00_progress.md` and `out/logs/`.

## 0 Preflight and bootstrap

- Skill: `skills/preflight`
- Read: `project/00_setup/run-config.md`, `project/secrets.env.example`, `project/stack.md`, `project/constraints.md` ("Components"), `standards/engineering.md`, `standards/security.md`
- Write: `docs/00_preflight-report.md`, `docs/00_input-manifest.sha256`, `docs/00_progress.md`, one skeleton per component, repository files (ES-03), `out/scripts/verify.sh`
- Gate: every preflight check passes; every component builds; manifests and lock files match `project/stack.md` exactly (as amended by decisions); every listed tool runs through `verify.sh`; input manifest written.
- Commits: one per component skeleton; one for the repository files; one for `verify.sh`; one for the preflight documents.

## 1 Requirements

- Read: `project/requirements.md`
- Write: `docs/01_acceptance-criteria.md`
- Do:
  1. Derive acceptance criteria `AC-nnn-nn` from the stories: Given/When/Then, one observable behaviour each, covering the accepted case, each rejection and each business edge the stories and rules state. Each cites its `US` and any `BR`.
  2. Write them as one table per story: `| AC | BR | Given | When | Then |`. No other text.
  3. For every unanswered open question (`OQ`), record a non-blocking decision with the more conservative behaviour and write the criteria accordingly.
- Do not: invent behaviour that no story or rule states (record the gap as a decision); name a technology, endpoint or data structure in a criterion.
- Gate: every story has at least one AC; every AC cites its story; every open question is answered or has a decision.
- Commits: one for the document.

## 2 Design

- Read: `docs/01_acceptance-criteria.md`, `project/stack.md`, `project/constraints.md`, `standards/security.md`
- Write: `docs/02_specification.md` (at most about 250 lines), `docs/02_contracts/`
- Do:
  1. Define every interface listed in `project/constraints.md` ("Interfaces") as a contract in `docs/02_contracts/`, in a format a parser can validate (for example OpenAPI, JSON Schema, SQL).
  2. Validate every contract with a parser through `verify.sh`. Run the static-analysis and secret scanners over the contracts too, and handle every Critical or High finding now: a design finding found after phase 3 would hold up the frozen tests.
  3. Write the specification: components and their responsibilities, the declared internal architecture (AR), configuration, security controls, error behaviour, and every choice the inputs leave open, each with a one-line reason.
  4. End with the traceability table: every AC, SR, SB, NFR and AR maps to a section or a contract.
- Do not: repeat a contract's content in the specification (link to it); write production code or tests.
- Gate: every AC, SR, SB, NFR and AR maps to the specification or a contract; every contract validates with a parser; no Critical or High scanner finding on the contracts is open.
- Commits: one for the specification; one per contract.

## 3 Test design

- Skill: `skills/write-acceptance-tests`
- Read: `docs/01_acceptance-criteria.md`, `docs/02_contracts/`, `standards/testing.md`
- Write: acceptance and end-to-end tests, `docs/03_test-strategy.md`, `docs/03_acceptance-manifest.sha256`
- Gate: every AC has a test; every test fails for a behavioural reason, or is listed as passing on bootstrap code; the manifest is committed last, alone.
- Commits: one per user story's acceptance and end-to-end tests; then the freeze commit (manifest only).

## 4 Build

- Read: `docs/02_specification.md`, `docs/02_contracts/`, `project/stack.md`, `project/constraints.md` ("Architecture"), `project/requirements.md` ("Priorities"), `standards/engineering.md`
- Write: source code
- Do:
  1. Build story by story, in the priority order of `project/requirements.md` ("Priorities"). After each story: run its frozen tests and the check command through `verify.sh`, then commit.
  2. Follow the specification and contracts. Where they turn out wrong, record a decision and correct the document in its own commit.
  3. A frozen test that appears wrong is a blocking decision; continue with the other stories first.
  4. Before the gate, save `git log --oneline <freeze commit>..HEAD` to `out/logs/` and check there is a commit per story.
- Do not: write unit tests; edit a frozen test, its helpers or its configuration; add a dependency that `project/stack.md` does not list, except under `standards/engineering.md` ("Versions").
- Gate: all frozen acceptance and end-to-end tests pass; format, lint and type checks are clean; at least one commit per user story, each naming its `US` or `AC` id.
- Commits: one per user story, or per coherent group of ACs of one story.

## 5 Unit tests

- Read: source code, `standards/testing.md`, `project/constraints.md` ("Thresholds")
- Write: unit and integration tests, `docs/03_test-strategy.md`
- Do:
  1. Write unit and integration tests for the logic the acceptance tests do not reach: validation, security, persistence, business rules, error paths.
  2. Run the full suite, all levels. If it does not compile, fix compile errors only, changing no behaviour and no assertion, and run again. Record that result as the first complete run (`firstTestRun`) before any other fix.
  3. Classify each failure, then fix: an implementation defect → fix the code; a defect in a non-frozen test → fix the test and say so in `docs/03_test-strategy.md`; a frozen test that appears wrong → blocking decision, do not touch the test.
  4. Once the suite passes, run coverage and mutation testing through `verify.sh`. Classify every surviving mutant in validation, security, persistence and business-rule code: add a test where one should have caught it, otherwise state why not (equivalent, logging only, not observable).
  5. Fill `docs/03_test-strategy.md`: test levels as executed, the first run, and the measures table of `standards/testing.md`.
- Gate: the first complete run is recorded and classified before any fix; the full suite passes; coverage and mutation score are recorded; every surviving mutant in validation, security, persistence and business-rule code is classified.
- Commits: one per component area or layer under test; one per fix.

## 6 Verify

- Skill: `skills/verify-release`
- Read: `project/requirements.md`, `project/constraints.md`, `docs/01_acceptance-criteria.md`, `docs/02_specification.md`, every file in `standards/`
- Write: `docs/06_verification-report.md`, `docs/03_test-strategy.md` ("Final run")
- Gate: every DoD and DoD-P item except DoD-08 and DoD-09 has evidence; no open Critical or High finding; manifest hashes match; no secret value in any file, log or commit message.
- Commits: one per fix with its re-run evidence; the report in its own commit.

## 7 Release

- Skill: `skills/release`
- Read: `project/constraints.md` ("Components"), `project/stack.md` ("External services"), `standards/engineering.md` (ES-05, ES-06), `standards/done.md`, `docs/decisions-log.md`, `docs/03_test-strategy.md`, `03_statistics/metrics.md`
- Write: `out/README.md`, one README per component, `docs/release-notes.md`, the DoD-08 and DoD-09 rows of `docs/06_verification-report.md`, `03_statistics/run-summary.md`, `03_statistics/run-log.json`
- Gate: every README works from a clean checkout; the release notes have every section of their skeleton; DoD-08 and DoD-09 have evidence; every decision has a resolution or is listed as pending review; `usage` in `run-log.json` is filled (the cost may stay `null` only when `usage.md` has no price row for the model).
- Commits: one per README; one for the release notes; one for the verification report rows; one for the run summary and run log.
