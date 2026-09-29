# Business rules

> Owner: Product owner · Read in: phases 1, 6 · Agent: read-only

## Rules

- BR-01: Required values must remain nonblank after trimming Unicode whitespace,
  including NBSP. Preserve Unicode content, including Slovenian characters.
- BR-02: Validate email format and all required fields on the backend; client-side
  validation is additional usability support, never the security boundary.
- BR-03: Option groups are workshops, events, meals and other optional activities.
  Each option has a stable ID, display name and active flag. Reject unknown/inactive
  selections, including direct API requests. No student-only option restrictions.
- BR-04: Where required, consent starts unchecked and is enforced server-side.
  Use a clearly synthetic consent fixture for local verification. No invented
  legal notice, legal basis or production consent categories.
- BR-05: Accept only when the database record and corresponding durable raw JSON
  both exist. Show success only after backend acceptance; no false partial success.
- BR-06: Same client request ID retried must not create another accepted record.
  Server assigns the registration ID. Distinct request IDs may register the same email again; email is not an identity
  or uniqueness constraint.
- BR-07: Accepted registrations retain durable participant/organizer notification
  intent. SMTP outage cannot discard acceptance. Retry; never claim unobserved delivery.
- BR-08: Organizer mail includes submitted data and the matching raw JSON attachment.
- BR-09: Excel contains current accepted registrations from both forms; only an
  authenticated organizer may obtain it.
- BR-10: Catalog changes require configuration update/restart, not recompilation
  or modification of fixed participant fields.

## Data

| Form/entity | Required data |
|---|---|
| External | first name, last name, email, organization/institution |
| Student | first name, last name, email, study institution, study programme, student ID |
| Selection | known active option IDs; zero or more distinct options per group; no capacity limits |
| Stored registration | registration ID, client request ID, form type, normalized submitted fields/selections and applicable consent state |

Student ID is text; no unprovided institutional verification/format rule.
No booking capacity, exclusivity or waiting list. The local catalog uses synthetic
workshop/event/meal/other choices. Field-length/request limits are technical design
choices; document and test them. Real-data retention is outside this lab run.

## Glossary

Accepted: both durable representations exist. Pending notification: delivery remains
to be completed. Organizer: configured credential holder authorized for Excel export.
