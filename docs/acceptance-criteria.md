# Acceptance Criteria Specification

**Project:** Conference Registration System  
**Experiment Branch:** `gemini_3.8_flash_medium_single_agent_classic_sdd`  
**Phase:** Phase 1 — Acceptance Criteria  
**Traceability Base:** `USER_STORIES.md`, `PROJECT_CONSTRAINTS.md`, `FORM_SCHEMA.md`  
**Creation Timestamp:** 2026-09-21T15:57:00+02:00  

---

## 1. Overview and Scope

This document specifies the observable, testable acceptance criteria derived from the User Stories (`USER_STORIES.md`), mandatory Project Constraints (`PROJECT_CONSTRAINTS.md`), and Form Schema (`FORM_SCHEMA.md`).

All criteria are uniquely identified with traceability tags `AC-<US_ID>-<SEQ>` and formulated with clear verification conditions covering happy paths, input validation, security/anti-bot controls, persistence, email processing, export functionality, and edge cases.

---

## 2. Acceptance Criteria by User Story

### US-001 — External Participant Registration

*As an external conference participant, I want to register for the conference using the external participant registration form, so that I can attend the conference and selected conference activities.*

#### AC-001-01: External Registration Form Display
* **Given** an external participant visits the registration web application on desktop or mobile,
* **When** they navigate to the external registration view (e.g. `/` or `/register`),
* **Then** the form renders with all required fixed fields:
  * First name (`firstName`, text)
  * Last name (`lastName`, text)
  * Email (`email`, email)
  * Organization / Institution (`organization`, text)
  * Configurable options grouped by category (workshops, events, meals, optional activities)
  * Mandatory privacy/data-processing consent checkbox (`privacyConsent`, boolean, unselected by default)
  * Anti-bot honeypot field (hidden from visual display and screen readers)
* **And** the form layout is responsive and cleanly styled.

#### AC-001-02: Successful External Registration Submission (Happy Path)
* **Given** an external participant fills out valid values for:
  * `firstName`: "Ana"
  * `lastName`: "Novak"
  * `email`: "ana.novak@example.com"
  * `organization`: "Inštitut Jožef Stefan"
  * Active configurable options (e.g. selected workshop and meal)
  * `privacyConsent`: `true` (checked)
  * Honeypot field: empty
* **When** they submit the form,
* **Then** the frontend submits a `POST /api/register` request with `registrationType: "external"`,
* **And** the backend validates the payload, responds with HTTP `201 Created` with a JSON payload containing `success: true`, a unique `registrationId`, and confirmation details,
* **And** the registration is stored in the relational database,
* **And** a JSON backup file is created on persistent storage,
* **And** confirmation emails are queued/sent to participant and organizers.

#### AC-001-03: Required Fixed Fields Validation
* **Given** an external registration submission where one or more required fields (`firstName`, `lastName`, `email`, `organization`, or `privacyConsent`) are omitted, empty, or contain only whitespace,
* **When** the participant attempts submission or client/server validation evaluates the payload,
* **Then** submission is blocked,
* **And** the frontend displays specific field-level validation error messages without sending the request if invalid client-side,
* **And** if a direct API request is sent missing required fields, the backend rejects the request with HTTP `400 Bad Request` and a structured validation error response detailing the missing fields,
* **And** no database record, JSON backup, or email is generated.

#### AC-001-04: Email Format Validation
* **Given** an external registration with an invalid email format (e.g. `invalid-email`, `user@`, `@domain.com`, `user@domain`),
* **When** the form is submitted or backend API receives the payload,
* **Then** the submission is rejected with an observable validation message "Please enter a valid email address",
* **And** the backend returns HTTP `400 Bad Request` specifying the email field error.

