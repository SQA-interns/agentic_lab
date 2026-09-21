# Technical Specification: Conference Registration System

**Project:** Conference Registration System  
**Experiment Branch:** `gemini_3.8_flash_medium_single_agent_classic_sdd`  
**Phase:** Phase 2 — Specification  
**Traceability Base:** `USER_STORIES.md`, `PROJECT_CONSTRAINTS.md`, `FORM_SCHEMA.md`, `docs/acceptance-criteria.md`  
**Creation Timestamp:** 2026-09-21T16:00:00+02:00  

---

## 1. Executive Summary & Architecture Overview

The Conference Registration System is an enterprise-grade, lightweight web application providing self-service registration for external conference participants and students, automated email notifications, dual persistence (relational database and JSON file backup), configurable conference activities, security protections, and administrative Excel reporting.

### 1.1 Architectural Pattern
The system is built on a modular client-server architecture:
* **Frontend:** Responsive, semantic HTML5, accessible modern CSS, and modular vanilla JavaScript. Designed for both desktop and mobile devices. Communicates with the backend exclusively via REST API.
* **Backend:** Node.js service utilizing Express.js for REST routing and middleware pipeline.
* **Persistence Layer:** Dual-storage architecture:
  1. Relational Database: SQLite with WAL (Write-Ahead Logging) mode, foreign key enforcement, and ACID transaction guarantees.
  2. Persistent Storage File Backup: Standalone JSON representations written to `data/backups/registration_<uuid>.json`.
* **Export Engine:** Server-side Microsoft Excel (`.xlsx`) generation using `exceljs`.
* **Email Subsystem:** Nodemailer integration with configurable SMTP transport and fallback test/stream logger for offline or automated test environments.
* **Containerization:** Production Docker image based on `node:24-alpine` with health checking and persistent volume mounts.

```
+-----------------------------------------------------------------------------------+
|                                 Client Layer                                      |
|   External Registration View   |   Student Registration View   |   Admin Report   |
+-----------------------------------------------------------------------------------+
                                         | REST (JSON / HTTPS)
                                         v
+-----------------------------------------------------------------------------------+
|                                 Express Backend                                   |
|  [Security Headers] -> [Rate Limiter] -> [Honeypot Filter] -> [Body Parser]      |
|                                        |                                          |
|                          +-------------+-------------+                            |
|                          |                           |                            |
|                 [Registration Router]      [Admin & Export Router]                |
|                          |                           |                            |
|                 [Registration Service]      [Export Service]                      |
|                 /        |         \                 |                            |
|                v         v          v                v                            |
|          [Validation] [Storage]  [Email Service] [Relational DB]                  |
+-----------------------------------------------------------------------------------+
                     |           |             |
                     v           v             v
             SQLite Database  JSON Backups   SMTP / Email Transport
```

---

## 2. Component and Module Architecture

### 2.1 Directory Layout
```
agentic_lab/
├── config/
│   ├── index.js                     # Environment configuration loader
│   └── conference-options.json      # Configurable conference options schema & data
├── src/
│   ├── app.js                       # Express application assembly & middleware setup
│   ├── server.js                    # Server bootstrap & lifecycle management
│   ├── db/
│   │   ├── database.js              # SQLite connection & schema initialization
│   │   └── registrationRepository.js# Relational CRUD and query operations
│   ├── services/
│   │   ├── optionsService.js        # Conference options retrieval and validation
│   │   ├── backupService.js         # JSON persistent backup file writer
│   │   ├── emailService.js          # Participant confirmation & organizer notifications
│   │   ├── exportService.js         # Excel (.xlsx) workbook builder
│   │   └── registrationService.js   # Main registration orchestration service
│   ├── validators/
│   │   ├── sanitize.js              # Input sanitization and trimming
│   │   └── registrationValidator.js # Validation rules for external and student payloads
│   ├── middleware/
│   │   ├── antiBot.js               # Honeypot verification
│   │   ├── rateLimiter.js           # Request throttling protection
│   │   ├── securityHeaders.js       # CSP, X-Frame-Options, X-Content-Type-Options
│   │   └── errorHandler.js          # Centralized error handling & logging
│   └── routes/
│       ├── optionsRoutes.js         # GET /api/conference-options
│       ├── registrationRoutes.js    # POST /api/register
│       └── adminRoutes.js           # GET /api/admin/registrations, GET /api/admin/export/excel
├── public/
│   ├── index.html                   # External participant registration form
│   ├── student.html                 # Student participant registration form
│   ├── admin.html                   # Organizer export and overview dashboard
│   ├── css/
│   │   └── style.css                # Mobile-responsive CSS layout
│   └── js/
│       ├── app.js                   # Common form handling, option loader, client validation
│       └── confirmation.js          # Confirmation modal / view handler
├── data/
│   ├── database.sqlite              # Relational SQLite file (persistent)
│   └── backups/                     # Dedicated JSON backup files (persistent)
├── Dockerfile                       # Multi-stage production container build
├── docker-compose.yml               # Container orchestration with volume mounts
└── package.json                     # Node.js project manifest & dependencies
```

