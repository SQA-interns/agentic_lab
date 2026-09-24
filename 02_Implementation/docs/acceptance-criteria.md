# Acceptance Criteria — Conference Registration System

Derived from `01_Input_Files/USER_STORY.md`, `PROJECT_CONSTRAINTS.md`
(cited as **PC §<section>**) and `FORM_SCHEMA.md` (cited as **FS §<section>**),
per `ACCEPTANCE_CRITERIA_RULES.md`.

Format: Given–When–Then, one observable behaviour per criterion.
Numbering: `AC-<story>-<seq>`; numbers are never reassigned.
Each criterion is tagged with its category: **[happy]**, **[invalid]** or **[edge]**.

---

## US-001 — External participant registration

**AC-001-01** [happy] — *Source: US-001, PC §Registration forms, FS §External participant registration*
Given the external registration form with first name, last name, a valid email, organization and the mandatory privacy consent checked,
When the participant submits the form,
Then the backend accepts the registration through the REST API and responds with a success status containing a registration identifier.

**AC-001-02** [happy] — *Source: US-001, FS §External participant registration*
Given the external registration form is opened,
When it is rendered,
Then it shows exactly the fixed fields First name, Last name, Email and Organization / institution (and no student-only fields).

**AC-001-03** [invalid] — *Source: US-001, FS §General field rules, PC §Validation and security*
Given the external form with one required fixed field left empty,
When the participant attempts to submit,
Then the frontend blocks submission and shows a field-specific validation message.

**AC-001-04** [invalid] — *Source: US-001, FS §General field rules, PC §Validation and security*
Given a request to the external registration API with a malformed email,
When the backend receives it (bypassing the frontend),
Then the backend rejects it with HTTP 400 and a field-level error for `email`, and nothing is stored.

**AC-001-05** [invalid] — *Source: US-001, FS §Consent*
Given the external form where the mandatory privacy consent is not checked,
When the participant submits (via UI or directly via the API),
Then the registration is rejected with a consent validation error.

**AC-001-06** [edge] — *Source: FS §Consent*
Given the external form is opened for the first time,
When it is rendered,
Then the mandatory consent checkbox is not preselected.

**AC-001-07** [edge] — *Source: FS §General field rules*
Given text fields containing Slovenian characters (e.g. "Žiga Čeh", "Univerza v Mariboru – FERI, Šolska"),
When the external registration is submitted,
Then it is accepted and the stored values preserve those characters exactly.

**AC-001-08** [edge] — *Source: FS §General field rules*
Given field values with leading and trailing whitespace (e.g. "  Ana  "),
When the external registration is submitted,
Then the values are stored trimmed ("Ana").

**AC-001-09** [edge] — *Source: FS §General field rules*
Given a required field containing only whitespace,
When it is submitted to the backend,
Then the backend treats it as empty and rejects it with HTTP 400.

---

## US-002 — Student registration

**AC-002-01** [happy] — *Source: US-002, PC §Registration forms, FS §Student registration*
Given the student form with first name, last name, valid email, study institution, study programme, student ID and the mandatory consent checked,
When the student submits the form,
Then the backend accepts the registration through the REST API and responds with a success status containing a registration identifier.

**AC-002-02** [happy] — *Source: US-002, FS §Student registration*
Given the student registration form is opened,
When it is rendered,
Then it shows exactly the fixed fields First name, Last name, Email, Study institution, Study programme and Student ID.

**AC-002-03** [invalid] — *Source: US-002, FS §General field rules*
Given a student registration API request with the student ID missing,
When the backend receives it,
Then it is rejected with HTTP 400 and a field-level error for `studentId`.

**AC-002-04** [invalid] — *Source: US-002, FS §General field rules*
Given the student form with a malformed email,
When the student attempts to submit,
Then the frontend blocks submission and shows an email-format validation message.

