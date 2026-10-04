# Phase 2: Design

> Owner: Architect · Read in: phase 2 · Agent: read-only

- Read: `docs/01_acceptance-criteria.md`, `project/00_setup/tech-stack.md`, `project/00_setup/environments.md`, `project/02_design/*`, `general/standards.md` ("Security baseline")
- Write: `docs/02_specification.md`, `docs/02_contracts/`

## Do

1. Define every interface listed in `architecture.md` as a contract in `docs/02_contracts/`, in a format a parser can validate (for example OpenAPI, JSON Schema, SQL).
2. Validate every contract with a parser through `verify.sh`; save the output to `out/logs/`. Run the static-analysis and secret scanners over the contracts too, and handle every Critical or High finding now (`rules.md`): a design finding found after phase 3 would hold up the frozen tests.
3. Write the specification: components and their responsibilities, the declared internal architecture (AR), configuration, security controls, error behaviour, and every choice the inputs leave open, each with a one-line reason.
4. End with the traceability table: every AC, SR, SB, NFR and AR maps to a section or a contract.

## Do not

- Do not repeat a contract's content in the specification; link to it.
- Do not write production code or tests.

## Size

Specification: at most about 250 lines.

## Gate

- Every AC, SR, SB, NFR and AR maps to the specification or a contract.
- Every contract validates with a parser.
- No Critical or High scanner finding on the contracts is open.

## Commits

One for the specification; one per contract.