### 2.2 Component Responsibilities
* **`config/conference-options.json`:** Decoupled conference configuration. Allows adding, editing, or deactivating workshops, events, meals, and optional activities without code changes (`US-003`, `AC-003-01..04`).
* **`registrationValidator.js`:** Pure validation functions ensuring adherence to `FORM_SCHEMA.md` and constraints (`AC-001-03..06`, `AC-002-03..04`, `AC-003-03`).
* **`registrationRepository.js`:** Encapsulates SQLite database queries, parameterized SQL execution preventing SQL injection, and structured row mapping (`AC-005-01`).
* **`backupService.js`:** Formats registration payload into structured JSON and writes to `data/backups/registration_<uuid>.json` (`AC-005-02`).
* **`emailService.js`:** Dispatches participant confirmation email (`AC-006-01..03`) and organizer notification with JSON attachment (`AC-007-01..02`).
* **`exportService.js`:** Streams or generates binary `.xlsx` files with complete registration records (`AC-008-01..04`).
* **`antiBot.js` & `rateLimiter.js`:** Enforces automated bot mitigation and rate throttling (`AC-001-07`, `AC-SEC-01`).

---

## 3. Data Model & Storage Specification

### 3.1 Relational Database Schema (`SQLite`)
The database schema uses an explicit relational table with strong column types, constraints, and indexes.

```sql
CREATE TABLE IF NOT EXISTS registrations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    uuid TEXT NOT NULL UNIQUE,
    registration_type TEXT NOT NULL CHECK(registration_type IN ('external', 'student')),
    first_name TEXT NOT NULL,
    last_name TEXT NOT NULL,
    email TEXT NOT NULL,
    organization TEXT NULL,
    study_institution TEXT NULL,
    study_programme TEXT NULL,
    student_id TEXT NULL,
    selected_options TEXT NOT NULL, -- JSON array of stable string option IDs
    privacy_consent INTEGER NOT NULL CHECK(privacy_consent = 1),
    ip_address TEXT NULL,
    user_agent TEXT NULL,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE INDEX IF NOT EXISTS idx_registrations_email ON registrations(email);
CREATE INDEX IF NOT EXISTS idx_registrations_created_at ON registrations(created_at);
CREATE INDEX IF NOT EXISTS idx_registrations_type ON registrations(registration_type);
```

### 3.2 Persistent JSON Backup Schema
Every successful registration generates a file: `data/backups/registration_{uuid}.json`.

```json
{
  "registrationId": "reg_a1b2c3d4-e5f6-7890-abcd-1234567890ab",
  "registrationType": "external",
  "participant": {
    "firstName": "Ana",
    "lastName": "Novak",
    "email": "ana.novak@example.com",
    "organization": "Inštitut Jožef Stefan"
  },
  "selectedOptions": [
    {
      "id": "ws-ai-2026",
      "category": "workshops",
      "name": "Praktična delavnica strojnega učenja"
    },
    {
      "id": "meal-vegetarian",
      "category": "meals",
      "name": "Vegetarijanski meni"
    }
  ],
  "privacyConsent": true,
  "submittedAt": "2026-09-21T16:05:00.000Z",
  "metadata": {
    "ipAddress": "127.0.0.1",
    "userAgent": "Mozilla/5.0..."
  }
}
```

