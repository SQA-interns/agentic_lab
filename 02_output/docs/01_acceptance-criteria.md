# Acceptance criteria

> Written in: phase 1 · Source: `project/01_requirements/*` · Agent: writes (frozen after phase 3)

## US-001 External participant registration

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-001-01 | BR-01, BR-04, BR-05, BR-06, BR-07 | the external form with active options | a participant submits valid values for every external field, selects active options, gives the mandatory consent and passes the anti-automation check | the registration is accepted as an external registration with the selected options and the confirmation is shown |
| AC-001-02 | BR-01 | a participant opens registration | they choose the external participant form | the form asks for exactly first name, last name, email and organization / institution, and for no student field |
| AC-001-03 | BR-02 | the external form | a participant submits it with any one required field empty or only whitespace | the registration is rejected, that field is marked as required, and nothing is stored |
| AC-001-04 | BR-02 | the external form | a participant submits required values with leading or trailing whitespace | the registration is accepted and the stored values have no leading or trailing whitespace |
| AC-001-05 | BR-03 | the external form | a participant submits an email that is not a valid email address | the registration is rejected, the email field is marked as invalid, and nothing is stored |
| AC-001-06 | BR-03 | the external form | a participant submits names and organization containing č, š, ž and other Unicode letters | the registration is accepted and the stored values keep those characters unchanged |
| AC-001-07 | BR-04 | the external form | a submission selects an option identifier that is not configured | the registration is rejected and nothing is stored |
| AC-001-08 | BR-04 | the external form | a submission selects an option that is configured as inactive | the registration is rejected and nothing is stored |
| AC-001-09 | BR-04 | the external form | a submission selects no option at all | the registration is accepted with no selected options |
| AC-001-10 | BR-04 | the external form | a submission selects the same option more than once | the registration is rejected and nothing is stored |
| AC-001-11 | BR-05 | a participant opens the external form | the form is shown | the mandatory consent is shown with its configured wording and is not selected |
| AC-001-12 | BR-05 | the external form | a participant submits it without giving the mandatory consent | the registration is rejected, the consent is marked as required, and nothing is stored |
| AC-001-13 | — | the external form | a submission fails or omits the anti-automation check | the registration is rejected and nothing is stored |
| AC-001-14 | — | an accepted registration of either type exists for an email address | an external registration is submitted with the same address in any letter case or with surrounding whitespace | the registration is rejected with a message that the address is already registered and the organizers can be contacted, and nothing new is stored |

## US-002 Student registration

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-002-01 | BR-01, BR-04, BR-05, BR-06, BR-07 | the student form with active options | a student submits valid values for every student field, selects active options, gives the mandatory consent and passes the anti-automation check | the registration is accepted as a student registration with the selected options and the confirmation is shown |
| AC-002-02 | BR-01 | a participant opens registration | they choose the student form | the form asks for exactly first name, last name, email, study institution, study programme and student ID, and for no organization field |
| AC-002-03 | BR-02 | the student form | a student submits it with any one required field empty or only whitespace | the registration is rejected, that field is marked as required, and nothing is stored |
| AC-002-04 | BR-02 | the student form | a student submits required values with leading or trailing whitespace | the registration is accepted and the stored values have no leading or trailing whitespace |
| AC-002-05 | BR-03 | the student form | a student submits an email that is not a valid email address | the registration is rejected, the email field is marked as invalid, and nothing is stored |
| AC-002-06 | BR-03 | the student form | a student submits names, study institution and study programme containing č, š, ž and other Unicode letters | the registration is accepted and the stored values keep those characters unchanged |
| AC-002-07 | BR-05 | the student form | a student submits it without giving the mandatory consent | the registration is rejected, the consent is marked as required, and nothing is stored |
| AC-002-08 | BR-04 | active and inactive options are configured | a participant opens the student form | the student form offers exactly the same active options as the external form |
| AC-002-09 | — | an accepted registration of either type exists for an email address | a student registration is submitted with the same address in any letter case or with surrounding whitespace | the registration is rejected with a message that the address is already registered and the organizers can be contacted, and nothing new is stored |
| AC-002-10 | BR-04 | the student form | a submission selects an option identifier that is not configured, or an inactive option | the registration is rejected and nothing is stored |
| AC-002-11 | — | the student form | a submission fails or omits the anti-automation check | the registration is rejected and nothing is stored |

