# Decisions log

> Written in: every phase · Format: `general/decision-record.md` · Agent: appends only

## D-01: Host JDK is not Temurin 21.0.12+8
- Timestamp: 2026-09-30T09:51Z
- Phase: 0
- Type: blocking
- Trigger: tech-stack.md `java` = Temurin JDK 21.0.12+8; host has Ubuntu OpenJDK 21.0.12.1+1 only (00_preflight-report.md).
- Options: 1. (default) Agent downloads the official Temurin 21.0.12+8 tarball (`OpenJDK21U-jdk_x64_linux_hotspot_21.0.12_8.tar.gz`, adoptium GitHub, checksum verified) into a user-local directory outside the repo (no sudo, system JDK untouched) and uses it via JAVA_HOME. 2. Operator installs Temurin 21.0.12+8 and reports its path. 3. Accept Ubuntu OpenJDK 21.0.12.1 as a recorded deviation (containers still use `eclipse-temurin:21.0.12_8`).
- Human response: approved all proposed defaults (2026-09-30T09:57:23Z)
- Resolution: option 1 (approved)

## D-02: Maven 3.9.11 not installed
- Timestamp: 2026-09-30T09:51Z
- Phase: 0
- Type: blocking
- Trigger: tech-stack.md `maven` 3.9.11 / `maven-wrapper` 3.3.2; `mvn` absent on host. Backend dependency scan (security-scan) cannot run without it.
- Options: 1. (default) Generate Maven Wrapper 3.3.2 in the backend; it downloads Maven 3.9.11 from Maven Central into `~/.m2/wrapper` (user-local). 2. Operator installs Maven 3.9.11 on the host.
- Human response: approved all proposed defaults (2026-09-30T09:57:23Z)
- Resolution: option 1 (approved)

## D-03: Docker Engine/Compose versions differ from pins
- Timestamp: 2026-09-30T09:51Z
- Phase: 0
- Type: blocking
- Trigger: tech-stack.md Docker Engine 28.1.1 / Compose 2.35.1; host has Engine 29.3.1 / Compose v5.1.1. Downgrading would also affect another running project.
- Options: 1. (default) Accept the installed newer versions as a recorded deviation for this lab run; Compose file uses only features supported by 2.35.1. 2. Operator installs the pinned versions.
- Human response: approved all proposed defaults (2026-09-30T09:57:23Z)
- Resolution: option 1 (approved)

## D-04: semgrep, gitleaks, cloc not at pinned versions on host
- Timestamp: 2026-09-30T09:51Z
- Phase: 0
- Type: blocking
- Trigger: semgrep 1.120.0 pinned, 1.178.0 installed; gitleaks 8.24.3 and cloc 2.04 not installed.
- Options: 1. (default) Run the exact versions as containers pinned by digest (`semgrep/semgrep:1.120.0`, `zricethezav/gitleaks:v8.24.3`, `aldanial/cloc:2.04`; digests in `out/logs/phase0-dependency-resolution.log`), no host install. 2. Operator installs the exact versions on the host.
- Human response: approved all proposed defaults (2026-09-30T09:57:23Z)
- Resolution: option 1 (approved)

## D-05: vitest 3.2.4 has a Critical advisory
- Timestamp: 2026-09-30T09:51Z
- Phase: 0
- Type: blocking
- Trigger: npm audit of the pinned frontend set: `vitest@3.2.4` Critical GHSA-5xrq-8626-4rwp (fixed 3.2.6), Moderate GHSA-82fw-gwwq-j7x9; also Moderate/Low in stryker 9.0.1 (ajv, @babel/core) and eslint 9.25.1 (@eslint/plugin-kit). Raw: `out/logs/phase0-npm-audit.json`.
- Options: 1. (default) Pin `vitest` and `@vitest/coverage-v8` to 3.2.7 (same minor; audit then shows 0 Critical/0 High, 6 Moderate, 4 Low, all dev-only). 2. Keep 3.2.4 and downgrade the finding (dev-only; Vitest UI server never started) — requires written approval.
- Human response: approved all proposed defaults (2026-09-30T09:57:23Z)
- Resolution: option 1 (approved)

