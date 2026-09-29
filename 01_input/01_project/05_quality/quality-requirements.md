# Quality requirements

> Owner: QA lead · Read in: phases 2, 6, 7 · Agent: read-only

## Non-functional requirements

| ID | Category | Requirement | Check |
|---|---|---|---|
| NFR-01 | Integrity | No success without DB plus durable matching JSON; request retry does not duplicate acceptance. | Fault injection, retry, reconciliation |
| NFR-02 | Recovery | Restart/recreate with retained volumes preserves acceptance; SMTP recovery delivers both mails and organizer attachment. | Real PostgreSQL/filesystem/SMTP-catcher checks |
| NFR-03 | Usability | English UI; Unicode/Slovenian names; trim NBSP; clear field errors and post-acceptance confirmation. | Browser and API probes |
| NFR-04 | Accessibility | Semantic labels, keyboard operation, visible focus, associated errors; no colour-only feedback. | Keyboard walkthrough and component/browser checks |
| NFR-05 | Reproduction | Independent frontend/backend builds plus Compose; clean-checkout documentation works without untracked state. | Fresh isolated setup/build/start/smoke |

## Thresholds

| Measure | Threshold |
|---|---|
| Line coverage | record only; separate component/unit/integration scopes |
| Branch coverage | record only |
| Mutation score | record only; scope security/validation/persistence/domain logic |

Surviving mutants receive general severity review; no score-chasing exclusions.

## Additional done criteria

| ID | Criterion | Evidence |
|---|---|---|
| DoD-P01 | Both forms: valid Unicode/NBSP; each required field, email and captcha rejection; direct unknown/inactive option rejection. | UI/API tests, no rejected accepted-record |
| DoD-P02 | Catalog restart, initially unchecked mandatory synthetic consent; absent rejected, given accepted. | Configuration/UI/API probes |
| DoD-P03 | Persistence/duplicate/SMTP failures recover; authorized Excel includes both forms, unauthorized reveals none. | Fault tests, parsed workbook, mail attachment |
| DoD-P04 | Contracts semantically validate; protected helpers/config and collected/executed cases cannot silently bypass frozen tests. | Validator and inventory/hash evidence |
| DoD-P05 | Each bounded verified slice has a scoped commit with AC/check IDs. Preserve history; use scoped revert, not blanket reset. After undo recheck affected behavior and side effects. | Git/check/recovery evidence |
| DoD-P06 | Component/top guides and manual production checklist distinguish local substitutes from real TLS/captcha/SMTP checks. | Clean-checkout report/release notes |

## Overrides

Overrides: ES-06, DoD-08 — application entry README is 02_output/README.md;
repository-root README is protected template documentation. Prepare app/component
guides before phase-6 verification; phase 7 finalizes and rechecks them.

Slices follow SDD order: unit/integration tests belong to phase 5.
