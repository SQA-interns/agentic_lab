# User stories

> Owner: Product owner · Read in: phases 1, 6 · Agent: read-only

Derive AC-nnn-nn Given/When/Then criteria in phase 1, each referencing its US and
applicable BR. No generated acceptance criteria or tests are supplied here.

| ID | Story |
|---|---|
| US-001 | As an external participant, I want to submit my registration and select available conference activities so that I can attend. |
| US-002 | As a student, I want a registration form containing my study details and activity selections so that I can attend. |
| US-003 | As an organizer, I want to change workshops, events, meals and other optional activities through configuration between conferences so that code changes and an admin UI are unnecessary. |
| US-004 | As a participant, I want clear in-app confirmation after backend acceptance so that I know my registration was stored successfully. |
| US-005 | As an organizer, I want accepted registrations durably stored in a database and raw JSON files so that they remain recoverable. |
| US-006 | As a participant, I want a confirmation email for my accepted registration so that I have a record. |
| US-007 | As an organizer, I want a notification containing the submitted data and matching raw JSON attachment so that I can process registrations. |
| US-008 | As an authenticated organizer, I want to export current accepted registrations as Excel so that I can work with them offline. |

All stories are required. Success means durable backend acceptance, not email
arrival. Notifications use durable retry; actual local SMTP delivery must be
verified. Configuration is startup-loaded; restarting is acceptable.

Out of scope: participant accounts, payments, editing submissions, student-status
verification, configuration/admin UI, full organizer dashboard and external IdP.
Record discovered questions in generated decision records, referring to scope.md;
do not edit immutable input files to add new stories or questions.