---

## 4. Configurable Conference Options Schema

Conference activities are defined in `config/conference-options.json`.

```json
{
  "workshops": [
    { "id": "ws-cloud-native", "name": "Cloud Native Architecture Masterclass", "active": true },
    { "id": "ws-cybersec", "name": "Sodobna kibernetska varnost v praksi", "active": true },
    { "id": "ws-legacy-systems", "name": "Legacy Systems Migration (Polno)", "active": false }
  ],
  "events": [
    { "id": "ev-keynote", "name": "Otvoritveno predavanje in plenarno zasedanje", "active": true },
    { "id": "ev-networking-dinner", "name": "Sprejem in družabna večerja", "active": true }
  ],
  "meals": [
    { "id": "meal-standard", "name": "Standardni meni", "active": true },
    { "id": "meal-vegetarian", "name": "Vegetarijanski meni", "active": true },
    { "id": "meal-vegan", "name": "Veganski meni", "active": true }
  ],
  "other": [
    { "id": "act-morning-run", "name": "Jutranji tek udeležencev ob reki Dravi", "active": true },
    { "id": "act-city-tour", "name": "Voden ogled starega mestnega jedra Maribora", "active": true }
  ]
}
```

Validation Rule:
* Backend validates all submitted IDs against this configuration.
* If any submitted ID is not found, or `active === false`, submission is rejected with HTTP `400 Bad Request`.

---

## 5. REST API Specification

### 5.1 `GET /api/conference-options`
Returns the active configurable options for dynamic frontend rendering.

* **Response HTTP 200 OK:**
```json
{
  "success": true,
  "data": {
    "workshops": [
      { "id": "ws-cloud-native", "name": "Cloud Native Architecture Masterclass", "active": true },
      { "id": "ws-cybersec", "name": "Sodobna kibernetska varnost v praksi", "active": true }
    ],
    "events": [ ... ],
    "meals": [ ... ],
    "other": [ ... ]
  }
}
```

### 5.2 `POST /api/register`
Submits a new registration.

#### Request Headers:
`Content-Type: application/json`

#### External Participant Request Payload:
```json
{
  "registrationType": "external",
  "firstName": "Ana",
  "lastName": "Novak",
  "email": "ana.novak@example.com",
  "organization": "Inštitut Jožef Stefan",
  "selectedOptions": ["ws-cloud-native", "meal-vegetarian"],
  "privacyConsent": true,
  "honeypot": ""
}
```

#### Student Participant Request Payload:
```json
{
  "registrationType": "student",
  "firstName": "Luka",
  "lastName": "Krajnc",
  "email": "luka.krajnc@student.um.si",
  "studyInstitution": "Univerza v Mariboru",
  "studyProgramme": "Računalništvo in informacijske tehnologije",
  "studentId": "E1094821",
  "selectedOptions": ["ev-keynote", "meal-standard"],
  "privacyConsent": true,
  "honeypot": ""
}
```

#### Successful Response HTTP 201 Created:
```json
{
  "success": true,
  "message": "Registration successfully completed.",
  "data": {
    "registrationId": "reg_a1b2c3d4-e5f6-7890-abcd-1234567890ab",
    "registrationType": "external",
    "name": "Ana Novak",
    "email": "ana.novak@example.com",
    "organization": "Inštitut Jožef Stefan",
    "selectedOptions": [
      { "id": "ws-cloud-native", "name": "Cloud Native Architecture Masterclass" },
      { "id": "meal-vegetarian", "name": "Vegetarijanski meni" }
    ],
    "submittedAt": "2026-09-21T16:05:00.000Z"
  }
}
```

#### Validation Error Response HTTP 400 Bad Request:
```json
{
  "success": false,
  "error": "Validation failed",
  "details": [
    { "field": "email", "message": "A valid email address is required." }
  ]
}
```

