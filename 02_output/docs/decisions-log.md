# Decisions log

> Written in: every phase · Format: `skills/decisions` ("Decision record") · Agent: appends only

## D-01: Host JDK differs from the pinned Temurin 21.0.10+7
- Timestamp: 2026-10-09T08:27:05Z
- Phase: 0
- Type: non-blocking
- Trigger: preflight check 2, platform `java`: `java -version` reports Oracle Java SE 21.0.11+9-LTS (`JAVA_HOME` = jdk-21.0.11); no Temurin JDK on the host. It runs and builds the backend.
- Options: 1. build and test locally with the host JDK 21.0.11 (same language level 21); container images keep the pinned `eclipse-temurin:21.0.10_7-jre-alpine` (proposed); 2. stop until the human installs Temurin 21.0.10+7
- Human response: none
- Resolution: 1, pending review

## D-02: cloc image tag 2.10 reports version 1.98
- Timestamp: 2026-10-09T08:27:05Z
- Phase: 0
- Type: non-blocking
- Trigger: preflight check 2, tooling `aldanial/cloc` 2.10: `cloc --version` inside the image prints 1.98 (two fresh runs).
- Options: 1. keep the pinned tag `2.10`, which stays authoritative (proposed); 2. change the pin
- Human response: none
- Resolution: 1, pending review

## D-03: vitest 3.2.7 has a Critical advisory
- Timestamp: 2026-10-09T08:27:05Z
- Phase: 0
- Type: blocking
- Trigger: preflight check 6, `npm audit` (log `0_fe-audit.log`, confirmed twice): `vitest` 3.2.7 Critical via `tinypool` (GHSA-5gmw-xhrv-c9v3, GHSA-85c8-ppgw-ccpr, prototype pollution to RCE) and Moderate via `@vitest/mocker` (GHSA-82fw-gwwq-j7x9, fixed in 4.1.11); `@vitest/coverage-v8` 3.2.7 inherits it. Test tooling only, but the pin must change (`project/stack.md`).
- Options: 1. `vitest` and `@vitest/coverage-v8` to 4.1.11, the lowest release outside both advisory ranges; supports vite 6, no `tinypool`; `@stryker-mutator/vitest-runner` 10.0.0 accepts vitest >= 2 (proposed); 2. both to 5.0.3, the version `npm audit` suggests; 3. keep 3.2.7 and lower the Critical (dev-only, never shipped)
- Human response: pending
- Resolution: pending

## D-04: CVE-2025-7962 (CVSS 7.5, High) reported on angus-activation 2.0.3
- Timestamp: 2026-10-09T08:27:05Z
- Phase: 0
- Type: blocking
- Trigger: preflight check 6, Dependency-Check (log `0_be-depscan.log`, confirmed twice): CVE-2025-7962 (SMTP injection in Jakarta Mail < 2.0.2 / Angus Mail < 2.0.4) matched to `org.eclipse.angus:angus-activation` 2.0.3 through the CPE `eclipse:angus_mail:2.0.3`. The mail implementation actually resolved is `org.eclipse.angus:angus-mail` 2.0.5, outside the affected range; `angus-activation` contains no SMTP code. Lowering a High needs the human (`rules.md`, "Decisions").
- Options: 1. false positive: classify Low, suppress for `angus-activation` and this CVE only in a Dependency-Check suppression file, re-check in phase 6 (proposed); 2. keep it High and stop (no fixed `angus-activation` exists to move to)
- Human response: pending
- Resolution: pending

## D-05: CVE-2025-15104 (CVSS 5.3, Medium) reported on hibernate-validator 9.1.3
- Timestamp: 2026-10-09T08:27:05Z
- Phase: 0
- Type: non-blocking
- Trigger: preflight check 6, Dependency-Check: CVE-2025-15104 concerns Nu Html Checker (CPE `validator:validator`), matched to `hibernate-validator` by product name.
- Options: 1. false positive: classify Low, suppress for `hibernate-validator` and this CVE only, re-check in phase 6 (proposed); 2. keep as Medium
- Human response: none
- Resolution: 1, pending review

