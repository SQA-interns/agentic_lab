# Project Constraints

These constraints are mandatory project-level requirements. They are input to the development process and are not Acceptance Criteria. Acceptance Criteria must be derived separately from the User Stories and these constraints.

## Registration forms

The system must provide two registration variants:

* external participant registration
* student registration

Reference applications:

* https://prijava.informatika.uni-mb.si/prijava
* https://prijava.informatika.uni-mb.si/studentska-prijava

The set of participant input fields is fixed.

The following sets are configurable and may change without changing the fixed participant fields:

* workshops
* events
* meals
* other optional checkbox-based conference activities

The exact fixed field schema must be frozen before the experiment and stored in the repository so that every experimental run receives exactly the same input.

## Frontend/backend communication

A valid registration must be submitted to the backend through a REST API.

The frontend may display the successful registration confirmation only after receiving a successful response from the backend.

## Persistence and backup

For every successfully processed registration, the backend must:

* persist the registration in a relational database;
* create a JSON representation of the registration and store it on persistent storage.

## Email

After a successful registration:

* the participant must receive a confirmation email;
* conference organizers must receive a notification email containing the registration information;
* the organizer notification must include the registration JSON file as an attachment.

## Export

Organizers must have access to an export of registered participants in Excel format.

## Validation and security

The application must implement appropriate production-level protections, including:

* frontend input validation;
* backend input validation;
* appropriate validation error messages;
* protection against malicious input;
* protection against automated/bot submissions;
* appropriate backend preventive security controls;
* secure handling of submitted participant data.

Frontend validation must not be treated as a replacement for backend validation.

## Frontend quality

The frontend must be responsive and usable on relevant desktop and mobile screen sizes.

## Deployment

The application must support containerized deployment.

## General quality

The final result is expected to be production-ready.

Architecture, implementation choices, validation strategy, security controls, testing strategy and deployment decisions must be documented and justified during the specified development process.

No Acceptance Criteria or implementation specification are provided in this document. Those artefacts must be created during the experiment.