# Decisions log

> Written in: every phase · Format: `skills/decisions` ("Decision record") · Agent: appends only

## D-01: Host JDK reports 21.0.11 (Oracle) instead of Temurin 21.0.10+7
- Timestamp: 2026-10-05T20:26:21Z
- Phase: 0
- Type: non-blocking
- Trigger: preflight check 2, `java -version` reports Java HotSpot 21.0.11+9 (`project/stack.md` platform `java`: Eclipse Temurin 21.0.10+7). The tool runs; same major version 21.
- Options: 1. (default) build and test with the host JDK 21.0.11; the pin stays authoritative and the runtime image stays `eclipse-temurin:21.0.10_7-jre-alpine`; 2. ask the human to install Temurin 21.0.10+7.
- Human response: accepted as recorded (2026-10-05T20:34:17Z)
- Resolution: 1

## D-02: Host Node.js 24.10.0 with npm 10.9.4 instead of 24.13.0 with npm 11.6.2
- Timestamp: 2026-10-05T20:26:21Z
- Phase: 0
- Type: non-blocking
- Trigger: preflight check 2, `node --version` v24.10.0, `npm --version` 10.9.4 (`project/stack.md` platform `node` and tool `npm audit (npm 11.6.2)`). Both run; `package-lock.json` is lockfileVersion 3, the format npm 11 writes as well.
- Options: 1. (default) use the host Node.js and npm; the pins stay authoritative and the frontend build image stays `node:24.13.0-alpine`; 2. ask the human to install Node.js 24.13.0 with npm 11.6.2.
- Human response: accepted as recorded (2026-10-05T20:34:17Z)
- Resolution: 1

## D-03: Dependency-Check reports High CVE-2025-7962 on angus-activation 2.0.3 (false positive)
- Timestamp: 2026-10-05T20:26:21Z
- Phase: 0
- Type: blocking
- Trigger: preflight check 6, `02_output/logs/0_be-depscan.log`: CVE-2025-7962, CVSS 7.5 (High), matched to `org.eclipse.angus:angus-activation:2.0.3` (transitive, via `spring-boot-starter-mail`) through CPE `cpe:2.3:a:eclipse:angus_mail:*` with `versionEndExcluding 2.0.4`, match confidence LOW. Evidence: the CVE is an SMTP injection in Jakarta Mail / Angus Mail; the mail implementation on the classpath is `org.eclipse.angus:angus-mail:2.0.5`, outside the affected range and not flagged; angus-activation is the activation framework, not the mail implementation, and only its version number falls under the angus_mail range. Also reported, Medium (non-blocking): CVE-2025-15104 on `hibernate-validator:9.1.3.Final`, a Nu Html Checker (validator.nu) CVE matched by product name.
- Options: 1. (default) classify CVE-2025-7962 on angus-activation as a false positive (Low), suppress it with a Dependency-Check suppression file in `02_output/backend` that names this CVE and this artifact only, and continue; 2. keep it as High and change the dependency set (needs a `project/stack.md` change from the human, for example an explicit angus-activation version); 3. other instruction.
- Human response: "D-03: 1" (2026-10-05T20:34:17Z): treat as a false positive, lower to Low with the evidence above, suppress for that one artifact only, keep the raw report, re-run the scan.
- Resolution: 1. Suppression `02_output/backend/dependency-check-suppressions.xml` (packageUrl `pkg:maven/org.eclipse.angus/angus-activation@2.0.3`, CVE-2025-7962 only); raw report kept in `02_output/logs/0_be-depscan-raw-report.json` and `0_be-depscan-raw.log`; re-scan `0_be-depscan.log`: Critical 0, High 0, Medium 1.

## D-04: Commit of verify.sh exceeds the 400-line commit limit
- Timestamp: 2026-10-05T20:35:19Z
- Phase: 0
- Type: non-blocking
- Trigger: commit `35c3c77` ("build: add verify.sh and report helpers for all tools") changed 441 lines (`verify.sh` 253, `summarize.mjs` 151, `validate-contracts.mjs` 37); `rules.md` ("Commits") asks for at most about 400 lines or a stated reason, and the message gives none.
- Options: 1. (default) state the reason here and leave history unchanged (`rules.md` forbids rewriting); reason: `phases.md` asks for one commit for `verify.sh`, and its two helper scripts are only called by it, so splitting them would leave a commit whose `verify.sh` cannot run; 2. rewrite the commit (not allowed).
- Human response: "state the reason in the decisions log or progress file; do not rewrite history" (2026-10-05T20:34:17Z)
- Resolution: 1