## D-06: qs advisories (Moderate) in test tooling
- Timestamp: 2026-10-09T08:27:05Z
- Phase: 0
- Type: non-blocking
- Trigger: preflight check 6, `npm audit`: `qs` 6.15.1 (GHSA-q8mj-m7cp-5q26, GHSA-x5fp-wj9c-mxmx, GHSA-4mjr-xmp4-gh2g, DoS) via `@stryker-mutator/core` 10.0.0 → `typed-rest-client` 2.3.1. Mutation-testing tool only; never shipped.
- Options: 1. keep, count as Medium, re-check in phase 6 (proposed); 2. override the transitive `qs` version
- Human response: none
- Resolution: 1, pending review

## D-07: Answer to D-03 (vitest pin)
- Timestamp: 2026-10-09T08:34:24Z
- Phase: 0
- Type: blocking
- Trigger: human answer to D-03
- Options: as D-03
- Human response: "4.1.11 (Recommended)", 2026-10-09T08:34:24Z
- Resolution: D-03 option 1: `vitest` and `@vitest/coverage-v8` pinned to 4.1.11; this replaces the 3.2.7 entries of `project/stack.md` for this run

## D-08: Answer to D-04 (CVE-2025-7962 on angus-activation)
- Timestamp: 2026-10-09T08:34:24Z
- Phase: 0
- Type: blocking
- Trigger: human answer to D-04
- Options: as D-04
- Human response: "False positive → Low (Recommended)", 2026-10-09T08:34:24Z
- Resolution: D-04 option 1: Low, suppressed for `angus-activation` and CVE-2025-7962 only in `backend/dependency-check-suppressions.xml`; re-check in phase 6

## D-09: OQ-01, option availability per registration type
- Timestamp: 2026-10-09T08:44:05Z
- Phase: 1
- Type: non-blocking
- Trigger: OQ-01 unanswered (`project/requirements.md`).
- Options: 1. every option states in configuration which registration types may select it (default: both); a selection not available to the registrant's type is rejected and not offered (proposed, more conservative); 2. every active option is available to both types
- Human response: none
- Resolution: 1, pending review (AC-001-09, AC-002-08, AC-003-03)

## D-10: OQ-02, mandatory consents
- Timestamp: 2026-10-09T08:44:05Z
- Phase: 1
- Type: non-blocking
- Trigger: OQ-02 unanswered.
- Options: 1. one mandatory consent: processing of the submitted personal data for the registration and organisation of the conference; its wording comes from configuration; never preselected; stored with the time it was given (proposed, the minimum SB-12/SB-14 need); 2. additional optional consents (e.g. photos, newsletter)
- Human response: none
- Resolution: 1, pending review (AC-001-12, AC-002-10)

## D-11: OQ-03, storage succeeds but an email fails
- Timestamp: 2026-10-09T08:44:05Z
- Phase: 1
- Type: non-blocking
- Trigger: OQ-03 unanswered; priority 1 in `project/requirements.md` puts storage before notification.
- Options: 1. the registration stays accepted, stored and confirmed; the failure is logged without personal data; no automatic retry (proposed: a stored registration is never reported as failed, which would invite a duplicate rejected by D-13); 2. report the registration as failed; 3. retry emails from a queue
- Human response: none
- Resolution: 1, pending review (AC-006-04, AC-007-05)

## D-12: OQ-04, options per category
- Timestamp: 2026-10-09T08:44:05Z
- Phase: 1
- Type: non-blocking
- Trigger: OQ-04 unanswered.
- Options: 1. each category has a maximum number of selections in configuration, default 1; selecting none is allowed; more than the maximum is rejected (proposed, more conservative); 2. no limit
- Human response: none
- Resolution: 1, pending review (AC-001-10, AC-001-11, AC-002-09)

## D-13: OQ-05, second registration with the same email
- Timestamp: 2026-10-09T08:44:05Z
- Phase: 1
- Type: non-blocking
- Trigger: OQ-05 unanswered.
- Options: 1. rejected: one registration per email address across both types, compared after trimming and ignoring letter case (proposed, more conservative: no duplicates, and editing is out of scope); 2. allowed
- Human response: none
- Resolution: 1, pending review (AC-001-15, AC-002-13)