**AC-002-05** [edge] — *Source: US-002, PC §Registration forms*
Given a request to the student endpoint that carries external-only data (organization) instead of the student fields,
When the backend receives it,
Then it is rejected with HTTP 400 (the variants' field sets are not interchangeable).

**AC-002-06** [edge] — *Source: US-002 (activities available to students), US-003*
Given a configurable option that is active but not available to students,
When a student registration selects it,
Then the backend rejects the registration with HTTP 400.

**AC-002-07** [edge] — *Source: US-002 (activities available to students), US-003*
Given a configurable option that is not available to students,
When the student form is rendered,
Then that option is not offered.

---

## US-003 — Configurable conference options

**AC-003-01** [happy] — *Source: US-003, FS §Configurable conference options*
Given a configuration defining workshops, events, meals and other activities, each with a stable identifier, display name and active flag,
When the registration form is loaded,
Then the active options are displayed grouped by category with their display names.

**AC-003-02** [happy] — *Source: US-003, PC §Registration forms*
Given an organizer changes the options configuration (adds, removes or deactivates an option) without changing any code or fixed fields,
When the application is restarted,
Then the form and backend reflect the new option set.

**AC-003-03** [invalid] — *Source: FS §General field rules*
Given a registration request that contains an unknown option identifier,
When the backend receives it,
Then it is rejected with HTTP 400 and nothing is stored.

**AC-003-04** [invalid] — *Source: FS §General field rules*
Given an option that exists in the configuration but is inactive,
When a registration request selects it,
Then the backend rejects it with HTTP 400.

**AC-003-05** [edge] — *Source: FS §General field rules*
Given an inactive option in the configuration,
When the form is loaded,
Then the inactive option is not displayed.

**AC-003-06** [edge] — *Source: US-003*
Given a registration selecting no optional activities at all,
When it is submitted,
Then it is accepted (options are optional).

**AC-003-07** [edge] — *Source: US-003, FS §Configurable conference options*
Given an options configuration with a duplicate identifier or a missing identifier/display name,
When the application starts,
Then startup fails with an explicit configuration error rather than running with an ambiguous option set.

---

## US-004 — Registration confirmation in the application

**AC-004-01** [happy] — *Source: US-004, PC §Frontend/backend communication*
Given a valid form submission,
When the backend responds with success,
Then the application shows a confirmation message to the participant.

**AC-004-02** [invalid] — *Source: US-004, PC §Frontend/backend communication*
Given a submission that the backend rejects with a validation error,
When the response arrives,
Then no confirmation is shown and the backend's field errors are displayed on the form.

**AC-004-03** [edge] — *Source: US-004, PC §Frontend/backend communication*
Given the backend is unreachable or returns a server error,
When the participant submits,
Then no confirmation is shown and a general error message is displayed, keeping the entered data.

**AC-004-04** [edge] — *Source: US-004*
Given a submission is in progress,
When the participant clicks submit again,
Then no second request is sent (submit is disabled until the response arrives).

---

## US-005 — Reliable registration storage

**AC-005-01** [happy] — *Source: US-005, PC §Persistence and backup*
Given a valid registration,
When it is processed successfully,
Then a row for it exists in the relational database including its fixed fields and selected options.

**AC-005-02** [happy] — *Source: US-005, PC §Persistence and backup*
Given a valid registration,
When it is processed successfully,
Then a JSON file representing the registration exists on persistent storage.

**AC-005-03** [invalid] — *Source: US-005, PC §Persistence and backup*
Given an invalid registration,
When it is rejected,
Then no database row and no JSON backup file are created.

**AC-005-04** [edge] — *Source: US-005 ("stored reliably")*
Given the JSON backup cannot be written (e.g. storage failure),
When a registration is processed,
Then the registration is not reported as successful and the database transaction is rolled back.

**AC-005-05** [edge] — *Source: US-005, PC §Deployment*
Given the application containers are restarted,
When stored registrations and backup files are inspected,
Then previously stored registrations and JSON files are still present (persistent volumes).

**AC-005-06** [edge] — *Source: US-005*
Given the JSON backup of a registration,
When it is read,
Then it contains the registration id, type, fixed fields, selected option identifiers, consent and creation timestamp sufficient to recover the registration.

---

## US-006 — Participant email confirmation

**AC-006-01** [happy] — *Source: US-006, PC §Email*
Given a successfully processed registration,
When processing completes,
Then a confirmation email is sent to the participant's email address.

**AC-006-02** [invalid] — *Source: US-006, PC §Email*
Given a rejected (invalid) registration,
When processing ends,
Then no confirmation email is sent.

**AC-006-03** [edge] — *Source: US-006, US-005*
Given the mail server is unavailable,
When a valid registration is processed,
Then the registration remains stored and the participant still receives the in-app success response, and the email failure is logged.

**AC-006-04** [edge] — *Source: US-006, FS §General field rules*
Given a participant name containing Slovenian characters,
When the confirmation email is sent,
Then it is UTF-8 encoded and the name renders correctly.

---

## US-007 — Organizer notification

**AC-007-01** [happy] — *Source: US-007, PC §Email*
Given a successfully processed registration,
When processing completes,
Then a notification email is sent to the configured organizer address(es) containing the registration information in its body.

**AC-007-02** [happy] — *Source: US-007, PC §Email*
Given a successfully processed registration,
When the organizer notification is sent,
Then it carries the registration JSON file as an attachment.

**AC-007-03** [invalid] — *Source: US-007*
Given a rejected (invalid) registration,
When processing ends,
Then no organizer notification is sent.

**AC-007-04** [edge] — *Source: US-007, PC §Validation and security*
Given participant input containing HTML/script markup,
When the organizer notification is generated,
Then the markup is not rendered as active HTML in the email (it is escaped or sent as plain text).

---

## US-008 — Excel export

**AC-008-01** [happy] — *Source: US-008, PC §Export*
Given stored registrations,
When an authenticated organizer requests the export,
Then an `.xlsx` file is downloaded containing one row per registration with the fixed fields, type, selected options and timestamp.

**AC-008-02** [invalid] — *Source: US-008, PC §Validation and security (secure handling of participant data)*
Given a request to the export without valid organizer credentials,
When the backend receives it,
Then it responds with HTTP 401 and no data.

**AC-008-03** [edge] — *Source: US-008*
Given no registrations exist,
When the organizer requests the export,
Then a valid `.xlsx` with only the header row is returned.

**AC-008-04** [edge] — *Source: US-008 ("at any time", "current list")*
Given a registration is added after a previous export,
When the organizer exports again,
Then the new export includes the newly added registration.

**AC-008-05** [edge] — *Source: US-008, PC §Validation and security (malicious input)*
Given a registration whose text field starts with a formula character (`=`, `+`, `-`, `@`),
When it is exported,
Then the cell is written as inert text so it is not evaluated as a formula in Excel.

---

## PC — Cross-cutting project constraints

Criteria below trace to `PROJECT_CONSTRAINTS.md` clauses not owned by a single story.

**AC-PC-01** [happy] — *Source: PC §Validation and security (malicious input)*
Given a text field containing `<script>alert(1)</script>`,
When the registration is submitted and later displayed or exported,
Then it is stored as literal text and never executed.

**AC-PC-02** [invalid] — *Source: PC §Validation and security (malicious input)*
Given a text field exceeding its maximum length,
When submitted to the backend,
Then it is rejected with HTTP 400.

**AC-PC-03** [invalid] — *Source: PC §Validation and security (automated/bot submissions)*
Given a submission where the hidden honeypot field is filled,
When the backend receives it,
Then the registration is not stored.

**AC-PC-04** [edge] — *Source: PC §Validation and security (automated/bot submissions)*
Given a submission sent faster than a human could plausibly fill the form (below the minimum fill time),
When the backend receives it,
Then it is rejected.

**AC-PC-05** [edge] — *Source: PC §Validation and security (automated/bot submissions)*
Given a single client IP exceeding the registration rate limit,
When it sends a further registration request within the window,
Then the backend responds with HTTP 429.

**AC-PC-06** [edge] — *Source: PC §Validation and security (backend preventive security controls)*
Given any backend response,
When it is received,
Then it contains security headers (`X-Content-Type-Options: nosniff`, `X-Frame-Options`/frame-ancestors, `Content-Security-Policy`, `Referrer-Policy`).

**AC-PC-07** [invalid] — *Source: PC §Validation and security (backend preventive security controls)*
Given a request with a malformed JSON body or unsupported content type,
When the backend receives it,
Then it responds with a 4xx error without exposing stack traces or internal details.

**AC-PC-08** [happy] — *Source: PC §Frontend quality*
Given the form is opened on a mobile-width viewport (≤ 400 px),
When rendered,
Then all fields and the submit button are usable without horizontal scrolling.

**AC-PC-09** [happy] — *Source: PC §Deployment, TECH_STACK §Deployment target*
Given the docker-compose deployment,
When it is started,
Then the backend health endpoint reports `UP` and the frontend serves the registration form.

**AC-PC-10** [edge] — *Source: PC §Validation and security (secure handling of participant data)*
Given a request for a registration's stored data via any public (unauthenticated) endpoint,
When sent,
Then no endpoint returns other participants' personal data (only export, which requires organizer authentication).
