# Accepted architecture and consequences

These are this package's pre-run baseline decisions, including choices that
the source project left to its agent. Each rule ID is independently
reviewable. Choose internal implementation details only within these
boundaries; record them in the decisions section of the generated
`docs/specification.md` without rewriting this file.

Decision context: a small registration service with two client forms, durable
acceptance, notifications and a limited organizer export. A single backend
avoids distributed-service overhead. Async durable mail decouples acceptance
from SMTP availability at the cost of retry state. HTTP Basic keeps the export
surface small but depends on external TLS and credential operations. Startup
configuration avoids an admin UI but requires restarts. Database plus filesystem
storage is a source constraint; its reconciliation cost is unavoidable here.
These rationales explain the baseline; they do not authorize changing it.

| ID | Decision | Observable consequence |
|---|---|---|
| AR-01 | Separate React frontend and a single Spring Boot backend communicating through REST. | Both apps build independently; all authoritative validation/storage is in the backend. |
| AR-02 | Keep the backend as one deployable with cohesive registration, options, notification, and export modules. | No service-per-feature split or shared mutable database with frontend. Avoid cyclic module dependencies; ArchUnit must check chosen boundaries. |
| AR-03 | PostgreSQL is the authoritative registration index. Flyway owns schema changes. Raw JSON backups use a separate persistent volume. | Never rely on a container's ephemeral filesystem or Hibernate auto-DDL in production. |
| AR-04 | An accepted API response requires both committed database state and the corresponding durable raw JSON copy. | Do not report success after only one write. Handle partial failure and crash leftovers explicitly. |
| AR-05 | Outgoing emails are recorded for retry after acceptance; delivery occurs asynchronously. | SMTP failure cannot erase an accepted registration. Report delivery as pending/failed rather than falsely claiming an email arrived. |
| AR-06 | Organizer Excel export requires Spring Security HTTP Basic with an organizer credential sourced from environment configuration; production access only behind HTTPS at the external proxy. | No public export, credential in source, participant account, or full admin UI. Test unauthorized and authorized access. |
| AR-07 | Conference option sets are read from external configuration at service startup; changing them requires configuration update and service restart, not recompiling. | Fixed participant fields remain fixed; unknown/inactive options are rejected server-side. |
| AR-08 | External nginx terminates TLS, manages Let's Encrypt, serves/routes the frontend, and forwards `/api` to backend. Application containers expose HTTP only inside the trusted deployment network. | No TLS keys inside application containers; document forwarded-header trust and enforce secure external routing. |

## Registration consistency

Use a server-generated stable registration ID. Validate and verify captcha
before durable writes. Stage JSON on the persistent volume and publish it
atomically; commit the matching database registration with email delivery
records. If either write fails, do not return success; reconcile orphaned
files/records after a crash. Document exact ordering, cleanup, and recovery
in the generated specification. PostgreSQL and filesystem writes do **not**
form one ACID transaction: do not claim they do. Protect against duplicate
submissions/retries with a stable client request ID and uniqueness handling.

The participant sees success once both durable representations are present;
email is queued for retry and may arrive later. Local container verification
must demonstrate actual delivery to the SMTP catcher, including organizer
JSON attachment. Any permanent email failure remains a recorded operational
failure, not a secretly successful notification.

## Security and operations

Validate all input and option IDs server-side; encode outputs; generate safe
email content; set appropriate security headers; bound request sizes; rate
limit public submission; handle errors without leaking personal data or
credentials; avoid unnecessary personal-data logs. Expose backend health and
readiness. Keep secrets and environment-specific configuration out of code.

These decisions fix topology and failure semantics. REST endpoint naming,
internal package names, option-file format, and UI design are implementation
choices to record and justify. Do not introduce new business capabilities.
