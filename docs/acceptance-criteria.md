# Acceptance Criteria

**Phase 1 artefact.** Derived from `USER_STORIES.md`, `PROJECT_CONSTRAINTS.md` and `FORM_SCHEMA.md`.

These Acceptance Criteria (AC) are the observable, testable contract for the Conference Registration System.
They contain no technical design decisions; those belong to `docs/specification.md` (Phase 2).

## Conventions

* **AC-<US>-<n>** — criterion traceable to a User Story.
* **AC-G-<n>** — cross-cutting criterion derived from `PROJECT_CONSTRAINTS.md` / `FORM_SCHEMA.md` that applies to more than one User Story.
* Every criterion is written so that it can be evaluated by observing system behaviour (HTTP response, database content, file on disk, email produced, rendered UI) without reading the implementation.
* "Registration form" means either of the two variants unless a variant is named explicitly.
* "Successful registration" means the backend accepted the submission and completed processing (see AC-005-*).
* Terms such as *rejected*, *accepted*, *stored*, *sent* refer to externally observable outcomes only.

## Fixed field schema (from `FORM_SCHEMA.md`, restated for testability)

| Variant | Fixed participant fields |
| --- | --- |
| External participant | First name, Last name, Email, Organization / institution |
| Student | First name, Last name, Email, Study institution, Study programme, Student ID |

Configurable option groups (both variants): **workshops**, **events**, **meals**, **other optional activities**.
Each configurable option has at least a stable identifier, a display name and an active/inactive status.

---

## US-001 — External participant registration

> As an external conference participant, I want to register for the conference using the external participant registration form, so that I can attend the conference and selected conference activities.

### Happy path

* **AC-001-01** An external participant registration form is reachable in the frontend and presents exactly the fixed external-participant fields: First name, Last name, Email, Organization / institution.
* **AC-001-02** The external form presents the currently **active** configurable options, grouped by workshops, events, meals and other optional activities. Inactive options are not offered.
* **AC-001-03** Selecting zero, one or several active options is possible; option selection is never mandatory.
* **AC-001-04** Submitting the form with all required fixed fields valid, mandatory consent given and any subset of active options selected results in the registration being accepted by the backend and reported as successful.
* **AC-001-05** An accepted external registration is stored with participant type `external` and with exactly the option identifiers that were submitted — no more, no fewer.

### Invalid input

* **AC-001-06** Submitting the external form with any required fixed field empty or whitespace-only is rejected, and the response identifies each offending field.
* **AC-001-07** Submitting the external form without the mandatory consent is rejected and identifies the consent field.
* **AC-001-08** Submitting the external form with a syntactically invalid email address (for example `a@`, `a b@c.d`, `no-at-sign`) is rejected and identifies the email field.
* **AC-001-09** A rejected external registration causes no persisted registration, no JSON backup file and no email of any kind.

### Edge cases

* **AC-001-10** Fixed text fields accept Unicode, including Slovenian characters (`Č č Š š Ž ž`), and the stored value is byte-for-byte the submitted value after whitespace trimming.
* **AC-001-11** Leading and trailing whitespace in submitted text fields is not significant: `"  Ana  "` is accepted and stored as `"Ana"`; a field containing only whitespace is treated as empty and rejected.
* **AC-001-12** Field values longer than the documented per-field maximum length are rejected with a message identifying the field, and no partially-truncated value is stored.
* **AC-001-13** Student-only fields (Study institution, Study programme, Student ID) submitted to the external variant are not stored; their presence never changes the participant type of the stored registration.

---

## US-002 — Student registration

> As a student, I want to register for the conference using the student registration form, so that I can attend the conference and activities available to students.

### Happy path

* **AC-002-01** A student registration form is reachable in the frontend, at a location distinct from the external form, and presents exactly the fixed student fields: First name, Last name, Email, Study institution, Study programme, Student ID.
* **AC-002-02** The student form presents the currently **active** configurable options, grouped by workshops, events, meals and other optional activities; options marked as unavailable to students are not offered.
* **AC-002-03** Submitting the student form with all required fixed fields valid, mandatory consent given and any subset of active options available to students is accepted by the backend and reported as successful.
* **AC-002-04** An accepted student registration is stored with participant type `student` and with exactly the submitted option identifiers.

### Invalid input

* **AC-002-05** Submitting the student form with any required fixed field empty or whitespace-only is rejected, identifying each offending field — including the student-specific fields.
* **AC-002-06** Submitting the student form without the mandatory consent is rejected.
* **AC-002-07** Submitting the student form with an invalid email is rejected and identifies the email field.
* **AC-002-08** Submitting the student form with an option that is offered only to external participants is rejected and identifies the offending option identifier.
* **AC-002-09** A rejected student registration causes no persisted registration, no JSON backup file and no email.

