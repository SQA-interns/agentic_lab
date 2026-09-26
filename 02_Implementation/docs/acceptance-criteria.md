# Acceptance Criteria — Conference Registration System

Derived in Phase 1 from:

- `01_Business/USER_STORIES.md` (US)
- `01_Business/BUSINESS_RULES.md` (BR)
- `01_Business/FORM_SCHEMA.md` (FS)

Format: Given–When–Then. Each criterion describes one observable,
independently testable behaviour. Technical implementation choices are
intentionally excluded; they are decided in the Specification.

Source abbreviations used in the traceability lines:

| Abbreviation | Source section |
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

### AC-001-01 — External registration form shows the fixed fields

- **Given** a participant chooses the external participant registration
- **When** the external participant registration form is displayed
- **Then** it contains input fields for First name, Last name, Email and
  Organization / institution.

Source: US-001; BR-Types; BR-Fields; FS-External.

### AC-001-02 — Successful external registration

- **Given** the external participant registration form
- **And** First name, Last name, Email and Organization / institution are
  filled with valid values
- **And** all mandatory consents are given
- **When** the participant submits the form
- **Then** the registration is accepted as an external participant
  registration.

Source: US-001; BR-Types; FS-External; FS-Consent.

### AC-001-03 — Selecting active conference options

- **Given** the external participant registration form
- **And** active conference options exist
- **When** the participant selects one or more active options and submits
  an otherwise valid registration
- **Then** the registration is accepted
- **And** the selected options are recorded with the registration.

Source: US-001; BR-Options; FS-Options.

### AC-001-04 — Registration without optional options

- **Given** the external participant registration form
- **When** the participant submits an otherwise valid registration without
  selecting any conference option
- **Then** the registration is accepted.

Source: US-001 (options are optional activities); BR-Options.

### AC-001-05 — Missing required external field is rejected

- **Given** the external participant registration form
- **When** the participant submits it with any one of First name, Last
  name, Email or Organization / institution empty
- **Then** the registration is not accepted
- **And** the participant is informed which field is missing
- **And** no registration is stored.

Source: US-001; FS-External; FS-Data (required fields must not be empty).

### AC-001-06 — Whitespace-only required value is treated as empty

- **Given** the external participant registration form
- **When** the participant submits a required field that contains only
  whitespace
- **Then** the registration is not accepted and the field is reported as
  missing.

Source: US-001; FS-Data (required fields; whitespace not significant).

### AC-001-07 — Invalid email format is rejected

- **Given** the external participant registration form
- **When** the participant submits an Email value that is not a valid
  email address format
- **Then** the registration is not accepted
- **And** the participant is informed that the email is invalid
- **And** no registration is stored.

Source: US-001; FS-Data (valid email format).

### AC-001-08 — Leading and trailing whitespace is not significant

- **Given** the external participant registration form
- **When** the participant submits otherwise valid values that contain
  leading or trailing whitespace
- **Then** the registration is accepted
- **And** the values are recorded without the leading and trailing
  whitespace.

Source: US-001; FS-Data (whitespace not significant).

### AC-001-09 — Unicode / Slovenian characters are preserved

- **Given** the external participant registration form
- **When** the participant submits values containing Slovenian characters
  (for example `Čeč`, `Šušteršič`, `Žiga`, `Univerza v Ljubljani — FRI`)
- **Then** the registration is accepted
- **And** the values are recorded and later reproduced exactly as entered
  (apart from trimmed outer whitespace).

Source: US-001; FS-Data (Unicode support).

### AC-001-10 — Unknown option is rejected

- **Given** a registration submission for an external participant
- **When** it references a conference option identifier that does not
  exist in the current configuration
- **Then** the registration is not accepted
- **And** no registration is stored.

Source: US-001; BR-Options; FS-Data (unknown options not accepted).

### AC-001-11 — Inactive option is rejected

- **Given** a conference option that exists but is inactive
- **When** an external participant registration submission references
  that option
- **Then** the registration is not accepted
- **And** no registration is stored.

Source: US-001; BR-Options (only active options may be selected);
FS-Data (inactive options not accepted).

### AC-001-12 — Mandatory consent is not preselected

- **Given** the external participant registration form is displayed
- **When** the participant has not interacted with it
- **Then** every mandatory consent field is unselected.

Source: US-001; FS-Consent.

### AC-001-13 — Missing mandatory consent is rejected

- **Given** the external participant registration form with all fixed
  fields valid
- **When** the participant submits without giving a mandatory consent
- **Then** the registration is not accepted
- **And** the participant is informed that the consent is required
- **And** no registration is stored.

Source: US-001; FS-Consent.

### AC-001-14 — No account, login or payment step

