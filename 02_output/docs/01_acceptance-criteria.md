# Acceptance criteria

> Written in: phase 1 · Source: `project/requirements.md` · Procedure: `skills/derive-acceptance-criteria` · Agent: writes (frozen after phase 3)

## US-001 External participant registration

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-001-01 | BR-01 | the external participant form is opened | the participant looks at the form | it asks for exactly first name, last name, email and organization / institution, plus the options and consents |
| AC-001-02 | BR-01, BR-07 | an external form with every required field valid, the mandatory consent given and the anti-automation check passed | the participant submits it | the registration is accepted as an external participant registration with the entered values |
| AC-001-03 | BR-04, D-14 | an otherwise valid external form with one or more active options selected, or none | the participant submits it | the registration is accepted with exactly the selected options |
| AC-001-04 | BR-02 | an external form in which one required field is empty or contains only whitespace | the participant submits it | the registration is rejected and an error is shown next to that field |
| AC-001-05 | BR-02 | an external form whose values have leading or trailing whitespace | the participant submits it | the registration is accepted with the values without that whitespace |
| AC-001-06 | BR-03 | an external form whose email is not a valid email address | the participant submits it | the registration is rejected and an error is shown next to the email field |
| AC-001-07 | BR-03 | an external form whose name and organization contain č, š and ž | the participant submits it | the registration is accepted and the values are kept unchanged |
| AC-001-08 | BR-04 | an external submission that selects an option identifier that does not exist | it is received | the registration is rejected |
| AC-001-09 | BR-04 | an external submission that selects an inactive option | it is received | the registration is rejected |
| AC-001-10 | BR-04, D-14 | a category with a configured maximum and an external submission selecting more options of that category | it is received | the registration is rejected |
| AC-001-11 | BR-05 | the external form is opened | the participant looks at the consents | no consent is preselected |
| AC-001-12 | BR-05 | an otherwise valid external form without the mandatory consent | the participant submits it | the registration is rejected and an error is shown next to the consent |
| AC-001-13 | SR-01 | an external submission whose anti-automation check is missing or failed | it is received | the registration is rejected and nothing is stored |
| AC-001-14 | D-15 | an email that is already registered, in any letter case | an external form with that email is submitted | the registration is rejected with a message to contact the organizers |
| AC-001-15 | BR-02, BR-03 | an external form with an empty required field or an invalid email | the participant tries to submit it | the error is shown next to the field before anything is sent |

## US-002 Student registration

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-002-01 | BR-01 | the student form is opened | the student looks at the form | it asks for exactly first name, last name, email, study institution, study programme and student ID, plus the options and consents |
| AC-002-02 | BR-01, BR-07 | a student form with every required field valid, the mandatory consent given and the anti-automation check passed | the student submits it | the registration is accepted as a student registration with the entered values |
| AC-002-03 | BR-04, D-11 | an otherwise valid student form with active options available to students selected, or none | the student submits it | the registration is accepted with exactly the selected options |
| AC-002-04 | BR-02 | a student form in which one required field is empty or contains only whitespace | the student submits it | the registration is rejected and an error is shown next to that field |
| AC-002-05 | BR-02 | a student form whose values have leading or trailing whitespace | the student submits it | the registration is accepted with the values without that whitespace |
| AC-002-06 | BR-03 | a student form whose email is not a valid email address | the student submits it | the registration is rejected and an error is shown next to the email field |
| AC-002-07 | BR-03 | a student form whose name, study institution and study programme contain č, š and ž | the student submits it | the registration is accepted and the values are kept unchanged |
| AC-002-08 | BR-04 | a student submission that selects an unknown or an inactive option | it is received | the registration is rejected |
| AC-002-09 | BR-04, D-11 | a student submission that selects an active option not available to students | it is received | the registration is rejected |
| AC-002-10 | BR-05 | an otherwise valid student form without the mandatory consent, the consent not preselected when the form opened | the student submits it | the registration is rejected and an error is shown next to the consent |
| AC-002-11 | SR-01 | a student submission whose anti-automation check is missing or failed | it is received | the registration is rejected and nothing is stored |
| AC-002-12 | D-15 | an email that is already registered, by an external participant or a student | a student form with that email is submitted | the registration is rejected with a message to contact the organizers |

