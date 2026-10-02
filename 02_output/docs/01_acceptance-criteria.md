# Acceptance criteria

> Written in: phase 1 · Source: `project/01_requirements/*` · Agent: writes (frozen after phase 3)

## US-001 External participant registration

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-001-01 | BR-01, BR-02, BR-03, BR-04, BR-05 | the external participant form with first name, last name, a valid email, organization / institution filled, the mandatory consent given, only active options selected and the anti-automation check passed | the participant submits | the registration is accepted as an external participant registration |
| AC-001-02 | BR-01 | the external participant form | it is displayed | it asks for exactly first name, last name, email and organization / institution, plus options and consent |
| AC-001-03 | BR-02 | an otherwise valid external form in which one required field is empty or contains only whitespace (each of the four fields in turn) | the participant submits | the registration is rejected and that field is reported as required |
| AC-001-04 | BR-02 | an otherwise valid external form whose text fields have leading and trailing whitespace | the participant submits | the registration is accepted and the values are kept without that whitespace |
| AC-001-05 | BR-03 | an otherwise valid external form whose email does not have a valid email format | the participant submits | the registration is rejected and the email is reported as invalid |
| AC-001-06 | BR-03 | an otherwise valid external form whose names and organization contain Slovenian characters (č, š, ž) | the participant submits | the registration is accepted and the characters are kept unchanged |
| AC-001-07 | BR-04 | an otherwise valid external submission that selects an option that does not exist | it is submitted | the registration is rejected and the option is reported as not selectable |
| AC-001-08 | BR-04 | an otherwise valid external submission that selects an inactive option | it is submitted | the registration is rejected and the option is reported as not selectable |
| AC-001-09 | BR-05 | an otherwise valid external form without the mandatory consent given | the participant submits | the registration is rejected and the consent is reported as required |
| AC-001-10 | BR-04 | an otherwise valid external form with no option selected (D-11) | the participant submits | the registration is accepted |
| AC-001-11 | BR-04 | an otherwise valid external submission that selects the same option twice (D-11) | it is submitted | the registration is rejected |
| AC-001-12 | | an otherwise valid external submission for which the anti-automation check is missing or failed | it is submitted | the registration is rejected |
| AC-001-13 | | an accepted registration and a new valid external form with the same email (D-12) | the participant submits | the new registration is accepted as a separate registration |
| AC-001-14 | BR-05 | the external participant form | it is displayed | the mandatory consent is not preselected |

## US-002 Student registration

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-002-01 | BR-01, BR-02, BR-03, BR-04, BR-05 | the student form with first name, last name, a valid email, study institution, study programme, student ID filled, the mandatory consent given, only active options selected and the anti-automation check passed | the student submits | the registration is accepted as a student registration |
| AC-002-02 | BR-01 | the student form | it is displayed | it asks for exactly first name, last name, email, study institution, study programme and student ID, plus options and consent |
| AC-002-03 | BR-02 | an otherwise valid student form in which one required field is empty or contains only whitespace (each of the six fields in turn) | the student submits | the registration is rejected and that field is reported as required |
| AC-002-04 | BR-02 | an otherwise valid student form whose text fields have leading and trailing whitespace | the student submits | the registration is accepted and the values are kept without that whitespace |
| AC-002-05 | BR-03 | an otherwise valid student form whose email does not have a valid email format | the student submits | the registration is rejected and the email is reported as invalid |
| AC-002-06 | BR-03 | an otherwise valid student form whose text fields contain Slovenian characters (č, š, ž) | the student submits | the registration is accepted and the characters are kept unchanged |
| AC-002-07 | BR-04 | an otherwise valid student submission that selects an unknown or an inactive option | it is submitted | the registration is rejected and the option is reported as not selectable |
| AC-002-08 | BR-05 | an otherwise valid student form without the mandatory consent given | the student submits | the registration is rejected and the consent is reported as required |
| AC-002-09 | | an otherwise valid student submission for which the anti-automation check is missing or failed | it is submitted | the registration is rejected |
| AC-002-10 | BR-04 | active options in every category (D-08) | the student form is displayed | every active option is offered, the same as on the external form |
| AC-002-11 | BR-05 | the student form | it is displayed | the mandatory consent is not preselected |
| AC-002-12 | BR-01 | a participant on the registration page | they choose between external participant and student | the form of the chosen type is shown |