## D-14: OQ-06, retention of registrations and JSON copies
- Timestamp: 2026-10-09T08:44:05Z
- Phase: 1
- Type: non-blocking
- Trigger: OQ-06 unanswered; SB-13 needs a stated retention period.
- Options: 1. the system deletes nothing automatically (deleting is out of scope); the retention period is set by the operator and listed under "Before production" in the release notes (proposed: no data is lost by an invented rule); 2. automatic deletion after a fixed period
- Human response: none
- Resolution: 1, pending review (no acceptance criterion; release notes)

## D-15: Maximum field lengths
- Timestamp: 2026-10-09T08:44:05Z
- Phase: 1
- Type: non-blocking
- Trigger: gap: no rule limits field length, but SR-03 limits request size and an unbounded field would be rejected only by the size limit, without naming the field.
- Options: 1. maximum lengths after trimming: email 254, first and last name 100, organization / institution, study institution and study programme 200, student ID 50; longer values are rejected naming the field (proposed); 2. no per-field limit
- Human response: none
- Resolution: 1, pending review (AC-001-16, AC-002-14)

## D-16: Invalid options configuration
- Timestamp: 2026-10-09T08:44:05Z
- Phase: 1
- Type: non-blocking
- Trigger: gap: US-003 does not say what happens with an invalid configuration.
- Options: 1. the system refuses to start and reports the configuration error (proposed: never accept registrations against a wrong option set); 2. start and ignore invalid entries
- Human response: none
- Resolution: 1, pending review (AC-003-06)

## D-17: Control characters in text fields
- Timestamp: 2026-10-09T08:46:48Z
- Phase: 2
- Type: non-blocking
- Trigger: gap found in design: BR-03 accepts Unicode text but no rule covers line breaks or other control characters, which can break emails, logs and the workbook (SR-05).
- Options: 1. reject any text field that contains a Unicode control character (category Cc), naming the field; AC-001-18 and AC-002-16 added, AC-006-03 narrowed to markup characters (proposed, more conservative); 2. accept and neutralise them on output
- Human response: none
- Resolution: 1, pending review

## D-18: Language of the user interface and emails
- Timestamp: 2026-10-09T08:46:48Z
- Phase: 2
- Type: non-blocking
- Trigger: gap: no input states the language of the form and emails; the requirements are in English.
- Options: 1. English texts, kept in one module per component so they can be translated; consent wording and option names come from configuration in any language (proposed); 2. Slovenian
- Human response: none
- Resolution: 1, pending review

## D-19: Semgrep High on HTTP Basic authentication for the export
- Timestamp: 2026-10-09T09:01:27Z
- Phase: 2
- Type: blocking
- Trigger: `verify.sh 2 semgrep`: rule `yaml.openapi.security.use-of-basic-authentication` (ERROR → High) on `docs/02_contracts/openapi.yaml` (`organizerBasic`, used only by `GET /api/export`). Mitigations in the design: credentials accepted only over HTTPS except on localhost (SR-06), password held only as a BCrypt hash in memory (SB-03), export rate limited (SR-03), read-only GET so no CSRF exposure; identity providers are out of scope and ASVS Level 1 applies. Lowering a High needs the human.
- Options: 1. keep Basic with these mitigations and classify the finding Low (accepted, evidence above); suppress only this rule on this contract and recheck in phase 6 (proposed); 2. replace Basic with a session login (organizer login form, session cookie, CSRF protection), which adds a login page to an otherwise UI-less organizer feature
- Human response: pending
- Resolution: pending

## D-20: Answer to D-19 (Basic authentication)
- Timestamp: 2026-10-09T09:03:08Z
- Phase: 2
- Type: blocking
- Trigger: human answer to D-19
- Options: as D-19
- Human response: "Keep Basic, classify Low (Recommended)", 2026-10-09T09:03:08Z
- Resolution: D-19 option 1: Low; inline `nosemgrep` for this rule on the `organizerBasic` scheme only; recheck in phase 6