## D-06: No `.env` present
- Timestamp: 2026-09-30T09:51Z
- Phase: 0
- Type: blocking
- Trigger: secrets.env.example; required locally: DB_PASSWORD, ORGANIZER_USERNAME, ORGANIZER_PASSWORD_HASH. Not needed locally: RECAPTCHA_*, SMTP_* (stub captcha, isolated Mailpit). NVD_API_KEY optional but dependency-check is very slow or rate-limited without it.
- Options: 1. (default) Agent generates random synthetic local-only values into the git-ignored `.env` (bcrypt hash for the organizer; the demo plaintext kept only in a git-ignored local file for the runtime demonstration); values never printed. 2. Operator fills `.env` and tells the agent where a demo organizer password can be read for the runtime check. Either way, operator optionally adds NVD_API_KEY.
- Human response: approved all proposed defaults (2026-09-30T09:57:23Z)
- Resolution: option 1 (approved)

## D-07: Not on the intended run branch
- Timestamp: 2026-09-30T09:51Z
- Phase: 0
- Type: blocking
- Trigger: run-config.md intends branch `run/single-sdd/conference-001`; HEAD is `filled_out_files` at 52eb3ed (clean).
- Options: 1. (default) Agent creates local branch `run/single-sdd/conference-001` at 52eb3ed and commits there; no push. 2. Operator creates/selects the branch. 3. Commit on `filled_out_files`.
- Human response: approved all proposed defaults (2026-09-30T09:57:23Z)
- Resolution: option 1 (approved)

## D-08: Playwright 1.55.1 browser not installed
- Timestamp: 2026-09-30T09:51Z
- Phase: 0
- Type: blocking
- Trigger: e2e tests need Chromium revision 1193 for @playwright/test 1.55.1; only revision 1243 is cached.
- Options: 1. (default) Run `npx playwright install chromium` from the pinned package (user cache `~/.cache/ms-playwright`, no system packages). 2. Run e2e in `mcr.microsoft.com/playwright:v1.55.1-noble`.
- Human response: approved all proposed defaults (2026-09-30T09:57:23Z)
- Resolution: option 1 (approved)

## D-09: Container base images not named in tech-stack.md
- Timestamp: 2026-09-30T09:51Z
- Phase: 0
- Type: non-blocking
- Trigger: architecture.md requires backend/frontend containers; tech-stack.md names platforms but not build images.
- Options: 1. (default) Use the official images of the pinned platforms, pinned by digest: `eclipse-temurin:21.0.12_8-jdk`/`-jre`, `node:22.23.3`, `nginx:1.28.0` (configured to run as non-root, SB-11), `postgres:16.14`, `axllent/mailpit:v1.24.1`. 2. Other bases (would alter the stack).
- Human response: none
- Resolution: 1, pending review

## D-10: Foreign Compose stack running on the host
- Timestamp: 2026-09-30T09:51Z
- Phase: 0
- Type: non-blocking
- Trigger: containers `conference-*` from `/home/tjan-kazar/storage/Work/lab/single_agent/02_Implementation` occupy 127.0.0.1:8088 and :8025.
- Options: 1. (default) Leave them untouched; this run uses Compose project name `agenticlab` and host ports 18080 (app), 18025 (Mailpit UI). 2. Stop them.
- Human response: none
- Resolution: 1, pending review

## D-11: Spring Boot 3.5.16 managed graph has Critical/High CVEs
- Timestamp: 2026-09-30T10:09Z
- Phase: 0
- Type: blocking
- Trigger: dependency-check 12.1.3 (NVD data 2026-09-30) on the pinned backend graph: 22 Critical / 23 High (CVSS) in tomcat-embed-core 10.1.55 (14 CVEs, fixed 10.1.56–10.1.58), spring-core/spring-web 6.2.19 (12 CVEs), spring-security 6.5.11 (3 CVEs), plus pgjdbc 42.7.11 CVE-2026-54291 (reported HIGH, CVSS 5.9). Boot 3.5.16 is the newest 3.5.x; Spring Framework 6.2.20 / Security 6.5.12 are not published on Maven Central; Tomcat 10.1.59/10.1.60 are. Raw: out/logs/phase0-dependency-check.log, phase0-dependency-check-report.json, phase0-dependency-check-reachability-input.txt.
- Reachability evidence (Spring/pgjdbc): every Spring CVE needs a feature this app does not use — XsltView, SSE, WebFlux (incl. Aalto, PartEvent, Jetty, WebSocket, functional endpoints), SpEL on user input, RSocket, data binding of user-supplied property paths (the API binds JSON bodies with Jackson only), embedded UnboundID LDAP, DPoP, WebAuthn. pgjdbc CVE needs channelBinding=require, which is not configured.
- Options: 1. (default) Keep Boot 3.5.16 and all direct pins; override only the Boot-managed tomcat.version to 10.1.60 (removes all Tomcat Critical/High); record the Spring Framework/Security and pgjdbc CVEs as not reachable (downgraded to Low with the evidence above), enforce "features not used" with an ArchUnit test, and re-scan in phase 6. 2. As 1, and also change the direct pin postgresql 42.7.11 -> 42.7.13. 3. Migrate to Spring Boot 4.1.1 (Framework 7.0.9, Security 7.1.1): major stack change (Jackson 3, Testcontainers/Flyway integration changes), re-pin and re-scan. 4. Stop the run.
- Human response: approved option 1 (2026-09-30T10:13:29Z)
- Resolution: option 1 (approved): tomcat.version=10.1.60; Spring Framework/Security and pgjdbc CVEs downgraded to Low as not reachable, enforced by ArchUnit; re-scan in phase 6

