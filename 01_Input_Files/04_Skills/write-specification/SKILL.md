---
name: write-specification
description: Convert accepted requirements and technical constraints into an implementable design.
---

# Procedure

Read:

- business inputs;
- Acceptance Criteria;
- Tech Stack;
- Technical Constraints;
- Security Requirements;
- Deployment Constraints.

Define:

- architecture;
- component responsibilities;
- API;
- data model;
- configuration;
- persistence;
- backup behaviour;
- email behaviour;
- validation;
- security controls;
- export;
- error handling;
- container deployment;
- requirement traceability.

Do not implement production code or tests.

## API contract

Because acceptance tests are written against this contract before any
implementation exists (Phase 3), the API surface must be concrete
enough to write a real HTTP test against — not just prose. Produce it
as a machine-readable contract (OpenAPI, or an equivalent schema
matching the stack in `02_Technical/TECH_STACK.md`), covering every
endpoint, request/response shape, and status code the specification
describes.

## Output

Create exactly:

- `<IMPLEMENTATION_ROOT>/docs/specification.md`
- `<IMPLEMENTATION_ROOT>/docs/contracts/` (the API contract described
  above)
