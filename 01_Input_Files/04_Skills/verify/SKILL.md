---
name: verify
description: Verify the complete solution independently against requirements.
---

# Procedure

Re-read:

- User Stories;
- Business Rules;
- Form Schema;
- Acceptance Criteria;
- Specification;
- all technical constraints.

Execute every Definition of Done check.

Inspect specifically for:

- missing requirements;
- incorrect requirements;
- unrequested functionality;
- security failures;
- architecture violations;
- regressions;
- runtime/container failures.

Classify findings:

- Critical
- Major
- Minor

For every failure:

Verify → Fix → Re-verify

Record each loop separately.

## Output

Create exactly:

`02_Implementation/docs/verification-report.md`

Update fix-loop records in:

`03_Run-Statistics/run-log.json`
