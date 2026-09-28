# Security Requirements

The system is a public-facing registration application and must apply
appropriate production-level security controls.

## Baseline

Treat **OWASP ASVS 5.0, Level 1** as the baseline for "appropriate
production-level security controls" below — it is a testable
reference, not a synonym for "reasonable effort." Where a bullet below
and an ASVS Level 1 requirement overlap, satisfying the ASVS
requirement satisfies the bullet. Where this file is more specific
than ASVS (e.g. the reCAPTCHA/anti-automation requirement), the more
specific requirement here still applies.

The Specification must address at minimum:

- backend validation of all submitted input;
- frontend validation for usability;
- protection against malicious input;
- protection against automated submissions;
- backend verification of reCAPTCHA;
- rate limiting where appropriate;
- secure relational database access;
- output encoding where appropriate;
- HTTP security headers;
- request-size limits;
- secure error handling;
- secret/configuration management;
- dependency vulnerabilities;
- safe generation of email content;
- avoidance of unnecessary personal-data logging;
- validation of all configurable-option identifiers.

Frontend validation is not a security boundary.

Organizer-only operations such as registration export must not expose
participant data unintentionally. They must not be publicly accessible
without an organizer-level access control mechanism. A full
administrative UI, participant accounts and identity-provider
integration are out of scope unless later required by an immutable
input.

Security implementation decisions belong to the Specification phase.