# Acceptance criteria

> Written in: phase 1 · Source: `project/02_business/*` · Agent: writes (frozen after phase 3)

Derived from `project/02_business/user-stories.md`, `business-rules.md` and `scope.md`. Each criterion names its story (US) and the rules (BR) it applies. "Nothing stored" means: no database registration, no published JSON file, no notification intent, no email, and no row in the organizer export.

Terms: *valid external submission* = first name, last name, email, organization/institution, a valid captcha token, the required consent given, and zero or more distinct active option IDs per group. *Valid student submission* = first name, last name, email, study institution, study programme, student ID, a valid captcha token, the required consent given, and the same selection rules. The local catalog and consent are synthetic fixtures (OQ-01, OQ-02).

## US-001: External participant registration

| AC | Given / When / Then | BR |
|---|---|---|
| AC-001-01 | **Given** the external form and the configured catalog, **when** a participant sends a valid external submission with options from several groups, **then** the backend accepts it, returns a server-generated registration ID, and stores the normalised fields, selections and consent state. | BR-02, BR-03, BR-05, BR-06 |
| AC-001-02 | **Given** the external form, **when** any required field (first name, last name, email, organization/institution) is missing, empty, or only Unicode whitespace (including NBSP), **then** the backend rejects it with an error naming that field, and nothing is stored. | BR-01, BR-02 |
| AC-001-03 | **Given** the external form, **when** the email is not a valid address, **then** the backend rejects it with an email field error, and nothing is stored. | BR-02 |
| AC-001-04 | **Given** the external form, **when** the values contain Slovenian or other Unicode letters and leading or trailing spaces/NBSP, **then** the submission is accepted, the whitespace is trimmed, and the Unicode content is stored and shown unchanged. | BR-01 |
| AC-001-05 | **Given** the external API, **when** a submission (including a direct API request that bypasses the UI) selects an unknown option ID, an inactive option ID, an option under the wrong group, or the same option twice, **then** it is rejected with a selections error, and nothing is stored. | BR-03 |
| AC-001-06 | **Given** the external API, **when** the captcha token is missing or fails verification, **then** the submission is rejected with a captcha error, and nothing is stored. | BR-02 |
| AC-001-07 | **Given** the required synthetic consent, **when** a submission has consent absent or false, **then** it is rejected with a consent error and nothing is stored; **when** consent is true, **then** it is accepted and the consent state is stored. | BR-04 |
| AC-001-08 | **Given** the documented field and request size limits, **when** a field exceeds its maximum length or the request body exceeds its maximum size, **then** the submission is rejected, and nothing is stored. | BR-02 (data note) |
| AC-001-09 | **Given** the browser UI, **when** a participant opens the external form, **then** it shows labelled inputs for the external fields, the active options grouped as workshops, events, meals and other, and the consent checkbox initially unchecked; the form can be completed and submitted with the keyboard alone, and client-side errors are shown next to (and programmatically associated with) their fields. | BR-02, BR-03, BR-04 |
| AC-001-10 | **Given** one client address, **when** it sends registration requests faster than the documented rate limit, **then** the excess requests are rejected with "too many requests", and nothing is stored for them. | BR-02 (SR-03) |

## US-002: Student registration

