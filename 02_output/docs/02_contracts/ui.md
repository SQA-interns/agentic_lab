# UI contract (participant ↔ frontend)

> Written in: phase 2 · Source: US-001 … US-004, BR-01, BR-05, BR-06, NFR-03 · Agent: writes

One page at `/`. Language of the labels: English (the configured option names and consent texts are shown as configured). All controls have visible labels bound with `<label for>`; end-to-end tests locate elements by these accessible names, so they are part of the contract.

## Page structure

1. Heading (level 1): `Registration: <conferenceName>` (from `/api/config`).
2. Radio group with legend `Registration type` and two radios: `External participant` (selected by default) and `Student`.
3. Fieldset with legend `Your details`, containing only the fields of the selected type (BR-01):
   - External participant: `First name`, `Last name`, `Email`, `Organization / institution`
   - Student: `First name`, `Last name`, `Email`, `Study institution`, `Study programme`, `Student ID`
   Switching type keeps the common fields' values and hides the others; hidden fields are not sent.
4. Fieldset with legend `Options`, one sub-group per category that has active options, in this order and with these headings (level 2): `Workshops`, `Events`, `Meals`, `Other activities`. Each active option is a checkbox labelled with its name. Only options from `/api/options` are shown (active only).
5. Fieldset with legend `Consents`: one checkbox per consent, labelled with its text, never pre-checked (BR-05, SB-14). Required consents are marked `(required)`.
6. reCAPTCHA area: the Google widget, or the text `reCAPTCHA test mode` in test mode.
7. Submit button `Register`. While a request is running it is disabled and reads `Submitting…`.

## Validation and errors (NFR-03)

- Before sending, the frontend checks: required fields of the selected type non-empty after trimming, email format, every required consent checked. For each failure it shows a message in an element with id `<field>-error` directly after the field, linked with `aria-describedby`, and sends no request (AC-004-03). Consents use `consents-error`; options use `optionIds-error`; reCAPTCHA uses `recaptchaToken-error`.
- Messages: empty field `This field is required.`; invalid email `Enter a valid email address.`; missing consent `This consent is required.`
- On a 400 or 409 response the frontend shows each `fieldErrors[].message` in the matching `<field>-error` element, keeps all entered values and shows no confirmation (AC-004-02). Other failures (413, 429, 5xx, network) show a general alert (`role="alert"`) with a generic message.

## Confirmation (BR-06, AC-004-01)

Shown only after a 201 response; it replaces the form:

- Heading (level 2) `Registration received`.
- Text `Thank you, <firstName> <lastName>. Your registration ID is <id>.`
- A list headed `Selected options` with each selected option name, or `No options selected.`
- Text `A confirmation email has been sent to <email>.`
