# Project Constraints

These are mandatory project-level requirements. They are input to the
development process and are not Acceptance Criteria — Acceptance
Criteria must be derived separately, per `ACCEPTANCE_CRITERIA_RULES.md`,
from `USER_STORY.md` and this file.

Technology choices (language, framework, database, deployment tooling)
are defined in `TECH_STACK.md`, not here. If anything below appears to
require a specific technology, treat `TECH_STACK.md` as authoritative
and this file as describing the requirement only.

## Registration forms

The system must provide two registration variants:

* external participant registration
* student registration

Reference applications:

* https://prijava.informatika.uni-mb.si/prijava
* https://prijava.informatika.uni-mb.si/studentska-prijava

The set of participant input fields is fixed (see `FORM_SCHEMA.md`).

The following sets are configurable and may change without changing the
fixed participant fields:

* workshops
* events
* meals
* other optional checkbox-based conference activities

The exact fixed field schema is frozen in `FORM_SCHEMA.md` so that every
experimental run receives exactly the same input.

## Frontend/backend communication

A valid registration must be submitted to the backend through a REST
API.

The frontend may display the successful registration confirmation only
after receiving a successful response from the backend.

## Persistence and backup

For every successfully processed registration, the backend must:

* persist the registration in a relational database;
* create a JSON representation of the registration and store it on
  persistent storage.

## Email

After a successful registration:

* the participant must receive a confirmation email;
* conference organizers must receive a notification email containing
  the registration information;
* the organizer notification must include the registration JSON file
  as an attachment.

## Export

Organizers must have access to an export of registered participants in
Excel format.

## Validation and security

The application must implement appropriate production-level
protections, including:

* frontend input validation;
* backend input validation;
* appropriate validation error messages;
* protection against malicious input;
* protection against automated/bot submissions;
* appropriate backend preventive security controls;
* secure handling of submitted participant data.

Frontend validation must not be treated as a replacement for backend
validation.

## Frontend quality

The frontend must be responsive and usable on relevant desktop and
mobile screen sizes.

## Deployment

The application must support containerized deployment. The built
container must actually be run and exercised as part of verification —
see `DEFINITION_OF_DONE.md`.

## General quality

The final result is expected to be production-ready.

Architecture, implementation choices, validation strategy, security
controls, testing strategy and deployment decisions must be documented
and justified during the development process.

No Acceptance Criteria or implementation specification are provided in
this document. Those artefacts must be created during the run, per
`ACCEPTANCE_CRITERIA_RULES.md`.
