# Conference Registration System — Acceptance Criteria

This document derives observable, testable acceptance criteria from `USER_STORIES.md`, `PROJECT_CONSTRAINTS.md`, and the immutable `FORM_SCHEMA.md`. The source files remain unchanged.

## US-001 — External participant registration

- **AC-001** Given the external registration variant is opened, when the form is displayed, then it contains exactly the fixed participant fields First name, Last name, Email, and Organization / institution, plus applicable configurable options and mandatory consent fields. (Sources: US-001, Form Schema)
- **AC-002** Given all required external fields contain valid values, all submitted configurable options are active and known, and every mandatory consent is selected, when the participant submits the form, then the registration is sent to the backend through the REST API and can be successfully processed. (Sources: US-001, Frontend/backend communication, Form Schema)
- **AC-003** Given any required external fixed field is empty or whitespace-only, the email format is invalid, or mandatory consent is not selected, when submission is attempted, then the frontend prevents submission and identifies each invalid field with an appropriate message. (Sources: US-001, Validation and security, Form Schema)
- **AC-004** Given an external registration payload that is invalid, malformed, or omits a required value reaches the backend, when it is processed, then the backend rejects it with a validation response that identifies actionable field errors and does not report success. (Sources: US-001, Validation and security, Form Schema)
- **AC-005** Given valid external text includes Unicode, including Slovenian characters, and/or leading or trailing whitespace, when the registration is processed, then Unicode is preserved and leading/trailing whitespace is not significant. (Sources: US-001, Form Schema)

## US-002 — Student registration

- **AC-006** Given the student registration variant is opened, when the form is displayed, then it contains exactly the fixed participant fields First name, Last name, Email, Study institution, Study programme, and Student ID, plus applicable configurable options and mandatory consent fields. (Sources: US-002, Form Schema)
- **AC-007** Given all required student fields contain valid values, all submitted configurable options are active and known, and every mandatory consent is selected, when the student submits the form, then the registration is sent to the backend through the REST API and can be successfully processed. (Sources: US-002, Frontend/backend communication, Form Schema)
- **AC-008** Given any required student fixed field is empty or whitespace-only, the email format is invalid, or mandatory consent is not selected, when submission is attempted, then the frontend prevents submission and identifies each invalid field with an appropriate message. (Sources: US-002, Validation and security, Form Schema)
- **AC-009** Given a student registration payload that is invalid, malformed, or omits a required value reaches the backend, when it is processed, then the backend rejects it with a validation response that identifies actionable field errors and does not report success. (Sources: US-002, Validation and security, Form Schema)
- **AC-010** Given valid student text includes Unicode, including Slovenian characters, and/or leading or trailing whitespace, when the registration is processed, then Unicode is preserved and leading/trailing whitespace is not significant. (Sources: US-002, Form Schema)

## US-003 — Configurable conference options

- **AC-011** Given configured workshops, events, meals, and other checkbox-based activities exist, when either registration form is loaded, then active applicable options are shown with their display names and stable identifiers, while inactive options are not selectable. (Sources: US-003, Registration forms, Form Schema)
- **AC-012** Given an organizer changes the configured option set or an option's display name or active status without changing the fixed participant schema, when the form is next loaded, then the form reflects the new configuration without a change to its fixed participant fields. (Sources: US-003, Registration forms, Form Schema)
- **AC-013** Given a client submits an unknown or inactive configurable option identifier, when the backend validates the registration, then it rejects the submission with an appropriate validation error and creates no successful registration. (Sources: US-003, Validation and security, Form Schema)
- **AC-014** Given the client submits an active configurable option identifier, when the registration is successfully processed, then the stored registration retains the stable identifier and the option information needed to identify the selection even if configuration later changes. (Sources: US-003, Reliable registration storage, Form Schema)
- **AC-015** Given no optional conference activity is selected, when all fixed fields and mandatory consents are valid, then the registration can still succeed unless an option is explicitly configured as mandatory. (Sources: US-003, Form Schema)

## US-004 — Registration confirmation in the application

