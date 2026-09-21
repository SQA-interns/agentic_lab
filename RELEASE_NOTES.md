# Release Notes — Conference Registration System 1.0.0

**Date:** 2026-09-21
**Branch merged:** `experiment/conference-registration-opus5` → `opus5_medium_single_agent_classic_sdd`
**Baseline commit:** `d4a6f12668f88487c8e12ad1a4bc3b62bf9f5513`

First release. The repository previously held only the experiment inputs
(`USER_STORIES.md`, `PROJECT_CONSTRAINTS.md`, `FORM_SCHEMA.md`, `PROMPT.md`) and an empty
documentation folder; this release adds the entire application.

---

## What was delivered, by User Story

### US-001 — External participant registration

An external participant registration form at `/` presenting exactly the fixed fields from
`FORM_SCHEMA.md` (first name, last name, email, organization / institution), the currently
active conference options grouped into workshops, events, meals and other activities, and a
mandatory data-processing consent that is never pre-ticked. Selecting activities is optional.
Invalid submissions are rejected with a message on each offending field and nothing is
stored. Slovenian characters are preserved, surrounding whitespace is insignificant, and
fields belonging to the other variant are discarded rather than stored.

### US-002 — Student registration

A separate student form at `/studentska-prijava/` with its own fixed fields (first name,
last name, email, study institution, study programme, student ID). Options that the
programme marks as available only to external participants are neither offered to students
nor accepted from them. Each form links to the other.

### US-003 — Configurable conference options

Workshops, events, meals and other activities live in
[`config/conference-options.json`](config/conference-options.json), outside the source tree.
Each option carries a stable identifier, a display name, an active flag and an optional
variant restriction. Adding, renaming, deactivating or removing an option requires no change
to the fixed participant fields, the database schema or the API request shape. The file is
schema-validated at startup: a duplicate identifier, a missing field or an unknown group
stops the application from starting instead of surfacing at the first registration.
Registrations stored before a programme change stay readable, because each selection keeps
the identifier as submitted and the display name as it was at the time.

### US-004 — Registration confirmation in the application

The frontend shows the confirmation **only** after the backend returns `201`. The
confirmation gives the registration reference, the selected activities and the address the
confirmation email went to. A validation rejection shows per-field messages and no
confirmation; a backend outage shows a clearly technical message, keeps everything the
participant typed and re-enables the submit button. The submit control is disabled while a
request is in flight, so one click cannot produce two registrations.

### US-005 — Reliable registration storage

Every accepted registration is written to a relational database (SQLite, WAL,
`synchronous = FULL`) **and** as a self-contained JSON file on persistent storage. Both
writes happen inside one transaction: if the file cannot be written the database row is
rolled back, and if the commit fails the file is removed. The success response is returned
only once both are durable, so `201` is a truthful promise. The JSON file is written to a
temporary name, flushed and renamed, so a file that exists is always complete. Each backup
file alone is enough to reconstruct a registration without the database. Concurrent
submissions each get a distinct reference and a distinct file, and everything survives a
restart.

### US-006 — Participant email confirmation

Exactly one confirmation email per accepted registration, to the submitted address,
containing the reference, the participant's name, the participant type and the display names
of the selected activities. Participant-supplied values are escaped in the HTML part, and
control characters are rejected at the boundary, so neither markup nor extra mail headers can
be injected.

### US-007 — Organizer notification

Exactly one notification per accepted registration to the configured organizer addresses,
containing the reference, the timestamp, the participant type and every fixed field of the
variant, with the registration JSON attached. The attachment is read back from persistent
storage, so it is byte-identical to the stored backup. `Reply-To` is the participant, so
organizers can answer directly; the two recipient lists never cross. Organizer addresses are
configuration.

Email is never a precondition for storage: if sending fails, the registration stays stored in
both places, the participant still sees the confirmation, and the failure is logged.

### US-008 — Excel export

`GET /api/export/registrations.xlsx` returns a real `.xlsx` workbook with a header row and
one row per registration, covering the reference, timestamp, participant type, the fixed
fields of both variants, the consent record and the selected options grouped by category. It
reflects storage at the moment of the request, preserves Slovenian characters, and returns a
header-only workbook when nothing is registered. The endpoint requires HTTP Basic
credentials and is rate-limited; cell values that begin with a formula character are written
as text so the export cannot execute code on an organizer's machine.

---

## Cross-cutting

**Validation.** One rule table, declared on the backend and published to the browser through
`GET /api/registration-config`, so the two cannot drift. The frontend validation is a
usability layer; the backend re-applies every rule to every request, and a request sent
straight to the API gains nothing. Rejections list every offending field at once.

