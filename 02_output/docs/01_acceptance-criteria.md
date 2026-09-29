# Acceptance criteria

> Written in: phase 1 · Source: `project/02_business/*` · Agent: writes (frozen after phase 3)

Each criterion is one observable behaviour (Given/When/Then) and cites its story, rules and, where an open question shaped it, the decision that answered it (D-09 … D-14, pending review). "Nothing stored" means: no database row, no JSON copy file and no email.

## Open questions

| OQ | Status | Decision |
|---|---|---|
| OQ-01 options per registration type | unanswered | D-09: every active option is offered to both types unless its configuration restricts it to one type |
| OQ-02 mandatory consents | unanswered | D-10: consents come from configuration; the shipped configuration has one mandatory data-processing consent |
| OQ-03 storage succeeds, email fails | unanswered | D-11: registration stays accepted; the failed email is recorded and retried |
| OQ-04 options per category | unanswered | D-12: any number of distinct options per category, unless the configuration sets a per-category maximum |
| OQ-05 second registration with the same email | unanswered | D-13: allowed; each is stored separately |
| OQ-06 retention | unanswered | D-14: kept until deleted by the operator; proposed period 12 months after the conference, deletion is manual |

## US-001 External participant registration

| AC | Given / When / Then | Rules |
|---|---|---|
| AC-001-01 | Given active options exist, when an external participant submits first name, last name, email, organization, every mandatory consent and a selection of active options, then the registration is accepted. | BR-01, BR-04 |
| AC-001-02 | Given the external form, when any required field (first name, last name, email, organization) is empty or only whitespace, then the registration is rejected with an error for that field and nothing is stored. | BR-01, BR-02 |
| AC-001-03 | Given the external form, when the email is not a valid email address, then the registration is rejected with an error for the email field and nothing is stored. | BR-03 |
| AC-001-04 | Given a submission whose text fields have leading or trailing whitespace, when it is accepted, then the stored values have that whitespace removed. | BR-02 |
| AC-001-05 | Given names and organization containing č, š, ž (and other Unicode letters), when the registration is accepted, then the stored values equal the submitted values. | BR-03, NFR-01 |
| AC-001-06 | Given a submission that selects an unknown or an inactive option, when it is submitted, then it is rejected with an error for the options and nothing is stored. | BR-04, SR-04 |
| AC-001-07 | Given a submission in which a mandatory consent is not given, when it is submitted, then it is rejected with an error for that consent and nothing is stored. | BR-05, D-10 |
| AC-001-08 | Given the registration form is opened, then every consent checkbox is unchecked. | BR-05, SB-14 |

## US-002 Student registration

| AC | Given / When / Then | Rules |
|---|---|---|
| AC-002-01 | Given active options exist, when a student submits first name, last name, email, study institution, study programme, student ID, every mandatory consent and a selection of active options, then the registration is accepted. | BR-01, BR-04 |
| AC-002-02 | Given the student form, when any required field (first name, last name, email, study institution, study programme, student ID) is empty or only whitespace, then the registration is rejected with an error for that field and nothing is stored. | BR-01, BR-02 |
| AC-002-03 | Given the form, when the participant chooses "student", then the student fields are shown and the organization field is not; when they choose "external participant", the opposite holds. | BR-01 |

## US-003 Configurable conference options

| AC | Given / When / Then | Rules |
|---|---|---|
| AC-003-01 | Given a configuration with active and inactive options, when the options are requested, then only the active options are returned, each with identifier, display name and category (workshop, event, meal, other). | BR-04 |
| AC-003-02 | Given the configuration is changed (an option added, an option made inactive) and the backend restarted, when the options are requested, then the change is visible, without a code change. | AR-04 |
| AC-003-03 | Given the form is opened, then the active options are shown grouped by category. | BR-04 |
| AC-003-04 | Given an option configured for only one registration type, when the other type requests the options or submits a registration selecting it, then it is not offered and the submission is rejected with nothing stored. | BR-04, D-09 |
| AC-003-05 | Given a category with a configured maximum, when a submission selects more options of that category than the maximum, or selects the same option twice, then it is rejected and nothing is stored. | BR-04, D-12 |

## US-004 Registration confirmation

| AC | Given / When / Then | Rules |
|---|---|---|
| AC-004-01 | Given a valid submission, when the backend accepts it, then the application shows a confirmation with the registration reference and the selected options. | BR-06 |
| AC-004-02 | Given a submission the backend rejects, when the response arrives, then no confirmation is shown and each field error is shown next to its field. | BR-06, NFR-03 |
| AC-004-03 | Given a required field is empty or the email is invalid, when the participant tries to submit, then the form shows the error next to the field and sends no request. | NFR-03 |

## US-005 Reliable registration storage

| AC | Given / When / Then | Rules |
|---|---|---|
| AC-005-01 | Given an accepted registration, then the database contains it with its type, every field, the selected options and each consent given with its timestamp. | BR-07 |
| AC-005-02 | Given an accepted registration, then the JSON copy directory contains one file for it whose content is the registration as accepted (same reference, fields, options, consents). | BR-07 |
| AC-005-03 | Given the JSON copy cannot be written, when a valid registration is submitted, then the response is an error, no confirmation is shown, and no database row remains. | BR-06, BR-07, AR-05 |

## US-006 Participant email confirmation

| AC | Given / When / Then | Rules |
|---|---|---|
| AC-006-01 | Given an accepted registration, then one email is sent to the participant's address containing the conference name, the participant's name, the registration reference and the selected options. | US-006 |
| AC-006-02 | Given text fields containing markup or line breaks (for example `<b>x</b>` or `Ana\r\nBcc: x@example.com`), when the registration is processed, then the emails contain the text literally or it is rejected, and no additional header or recipient appears. | SR-05 |
| AC-006-03 | Given the mail server is unavailable, when a valid registration is submitted, then the registration is still accepted and stored, and the email is sent once the mail server is available again. | BR-07, D-11 |

## US-007 Organizer notification

| AC | Given / When / Then | Rules |
|---|---|---|
| AC-007-01 | Given an accepted registration, then every configured organizer address receives an email with the submitted data and a JSON attachment identical to the stored JSON copy. | US-007, BR-07 |
| AC-007-02 | Given an accepted registration, then the organizer email and its attachment contain only registration data (no database identifiers, email delivery status or other system data). | SR-07 |

## US-008 Registration export

| AC | Given / When / Then | Rules |
|---|---|---|
| AC-008-01 | Given registrations exist, when an organizer requests the export with valid organizer credentials, then an Excel workbook is returned with one row per registration and columns for type, every field, selected options, consents and submission time. | BR-08 |
| AC-008-02 | Given no credentials or wrong credentials, when the export is requested, then it is refused (401) and no registration data is returned. | BR-08, SB-02 |
| AC-008-03 | Given registrations with č, š, ž, when exported, then the workbook contains the values unchanged. | NFR-01 |
| AC-008-04 | Given registrations exist, when exported, then the workbook contains only registration data (no database identifiers or email delivery status). | SR-07 |

## Traceability

| Story | AC |
|---|---|
| US-001 | AC-001-01 … AC-001-08 |
| US-002 | AC-002-01 … AC-002-03 |
| US-003 | AC-003-01 … AC-003-05 |
| US-004 | AC-004-01 … AC-004-03 |
| US-005 | AC-005-01 … AC-005-03 |
| US-006 | AC-006-01 … AC-006-03 |
| US-007 | AC-007-01, AC-007-02 |
| US-008 | AC-008-01 … AC-008-04 |