- **AC-016** Given the backend has returned a successful response for a submitted registration, when the frontend receives it, then the participant sees a clear successful-registration confirmation. (Sources: US-004, Frontend/backend communication)
- **AC-017** Given the backend returns a validation or processing failure, times out, or cannot be reached, when the frontend handles the result, then it does not display successful-registration confirmation and instead displays an appropriate, non-sensitive error with a retry path where retry is safe. (Sources: US-004, Frontend/backend communication, Error/validation constraints)
- **AC-018** Given a submission is still awaiting the backend result, then the frontend does not show success and prevents accidental repeated submissions while the request remains in progress. (Sources: US-004, Frontend/backend communication, General quality)

## US-005 — Reliable registration storage

- **AC-019** Given a registration is reported as successfully processed, then a complete registration record exists in the relational database. (Sources: US-005, Persistence and backup)
- **AC-020** Given a registration is reported as successfully processed, then a valid JSON representation of that registration exists on persistent storage and can be correlated with the database record. (Sources: US-005, Persistence and backup)
- **AC-021** Given relational persistence or JSON backup creation fails, when a registration is processed, then the backend does not return a successful-registration response and does not leave a registration presented as successfully processed. (Sources: US-005, Persistence and backup, Frontend/backend communication)
- **AC-022** Given stored text contains Unicode and submitted selections, when either the relational record or JSON backup is retrieved, then the participant data, registration variant, option selections, consent evidence, identifier, and processing timestamp are preserved consistently. (Sources: US-005, Form Schema)
- **AC-023** Given concurrent valid submissions are processed, when storage completes, then each successful submission has its own identifiable database record and correlated JSON backup without overwriting another registration. (Sources: US-005, General quality)
- **AC-024** Given the application container is replaced or restarted while its configured persistent data resources remain available, when existing records and backups are inspected afterward, then previously successful registrations remain available. (Sources: US-005, Persistence and backup, Deployment)

## US-006 — Participant email confirmation

- **AC-025** Given a registration has been successfully persisted and backed up, when post-registration notifications are processed, then a confirmation email is addressed to the participant's normalized submitted email address. (Sources: US-006, Email)
- **AC-026** Given the participant confirmation email is generated, then it clearly confirms successful registration and contains sufficient non-sensitive registration context for the participant to recognize it. (Sources: US-006)
- **AC-027** Given an invalid registration is rejected or storage/backup fails, then no participant success-confirmation email is sent for that attempt. (Sources: US-006, Email, Persistence and backup)
- **AC-028** Given email delivery initially fails after the registration has been stored successfully, then the failure is recorded and the notification remains recoverable for a later delivery attempt without creating a duplicate registration. (Sources: US-005, US-006, General quality)

## US-007 — Organizer notification

- **AC-029** Given a registration has been successfully persisted and backed up, when post-registration notifications are processed, then all configured conference organizer recipients are sent a notification containing the submitted registration information. (Sources: US-007, Email)
- **AC-030** Given an organizer notification is generated, then the corresponding registration JSON file is attached with a JSON media type and its contents correlate with the successfully stored registration. (Sources: US-007, Email)
- **AC-031** Given an invalid registration is rejected or storage/backup fails, then no organizer new-registration notification is sent for that attempt. (Sources: US-007, Email, Persistence and backup)
- **AC-032** Given organizer email delivery initially fails after the registration has been stored successfully, then the failure is recorded and the notification remains recoverable for a later delivery attempt without creating a duplicate registration. (Sources: US-005, US-007, General quality)

## US-008 — Excel export

- **AC-033** Given an authorized organizer requests an export at any time, when the request succeeds, then an Excel-format file containing the current successfully stored registrations is downloaded. (Sources: US-008, Export, Secure handling)
- **AC-034** Given both external and student registrations exist, when exported, then each row identifies its registration variant and exposes the applicable fixed participant fields without losing Unicode characters. (Sources: US-008, Form Schema)
- **AC-035** Given registrations contain configurable selections, when exported, then their selected workshops, events, meals, and other activities are identifiable by stable data even if the current configuration has changed. (Sources: US-003, US-008, Form Schema)
- **AC-036** Given no registrations exist, when an authorized organizer exports, then a valid Excel file with the defined column headers and no participant rows is returned. (Sources: US-008, Export)
- **AC-037** Given an unauthenticated or unauthorized requester attempts an export, when the backend handles the request, then access is denied without disclosing participant data. (Sources: US-008, Validation and security, Secure handling)
- **AC-038** Given stored participant text begins with a spreadsheet formula-control character, when it is exported, then opening the workbook does not interpret that participant-controlled text as a formula. (Sources: US-008, Protection against malicious input)

