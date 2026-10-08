# Acceptance criteria

> Written in: phase 1 · Source: `project/01_requirements/*` · Agent: writes (frozen after phase 3)

## US-001 External participant registration

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-001-01 | BR-01, BR-04, BR-05, BR-06 | the external participant form with first name, last name, email, organization / institution filled, the mandatory consents given and active options selected | the participant submits | the registration is accepted and the confirmation is shown |
| AC-001-02 | BR-01 | the registration page | the participant chooses the external participant form | exactly the fields first name, last name, email and organization / institution are shown, all marked required |
| AC-001-03 | BR-02 | the external form with one required field left empty | the participant submits | the registration is rejected, that field is identified as required, and nothing is stored |
| AC-001-04 | BR-02 | the external form with a required field containing only whitespace, including the no-break space (U+00A0) | the participant submits | the registration is rejected as if the field were empty |
| AC-001-05 | BR-02 | the external form with leading and trailing whitespace, including the no-break space, around the field values | the participant submits | the registration is accepted and the values are stored without that whitespace |
| AC-001-06 | BR-03 | the external form with an email that is not a valid email address | the participant submits | the registration is rejected and the email field is identified as invalid |
| AC-001-07 | BR-03 | the external form with names and organization containing č, š, ž and other Unicode letters | the participant submits | the registration is accepted and the text is stored and shown unchanged |
| AC-001-08 | BR-04 | the configured options | the participant opens the external form | only active options are offered, grouped as workshops, events, meals and other activities |
| AC-001-09 | BR-04 | an otherwise valid external registration that names an inactive option | it is submitted | the registration is rejected and nothing is stored |
| AC-001-10 | BR-04 | an otherwise valid external registration that names an unknown option | it is submitted | the registration is rejected and nothing is stored |
| AC-001-11 | BR-04 (D-14) | an otherwise valid external registration that selects more options in one category than that category's configured maximum | it is submitted | the registration is rejected and nothing is stored |
| AC-001-12 | BR-05 (D-12) | the external form is opened | it is shown | each mandatory consent is shown with its configured wording and is not preselected |
| AC-001-13 | BR-05 | an otherwise valid external registration without a mandatory consent | it is submitted | the registration is rejected, the missing consent is identified, and nothing is stored |
| AC-001-14 | scope (anti-automation) | an otherwise valid external registration whose anti-automation check is missing or fails | it is submitted | the registration is rejected and nothing is stored |
| AC-001-15 | BR-03 (D-15) | an accepted registration with an email address | another registration with the same email address, compared without regard to letter case and surrounding whitespace, is submitted | the second registration is rejected with a message that the email is already registered, and nothing new is stored |

## US-002 Student registration

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-002-01 | BR-01, BR-04, BR-05, BR-06 | the student form with first name, last name, email, study institution, study programme, student ID filled, the mandatory consents given and options available to students selected | the student submits | the registration is accepted and the confirmation is shown |
| AC-002-02 | BR-01 | the registration page | the participant chooses the student form | exactly the fields first name, last name, email, study institution, study programme and student ID are shown, all marked required |
| AC-002-03 | BR-02 | the student form with one required field (including study institution, study programme or student ID) empty or only whitespace, including the no-break space | the student submits | the registration is rejected, that field is identified as required, and nothing is stored |
| AC-002-04 | BR-03 | the student form with an email that is not a valid email address | the student submits | the registration is rejected and the email field is identified as invalid |
| AC-002-05 | BR-03 | the student form with text fields containing č, š, ž | the student submits | the registration is accepted and the text is stored unchanged |
| AC-002-06 | BR-04 (D-11) | an active option configured as not available to students | the student opens the student form | that option is not offered |
| AC-002-07 | BR-04 (D-11) | an otherwise valid student registration that names an option not available to students | it is submitted | the registration is rejected and nothing is stored |
| AC-002-08 | BR-04 | an otherwise valid student registration that names an inactive or unknown option | it is submitted | the registration is rejected and nothing is stored |
| AC-002-09 | BR-05 | an otherwise valid student registration without a mandatory consent | it is submitted | the registration is rejected and nothing is stored |
| AC-002-10 | scope (anti-automation) | an otherwise valid student registration whose anti-automation check is missing or fails | it is submitted | the registration is rejected and nothing is stored |

## US-003 Configurable conference options

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-003-01 | BR-04 | an option configuration with a stable identifier, display name, category and active flag per option | the application starts | the forms offer each active option by display name under its category |
| AC-003-02 | BR-04 | an option is added, renamed or set inactive in the configuration only | the application is restarted | the forms reflect the change without any code change |
| AC-003-03 | BR-01 | the option configuration is changed | the forms are shown | the participant fields of both forms are unchanged |

## US-004 Registration confirmation

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-004-01 | BR-06 | a submitted registration | it is accepted | the application shows a confirmation that the registration was received |
| AC-004-02 | BR-06 | a submitted registration | it is rejected or cannot be processed | no confirmation is shown and an error message without internal details is shown |

## US-005 Reliable registration storage

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-005-01 | BR-07 | a valid registration | it is accepted | it is stored in the database with every submitted field, the selected options and the given consents |
| AC-005-02 | BR-07 | a valid registration | it is accepted | a raw JSON copy of the registration exactly as accepted is written to persistent storage |
| AC-005-03 | BR-06, BR-07 | a valid registration | storing it in the database or writing the raw JSON copy fails | the registration is not accepted, no confirmation is shown, and no partial record remains |
| AC-005-04 | BR-07 | accepted registrations | the application and its containers are restarted | the registrations and their raw JSON copies are still present |
| AC-005-05 | BR-07 (D-16) | a registration and its raw JSON copy older than the configured retention period | the retention period has passed | both are deleted |

## US-006 Participant email confirmation

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-006-01 | BR-07 | a registration | it is accepted and stored | one email confirming the registration is sent to the participant's email address |
| AC-006-02 | BR-06 | a registration | it is rejected | no email is sent to the participant |
| AC-006-03 | BR-07 (D-13) | a registration that was stored | sending the participant email fails | the registration stays accepted and stored, the confirmation is shown, and the failure is logged without personal data |

## US-007 Organizer notification

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-007-01 | BR-07 | a registration | it is accepted and stored | every configured organizer recipient receives an email with the submitted data |
| AC-007-02 | BR-07 | a registration | it is accepted and stored | the organizer email has the raw JSON copy attached, identical to the stored copy |
| AC-007-03 | BR-07 (D-13) | a registration that was stored | sending the organizer email fails | the registration stays accepted and stored, the confirmation is shown, and the failure is logged without personal data |
| AC-007-04 | BR-06 | a registration | it is rejected | no organizer email is sent |

## US-008 Registration export

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-008-01 | BR-08 | stored external and student registrations and a user with organizer access | the organizer requests the export | an Excel workbook is returned with one row per registration, containing the registration type, every participant field, the selected options and the consents |
| AC-008-02 | BR-08 | stored registrations | the export is requested without organizer credentials | it is refused and no registration data is returned |
| AC-008-03 | BR-08 | stored registrations | the export is requested with wrong organizer credentials | it is refused and no registration data is returned |
| AC-008-04 | BR-03, BR-08 | stored registrations with č, š, ž in text fields | the organizer exports | the workbook shows the characters unchanged |