- **Given** the running system
- **When** a participant (external or student) registers
- **Then** no participant account, login or payment step is required
- **And** no function for editing an already submitted registration is
  offered.

Source: US-001 and US-002 (out of scope: accounts, payment, editing);
BR-Scope.

---

## US-002 — Student registration

### AC-002-01 — Student registration form shows the fixed fields

- **Given** a participant chooses the student registration
- **When** the student registration form is displayed
- **Then** it contains input fields for First name, Last name, Email,
  Study institution, Study programme and Student ID.

Source: US-002; BR-Types; BR-Fields; FS-Student.

### AC-002-02 — Successful student registration

- **Given** the student registration form
- **And** First name, Last name, Email, Study institution, Study programme
  and Student ID are filled with valid values
- **And** all mandatory consents are given
- **When** the student submits the form
- **Then** the registration is accepted as a student registration.

Source: US-002; BR-Types; FS-Student; FS-Consent.

### AC-002-03 — Student selects active conference options

- **Given** the student registration form
- **And** active conference options exist
- **When** the student selects one or more active options and submits an
  otherwise valid registration
- **Then** the registration is accepted
- **And** the selected options are recorded with the registration.

Source: US-002; BR-Options; FS-Options.

### AC-002-04 — Missing required student field is rejected

- **Given** the student registration form
- **When** the student submits it with any one of First name, Last name,
  Email, Study institution, Study programme or Student ID empty
  (including whitespace-only)
- **Then** the registration is not accepted
- **And** the student is informed which field is missing
- **And** no registration is stored.

Source: US-002; FS-Student; FS-Data.

### AC-002-05 — Invalid student email is rejected

- **Given** the student registration form
- **When** the student submits an Email value that is not a valid email
  address format
- **Then** the registration is not accepted
- **And** no registration is stored.

Source: US-002; FS-Data.

### AC-002-06 — Unknown or inactive option is rejected for students

- **Given** a student registration submission
- **When** it references an unknown or inactive conference option
- **Then** the registration is not accepted
- **And** no registration is stored.

Source: US-002; BR-Options; FS-Data.

### AC-002-07 — Missing mandatory consent is rejected for students

- **Given** the student registration form with all fixed fields valid
- **When** the student submits without giving a mandatory consent
- **Then** the registration is not accepted
- **And** no registration is stored.

Source: US-002; FS-Consent.

### AC-002-08 — Student-only fields are required only for students

- **Given** the external participant registration form
- **When** an external participant submits a valid registration
- **Then** Study institution, Study programme and Student ID are not
  requested or required
- **And** conversely, a student registration does not require
  Organization / institution.

Source: US-001; US-002; BR-Fields; FS-External; FS-Student.

### AC-002-09 — Student status is not externally verified

- **Given** a student registration with a syntactically non-empty Student
  ID
- **When** the student submits an otherwise valid registration
- **Then** the registration is accepted without any external verification
  of student status.

Source: US-002 (out of scope: external verification of student status).

---

## US-003 — Configurable conference options

### AC-003-01 — Active options are offered in the forms

- **Given** a conference configuration containing active options in the
  categories Workshops, Events, Meals and Other optional activities
- **When** a registration form is displayed
- **Then** every active option is offered for selection under its
  category, identified by its display name.

Source: US-003; BR-Options; FS-Options.

### AC-003-02 — Inactive options are not offered

- **Given** a conference configuration containing an inactive option
- **When** a registration form is displayed
- **Then** the inactive option is not offered for selection.

Source: US-003; BR-Options.

### AC-003-03 — Options change through configuration only

- **Given** the conference configuration is changed (an option is added,
  renamed, activated or deactivated)
- **When** the system is started with the changed configuration and a
  registration form is displayed
- **Then** the offered options reflect the changed configuration
- **And** no change to the application code is required.

Source: US-003; BR-Options (options may change between conferences).

### AC-003-04 — Each option has a stable identifier, display name and status

- **Given** the conference configuration
- **When** the configured options are inspected
- **Then** each option has a stable identifier, a display name and an
  active/inactive status.

Source: US-003; FS-Options.

### AC-003-05 — Fixed participant fields are not affected by configuration

- **Given** the conference option configuration is changed
- **When** a registration form is displayed
- **Then** the fixed participant fields of each registration type remain
  exactly as defined for that type.

Source: US-003; BR-Fields.

---

## US-004 — Registration confirmation

### AC-004-01 — Confirmation after successful registration

- **Given** a participant submits a valid registration
- **When** the registration has been successfully accepted
- **Then** the participant sees a clear in-application confirmation that
  the registration was received.

Source: US-004; BR-Success.