## D-12: Location of .gitignore, .gitattributes and .env
- Timestamp: 2026-09-30T10:09Z
- Phase: 0
- Type: non-blocking
- Trigger: ES-02/ES-03 require ignore/attributes files and an ignored .env; AGENTS.md allows writing only README section 3 paths (02_output/, 03_statistics run files).
- Options: 1. (default) Put .gitignore/.gitattributes in 02_output/ (they apply to all generated files) and the ignored .env in 02_output/.env next to the Compose file. 2. Write repository-root files (outside the allowed paths).
- Human response: none
- Resolution: 1, pending review

## D-13: google-java-format version for Spotless
- Timestamp: 2026-09-30T10:09Z
- Phase: 0
- Type: non-blocking
- Trigger: spotless-maven-plugin 2.44.3 downloads a formatter at run time; tech-stack.md does not name it.
- Options: 1. (default) Pin google-java-format 1.25.2 in the pom (exact version, Apache-2.0). 2. Leave the plugin default (implicit version).
- Human response: none
- Resolution: 1, pending review

## D-14: Additional dev dependency @types/node
- Timestamp: 2026-09-30T10:26Z
- Phase: 0
- Type: non-blocking
- Trigger: tsc type-check of vite.config.ts / playwright.config.ts needs Node type definitions; not listed in tech-stack.md.
- Options: 1. (default) Add devDependency @types/node 22.20.4 (exact, MIT, matches Node 22 line; npm audit clean). 2. Avoid Node APIs in config files (loses ES-01 configurable proxy target).
- Human response: none
- Resolution: 1, pending review

## D-15: unzip in the backend build image
- Timestamp: 2026-09-30T10:26Z
- Phase: 0
- Type: non-blocking
- Trigger: Maven Wrapper 3.3.2 only-script mode falls back to the .tar.gz distribution when unzip is absent, which fails the pinned SHA-256 of the .zip (out/logs/phase0-compose-build.log). The wrapper rejects .tar.gz URLs.
- Options: 1. (default) apt-get install unzip in the build stage only (not in the runtime image). 2. Use a different build image (not pinned in tech-stack.md).
- Human response: none
- Resolution: 1, pending review

## D-16: Licence inventory
- Timestamp: 2026-09-30T10:26Z
- Phase: 0
- Type: non-blocking
- Trigger: tech-stack.md requires SPDX identifiers from publisher metadata. Inventory (out/logs/phase0-licence-inventory.txt): 139 Maven + 499 npm artifacts; permissive (MIT, Apache-2.0, BSD, ISC, BlueOak, 0BSD, CC0, CC-BY-4.0, Python-2.0) plus weak copyleft / dual: Hibernate ORM (LGPL-2.1-or-later), logback (EPL-2.0 or LGPL-2.1), Jakarta APIs / Angus Mail (EPL-2.0 or GPL-2.0 with Classpath exception), JNA (test only). xmlbeans POM has no licence element; its jar LICENSE.txt is Apache-2.0.
- Options: 1. (default) Accept for private lab use without redistribution (OQ-05); preserve notices; no repository licence added. 2. Replace components.
- Human response: none
- Resolution: 1, pending review
