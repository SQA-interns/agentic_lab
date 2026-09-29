# Release Notes — Conference Registration System 0.0.1

Run `opus5.5-testrun-latest-2026-09-29`, branch `TestRun_Latest`.
How to build and run: `README.md`.

## Delivered

- **Registration forms** for external participants and students
  (US-001, US-002): per-type fixed fields, trimming of leading/trailing
  whitespace (including no-break spaces), full Unicode/Slovenian support,
  email format check, one mandatory, non-preselected personal-data
  consent (D-2), selection of any number of active options (D-4).
- **Configurable options** (US-003): workshops, events, meals and other
  activities from `config/conference-options.json`, picked up at runtime
  without restart; options are never deleted, only deactivated, so past
  registrations keep their selections.
- **Confirmation** (US-004) shown only after a `201` from the backend;
  field-level error messages otherwise.
- **Reliable storage** (US-005): PostgreSQL (Flyway schema with per-type
  CHECK constraints) plus a raw JSON backup per registration, written
  atomically in the same transaction; organizer restore endpoint
  (`POST /api/organizer/backups/restore`) re-imports missing
  registrations; data survives container recreation.
- **Emails** (US-006, US-007): plain-text UTF-8 participant confirmation;
  organizer notification with all submitted data and the raw JSON
  attached (byte-identical to the backup). An email failure never undoes
  a stored registration (D-3).
- **Excel export** (US-008): `GET /api/organizer/registrations/export`,
  one row per registration, 15 fixed columns, string cells only.
- **Security**: backend validation of every field (control/format
  characters rejected, length limits, strict JSON, 16 KiB body limit),
  Google reCAPTCHA v2 on frontend and backend with deterministic test
  mode, per-IP rate limiting (registrations 30/min, organizer 10/min),
  HTTP Basic organizer access over HTTPS only (BCrypt, ≥16-character
  password, stateless), security headers on API and HTML, RFC 9457 error
  bodies without internals, no personal data in logs, secrets only from
  the environment.
- **Operations**: Actuator health/readiness, Docker Compose stack
  (PostgreSQL 16, Mailpit, backend as non-root, frontend nginx with CSP).

## Technology versions

Java 21, Spring Boot **3.5.16** (scaffold pinned 3.4.4 — upgraded in
Verification with operator approval, D-8) with Tomcat 10.1.60 and
log4j 2.26.1 overrides, PostgreSQL 16, Apache POI 5.5.1, React 19,
TypeScript 5, Vite 6. Full list of added dependencies:
`docs/implementation-notes.md`.

## Quality evidence (self-verified — not an independent review)

- 275 automated tests passing: 90 frozen acceptance (HTTP API) + 9 frozen
  E2E (Playwright) + 112 backend unit + 16 backend integration + 9
  ArchUnit + 39 frontend component/unit.
- Acceptance-test freeze held (18/18 hashes).
- Coverage: backend 97.0 % instructions / 88.0 % branches (combined),
  frontend 97.9 % statements. Mutation score: backend 84.7 % (PIT),
  frontend 71.3 % (Stryker).
- No unresolved Critical/High finding after two fix loops; details and
  the agent's severity judgements in `docs/verification-report.md`.

## Known limitations and open items

1. **Dependency CVEs without a published OSS fix for Spring Boot 3.x**
   (Spring Framework 6.2.19, Spring Security 6.5.11): 15 CVEs rated
   Critical/High by NVD are unreachable in this application (the affected
   features — WebFlux, RSocket, SSE, XSLT views, user-supplied SpEL,
   `DataBinder` property paths, embedded LDAP, DPoP, WebAuthn — are not
   used) and were reclassified Medium. Re-check when Spring publishes
   6.2.20+/6.5.12+ for the 3.x line, or plan a move to Spring Boot 4
   (outside the frozen stack).
2. **HTTP Basic for organizers** (Semgrep High → residual Medium, D-9,
   pending human review): acceptable only behind HTTPS; the backend now
   enforces it. Consider mTLS or an identity provider if the scope changes.
3. **reCAPTCHA production mode** was verified against a local stub only;
   real site/secret keys must be configured and tested before go-live
   (`RECAPTCHA_SITE_KEY`, `RECAPTCHA_SECRET_KEY`, `RECAPTCHA_TEST_MODE=false`).
4. **External SMTP delivery** was not tested (Mailpit only); configure
   `SPRING_MAIL_*` for production.
5. **External nginx** (TLS, Let's Encrypt, HSTS, `/api` routing, setting
   `X-Forwarded-Proto`) is outside this delivery; the backend port must
   not be publicly reachable.
6. **Container base images** were not scanned for OS-package
   vulnerabilities.
7. **Dev-tooling advisories** (npm audit, 5 moderate in Vitest/Stryker
   dependencies; none in the production bundle).
8. **Pending human review of agent defaults**: D-1 (all options open to
   both types), D-2 (one consent), D-3 (email failure keeps the
   registration), D-4 (no selection limits), D-5 (duplicate emails
   allowed), D-7 (Docker API version pinned for Testcontainers via
   `docker-java.properties`), D-9.
9. **Windows checkouts** into very deep directories fail with
   `Filename too long`; clone to a short path or enable
   `git config core.longpaths true`.
10. A single organizer account; no organizer UI (by scope).