#### AC-001-05: Unicode and Slovenian Character Support
* **Given** an external participant enters text fields containing Slovenian diacritics and special Unicode characters (`Č`, `č`, `Š`, `š`, `Ž`, `ž`, `Ć`, `ć`, `Đ`, `đ`) in `firstName`, `lastName`, or `organization` (e.g., "Boštjan", "Žagar", "Univerza v Mariboru - Fakulteta za elektrotehniko, računalništvo in informatiko"),
* **When** the form is submitted and processed,
* **Then** the backend persists and displays the exact characters without corruption, truncation, or encoding degradation (UTF-8 encoding preserved across database, JSON backup, confirmation email, and Excel export).

#### AC-001-06: Whitespace Trimming
* **Given** an external registration with leading and/or trailing whitespace in text inputs (e.g. `firstName`: `"  Mojca  "`),
* **When** the submission is processed,
* **Then** the backend trims leading and trailing whitespace so that the saved value is `"Mojca"`.

#### AC-001-07: Anti-Bot Honeypot Protection
* **Given** an automated bot fills out all form fields including the invisible honeypot field,
* **When** the payload is submitted to the registration API,
* **Then** the backend detects the honeypot presence, silently drops or rejects the submission with HTTP `400 Bad Request` (or `422 Unprocessable Entity`),
* **And** no database record is created, no backup file is written, and no emails are dispatched.

---

### US-002 — Student Registration

*As a student, I want to register for the conference using the student registration form, so that I can attend the conference and activities available to students.*

#### AC-002-01: Student Registration Form Display
* **Given** a student participant visits the registration web application,
* **When** they navigate to the student registration view (e.g. `/student` or `/register/student`),
* **Then** the form renders with student-specific fixed fields:
  * First name (`firstName`, text)
  * Last name (`lastName`, text)
  * Email (`email`, email)
  * Study institution (`studyInstitution`, text)
  * Study programme (`studyProgramme`, text)
  * Student ID (`studentId`, text)
  * Configurable options grouped by category
  * Mandatory privacy/data-processing consent checkbox (`privacyConsent`, boolean, unselected by default)
  * Anti-bot honeypot field
* **And** the field `organization` from the external form is not displayed or required.

#### AC-002-02: Successful Student Registration Submission (Happy Path)
* **Given** a student provides valid values:
  * `firstName`: "Luka"
  * `lastName`: "Krajnc"
  * `email`: "luka.krajnc@student.um.si"
  * `studyInstitution`: "Univerza v Mariboru"
  * `studyProgramme`: "Računalništvo in informacijske tehnologije"
  * `studentId`: "E1094821"
  * Selected active configurable options
  * `privacyConsent`: `true`
  * Honeypot: empty
* **When** they submit the form,
* **Then** the frontend sends `POST /api/register` with `registrationType: "student"`,
* **And** the backend validates the payload, responds with HTTP `201 Created` with `success: true` and `registrationId`,
* **And** the record is stored in the database with student fields populated and `registration_type = "student"`,
* **And** persistent JSON backup and emails are generated.

#### AC-002-03: Required Student Fields Validation
* **Given** a student registration submission omitting any of `firstName`, `lastName`, `email`, `studyInstitution`, `studyProgramme`, `studentId`, or `privacyConsent`,
* **When** submission is attempted,
* **Then** frontend prevents submission with visible field validation errors,
* **And** backend rejects the payload with HTTP `400 Bad Request` describing the missing student fields.

#### AC-002-04: Student ID and Academic Unicode Text Support
* **Given** student fields contain alphanumeric symbols, hyphens, slashes, or Slovenian diacritics (e.g., student ID `1002345678`, study programme `Medijske komunikacije`),
* **When** submitted,
* **Then** all characters are safely accepted, sanitized, stored, and verified in database, JSON backup, and exports.

---

### US-003 — Configurable Conference Options

*As a conference organizer, I want to change the available workshops and optional conference activities without changing the fixed participant-data fields, so that the same registration system can be reused when the conference programme changes.*

