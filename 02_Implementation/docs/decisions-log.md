# Decisions Log

Every escalation raised during this run, per `CONSTITUTION.md` §3.
Each entry has `{timestamp, category, trigger, proposedAlternatives,
humanResponse, resolution}`. Entries are mirrored in
`03_Run-Statistics/run-log.json` → `escalations`.

The run was started with an instruction to work autonomously and to
stop only for tool/version/secret substitutions. Requirement
ambiguities are therefore resolved with the most conservative
alternative and marked `agent-default, pending human review`, as §3
permits when no human is available synchronously.

---

## D-1 — Option availability for students

- **timestamp:** 2026-09-28T23:28:57Z
- **category:** requirement-ambiguity
- **trigger:** US-002 says a student registers "so that I can attend
  the conference and activities available to students". No input
  defines per-registration-type eligibility for options; FORM_SCHEMA
  gives options only an identifier, display name and active status.
  One implementation would restrict some options to one type, another
  would offer every active option to both — a visible difference.
- **proposedAlternatives:**
  1. Every active option is available to both registration types
     (no extra attribute invented). *(conservative — chosen)*
  2. Add a per-option "available to" attribute (external / student /
     both) and filter by it.
- **humanResponse:** none (autonomous run; no synchronous human)
- **resolution:** agent-default, pending human review — alternative 1.

## D-2 — Which mandatory consents exist

- **timestamp:** 2026-09-28T23:28:57Z
- **category:** requirement-ambiguity
- **trigger:** FORM_SCHEMA "Consent" requires support for mandatory
  consent fields "where required" but does not name any.
- **proposedAlternatives:**
  1. One mandatory, non-preselected consent on both forms: consent to
     processing of the participant's personal data for the purpose of
     conference registration. *(conservative — chosen; it is the
     minimum that makes "mandatory consent" observable and matches
     storing personal data)*
  2. No consent at all (the rule would then be untestable).
  3. Several consents (e.g. photography, publication of attendee
     list) — invents requirements.
- **humanResponse:** none (autonomous run; no synchronous human)
- **resolution:** agent-default, pending human review — alternative 1.

## D-3 — Email/notification failure after a stored registration

- **timestamp:** 2026-09-28T23:28:57Z
- **category:** requirement-ambiguity
- **trigger:** US-004/US-005 require confirmation after successful
  storage; US-006/US-007 require emails. No input says what happens
  when the registration is stored but an email cannot be sent.
- **proposedAlternatives:**
  1. Registration stays accepted; the email failure is recorded for the
     operator and the participant still sees the confirmation.
     *(conservative for US-005 — chosen; no stored registration is lost
     or reported as failed)*
  2. Roll back the registration and report failure.
- **humanResponse:** none (autonomous run; no synchronous human)
- **resolution:** agent-default, pending human review — alternative 1.

## D-4 — Number of option selections

- **timestamp:** 2026-09-28T23:28:57Z
- **category:** requirement-ambiguity
- **trigger:** No input limits or requires option selections (e.g. at
  most one meal, at least one workshop).
- **proposedAlternatives:**
  1. Any number of active options, including none. *(chosen — invents
     no rule)*
  2. Per-set min/max limits.
- **humanResponse:** none (autonomous run; no synchronous human)
- **resolution:** agent-default, pending human review — alternative 1.

## D-5 — Duplicate registrations with the same email

- **timestamp:** 2026-09-28T23:28:57Z
- **category:** requirement-ambiguity
- **trigger:** No input says whether the same email may register more
  than once. Rejecting vs. accepting duplicates is user-visible.
- **proposedAlternatives:**
  1. Accept every valid submission as its own registration; the
     organizer sees duplicates in the export. *(chosen — invents no
     rule and never loses a registration)*
  2. Reject a second registration with the same email.
- **humanResponse:** none (autonomous run; no synchronous human)
- **resolution:** agent-default, pending human review — alternative 1.

## D-6 — Unresolved human inputs (HUMAN_INPUTS_MANIFEST.md)

- **timestamp:** 2026-09-28T23:29:46Z
- **category:** missing-human-input
- **trigger:** At the start of Phase 2 every row of
  `03_Process/HUMAN_INPUTS_MANIFEST.md` was still unticked, and the
  environment held no reCAPTCHA keys, SMTP credentials, organizer
  export credentials or NVD API key. CONSTITUTION §3 (missing required
  secret) and the operator's instruction to stop rather than substitute
  a secret both apply.
- **proposedAlternatives:**
  1. reCAPTCHA: deterministic test mode only; production-mode
     verification exercised against a mocked siteverify endpoint in
     Verification; production mode refuses to start with a blank key.
     / Human provides real keys.
  2. SMTP: local catcher only (Mailpit in compose, Mailpit container in
     tests); external delivery an open item in RELEASE_NOTES. / Human
     provides SMTP credentials.
  3. Organizer export credentials: generated locally by the agent into
     a git-ignored env file only. / Human provides them.
  4. NVD API key: not provided, substitute scanner if needed; container
     images public only. / Human provides key.
- **humanResponse:** (2026-09-28T23:30:58Z, via synchronous question in
  the session) reCAPTCHA → test-mode only; SMTP → local catcher only;
  organizer credentials → generated locally; NVD API key → provided by
  the human in the session (the key value is not recorded here or in
  any committed file; it is passed to Dependency-Check only as an
  environment variable at run time). Container images: public
  registries only (every image in `docker-compose.yml` is public).
- **resolution:** resolved by human — alternatives as answered above.

## D-7 — Testcontainers cannot talk to Docker Engine 29 as shipped

