# Decisions log

> Written in: every phase · Format: `general/working-rules.md` ("Decision record") · Agent: appends only

## D-01: cloc image tag 2.10 reports version 1.98
- Timestamp: 2026-09-30T14:14:00Z
- Phase: 0
- Type: non-blocking
- Trigger: preflight step 2; `docker run --rm aldanial/cloc:2.10 --version` prints `1.98` (tech-stack.md `tooling`, id `aldanial/cloc`). The image resolves and runs.
- Options: 1. keep the listed pin `aldanial/cloc:2.10` (default, tech-stack rule); 2. ask for another tag
- Human response: none
- Resolution: 1 (the listed pin stays authoritative)

## D-02: Dependency-Check analysers disabled
- Timestamp: 2026-09-30T14:15:00Z
- Phase: 0
- Type: non-blocking
- Trigger: preflight step 6; the OSS Index analyser of `org.owasp:dependency-check-maven` needs Sonatype credentials that `secrets.env.example` does not list. The Node, Node Audit, RetireJS and .NET assembly analysers scan other ecosystems; the frontend is scanned with `npm audit` (tech-stack.md).
- Options: 1. disable `ossindexAnalyzerEnabled`, `nodeAnalyzerEnabled`, `nodeAuditAnalyzerEnabled`, `retireJsAnalyzerEnabled`, `assemblyAnalyzerEnabled`; scan with the NVD-based analysers (default); 2. ask for OSS Index credentials
- Human response: none
- Resolution: 1 (scan completed with the NVD analysers, see `logs/00_dependency-check.log`)

## D-03: Pinned tool sub-versions and Boot property overrides
- Timestamp: 2026-09-30T14:16:00Z
- Phase: 0
- Type: non-blocking
- Trigger: tech-stack.md lists `spotless-maven-plugin` "(google-java-format)" without a version, and the CVE override `tomcat-embed-core 11.0.26` is applied through Boot's `tomcat.version` property, which also moves the sibling transitive artifacts `tomcat-embed-el` and `tomcat-embed-websocket` to 11.0.26 (the same release). `log4j2.version` is set to 2.26.1 the same way.
- Options: 1. pin `google-java-format` 1.25.2 (dev-only formatter) and use the Boot version properties so all Tomcat artifacts stay on one release (default); 2. override only `tomcat-embed-core` and mix Tomcat releases
- Human response: none
- Resolution: 1

## D-04: CVE-2025-7962 (CVSS 7.5, High) reported on angus-activation 2.0.3
- Timestamp: 2026-09-30T14:20:00Z
- Phase: 0
- Type: blocking
- Trigger: preflight step 6; Dependency-Check matched `org.eclipse.angus:angus-activation:2.0.3` (transitive via `spring-boot-starter-mail`) to CPE `eclipse:angus_mail < 2.0.4` for CVE-2025-7962 (SMTP injection through CR/LF). Evidence it is a false positive: (a) the vulnerable product is Angus Mail; the build uses `org.eclipse.angus:angus-mail:2.0.5`, which is not flagged; (b) `angus-activation-2.0.3.jar` contains only `org.eclipse.angus.activation` classes and no SMTP code (checked with `unzip -l`). SR-05 additionally rejects CR/LF in every value used in email headers. Lowering a High needs human approval (severity-scale.md).
- Options: 1. treat as false positive: add a Dependency-Check suppression for CVE-2025-7962 on `angus-activation` only, with this evidence, and continue (default); 2. add `org.eclipse.angus:angus-activation` at a newer exact version to tech-stack via a human-approved change; 3. other
- Human response: option 1 approved ("False positive"), 2026-09-30T14:23:14Z
- Resolution: 1 (suppression in `backend/dependency-check-suppressions.xml`)

## D-05: CVE-2025-15104 (Medium) reported on hibernate-validator 9.1.3.Final
- Timestamp: 2026-09-30T14:20:00Z
- Phase: 0
- Type: non-blocking
- Trigger: preflight step 6; Dependency-Check matched `hibernate-validator` to CPE `validator:validator` (Nu Html Checker, validator.nu), an unrelated product. Medium does not block.
- Options: 1. record as a Low false positive and suppress it with this reason (default); 2. keep it open
- Human response: none
- Resolution: 1