#### Rate Limit Response HTTP 429 Too Many Requests:
```json
{
  "success": false,
  "error": "Too many registration attempts from this IP. Please try again later."
}
```

### 5.3 `GET /api/admin/registrations`
Administrative JSON endpoint returning paginated or full registration list.

### 5.4 `GET /api/admin/export/excel`
Generates and downloads the Excel file of all registered participants.

* **Response Headers:**
  * `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
  * `Content-Disposition: attachment; filename="registracije_konferenca_2026-09-21.xlsx"`
* **Response Body:** Binary `.xlsx` stream.

### 5.5 `GET /api/health`
Health check endpoint returning `{ "status": "ok", "uptime": 123.45 }`.

---

## 6. Email Processing Specification

### 6.1 Participant Email Confirmation (`US-006`, `AC-006-01..03`)
* **Recipient:** Submitted `email`
* **Subject:** `Potrditev prijave na konferenco / Conference Registration Confirmation - {registrationId}`
* **Content:**
  * Greeting addressed to participant
  * Confirmation that the registration has been recorded
  * Registration ID reference
  * Summary of participant data (Name, Institution/Organization, Type)
  * Breakdown of chosen workshops, meals, events, and activities
  * Information on contact channels and conference dates

### 6.2 Organizer Notification Email (`US-007`, `AC-007-01..02`)
* **Recipient:** Configured `ORGANIZER_EMAIL` (default: `organizator@konferenca.si`)
* **Subject:** `Nova prijava na konferenco: {firstName} {lastName} ({registrationType})`
* **Content:**
  * Overview of the new registration
  * Summary table of participant details and selections
* **Attachment:**
  * File name: `registration_{registrationId}.json`
  * Content: Full JSON backup representation (identical to stored persistent backup)
  * MIME type: `application/json`

### 6.3 Resilient Transport
* Uses `nodemailer`.
* In production: Configured with standard SMTP transport via environment variables (`SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASS`).
* In local/test mode: When SMTP is not configured or `NODE_ENV=test`, uses a memory/logger transport ensuring tests execute reliably without requiring an external internet mail server.

---

## 7. Excel Export Specification (`US-008`, `AC-008-01..04`)

### 7.1 Workbook Structure
* Built with `exceljs`.
* Sheet Name: `Prijave` (Registrations)
* Stylized Header Row: Dark blue background `#1e3a8a`, bold white text, frozen header row, auto-fit column widths.

### 7.2 Column Mapping
| Column # | Header Name (Slovenian) | English Equivalent | Data Source |
|---|---|---|---|
| 1 | ID prijave | Registration ID | `uuid` |
| 2 | Datum in čas | Submission Timestamp | `created_at` (formatted ISO) |
| 3 | Tip prijave | Participant Type | "Zunanji udeleženec" or "Študent" |
| 4 | Ime | First Name | `first_name` |
| 5 | Priimek | Last Name | `last_name` |
| 6 | E-pošta | Email | `email` |
| 7 | Organizacija / Ustanova | Organization | `organization` or `study_institution` |
| 8 | Študijski program | Study Programme | `study_programme` (or "-") |
| 9 | Vpisna številka | Student ID | `student_id` (or "-") |
| 10 | Izbrane delavnice | Workshops | Comma-separated names |
| 11 | Izbrani dogodki | Events | Comma-separated names |
| 12 | Izbrana prehrana | Meals | Comma-separated names |
| 13 | Druge aktivnosti | Other Activities | Comma-separated names |
| 14 | Soglasje GDPR | Privacy Consent | "DA" |

### 7.3 Edge Case & Empty State Handling
* If zero registrations exist, the exported file contains the complete styled header row and zero data rows.
* Slovenian diacritics (`č`, `š`, `ž`, `ć`, `đ`) are preserved cleanly.

---

## 8. Frontend Architecture & User Experience

### 8.1 Views and Layouts
* Two dedicated, accessible registration views matching reference applications:
  1. `/` (or `/index.html`): External participant registration.
  2. `/student.html`: Student registration.
  3. `/admin.html`: Organizer overview and Excel export action.