**Security.** Parameterised SQL only; all dynamic values rendered via `textContent`; a
content security policy with no inline or third-party script; control characters rejected at
the boundary; strict schemas that reject unknown properties; a 32 KB body limit; security
response headers; an exact CORS origin allowlist; per-endpoint rate limits; constant-time
credential comparison; personal data redacted from logs and kept out of URLs. Anti-automation
combines a honeypot, an HMAC-signed single-use form token bound to the variant with a
lifetime and a minimum fill time, and a per-IP rate limit — every rejection returns the same
opaque message. `npm audit` reports no vulnerabilities.

**Frontend quality.** Mobile-first layout verified in a real browser at 375 px and 1280 px
with no horizontal overflow, labelled inputs, error messages linked to their fields, a status
region for the confirmation, full keyboard operability and visible focus outlines.

**Deployment.** `docker compose up --build` builds and starts both services; this was run,
not just written. The backend comes up, reports healthy, and only then does the frontend
start. The backend image runs as a non-root user and contains no development dependencies.
A named volume at `/data` holds the database and the JSON backups, verified to survive both
a container restart and a full `down`/`up` recreation. All environment-specific values come
from the environment, and the backend exits with code 1 and a message naming the problem
when a required value is missing or the conference programme is invalid.

---

## Architecture

A layered modular monolith: `api → application → domain / infrastructure`, with the domain
layer depending on nothing. The rules are machine-checked by `dependency-cruiser`, which also
forbids any module outside the repository from importing the database driver. Only
`registrationRepository.ts` contains SQL, so changing database engine is a single-module
change. Decisions and their trade-offs are recorded as ADR-001 … ADR-005 in
[`docs/specification.md`](docs/specification.md).

---

## Quality at release

| | |
| --- | --- |
| Tests | 397 passing (281 backend, 42 frontend, 28 native browser end-to-end, 46 against the containerized stack) |
| Backend coverage | 95.21% statements, 82.82% branches, 96.02% functions |
| Lint | 0 errors, 0 warnings |
| Type check | 0 errors, strict mode |
| Dependency advisories | 0 |
| Architecture rule violations | 0; 0 dependency cycles |
| Production code duplication | 0.00% |
| Cyclomatic complexity | average 2.41, maximum 12 |
| Verification findings | 3 Major, 4 Minor — all resolved or documented; 0 Critical |

All 87 acceptance criteria are verified. Verification details, including the two Major
defects that only running the containers could reveal, are in
[`docs/verification-report.md`](docs/verification-report.md).

---

## Known limitations

* **Real SMTP delivery to a mailbox is not demonstrated.** No mail server was available. The
  message composition including the attachment is tested, and the container run demonstrated
  the failure path against an unreachable SMTP host, so only delivery to a real inbox remains
  an operational check.
* **Single backend instance.** The used-token cache and the rate-limit counters are
  per-process; running several instances would weaken both until a shared store is added.
* **TLS terminates upstream.** The application expects a TLS-terminating reverse proxy;
  `TRUST_PROXY` must be enabled only when one is really in front.
* **SQLite scales vertically only.** Appropriate for a conference's volume; a different engine
  would be needed for horizontal scaling. The repository is the only module to change.
* **No third-party CAPTCHA**, by design — see ADR-005 for the reasoning and the extension
  point.

---

## Fixed before release, found by running the containers

Two Major defects existed in the deployment artefact and were invisible to every check that
did not start the containers. Both are fixed and regression-tested.

* **No security headers on any served HTML document.** The Content-Security-Policy and the
  four other security headers were declared once at nginx `server` level. nginx does not
  inherit `add_header` into a location that declares one of its own, and both document
  locations set `Cache-Control` — so the documents, the very thing the policy protects, were
  served bare, while the proxied API still looked correct. The headers are now included
  explicitly per location.
* **Read-path rate limits denied the form to participants sharing one address.** Loading the
  form costs one configuration request, and the limit was 60 per five minutes per address.
  Everyone behind a university or company NAT shares that budget, so a registration rush
  would have locked them out. The read limits are now 300 per five minutes and a 1200 per
  fifteen minutes global backstop, all tunable; the strict 5 per 10 minutes on submissions —
  the control that actually caps abuse — is unchanged.

## Upgrading

Not applicable: this is the first release. For a new deployment, copy `.env.example` to
`.env`, fill in the SMTP settings, organizer addresses, export credentials and the form-token
secret, edit `config/conference-options.json` to match the conference programme, then run
`docker compose up --build`.