| AC | Given / When / Then | BR |
|---|---|---|
| AC-002-01 | **Given** the student form and the configured catalog, **when** a student sends a valid student submission, **then** the backend accepts it with a server-generated registration ID and stores the study institution, study programme and student ID (as free text) with the other fields. | BR-02, BR-05, BR-06 |
| AC-002-02 | **Given** the student form, **when** any required field (first name, last name, email, study institution, study programme, student ID) is missing, empty, or only Unicode whitespace (including NBSP), **then** it is rejected with an error naming that field, and nothing is stored. | BR-01, BR-02 |
| AC-002-03 | **Given** the student form, **when** the email is not a valid address, **then** it is rejected with an email field error, and nothing is stored. | BR-02 |
| AC-002-04 | **Given** the student form, **when** the values contain Unicode (Slovenian) letters with surrounding spaces/NBSP, **then** the submission is accepted with trimmed, otherwise unchanged values. | BR-01 |
| AC-002-05 | **Given** the student API, **when** it selects an unknown, inactive, wrong-group or duplicated option, **then** it is rejected, and nothing is stored; **when** it selects any active option from any group, **then** it is accepted (no student-only restrictions). | BR-03 |
| AC-002-06 | **Given** the student API, **when** the captcha token is missing or invalid, **then** it is rejected, and nothing is stored. | BR-02 |
| AC-002-07 | **Given** the required synthetic consent, **when** a student submission has consent absent or false, **then** it is rejected, and nothing is stored; **when** true, **then** it is accepted and the consent state is stored. | BR-04 |
| AC-002-08 | **Given** the browser UI, **when** a student opens the student form, **then** it shows labelled inputs for the student fields, the grouped active options and an unchecked consent checkbox; it is keyboard-operable, and errors are associated with their fields. | BR-02, BR-03, BR-04 |

## US-003: Configurable activity catalog

| AC | Given / When / Then | BR |
|---|---|---|
| AC-003-01 | **Given** a catalog configuration file with workshops, events, meals and other options, some inactive, **when** the catalog is requested (API or either form), **then** exactly the active options are offered, each with its stable ID and display name, under its group. | BR-03, BR-10 |
| AC-003-02 | **Given** a running system, **when** the operator edits the external catalog file (adds, renames and deactivates options) and restarts the backend without rebuilding, **then** the catalog and both forms show the new active options, a newly added option is accepted, and a newly deactivated option is rejected. | BR-03, BR-10 |
| AC-003-03 | **Given** an invalid catalog file (malformed, duplicate option IDs, unknown group, or missing display name), **when** the backend starts, **then** it refuses to start rather than serving a partial catalog. | BR-10 |
| AC-003-04 | **Given** a catalog change, **when** the catalog is reloaded, **then** the fixed participant fields of both forms are unchanged. | BR-10 |

## US-004: Confirmation after backend acceptance

| AC | Given / When / Then | BR |
|---|---|---|
| AC-004-01 | **Given** a filled form, **when** the backend accepts the submission, **then** the UI shows a confirmation screen with the registration ID, and it appears only after the backend's acceptance response. | BR-05 |
| AC-004-02 | **Given** a filled form, **when** the backend rejects the submission or is unavailable, **then** the UI shows no confirmation, shows an error message (field errors next to their fields), and keeps the entered data for correction or retry. | BR-05 |
| AC-004-03 | **Given** an accepted submission, **when** the same client request ID is sent again with the same content, **then** no new registration is created and the response carries the original registration ID. | BR-06 |
| AC-004-04 | **Given** an accepted submission, **when** the same client request ID is sent with different content, **then** it is rejected as a conflict, and nothing new is stored. | BR-06 |
| AC-004-05 | **Given** an accepted registration, **when** another submission with a different client request ID uses the same email, **then** it is accepted as a separate registration with a different registration ID. | BR-06 |

## US-005: Durable storage in database and raw JSON

| AC | Given / When / Then | BR |
|---|---|---|
| AC-005-01 | **Given** an accepted submission, **then** the database holds its registration and a JSON file named by its registration ID exists in the JSON store with the same registration ID, client request ID, form type, fields, selections and consent state. | BR-05 |
| AC-005-02 | **Given** the JSON store cannot be written, **when** a valid submission arrives, **then** the backend answers with a server error, shows no success, and nothing is stored. | BR-05 |
| AC-005-03 | **Given** the database is unavailable, **when** a valid submission arrives, **then** the backend answers with a server error, shows no success, and no accepted registration (export row or notification) results from it. | BR-05 |
| AC-005-04 | **Given** a JSON file left in the JSON store without a database registration (for example after a crash), **when** reconciliation runs, **then** the file is moved out of the accepted set and never appears as an accepted registration. | BR-05 |
| AC-005-05 | **Given** accepted registrations, **when** the backend (and database) restart with the same volumes, **then** all accepted registrations and their JSON files are still present and exported. | BR-05 |

