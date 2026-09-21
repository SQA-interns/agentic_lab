# Release Notes: Conference Registration System v1.0.0

**Release Tag / Version:** `v1.0.0`  
**Experiment Branch:** `gemini_3.8_flash_medium_single_agent_classic_sdd`  
**Base Commit:** `d4a6f12668f88487c8e12ad1a4bc3b62bf9f5513`  
**Release Date:** 2026-09-21  

---

## Overview

We are pleased to announce the release of the **Conference Registration System (v1.0.0)**. This release delivers a complete, secure, responsive, and production-ready online conference registration solution with dual storage persistence (relational SQLite and filesystem JSON backups), dynamic conference options, transactional participant and organizer emails, anti-bot controls, and administrative Excel reporting.

---

## Implemented User Stories & Features

### 1. External Participant Registration (`US-001`)
* Responsive registration form at `/` (and `/index.html`) tailored for industry and external participants.
* Captures required fixed fields: First Name, Last Name, Email, Organization / Institution.
* Support for Slovenian and international Unicode characters (`č`, `š`, `ž`, `ć`, `đ`).
* Client and server-side input validation and automatic whitespace trimming.
* Anti-automation protection via hidden honeypot field.

### 2. Student Registration (`US-002`)
* Dedicated student registration portal at `/student.html`.
* Captures student-specific academic information: Study Institution, Study Programme, and Student ID.
* Ensures validation of student credentials and proper classification in downstream reports and emails.

### 3. Configurable Conference Options (`US-003`)
* Dynamic loading of conference options via `GET /api/conference-options`.
* Decoupled configuration (`config/conference-options.json`) for:
  - Workshops
  - Keynotes and events
  - Meal selections (standard, vegetarian, vegan)
  - Other optional activities (e.g. morning run, city tour)
* Server-side rejection of inactive or unknown option identifiers.

### 4. Registration Confirmation in the Application (`US-004`)
* Strict timing guarantee: Success confirmation card is rendered only upon receiving an HTTP `201 Created` response from the backend.
* Displays unique registration reference ID (`reg_<uuid>`), participant details, summary of chosen activities, and notification of confirmation email delivery.
* Clear, informative error alerts on invalid inputs or rate limiting.

### 5. Reliable Registration Storage & Persistent Backup (`US-005`)
* **Relational Persistence:** SQLite database (`data/database.sqlite`) in WAL mode with ACID guarantees, structured schema, and indexed queries.
* **JSON File Backup:** Standalone formatted JSON file generated for every registration at `data/backups/registration_<uuid>.json` for disaster recovery.

### 6. Participant Email Confirmation (`US-006`)
* Automatic transactional email dispatched to the participant upon successful registration.
* Contains full registration details, reference ID, and breakdown of chosen workshops and meals.
* Resilient architecture with error isolation preventing email transport delays from interrupting persistence.

### 7. Organizer Notification with JSON Attachment (`US-007`)
* Automatic notification email dispatched to conference organizers (`organizator@konferenca.si`).
* Includes the registration data summary and attaches the full JSON backup file (`registration_<uuid>.json`).

### 8. Microsoft Excel Export (`US-008`)
* Administrative dashboard at `/admin.html` with real-time registration counters.
* Dedicated export endpoint `GET /api/admin/export/excel` generating styled OpenXML `.xlsx` spreadsheets with complete participant data, UTF-8 Slovenian character rendering, and empty-state support.

### 9. Security & Containerized Deployment
* In-memory IP rate limiter returning HTTP `429 Too Many Requests`.
* Strict HTTP security headers: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy`, and `Content-Security-Policy`.
* HTML script tag sanitization against XSS.
* Production Docker containerization (`Dockerfile` based on `node:24-alpine` and `docker-compose.yml`) with automated health checks and persistent storage mounts.

---

## Test & Quality Verification

* **Automated Test Suite:** 30 automated tests across 7 test suites (Unit, Integration, Contract, Security).
* **Test Pass Rate:** 100% (30 passed, 0 failed).
* **Verification Status:** All 24 Acceptance Criteria verified with zero defects or fix loops.