## US-003 Configurable conference options

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-003-01 | BR-04 | options of all four categories are configured as active | a participant opens either form | the options are offered grouped as workshops, events, meals and other activities, each with its display name |
| AC-003-02 | BR-04 | an option is configured as inactive | a participant opens either form | that option is not offered |
| AC-003-03 | BR-01, BR-04 | the option configuration is changed (an option added, renamed, deactivated) without a code change | the system is started with the new configuration | the forms offer the changed set of options and ask for the same participant fields as before |
| AC-003-04 | BR-04 | an accepted registration selected an option | that option's display name is changed in the configuration | the stored registration still refers to the same option by its stable identifier |

## US-004 Registration confirmation

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-004-01 | BR-06 | a submission that the system accepts | the submission completes | the application shows a confirmation that the registration was received |
| AC-004-02 | BR-06 | a submission that the system rejects | the submission completes | no confirmation is shown and the reasons for rejection are shown |
| AC-004-03 | BR-06, BR-07 | the database or the persistent storage for JSON copies cannot be written | a valid registration is submitted | no confirmation is shown, an error message without internal details is shown, and the registration is not kept anywhere |

## US-005 Reliable registration storage

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-005-01 | BR-07 | a valid registration | it is accepted | the database holds it with its type, every submitted field, the selected options, the given consent and the time it was accepted |
| AC-005-02 | BR-07 | a valid registration | it is accepted | a raw JSON copy of the registration exactly as accepted exists on persistent storage |
| AC-005-03 | BR-07 | either the database row or the JSON copy cannot be written | a valid registration is submitted | neither is kept, and the same registration can be submitted again once storage works |
| AC-005-04 | BR-07 | an accepted registration | the system is restarted | the registration is still in the database and its JSON copy still exists |

## US-006 Participant email confirmation

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-006-01 | BR-07 | a valid registration | it is accepted | an email confirming the registration and listing the submitted details and selected options is sent to the submitted email address |
| AC-006-02 | — | a submission | it is rejected | no email is sent to the participant |
| AC-006-03 | BR-06, BR-07 | the participant email cannot be sent | a valid registration is submitted | the registration is still stored with its JSON copy and the confirmation is shown |

## US-007 Organizer notification

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-007-01 | BR-07 | one or more configured organizer addresses | a registration is accepted | each organizer address receives an email containing the submitted data, with the raw registration JSON attached, identical to the stored JSON copy |
| AC-007-02 | — | a submission | it is rejected | no organizer notification is sent |
| AC-007-03 | BR-06, BR-07 | the organizer notification cannot be sent | a valid registration is submitted | the registration is still stored with its JSON copy and the confirmation is shown |

## US-008 Registration export

| AC | BR | Given | When | Then |
|---|---|---|---|---|
| AC-008-01 | BR-08 | accepted registrations of both types | an organizer with valid organizer access requests the export | an Excel workbook is returned with one row per accepted registration showing its type, every field of its type, the selected options, the consent and the time accepted |
| AC-008-02 | BR-08 | accepted registrations | the export is requested without organizer access | the request is refused and no registration data is returned |
| AC-008-03 | BR-08 | accepted registrations | the export is requested with wrong organizer credentials | the request is refused and no registration data is returned |
| AC-008-04 | BR-08 | no accepted registrations | an organizer requests the export | an Excel workbook is returned with column headings and no registration rows |
| AC-008-05 | BR-03, BR-08 | an accepted registration containing č, š, ž | an organizer requests the export | the workbook shows those characters unchanged |
| AC-008-06 | BR-08 | an organizer exported the registrations | a new registration is accepted and the organizer exports again | the new export contains the new registration |
