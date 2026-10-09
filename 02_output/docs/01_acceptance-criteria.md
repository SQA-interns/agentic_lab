# Acceptance criteria

> Written in: phase 1 · Source: `project/requirements.md` · Procedure: `skills/derive-acceptance-criteria` · Agent: writes (frozen after phase 3)

## US-001 External participant registration

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-001-01 | BR-01, BR-04 | the external form with first name, last name, email and organization filled validly, the mandatory consent given, active options selected and a valid anti-automation proof | the participant submits it | the registration is accepted as an external participant registration with exactly the given data and options |
| AC-001-02 | BR-01 | the registration page | the participant chooses the external participant type | the form asks for first name, last name, email and organization / institution, and for no student field |
| AC-001-03 | BR-02 | the external form with one required field (first name, last name, email or organization) empty or only whitespace | the participant submits it | the registration is rejected, the error names that field, and nothing is stored |
| AC-001-04 | BR-02 | a valid external registration whose text values have leading and trailing whitespace | the participant submits it | the registration is accepted and stored without that whitespace |
| AC-001-05 | BR-03 | the external form with an email that is not a valid email address | the participant submits it | the registration is rejected and the error names the email field |
| AC-001-06 | BR-03 | a valid external registration whose names and organization contain č, š, ž and other Unicode letters | the participant submits it | the registration is accepted and the text is stored unchanged |
| AC-001-07 | BR-04 | a valid external registration that selects an option identifier not in the configured set | the participant submits it | the registration is rejected and nothing is stored |
| AC-001-08 | BR-04 | a valid external registration that selects an inactive option | the participant submits it | the registration is rejected and nothing is stored |
| AC-001-09 | BR-04, D-09 | a valid external registration that selects an active option not available to external participants | the participant submits it | the registration is rejected and nothing is stored |
| AC-001-10 | BR-04, D-12 | a valid external registration that selects more options in one category than that category allows | the participant submits it | the registration is rejected and nothing is stored |
| AC-001-11 | BR-04, D-12 | a valid external registration that selects no option | the participant submits it | the registration is accepted |
| AC-001-12 | BR-05, D-10 | a valid external registration without the mandatory consent | the participant submits it | the registration is rejected, the error names the consent, and nothing is stored |
| AC-001-13 | BR-05 | the registration page | the participant opens the external form | no consent is preselected |
| AC-001-14 | — | a valid external registration without a valid anti-automation proof | the participant submits it | the registration is rejected and nothing is stored |
| AC-001-15 | D-13 | an accepted registration with an email address | a second registration (either type) is submitted with the same address in any letter case | the second registration is rejected and the first is unchanged |
| AC-001-16 | D-15 | a valid external registration with one text field longer than its maximum length | the participant submits it | the registration is rejected and the error names that field |
| AC-001-17 | BR-02, BR-03 | the external form with an empty required field or an invalid email | the participant tries to submit it | the form shows the error next to that field and sends nothing |

## US-002 Student registration

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-002-01 | BR-01, BR-04 | the student form with first name, last name, email, study institution, study programme and student ID filled validly, the mandatory consent given, active options selected and a valid anti-automation proof | the student submits it | the registration is accepted as a student registration with exactly the given data and options |
| AC-002-02 | BR-01 | the registration page | the participant chooses the student type | the form asks for first name, last name, email, study institution, study programme and student ID, and not for organization / institution |
| AC-002-03 | BR-02 | the student form with one required field (any of the six) empty or only whitespace | the student submits it | the registration is rejected, the error names that field, and nothing is stored |
| AC-002-04 | BR-02 | a valid student registration whose text values have leading and trailing whitespace | the student submits it | the registration is accepted and stored without that whitespace |
| AC-002-05 | BR-03 | the student form with an email that is not a valid email address | the student submits it | the registration is rejected and the error names the email field |
| AC-002-06 | BR-03 | a valid student registration whose names, institution and programme contain č, š, ž | the student submits it | the registration is accepted and the text is stored unchanged |
| AC-002-07 | BR-04 | a valid student registration that selects an unknown or inactive option | the student submits it | the registration is rejected and nothing is stored |
| AC-002-08 | BR-04, D-09 | a valid student registration that selects an active option not available to students | the student submits it | the registration is rejected and nothing is stored |
| AC-002-09 | BR-04, D-12 | a valid student registration that selects more options in one category than that category allows | the student submits it | the registration is rejected and nothing is stored |
| AC-002-10 | BR-05, D-10 | a valid student registration without the mandatory consent | the student submits it | the registration is rejected and nothing is stored |
| AC-002-11 | BR-05 | the registration page | the participant opens the student form | no consent is preselected |
| AC-002-12 | — | a valid student registration without a valid anti-automation proof | the student submits it | the registration is rejected and nothing is stored |
| AC-002-13 | D-13 | an accepted external registration with an email address | a student registration is submitted with the same address | the student registration is rejected |
| AC-002-14 | D-15 | a valid student registration with one text field longer than its maximum length | the student submits it | the registration is rejected and the error names that field |
| AC-002-15 | BR-02 | the student form with an empty required field | the student tries to submit it | the form shows the error next to that field and sends nothing |

