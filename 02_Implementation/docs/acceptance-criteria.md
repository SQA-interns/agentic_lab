# Acceptance Criteria — Conference Registration System

Derived in Phase 1 from:

- `01_Business/USER_STORIES.md` (US)
- `01_Business/BUSINESS_RULES.md` (BR)
- `01_Business/FORM_SCHEMA.md` (FS)

Format: Given–When–Then. Each criterion describes one observable,
independently testable behaviour. No technical implementation decisions
are made here; those belong to the Specification.

Source abbreviations used in traceability lines:

| Code | Source |
| --- | --- |
| BR-Types | BUSINESS_RULES — Registration types |
| BR-Fields | BUSINESS_RULES — Participant fields |
| BR-Options | BUSINESS_RULES — Configurable options |
| BR-Success | BUSINESS_RULES — Successful registration |
| BR-Organizer | BUSINESS_RULES — Organizer access |
| BR-Scope | BUSINESS_RULES — Scope boundaries |
| FS-External | FORM_SCHEMA — External participant |
| FS-Student | FORM_SCHEMA — Student participant |
| FS-Options | FORM_SCHEMA — Configurable conference options |
| FS-Consent | FORM_SCHEMA — Consent |
| FS-Data | FORM_SCHEMA — General data rules |

---

## US-001 — External participant registration

### AC-001-01 — Successful external registration

- **Given** the external participant registration form is displayed
- **When** the participant enters a non-empty first name, last name,
  valid email and organization / institution, gives every mandatory
  consent and submits the form
- **Then** the registration is accepted as an external participant
  registration

Trace: US-001; BR-Types; FS-External.

### AC-001-02 — External form presents the fixed external fields

- **Given** a participant opens the external participant registration
  form
- **When** the form is displayed
- **Then** it contains the fields First name, Last name, Email and
  Organization / institution

Trace: US-001; BR-Fields; FS-External.

### AC-001-03 — Missing required external field is rejected

- **Given** the external participant registration form
- **When** the participant submits it with any one of First name, Last
  name, Email or Organization / institution empty
- **Then** the registration is not accepted and the participant is
  informed which field must be filled in

Trace: US-001; FS-External; FS-Data (required fields must not be empty).

### AC-001-04 — Whitespace-only required external field is rejected

- **Given** the external participant registration form
- **When** the participant submits a required field that contains only
  whitespace
- **Then** the registration is not accepted and the field is treated as
  empty

Trace: US-001; FS-Data (required fields must not be empty; leading and
trailing whitespace not significant).

### AC-001-05 — Invalid external email is rejected

- **Given** the external participant registration form
- **When** the participant submits an email that does not have a valid
  email format (for example `ana.novak`, `ana@`, `@example.com`)
- **Then** the registration is not accepted and the participant is
  informed that the email is invalid

Trace: US-001; FS-Data (valid email format).

### AC-001-06 — Selection of available conference options

- **Given** active conference options exist
- **When** the external participant selects one or more active options
  and submits an otherwise valid registration
- **Then** the registration is accepted and records exactly the selected
  options

Trace: US-001 (selection of available conference options); BR-Options;
FS-Options.

### AC-001-07 — Registration without optional selections

- **Given** active conference options exist
- **When** the external participant submits an otherwise valid
  registration without selecting any optional conference option
- **Then** the registration is accepted with no options recorded

Trace: US-001; BR-Options (options are optional activities).

### AC-001-08 — Missing mandatory consent is rejected

- **Given** the external participant registration form contains a
  mandatory consent
- **When** the participant submits an otherwise valid registration
  without giving that consent
- **Then** the registration is not accepted and the participant is
  informed that the consent is required

Trace: US-001; FS-Consent.

---

## US-002 — Student registration

### AC-002-01 — Successful student registration

- **Given** the student registration form is displayed
- **When** the student enters a non-empty first name, last name, valid
  email, study institution, study programme and student ID, gives every
  mandatory consent and submits the form
- **Then** the registration is accepted as a student registration

Trace: US-002; BR-Types; FS-Student.

### AC-002-02 — Student form presents the fixed student fields

- **Given** a participant opens the student registration form
- **When** the form is displayed
- **Then** it contains the fields First name, Last name, Email, Study
  institution, Study programme and Student ID

Trace: US-002; BR-Fields; FS-Student.

### AC-002-03 — Missing required student field is rejected

- **Given** the student registration form
- **When** the student submits it with any one of First name, Last name,
  Email, Study institution, Study programme or Student ID empty or
  whitespace-only
- **Then** the registration is not accepted and the student is informed
  which field must be filled in

Trace: US-002; FS-Student; FS-Data (required fields must not be empty;
whitespace not significant).

### AC-002-04 — Invalid student email is rejected

- **Given** the student registration form
- **When** the student submits an email that does not have a valid email
  format
- **Then** the registration is not accepted and the student is informed
  that the email is invalid

