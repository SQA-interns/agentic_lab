# Security requirements

> Owner: Security officer · Read in: phases 2, 6 · Agent: read-only

Adds project controls to SB-01…SB-14; no security gate is waived.

## Verification standard

| Component | Standard | Level |
|---|---|---|
| backend | OWASP ASVS, exact published revision captured in phase-2 specification | Level 2 applicable controls: organizer authentication and personal data |
| frontend | Same ASVS revision | Applicable Level 2 browser controls; authentication remains server-side |

This is a control-based review, not certification. Document inapplicable controls
with reasons; do not invent evidence for untested production integrations.

## Authentication and authorization

Registration/catalog are public. Excel export requires a configured organizer
username and salted slow password hash; Spring Security HTTP Basic only over HTTPS
outside localhost. No participant account, registration lookup by email or public
export. No new session/JWT system. Retain appropriate CSRF/origin protections for
browser-sent credentials; do not disable protections globally for convenience.

## Personal data

| Data | Purpose | Retention | Disclosure |
|---|---|---|---|
| Names/email | Identify registration, confirmation | Synthetic lab data: until operator ends experiment and deletes run volumes; real policy not approved | Participant's own confirmation; organizer |
| Organization/study details/student ID | Registration context | Same | Authorized organizer; participant's own data |
| Selections/consent/request IDs | Fulfilment, consent fixture, idempotency | Same | Authorized organizer; own confirmation where relevant |
| JSON/Excel/mail copies | Recovery and organizer processing | Same; deletion must cover every representation | Authorized organizer only |

## Project requirements

- SR-01: Production captcha verifies server-side; local/test substitute cannot be
  activated in production. Missing/invalid production configuration fails closed.
- SR-02: JSON paths use generated IDs, not user paths; inaccessible through static
  hosting. Backup and export receive equivalent access protection.
- SR-03: Bound input/request sizes and rate-limit registration/authentication; specify
  chosen operational limits in design. Test legitimate Unicode input remains usable.
- SR-04: Treat spreadsheet text as text, preventing formula injection; safely encode
  mail/HTML and prevent user-controlled mail headers/recipient override.
- SR-05: Local SMTP is isolated. Only synthetic identities and reserved test-domain
  addresses are authorized. Production consent/privacy/retention decisions block deployment.
- SR-06: Keep original scanner findings and severity. Downgrades require the general
  approval process, never silent rewriting of raw reports.

## Overrides

None. SB-13's retention statement above applies only to synthetic experimental data.
