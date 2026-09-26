# Experiment Metrics

Only metrics defined here belong to the experiment.

`METRICS.md` defines **what** is measured and **how**.
`run-log.template.json` is the **schema** for those measurements.

Copy the template to `<STATISTICS_ROOT>/run-log.json` at run start.
Write every run measurement into that file. Do not add fields that are
not in the template.

A metric must not be estimated.

A missing field is a bug. If a metric cannot be measured with its
defined method, record `null` and the reason (`<field>Reason` for a
scalar, or `_reason` on an object).

Derived values listed below are **not** stored as extra fields. They
are calculated later from the stored timestamps and arrays, so every
run can be compared with the same formula.

# Agent-recorded metrics

Filled live during the run. Everything above `_selfReportNote` in
`run-log.json`.

## Time

| Metric | Field | Method |
| --- | --- | --- |
| Experiment start | `experimentStart` | Record immediately when the run begins |
| Experiment end | `experimentEnd` | Record when finalization completes |
| Phase duration | `phases[].start`, `phases[].end` | Record as each phase happens |
| Time to first working happy path | `firstWorkingHappyPathTimestamp` | First time the primary registration happy path is executable |
| Time spent in fix loops | `fixLoops[].start`, `fixLoops[].end` | Sum of closed-loop durations after the run |

Total run time is `experimentEnd - experimentStart`. It must also be
checkable against the first and last phase timestamps.

## Human effort

| Metric | Field | Method |
| --- | --- | --- |
| Human interventions | `humanInterventions[]` | Each entry is `{start, end, reason}` |
| Human-intervention duration | `humanInterventions[].start`, `humanInterventions[].end` | Sum of entry durations after the run |
| Clarifying questions | `clarifyingQuestions` | Count |
| Manual code fixes | `manualCodeFixes` | Count of human-edited code changes |

Do not record interventions as a count only.

## Verification

| Metric | Field | Method |
| --- | --- | --- |
| First complete test-run passed/failed | `firstTestRun.passed`, `firstTestRun.failed` | Record before repairing failures |
| Final test passed/failed | `finalTestRun.passed`, `finalTestRun.failed` | Last complete test execution of the run |
| Verifier findings by severity | `verificationFindings` | Counts for `critical`, `major`, `minor` |
| Fix-loop count | `fixLoops[]` | One object per loop; count is the array length |

Each fix loop is:

`{loopNumber, trigger, findingIds, change, start, end}`

## Git

| Metric | Field | Method |
| --- | --- | --- |
| Commits | `commits[]` | One object per commit; count is the array length |
| Reverts | `reverts` | Count |
| Merge conflicts | `mergeConflicts` | Count |

Each commit is:

`{sha, phase, message, filesChanged, linesAdded, linesRemoved}`

Also record `startCommit` and `finalCommit`.

# Deterministic post-run metrics

Filled after the run by external audit. Everything below
`_selfReportNote`. The agent leaves these `null` with the template
reason.

## Code size

| Metric | Field | Method |
| --- | --- | --- |
| Backend LOC | `codeQuality.backendLoc` | Frozen backend toolchain |
| Frontend LOC | `codeQuality.frontendLoc` | Frozen frontend toolchain |
| Total LOC | `codeQuality.loc` | `backendLoc + frontendLoc` |

## Complexity

| Metric | Field | Method |
| --- | --- | --- |
| Backend complexity | `codeQuality.backendComplexity` | PMD |
| Frontend complexity | `codeQuality.frontendComplexity` | Frozen ESLint configuration |
| Cyclomatic complexity average | `codeQuality.cyclomaticComplexityAvg` | Frozen toolchain after the run |
| Cyclomatic complexity maximum | `codeQuality.cyclomaticComplexityMax` | Frozen toolchain after the run |

## Duplication

| Metric | Field | Method |
| --- | --- | --- |
| Backend duplication | `codeQuality.backendDuplicationPct` | PMD CPD |
| Frontend duplication | `codeQuality.frontendDuplicationPct` | jscpd |
| Total duplication | `codeQuality.duplicationPct` | Combined frozen-toolchain duplication |

## Quality

| Metric | Field | Method |
| --- | --- | --- |
| Lint errors | `codeQuality.lintErrors` | Frozen linters |
| Lint warnings | `codeQuality.lintWarnings` | Frozen linters |
| Type-check errors | `codeQuality.typeCheckErrors` | Frozen type checker |
| Backend unit coverage | `codeQuality.backendUnitCoveragePct` | JaCoCo unit |
| Backend integration coverage | `codeQuality.backendIntegrationCoveragePct` | JaCoCo integration |
| Frontend test coverage | `codeQuality.frontendCoveragePct` | Frozen frontend coverage |
| New runtime dependencies | `codeQuality.newDependenciesRuntime` | Count added versus the frozen baseline |
| New dev dependencies | `codeQuality.newDependenciesDev` | Count added versus the frozen baseline |

## Security

| Metric | Field | Method |
| --- | --- | --- |
| Semgrep findings by severity | `security.semgrep` | Counts for `critical`, `high`, `medium`, `low` |
| OWASP Dependency-Check findings by severity | `security.owaspDependencyCheck` | Counts for `critical`, `high`, `medium`, `low` |
| npm audit findings by severity | `security.npmAudit` | Counts for `critical`, `high`, `medium`, `low` |

## Architecture

| Metric | Field | Method |
| --- | --- | --- |
| ArchUnit violations | `architecture.archUnitViolations` | ArchUnit |
| Layer violations | `architecture.layerViolations` | ArchUnit rules for the architecture declared in the Specification |
| Dependency cycles | `architecture.dependencyCycles` | Frozen architecture check |
| Average afferent coupling | `architecture.couplingAvgCa` | Frozen architecture / dependency analysis |
| Average efferent coupling | `architecture.couplingAvgCe` | Frozen architecture / dependency analysis |
| Maximum dependency depth | `architecture.dependencyDepthMax` | Frozen architecture / dependency analysis with path-length reporting |
| Runtime dependency count | `architecture.runtimeDependenciesBackend`, `architecture.runtimeDependenciesFrontend` | Declared runtime dependencies |

# Harness/provider metrics

Also below `_selfReportNote`. Never estimated by the development agent.

| Metric | Field | Method |
| --- | --- | --- |
| Tokens consumed | `tokensConsumed` | Provider usage export |
| Estimated provider cost | `estimatedCostUsd` | Derived from `tokensConsumed` |
| Tool calls | `toolCalls` | Harness transcript |
| Approval prompts | `approvalPrompts` | Counted separately from `toolCalls` |

# Cross-run metrics

Calculated only after multiple runs. They are not fields in
`run-log.json`.

- execution-time variance;
- token-use variance;
- quality variance;
- output similarity;
- architecture similarity.