Trace: US-002; FS-Data (valid email format).

### AC-002-05 — Student selection of available conference options

- **Given** active conference options exist
- **When** the student selects one or more active options and submits an
  otherwise valid registration
- **Then** the registration is accepted and records exactly the selected
  options

Trace: US-002 (selection of available conference options); BR-Options;
FS-Options.

### AC-002-06 — Missing mandatory consent is rejected for students

- **Given** the student registration form contains a mandatory consent
- **When** the student submits an otherwise valid registration without
  giving that consent
- **Then** the registration is not accepted and the student is informed
  that the consent is required

Trace: US-002; FS-Consent.

### AC-002-07 — No external verification of student status

- **Given** the student registration form
- **When** the student submits a valid registration with any non-empty
  student ID
- **Then** the registration is accepted without any external verification
  of student status

Trace: US-002 (out of scope: external verification of student status).

---

## Shared data rules (applicable to both registration types)

The following criteria are traced to US-001 and US-002 jointly because
FORM_SCHEMA general data rules apply to both forms. They are numbered
under US-001 and US-002 respectively to keep numbering per User Story.

### AC-001-09 — Leading and trailing whitespace is not significant

- **Given** a registration form (external or student)
- **When** the participant submits text values with leading or trailing
  whitespace (for example `"  Ana  "`)
- **Then** the registration is accepted and the values are recorded
  without the leading and trailing whitespace (`"Ana"`)

Trace: US-001, US-002; FS-Data (leading and trailing whitespace must not
be significant).

### AC-001-10 — Unicode text including Slovenian characters is preserved

- **Given** a registration form (external or student)
- **When** the participant submits text values containing Unicode
  characters, including Slovenian characters (for example `Žiga`,
  `Čeh`, `Šolski center`, `Univerza v Ljubljani – FRI`)
- **Then** the registration is accepted and the values are recorded and
  subsequently shown exactly as entered (apart from trimmed whitespace)

Trace: US-001, US-002; FS-Data (Unicode, Slovenian characters).

### AC-002-08 — Unknown option is rejected

- **Given** a registration (external or student)
- **When** it is submitted with an option identifier that does not exist
  in the conference configuration
- **Then** the registration is not accepted and nothing is recorded

Trace: US-001, US-002, US-003; BR-Options; FS-Data (unknown options must
not be accepted).

### AC-002-09 — Inactive option is rejected

- **Given** a conference option exists but is inactive
- **When** a registration (external or student) is submitted selecting
  that option
- **Then** the registration is not accepted and nothing is recorded

Trace: US-001, US-002, US-003; BR-Options (only currently active options
may be selected); FS-Data (inactive options must not be accepted).

### AC-002-10 — Mandatory consent is not preselected

- **Given** a registration form (external or student) with a mandatory
  consent
- **When** the form is first displayed
- **Then** the mandatory consent is not selected

Trace: US-001, US-002; FS-Consent (mandatory consent must not be
preselected).

---

## US-003 — Configurable conference options

### AC-003-01 — Only active options are offered

- **Given** the conference configuration contains active and inactive
  options
- **When** a participant opens a registration form
- **Then** only the active options are offered for selection

Trace: US-003; BR-Options; FS-Options.

### AC-003-02 — Options are grouped by category

- **Given** the conference configuration contains options in the
  categories Workshops, Events, Meals and Other optional conference
  activities
- **When** a participant opens a registration form
- **Then** the active options are offered under their respective
  category, each shown by its display name

Trace: US-003; FS-Options (workshops, events, meals, other optional
conference activities; display name).

### AC-003-03 — Options change without changing fixed fields or code

- **Given** the organizer changes the conference configuration (adds an
  option, removes an option, or changes an option between active and
  inactive)
- **When** a participant subsequently opens a registration form
- **Then** the offered options reflect the changed configuration, the
  fixed participant fields are unchanged, and no change to application
  code was required

Trace: US-003 (reuse when the programme changes); BR-Options; BR-Fields
(fixed fields do not change through configuration).

### AC-003-04 — Every option has identifier, display name and status

- **Given** the conference configuration
- **When** an option is defined
- **Then** it has a stable identifier, a display name and an
  active/inactive status, and an option lacking any of these is not
  offered for selection

Trace: US-003; FS-Options (minimum option attributes).

### AC-003-05 — Stable identifier survives display-name change

- **Given** a registration was accepted with an option selected
- **When** the organizer later changes the display name of that option
- **Then** the stored registration still refers to the same option by
  its stable identifier

Trace: US-003; FS-Options (stable identifier).

---

## US-004 — Registration confirmation

### AC-004-01 — Confirmation after successful registration

- **Given** a participant submits a valid registration (external or
  student)
- **When** the registration has been successfully accepted
- **Then** the application displays a clear confirmation that the
  registration was received

Trace: US-004; BR-Success.

