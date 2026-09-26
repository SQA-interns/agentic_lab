# Product contract — Conference Registration System

## Goal and users

The system lets conference organizers reliably collect registrations online.
It supports two registration paths and selected active conference activities.

| ID | User story | Required outcome |
|---|---|---|
| US-001 | External participant registers | Submit external form and selected active options |
| US-002 | Student registers | Submit student form and selected active options |
| US-003 | Organizer changes conference options | Workshops, events, meals and other activities are configurable without changing fixed participant fields; no configuration UI is required |
| US-004 | Participant receives confirmation | Clear in-app confirmation only after backend acceptance |
| US-005 | Organizer relies on storage | Successful registrations are stored reliably and recoverably |
| US-006 | Participant receives email | Confirmation email after successful registration |
| US-007 | Organizer is notified | Notification email for each successful registration |
| US-008 | Organizer exports registrations | Current registrations are available as an Excel workbook |

## Participant data

| Registration type | Fixed required fields |
|---|---|
| External | First name; last name; email; organization/institution |
| Student | First name; last name; email; study institution; study programme; student ID |

Every configurable option has a stable identifier, display name, and
active/inactive status. Forms support required consent fields where required;
required consent is never preselected.

## Invariants

- Required values are non-empty after trimming; leading/trailing whitespace is
  not significant.
- Email has valid format; text supports Unicode, including Slovenian characters.
- Only known, active configurable options are accepted.
- A registration succeeds only after backend acceptance.
- Persist each successful registration both in the relational database and as
  raw JSON on persistent filesystem storage.
- Send participant confirmation and organizer notification after success; the
  organizer message includes submitted data and attaches the raw JSON.
- Export and any organizer-only operation require organizer-level access
  control and must not expose participant data publicly.

## Explicit non-goals

Do not add participant accounts, payment processing, editing submitted
registrations, a full administrative UI/dashboard, identity-provider
integration, or external student-status verification unless a new immutable
input explicitly adds it.

## Acceptance-criteria gate

Before design or code, derive `docs/acceptance-criteria.md` from this document
alone. Use independently observable Given–When–Then criteria; include happy,
invalid-input, and relevant business-edge behavior. Number `AC-<US>-<n>` and
cite the US and invariant. Do not introduce implementation choices or invent
functionality.
