# Release notes

> Written in: phase 7 · Agent: writes

Release 1.0.0 of the conference registration system, run `05_conference-registration_opus5.5_sdd_template-1.2`, 2026-09-30.

## Delivered

- US-001 / US-002: registration forms for external participants (first name, last name, email, organization / institution) and students (plus study institution, study programme, student ID), with server-side validation of every field, Unicode text (č, š, ž), trimming, email format, and rejection of fields of the other form, unknown fields and control characters.
- US-003: options (workshops, events, meals, other activities) and consents from a JSON configuration file validated at startup; only active options can be chosen; all options are open to both types (D-06), without a per-category limit (D-09).
- US-004: confirmation page only after the backend accepted the registration; client-side validation with messages next to the fields; server errors shown next to the fields with the entered values kept.
- US-005: every accepted registration is stored in PostgreSQL (Flyway schema) and as a raw JSON copy `<id>.json` on a persistent volume; the success response is sent only after both are written; a failed copy rolls the database back.
- US-006: plain-text UTF-8 confirmation email to the participant; an email failure does not undo the registration (D-08).
- US-007: notification to all organizer addresses with the submitted data and the JSON copy attached unchanged.
- US-008: Excel export (`.xlsx`) of all registrations for the organizer login (HTTP Basic, HTTPS-only outside localhost).
- Security: reCAPTCHA v2 verified on the backend (fail closed) with a deterministic test mode that production refuses; rate limits on registration, export (including failed logins) and reads; 16 KiB request limit (64 KiB at nginx); duplicate registrations per email rejected (D-10); security headers; non-root containers; no secrets or personal data in logs.
- Operations: health and readiness endpoints used by the container health checks; local stack with Docker Compose (backend, frontend, PostgreSQL, Mailpit); data on named volumes survives container recreation.
- Quality evidence: 105 frozen acceptance tests, 8 frozen end-to-end tests, 123 backend and 45 frontend unit/component tests, all passing; mutation score 84 % (backend and frontend); see `06_verification-report.md`.

## Known limitations

- Retention: no automatic deletion of registrations or JSON copies; organizers delete them manually after the conference (D-11, backend README).
- A second registration with the same email is refused with 409, which tells the submitter that the address is already registered (D-10).
- Rate limits are kept in memory per backend instance; running several backend instances multiplies the limits and a restart resets them.
- The backend trusts `X-Forwarded-For` and `X-Forwarded-Proto` from private-network addresses; it must be reachable only through the reverse proxy, which must set both headers itself (F-10).
- Container images are not scanned for vulnerabilities by this project's tooling; scan them before production (F-08).
- `.npmrc` cannot set `min-release-age` because the pinned npm 11.6.2 does not support it (F-02); dependencies are exact and locked.
- Five moderate npm advisories remain in development and test tools only (Vitest, `qs` via tooling); nothing ships in the frontend bundle (F-03).
- There is no administration UI: options are changed in the options file followed by a restart; participants cannot edit or cancel a registration (out of scope).
- The shipped options file (`backend/src/main/resources/conference-options.json`) and the consent wording are placeholders; production must mount its own file (`OPTIONS_FILE`), with the wording the product owner approves (D-07).
- Emails are not retried automatically after an SMTP failure (D-08); the log shows the registration id.

## Must be tested manually by a human

1. reCAPTCHA with the production keys: one real registration through the Google widget succeeds, and a submission without solving the challenge is refused (environments.md).
2. Email delivery through the production SMTP server to a real mailbox, for both the participant confirmation and the organizer notification with its JSON attachment; check that STARTTLS is used.
3. The production reverse proxy: HTTPS with the Let's Encrypt certificate, HTTP → HTTPS redirect, HSTS, `/` served from the frontend and `/api` forwarded to the backend with `X-Forwarded-For $remote_addr` and `X-Forwarded-Proto $scheme`.
4. Organizer export over HTTPS in production (200 with the workbook), and refusal over plain HTTP (403); open the workbook in Excel and LibreOffice and check Slovenian characters.
5. That the backend port is not reachable from outside the proxy (F-10).
6. The production options file and consent wording (D-07) as shown in the form and in the confirmation email.
7. A vulnerability scan of the container images before deployment (F-08).
8. Backups of the `pgdata` and `jsondata` volumes and a restore test (NFR-02 in production).

## Decisions pending review

| Decision | Question | Implemented behaviour |
|---|---|---|
| D-06 | OQ-01: are some options only for external participants? | All active options are available to both types |
| D-07 | OQ-02: which consents, with what wording? | One required consent `privacy` with placeholder wording, configurable in the options file |
| D-08 | OQ-03: storage succeeds but an email fails? | Registration stays accepted; failure logged by id; no retry |
| D-09 | OQ-04: how many options per category? | No limit; each option at most once |
| D-10 | OQ-05: second registration with the same email? | Refused with 409 (case-insensitive) |
| D-11 | OQ-06: how long are data kept? | No automatic deletion; manual deletion after the conference, at most 12 months |
