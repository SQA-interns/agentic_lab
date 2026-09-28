# Product contract

Build a conference registration system for external participants, students,
and an organizer. IDs below are stable references for acceptance criteria,
tests, and verification.

## User stories

| ID | Required behavior |
|---|---|
| US-001 | External participant submits their form and selects available conference activities. |
| US-002 | Student submits their form and selects available conference activities. |
| US-003 | Organizer changes the available workshops, events, meals, and other optional activities between conferences without changing participant fields or needing an admin UI. |
| US-004 | Participant sees clear in-app confirmation after successful backend acceptance. |
| US-005 | Organizer can recover successfully accepted registrations from durable storage. |
| US-006 | Participant receives a confirmation email for a successful registration. |
| US-007 | Organizer receives a notification for a successful registration. |
| US-008 | Organizer can export current registrations as Excel. |

## Fixed form fields and options

| Form | Required fixed fields |
|---|---|
| External | First name, last name, email, organization/institution |
| Student | First name, last name, email, study institution, study programme, student ID |

Conference-option groups: workshops, events, meals, other optional
activities. Each option has a stable identifier, display name, and
active/inactive status. Only known, active options may be selected. No
student-specific option restrictions are specified.

Required fields cannot be blank after trimming. Validate email format on
the backend; frontend validation improves usability. Support Unicode,
including Slovenian characters. Treat leading/trailing Unicode whitespace,
including no-break spaces, as insignificant. Where consent is mandatory,
present it unchecked and reject submission until given. The input does not
specify additional consent categories or exact legal wording: do not invent
legal claims.

## Success and boundaries

- Show in-app success only after the backend confirms acceptance.
- For every accepted registration store a database record and a raw JSON
  representation on persistent storage. The JSON also becomes the organizer
  notification attachment.
- Send confirmation to the participant and notification with submitted data
  and the raw JSON attachment to organizers, subject to the delivery
  semantics in `ARCHITECTURE.md`.
- Excel export reflects current accepted registrations and is accessible
  only to an authenticated organizer.

Do not add participant accounts, payment processing, editing submitted
registrations, student-status verification, administrative configuration UI,
full organizer dashboard, or identity-provider integration.

## Fixed acceptance examples

These are minimum behavior probes, not exhaustive tests or extra user stories.
Use synthetic data; derive detailed AC IDs without changing these expectations.

| Case | Input/action | Expected observable result |
|---|---|---|
| P-01 | Valid external form, including `Špela` and surrounding NBSP | Accepted once; text preserved with surrounding whitespace trimmed; matching database/JSON and success. |
| P-02 | Valid student form; then omit each required student field | Valid form accepted; missing required data rejected server-side, no accepted record. |
| P-03 | Blank required value, malformed email, or invalid captcha | Rejected without acceptance/confirmation. |
| P-04 | Known active option; then unknown/inactive ID sent directly to API | Active selection accepted; invalid selection rejected independently of UI. |
| P-05 | Change catalog configuration and restart | New catalog displayed and enforced; fixed participant fields unchanged. |
| P-06 | Synthetic required-consent fixture, initially unchecked; submit absent then present | Absent rejected; present permits otherwise valid submission. Fixture wording is not an approved legal notice. |
| P-07 | Repeat the same accepted request ID; interrupt one storage write | No second accepted registration for the same request; no false success after partial persistence; recover/reconcile as specified. |
| P-08 | SMTP unavailable then restored | Accepted data retained, pending notifications retried, both recipients and organizer attachment verified locally. |
| P-09 | Export with/without organizer authorization | Authorized workbook matches accepted records; unauthorized request reveals none. |
| P-10 | Recreate application/database containers preserving volumes | Previously accepted registrations and JSON remain available. |

Do not infer capacity limits, selection cardinalities, repeated-email policy,
retention periods or legal compliance requirements from these examples. State
ordinary chosen behavior in the specification; flag missing policy that blocks
real deployment. Completion here is the defined experimental DoD, not certification
of legal compliance or general production readiness.