### Edge cases

* **AC-002-10** Student ID values are accepted in the documented format; values violating it are rejected with a message identifying the field.
* **AC-002-11** The same Unicode and whitespace rules as AC-001-10 and AC-001-11 apply to all student fixed fields.
* **AC-002-12** Organization / institution submitted to the student variant is not stored as a student field and never changes the participant type.

---

## US-003 — Configurable conference options

> As a conference organizer, I want to change the available workshops and optional conference activities without changing the fixed participant-data fields, so that the same registration system can be reused when the conference programme changes.

* **AC-003-01** Workshops, events, meals and other optional activities are defined in configuration outside the application source code, and each option carries at least a stable identifier, a display name and an active/inactive status.
* **AC-003-02** Adding, removing, renaming or deactivating an option requires no change to the fixed participant fields, to the database schema of the participant fields, or to the registration API request shape.
* **AC-003-03** After an option is added and activated, it is offered by the relevant form(s) and a registration selecting it is accepted.
* **AC-003-04** After an option is deactivated, it is no longer offered by any form, and a registration selecting it is rejected with a message identifying the offending option identifier.
* **AC-003-05** A registration selecting an identifier that exists in no configuration (unknown identifier) is rejected with a message identifying the offending identifier.
* **AC-003-06** A configuration containing duplicate option identifiers, a missing identifier, a missing display name or a missing active flag is detected and reported as a configuration error; the application does not serve registrations against an invalid configuration.
* **AC-003-07** Registrations already stored before an option was deactivated or renamed remain readable and exportable, and retain the option identifier as submitted.
* **AC-003-08** The frontend obtains the option list from the backend at runtime; it contains no hard-coded option list.
* **AC-003-09** Submitting the same option identifier several times in one registration does not create duplicate stored selections.

---

## US-004 — Registration confirmation in the application

> As a participant, I want to receive clear confirmation after my registration has been successfully processed, so that I know that my registration was received.

* **AC-004-01** The frontend displays the success confirmation **only after** it has received a success response from the backend REST API; no confirmation is shown on submit, on timeout, or on any non-success response.
* **AC-004-02** The confirmation is clear and identifies the registration: it states that the registration was received and shows a registration reference returned by the backend.
* **AC-004-03** The confirmation states that a confirmation email has been sent to the submitted email address, and shows that address.
* **AC-004-04** When the backend rejects the submission because of validation, the frontend shows the validation messages for the affected fields and shows no success confirmation.
* **AC-004-05** When the backend is unreachable or returns a server error, the frontend shows an error message that distinguishes a technical failure from a validation failure, shows no success confirmation, and keeps the entered data so the participant can retry.
* **AC-004-06** While a submission is in flight the submit control is disabled, so a single user action cannot produce two submissions.
* **AC-004-07** The confirmation is reachable and readable on the supported mobile viewport width (see AC-G-12).

---

## US-005 — Reliable registration storage

> As a conference organizer, I want every successfully submitted registration to be stored reliably and backed up, so that participant registrations are not lost and can be recovered if necessary.

* **AC-005-01** Every successfully processed registration exists as a row in a relational database, containing the fixed participant fields of its variant, the participant type, the selected option identifiers, the consent record and a creation timestamp.
* **AC-005-02** Every successfully processed registration also exists as a JSON file on persistent storage, containing the same registration data and the same registration reference as the database row.
* **AC-005-03** The JSON file name is stable, unique per registration and derivable from the registration reference.
* **AC-005-04** A success response is returned only after both the database row and the JSON file are durably written. If either fails, the registration is reported as failed and no partial registration remains in the database.
* **AC-005-05** Email delivery is not a precondition for storage: if sending an email fails, the registration remains stored in both the database and the JSON file, and the failure is recorded in the application log.
* **AC-005-06** Concurrent submissions are each stored exactly once, with distinct registration references and distinct JSON files.
* **AC-005-07** The stored data survives an application restart: registrations stored before a restart are still retrievable and exportable afterwards.
* **AC-005-08** The set of JSON backup files is sufficient to reconstruct the registration list without the database: each file is self-contained and includes the participant type, all fixed fields, the selected option identifiers and the timestamp.

---

## US-006 — Participant email confirmation

> As a registered participant, I want to receive an email confirming my registration, so that I have a record of my successful registration.