## D-06: OQ-01 option availability for students
- Timestamp: 2026-09-30T14:26:04Z
- Phase: 1
- Type: non-blocking
- Trigger: `scope.md` OQ-01 is unanswered; US-002 mentions "activities available to students" but no rule restricts any option.
- Options: 1. every active option is available to both registration types; no audience field is invented (default); 2. add a per-option audience field to the options configuration
- Human response: none
- Resolution: pending review (option 1 implemented; AC-002-01)

## D-07: OQ-02 mandatory consents and wording
- Timestamp: 2026-09-30T14:26:04Z
- Phase: 1
- Type: non-blocking
- Trigger: `scope.md` OQ-02 and `business-rules.md` (Consent) are unanswered.
- Options: 1. consents are defined in the options configuration file (id, wording, required); the shipped configuration has one mandatory consent `privacy` ("I agree that the organizer processes my personal data to organise the conference, as described in the privacy notice."); each given consent is stored with its timestamp (default); 2. hard-code consents
- Human response: none
- Resolution: pending review (option 1; the product owner must supply the final wording)

## D-08: OQ-03 email failure after successful storage
- Timestamp: 2026-09-30T14:26:04Z
- Phase: 1
- Type: non-blocking
- Trigger: `scope.md` OQ-03 is unanswered; priority 1 in `scope.md` says a registration is never lost.
- Options: 1. the registration stays accepted (201, stored, JSON copy written); the failed email is logged with the registration id only (no personal data) and not retried automatically (default); 2. reject the registration; 3. add a retry queue
- Human response: none
- Resolution: pending review (option 1; AC-006-03)

## D-09: OQ-04 number of options per category
- Timestamp: 2026-09-30T14:26:04Z
- Phase: 1
- Type: non-blocking
- Trigger: `scope.md` OQ-04 is unanswered.
- Options: 1. no per-category limit: any set of distinct active options, each at most once (default, invents no limit that could lock out legitimate participants); 2. at most one option per category
- Human response: none
- Resolution: pending review (option 1; duplicates rejected by AC-001-05)

## D-10: OQ-05 second registration with the same email
- Timestamp: 2026-09-30T14:26:04Z
- Phase: 1
- Type: non-blocking
- Trigger: `scope.md` OQ-05 is unanswered; editing a registration is out of scope, so duplicates could not be corrected by participants.
- Options: 1. reject a second registration with the same email, compared case-insensitively, with 409 (default, conservative: no duplicate records to reconcile); 2. allow duplicates
- Human response: none
- Resolution: pending review (option 1; AC-001-09). Side effect: the 409 reveals that an address is registered (accepted for ASVS level 1; listed in release notes).

## D-11: OQ-06 retention of registrations and JSON copies
- Timestamp: 2026-09-30T14:26:04Z
- Phase: 1
- Type: non-blocking
- Trigger: `scope.md` OQ-06 and the retention column in `security-requirements.md` are unanswered (SB-13).
- Options: 1. no automatic deletion is built; the retention period is documented as "until the organizer deletes them after the conference, at most 12 months after it", and the component README describes manual deletion of database rows and JSON copies (default); 2. build scheduled deletion with a configurable period
- Human response: none
- Resolution: pending review (option 1)

## D-12: Frozen test casts timestamptz to OffsetDateTime (AC-005-01)
- Timestamp: 2026-09-30T14:55:08Z
- Phase: 4
- Type: blocking
- Trigger: `backend/src/test/java/si/konferenca/registration/acceptance/StorageAcceptanceTest.java` (frozen, `03_acceptance-manifest.sha256`) line 46 and the consent loop cast `row.get("submitted_at")` and `c.get("given_at")` to `java.time.OffsetDateTime`. The support class `Database` reads values with `ResultSet.getObject(int)`, for which the PostgreSQL JDBC driver (42.7.13) returns `java.sql.Timestamp` for `TIMESTAMPTZ` columns. The test therefore fails with `ClassCastException` whatever the implementation does; the schema contract (`database-schema.sql`) requires `TIMESTAMPTZ`. All other assertions of AC-005-01 are reached only after the cast.
- Options: 1. correct the test: replace the three casts with a conversion `((java.sql.Timestamp) value).toInstant().atOffset(ZoneOffset.UTC)` (same assertions, no weakening), then regenerate the manifest line for this file in a separate commit that names D-12 (default); 2. leave the test failing and release with AC-005-01 unverified by its acceptance test; 3. other
- Human response: option 1 approved ("Fix the cast"), 2026-09-30T15:13:22Z
- Resolution: 1 (two casts replaced by a Timestamp conversion; manifest line updated in its own commit)
