# Acceptance criteria

> Written in: phase 1 · Source: `project/01_requirements/*` · Agent: writes (frozen after phase 3)

## US-001 External participant registration

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-001-01 | BR-01 | the external participant form is opened | the participant views it | it asks for exactly first name, last name, email and organization / institution, the options and the consents |
| AC-001-02 | BR-01, BR-04, BR-05 | all required fields are filled with valid values, the mandatory consents are given and active options available to external participants are selected | the participant submits | the registration is accepted |
| AC-001-03 | BR-04, D-17 | all required fields are valid, the mandatory consents are given and no option is selected | the participant submits | the registration is accepted |
| AC-001-04 | BR-02 | any one required field is empty | the participant submits | the registration is rejected and that field is identified as required |
| AC-001-05 | BR-02 | a required field contains only whitespace, including the no-break space U+00A0 | the participant submits | the registration is rejected and that field is identified as required |
| AC-001-06 | BR-02 | required fields have leading or trailing whitespace, including U+00A0 | the participant submits | the registration is accepted and the values are kept without that whitespace |
| AC-001-07 | BR-03 | the email does not have a valid email format | the participant submits | the registration is rejected and the email field is identified as invalid |
| AC-001-08 | BR-03 | text fields contain Slovenian characters (č, š, ž, Č, Š, Ž) | the participant submits | the registration is accepted and the characters are kept unchanged |
| AC-001-09 | BR-04 | the form is opened | the participant views the options | only active options available to external participants are offered, grouped as workshops, events, meals and other activities |
| AC-001-10 | BR-04 | a submission selects an option identifier that does not exist | it is submitted | the registration is rejected |
| AC-001-11 | BR-04 | a submission selects an inactive option | it is submitted | the registration is rejected |
| AC-001-12 | BR-04, D-17 | a submission selects more options in one category than the configured maximum | it is submitted | the registration is rejected |
| AC-001-13 | BR-05 | the form is opened | the participant views the consents | no consent is preselected |
| AC-001-14 | BR-05 | a mandatory consent is not given | the participant submits | the registration is rejected and the missing consent is identified |
| AC-001-15 | D-18 | a registration with the same email (ignoring letter case and surrounding whitespace) already exists | the participant submits | the registration is rejected and the participant is told that this email is already registered |
| AC-001-16 | scope | the anti-automation check is missing or not passed | the participant submits | the registration is rejected and nothing is stored |

## US-002 Student registration

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-002-01 | BR-01 | the student form is opened | the student views it | it asks for exactly first name, last name, email, study institution, study programme and student ID, the options and the consents |
| AC-002-02 | BR-01, BR-04, BR-05 | all required fields are filled with valid values, the mandatory consents are given and active options available to students are selected | the student submits | the registration is accepted |
| AC-002-03 | BR-02 | any one of study institution, study programme or student ID is empty or only whitespace, including U+00A0 | the student submits | the registration is rejected and that field is identified as required |
| AC-002-04 | BR-03 | the email does not have a valid email format | the student submits | the registration is rejected and the email field is identified as invalid |
| AC-002-05 | BR-04, D-14 | the form is opened | the student views the options | only active options available to students are offered, grouped as workshops, events, meals and other activities |
| AC-002-06 | BR-04, D-14 | a student submission selects an active option not available to students | it is submitted | the registration is rejected |
| AC-002-07 | BR-05 | a mandatory consent is not given | the student submits | the registration is rejected and the missing consent is identified |
| AC-002-08 | D-18 | a registration with the same email already exists, of either type | the student submits | the registration is rejected and the student is told that this email is already registered |
| AC-002-09 | scope | the anti-automation check is missing or not passed | the student submits | the registration is rejected and nothing is stored |

## US-003 Configurable conference options

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-003-01 | BR-04 | the configuration lists an active option with identifier, display name and category | the application runs with that configuration | the option is offered on the forms of the types it is available to, under its category |
| AC-003-02 | BR-04 | the configuration marks an option inactive | the application runs with that configuration | the option is not offered and a submission selecting it is rejected |
| AC-003-03 | BR-01 | the configured options are changed | the application runs with the new configuration | both forms still ask for exactly their fixed fields |
| AC-003-04 | BR-05, D-15 | the configuration defines the consents and their wording | the forms are opened | each configured consent is shown with its wording, unticked |

## US-004 Registration confirmation

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-004-01 | BR-06 | a submission is accepted | the response arrives | a confirmation is shown stating that the registration was received |
| AC-004-02 | BR-06 | a submission is rejected | the response arrives | no confirmation is shown and the reasons are shown |
| AC-004-03 | BR-06, BR-07 | the registration cannot be stored | the participant submits | no confirmation is shown and a general error without internal details is shown |

## US-005 Reliable registration storage

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-005-01 | BR-07 | a submission is accepted | the organizer exports the registrations | the export contains it with every submitted value |
| AC-005-02 | BR-07 | a submission is accepted | the persistent storage is inspected | a raw JSON copy of exactly the accepted registration exists there |
| AC-005-03 | BR-07, BR-06 | the raw JSON copy cannot be written | the participant submits | the registration is not accepted, no confirmation is shown and the export does not contain it |
| AC-005-04 | BR-07 | registrations were accepted | the application is restarted | the registrations and their JSON copies are still present |
| AC-005-05 | BR-07 | a submission is rejected | storage is inspected | neither a stored registration nor a JSON copy exists for it |

## US-006 Participant email confirmation

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-006-01 | BR-07 | a submission is accepted | processing completes | one email is sent to the participant's address confirming the registration, with the submitted data and selected options |
| AC-006-02 | BR-06 | a submission is rejected | processing completes | no email is sent to the participant |
| AC-006-03 | D-16 | a submission is accepted and the participant email cannot be sent | processing completes | the registration stays stored and the confirmation is shown |
| AC-006-04 | BR-03 | the participant's data contains Slovenian characters | the email is received | the characters are shown unchanged |

## US-007 Organizer notification

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-007-01 | BR-07 | a submission is accepted | processing completes | each configured organizer address receives a notification containing the submitted data |
| AC-007-02 | BR-07 | a submission is accepted | the notification is received | it has the raw registration JSON attached, identical to the stored JSON copy |
| AC-007-03 | BR-06 | a submission is rejected | processing completes | no organizer notification is sent |
| AC-007-04 | D-16 | a submission is accepted and the organizer notification cannot be sent | processing completes | the registration stays stored and the confirmation is shown |

## US-008 Registration export

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-008-01 | BR-08 | an organizer with valid organizer access | requests the export | a workbook is returned with one row per stored registration of both types, with every field, the registration type and the selected options |
| AC-008-02 | BR-08 | no stored registrations | an organizer requests the export | a workbook with only the column headings is returned |
| AC-008-03 | BR-08 | a requester without organizer access | requests the export | access is refused and no registration data is returned |
| AC-008-04 | BR-08 | a requester with wrong organizer credentials | requests the export | access is refused and no registration data is returned |
| AC-008-05 | BR-03 | stored registrations contain Slovenian characters | an organizer exports | the workbook shows them unchanged |