* Responsive viewport meta tag, mobile-first design, accessible form controls, clean labels, and WCAG AA contrast compliance.

### 8.2 Client-Side Validation & Interaction Behavior
1. All required fields are validated before submission (`required`, email format, unchecked consent block).
2. Whitespace is trimmed on blur and submit.
3. Conference options are populated dynamically from `GET /api/conference-options`. Inactive options are not displayed.
4. On submit click:
   - Submit button is disabled and replaced with a loading spinner / progress text to prevent double submission.
5. Confirmation:
   - Confirmation dialog/modal appears **only after** an HTTP 201 response is received.
   - Shows registration ID, name, email, and reminder that email was sent.
6. Error handling:
   - If HTTP 400/429/500 occurs, an informative alert box displays the exact error message and the form remains editable.

---

## 9. Security & Anti-Automation Controls

1. **Anti-Bot Honeypot:**
   - A hidden input field `website_hp` with inline CSS `position: absolute; left: -9999px; opacity: 0; pointer-events: none; tab-index: -1;` and `aria-hidden="true"`.
   - Normal users do not see or fill this field.
   - If submitted with non-empty content, backend flags the submission as bot automated, logs the event, and rejects with HTTP 400.
2. **Rate Limiting:**
   - In-memory rate limiter tracking client IP.
   - Max 20 registration requests per IP per 5-minute sliding window. Exceeded requests return HTTP 429.
3. **HTTP Security Headers:**
   - `Content-Security-Policy`: Default self, script-src self, style-src self 'unsafe-inline'.
   - `X-Content-Type-Options: nosniff`.
   - `X-Frame-Options: DENY`.
   - `Referrer-Policy: strict-origin-when-cross-origin`.
4. **Input Sanitization & Injection Prevention:**
   - Parameterized SQL queries using `node:sqlite` statements.
   - Stripping / escaping of HTML tags from text inputs to neutralize stored XSS.
   - Payload limit restricted to 250 KB (`express.json({ limit: '250kb' })`).

---

## 10. Containerized Deployment Specification

### 10.1 Dockerfile
* Base image: `node:24-alpine`.
* Non-root user: `node`.
* Multi-stage or optimized production build.
* Dedicated persistent volume mount point at `/app/data` for database and backup files.
* Built-in `HEALTHCHECK` probing `GET /api/health`.

### 10.2 docker-compose.yml
* Container service `conference-app`.
* Port mapping `3000:3000`.
* Named volume `conference_data` mounted to `/app/data`.
* Configurable environment variables for SMTP and port.

---

## 11. Traceability Matrix

| Requirement / AC | Specification Section | Implementation Component |
|---|---|---|
| **US-001 / AC-001-01..07** | §2, §4, §5.2, §8, §9 | `public/index.html`, `registrationValidator.js`, `antiBot.js` |
| **US-002 / AC-002-01..04** | §2, §4, §5.2, §8 | `public/student.html`, `registrationValidator.js` |
| **US-003 / AC-003-01..04** | §2, §4, §5.1 | `config/conference-options.json`, `optionsService.js` |
| **US-004 / AC-004-01..03** | §5.2, §8.2 | `public/js/app.js`, `public/js/confirmation.js` |
| **US-005 / AC-005-01..03** | §3.1, §3.2 | `db/registrationRepository.js`, `services/backupService.js` |
| **US-006 / AC-006-01..03** | §6.1, §6.3 | `services/emailService.js` |
| **US-007 / AC-007-01..02** | §6.2, §6.3 | `services/emailService.js` |
| **US-008 / AC-008-01..04** | §5.4, §7.1..7.3 | `services/exportService.js`, `routes/adminRoutes.js` |
| **Constraints / AC-SEC, DEP**| §9, §10 | `middleware/*`, `Dockerfile`, `docker-compose.yml` |

---

## 12. Phase 2 Sign-Off
* **Status:** Complete
* **Artefact:** `docs/specification.md`
* **Completion Timestamp:** 2026-09-21T16:03:00+02:00