## D-05: OQ-01, options available per registration type
- Timestamp: 2026-10-05T20:41:40Z
- Phase: 1
- Type: non-blocking
- Trigger: OQ-01 unanswered (`project/requirements.md`).
- Options: 1. every active option is offered to both types; 2. (default) each option states in configuration the registration types it is offered to, default both; an option is shown and accepted only for those types. Option 2 covers either answer by configuration alone (US-003, AR-04) and rejects a selection the organizer did not offer.
- Human response: none
- Resolution: 2, pending review

## D-06: OQ-02, mandatory consents and their wording
- Timestamp: 2026-10-05T20:41:40Z
- Phase: 1
- Type: non-blocking
- Trigger: OQ-02 unanswered; BR-05 and SB-14 require explicit, never preselected consent with a timestamp (`project/constraints.md`, "Personal data").
- Options: 1. no consents; 2. (default) consents come from configuration (identifier, wording, mandatory or not); the default configuration holds one mandatory consent to the processing of the entered personal data for registration and organisation of the conference, with placeholder wording to be replaced by the organizer; each consent given is stored with its timestamp.
- Human response: none
- Resolution: 2, pending review (wording to be supplied by the product owner)

## D-07: OQ-03, storage succeeds but an email fails
- Timestamp: 2026-10-05T20:41:40Z
- Phase: 1
- Type: non-blocking
- Trigger: OQ-03 unanswered; priority 1 ("a registration is never lost") and BR-06.
- Options: 1. treat the registration as failed and undo it; 2. (default) the registration stays accepted and the confirmation is shown; the failed delivery is logged without personal data so organizers can follow up from the stored data and the JSON copy; no automatic retry.
- Human response: none
- Resolution: 2, pending review

## D-08: OQ-04, number of options per category
- Timestamp: 2026-10-05T20:41:40Z
- Phase: 1
- Type: non-blocking
- Trigger: OQ-04 unanswered. Inventing a limit could lock out legitimate participants (High in `standards/security.md`).
- Options: 1. one option per category; 2. (default) any number of distinct active options per category, including none; the same option twice in one registration is rejected.
- Human response: none
- Resolution: 2, pending review

## D-09: OQ-05, second registration with the same email
- Timestamp: 2026-10-05T20:41:40Z
- Phase: 1
- Type: non-blocking
- Trigger: OQ-05 unanswered; editing or cancelling a registration is out of scope.
- Options: 1. allow duplicates; 2. (default) reject a registration whose email (trimmed, case-insensitive) is already registered under either type, with a message telling the participant to contact the organizers; this keeps the stored data and the export free of duplicates and limits repeated automated submissions.
- Human response: none
- Resolution: 2, pending review

## D-10: OQ-06, retention of registrations and JSON copies
- Timestamp: 2026-10-05T20:41:40Z
- Phase: 1
- Type: non-blocking
- Trigger: OQ-06 unanswered; SB-13 requires a retention period; deletion and administration functions are out of scope.
- Options: 1. build automatic deletion after a fixed period (invents behaviour and an unrequested function); 2. (default) no deletion function is built; retention is stated as "until the organizers delete the data after the conference" and listed under "Before production" for the product owner to set; no acceptance criterion.
- Human response: none
- Resolution: 2, pending review

## D-11: Semgrep High: HTTP Basic authentication on the organizer export
- Timestamp: 2026-10-05T20:50:26Z
- Phase: 2
- Type: blocking
- Trigger: `verify.sh 2 semgrep` (`02_output/logs/2_semgrep.json`): rule `yaml.openapi.security.use-of-basic-authentication` (ERROR → High, CWE-287) on `docs/02_contracts/api.openapi.yaml` line 136, the `organizerBasic` scheme of `GET /api/registrations/export` (`02_specification.md` §7). The rule flags the scheme itself; it does not show an exploit path in this design.
- Evidence for a downgrade: credentials are accepted only over HTTPS (SR-06, 403 otherwise; refused at startup if switched off in production); the password is at least 16 random characters (`secrets.env.example`) and held only as a BCrypt hash (SB-03); the endpoint is rate limited (SR-03); there is one role and one read-only operation; ASVS 5.0 Level 1 is the chosen level (`project/constraints.md`); identity providers are out of scope, so OAuth2 / OpenID Connect are not available; there is no session, so there is no CSRF exposure.
- Options: 1. (default) keep HTTP Basic as specified and classify the finding as Low (scanner match on a deliberate, mitigated design), with the evidence above; 2. replace Basic with a credential exchange (`POST /api/organizer/token` with username and password returns a short-lived bearer token for the export); same password secret, more code, and the organizer can no longer download from a browser without a tool or UI; 3. replace Basic with form login and a session cookie plus CSRF protection; needs a login page, which is close to the out-of-scope administration UI; 4. other instruction.
- Human response: none
- Resolution: pending
