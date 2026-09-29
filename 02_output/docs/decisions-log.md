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
