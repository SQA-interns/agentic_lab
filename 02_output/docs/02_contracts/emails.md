# Email contract (backend → SMTP)

> Written in: phase 2 · Source: US-006, US-007, SR-05, SR-07 · Agent: writes

Both emails are sent after the registration is stored (AR-05, D-08), each in its own SMTP transaction. A failure is logged with the registration id only and does not change the API response.

## Common rules (SR-05)

- `Content-Type: text/plain; charset=UTF-8`; the body is never HTML, so markup in user input appears literally.
- Headers contain no user input except the participant's validated address in `To`; every text field has already been rejected if it contains CR, LF or another control character.
- `From`: `MAIL_FROM`. Subjects contain only the conference name, the registration type and the registration id.

## Participant confirmation (US-006, AC-006-01)

- To: the participant's email. Subject: `Registration confirmed: <CONFERENCE_NAME>`.
- Body lines, in order:

```
Dear <firstName> <lastName>,

your registration for <CONFERENCE_NAME> has been received.

Registration ID: <id>
Registration type: External participant | Student

Selected options:
- <option name> (<Workshop|Event|Meal|Other activity>)
(or "- none")

This is an automatic message.
```

## Organizer notification (US-007, AC-007-01, AC-007-02)

- To: every address in `ORGANIZER_EMAILS` (one message, all addresses in `To`). Subject: `New registration (<EXTERNAL|STUDENT>): <id>`.
- `multipart/mixed`: part 1 is the plain-text body, part 2 the attachment.
- Body: one `Label: value` line per field of the JSON copy, in schema order, with the labels of the export header (`02_specification.md` 5.4), then the options and consents as in the participant email. Nothing else.
- Attachment: filename `registration-<id>.json`, `Content-Type: application/json`, content byte-for-byte equal to the JSON copy file (`registration-copy.schema.json`).
