# Acceptance criteria

> Written in: phase 1 · Source: `project/01_requirements/*` · Agent: writes (frozen after phase 3)

## US-001 External participant registration

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-001-01 | BR-01, BR-04, BR-05 | the external participant form | first name, last name, email, organization/institution are filled, active options are selected, the mandatory consent is given and the anti-automation check passes, and the form is submitted | the registration is accepted |
| AC-001-02 | BR-01, BR-02 | the external participant form | it is submitted with any one of the four required fields empty | the registration is rejected and that field is identified as required |
| AC-001-03 | BR-02 | the external participant form | a required field contains only whitespace, including the no-break space (KP-03) | the registration is rejected and that field is identified as required |
| AC-001-04 | BR-02 | an accepted external registration whose fields had leading and trailing whitespace (including no-break spaces) | the stored registration is read | every field is stored without the leading and trailing whitespace |
| AC-001-05 | BR-03 | the external participant form | the email is not a valid email address | the registration is rejected and the email field is identified as invalid |
| AC-001-06 | BR-03 | the external participant form | names and organization contain Slovenian characters (č, š, ž, Č, Š, Ž) and the form is submitted | the registration is accepted and the characters are stored unchanged |
| AC-001-07 | BR-04 | an option that is configured but inactive | a registration selecting it is submitted | the registration is rejected and nothing is stored |
| AC-001-08 | BR-04 | an option identifier that is not configured | a registration selecting it is submitted | the registration is rejected and nothing is stored |
| AC-001-09 | BR-05 | the external participant form | it is submitted without the mandatory consent | the registration is rejected and the consent is identified as required |
| AC-001-10 | BR-05 | a participant opens the external participant form | the form is displayed | the mandatory consent is shown with its wording and is not preselected |
| AC-001-11 | BR-04 | active and inactive options in all four categories | the external participant form is displayed | only active options are shown, grouped as workshops, events, meals and other activities, by display name |
| AC-001-12 | BR-04 | the external participant form | it is submitted with no option selected | the registration is accepted |
| AC-001-13 | BR-04 | several active options in one category (D-15) | all of them are selected and the form is submitted | the registration is accepted with all selected options |
| AC-001-14 | — | the external participant form | the anti-automation check is missing or fails | the registration is rejected and nothing is stored |
| AC-001-15 | — | a stored registration with an email address (D-16) | another registration with the same address, differing only in letter case or surrounding whitespace, is submitted | it is rejected with a message to contact the organizers, and nothing new is stored |

## US-002 Student registration

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-002-01 | BR-01, BR-04, BR-05 | the student form | first name, last name, email, study institution, study programme, student ID are filled, active options offered to students are selected, the mandatory consent is given and the anti-automation check passes, and the form is submitted | the registration is accepted as a student registration |
| AC-002-02 | BR-01, BR-02 | the student form | it is submitted with any one of the six required fields empty | the registration is rejected and that field is identified as required |
| AC-002-03 | BR-02 | the student form | the student ID or study programme contains only whitespace, including the no-break space | the registration is rejected and that field is identified as required |
| AC-002-04 | BR-03 | the student form | the email is not a valid email address | the registration is rejected and the email field is identified as invalid |
| AC-002-05 | BR-04 | an active option not offered to students (D-12) | a student registration selecting it is submitted | the registration is rejected and nothing is stored |
| AC-002-06 | BR-04 | an active option not offered to students (D-12) | the student form is displayed | that option is not shown |
| AC-002-07 | BR-04 | an inactive option | a student registration selecting it is submitted | the registration is rejected and nothing is stored |
| AC-002-08 | BR-05 | the student form | it is submitted without the mandatory consent | the registration is rejected; the consent is shown not preselected when the form opens |
| AC-002-09 | BR-01 | a participant on the registration page | they choose the student type | the student fields are shown instead of the organization/institution field |

## US-003 Configurable conference options

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-003-01 | BR-04 | a new option is added to the option configuration | the system is restarted, without a code change | the option is shown in its category and can be selected |
| AC-003-02 | BR-04 | an active option is marked inactive in the configuration | the system is restarted | the option is no longer shown and a registration selecting it is rejected |
| AC-003-03 | BR-01 | the option configuration is changed | either form is displayed or submitted | the participant fields of both forms are unchanged |
| AC-003-04 | BR-04 | an option configuration with an unknown category, a duplicate identifier or a missing display name | the system starts | it refuses to start and reports the configuration error |

## US-004 Registration confirmation

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-004-01 | BR-06 | a valid registration | it is submitted and accepted | a confirmation that the registration was received is shown in the application |
| AC-004-02 | BR-06 | an invalid registration | it is submitted and rejected | no confirmation is shown; the form shows what to correct and keeps the entered values |
| AC-004-03 | BR-06 | the registration cannot be processed (service unavailable or an unexpected error) | the form is submitted | no confirmation is shown; a general error message without internal details is shown |

## US-005 Reliable registration storage

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-005-01 | BR-07 | a valid registration | it is accepted | it is stored with its type, all fields, selected options, the consent and the time of acceptance |
| AC-005-02 | BR-07 | a valid registration | it is accepted | a raw JSON copy of the registration as accepted is written to persistent storage |
| AC-005-03 | BR-07 | the raw JSON copy cannot be written | a valid registration is submitted | the registration is not accepted, no confirmation is shown, no email is sent and no stored record remains |
| AC-005-04 | BR-07 | the database cannot store the registration | a valid registration is submitted | the registration is not accepted, no confirmation is shown and no email is sent |
| AC-005-05 | BR-07 | accepted registrations | the system is restarted | the registrations and their JSON copies are still present |

## US-006 Participant email confirmation

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-006-01 | BR-06 | a valid registration | it is accepted | the participant's address receives a confirmation email with their name and selected options |
| AC-006-02 | — | an invalid registration | it is rejected | no email is sent to the participant |
| AC-006-03 | BR-07 | the mail server is unavailable (D-14) | a valid registration is submitted | the registration is accepted and stored and the confirmation is shown |

## US-007 Organizer notification

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-007-01 | BR-07 | configured organizer addresses | a registration is accepted | every organizer address receives an email with the submitted data and the raw registration JSON attached, identical to the stored JSON copy |
| AC-007-02 | — | an invalid registration | it is rejected | no organizer email is sent |
| AC-007-03 | SB-05 | submitted data containing markup characters (`<`, `>`, `&`, quotes) | the organizer email is generated | the characters appear as text, not as markup |

## US-008 Registration export

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-008-01 | BR-08 | accepted external and student registrations | an organizer with valid credentials requests the export | an Excel workbook is returned with one row per registration, its type, all fields, selected options and time of acceptance |
| AC-008-02 | BR-08 | — | the export is requested without credentials | access is refused and no registration data is returned |
| AC-008-03 | BR-08 | — | the export is requested with wrong credentials | access is refused and no registration data is returned |
| AC-008-04 | BR-08 | no registrations | an organizer requests the export | a workbook with only the header row is returned |
| AC-008-05 | BR-03 | registrations with Slovenian characters | an organizer requests the export | the characters appear unchanged in the workbook |
| AC-008-06 | SB-05 | a registration whose text field starts with `=`, `+`, `-` or `@` | an organizer opens the export | the value is shown as text and is not evaluated as a formula |
