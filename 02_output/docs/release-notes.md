# Release notes

> Written in: phase 7 · Procedure: `skills/release` · Agent: writes

Release 0.1.0 of the conference registration (backend and frontend), 2026-10-09.

## Delivered

| Story | Delivered |
|---|---|
| US-001 External participant registration | external form with validation in the page and on the server; registration through `POST /api/registrations` |
| US-002 Student registration | student form with study institution, programme and student ID |
| US-003 Configurable conference options | options, category limits and consents from a JSON file (`OPTIONS_FILE`), per registration type; restart applies changes |
| US-004 Registration confirmation | confirmation with the registration ID only after storage; field errors next to the fields |
| US-005 Reliable registration storage | PostgreSQL row and raw JSON copy written in one transaction; neither or both |
| US-006 Participant email confirmation | plain-text confirmation with type and selected options |
| US-007 Organizer notification | email to all organizer addresses with the submitted data and the JSON copy attached |
| US-008 Registration export | Excel workbook for the organizer at `/api/export` (HTTP Basic, HTTPS only) |

## Known limitations

| Limitation | Source |
|---|---|
| One registration per email address across both types; a submitted registration cannot be edited or cancelled | D-13; out of scope |
| A failed confirmation or notification email is logged but not retried | D-11 |
| Registrations and JSON copies are never deleted automatically | D-14 |
| Interface and email texts are English only | D-18 |
| Option changes need a restart; an invalid options file stops the start | AR-04, D-16 |
| Rate limits are kept in memory of one backend instance | specification section 14 |
| Organizer access is HTTP Basic without logout; the browser keeps the credentials until it is closed | D-20 |
| Text fields have maximum lengths and reject line breaks and control characters | D-15, D-17 |

## Decisions pending review

Already applied during the run; review them before production use.

| Decision | Choice applied |
|---|---|
| D-01 | build and tests ran on the host's Oracle JDK 21.0.11; images use the pinned Temurin 21.0.10 |
| D-02 | cloc image tag `2.10` (reports 1.98) kept |
| D-05 | CVE-2025-15104 on `hibernate-validator` suppressed as a false positive |
| D-06 | `qs` advisories in the mutation-testing tool accepted as Medium (not shipped) |
| D-09 | options can be limited to one registration type (default both) |
| D-10 | one mandatory consent to data processing, wording from configuration |
| D-11 | an email failure does not undo an accepted registration |
| D-12 | maximum selections per category from configuration, default 1 |
| D-13 | a second registration with the same email is rejected |
| D-14 | no automatic deletion; the operator sets the retention period |
| D-15 | maximum field lengths: email 254, names 100, institutions and programme 200, student ID 50 |
| D-16 | an invalid options file prevents startup |
| D-17 | control characters in text fields are rejected |
| D-18 | English texts |

## Before production

Checks that the local run could not do (real external services) and accepted findings whose fix lies outside the run. They are not part of the run's results.

| Check | Why |
|---|---|
| One real registration with the production reCAPTCHA v2 keys (`RECAPTCHA_TEST_MODE=false`) | local runs used test mode and a mocked verification endpoint |
| Delivery of both emails to a real mailbox through the production SMTP server with STARTTLS | local runs used Mailpit |
| HTTPS through the external nginx with Let's Encrypt: redirects, `/` to the frontend, `/api` to the backend with `X-Forwarded-Proto`, organizer export over HTTPS | local runs used plain HTTP on 127.0.0.1 |
| Upgrade `@stryker-mutator/core` once its `qs` dependency is fixed | accepted finding F-05 (development tooling only) |