* **AC-006-01** After a successful registration, exactly one confirmation email is produced, addressed to the email address submitted in the registration.
* **AC-006-02** The confirmation email contains the registration reference, the participant's name, the participant type and the display names of the selected options.
* **AC-006-03** The confirmation email is produced only for successful registrations; rejected submissions produce no participant email.
* **AC-006-04** Failure to send the participant email does not fail the registration (see AC-005-05) and is recorded in the application log.
* **AC-006-05** Participant-supplied values appearing in the email are escaped/encoded so that they are rendered as text and cannot inject markup or additional email headers.

---

## US-007 — Organizer notification

> As a conference organizer, I want to be notified when a new participant registers and receive the submitted registration information, so that I have an additional record of every registration and can follow registrations as they arrive.

* **AC-007-01** After a successful registration, exactly one organizer notification email is produced, addressed to the configured organizer recipient(s).
* **AC-007-02** The organizer notification contains the submitted registration information: registration reference, participant type, all fixed fields of the variant, selected options and the creation timestamp.
* **AC-007-03** The organizer notification has the registration JSON file attached, and the attachment content is identical to the JSON file stored on persistent storage for that registration.
* **AC-007-04** The organizer notification is produced only for successful registrations.
* **AC-007-05** Failure to send the organizer notification does not fail the registration and is recorded in the application log.
* **AC-007-06** The organizer recipient list is configuration, changeable without a code change.
* **AC-007-07** The participant is not disclosed as a recipient of the organizer notification, and organizer addresses are not disclosed to the participant.

---

## US-008 — Excel export

> As a conference organizer, I want to export the current list of registered participants to an Excel file at any time, so that I can process, review and share registration data outside the registration system.

* **AC-008-01** An organizer can request an export and receives an Excel workbook file (`.xlsx`) that opens in a spreadsheet application.
* **AC-008-02** The export contains one row per stored registration and a header row naming each column.
* **AC-008-03** The export columns cover the registration reference, creation timestamp, participant type, all fixed fields of both variants (empty where not applicable to the variant), the selected options and the consent record.
* **AC-008-04** The export reflects the current state of storage: a registration accepted before the export request appears in that export.
* **AC-008-05** Unicode values, including Slovenian characters, are exported without corruption.
* **AC-008-06** Requesting the export when no registrations exist returns a valid workbook containing only the header row.
* **AC-008-07** The export endpoint is not publicly accessible: an unauthenticated or incorrectly authenticated request is rejected and returns no registration data.
* **AC-008-08** Cell values that begin with a spreadsheet formula character (`=`, `+`, `-`, `@`) are exported so that a spreadsheet application does not evaluate them as formulas.
* **AC-008-09** The export response carries a filename and the Excel content type, so a browser saves it as a spreadsheet file.

---

## Cross-cutting criteria (from `PROJECT_CONSTRAINTS.md` and `FORM_SCHEMA.md`)

### Frontend/backend communication

* **AC-G-01** Registrations are submitted from the frontend to the backend over a REST API using JSON, and the API is documented in the specification.
* **AC-G-02** The backend distinguishes successful processing from validation rejection and from technical failure using distinct HTTP status codes, and the frontend behaves differently in each case (AC-004-01, AC-004-04, AC-004-05).

### Validation

* **AC-G-03** Every validation rule enforced by the frontend is also enforced by the backend; a request sent directly to the API bypassing the frontend is subject to the full rule set.
* **AC-G-04** Validation rejections identify every offending field in one response, rather than only the first one.
* **AC-G-05** Validation messages are human-readable and state what is wrong with the field, without exposing internal implementation details (stack traces, SQL, file paths).
* **AC-G-06** A request body that is not valid JSON, is missing entirely, has wrong field types, or contains unknown fields is rejected without a server error.
* **AC-G-07** Configurable option identifiers submitted by the client are validated against the active configuration for the submitted variant; inactive and unknown identifiers are rejected (AC-003-04, AC-003-05).
* **AC-G-08** Mandatory consent is never preselected in the frontend.

### Security

* **AC-G-09** Malicious input is neutralised: values containing HTML/script (`<script>alert(1)</script>`), SQL metacharacters (`'; DROP TABLE registrations; --`), CRLF sequences and oversized payloads do not execute, do not corrupt storage and do not cause a server error — they are either rejected by validation or stored and rendered inertly.
* **AC-G-10** Automated/bot submissions are resisted: a submission that does not satisfy the anti-automation control is rejected without being stored, and repeated rapid submissions from the same origin are rate-limited with a distinct response.
* **AC-G-11** Backend preventive security controls are present and observable: security response headers, restrictive CORS, a request body size limit, and no leakage of stack traces or internal paths in any error response. Submitted participant data is not written to logs in a way that exposes personal data unnecessarily.

