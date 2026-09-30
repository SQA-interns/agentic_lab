# Decisions log

> Written in: every phase · Format: `general/decision-record.md` · Agent: appends only

## D-01: NVD_API_KEY missing from .env
- Timestamp: 2026-09-29T23:20:00Z
- Phase: 0
- Type: blocking
- Trigger: preflight step 4/6. `project/01_setup/secrets.env.example` marks `NVD_API_KEY` as "provided" for local phase 0 and phase 6 scans; the key is absent from `.env`. Without it `org.owasp:dependency-check-maven` (the only backend `dependency-scan` tool in `tech-stack.md`) cannot run, so preflight step 6 cannot complete for the backend.
- Options: 1. (default) Human adds `NVD_API_KEY=<value>` to `.env` at the repository root; the agent re-runs the backend dependency scan. 2. Human approves running Dependency-Check without a key (very slow NVD download, may be rate-limited). 3. Human approves skipping the backend dependency scan in phase 0 and running it only in phase 6.
- Human response: none
- Resolution: pending

## D-02: Location of the ES-03 repository files
- Timestamp: 2026-09-29T23:21:00Z
- Phase: 0
- Type: non-blocking
- Trigger: ES-03 and `phases.md` phase 0 require `.gitattributes` and `.gitignore`; `AGENTS.md` (highest precedence) allows writing only files in section 3 of `README.md`, which covers `02_output/` but not the repository root. `.env` lives at the repository root.
- Options: 1. (chosen) Put `.gitattributes` and `.gitignore` in `02_output/` (they govern all code the agent writes) and add `/.env` to the untracked local `.git/info/exclude` so the root `.env` cannot be committed from this checkout. 2. Write root-level files, violating `AGENTS.md`.
- Human response: none
- Resolution: option 1, pending review (a human may want a root `.gitignore` containing `.env` in the template so other clones are protected too)

## D-03: cloc image tag 2.10 contains cloc 1.98
- Timestamp: 2026-09-29T23:22:00Z
- Phase: 0
- Type: non-blocking
- Trigger: `tech-stack.md` lists `aldanial/cloc:2.10` (code-metrics). The tag resolves and runs, but `cloc --version` inside it prints 1.98 (image created 2023-08-19).
- Options: 1. (chosen) Use the listed image tag unchanged; it is a measurement tool with no security or build role. 2. Ask for a different tag (a tech-stack change, blocking).
- Human response: none
- Resolution: option 1, pending review

## D-04: Follow-up to D-01 (NVD_API_KEY was present)
- Timestamp: 2026-09-29T23:21:04Z
- Phase: 0
- Type: blocking
- Trigger: follow-up to D-01. The human reported that `NVD_API_KEY` is on line 25 of `.env`. Re-check confirmed it is set; the preflight secret check had skipped the last line because `.env` has no trailing newline (agent defect in the check, not a missing secret).
- Options: 1. (chosen) Use the key; run the backend dependency scan.
- Human response: "I have already added a NVD_API_KEY it's on line 25 in .env" (2026-09-29T23:21Z)
- Resolution: D-01 resolved by option 1 of D-01 (key present); no human action was needed

