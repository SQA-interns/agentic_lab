---
name: derive-acceptance-criteria
description: Convert business requirements into observable acceptance criteria.
---

# Procedure

1. Read every User Story.
2. Read Business Rules.
3. Read Form Schema.
4. Identify observable business behaviour.
5. Create Given–When–Then criteria.
6. Add relevant invalid and boundary behaviour.
7. Trace every criterion to its source.
8. Check that no technical implementation decision was introduced.

Do not:

- invent features;
- specify architecture;
- specify frameworks;
- implement code.

## Output

Create exactly:

`02_Implementation/docs/acceptance-criteria.md`
