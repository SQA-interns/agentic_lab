# Acceptance Criteria — Conference Registration System

Derived in Phase 1 from `01_Business/USER_STORIES.md`,
`01_Business/BUSINESS_RULES.md` and `01_Business/FORM_SCHEMA.md` only,
following `03_Process/ACCEPTANCE_CRITERIA_RULES.md`. No technical
implementation choice is made here; those belong to the Specification.

Source abbreviations:

- **US-nnn** — User Story in `USER_STORIES.md`
- **BR:<section>** — section of `BUSINESS_RULES.md`
- **FS:<section>** — section of `FORM_SCHEMA.md`

Where the business inputs leave a user-visible behaviour open, the
criterion states the conservative reading and points to the matching
entry in `docs/decisions-log.md` (marked **[D-n]**).

Terms used below:

- **accepted** — the system has taken the registration and stored it
  (BR: Successful registration).
- **rejected** — the system refuses the submission; nothing is stored,
  and the participant sees why.
- **active option** — a configurable conference option whose status is
  active (FS: Configurable conference options).

---

## US-001 — External participant registration

### AC-001-01 — Valid external registration is accepted

Given the external participant registration form
And the participant has entered a first name, last name, a valid email
and an organization / institution
And has given every mandatory consent
When the participant submits the form
Then the registration is accepted as an external participant
registration with the entered values.

*Source: US-001; FS: External participant; BR: Registration types.*

### AC-001-02 — Missing required field is rejected

Given the external participant registration form
And one of the required fields (first name, last name, email,
organization / institution) is left empty
When the participant submits the form
Then the registration is rejected
And the participant is told which field is missing.

*Source: US-001; FS: General data rules (required fields must not be
empty).*

### AC-001-03 — Whitespace-only value counts as empty

Given the external participant registration form
And a required field contains only whitespace
When the participant submits the form
Then the registration is rejected for that field in the same way as an
empty field.

*Source: US-001; FS: General data rules (whitespace not significant;
required fields must not be empty).*

### AC-001-04 — Invalid email format is rejected

Given the external participant registration form
And the email field contains a value that is not a valid email address
(e.g. `ana.novak`, `ana@`, `@example.com`)
When the participant submits the form
Then the registration is rejected
And the participant is told the email is invalid.

*Source: US-001; FS: General data rules (valid email format).*

### AC-001-05 — Leading and trailing whitespace is not significant

Given the external participant registration form
And a text field is entered with leading or trailing whitespace
(e.g. `"  Ana  "`)
When the registration is accepted
Then the value is stored and later shown without that leading or
trailing whitespace (`"Ana"`).

*Source: US-001; FS: General data rules (leading/trailing whitespace
not significant).*

### AC-001-06 — Slovenian and other Unicode characters are preserved

Given the external participant registration form
And text fields contain Unicode characters, including Slovenian
characters (e.g. first name `Živa`, last name `Čepič`, organization
`Univerza v Ljubljani — Fakulteta za računalništvo`)
When the registration is accepted
Then the values are stored exactly as entered (after trimming) and
appear unchanged everywhere the registration is later shown.

*Source: US-001; FS: General data rules (Unicode, Slovenian
characters).*

### AC-001-07 — Mandatory consent must be given

Given the external participant registration form
And a mandatory consent has not been given
When the participant submits the form
Then the registration is rejected
And the participant is told the consent is required.

*Source: US-001; FS: Consent. See [D-2] for which consents are
mandatory.*

### AC-001-08 — Mandatory consent is not preselected

Given a participant opens the external participant registration form
When the form is first displayed
Then no mandatory consent is preselected.

*Source: US-001; FS: Consent (mandatory consent must not be
preselected).*

### AC-001-09 — Only active options are offered

Given conference options exist in each configurable set (workshops,
events, meals, other activities), some active and some inactive
When a participant opens the external participant registration form
Then every active option is offered for selection, by its display name
And no inactive option is offered.

*Source: US-001; US-003; BR: Configurable options (only currently
active options may be selected).*

### AC-001-10 — Selected active options are recorded

Given the external participant registration form is otherwise valid
And the participant selects one or more active options
When the participant submits the form
Then the registration is accepted
And exactly the selected options are recorded with it.

*Source: US-001 (selection of available conference options).*

### AC-001-11 — Registration without optional selections is accepted

Given the external participant registration form is otherwise valid
And the participant selects no conference options
When the participant submits the form
Then the registration is accepted with no options recorded.

*Source: US-001; FS: Configurable conference options (options are
optional activities). See [D-4].*

### AC-001-12 — Unknown option is rejected

Given the external participant registration form is otherwise valid
And the submission references an option identifier that does not exist
When the submission is received
Then the registration is rejected and nothing is stored.

*Source: US-001; FS: General data rules (unknown options must not be
accepted).*

### AC-001-13 — Inactive option is rejected

Given the external participant registration form is otherwise valid
And the submission references an option that exists but is inactive
(e.g. it was deactivated after the form was loaded)
When the submission is received
Then the registration is rejected and nothing is stored.

