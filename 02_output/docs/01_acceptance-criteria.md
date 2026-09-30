# Acceptance criteria

> Written in: phase 1 · Source: `project/01_requirements/*` · Agent: writes (frozen after phase 3)

Each criterion is one observable behaviour in Given/When/Then form and cites its story and rules. "Nothing stored" means: no database row and no JSON copy were added. Open questions are resolved by decisions D-06 to D-11 (`decisions-log.md`, pending review); criteria that depend on them cite the decision.

Interfaces: "API" is the backend REST API under `/api` (`02_contracts/openapi.yaml`); "UI" is the frontend in a browser.

## US-001 External participant registration

| AC | Given | When | Then | Rules |
|---|---|---|---|---|
| AC-001-01 | active options and the configured mandatory consents | an external participant submits first name, last name, email, organization, a set of active option ids, all mandatory consents and a valid reCAPTCHA token to the API | the response is 201 with a registration id and the accepted data; the registration is stored | BR-01, BR-04, BR-07 |
| AC-001-02 | an otherwise valid external submission | one required field (first name, last name, email, organization) is missing, empty or only whitespace | the response is 400 with a field error naming that field; nothing stored | BR-01, BR-02, SB-01 |
| AC-001-03 | a valid external submission whose text fields have leading and trailing spaces | it is submitted | it is accepted and the stored and returned values have no leading or trailing whitespace | BR-02 |
| AC-001-04 | an otherwise valid submission | the email is not a valid email address | the response is 400 with a field error on `email`; nothing stored | BR-03 |
| AC-001-05 | an otherwise valid submission | it contains an unknown option id, an inactive option id or the same option id twice | the response is 400 with a field error on `optionIds`; nothing stored | BR-04, SR-04 |
| AC-001-06 | an otherwise valid submission | a mandatory consent is missing or not given, or an unknown consent id is sent | the response is 400 with a field error on `consents`; nothing stored | BR-05 |
| AC-001-07 | reCAPTCHA test mode is on | the reCAPTCHA token is missing or is not the test-mode pass token | the response is 400 with a field error on `recaptchaToken`; nothing stored | SR-01 |
| AC-001-08 | reCAPTCHA test mode is off and the verification endpoint is a mock | a submission is made | the backend sends the secret and the token to the verification endpoint; a token the endpoint accepts gives 201, a token it rejects (or an unreachable endpoint) gives 400 on `recaptchaToken` with nothing stored | SR-01, DoD-P05 |
| AC-001-09 | a stored registration with an email address | a second registration with the same address in any letter case is submitted | the response is 409 with a field error on `email`; the first registration is unchanged and nothing new is stored | D-10 (OQ-05) |
| AC-001-10 | the registration rate limit is N requests per minute per client | a client sends more than N registration requests within one minute | the requests above N get 429; nothing is stored for them | SR-03, SB-06 |
| AC-001-11 | the request-size limit | a registration request body is larger than the limit | the response is 413; nothing stored | SR-03 |
| AC-001-12 | the registration page | a participant chooses the external participant form | the UI shows exactly the fields first name, last name, email and organization / institution, the active options, and every consent checkbox unchecked | BR-01, BR-05, SB-14 |
| AC-001-13 | an external submission whose names and organization contain č, š, ž (and upper-case Č, Š, Ž) | it is submitted | it is accepted and the returned and stored values are unchanged | BR-03, NFR-01 |
| AC-001-14 | an otherwise valid external submission | it also contains a field that is not part of the external form (for example `studentId`) or an unknown field | the response is 400; nothing stored | BR-01 |
| AC-001-15 | the backend configuration | it is started with the `production` profile and reCAPTCHA test mode on, or with test mode off and an empty site or secret key | the backend refuses to start; with no test-mode setting, test mode is off | SR-02 |

## US-002 Student registration

| AC | Given | When | Then | Rules |
|---|---|---|---|---|
| AC-002-01 | active options in every category and the mandatory consents | a student submits first name, last name, email, study institution, study programme, student ID, active option ids from any category, the consents and a valid token | the response is 201 with a registration id; the registration is stored with type student | BR-01, BR-04, D-06 (OQ-01) |
| AC-002-02 | an otherwise valid student submission | one student field (study institution, study programme, student ID, or a common field) is missing, empty or only whitespace, or it contains `organization` | the response is 400 with a field error naming that field; nothing stored | BR-01, BR-02 |
| AC-002-03 | the registration page | a participant chooses the student form | the UI shows exactly the fields first name, last name, email, study institution, study programme and student ID, the active options, and every consent checkbox unchecked | BR-01, BR-05 |

## US-003 Configurable conference options

