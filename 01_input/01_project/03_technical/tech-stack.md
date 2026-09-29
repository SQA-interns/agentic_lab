# Tech stack

> Owner: Architect · Read in: phases 0, 2, 4, 6 · Agent: read-only

Exact selected baseline; not an installed, integration-tested or vulnerability-cleared
baseline. Preflight must resolve/scan it. Changes remain blocking decisions. No
floating versions. Sources: M=https://repo.maven.apache.org/maven2/;
N=https://registry.npmjs.org/; G=https://github.com/; H=https://hub.docker.com/.
Maven coordinates below use M, npm packages N; native-tool sources are named.

## Platforms

|ID|Distribution|Version|Source|
|---|---|---|---|
|java|Temurin JDK|21.0.12+8|G/adoptium/temurin21-binaries|
|node|Node.js|22.23.3|https://nodejs.org/dist/|
|postgres|PostgreSQL|16.14|https://www.postgresql.org/|

## Backend dependencies

All backend Maven; runtime unless marked test/build. Use Spring Boot's exact parent/BOM
for transitive versions; capture effective dependency tree. Do not manually override
managed transitive versions. Listed direct pins prevail; scan resolved graph.

|Coordinate|Version|Scope|
|---|---|---|
|org.springframework.boot:spring-boot-starter-parent|3.5.16|build|
|org.springframework.boot:spring-boot-starter-web|3.5.16|runtime|
|org.springframework.boot:spring-boot-starter-data-jpa|3.5.16|runtime|
|org.springframework.boot:spring-boot-starter-validation|3.5.16|runtime|
|org.springframework.boot:spring-boot-starter-mail|3.5.16|runtime|
|org.springframework.boot:spring-boot-starter-actuator|3.5.16|runtime|
|org.springframework.boot:spring-boot-starter-security|3.5.16|runtime|
|org.springframework.boot:spring-boot-starter-test|3.5.16|test|
|org.flywaydb:flyway-core|11.7.2|runtime|
|org.flywaydb:flyway-database-postgresql|11.7.2|runtime|
|org.postgresql:postgresql|42.7.11|runtime|
|org.apache.poi:poi-ooxml|5.4.1|runtime|
|org.testcontainers:postgresql|1.21.4|test|
|org.testcontainers:junit-jupiter|1.21.4|test|
|com.tngtech.archunit:archunit-junit5|1.4.1|test|

JUnit/Mockito/Jackson/Hibernate are supplied through the pinned Boot graph.

## Frontend dependencies and tooling

All npm; React/ReactDOM runtime, others dev/build/test.

|Package|Version|Purpose|
|---|---|---|
|react,react-dom|19.2.8|UI|
|@types/react|19.0.12|types|
|@types/react-dom|19.0.4|types|
|typescript|5.8.3|type-check|
|vite|6.4.3|build|
|@vitejs/plugin-react|4.4.1|build|
|eslint,@eslint/js|9.25.1|lint|
|typescript-eslint|8.31.1|lint|
|prettier|3.5.3|format|
|vitest,@vitest/coverage-v8|3.2.4|test/coverage|
|@testing-library/react|16.3.0|test|
|@testing-library/jest-dom|6.6.3|test|
|@testing-library/user-event|14.6.1|test|
|jsdom|26.1.0|test|
|@playwright/test|1.55.1|e2e|
|@stryker-mutator/core,@stryker-mutator/vitest-runner|9.0.1|mutation|
|jscpd|4.0.5|duplication|

## Tooling

Maven plugins use M; listed Maven versions include suffix-free coordinates.

|Tool|Version|Purpose/source|
|---|---|---|
|maven|3.9.11|build; https://maven.apache.org/|
|maven-wrapper|3.3.2|build; M/org/apache/maven/wrapper/|
|npm-vite: npm|10.9.9|package/build; N/npm|
|org.springframework.boot:spring-boot-maven-plugin|3.5.16|build|
|com.diffplug.spotless:spotless-maven-plugin|2.44.3|format|
|com.github.spotbugs:spotbugs-maven-plugin|4.9.3.0|static-analysis|
|org.apache.maven.plugins:maven-pmd-plugin|3.26.0|static-analysis/CPD/complexity|
|org.jacoco:jacoco-maven-plugin|0.8.13|coverage|
|org.pitest:pitest-maven|1.19.1|mutation|
|org.pitest:pitest-junit5-plugin|1.2.2|mutation|
|security-scan: org.owasp:dependency-check-maven|12.1.3|dependency-scan|
|semgrep|1.120.0|SAST; https://pypi.org/project/semgrep/|
|gitleaks|8.24.3|secret-scan; G/gitleaks/gitleaks|
|cloc|2.04|code-metrics; G/AlDanial/cloc|
|Docker Engine|28.1.1|containers; https://docs.docker.com/engine/|
|Docker Compose|2.35.1|orchestration; G/docker/compose|
|axllent/mailpit|v1.24.1|local SMTP; H/axllent/mailpit|
|nginx|1.28.0|frontend container/proxy; H/library/nginx|

npm audit uses pinned npm. Resolve exact container tags to recorded digests during
preflight; no unversioned tags. Additional dependencies require exact pins and
non-blocking decisions, unless they alter the declared stack. Verify SPDX licences
from distributions; no repository licence is inferred from this input archive.

Licence decision: private lab use only; preserve notices. Preflight must extract
actual SPDX identifiers from publisher metadata for direct/transitive artifacts.
Unknown or incompatible terms require a decision; do not assume all tools share
a licence or claim legal certification.

Selection sources: https://github.com/spring-projects/spring-boot/releases ;
https://nodejs.org/en/download/archive/v22 ; https://github.com/react/react/releases ;
https://github.com/vitejs/vite/actions/workflows/release-tag.yml . Other pins are
author-selected candidates requiring registry/compatibility/security checks.
