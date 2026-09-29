# Email contract (SMTP)

> Written in: phase 2 · Source: US-006, US-007, SR-05, SR-07, D-11 · Agent: writes

Transport: SMTP to `SMTP_HOST:SMTP_PORT`, STARTTLS when `SMTP_STARTTLS=true`, authentication when `SMTP_USERNAME` is set. Local and test: Mailpit.

All messages: `Content-Type: text/plain; charset=UTF-8` body (no HTML); `From: MAIL_FROM`; headers contain no user-supplied text except the validated participant address in `To` (SR-05). No database ids or delivery status (SR-07).

## Participant confirmation (US-006)

| Part | Value |
|---|---|
| To | the participant's email |
| Subject | `Registration confirmed – <CONFERENCE_NAME>` |
| Body | greeting with first and last name; registration type; registration reference; selected options grouped by category (or "none"); the conference name |

## Organizer notification (US-007)

| Part | Value |
|---|---|
| To | every address in `ORGANIZER_EMAILS` |
| Subject | `New registration – <CONFERENCE_NAME>` |
| Body | reference, submission time (UTC), type, every participant field, selected options, consents given with timestamps |
| Attachment | `registration-<reference>.json`, `application/json`, byte-identical to the JSON copy (`registration-copy.schema.json`) |

## Delivery (D-11)

Each message is sent once after the registration is committed. A failure is recorded and retried every `MAIL_RETRY_INTERVAL` up to `MAIL_MAX_ATTEMPTS`; it never changes the registration's acceptance.
