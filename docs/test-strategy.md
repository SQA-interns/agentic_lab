# Test Strategy & Level Documentation

**Project:** Conference Registration System  
**Phase:** Phase 4 — Tests  
**Creation Timestamp:** 2026-09-21T16:17:00+02:00  

---

## 1. Selected Test Levels and Rationale

In accordance with `PROMPT.md`, the test suite is structured across multiple distinct testing levels. Each level draws from its designated authoritative source artefact:

### 1.1 Unit Tests (`tests/unit/`)
* **Source Artefact:** Technical Specification (`docs/specification.md`) and implementation modules.
* **Scope:**
  - `validation.test.js`: Validates input trimming, Unicode/Slovenian character preservation (`č`, `š`, `ž`), email syntax validation, and field requirements for both external and student schemas.
  - `options.test.js`: Tests the loading of conference options, mapping, category separation, and rejection of inactive or unknown option identifiers.
* **Rationale:** Unit tests verify deterministic logic in isolation with high speed and precision, catching data validation regressions immediately.

### 1.2 Integration & Persistence Tests (`tests/integration/`)
* **Source Artefact:** Acceptance Criteria (`docs/acceptance-criteria.md`) and Technical Specification (`docs/specification.md`).
* **Scope:**
  - `persistence_backup.test.js`: Tests atomic SQLite relational persistence (`AC-005-01`), JSON backup file creation on persistent disk (`AC-005-02`), and schema consistency between database rows and JSON files (`AC-005-03`).
  - `email.test.js`: Verifies email generation, participant confirmation content (`AC-006-01..02`), organizer notification with JSON attachment (`AC-007-01..02`), and fault isolation.
  - `export.test.js`: Verifies Microsoft Excel workbook generation (`AC-008-01..04`), column headers, data mapping, empty-state resilience, and Unicode preservation.
* **Rationale:** Ensures that the relational database, local filesystem storage, email subsystem, and reporting engine cooperate reliably across boundaries.

### 1.3 REST API & Contract Tests (`tests/api/`)
* **Source Artefact:** User Stories (`USER_STORIES.md`), Acceptance Criteria (`docs/acceptance-criteria.md`), and API Specification.
* **Scope:**
  - `registration_api.test.js`: End-to-end HTTP request/response tests for `GET /api/conference-options`, `POST /api/register` (both external and student variants), `GET /api/admin/registrations`, `GET /api/admin/export/excel`, and `GET /api/health`.
  - Verifies HTTP status codes (`200 OK`, `201 Created`, `400 Bad Request`), response bodies, validation messages, and timing semantics.
* **Rationale:** Validates the exact contracts exposed to web and mobile clients, guaranteeing compliance with protocol requirements.

### 1.4 Security & Anti-Bot Tests (`tests/security/`)
* **Source Artefact:** Project Constraints (`PROJECT_CONSTRAINTS.md`), Acceptance Criteria (`AC-SEC-01..03`, `AC-001-07`).
* **Scope:**
  - `security_antibot.test.js`: Evaluates honeypot bot trap detection, IP rate limiting triggering HTTP 429, payload size rejection, XSS text sanitization, and security headers (`X-Content-Type-Options`, `X-Frame-Options`, `CSP`).
* **Rationale:** Verifies the defensive posture against automated spam, high-frequency abuse, and web vulnerabilities.