*Source: US-001; BR: Configurable options; FS: General data rules
(inactive options must not be accepted).*

---

## US-002 — Student registration

### AC-002-01 — Valid student registration is accepted

Given the student registration form
And the student has entered a first name, last name, a valid email, a
study institution, a study programme and a student ID
And has given every mandatory consent
When the student submits the form
Then the registration is accepted as a student registration with the
entered values.

*Source: US-002; FS: Student participant; BR: Registration types.*

### AC-002-02 — Missing required student field is rejected

Given the student registration form
And one of the required fields (first name, last name, email, study
institution, study programme, student ID) is empty or whitespace-only
When the student submits the form
Then the registration is rejected
And the student is told which field is missing.

*Source: US-002; FS: General data rules.*

### AC-002-03 — Invalid email format is rejected for students

Given the student registration form
And the email field contains a value that is not a valid email address
When the student submits the form
Then the registration is rejected
And the student is told the email is invalid.

*Source: US-002; FS: General data rules (valid email format).*

### AC-002-04 — Student text values are trimmed and Unicode-preserving

Given the student registration form
And text fields contain Slovenian characters and leading/trailing
whitespace (e.g. study programme `"  Računalništvo in informatika "`)
When the registration is accepted
Then the values are stored without the leading/trailing whitespace and
with every character otherwise preserved.

*Source: US-002; FS: General data rules (whitespace, Unicode).*

### AC-002-05 — Mandatory consent must be given, and is not preselected

Given the student registration form
When the form is first displayed
Then no mandatory consent is preselected
And when the student submits without giving a mandatory consent, the
registration is rejected with a message that the consent is required.

*Source: US-002; FS: Consent. See [D-2].*

### AC-002-06 — Student may select active options

Given the student registration form is otherwise valid
And the student selects one or more active options
When the student submits the form
Then the registration is accepted
And exactly the selected options are recorded with it.

*Source: US-002 (selection of available conference options). See [D-1]
for which options are available to students.*

### AC-002-07 — Unknown or inactive option is rejected for students

Given the student registration form is otherwise valid
And the submission references an option that does not exist or is
inactive
When the submission is received
Then the registration is rejected and nothing is stored.

*Source: US-002; FS: General data rules.*

### AC-002-08 — Registration type determines the required fields

Given a participant submits a registration as one type
When the submission contains only the fields of that type
Then it is validated against that type's required fields only
(an external registration does not require student fields, and a
student registration does not require an organization / institution).

*Source: BR: Participant fields; FS: External participant, Student
participant.*

---

## US-003 — Configurable conference options

### AC-003-01 — Options are grouped by configurable set

Given active options are configured in the sets workshops, events,
meals and other optional activities
When a participant opens either registration form
Then the options are offered grouped under their set, each shown by its
display name.

*Source: US-003; FS: Configurable conference options.*

### AC-003-02 — A newly configured option is offered without changing the application

Given the organizer adds a new active option to one of the sets through
the configuration mechanism
When a participant next opens a registration form
Then the new option is offered and can be selected
And no change to the application itself was required.

*Source: US-003 (reuse when the programme changes); BR: Configurable
options.*

### AC-003-03 — A deactivated option stops being offered and accepted

Given an option that was active is changed to inactive through the
configuration mechanism
When a participant next opens a registration form or submits a
registration selecting it
Then the option is not offered
And a submission selecting it is rejected.

*Source: US-003; BR: Configurable options (only currently active
options may be selected).*

### AC-003-04 — Existing registrations keep their selections after reconfiguration

Given a registration was accepted with an option that is later
deactivated
When the organizer later views or exports registrations
Then that registration still shows the option it was accepted with.

*Source: US-003; US-005 (registrations are not lost).*

### AC-003-05 — Option identifiers are stable

Given an option with a given identifier
When its display name is changed through the configuration mechanism
Then registrations that selected it remain associated with the same
option.

*Source: FS: Configurable conference options (stable identifier).*

---

## US-004 — Registration confirmation

### AC-004-01 — Confirmation is shown after acceptance

Given a participant submits a valid registration (either type)
When the registration is accepted
Then the application shows a clear confirmation that the registration
was received.

*Source: US-004; BR: Successful registration.*

### AC-004-02 — No confirmation for a rejected submission

Given a participant submits a registration that is rejected
When the response is shown
Then no confirmation is shown
And the participant sees the reason(s) for rejection.

*Source: US-004; BR: Successful registration (confirmation only after
acceptance).*

### AC-004-03 — No confirmation when the registration could not be stored

Given a participant submits a valid registration
And the system is unable to store it
When the response is shown
Then no confirmation is shown
And the participant is told the registration did not succeed.

*Source: US-004; US-005; BR: Successful registration.*

---

## US-005 — Reliable registration storage

### AC-005-01 — Accepted registrations are stored durably

Given a registration has been accepted and confirmed
When the system is stopped and started again
Then the registration, including its selected options, is still
present.

*Source: US-005.*

### AC-005-02 — A registration is stored completely or not at all

