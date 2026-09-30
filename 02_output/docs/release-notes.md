# Release notes

> Written in: phase 7 · Agent: writes

Lab Conference registration 1.0.0, run `conference-single-sdd-001`, branch `run/single-sdd/conference-001`. Synthetic local experiment; not approved for production or real personal data.

## Delivered

- **US-001 / US-002:** separate external and student forms (English UI), with Unicode/NBSP-safe trimming, authoritative backend validation, field errors linked to their inputs, keyboard operation and visible focus.
- **US-003:** workshops, events, meals and other activities come from an external JSON catalog loaded at startup. Changing the file and restarting the backend is enough; an invalid catalog stops the startup.
- **US-004:** confirmation page with the registration ID, shown only after the backend accepted. Retries reuse the client request ID; a replay returns the same registration; a changed body gives 409.
- **US-005:** a registration is accepted only after an atomic durable JSON file and the PostgreSQL row (Flyway schema) both exist. Crash leftovers are moved to `orphaned/`, and restarts keep all data.
- **US-006 / US-007:** participant confirmation mail and organizer mail with a byte-identical `registration-<id>.json` attachment, through a durable outbox with unbounded capped retries (at-least-once, stable Message-ID).
- **US-008:** Excel export of all accepted registrations from both forms, behind HTTP Basic (bcrypt hash). Cells are formula-safe.
- **Security:**
  - rate limits on registration and export;
  - Origin allow-list and body-size limit;
  - security headers;
  - non-root containers;
  - production guards: the stub captcha is refused, SMTP TLS is required, and the organizer password hash must be slow.
- **Local stack:** Docker Compose (PostgreSQL 16.14, Mailpit v1.24.1, backend, nginx frontend), helper tools (`tools/hash-password.sh`, `tools/runtime-demo.sh`, `tools/manifests.sh`), component READMEs, contracts (`docs/02_contracts/`).
- **Verification:** 339 automated tests pass: 109 frozen acceptance, 19 frozen e2e, 5 integration, 165 backend unit, 41 frontend unit. Coverage and mutation results are in `docs/06_verification-report.md`.

## Known limitations

- **Unpatched framework CVEs:** Spring Framework 6.2.19 / Spring Security 6.5.11 (Boot 3.5.16 BOM) carry Critical/High CVEs, and no OSS fix exists for these lines. They were accepted as not reachable (D-11, enforced by ArchUnit), and Tomcat is overridden to 10.1.60. **Before any production use, move to a patched Spring line** (for example Boot 4.1.x) and re-scan. Medium CVEs are accepted with reasons (F-07).
- **Host tools differ from the pins:** Docker Engine/Compose on the verification host were 29.3.1/v5.1.1 instead of the pinned 28.1.1/2.35.1 (D-03).
- **No production evidence:** TLS termination, real reCAPTCHA, a real SMTP relay, backups and production proxy trust were **not** verified in this run (local substitutes only).
- **Single instance only:** the rate limiter is in memory per instance. Behind several backend instances, limits apply per instance and the reconciliation assumes one instance.
- **Duplicate mail possible:** delivery is at-least-once. A crash between SMTP acceptance and the outbox update can send a mail twice (same Message-ID). "Sent" means only that the SMTP server accepted the message.
- **No real consent text or retention policy:** consent wording and legal basis (OQ-02) and the retention/deletion policy (OQ-03) are not supplied. Both block real deployment. Synthetic data is deleted with `docker compose down -v`, which removes the database, the JSON files and the Mailpit copies.
- **No admin features:** there is no admin UI, no editing of registrations, no participant accounts and no deletion API (out of scope).
- **Scanner gaps:** semgrep could not parse `mvnw` or the Unicode examples in `openapi.yaml` (F-06); npm 10.9.9 lacks the `min-release-age` setting (F-05).
- **Old repository history:** commits made before this run contain 11 secret-scanner hits from another project (F-11). This run did not rewrite history.

## Must be tested manually by a human

1. **TLS / proxy (SB-04, AR-08):**
   - external nginx with Let's Encrypt certificates;
   - HTTP→HTTPS redirect;
   - `/` to the frontend and `/api/` to the backend;
   - `TRUSTED_PROXIES` set to the proxy address only;
   - `X-Forwarded-For` spoofing from the client ignored;
   - `ALLOWED_ORIGINS` = the public HTTPS origin.
2. **Real reCAPTCHA (SR-01):** with `APP_PROFILE=production` and `CAPTCHA_MODE=recaptcha`, check the real site and secret keys and registered domains. A solved captcha must be accepted, and missing, expired or forged tokens rejected. Also check that startup fails with `CAPTCHA_MODE=stub` or missing keys.
3. **Real SMTP (BR-07, SR-04):**
   - authenticated TLS relay (`SMTP_TLS_ENABLED=true`);
   - actual delivery and rendering of the participant and organizer mails in real clients, including UTF-8 names;
   - the JSON attachment opens;
   - SPF/DKIM of `MAIL_FROM`;
   - behaviour during a relay outage.
4. **Organizer export in a browser:** the native Basic dialog over HTTPS, the downloaded `registrations.xlsx` opened in Excel/LibreOffice, and the formula-like values shown as text.
5. **Accessibility (NFR-04):** a screen-reader pass (NVDA/VoiceOver) over both forms, error summary and confirmation; 200% zoom and a narrow viewport; the colour contrast of error text and focus outlines.
6. **Production data (PostgreSQL / volumes):** backup and restore of the database **together with** the JSON volume; access controls on both, and on any backups and exports (SR-02); disk-full behaviour.
7. **Legal and privacy (SB-13, SB-14, SR-05):** real consent wording and legal basis, retention/deletion procedure covering the DB, JSON, mail and Excel copies, and a privacy notice. These are prerequisites for real data.
8. **Load and abuse:** rate-limit values under realistic traffic behind the proxy.

## Decisions pending review

Non-blocking decisions taken by the agent (the conservative option where users would notice). Blocking decisions D-01…D-08, D-11, D-22 and D-24 were approved by the human.

| Decision | Choice |
|---|---|
| D-09 | Official images of the pinned platforms, pinned by digest; nginx runs as non-root |
| D-10 | Compose project `agenticlab`, host ports 18080/18025 (another stack on the host was left untouched) |
| D-12 | `.gitignore`, `.gitattributes` and the ignored `.env` live in `02_output/` |
| D-13 | google-java-format 1.25.2 pinned for Spotless |
| D-14 | Added devDependency @types/node 22.20.4 |
| D-15 | `unzip` installed in the backend build stage only |
| D-16 | Licence inventory accepted for private lab use (permissive plus LGPL/EPL/GPL-CPE dual-licensed libraries); no repository licence added |
| D-17 | A request-ID reuse with different content → 409 Conflict |
| D-18 | "Raw JSON" = canonical registration document (no captcha token), byte-identical to the attachment |
| D-19 | Invalid catalog → the backend refuses to start |
| D-20 | Contract validators swagger-parser 13.1.0, ajv 8.20.0, ajv-formats 3.0.1, openapi-types 12.1.3 |
| D-21 | The bootstrap skeleton was completed with configuration only, so the phase 3 tests failed for behavioural reasons |
| D-23 | `PasswordHashCli` + `tools/hash-password.sh` for creating the organizer hash |