#### AC-003-01: Configurable Options Endpoint
* **Given** the backend is running,
* **When** a client sends a `GET /api/conference-options` request,
* **Then** the backend returns HTTP `200 OK` with a structured list of options grouped into categories:
  * `workshops`
  * `events`
  * `meals`
  * `other` (other optional checkbox-based conference activities)
* **And** each option contains:
  * `id`: stable unique string identifier (e.g. `ws-ai-2026`)
  * `name`: descriptive display name (e.g. "Practical Machine Learning Workshop")
  * `category`: category key
  * `active`: boolean flag indicating whether the option is selectable.

#### AC-003-02: Dynamic UI Rendering of Active Options
* **Given** conference options are retrieved from the backend configuration,
* **When** the registration form is rendered,
* **Then** only options with `active: true` are presented as selectable choices to the user,
* **And** any option marked `active: false` is excluded from the form choices or disabled with clear status.

#### AC-003-03: Rejection of Inactive or Unknown Options
* **Given** a client submits a registration request containing:
  * an option ID that does not exist in the conference options configuration, OR
  * an option ID that is configured with `active: false`,
* **When** the backend validates the submission,
* **Then** the backend rejects the request with HTTP `400 Bad Request` (or `422 Unprocessable Entity`),
* **And** returns a descriptive error message: "Selected option '{optionId}' is invalid or no longer active",
* **And** the registration is aborted without persistence or email generation.

#### AC-003-04: Configuration Change Without Code Modification
* **Given** the organizer updates the conference options configuration file (or environment configuration) by adding, modifying, or deactivating an option,
* **When** the application serves new requests,
* **Then** the new options are immediately reflected on the registration forms without altering the fixed participant field schema or requiring code changes to the registration logic.

---

### US-004 — Registration Confirmation in the Application

*As a participant, I want to receive clear confirmation after my registration has been successfully processed, so that I know that my registration was received.*

#### AC-004-01: Strict Timing of Confirmation Display
* **Given** a participant submits a registration form,
* **When** the frontend is awaiting the backend response,
* **Then** a clear loading/processing state is shown and submit button is disabled to prevent duplicate submissions,
* **And** the success confirmation view/modal is rendered **only after** receiving an HTTP `201 Created` successful response from the backend REST API.

#### AC-004-02: Content of Confirmation Display
* **Given** the backend returns a successful registration response,
* **When** the confirmation screen is displayed to the participant,
* **Then** it clearly states:
  * A prominent success heading (e.g. "Prijava uspešna" / "Registration Successful")
  * The unique registration reference number (`registrationId`)
  * The registered participant's name and email address
  * A summary of selected conference options
  * A clear notice informing the participant that a confirmation email has been dispatched to their email address.

#### AC-004-03: User-Friendly Error Feedback on Backend Failure
* **Given** the backend returns an error status (e.g. HTTP `400`, `429`, or `500`) or network failure occurs during registration,
* **When** the response is received by the frontend,
* **Then** the success confirmation is **not** displayed,
* **And** a clear, user-friendly error alert is displayed informing the user of the failure and highlighting any field corrections or advising them to try again.

---

### US-005 — Reliable Registration Storage

*As a conference organizer, I want every successfully submitted registration to be stored reliably and backed up, so that participant registrations are not lost and can be recovered if necessary.*

#### AC-005-01: Relational Database Persistence
* **Given** a valid registration payload is received by the backend,
* **When** the transaction executes,
* **Then** the registration is committed to a relational database table `registrations`,
* **And** the saved record includes:
  * `id`: unique primary key (UUID or auto-increment ID)
  * `registration_type`: "external" or "student"
  * `first_name`: string
  * `last_name`: string
  * `email`: string
  * `organization`: nullable string (populated for external)
  * `study_institution`: nullable string (populated for student)
  * `study_programme`: nullable string (populated for student)
  * `student_id`: nullable string (populated for student)
  * `selected_options`: JSON array or relation containing valid option IDs
  * `privacy_consent`: boolean (`true`)
  * `created_at`: ISO 8601 timestamp
  * `ip_hash` / security audit metadata
