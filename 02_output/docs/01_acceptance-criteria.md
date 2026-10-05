# Acceptance criteria

> Written in: phase 1 · Source: `project/requirements.md` · Procedure: `skills/derive-acceptance-criteria` · Agent: writes (frozen after phase 3)

## US-001 External participant registration

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-001-01 | BR-01 | the external participant form is open | the participant looks at the form | it asks for exactly first name, last name, email and organization / institution, plus the options and consents |
| AC-001-02 | BR-01, BR-04 | all external fields are valid, the mandatory consents are given, active options offered to external participants are selected and the anti-automation check is passed | the participant submits | the registration is accepted as an external registration with the selected options |
| AC-001-03 | BR-02 | any one of the four external fields is empty | the participant submits | the registration is rejected and the error is shown next to that field |
| AC-001-04 | BR-02 | a required field contains only whitespace | the participant submits | the registration is rejected as if the field were empty |
| AC-001-05 | BR-02 | required fields have leading or trailing whitespace | the registration is accepted | the stored values have the whitespace removed |
| AC-001-06 | BR-03 | the email is not a valid email address | the participant submits | the registration is rejected and the error is shown next to the email field |
| AC-001-07 | BR-03 | names and organization contain č, š and ž | the registration is accepted | the stored registration contains those characters unchanged |
| AC-001-08 | BR-04 | the submission contains an option identifier that is not configured | the registration is submitted | the registration is rejected |
| AC-001-09 | BR-04 | the submission contains a configured but inactive option | the registration is submitted | the registration is rejected |
| AC-001-10 | BR-04 | the submission contains the same option twice (D-08) | the registration is submitted | the registration is rejected |
| AC-001-11 | BR-04 | no option is selected (D-08) | the participant submits an otherwise valid registration | the registration is accepted |
| AC-001-12 | BR-05 | the form is opened | the participant looks at the consents | no consent is preselected |
| AC-001-13 | BR-05 | a mandatory consent is not given | the participant submits | the registration is rejected and the error is shown next to that consent |
| AC-001-14 | — | the anti-automation check is not passed or its token is rejected | the registration is submitted | the registration is rejected and nothing is stored |
| AC-001-15 | — | a registration with the same email, ignoring case and surrounding whitespace, already exists (D-09) | the participant submits | the registration is rejected with a message to contact the organizers |

## US-002 Student registration

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-002-01 | BR-01 | the student form is open | the student looks at the form | it asks for exactly first name, last name, email, study institution, study programme and student ID, plus the options and consents |
| AC-002-02 | BR-01, BR-04 | all student fields are valid, the mandatory consents are given, active options offered to students are selected and the anti-automation check is passed | the student submits | the registration is accepted as a student registration with the selected options |
| AC-002-03 | BR-02 | any one of the six student fields is empty or only whitespace | the student submits | the registration is rejected and the error is shown next to that field |
| AC-002-04 | BR-03 | the email is not a valid email address | the student submits | the registration is rejected and the error is shown next to the email field |
| AC-002-05 | BR-03 | the study institution and programme contain č, š and ž | the registration is accepted | the stored registration contains those characters unchanged |
| AC-002-06 | BR-04 | the submission contains an unknown or an inactive option | the registration is submitted | the registration is rejected |
| AC-002-07 | BR-04 | an active option is offered only to external participants (D-05) | a student registration selecting it is submitted | the registration is rejected |
| AC-002-08 | BR-05 | a mandatory consent is not given | the student submits | the registration is rejected and the error is shown next to that consent |
| AC-002-09 | — | the anti-automation check is not passed or its token is rejected | the registration is submitted | the registration is rejected and nothing is stored |
| AC-002-10 | — | a registration with the same email already exists (D-09) | the student submits | the registration is rejected with a message to contact the organizers |

## US-003 Configurable conference options

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-003-01 | BR-04 | active options of all four categories are configured | a form is opened | the options are shown grouped as workshops, events, meals and other activities, each with its display name |
| AC-003-02 | BR-04 | an option is configured as inactive | a form is opened | the option is not shown |
| AC-003-03 | BR-04 | an option is offered only to external participants (D-05) | the student form is opened | the option is not shown on the student form and is shown on the external form |
| AC-003-04 | BR-01 | the option configuration is changed (an option added, renamed or deactivated) and the application is restarted, with no code change | a form is opened | the forms show the changed options and the participant fields are unchanged |
| AC-003-05 | BR-04 | an accepted registration selected options | the registration is stored | the options are stored by their stable identifiers |

## US-004 Registration confirmation

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-004-01 | BR-06 | a valid registration is submitted | the registration is accepted | a confirmation is shown in the application |
| AC-004-02 | BR-06 | a registration is rejected | the response arrives | no confirmation is shown and the form stays open with the errors |
| AC-004-03 | BR-06, BR-07 | storing the registration fails | the participant submits | no confirmation is shown and the participant sees an error without internal details |

## US-005 Reliable registration storage

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-005-01 | BR-07 | a valid registration | it is accepted | it is stored in the database with every submitted field, the selected options and the given consents with their timestamp |
| AC-005-02 | BR-07 | a valid registration | it is accepted | a raw JSON copy of the registration exactly as accepted is stored on persistent storage |
| AC-005-03 | BR-07 | the raw JSON copy cannot be written | a valid registration is submitted | the registration is not accepted and no database record of it remains |
| AC-005-04 | BR-07 | the database record cannot be written | a valid registration is submitted | the registration is not accepted and no JSON copy of it remains |
| AC-005-05 | BR-07 | storing the registration fails | a valid registration is submitted | no email is sent |

## US-006 Participant email confirmation

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-006-01 | — | a registration is accepted | the emails are sent | the participant's address receives one email confirming the registration with the participant's name and selected options |
| AC-006-02 | — | a registration is rejected | the response arrives | no email is sent to the participant |
| AC-006-03 | BR-06 | sending the participant email fails (D-07) | a valid registration is submitted | the registration stays accepted and the confirmation is shown |
| AC-006-04 | BR-03 | the name contains č, š and ž | the participant email arrives | the characters are shown unchanged |

## US-007 Organizer notification

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-007-01 | — | organizer addresses are configured and a registration is accepted | the emails are sent | every organizer address receives an email containing the submitted data |
| AC-007-02 | BR-07 | a registration is accepted | the organizer email arrives | it has the raw JSON copy of the registration attached, identical to the stored copy |
| AC-007-03 | — | a registration is rejected | the response arrives | no organizer email is sent |
| AC-007-04 | BR-06 | sending the organizer email fails (D-07) | a valid registration is submitted | the registration stays accepted and the confirmation is shown |

## US-008 Registration export

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-008-01 | BR-08 | external and student registrations exist | an organizer with valid organizer access requests the export | an Excel workbook is returned with one row per registration, its type, every field, the selected options and the consents |
| AC-008-02 | BR-08 | registrations exist | the export is requested without organizer access | the request is refused and no registration data is returned |
| AC-008-03 | BR-08 | registrations exist | the export is requested with wrong organizer credentials | the request is refused and no registration data is returned |
| AC-008-04 | BR-08 | no registration exists | an organizer requests the export | a workbook with the column headers and no data rows is returned |
| AC-008-05 | BR-03 | a registration contains č, š and ž | an organizer opens the export | the characters are shown unchanged |
