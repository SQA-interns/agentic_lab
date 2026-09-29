# Scope

> Owner: Product owner · Read in: phases 1, 6 · Agent: read-only

## In scope

A complete conference-registration app: separate external/student forms, configurable
activities, durable database plus JSON storage, success screen, participant and
organizer notifications, and protected Excel export. Separate frontend/backend,
local container deployment, automated verification and reproducible build guides.
All US-001…008 are required; no demo-only delivery.

## Out of scope

Participant accounts, payments, post-submission editing, student verification,
admin/configuration UI, full organizer dashboard, IdP integration. No production
hosting, real-user data processing or real recipient messaging during the lab run.

## Priorities

Fixed stack/architecture and data integrity constrain implementation. Never trade
security, durable acceptance or required verification for appearance or speed.
Appearance/layout remain agent choices. Unresolved input conflicts use decision
records; do not silently discard requirements. Evaluate the whole system's
configuration, not a model leaderboard score.

## Open questions

| ID | Question | Owner | Answer/status |
|---|---|---|---|
| OQ-01 | Real conference title/catalog/recipients? | Product owner | Lab Conference; synthetic choices in every group; organizer@example.test and participant@example.test through the catcher only. |
| OQ-02 | Consent wording/legal basis? | Product owner | Not supplied; synthetic required-consent fixture only. Blocks real deployment, not synthetic development. |
| OQ-03 | Production retention/deletion policy? | Data owner | Not supplied. Retain synthetic data for the experiment; operator explicitly removes it afterward. No invented real-data retention period. |
| OQ-04 | Capacity, selection cardinality, repeated emails? | Product owner | No capacity limits; zero or more distinct selections/group; distinct request IDs may reuse email. No payment/waitlist feature. |
| OQ-05 | Repository licence? | Team lead | No distribution licence supplied. Private lab use only; do not add a licence or redistribute. Inventory third-party licence obligations. |
| OQ-06 | Exact stack baseline and run identity? | Operator/architect | Chosen in tech-stack.md and run-config.md; runtime availability and actual starting HEAD remain preflight facts. |

Additional material questions go in generated docs/decisions-log.md, never by
editing this read-only source. Production-only unknowns must not falsely imply
that local verification proves production readiness.
