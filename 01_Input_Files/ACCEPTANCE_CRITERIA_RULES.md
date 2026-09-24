# Acceptance Criteria Rules

Acceptance Criteria are not provided as an input. They must be derived
from `USER_STORY.md` and `PROJECT_CONSTRAINTS.md` before any
implementation begins. This derivation is itself part of what the run
measures — do not skip it, and do not begin implementation before it is
complete.

## Format

Given–When–Then. One observable, independently testable behavior per
criterion.

* Do not bundle multiple assertions into a single criterion.
* Do not split a single behavior into multiple criteria to inflate
  coverage.

## Required coverage

For each User Story, derive at minimum:

* the happy path;
* at least one invalid-input case;
* at least one edge case implied by the story or by
  `PROJECT_CONSTRAINTS.md`.

Do not derive criteria for functionality not named in `USER_STORY.md`,
`TECH_STACK.md`, `PROJECT_CONSTRAINTS.md`, or `FORM_SCHEMA.md`. If a
criterion feels necessary but has no traceable source, flag it as
"unrequested functionality" in `docs/verification-report.md` rather than
adding it silently.

## Traceability

Every Acceptance Criterion must cite the User Story ID or the
`PROJECT_CONSTRAINTS.md` clause it comes from.

## Numbering

`AC-<story-id>-<sequence>`, sequential within a story, never renumbered
once assigned — even if a later criterion is removed, leave a gap
rather than renumbering the rest.

## Output

Save the result as `02_Implementation/docs/acceptance-criteria.md`
before Phase 2 (Specification) begins, per `RUN_INSTRUCTIONS.md`.