## D-05: Critical and High vulnerabilities in the listed backend dependencies
- Timestamp: 2026-09-29T23:24:18Z
- Phase: 0
- Type: blocking
- Trigger: preflight step 6. OWASP Dependency-Check 12.1.0 (NVD, 68 dependencies; `out/logs/phase0-dependency-check-report.json`) on the `tech-stack.md` set reports: spring-core/spring-web 6.2.19 (Boot 3.5.16-managed) 6 Critical + 6 High (CVE-2026-47884, -47890, -47891, -47892, -59313, -59283; -47885, -47886, -47888, -47889, -47893, -59282), fixed only in Spring Framework 6.2.20+/7.0.9+; spring-security-core/web 6.5.11 1 Critical + 2 High (CVE-2026-59270, -41707, -47841), fixed in 6.5.12+; postgresql 42.7.11 1 High (CVE-2026-54291, channelBinding downgrade), fixed in 42.7.12. Spring Framework 6.2.20+ and Spring Security 6.5.12+ are not published on Maven Central; Spring Boot 3.5.16 is the newest 3.5.x. The tomcat 10.1.60, log4j 2.26.1, commons-lang3 3.20.0 and POI 5.5.1 overrides scan clean. Only a human may change `tech-stack.md`.
- Options:
  1. (proposed default) Move the backend to Spring Boot 4.1.1 (Spring Framework 7.0.9, Spring Security 7.1.1, pgjdbc 42.7.13, Flyway 12.4.0, Hibernate 7.4.5, Testcontainers 2.0.5, all Boot-managed) with a `tomcat-embed-core` override to 11.0.26 (Boot's 11.0.24 has 4 Critical + 5 High, fixed in 11.0.25). Verified by the agent: this set scans with 0 Critical, 0 High, 2 Medium (`out/logs/phase0-dependency-check-boot4.1.1-tomcat11.0.26.json`). Needs the human to update `tech-stack.md` (versions; `spring-boot-starter-web` → `spring-boot-starter-webmvc`; add `spring-boot-starter-flyway`; Testcontainers 2 artifact names `testcontainers-junit-jupiter`, `testcontainers-postgresql`; drop the Tomcat 10.1 override). The agent then re-writes the phase 0 input manifest (still phase 0) and re-runs preflight.
  2. Stay on Spring Boot 3.5.16; human changes only `org.postgresql:postgresql` to 42.7.13 in `tech-stack.md`; human accepts the 7 Critical + 8 High Spring findings as Medium on written non-reachability evidence: servlet Spring MVC only (no WebFlux, RSocket, Jetty, WebSocket, SSE, XsltView, functional endpoints), no SpEL evaluation of user input and the SpEL compiler left off, JSON `@RequestBody` only (no `WebDataBinder` property-path binding of user input), no LDAP/UnboundID, DPoP or WebAuthn. These would be enforced by ArchUnit rules and re-checked in phase 6. The vulnerable jars still ship.
  3. Obtain the commercial Spring 6.2.20+/6.5.12+ builds (Broadcom Tanzu Spring repository); needs a subscription, a repository credential and a new secret.
- Human response: none
- Resolution: pending

## D-06: OSS Index analyzer returns 401
- Timestamp: 2026-09-29T23:24:18Z
- Phase: 0
- Type: non-blocking
- Trigger: Dependency-Check's Sonatype OSS Index analyzer (enabled by default) now requires credentials and fails with HTTP 401, failing the Maven build; the NVD analyzer ran completely. `secrets.env.example` lists only `NVD_API_KEY` for this tool.
- Options: 1. (chosen) Disable the OSS Index analyzer (`ossindexAnalyzerEnabled=false`); NVD is the data source the project provides a key for. 2. Add OSS Index credentials (a new secret; human change to `secrets.env.example`).
- Human response: none
- Resolution: option 1, pending review

## D-07: Follow-up to D-05 (human chose Spring Boot 4.1.1)
- Timestamp: 2026-09-29T23:26:53Z
- Phase: 0
- Type: blocking
- Trigger: follow-up to D-05. The human chose option 1. `01_input/01_project/03_technical/tech-stack.md` is still unchanged and the agent may not edit `01_input/` (AGENTS.md), so the human must apply the change. Agent verified: all Boot 4.1.1 coordinates resolve; ArchUnit 1.3.2, JaCoCo 0.8.12, PIT 1.30.0 + pitest-junit5-plugin 1.2.3 run with Boot 4.1.1 (JUnit Jupiter 6.0.3) in a scratch project.
- Options: 1. (default) Human replaces the backend entries in `tech-stack.md` with the block given in the conversation; agent re-writes the phase 0 input manifest and re-runs preflight checks 2, 5, 6.
- Human response: "lets move backend to 4.1.1" (2026-09-29T23:26:53Z)
- Resolution: pending (waiting for the `tech-stack.md` edit)

## D-08: Follow-up to D-05 and D-07 (tech-stack.md updated to Spring Boot 4.1.1)
- Timestamp: 2026-09-29T23:30:55Z
- Phase: 0
- Type: blocking
- Trigger: follow-up to D-05 and D-07. The human reported "done, tech-stack.md is updated". Agent verified the diff matches the proposed block, updated `backend/pom.xml`, rebuilt, and re-ran Dependency-Check: 67 dependencies, 0 Critical, 0 High, 2 Medium (angus-activation CVE-2025-7962, hibernate-validator CVE-2025-15104). Resolved versions: spring-web 7.0.9, spring-security-core 7.1.1, tomcat-embed-core 11.0.26, postgresql 42.7.13, flyway 12.4.0. The input manifest was re-written (still phase 0).
- Options: 1. (chosen) Continue with the updated stack.
- Human response: "done, tech-stack.md is updated" (2026-09-29T23:30:55Z)
- Resolution: D-05 resolved by option 1 (Spring Boot 4.1.1 + Tomcat 11.0.26); D-07 resolved

## D-09: OQ-01 options per registration type
- Timestamp: 2026-09-29T23:33:12Z
- Phase: 1
- Type: non-blocking
- Trigger: OQ-01 (`project/02_business/scope.md`) unanswered; affects AC-003-04.
- Options: 1. (chosen) Every active option is offered to both types; an option may be restricted to one type through an optional configuration field (default: both), so a later answer needs no code change (AR-04). 2. Hard-code availability per type. 3. Offer all options to both types with no restriction mechanism.
- Human response: none
- Resolution: option 1, pending review

## D-10: OQ-02 mandatory consents and wording
- Timestamp: 2026-09-29T23:33:12Z
- Phase: 1
- Type: non-blocking
- Trigger: OQ-02 unanswered; BR-05 and SB-14 need at least one consent to exist; affects AC-001-07, AC-001-08.
- Options: 1. (chosen) Consents are defined in the options configuration file (id, wording, mandatory). The shipped configuration contains one mandatory consent: "I agree that the organizers process my personal data for conference registration and organization, as described in the privacy notice." Consent id, wording shown and timestamp are stored. 2. No consents until the product owner answers (BR-05 untestable).
- Human response: none
- Resolution: option 1, pending review (the product owner must confirm the wording and add further consents, e.g. photography, in configuration)

## D-11: OQ-03 storage succeeds but an email fails
- Timestamp: 2026-09-29T23:33:12Z
- Phase: 1
- Type: non-blocking
- Trigger: OQ-03 unanswered; scope priority 1 says a registration is never lost; affects AC-006-03.
- Options: 1. (chosen) The registration stays accepted (it is stored); each email's delivery status is recorded; a scheduled job retries failed emails (every 5 minutes, at most 10 attempts) and logs, without personal data, when it gives up. 2. Reject the registration and roll back (loses a stored registration; contradicts priority 1). 3. Accept and never retry.
- Human response: none
- Resolution: option 1, pending review

## D-12: OQ-04 options per category
- Timestamp: 2026-09-29T23:33:12Z
- Phase: 1
- Type: non-blocking
- Trigger: OQ-04 unanswered; affects AC-003-05.
- Options: 1. (chosen) Any number of distinct options per category, zero included; duplicates rejected; the configuration may set a maximum per category (the shipped configuration sets no maximum). 2. Exactly one per category. 3. At most one per category.
- Human response: none
- Resolution: option 1, pending review. Chosen because a limit the product owner did not ask for could lock out legitimate selections (a High-severity example in the severity scale).

## D-13: OQ-05 second registration with the same email
- Timestamp: 2026-09-29T23:33:12Z
- Phase: 1
- Type: non-blocking
- Trigger: OQ-05 unanswered.
- Options: 1. (chosen) Allowed; every accepted registration is stored separately and appears in the export with its submission time, so organizers can spot duplicates. 2. Reject a second registration with the same email (tells any visitor whether an address is registered, which discloses personal data, and can lock out people sharing an address).
- Human response: none
- Resolution: option 1, pending review

## D-14: OQ-06 retention of registrations and JSON copies
- Timestamp: 2026-09-29T23:33:12Z
- Phase: 1
- Type: non-blocking
- Trigger: OQ-06 unanswered; SB-13 requires a retention period for every personal-data item (`project/04_security/security-requirements.md`).
- Options: 1. (chosen) Proposed retention: 12 months after the conference ends; deletion of database rows and JSON copies is a manual operator procedure documented in the backend README (automatic deletion and an admin UI are out of scope). 2. Implement automatic deletion with a configured period (not requested; risk of deleting data the organizer still needs).
- Human response: none
- Resolution: option 1, pending review

## D-15: Contract validators not listed in tech-stack.md
- Timestamp: 2026-09-29T23:37:03Z
- Phase: 2
- Type: non-blocking
- Trigger: the phase 2 gate requires contracts to validate with a parser; `tech-stack.md` lists no OpenAPI or JSON Schema validator.
- Options: 1. (chosen) Use @apidevtools/swagger-parser 13.1.0 (MIT), ajv 8.20.0 (MIT) and ajv-formats 3.0.1 (MIT), exact versions, installed only in a scratch directory outside the project; the script is kept as `docs/02_contracts/validate-contracts.mjs` and output in `out/logs/phase2-contract-validation.log`. They are not dependencies of any component and do not ship. 2. Add them to the frontend `package.json` (would put unlisted tools into a component manifest).
- Human response: none
- Resolution: option 1, pending review

## D-16: @types/node added to the frontend (not listed in tech-stack.md)
- Timestamp: 2026-09-29T23:56:19Z
- Phase: 3
- Type: non-blocking
- Trigger: the frozen e2e helper (`frontend/tests/e2e/support.ts`) uses Node APIs (fs, zlib, Buffer); type checking (`tsc --noEmit`, ES-05, DoD-02) needs Node type definitions, which `tech-stack.md` does not list and no listed package pulls in.
- Options: 1. (chosen) Add `@types/node` 24.19.0 (MIT, matches Node 24.13.0) as an exact dev dependency, locked in `package-lock.json`; build-time only, never shipped. 2. Exclude the e2e tests from type checking.
- Human response: none
- Resolution: option 1, pending review

## D-17: One acceptance test passes on the bootstrap skeleton
- Timestamp: 2026-09-29T23:56:19Z
- Phase: 3
- Type: non-blocking
- Trigger: the phase 3 gate asks every test to fail for a behavioural reason. `OperationsAcceptanceTest.nfr04HealthAndReadiness` passes on the skeleton because Spring Boot Actuator (in the phase 0 bootstrap) already serves liveness/readiness.
- Options: 1. (chosen) Keep it as a regression guard for NFR-04 and add `JsonCopyFailureAcceptanceTest.nfr04ReadinessReportsUnwritableCopyDirectory`, which fails on the skeleton and covers the part of NFR-04 not yet implemented. 2. Delete the passing test. 3. Weaken the skeleton to make it fail.
- Human response: none
- Resolution: option 1, pending review

## D-18: Lower F-07 (CVE-2025-7962 on angus-activation) from High to Low as a false positive
- Timestamp: 2026-09-30T00:44:49Z
- Phase: 6
- Type: blocking
- Trigger: `docs/06_verification-report.md` F-07. Dependency-Check reports CVE-2025-7962 (CVSS 7.5 → High per `general/quality/severity-scale.md`) on `angus-activation-2.0.3.jar`, identified as `cpe:2.3:a:eclipse:angus_mail:2.0.3`. Evidence it is not exploitable: (1) the CVE affects the SMTP implementation Angus Mail < 2.0.4 / Jakarta Mail < 2.0.2; the shipped SMTP implementation is `org.eclipse.angus:angus-mail:2.0.5` and `jakarta.mail:jakarta.mail-api:2.1.5` (`out/logs/phase0-backend-dependency-tree.log`); (2) `angus-activation` is the Jakarta Activation implementation and contains no SMTP code, so the match is a CPE mis-identification; (3) defence in depth: every text field rejects CR/LF and control characters and no user input is placed in mail headers (AC-006-02, SR-05 tests). No GA `angus-activation` newer than 2.0.3 exists (only 2.1.0-M1). Lowering a High needs a human decision (`severity-scale.md`, `working-rules.md`).
- Options: 1. (proposed default) Approve lowering F-07 to Low as a false positive; the agent adds a Dependency-Check suppression for this CVE on `angus-activation` only, with the justification above, re-runs the scan and completes phase 6. 2. Override `angus-activation` to 2.1.0-M1 (a milestone; not recommended) as a new tech-stack entry. 3. Keep F-07 open and do not release.
- Human response: none
- Resolution: pending
