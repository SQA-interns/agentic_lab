# Testing standards

> Owner: QA lead · Fill: once per organisation; review when the test policy changes · Read in: phases 3, 5, 6 · Agent: read-only

## Levels

| Level | Written in phase | Written from | Frozen | Location |
|---|---|---|---|---|
| Acceptance | 3 | acceptance criteria + contracts | yes, from the freeze commit | path contains `acceptance` |
| End-to-end | 3 | acceptance criteria | yes, from the freeze commit | path contains `e2e` |
| Unit / integration | 5 | implementation | no | any other test path |

## Measures

Recorded in phases 5 and 6 in `docs/03_test-strategy.md` and copied to the run summary. Thresholds are in `project/constraints.md` ("Thresholds").

| Measure | Scope |
|---|---|
| Test counts | per component and per level (acceptance, end-to-end, unit, integration, architecture), passed and failed |
| Line and branch coverage | per component, unit and integration separately where the tools allow, and all levels together; name the tool |
| Mutation score | per component, over validation, security, persistence and business-rule code at least; state what was excluded and why; name the tool |

## Constraints

- Do not test acceptance behaviour through internals; use the component's public interface (API, UI, CLI, messages).
- Do not depend on live external services in automated tests; use the local substitutes in `project/stack.md` ("Environments").
- Do not leave flaky, skipped or disabled tests; fix them or record a decision.
- Do not weaken an assertion to make an incorrect implementation pass.
- Do not edit, delete or weaken a test listed in `docs/03_acceptance-manifest.sha256`.
- Test names contain the AC id they verify.
- `docs/03_acceptance-manifest.sha256` uses `sha256sum` format, paths relative to `02_output/`, hashed after LF normalisation.
