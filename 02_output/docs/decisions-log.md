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

## Blocking
