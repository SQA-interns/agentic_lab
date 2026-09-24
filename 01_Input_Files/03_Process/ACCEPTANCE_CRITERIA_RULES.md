# Acceptance Criteria Rules

Acceptance Criteria are not provided.

They must be derived during Phase 1.

## Sources

Acceptance Criteria must be derived from:

- `01_Business/USER_STORIES.md`
- `01_Business/BUSINESS_RULES.md`
- `01_Business/FORM_SCHEMA.md`

Technical implementation choices from `02_Technical/` are not
Acceptance Criteria.

They are enforced later through Specification and Verification.

## Format

Use Given–When–Then.

Each criterion must represent one observable and independently testable
behaviour.

Do not:

- bundle unrelated behaviours;
- artificially split one behaviour to inflate the AC count;
- invent functionality.

## Coverage

For each User Story derive, where applicable:

- happy path;
- invalid-input behaviour;
- relevant business edge cases.

## Traceability

Every criterion must cite its source User Story and, where relevant, the
Business Rule or Form Schema rule.

## Numbering

Use:

`AC-<US number>-<sequence>`

Example:

`AC-001-01`

Never renumber existing criteria during the run.

## Output

Create exactly:

`<IMPLEMENTATION_ROOT>/docs/acceptance-criteria.md`

Complete this artefact before Specification begins.