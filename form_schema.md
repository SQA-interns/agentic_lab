
Form Schema

This file defines the fixed registration fields and configurable conference options.

It is an immutable experiment input and must remain identical across all compared runs.

External participant registration

Fixed fields:

* First name
* Last name
* Email
* Organization / institution

Student registration

Fixed fields:

* First name
* Last name
* Email
* Study institution
* Study programme
* Student ID
 
Configurable conference options

The following are configurable and may change without changing the fixed participant fields:

* Workshops
* Events
* Meals
* Other optional conference activities

Each configurable option must have at least:

* stable identifier
* display name
* active/inactive status

Consent

The form must support mandatory consent fields where required, for example privacy/data-processing consent.

Mandatory consent must not be preselected.

General field rules

* Required fields must not be empty.
* Email must have a valid email format.
* Text fields must support Unicode characters, including Slovenian characters.
* Leading and trailing whitespace should not be significant.
* Validation must exist on both frontend and backend.
* Configurable option identifiers submitted by the client must be validated by the backend.
* Inactive or unknown configurable options must not be accepted.

Scope

This file defines only the form data.

It does not define:

* API design
* database schema
* architecture
* implementation technologies
* test strategy
* security implementation
* storage format

Those decisions belong to later phases of the development process.
