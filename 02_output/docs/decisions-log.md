# Decisions log

> Written in: every phase · Format: `general/rules.md` ("Decision log format") · Agent: appends only

## Non-blocking

| D | Timestamp | Phase | Trigger | Choice | Status |
|---|---|---|---|---|---|
| D-01 | 2026-10-04T20:08:12Z | 0 | `tech-stack.md` platform `java` 21.0.10+7 (Temurin); host `java -version` and `JAVA_HOME` both give Oracle JDK 21.0.11+9 | Build and test with the host JDK 21.0.11; the pin stays authoritative; the runtime image is the pinned `eclipse-temurin:21.0.10_7-jre-alpine` (reports 21.0.10) | pending review |
| D-02 | 2026-10-04T20:08:12Z | 0 | `tech-stack.md` platform `node` 24.13.0 with npm 11.6.2; host `node --version` 24.10.0, `npm --version` 10.9.4 (only install under `C:\Program Files\nodejs`); also tool `npm audit (npm 11.6.2)` | Use the host Node 24.10.0 / npm 10.9.4 for install, checks and `npm audit`; lock file is version 3 (same format as npm 11); the container build stage is the pinned `node:24.13.0-alpine` | pending review |
| D-03 | 2026-10-04T20:08:12Z | 0 | Dependency-Check: CVE-2025-7962 (CVSS 3 7.5, High) matched `cpe:eclipse:angus_mail` < 2.0.4 with LOW confidence against `org.eclipse.angus:angus-activation` 2.0.3; actual artifact `org.eclipse.angus:angus-mail` 2.0.5 ships the fix; raw report `out/logs/0_be-depcheck-raw-before-suppression.json` | False positive (identifier mismatch, fixed version shipped); suppressed in `backend/dependency-check-suppressions.xml` | pending review |
| D-04 | 2026-10-04T20:08:12Z | 0 | Dependency-Check: CVE-2025-15104 (Medium) of the Nu Html Checker (`cpe:validator:validator`) matched by name against `org.hibernate.validator:hibernate-validator` 9.1.3.Final; same raw report | False positive (identifier mismatch); suppressed in `backend/dependency-check-suppressions.xml` | pending review |
| D-05 | 2026-10-04T20:08:12Z | 0 | `tech-stack.md` tool `aldanial/cloc` "2.10": the image runs, `--version` prints 1.98 | Keep the pinned tag; the pin stays authoritative | pending review |
| D-06 | 2026-10-04T20:10:23Z | 1 | OQ-01 unanswered (options for students vs external); BR-04, Data "Conference option" has no eligibility field | Every active option is offered to both registration types; no per-type restriction is invented (AC-002-08) | pending review |
| D-07 | 2026-10-04T20:10:23Z | 1 | OQ-02 unanswered (mandatory consents and wording); BR-05, SB-12, SB-14 | Exactly one mandatory consent: processing of the submitted personal data to organise the participant's attendance and the selected activities; wording comes from configuration; never preselected; no optional consents (AC-001-11, AC-001-12, AC-002-07) | pending review |
| D-08 | 2026-10-04T20:10:23Z | 1 | OQ-03 unanswered (storage succeeds, email fails); scope priority 1 | The registration stays accepted and stored and the confirmation is shown; a failed email never undoes or blocks storage; the failure is logged without personal data (AC-006-03, AC-007-03) | pending review |
| D-09 | 2026-10-04T20:10:23Z | 1 | OQ-04 unanswered (options per category) | Zero or more distinct active options per category; selecting the same option twice is rejected; no upper limit, so no legitimate participant is locked out (AC-001-09, AC-001-10) | pending review |
| D-10 | 2026-10-04T20:10:23Z | 1 | OQ-05 unanswered (second registration with the same email) | Rejected: one registration per email address across both types, compared case-insensitively after trimming; the participant is told the address is already registered and to contact the organizers (AC-001-14, AC-002-09) | pending review |
| D-11 | 2026-10-04T20:10:23Z | 1 | OQ-06 unanswered (retention of registrations and JSON copies); SB-13 | The application never deletes registrations or JSON copies by itself (scope priority 1); retention is an operator procedure: the release notes state the purpose and ask the organizer to set the period and delete both copies by hand; no acceptance criterion | pending review |

## Blocking
