# Business Rules

These rules define business behaviour shared by one or more User Stories.

## Registration types

The system supports:

- external participant registration;
- student registration.

## Participant fields

The fixed fields for each registration type are defined in
`FORM_SCHEMA.md`.

Fixed participant fields do not change through conference configuration.

## Configurable options

The following may change between conferences:

- workshops;
- events;
- meals;
- other optional conference activities.

Only currently active options may be selected.

## Successful registration

A participant must receive an in-application confirmation only after
their registration has been successfully accepted.

## Organizer access

Organizers must be able to obtain the current list of registrations in
Excel format.

No participant account system or full administrative UI is required.
The organizer export may be exposed through a minimal dedicated
endpoint or view, but it must not be publicly accessible without an
organizer-level access control mechanism. The exact mechanism is
decided during Specification.

## Scope boundaries

Unless explicitly required elsewhere, the system does not require:

- participant accounts;
- payment processing;
- editing submitted registrations;
- an administrative UI for conference-option configuration;
- a full administrative dashboard or identity-provider integration.