## US-003 Configurable conference options

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-003-01 | BR-04 | configured options in the categories workshop, event, meal and other | a registration form is displayed | the active options are offered grouped by category, each with its display name |
| AC-003-02 | BR-04 | a configured option that is inactive | a registration form is displayed | that option is not offered |
| AC-003-03 | BR-01, BR-04 | the configuration is changed to add an option, rename one and deactivate one | the system runs with the new configuration | the forms offer the new set of active options and the fixed participant fields are unchanged |
| AC-003-04 | BR-04 | an option that was active and is now inactive in the configuration | a submission selects it | the registration is rejected |
| AC-003-05 | BR-04 | a category with no active option | a registration form is displayed | the form is still usable and a registration without options from that category is accepted |

## US-004 Registration confirmation

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-004-01 | BR-06 | a valid registration of either type | it has been accepted | the application shows a clear confirmation that the registration was received |
| AC-004-02 | BR-06 | a registration that is rejected | the participant submits | no confirmation is shown, the reasons are shown and the entered values remain for correction |
| AC-004-03 | BR-06, BR-07 | a valid registration that cannot be stored | the participant submits | no confirmation is shown and the participant is told that the registration was not received, without internal details |

## US-005 Reliable registration storage

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-005-01 | BR-07 | a valid registration of either type | it is accepted | it is stored in the database with its type, all its fields, the selected options, the consent and the time of acceptance |
| AC-005-02 | BR-07 | a valid registration of either type | it is accepted | a raw JSON copy with the registration exactly as accepted exists on persistent storage |
| AC-005-03 | BR-06, BR-07 | a valid registration whose database record or JSON copy cannot be written | it is submitted | the registration is not accepted and is not reported as received |
| AC-005-04 | BR-07 | a registration that is rejected | it is submitted | nothing is stored in the database and no JSON copy is written |
| AC-005-05 | BR-07 | accepted registrations | the system is restarted | the database records and the JSON copies are still present |

## US-006 Participant email confirmation

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-006-01 | BR-06 | a registration of either type that was accepted | it has been stored | an email confirming the registration is sent to the email address the participant entered |
| AC-006-02 | | a registration that is rejected | it is submitted | no email is sent to the participant |
| AC-006-03 | BR-07 | an accepted and stored registration whose participant email cannot be sent (D-10) | sending fails | the registration stays stored and accepted, and the in-application confirmation is still shown |

## US-007 Organizer notification

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-007-01 | | a registration of either type that was accepted | it has been stored | an email containing the submitted data is sent to every configured organizer recipient |
| AC-007-02 | BR-07 | a registration that was accepted | the organizer notification is sent | the raw registration JSON is attached and equals the stored JSON copy |
| AC-007-03 | | a registration that is rejected | it is submitted | no organizer notification is sent |
| AC-007-04 | BR-07 | an accepted and stored registration whose organizer notification cannot be sent (D-10) | sending fails | the registration stays stored and accepted, and the in-application confirmation is still shown |

## US-008 Registration export

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-008-01 | BR-08 | accepted registrations of both types and an organizer with organizer access | the organizer requests the export | an Excel workbook is returned that contains every current registration with its type, fields and selected options |
| AC-008-02 | BR-08 | a request without organizer access | the export is requested | it is refused and no registration data is returned |
| AC-008-03 | BR-08 | a request with wrong organizer credentials | the export is requested | it is refused and no registration data is returned |
| AC-008-04 | BR-08 | an export already taken and a registration accepted afterwards | the organizer requests the export again | the workbook also contains the new registration |
| AC-008-05 | BR-03, BR-08 | accepted registrations containing Slovenian characters (č, š, ž) | the organizer requests the export | the workbook shows the characters unchanged |
| AC-008-06 | BR-08 | no accepted registration and an organizer with organizer access | the organizer requests the export | a workbook with the column headings and no registration rows is returned |
