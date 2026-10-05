# Requirements

> Owner: Product owner · Fill: once per project · Read in: phases 1, 2, 6 · Agent: read-only

What the product must do. Ids: `US`, `BR`, `OQ`.

## User stories

Epic: as a conference organizer, I want participants to register online, so that registrations are collected and managed reliably.

#### US-001 External participant registration

As an external participant, I want to register using the external participant form, so that I can attend the conference and the activities I select.
In scope: the external form (BR-01), selecting active options (BR-04). Out of scope: payment, accounts, editing a submitted registration.

#### US-002 Student registration

As a student, I want to register using the student form, so that I can attend the conference and the activities available to students.
In scope: the student form (BR-01), selecting active options (BR-04). Out of scope: verifying student status externally, accounts.

#### US-003 Configurable conference options

As an organizer, I want workshops, events, meals and other optional activities to be configurable, so that the system can be reused when the programme changes.
Out of scope: an administration UI for options.

#### US-004 Registration confirmation

As a participant, I want a clear confirmation in the application after my registration is processed, so that I know it was received (BR-06).

#### US-005 Reliable registration storage

As an organizer, I want every accepted registration stored reliably, so that none is lost and each can be recovered (BR-07).

#### US-006 Participant email confirmation

As a participant, I want an email confirming my registration, so that I have a record of it.

#### US-007 Organizer notification

As an organizer, I want an email when someone registers, containing the submitted data with the raw registration JSON attached, so that I can monitor incoming registrations.

#### US-008 Registration export

As an organizer, I want to export the current registrations as an Excel workbook, so that I can process them outside the system (BR-08).

## Business rules

| ID | Rule |
|---|---|
| BR-01 | There are two registration types: external participant and student. Each has the fixed fields listed under Data; configuration never changes them. |
| BR-02 | Required fields are never empty; leading and trailing whitespace is not significant. |
| BR-03 | Email has a valid email format. Text accepts Unicode, including Slovenian characters (č, š, ž). |
| BR-04 | Options are grouped as workshops, events, meals and other activities. Only active options can be selected; unknown or inactive options are rejected. |
| BR-05 | Mandatory consents must be given and are never preselected. |
| BR-06 | The in-application confirmation is shown only after the registration was accepted. |
| BR-07 | An accepted registration is stored in the database and as a raw JSON copy on persistent storage. |
| BR-08 | Only organizers can obtain the registration list; it is not reachable without organizer access. |

### Data

| Form or entity | Fields (all required) |
|---|---|
| External participant | first name, last name, email, organization / institution |
| Student | first name, last name, email, study institution, study programme, student ID |
| Conference option | stable identifier, display name, category (workshop, event, meal, other), active or inactive |
| Consent | which consents exist and their wording: `OQ-02` |

### Glossary

- Participant: a person who registers, external or student.
- Organizer: conference staff who receive notifications and export registrations.
- Option: a selectable workshop, event, meal or other activity.
- Active option: an option currently offered for selection.
- Raw JSON copy: the registration exactly as accepted, stored as a file for backup.

## Scope

### In scope

- Registration forms for external participants and students (US-001, US-002)
- Configurable conference options (US-003)
- In-application confirmation, participant confirmation email, organizer notification email with the JSON attachment (US-004, US-006, US-007)
- Storage in the database plus a raw JSON copy (US-005)
- Excel export for organizers (US-008)
- Anti-automation protection of the registration form

### Out of scope

- Participant accounts and login
- Payment processing
- Editing or cancelling a submitted registration
- Verifying student status externally
- An administration UI or dashboard, including option configuration
- Integration with an identity provider

### Priorities

1. A registration is never lost: storage (BR-07) comes before any notification.
2. Security and privacy (`project/constraints.md`) come before convenience.
3. The participant flow (US-001 to US-006) comes before organizer features (US-007, US-008).

## Open questions

| ID | Question | Owner | Answer |
|---|---|---|---|
| OQ-01 | Are all options available to students, or are some only for external participants? | Product owner | |
| OQ-02 | Which mandatory consents exist, and what is their wording? | Product owner | |
| OQ-03 | What happens when storage succeeds but an email fails? | Product owner | |
| OQ-04 | How many options may a participant select per category? | Product owner | |
| OQ-05 | Is a second registration with the same email allowed? | Product owner | |
| OQ-06 | How long are registrations and JSON copies kept? | Product owner | |

Unanswered questions become decision records in phase 1.
