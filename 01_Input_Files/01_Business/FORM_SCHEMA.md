# Form Schema

This file defines the fixed participant data and configurable conference
options.

It is an immutable experimental input.

## External participant

Fixed fields:

- First name
- Last name
- Email
- Organization / institution

## Student participant

Fixed fields:

- First name
- Last name
- Email
- Study institution
- Study programme
- Student ID

## Configurable conference options

The following sets are configurable:

- Workshops
- Events
- Meals
- Other optional conference activities

Each option must have at minimum:

- stable identifier;
- display name;
- active/inactive status.

## Consent

The forms must support mandatory consent fields where required.

Mandatory consent must not be preselected.

## General data rules

- Required fields must not be empty.
- Email must have a valid email format.
- Text input must support Unicode, including Slovenian characters.
- Leading and trailing whitespace must not be significant.
- Unknown or inactive configurable options must not be accepted.

## Scope

This file defines data only.

It does not define:

- API design;
- database schema;
- architecture;
- frameworks;
- storage implementation;
- security implementation;
- testing strategy.