- **timestamp:** 2026-09-28T23:41:28Z
- **category:** tool-incompatibility (CONSTITUTION §3: primary tool named
  in TECH_STACK cannot be used as specified)
- **trigger:** First acceptance red run: every test errored in class
  initialisation with `BadRequestException: client version 1.32 is too
  old. Minimum supported API version is 1.40`. Testcontainers 1.20.6
  (the version managed by the pinned `spring-boot-starter-parent`
  3.4.4) defaults its docker-java client to Docker API 1.32; this
  machine runs Docker Engine 29.8.0 (API 1.56, minimum 1.40).
  Testcontainers is the TECH_STACK-named tool for PostgreSQL
  integration tests.
- **proposedAlternatives:**
  1. Configuration only: `backend/src/test/resources/docker-java.properties`
     with `api.version=1.44`, so the managed Testcontainers version talks
     a supported API. No tool, version or dependency changes.
     *(chosen — most conservative; nothing substituted)*
  2. Override the Testcontainers version (≥ 1.21.4) in the POM — a
     dependency-version change; would need human approval.
  3. Upgrade Spring Boot — a pinned-version change; would need human
     approval.
- **humanResponse:** none requested. The operator asked to stop for
  tool/version/secret *substitutions*; alternative 1 substitutes
  nothing, so it was applied and is put on the record here.
- **resolution:** agent-default, pending human review — alternative 1.
  Verified: with the file present, the containers start and every
  acceptance test fails for a behavioural reason.

## D-8 — Critical/High dependency CVEs in the pinned Spring Boot 3.4.4 stack

- **timestamp:** 2026-09-29T00:26:27Z
- **category:** pinned-version-change (CONSTITUTION §3; operator instruction
  to stop before substituting a version)
- **trigger:** Verification, OWASP Dependency-Check 12.1.0 (NVD data
  updated 2026-09-28T22:17Z): 35 Critical / 77 High / 77 Medium / 10 Low
  findings, all in libraries managed by the scaffold's pinned
  `spring-boot-starter-parent` 3.4.4 (Spring Framework 6.2.5, Spring
  Security 6.4.4, Tomcat 10.1.39, Jackson 2.18.3, PostgreSQL JDBC
  42.7.5, Angus mail/activation 2.0.x) or pulled in by POI (log4j-api
  2.24.3). DoD §3 forbids finalization with unresolved Critical/High.
  A scratch-copy trial (product untouched) with Boot 3.5.16, the newest
  3.x release, still showed 22 Critical / 27 High: Tomcat fixes exist
  (10.1.58+, latest 10.1.60), but several Spring Framework/Security
  CVEs are fixed only in 6.2.20+/7.x, which are not published on Maven
  Central for the 3.x line; Spring Boot 4.x would violate TECH_STACK
  "Spring Boot 3.x".
- **proposedAlternatives:**
  1. Upgrade the parent to 3.5.16 and override managed versions where a
     fixed release exists (e.g. Tomcat 10.1.60, log4j-api 2.26.x), re-run
     all tests, then triage the remainder per CVE by reachability —
     unreachable → Medium with written justification (SEVERITY_TAXONOMY
     example), reachable → open Critical/High escalation.
  2. Keep 3.4.4 and triage only.
  3. Move to Spring Boot 4.x (outside TECH_STACK).
- **humanResponse:** answered by 2026-09-29T06:16:32Z (first timestamp observed after the answer; synchronous question in the
  session): alternative 1, "Upgrade within 3.x + triage".
- **resolution:** resolved by human — alternative 1; execution recorded
  as fix loops in `docs/verification-report.md` and `run-log.json`.

## D-9 — Semgrep High: HTTP Basic authentication for organizer endpoints

- **timestamp:** 2026-09-29T06:46:58Z
- **category:** security-finding-vs-frozen-requirement (CONSTITUTION §3;
  finding cannot be removed without changing frozen artefacts)
- **trigger:** Verification, Semgrep rule
  `yaml.openapi.security.use-of-basic-authentication` (severity ERROR,
  impact HIGH → **High** per SEVERITY_TAXONOMY) on
  `docs/contracts/openapi.yaml` (`organizerBasic`). HTTP Basic is the
  mechanism chosen in the specification (§5.2/§8.4) and is exercised by the
  frozen acceptance tests (`ExportAcceptanceTest`,
  `DurableStorageAcceptanceTest`, `RateLimitAcceptanceTest` send
  `Authorization: Basic` and expect `WWW-Authenticate: Basic`). The
  alternatives the rule suggests (OAuth2 / OpenID Connect) are
  identity-provider integration, which BUSINESS_RULES "Scope boundaries" and
  SECURITY_REQUIREMENTS exclude; mTLS lives at the external reverse proxy.
- **proposedAlternatives:**
  1. Keep HTTP Basic; harden the transport so credentials are never accepted
     over plaintext (fix loop 2: `OrganizerTransportFilter`, 403
     `HTTPS_REQUIRED`, default on), on top of the existing controls
     (≥16-character password, BCrypt in memory, 10 req/min/IP organizer rate
     limit, stateless — no session/cookie, no CORS); record the residual as
     **Medium** ("control present but weaker than best practice") with this
     justification. *(chosen — conservative: no frozen test or contract
     scheme changes)*
  2. Replace Basic with a token scheme (password exchanged once for a
     short-lived signed token) — requires a test-defect request to regenerate
     frozen acceptance tests; still password-based.
  3. Require mTLS for `/api/organizer/**` at the external reverse proxy —
     deployment-level, cannot be verified in this workspace.
- **humanResponse:** none requested synchronously — the operator's stop
  instruction covers tool/version/secret substitutions, and no substitution
  is involved.
- **resolution:** agent-default, pending human review — alternative 1.