## US-003 Configurable conference options

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-003-01 | BR-04 | options configured in the four categories | a registration form is opened | the active options are offered grouped as workshops, events, meals and other activities, each with its display name |
| AC-003-02 | BR-04 | an option configured as inactive | a registration form is opened | that option is not offered |
| AC-003-03 | BR-01, BR-04 | the option configuration is changed (an option added, one deactivated) and the application restarted, with no code change | a registration form is opened | the changed set of options is offered and the participant fields are unchanged |
| AC-003-04 | D-11 | an option configured as available to external participants only | the student form is opened | that option is not offered on the student form and is offered on the external form |

## US-004 Registration confirmation

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-004-01 | BR-06 | a valid registration | it is submitted and accepted | a confirmation that the registration was received is shown in place of the form |
| AC-004-02 | BR-06 | an invalid registration | it is submitted and rejected | no confirmation is shown, the errors are shown and the entered values stay in the form |
| AC-004-03 | BR-06, BR-07 | a valid registration that cannot be stored | it is submitted | no confirmation is shown and a general error without internal details is shown |

## US-005 Reliable registration storage

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-005-01 | BR-07 | a registration | it is accepted | it is stored in the database with its type, all fields, the selected options and the consent with the time it was given |
| AC-005-02 | BR-07 | a registration | it is accepted | a raw JSON copy of the registration exactly as accepted is written to persistent storage |
| AC-005-03 | BR-07 | a registration for which the database record or the raw JSON copy cannot be written | it is submitted | the registration is not accepted, no confirmation is shown and no email is sent |
| AC-005-04 | BR-07 | accepted registrations | the application and its storage are restarted | every registration and its raw JSON copy are still present |
| AC-005-05 | BR-03, BR-07 | a registration whose values contain č, š and ž | it is accepted | the values are unchanged in the database and in the raw JSON copy |

## US-006 Participant email confirmation

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-006-01 | — | a registration | it is accepted | an email is sent to the participant's address with the conference name, the participant's name, the registration type and the selected options |
| AC-006-02 | BR-03 | an accepted registration whose values contain č, š and ž | the participant email is read | the characters are shown unchanged |
| AC-006-03 | SR-05 | an accepted registration whose values contain line breaks, header text or markup | the participant email is sent | it goes only to the participant, has no extra headers and shows the values as plain text |
| AC-006-04 | D-13 | an accepted registration whose participant email cannot be sent | the registration is processed | the registration stays stored and the confirmation is shown |
| AC-006-05 | BR-06 | a rejected registration | it is processed | no email is sent |

## US-007 Organizer notification

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-007-01 | BR-07 | configured organizer addresses and a registration | it is accepted | each organizer address receives an email with the submitted data and the raw JSON copy attached |
| AC-007-02 | BR-07 | an organizer notification | its attachment is compared with the stored raw JSON copy | they are identical |
| AC-007-03 | SR-07 | an organizer notification | it is read | it contains only registration data and no internal or other system data |
| AC-007-04 | BR-03 | an accepted registration whose values contain č, š and ž | the organizer email and its attachment are read | the characters are unchanged |
| AC-007-05 | D-13 | an accepted registration whose organizer notification cannot be sent | the registration is processed | the registration stays stored and the confirmation is shown |

## US-008 Registration export

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-008-01 | BR-08 | accepted external and student registrations | an organizer with valid organizer access requests the export | an Excel workbook is returned with one row per registration: type, all fields, the selected options and the consent with its time |
| AC-008-02 | BR-08 | accepted registrations | the export is requested without organizer access | the request is refused and no registration data is returned |
| AC-008-03 | BR-08 | accepted registrations | the export is requested with wrong organizer credentials | the request is refused and no registration data is returned |
| AC-008-04 | BR-08, SR-07 | an exported workbook | it is read | it contains only registration data and no internal or other system data |
| AC-008-05 | BR-08 | no accepted registrations | an organizer requests the export | a valid workbook with column headings and no registration rows is returned |
| AC-008-06 | BR-03 | accepted registrations whose values contain č, š and ž | an organizer opens the export | the characters are unchanged |
| AC-008-07 | SB-05 | a registration value that starts with a formula character such as = | an organizer opens the export | the cell shows the value as text and nothing is calculated |