* **And** the database transaction ensures atomic persistence before acknowledging success.

#### AC-005-02: Persistent JSON Representation Backup
* **Given** a registration is successfully committed,
* **When** the post-persistence backup step executes,
* **Then** a standalone JSON file representing the registration is generated and saved to a dedicated persistent directory (e.g. `data/backups/registration_<id>.json`),
* **And** the JSON file content contains the complete registration details:
  * `registrationId`
  * `registrationType`
  * fixed fields
  * selected option IDs and option names
  * `submittedAt` timestamp
* **And** file system writes ensure directory existence and file integrity with UTF-8 encoding.

#### AC-005-03: Durability and Disaster Recovery
* **Given** the relational database or JSON backup directory on persistent storage,
* **When** the server process is restarted,
* **Then** all previously stored registration records remain intact and recoverable,
* **And** every database record has a corresponding valid JSON backup file on storage.

---

### US-006 — Participant Email Confirmation

*As a registered participant, I want to receive an email confirming my registration, so that I have a record of my successful registration.*

#### AC-006-01: Participant Email Dispatch Trigger
* **Given** a participant completes a valid registration,
* **When** the registration is processed successfully,
* **Then** the system sends a confirmation email to the participant's submitted email address.

#### AC-006-02: Participant Email Content
* **Given** a confirmation email is generated for a participant,
* **When** the email message is assembled,
* **Then** it contains:
  * Clear Subject line: e.g. "Potrditev prijave na konferenco / Conference Registration Confirmation - {registrationId}"
  * Addressed to the participant's full name
  * Detailed summary of the registration details (name, email, participant type, institution/organization)
  * Itemized list of selected conference activities, workshops, and meals
  * Organizer contact information for inquiries.

#### AC-006-03: Email Service Fault Isolation
* **Given** the email delivery provider or SMTP transport encounters a temporary failure or simulates transport,
* **When** a registration is submitted,
* **Then** the primary database persistence and JSON backup are not corrupted, and the error is logged without crashing the application.

---

### US-007 — Organizer Notification

*As a conference organizer, I want to be notified when a new participant registers and receive the submitted registration information, so that I have an additional record of every registration and can follow registrations as they arrive.*

#### AC-007-01: Organizer Notification Dispatch Trigger
* **Given** a participant completes a valid registration,
* **When** the registration is processed,
* **Then** the system dispatches a notification email to the designated conference organizer email address(es) configured in the system.

#### AC-007-02: Organizer Email Content and Attachment
* **Given** an organizer notification email is dispatched,
* **When** inspected,
* **Then** the email contains:
  * Subject line indicating a new registration with participant name and type (e.g. "Nova prijava na konferenco: {firstName} {lastName} ({registrationType})")
  * Body summarizing the submitted data
  * **An attached JSON file** named `registration_<id>.json` containing the exact full registration data.

---

### US-008 — Excel Export

*As a conference organizer, I want to export the current list of registered participants to an Excel file at any time, so that I can process, review and share registration data outside the registration system.*