| AC | Given | When | Then | Rules |
|---|---|---|---|---|
| AC-003-01 | an options configuration with active and inactive options | a client requests the options from the API | the response lists exactly the active options with id, name and category, and the configured consents with id, text and required flag; inactive options are absent | BR-04 |
| AC-003-02 | the backend started with a different options configuration file | a client requests the options | the response reflects that file, with no code change and with the same participant fields | AR-04, BR-01 |
| AC-003-03 | an options configuration with a duplicate id, an unknown category, or a missing name | the backend starts | it refuses to start | AR-04 |
| AC-003-04 | the registration page | it loads | the UI groups the active options under the headings Workshops, Events, Meals and Other activities, and shows no inactive option | BR-04 |

## US-004 Registration confirmation

| AC | Given | When | Then | Rules |
|---|---|---|---|---|
| AC-004-01 | a correctly filled form | the participant submits it and the API accepts it | the UI replaces the form with a confirmation showing the participant's name and the selected options | BR-06 |
| AC-004-02 | a form the API rejects with field errors (for example a duplicate email) | the participant submits it | no confirmation is shown; the error message is shown next to the field concerned and the entered values stay in the form | BR-06, NFR-03 |
| AC-004-03 | a form with an empty required field or an invalid email | the participant submits it | the UI shows a message next to each invalid field and sends no request to the API | NFR-03 |

## US-005 Reliable registration storage

| AC | Given | When | Then | Rules |
|---|---|---|---|---|
| AC-005-01 | a valid submission | it is accepted | the database holds one registration row with every submitted field, its selected option ids and each consent with the time it was given | BR-07, SB-14 |
| AC-005-02 | a valid submission | it is accepted | a JSON copy named `<registration id>.json` exists in the configured directory, valid against `02_contracts/registration-copy.schema.json`, with the accepted data | BR-07 |
| AC-005-03 | the JSON copy directory cannot be written | a valid submission is made | the response is 500 with a generic message and no internal details; no database row remains | AR-05, BR-06, ES-07 |

## US-006 Participant email confirmation

| AC | Given | When | Then | Rules |
|---|---|---|---|---|
| AC-006-01 | a valid submission | it is accepted | one plain-text UTF-8 email arrives at the participant's address containing the conference name, the participant's name and the names of the selected options | US-006, NFR-01 |
| AC-006-02 | a submission with markup (`<b>`, `<script>`) in a text field, or with a CR or LF character in any field | it is submitted | markup is accepted and appears literally in a `text/plain` email; a CR or LF gives 400 with nothing stored and no email | SR-05 |
| AC-006-03 | the SMTP server is unreachable | a valid submission is made | the response is still 201 and the registration and its JSON copy are stored | D-08 (OQ-03), BR-07 |

## US-007 Organizer notification

| AC | Given | When | Then | Rules |
|---|---|---|---|---|
| AC-007-01 | configured organizer addresses | a registration is accepted | each organizer address receives an email containing the submitted data and one attachment `registration-<id>.json` of type `application/json` whose content equals the JSON copy | US-007, BR-07 |
| AC-007-02 | an accepted registration | the organizer email is sent | its body and attachment contain only registration data: the fields listed in `registration-copy.schema.json`, and no reCAPTCHA token, database key or other system data | SR-07 |

## US-008 Registration export

| AC | Given | When | Then | Rules |
|---|---|---|---|---|
| AC-008-01 | stored registrations, one with Slovenian characters | the organizer requests the export with valid organizer credentials | the response is 200 with an `.xlsx` workbook holding one row per registration under the documented header row only, with text unchanged | US-008, NFR-01, SR-07 |
| AC-008-02 | stored registrations | the export is requested without credentials or with wrong credentials | the response is 401 and contains no registration data | BR-08, SB-02 |
| AC-008-03 | organizer HTTPS-only access is on | the export is requested with valid credentials over plain HTTP | the response is 403 with no registration data; the same request forwarded by the proxy as HTTPS succeeds | SR-06 |
| AC-008-04 | the export rate limit is M requests per minute per client | a client requests the export more than M times in one minute | the requests above M get 429 | SR-03, SB-06 |

## Traceability: story → criteria

| Story | Criteria |
|---|---|
| US-001 | AC-001-01 … AC-001-15 |
| US-002 | AC-002-01 … AC-002-03 |
| US-003 | AC-003-01 … AC-003-04 |
| US-004 | AC-004-01 … AC-004-03 |
| US-005 | AC-005-01 … AC-005-03 |
| US-006 | AC-006-01 … AC-006-03 |
| US-007 | AC-007-01, AC-007-02 |
| US-008 | AC-008-01 … AC-008-04 |

Non-functional requirements checked outside these criteria: NFR-01 also by an end-to-end test through the UI; NFR-02 and NFR-04 by the phase 6 runtime demonstration.
