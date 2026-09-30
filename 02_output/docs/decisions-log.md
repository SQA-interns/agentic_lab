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
- Human response: none
- Resolution: pending (waiting for the human)

## D-05: CVE-2025-15104 (Medium) reported on hibernate-validator 9.1.3.Final
- Timestamp: 2026-09-30T14:20:00Z
- Phase: 0
- Type: non-blocking
- Trigger: preflight step 6; Dependency-Check matched `hibernate-validator` to CPE `validator:validator` (Nu Html Checker, validator.nu), an unrelated product. Medium does not block.
- Options: 1. record as a Low false positive and suppress it with this reason (default); 2. keep it open
- Human response: none
- Resolution: 1