Given a registration with selected options is submitted
When it is accepted
Then the participant data and all selected options are stored together
And a submission that is rejected leaves no partial registration
behind.

*Source: US-005; BR: Successful registration.*

### AC-005-03 — Registrations can be recovered

Given registrations have been accepted
When the stored registrations are lost or damaged and the documented
recovery procedure is carried out
Then the registrations accepted up to the last recovery point are
restored.

*Source: US-005 (can be recovered if necessary).*

---

## US-006 — Participant email confirmation

### AC-006-01 — Participant receives a confirmation email

Given a registration has been accepted
When processing completes
Then an email confirming the registration is sent to the email address
given in the registration.

*Source: US-006.*

### AC-006-02 — Confirmation email content

Given a confirmation email is sent for an accepted registration
When the participant reads it
Then it states that the registration was received and lists the
participant's name, registration type and selected options
And Unicode characters in those values are shown correctly.

*Source: US-006 (a record of the registration); FS: General data rules
(Unicode).*

### AC-006-03 — No email for a rejected submission

Given a registration submission is rejected
When the response is shown
Then no confirmation email is sent.

*Source: US-006 (email confirms a successful registration).*

### AC-006-04 — Email failure does not undo an accepted registration

Given a registration has been accepted and stored
And the confirmation email cannot be sent
When processing completes
Then the registration remains stored and the participant still sees
the in-application confirmation.

*Source: US-004; US-005; US-006. See [D-3].*

---

## US-007 — Organizer notification

### AC-007-01 — Organizer is notified of a new registration

Given a registration has been accepted
When processing completes
Then the organizer receives a notification identifying the new
registration (participant name, registration type).

*Source: US-007.*

### AC-007-02 — No notification for a rejected submission

Given a registration submission is rejected
When the response is shown
Then no organizer notification is sent.

*Source: US-007 (notified when a new participant registers).*

### AC-007-03 — Notification failure does not undo an accepted registration

Given a registration has been accepted and stored
And the organizer notification cannot be sent
When processing completes
Then the registration remains stored and the participant still sees
the in-application confirmation.

*Source: US-005; US-007. See [D-3].*

---

## US-008 — Registration export

### AC-008-01 — Organizer can export registrations as Excel

Given registrations of both types have been accepted
And the organizer has passed the organizer-level access control
When the organizer requests the export
Then an Excel workbook is returned containing one row per accepted
registration.

*Source: US-008; BR: Organizer access.*

### AC-008-02 — Export contains all registration data

Given the organizer exports registrations
When the workbook is opened
Then each row contains the registration type, every fixed field of
that type, the selected options and when the registration was
accepted
And fields that do not apply to a row's type are left empty.

*Source: US-008; FS: External participant, Student participant.*

### AC-008-03 — Export is current

Given a registration is accepted
When the organizer requests the export afterwards
Then the new registration is included.

*Source: US-008 (the current list of registrations); BR: Organizer
access.*

### AC-008-04 — Export preserves Unicode

Given a registration with Slovenian characters has been accepted
When the organizer opens the exported workbook
Then those characters are shown exactly as entered.

*Source: US-008; FS: General data rules (Unicode).*

### AC-008-05 — Export with no registrations

Given no registrations have been accepted
When the organizer requests the export
Then a valid Excel workbook is returned with the column headings and no
data rows.

*Source: US-008.*

### AC-008-06 — Export is refused without organizer access

Given a requester has not passed the organizer-level access control
(no credentials, or wrong credentials)
When the requester asks for the export
Then access is refused
And no registration data is disclosed.

*Source: US-008; BR: Organizer access (must not be publicly
accessible).*

---

## Traceability summary

| User Story | Criteria |
| --- | --- |
| US-001 | AC-001-01 … AC-001-13 |
| US-002 | AC-002-01 … AC-002-08 |
| US-003 | AC-003-01 … AC-003-05 |
| US-004 | AC-004-01 … AC-004-03 |
| US-005 | AC-005-01 … AC-005-03 |
| US-006 | AC-006-01 … AC-006-04 |
| US-007 | AC-007-01 … AC-007-03 |
| US-008 | AC-008-01 … AC-008-06 |

Total: 45 criteria.

## Open business ambiguities (escalated)

Each is recorded in `docs/decisions-log.md` with the conservative
default this document uses.

- **[D-1]** US-002 says "activities available to students", but no
  input defines option eligibility by registration type. Default: every
  active option is available to both registration types.
- **[D-2]** FS: Consent requires mandatory consents "where required"
  without naming them. Default: one mandatory consent to the processing
  of the participant's personal data for conference registration, on
  both forms; no optional consents.
- **[D-3]** No input says whether a failed participant email or
  organizer notification should undo an accepted registration.
  Default: the registration stays accepted (AC-006-04, AC-007-03).
- **[D-4]** No input limits how many options may be chosen per set, or
  requires any. Default: any number of active options, including none.
- **[D-5]** No input says whether the same email may register more
  than once. Default: no duplicate-registration rule is invented; each
  valid submission is accepted as its own registration.