### AC-004-02 — No confirmation for rejected registration

- **Given** a participant submits an invalid registration
- **When** the registration is not accepted
- **Then** no success confirmation is shown
- **And** the participant sees the reason(s) for rejection and can
  correct the input.

Source: US-004; BR-Success.

### AC-004-03 — No confirmation when processing fails

- **Given** a participant submits a valid registration
- **When** the registration cannot be accepted because processing fails
  (for example, the registration could not be stored)
- **Then** no success confirmation is shown
- **And** the participant is informed that the registration was not
  completed.

Source: US-004; BR-Success (confirmation only after successful
acceptance); US-005.

---

## US-005 — Reliable registration storage

### AC-005-01 — Accepted registration is stored

- **Given** a registration has been accepted
- **When** the stored registrations are retrieved
- **Then** the registration is present with all its submitted participant
  data, registration type, consents and selected options.

Source: US-005.

### AC-005-02 — Stored registrations survive a restart

- **Given** a registration has been accepted
- **When** the system is restarted
- **Then** the registration is still present.

Source: US-005 (registrations are not lost).

### AC-005-03 — Rejected registration is not stored

- **Given** a registration submission is rejected
- **When** the stored registrations are retrieved
- **Then** no record of the rejected submission is present.

Source: US-005; BR-Success.

### AC-005-04 — Registrations can be recovered

- **Given** registrations have been stored
- **When** recovery of the registration data is needed
- **Then** the stored registrations can be recovered from a backup of the
  registration data.

Source: US-005 (can be recovered if necessary).

---

## US-006 — Participant email confirmation

### AC-006-01 — Confirmation email to the participant

- **Given** a registration has been successfully accepted
- **When** registration processing completes
- **Then** an email confirming the registration is sent to the email
  address given in the registration.

Source: US-006.

### AC-006-02 — No confirmation email for rejected registration

- **Given** a registration submission is rejected
- **When** processing ends
- **Then** no confirmation email is sent.

Source: US-006 (email confirms successful registration only).

### AC-006-03 — Confirmation email content

- **Given** a confirmation email has been sent
- **When** the participant reads it
- **Then** it identifies the registration by the participant's name and
  registration type and lists the selected conference options.

Source: US-006 (record of the registration).

---

## US-007 — Organizer notification

### AC-007-01 — Organizer is notified about a new registration

- **Given** a registration has been successfully accepted
- **When** registration processing completes
- **Then** the organizer receives a notification about the new
  registration.

Source: US-007.

### AC-007-02 — No organizer notification for rejected registration

- **Given** a registration submission is rejected
- **When** processing ends
- **Then** no organizer notification is sent.

Source: US-007.

### AC-007-03 — Notification identifies the new registration

- **Given** an organizer notification has been sent
- **When** the organizer reads it
- **Then** it identifies the participant and the registration type.

Source: US-007 (monitor incoming registrations).

---

## US-008 — Registration export

### AC-008-01 — Organizer exports registrations in Excel format

- **Given** stored registrations exist
- **And** the requester is authorized as an organizer
- **When** the organizer requests the registration export
- **Then** an Excel-format file is returned containing one row per stored
  registration.

Source: US-008; BR-Organizer.

### AC-008-02 — Export contains the current data

- **Given** a new registration was accepted after a previous export
- **When** the organizer requests the export again
- **Then** the new registration is included.

Source: US-008 (current list); BR-Organizer.

### AC-008-03 — Export columns cover both registration types and options

- **Given** stored external and student registrations with selected
  options
- **When** the organizer exports the registrations
- **Then** each row shows the registration type, the fixed fields of that
  registration type, the consents given and the selected options.

Source: US-008; FS-External; FS-Student; FS-Options.

### AC-008-04 — Unicode is preserved in the export

- **Given** a stored registration containing Slovenian characters
- **When** the organizer exports the registrations
- **Then** the exported values contain exactly the same characters.

Source: US-008; FS-Data (Unicode).

### AC-008-05 — Export with no registrations

- **Given** no registrations are stored
- **When** the organizer requests the export
- **Then** a valid Excel-format file with column headings and no data rows
  is returned.

Source: US-008 (edge case: empty list).

### AC-008-06 — Export is denied without organizer authorization

- **Given** a requester without organizer-level authorization
- **When** the requester requests the registration export
- **Then** the export is refused
- **And** no registration data is disclosed.

Source: US-008; BR-Organizer (not publicly accessible).

### AC-008-07 — Export is denied with wrong organizer credentials

- **Given** a requester presenting invalid organizer credentials
- **When** the requester requests the registration export
- **Then** the export is refused
- **And** no registration data is disclosed.

Source: US-008; BR-Organizer.
