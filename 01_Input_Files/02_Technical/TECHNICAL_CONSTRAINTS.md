# Technical Constraints

These constraints define required technical behaviour.

They do not define the complete architecture.

## Client/server communication

The frontend must communicate with the backend through a REST API.

A successful in-application confirmation may be shown only after a
successful backend response.

## Persistence

Every successfully processed registration must be persisted in:

1. the relational database;
2. a raw JSON representation stored on persistent filesystem storage.

The JSON copy acts as an additional backup.

## Email

After successful registration:

- the participant receives a confirmation email;
- organizers receive a notification email;
- the organizer notification contains the submitted registration data;
- the raw registration JSON is attached to the organizer email.

## Export

The backend must provide a mechanism allowing organizers to obtain the
current registrations as an Excel workbook.

The export must not be publicly accessible without an organizer-level
access control mechanism. Participant accounts and a full
administrative UI are out of scope. The exact access-control mechanism
is decided during Specification.

## Configuration

Conference options must be changeable without changing the fixed
participant-data schema.

The exact configuration mechanism is decided during Specification.

## Runtime health

The deployed backend must expose sufficient runtime health/readiness
information for container verification.

## Architecture

The internal software architecture is intentionally not prescribed.

The agent must define and justify it in:

`02_Implementation/docs/specification.md`