## US-003 Configurable conference options

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-003-01 | BR-04 | a configuration with active options in the categories workshop, event, meal and other | a participant opens either form | the form offers each active option available to that type by its display name, grouped by category |
| AC-003-02 | BR-04 | a configuration in which an option is inactive | a participant opens either form | that option is not offered |
| AC-003-03 | BR-04, D-09 | a configuration in which an option is available only to external participants | a participant opens the student form | that option is not offered |
| AC-003-04 | BR-04 | an option is added, renamed or deactivated in the configuration only | the system is restarted with the new configuration | the forms and the validation of new registrations follow the new configuration without a code change |
| AC-003-05 | BR-01 | any valid option configuration | a participant opens either form | the participant fields are exactly those of BR-01 |
| AC-003-06 | D-16 | a configuration with a duplicate option identifier, an unknown category or a missing display name | the system starts | it refuses to start and reports the configuration error |

## US-004 Registration confirmation

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-004-01 | BR-06 | a valid registration | it has been accepted and stored | the application shows a confirmation that the registration was received |
| AC-004-02 | BR-06 | an invalid registration | it is rejected | no confirmation is shown, each field error is shown next to its field, and the entered values remain in the form |
| AC-004-03 | BR-06, BR-07 | a valid registration that cannot be stored | the participant submits it | no confirmation is shown and a general error without internal details is shown |

## US-005 Reliable registration storage

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-005-01 | BR-07 | a valid registration | it is accepted | the database holds it with every submitted field, the selected options, the consents given with their time, and the registration time |
| AC-005-02 | BR-07 | a valid registration | it is accepted | a raw JSON copy of the registration exactly as accepted exists as its own file on persistent storage and identifies the registration |
| AC-005-03 | BR-06, BR-07 | the JSON copy cannot be written | a valid registration is submitted | the registration is not accepted, no database record of it remains, and no email is sent |
| AC-005-04 | BR-06, BR-07 | the database is unavailable | a valid registration is submitted | the registration is not accepted, no JSON copy of it remains, and no email is sent |
| AC-005-05 | BR-07 | a rejected registration | it is rejected | neither a database record nor a JSON copy of it exists |

## US-006 Participant email confirmation

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-006-01 | — | a valid registration | it is accepted | an email is sent to the participant's address confirming the registration, with the participant's name, the registration type and the selected options |
| AC-006-02 | BR-03 | an accepted registration whose name contains č, š, ž | the participant email is sent | the name appears unchanged |
| AC-006-03 | — | an accepted registration whose text fields contain markup or line breaks | the participant email is sent | the markup appears as plain text, and the email has no recipient or header other than the intended ones |
| AC-006-04 | D-11 | the email cannot be sent | a valid registration is submitted | the registration is still accepted, stored and confirmed in the application |
| AC-006-05 | — | a rejected registration | it is rejected | no email is sent |

## US-007 Organizer notification

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-007-01 | — | one or more configured organizer addresses and a valid registration | it is accepted | each organizer address receives an email with the submitted data |
| AC-007-02 | BR-07 | an accepted registration | the organizer email is sent | it has the raw registration JSON attached, identical to the stored JSON copy |
| AC-007-03 | — | an accepted registration | the organizer email is sent | it contains only registration data and no internal or system data |
| AC-007-04 | BR-03 | an accepted registration with č, š, ž in its text | the organizer email is sent | the text appears unchanged in the email body and in the attachment |
| AC-007-05 | D-11 | the organizer email cannot be sent | a valid registration is submitted | the registration is still accepted, stored and confirmed in the application |

## US-008 Registration export

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-008-01 | BR-08 | accepted registrations of both types and organizer access | the organizer requests the export | an Excel workbook is returned with one row per registration holding its type, every field, the selected options, the consents with their time and the registration time |
| AC-008-02 | BR-08 | no organizer access, or wrong organizer credentials | the export is requested | the request is refused and no registration data is returned |
| AC-008-03 | BR-08 | no accepted registration and organizer access | the organizer requests the export | a workbook with only the header row is returned |
| AC-008-04 | BR-03 | an accepted registration with č, š, ž in its text and organizer access | the organizer requests the export | the text appears unchanged in the workbook |
| AC-008-05 | — | accepted registrations and organizer access | the organizer requests the export | the workbook contains only registration data and no internal or system data |