## US-006: Participant confirmation email

| AC | Given / When / Then | BR |
|---|---|---|
| AC-006-01 | **Given** an accepted registration, **then** a confirmation email is delivered to the submitted participant address, with the registration ID, the participant's name and the selected activities. | BR-07 |
| AC-006-02 | **Given** SMTP is unavailable when a submission arrives, **then** the submission is still accepted, and after SMTP recovers the confirmation email is delivered by the retry mechanism. | BR-07 |
| AC-006-03 | **Given** submitted values containing line breaks, header-like text or HTML, **then** they cannot add recipients or headers to any email; single-line fields with control characters are rejected, and accepted HTML-like text appears as plain text. | BR-07 (SR-04) |

## US-007: Organizer notification with JSON attachment

| AC | Given / When / Then | BR |
|---|---|---|
| AC-007-01 | **Given** an accepted registration, **then** each configured organizer address receives a notification containing the submitted data and an attachment `registration-<id>.json` byte-identical to the stored JSON file. | BR-07, BR-08 |
| AC-007-02 | **Given** SMTP is unavailable when a submission arrives, **when** SMTP recovers, **then** the organizer notification with its attachment is delivered. | BR-07, BR-08 |

## US-008: Organizer Excel export

| AC | Given / When / Then | BR |
|---|---|---|
| AC-008-01 | **Given** accepted external and student registrations, **when** the organizer requests the export with valid credentials, **then** an `.xlsx` workbook is returned with one row per accepted registration from both forms, including form type, registration ID, all participant fields, selections and consent. | BR-09 |
| AC-008-02 | **Given** no credentials or wrong credentials, **when** the export is requested, **then** it is refused with "unauthorized" and returns no registration data. | BR-09 |
| AC-008-03 | **Given** a registration whose text starts with a formula character (`=`, `+`, `-`, `@`), **when** it is exported, **then** the cell holds the same text as a string, not a formula. | BR-09 (SR-04) |
| AC-008-04 | **Given** rejected submissions and orphaned JSON files, **when** the export is requested, **then** they do not appear; only current accepted registrations do. | BR-05, BR-09 |
| AC-008-05 | **Given** one client address, **when** it makes more export authentication attempts than the documented limit, **then** further attempts are refused with "too many requests". | BR-09 (SB-06) |
| AC-008-06 | **Given** the browser UI, **when** an organizer opens the organizer page, **then** it offers the Excel download, which requires the organizer credentials. | BR-09 |

## Open questions

| OQ | Status |
|---|---|
| OQ-01 | Answered in `scope.md`: "Lab Conference", synthetic options, `organizer@example.test` / `participant@example.test` through the catcher |
| OQ-02 | Answered: synthetic required consent fixture only (AC-001-07, AC-002-07) |
| OQ-03 | Answered: synthetic data kept for the experiment; the operator deletes the volumes |
| OQ-04 | Answered: no capacity limits, zero or more distinct selections per group, email may repeat (AC-004-05) |
| OQ-05 | Answered: private lab use, no licence added (D-16) |
| OQ-06 | Answered by `tech-stack.md` / `run-config.md`; preflight facts in `00_preflight-report.md` |

Behaviour choices made here: D-17 (request-ID reuse with different content → conflict), D-18 (meaning of "raw JSON"), D-19 (invalid catalog → startup fails).

## Traceability

| Story | Criteria |
|---|---|
| US-001 | AC-001-01 … AC-001-10 |
| US-002 | AC-002-01 … AC-002-08 |
| US-003 | AC-003-01 … AC-003-04 |
| US-004 | AC-004-01 … AC-004-05 |
| US-005 | AC-005-01 … AC-005-05 |
| US-006 | AC-006-01 … AC-006-03 |
| US-007 | AC-007-01 … AC-007-02 |
| US-008 | AC-008-01 … AC-008-06 |