## Cross-cutting production acceptance criteria

- **AC-039** Given any registration request, when the backend processes it, then it independently enforces the fixed schema, registration-variant rules, length/type/format constraints, mandatory consent, and active-option validity regardless of frontend behaviour. (Sources: US-001, US-002, Validation and security, Form Schema)
- **AC-040** Given participant-controlled markup, script, SQL-like text, header-control characters, path-like text, or spreadsheet formulas are submitted within otherwise permitted text fields, when the system validates, stores, renders, emails, backs up, logs, or exports the values, then the values cannot execute commands, inject queries/headers/formulas, escape the intended backup location, or run active content. (Sources: Validation and security, Secure handling)
- **AC-041** Given a registration payload exceeds configured size or field-length limits, has an unsupported content type, contains unexpected fields, or is structurally malformed, when submitted, then the backend rejects it with a bounded non-sensitive client error and does not create a successful registration. (Sources: Validation and security, General quality)
- **AC-042** Given automated or abusive registration attempts, when configured anti-automation thresholds or checks are exceeded, then submissions are rejected or throttled without creating successful registrations, while an ordinary human submission can complete. (Sources: Protection against automated/bot submissions)
- **AC-043** Given cross-origin or cross-site requests target protected backend operations, when the origin or request is not trusted, then the request cannot create a registration or obtain an export. (Sources: Appropriate backend preventive security controls)
- **AC-044** Given an error occurs, when it is returned to a client or written to normal application logs, then credentials, secrets, connection details, stack traces, and unnecessary participant data are not exposed. (Sources: Secure handling of submitted participant data)
- **AC-045** Given the application is served in its production configuration, then transport security can be enforced at its deployment boundary, secrets are supplied outside source-controlled application content, security-relevant response headers are present, and organizer export access uses configured authentication. (Sources: Appropriate backend preventive security controls, Secure handling)
- **AC-046** Given the external or student form is used at relevant mobile and desktop viewport sizes, then all fixed fields, configurable options, validation messages, submit controls, and confirmation/error states remain readable and operable without unintended horizontal page scrolling. (Sources: Frontend quality)
- **AC-047** Given a keyboard-only user operates either form, then every input and action can be reached in a logical order, labels and errors are programmatically associated with fields, focus is visible, and result status is announced by assistive technology. (Sources: Frontend quality, General quality)
- **AC-048** Given the documented container deployment configuration and required environment values, when the system is built and started with persistent database/backup resources and email configuration, then the frontend and backend become operational without modifying source code. (Sources: Deployment, US-003)
- **AC-049** Given a required dependency such as the database is unavailable at startup or during a request, when readiness or registration processing is checked, then the service does not claim readiness or registration success and recovers when the dependency is restored. (Sources: US-005, General quality, Deployment)
- **AC-050** Given configurable-option retrieval, registration, export, or authentication requests are made, when operational records are produced, then relevant outcomes and correlation information are observable without logging secrets or unnecessary participant data. (Sources: General quality, Secure handling)

## Traceability summary

| User Story | Acceptance Criteria |
| --- | --- |
| US-001 | AC-001–AC-005, AC-039–AC-046, AC-048–AC-050 |
| US-002 | AC-006–AC-010, AC-039–AC-046, AC-048–AC-050 |
| US-003 | AC-011–AC-015, AC-035, AC-048 |
| US-004 | AC-016–AC-018, AC-044, AC-046–AC-047 |
| US-005 | AC-019–AC-024, AC-028, AC-032, AC-040–AC-045, AC-048–AC-050 |
| US-006 | AC-025–AC-028, AC-040, AC-044–AC-045, AC-048, AC-050 |
| US-007 | AC-029–AC-032, AC-040, AC-044–AC-045, AC-048, AC-050 |
| US-008 | AC-033–AC-038, AC-040, AC-043–AC-045, AC-048, AC-050 |

All eight User Stories have direct criteria, and the project constraints are represented by criteria for REST submission, dual validation, persistence plus JSON backup, both email paths, Excel export, security/anti-automation, responsive usability, and containerized deployment.
