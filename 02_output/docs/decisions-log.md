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