### AC-004-02 — No confirmation for rejected registration

- **Given** a participant submits an invalid registration
- **When** the registration is not accepted
- **Then** no confirmation is displayed and the participant sees what
  must be corrected, with the previously entered values kept

Trace: US-004; BR-Success (confirmation only after successful
acceptance).

### AC-004-03 — No confirmation when the registration cannot be accepted

- **Given** a participant submits a valid registration
- **When** the system is unable to accept it (for example because it
  cannot be stored)
- **Then** no confirmation is displayed and the participant is informed
  that the registration was not completed

Trace: US-004; US-005; BR-Success.

---

## US-005 — Reliable registration storage

### AC-005-01 — Accepted registration is stored

- **Given** a registration has been accepted and confirmed
- **When** the organizer obtains the current list of registrations
- **Then** the registration is present with all submitted participant
  fields, its registration type, consents and selected options

Trace: US-005; BR-Success; BR-Organizer.

### AC-005-02 — Stored registrations survive a restart

- **Given** registrations have been accepted
- **When** the system is restarted
- **Then** all previously accepted registrations are still available

Trace: US-005 (not lost, recoverable).

### AC-005-03 — Rejected registrations are not stored

- **Given** a participant submits an invalid registration
- **When** the registration is rejected
- **Then** no registration record is stored for that submission

Trace: US-005; BR-Success.

### AC-005-04 — Confirmation implies durable storage

- **Given** a participant has received a confirmation
- **When** the organizer obtains the current list of registrations at
  any later time
- **Then** the confirmed registration is included

Trace: US-005; US-004; BR-Success.

---

## US-006 — Participant email confirmation

### AC-006-01 — Participant receives confirmation email

- **Given** a registration (external or student) has been successfully
  accepted
- **When** registration processing completes
- **Then** an email confirming the successful registration is sent to
  the email address provided in the registration

Trace: US-006; BR-Success.

### AC-006-02 — No confirmation email for rejected registration

- **Given** a registration is rejected
- **When** processing completes
- **Then** no confirmation email is sent

Trace: US-006 (email confirms *successful* registration).

---

## US-007 — Organizer notification

### AC-007-01 — Organizer is notified of a new registration

- **Given** a registration (external or student) has been successfully
  accepted
- **When** registration processing completes
- **Then** the organizer receives a notification about the new
  registration

Trace: US-007.

### AC-007-02 — No organizer notification for rejected registration

- **Given** a registration is rejected
- **When** processing completes
- **Then** no organizer notification is sent

Trace: US-007 (notification concerns a new participant registration).

---

## US-008 — Registration export

### AC-008-01 — Organizer exports registrations in Excel format

- **Given** registrations have been accepted
- **When** an organizer with organizer-level access requests the export
- **Then** the organizer receives an Excel file containing every current
  registration

Trace: US-008; BR-Organizer.

### AC-008-02 — Export contains participant data, type and options

- **Given** external and student registrations with selected options
  exist
- **When** the organizer exports the registrations
- **Then** each row identifies the registration type, all fixed
  participant fields of that type, and the selected options

Trace: US-008; BR-Organizer; FS-External; FS-Student; FS-Options.

### AC-008-03 — Export preserves Unicode

- **Given** a registration containing Slovenian characters has been
  accepted
- **When** the organizer exports the registrations
- **Then** the Excel file shows those characters correctly

Trace: US-008; FS-Data (Unicode).

### AC-008-04 — Export without organizer access is denied

- **Given** a requester without organizer-level access
- **When** the requester requests the registration export
- **Then** the export is refused and no registration data is disclosed

Trace: US-008; BR-Organizer (must not be publicly accessible).

### AC-008-05 — Export reflects the current list

- **Given** the organizer has exported registrations
- **When** a new registration is accepted and the organizer exports again
- **Then** the new export includes the new registration

Trace: US-008 (current list of registrations); BR-Organizer.

### AC-008-06 — Export with no registrations

- **Given** no registrations have been accepted
- **When** the organizer requests the export
- **Then** the organizer receives a valid Excel file that contains no
  registration rows

Trace: US-008 (edge case of the current list being empty).

---

## Scope check

No criterion introduces participant accounts, payment processing,
editing of submitted registrations, an administrative configuration UI,
a full administrative dashboard or identity-provider integration
(BR-Scope; US-001/US-002/US-003 out-of-scope items).

## Traceability summary

| User Story | Criteria |
| --- | --- |
| US-001 | AC-001-01 … AC-001-10 |
| US-002 | AC-002-01 … AC-002-10 |
| US-003 | AC-003-01 … AC-003-05 |
| US-004 | AC-004-01 … AC-004-03 |
| US-005 | AC-005-01 … AC-005-04 |
| US-006 | AC-006-01, AC-006-02 |
| US-007 | AC-007-01, AC-007-02 |
| US-008 | AC-008-01 … AC-008-06 |
