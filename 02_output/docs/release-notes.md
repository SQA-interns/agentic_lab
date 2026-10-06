# Release notes

> Written in: phase 7 · Procedure: `skills/release` · Agent: writes

## Delivered

| Story | Delivered | Evidence |
|---|---|---|
| US-001 External participant registration | form with the four external fields, active options, consents and anti-automation check; backend validation of BR-01 to BR-05 | AC-001-01 to AC-001-15 pass |
| US-002 Student registration | form with the six student fields; options offered to students only | AC-002-01 to AC-002-10 pass |
| US-003 Configurable conference options | options and consents in `config/conference-config.json`, read at startup, validated against its schema | AC-003-01 to AC-003-05 pass |
| US-004 Registration confirmation | confirmation with name, email, options and registration id, shown only after acceptance | AC-004-01 to AC-004-03 pass |
| US-005 Reliable registration storage | PostgreSQL row and raw JSON copy, both or neither; data kept across container recreation | AC-005-01 to AC-005-05 pass; runtime demonstration |
| US-006 Participant email confirmation | plain-text UTF-8 confirmation email after storage | AC-006-01 to AC-006-04 pass |
| US-007 Organizer notification | one email to all organizer addresses with the JSON copy attached | AC-007-01 to AC-007-04 pass |
| US-008 Registration export | Excel workbook for organizers (HTTP Basic over HTTPS), refused without organizer access | AC-008-01 to AC-008-05 pass |

## Known limitations

- A registration cannot be edited or cancelled; a second registration with the same email is refused and the participant is asked to contact the organizers (D-09).
- No automatic deletion of registrations or JSON copies; retention is a manual organizer task (D-10).
- A failed email is logged with the registration id and not retried; the registration stays accepted (D-07).
- The consent wording in the local configuration is placeholder text (D-06).
- Option changes take effect only after a backend restart (AR-04).
- Rate limits are counted in memory per backend instance and reset on restart.
- The organizer export uses HTTP Basic authentication; there is no organizer user interface (D-11).
- Test tooling (Vitest, jscpd) carries five Medium dev-only advisories (F-03).

## Decisions pending review

Already applied during the run; review them before production use.

| Decision | Applied choice |
|---|---|
| D-05 | each option may be limited to one registration type in the configuration; default both |
| D-06 | consents come from the configuration; one mandatory data-processing consent with placeholder wording |
| D-07 | an email failure keeps the registration accepted; logged, no retry |
| D-08 | any number of distinct options per category, none required |
| D-09 | a second registration with the same email (any type, case-insensitive) is refused |
| D-10 | no deletion function; retention to be set by the product owner |
| D-13 | SpotBugs `EI_EXPOSE_REP2` excluded on constructors (dependency injection) |
| D-15 | the backend image runs a jar built on the host (no JDK image in `stack.md`) |
| D-16 | compose project name `registration-tanej04` |
| D-18 | npm override pins the test runner's `tinypool` to 2.1.2 |

## Before production

Checks that the local run could not do (real external services) and accepted findings whose fix lies outside the run. They are not part of the run's results.

| Check | Why |
|---|---|
| One real registration with the production reCAPTCHA v2 keys | the live verification ran only against a mocked endpoint (`stack.md`, "External services") |
| Delivery of both emails to a real mailbox through the production SMTP server with TLS | locally all mail went to Mailpit |
| HTTPS, redirects and `/api` routing through the external nginx reverse proxy, including organizer access over HTTPS only | no TLS proxy exists locally |
| Upgrade `vitest`, `@vitest/coverage-v8` and the `jscpd` dependency chain to versions without the Medium advisories | F-03; needs major versions outside `project/stack.md` |