#### AC-008-01: Excel Export Endpoint
* **Given** the backend has stored registrations,
* **When** an authorized request is sent to `GET /api/admin/export/excel` (or via the admin UI export action),
* **Then** the backend generates a valid Microsoft Excel workbook (`.xlsx` format),
* **And** responds with HTTP `200 OK` and appropriate headers:
  * `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
  * `Content-Disposition: attachment; filename="registrations_<timestamp>.xlsx"`.

#### AC-008-02: Excel Spreadsheet Structure and Data Completeness
* **Given** the exported Excel file is opened in Microsoft Excel or spreadsheet viewers,
* **When** its contents are inspected,
* **Then** it contains a cleanly formatted table with column headers:
  * Registration ID (`ID`)
  * Timestamp (`Datum in čas prijave`)
  * Type (`Tip udeleženca`: "Zunanji" / "Študent")
  * First name (`Ime`)
  * Last name (`Priimek`)
  * Email (`E-pošta`)
  * Organization / Institution (`Organizacija / Ustanova`)
  * Study programme (`Študijski program`)
  * Student ID (`Vpisna številka`)
  * Selected Workshops (`Delavnice`)
  * Selected Events (`Dogodki`)
  * Selected Meals (`Prehrana`)
  * Other Activities (`Druge aktivnosti`)
  * Privacy Consent (`Soglasje GDPR`)
* **And** all registered participants up to the moment of export are included as rows.

#### AC-008-03: Empty State Export
* **Given** zero participants have registered yet,
* **When** the export endpoint is requested,
* **Then** a valid `.xlsx` file is generated containing the complete header row and zero data rows without throwing an exception or returning a corrupted file.

#### AC-008-04: Unicode Integrity in Excel Export
* **Given** registrations with Slovenian characters ("Č", "š", "ž", "ć", "đ"),
* **When** exported to `.xlsx`,
* **Then** all characters display correctly in standard spreadsheet software without replacement symbols or garbled text.

---

## 3. Cross-Cutting Acceptance Criteria (Validation, Security, Usability)

#### AC-SEC-01: Anti-Automation and Rate Limiting
* **Given** incoming requests to `/api/register`,
* **When** an IP address sends rapid repeated requests exceeding the defined rate limit (e.g. > 15 requests per minute),
* **Then** the backend responds with HTTP `429 Too Many Requests` with a `Retry-After` header.

#### AC-SEC-02: Input Sanitization and XSS Prevention
* **Given** form input containing HTML or script tags (e.g. `<script>alert('xss')</script>`),
* **When** submitted,
* **Then** the input is safely escaped, sanitized, and stored without executing malicious scripts when rendered in the UI, email, or exported Excel.

#### AC-SEC-03: Security Headers and CORS
* **Given** the application backend serves HTTP traffic,
* **When** any response is inspected,
* **Then** standard security headers are present (`X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Content-Security-Policy`), and CORS is restricted to allowed origins.

#### AC-DEP-01: Containerized Deployment
* **Given** the Docker configuration (`Dockerfile` and/or `docker-compose.yml`),
* **When** `docker build` and `docker run` are executed,
* **Then** the container starts cleanly, health check endpoint returns HTTP `200 OK`, and the registration web application is accessible and fully functional.

---

## 4. Acceptance Criteria Traceability Matrix

| User Story | Name | Acceptance Criteria Identifiers |
|---|---|---|
| **US-001** | External participant registration | `AC-001-01`, `AC-001-02`, `AC-001-03`, `AC-001-04`, `AC-001-05`, `AC-001-06`, `AC-001-07` |
| **US-002** | Student registration | `AC-002-01`, `AC-002-02`, `AC-002-03`, `AC-002-04` |
| **US-003** | Configurable conference options | `AC-003-01`, `AC-003-02`, `AC-003-03`, `AC-003-04` |
| **US-004** | Registration confirmation in application | `AC-004-01`, `AC-004-02`, `AC-004-03` |
| **US-005** | Reliable registration storage | `AC-005-01`, `AC-005-02`, `AC-005-03` |
| **US-006** | Participant email confirmation | `AC-006-01`, `AC-006-02`, `AC-006-03` |
| **US-007** | Organizer notification | `AC-007-01`, `AC-007-02` |
| **US-008** | Excel export | `AC-008-01`, `AC-008-02`, `AC-008-03`, `AC-008-04` |
| **Constraints** | Security & Container Deployment | `AC-SEC-01`, `AC-SEC-02`, `AC-SEC-03`, `AC-DEP-01` |

---

## 5. Phase 1 Sign-Off
* **Status:** Complete
* **Artefact:** `docs/acceptance-criteria.md`
* **Completion Timestamp:** 2026-09-21T15:58:30+02:00
