# Experiment Metrics

Only metrics defined here belong to the experiment.

A metric must not be estimated.

If it cannot be measured using its defined method, record `null` and
the reason.

# Agent-recorded metrics

## Time

- experiment start timestamp (`experimentStart`);
- experiment end timestamp (`experimentEnd`);
- total run time;
- duration of each development phase;
- time to first working happy path;
- time spent in fix loops.

Source:
recorded timestamps. `experimentStart` and `experimentEnd` are
recorded directly; total run time must also be checkable against the
first and last phase timestamps.

## Human effort

- human interventions;
- total human-intervention duration;
- clarifying questions;
- manual code fixes.

Source:
run log.

## Verification

- first complete test-run passed/failed count;
- final test passed/failed count;
- verifier findings by severity;
- fix-loop count.

## Git

- commit count;
- reverts;
- merge conflicts.

# Deterministic post-run metrics

## Code size

- backend LOC;
- frontend LOC.

## Complexity

- backend complexity using PMD;
- frontend complexity using the frozen ESLint configuration.

## Duplication

- backend duplication using PMD CPD;
- frontend duplication using jscpd.

## Quality

- lint errors;
- lint warnings;
- type-check errors;
- backend unit coverage;
- backend integration coverage;
- frontend test coverage.

## Security

- Semgrep findings by severity;
- OWASP Dependency-Check findings by severity;
- npm audit findings by severity.

## Architecture

- ArchUnit violations;
- dependency cycles;
- runtime dependency count.

# Harness/provider metrics

When exposed externally:

- tokens consumed;
- tool calls;
- approval prompts;
- estimated provider cost.

These are never estimated by the development agent.

# Cross-run metrics

Calculated only after multiple runs:

- execution-time variance;
- token-use variance;
- quality variance;
- output similarity;
- architecture similarity.