### Frontend quality

* **AC-G-12** Both forms and the confirmation are usable without horizontal scrolling at a narrow mobile viewport (375 px wide) and at a desktop viewport (1280 px wide); all controls remain reachable and labelled.
* **AC-G-13** Every input is associated with a visible label, validation errors are associated with their input, and the forms are operable by keyboard.

### Deployment

* **AC-G-14** The application can be built and run as containers, with a documented single command that starts the full system (frontend, backend, database).
* **AC-G-15** All environment-specific values (database connection, SMTP settings, organizer recipients, export credentials, storage path, option configuration) are supplied as configuration/environment variables and none are hard-coded; the application refuses to start with a clear message when a required setting is missing.
* **AC-G-16** Persistent data (database contents and JSON backup files) survives a container restart.

### Documentation

* **AC-G-17** The repository contains `docs/acceptance-criteria.md`, `docs/specification.md`, `docs/verification-report.md`, `RELEASE_NOTES.md` and `experiment/run-log.json`, and the architecture, validation strategy, security controls, testing strategy and deployment decisions are documented and justified.

---

## Traceability matrix

| User Story | Acceptance Criteria |
| --- | --- |
| US-001 External participant registration | AC-001-01 … AC-001-13, AC-G-01…AC-G-13 |
| US-002 Student registration | AC-002-01 … AC-002-12, AC-G-01…AC-G-13 |
| US-003 Configurable conference options | AC-003-01 … AC-003-09, AC-G-07 |
| US-004 Registration confirmation in the application | AC-004-01 … AC-004-07, AC-G-02 |
| US-005 Reliable registration storage | AC-005-01 … AC-005-08, AC-G-16 |
| US-006 Participant email confirmation | AC-006-01 … AC-006-05 |
| US-007 Organizer notification | AC-007-01 … AC-007-07 |
| US-008 Excel export | AC-008-01 … AC-008-09, AC-G-11 |

| Constraint (`PROJECT_CONSTRAINTS.md`) | Acceptance Criteria |
| --- | --- |
| Two registration variants | AC-001-01, AC-002-01 |
| Fixed participant fields | AC-001-01, AC-002-01, AC-001-13, AC-002-12 |
| Configurable workshops/events/meals/other | AC-003-01 … AC-003-09 |
| REST submission; confirmation only after success response | AC-G-01, AC-004-01 |
| Relational database persistence | AC-005-01, AC-005-07 |
| JSON representation on persistent storage | AC-005-02, AC-005-03, AC-005-08 |
| Participant confirmation email | AC-006-01 … AC-006-05 |
| Organizer notification email with JSON attachment | AC-007-01 … AC-007-07 |
| Excel export | AC-008-01 … AC-008-09 |
| Frontend validation | AC-G-03, AC-G-13, AC-004-04 |
| Backend validation and error messages | AC-G-03 … AC-G-06 |
| Protection against malicious input | AC-G-09 |
| Protection against automated submissions | AC-G-10 |
| Backend preventive security controls | AC-G-11, AC-008-07 |
| Secure handling of participant data | AC-G-11, AC-006-05, AC-008-08 |
| Responsive frontend | AC-G-12 |
| Containerized deployment | AC-G-14, AC-G-15, AC-G-16 |
| Production-ready, documented and justified | AC-G-17 |

| `FORM_SCHEMA.md` rule | Acceptance Criteria |
| --- | --- |
| Fixed field sets per variant | AC-001-01, AC-002-01 |
| Option: identifier, display name, active status | AC-003-01, AC-003-06 |
| Mandatory consent, not preselected | AC-001-07, AC-002-06, AC-G-08 |
| Required fields not empty | AC-001-06, AC-002-05 |
| Valid email format | AC-001-08, AC-002-07 |
| Unicode incl. Slovenian characters | AC-001-10, AC-002-11, AC-008-05 |
| Whitespace not significant | AC-001-11, AC-002-11 |
| Validation on frontend and backend | AC-G-03 |
| Client option identifiers validated by backend | AC-G-07, AC-003-04, AC-003-05 |
| Inactive/unknown options not accepted | AC-003-04, AC-003-05 |

---

## Out of scope

Derived from the inputs, the following are explicitly **not** acceptance criteria and must not be implemented:

* participant self-service login, registration editing or cancellation;
* payment or invoicing;
* an organizer administration UI beyond the Excel export;
* multi-conference or multi-tenant support;
* public listing of registered participants.
