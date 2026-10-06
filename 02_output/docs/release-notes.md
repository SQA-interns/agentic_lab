# Release notes

> Written in: phase 7 · Procedure: `skills/release` · Agent: writes

## Delivered

| Story | Delivered |
|---|---|
| US-001 External participant registration | form with the four fixed fields, active options, mandatory consent, reCAPTCHA; server-side validation, duplicate-email refusal |
| US-002 Student registration | form with the six fixed fields; options limited to those available to students (D-11) |
| US-003 Configurable conference options | JSON configuration file validated at startup; categories, active flag, availability per type, category maxima (D-14) |
| US-004 Registration confirmation | confirmation shown only after the backend accepted and stored the registration; field and form errors otherwise |
| US-005 Reliable registration storage | PostgreSQL row and raw JSON copy written in one transaction before the response; survives container recreation |
| US-006 Participant email confirmation | plain-text UTF-8 confirmation with type and selected options |
| US-007 Organizer notification | email to every organizer address with the stored JSON copy attached byte for byte |
| US-008 Registration export | Excel workbook for organizers (HTTP Basic, HTTPS only behind the proxy); text cells only |

## Known limitations

- No editing, cancelling or deleting of registrations, and no automatic retention clean-up (out of scope, D-16).
- A failed email is logged with the registration id only and not resent (D-13).
- One registration per email address across both types (D-15).
- One mandatory consent with placeholder wording until the product owner supplies the text (D-12).
- English user interface; texts are in one module per component (D-18).
- Rate limits are in memory and per backend instance; a second instance would need a shared limiter.
- Organizer access is HTTP Basic for the single export (D-19).

## Decisions pending review

Already applied during the run; review them before production use.

| Decision | Applied choice |
|---|---|
| D-03, D-04, D-05, D-06 | host JDK 21.0.12, Docker 29.3.1, Compose 5.1.1 and the cloc image (reports 1.98) used; stack pins stay authoritative |
| D-09 | CVE-2025-15104 on hibernate-validator suppressed as a false positive |
| D-10 | npm audit Medium in `qs` (mutation tooling only) kept |
| D-11 | options carry `availableTo`; default both types |
| D-12 | one mandatory data-processing consent, wording from configuration |
| D-13 | email failure keeps the registration; logged, not resent |
| D-14 | options optional; optional per-category maximum (workshops: 2 in the example) |
| D-15 | duplicate email refused, case-insensitive, across types |
| D-16 | registrations kept until organizers delete them; no automatic deletion |
| D-17 | values with control characters rejected |
| D-18 | English UI |
| D-20 | Semgrep Medium on `.npmrc` minimum release age accepted (exact pins, lock file) |
| D-22 | client validation on submit only |
| D-23 | backend mutation testing scoped to rule classes and unit tests |

## Before production

Checks that the local run could not do (real external services) and accepted findings whose fix lies outside the run. They are not part of the run's results.

| Check | Why |
|---|---|
| One real registration with the production reCAPTCHA keys | only the test mode and a mocked verification endpoint were exercised |
| Delivery of both emails to a real mailbox through the production SMTP server | only Mailpit was used |
| HTTPS with Let's Encrypt on the external nginx, HTTP→HTTPS redirect, `/` to the frontend and `/api` to the backend, `X-Forwarded-Proto` set | no TLS or reverse proxy in the local stack; organizer export depends on it (SR-06) |
| Set the retention period and the consent wording (D-12, D-16) | product-owner input |
| F-03: `min-release-age` in `.npmrc` needs npm ≥ 11.10 | stack change outside the run |
| F-04: `qs` advisory via Stryker; update when Stryker ships a fixed dependency | upstream fix |
