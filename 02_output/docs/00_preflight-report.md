# Preflight report

> Written in: phase 0 · Source: `project/00_setup/*` · Procedure: `general/phases/0-preflight.md` · Agent: writes

| Check | Source of truth | Result |
|---|---|---|
| Run configuration complete | `project/00_setup/run-config.md` | pass: model, effort, template version, run id present |
| Platforms and tools installed at the listed versions | `project/00_setup/tech-stack.md` | pass with decisions: java runs, other version (D-03; `java -version`); node 24.13.0/npm 11.6.2 via nvm, not on PATH (D-02; `node --version`, `npm --version`); docker/compose run, other versions (D-04; `docker version`, `docker compose version`); cloc tag reports 1.98 (D-05); every other tool: version command inside its image or Maven/npm resolution, output contains the listed version |
| Local environments and services running or reachable | `project/00_setup/environments.md` | pass: Docker Engine reachable; all images pulled and started once (`out/logs/00_image-pull.log`); local stack is started by compose (bootstrap pending phase 4). Foreign containers from another project hold host ports 8088/8026, so this run uses other ports |
| Secrets present in `.env` or marked test-only | `project/00_setup/secrets.env.example` | pass after D-06 (generated local values) and D-07 (`NVD_API_KEY` absent; cached NVD data). Method: `ls .env`, then `sed -n 's/^KEY=//p' .env \| tr … \| grep -q .` per key |
| Every listed dependency resolves | `project/00_setup/tech-stack.md` | pass: all exact versions resolve (table below) |
| No listed dependency has a known Critical or High vulnerability | `project/00_setup/tech-stack.md` | pass with decisions: Dependency-Check 0 after FP suppressions D-08, D-09; `npm audit --omit=dev` 0; dev-only tooling Critical/High accepted D-10 |
| Clean working tree on the starting commit | repository | pass: clean at b6c9dc6 (detached; branch per D-01); only `.env` and `03_statistics/run-log.json` untracked |
| Input manifest written | `docs/00_input-manifest.sha256` | pass: 27 files, LF-normalised, `sha256sum -c` ok |

## Dependency results

| id | version | Resolves | Highest vulnerability |
|---|---|---|---|
| org.springframework.boot:spring-boot-starter-parent | 4.1.1 | yes (`mvnw dependency:list` (`out/logs/00_backend-deps.txt`)) | none |
| org.springframework.boot:spring-boot-starter-webmvc | 4.1.1 | yes (`mvnw dependency:list` (`out/logs/00_backend-deps.txt`)) | none |
| org.springframework.boot:spring-boot-starter-data-jpa | 4.1.1 | yes (`mvnw dependency:list` (`out/logs/00_backend-deps.txt`)) | none |
| org.springframework.boot:spring-boot-starter-validation | 4.1.1 | yes (`mvnw dependency:list` (`out/logs/00_backend-deps.txt`)) | none |
| org.springframework.boot:spring-boot-starter-mail | 4.1.1 | yes (`mvnw dependency:list` (`out/logs/00_backend-deps.txt`)) | none |
| org.springframework.boot:spring-boot-starter-actuator | 4.1.1 | yes (`mvnw dependency:list` (`out/logs/00_backend-deps.txt`)) | none |
| org.springframework.boot:spring-boot-starter-security | 4.1.1 | yes (`mvnw dependency:list` (`out/logs/00_backend-deps.txt`)) | none |
| org.springframework.boot:spring-boot-starter-flyway | 4.1.1 | yes (`mvnw dependency:list` (`out/logs/00_backend-deps.txt`)) | none |
| org.flywaydb:flyway-core | 12.4.0 | yes (`mvnw dependency:list` (`out/logs/00_backend-deps.txt`)) | none |
| org.flywaydb:flyway-database-postgresql | 12.4.0 | yes (`mvnw dependency:list` (`out/logs/00_backend-deps.txt`)) | none |
| org.postgresql:postgresql | 42.7.13 | yes (`mvnw dependency:list` (`out/logs/00_backend-deps.txt`)) | none |
| org.apache.poi:poi-ooxml | 5.5.1 | yes (`mvnw dependency:list` (`out/logs/00_backend-deps.txt`)) | none |
| org.apache.tomcat.embed:tomcat-embed-core | 11.0.26 | yes (`mvnw dependency:list` (`out/logs/00_backend-deps.txt`)) | none |
| org.apache.logging.log4j:log4j-api | 2.26.1 | yes (`mvnw dependency:list` (`out/logs/00_backend-deps.txt`)) | none |
| org.apache.commons:commons-lang3 | 3.20.0 | yes (`mvnw dependency:list` (`out/logs/00_backend-deps.txt`)) | none |
| org.springframework.boot:spring-boot-starter-test | 4.1.1 | yes (`mvnw dependency:list` (`out/logs/00_backend-deps.txt`)) | none |
| org.springframework.boot:spring-boot-starter-webmvc-test | 4.1.1 | yes (`mvnw dependency:list` (`out/logs/00_backend-deps.txt`)) | none |
| org.testcontainers:testcontainers-junit-jupiter | 2.0.5 | yes (`mvnw dependency:list` (`out/logs/00_backend-deps.txt`)) | none |
| org.testcontainers:testcontainers-postgresql | 2.0.5 | yes (`mvnw dependency:list` (`out/logs/00_backend-deps.txt`)) | none |
| com.tngtech.archunit:archunit-junit5 | 1.3.2 | yes (`mvnw dependency:list` (`out/logs/00_backend-deps.txt`)) | none |
| react | 19.3.0 | yes (`npm install` + lock file) | none |
| react-dom | 19.3.0 | yes (`npm install` + lock file) | none |
| @types/react | 19.3.0 | yes (`npm install` + lock file) | none |
| @types/react-dom | 19.3.0 | yes (`npm install` + lock file) | none |
| @vitejs/plugin-react | 4.7.0 | yes (`npm install` + lock file) | none |
| @testing-library/react | 16.3.3 | yes (`npm install` + lock file) | none |
| @testing-library/jest-dom | 6.9.1 | yes (`npm install` + lock file) | none |
| jsdom | 26.1.0 | yes (`npm install` + lock file) | none |
| globals | 15.15.0 | yes (`npm install` + lock file) | none |
| postgres | 16.15-alpine | yes (`docker pull` + version in image) | not scanned in phase 0 (images scanned in phase 6) |
| eclipse-temurin | 21.0.10_7-jre-alpine | yes (`docker pull` + version in image) | not scanned in phase 0 (images scanned in phase 6) |
| node | 24.13.0-alpine | yes (`docker pull` + version in image) | not scanned in phase 0 (images scanned in phase 6) |
| nginx | 1.30.5-alpine | yes (`docker pull` + version in image) | not scanned in phase 0 (images scanned in phase 6) |
| axllent/mailpit | v1.31.1 | yes (`docker pull` + version in image) | not scanned in phase 0 (images scanned in phase 6) |
| maven-wrapper (tool) | 3.3.2 | yes | none |
| org.springframework.boot:spring-boot-maven-plugin (tool) | 4.1.1 | yes | none |
| com.diffplug.spotless:spotless-maven-plugin (tool) | 2.44.3 | yes | none |
| com.github.spotbugs:spotbugs-maven-plugin (tool) | 4.10.4.1 | yes | none |
| org.apache.maven.plugins:maven-pmd-plugin (tool) | 3.26.0 | yes | none |
| org.jacoco:jacoco-maven-plugin (tool) | 0.8.12 | yes | none |
| org.pitest:pitest-maven (tool) | 1.30.0 | yes | none |
| org.owasp:dependency-check-maven (tool) | 12.1.0 | yes | none |
| vite (tool) | 6.4.3 | yes | none |
| typescript (tool) | 5.9.3 | yes | none |
| eslint (tool) | 9.39.5 | yes | none |
| @eslint/js (tool) | 9.39.5 | yes | none |
| typescript-eslint (tool) | 8.70.1 | yes | none |
| eslint-plugin-react-hooks (tool) | 5.2.0 | yes | none |
| eslint-plugin-react-refresh (tool) | 0.4.26 | yes | none |
| prettier (tool) | 3.9.9 | yes | none |
| vitest (tool) | 3.2.7 | yes | Critical (D-10, dev only) |
| @vitest/coverage-v8 (tool) | 3.2.7 | yes | Moderate (via vitest, dev only) |
| @playwright/test (tool) | 1.63.0 | yes | none |
| @stryker-mutator/core (tool) | 10.0.0 | yes | none |
| @stryker-mutator/vitest-runner (tool) | 10.0.0 | yes | none |
| jscpd (tool) | 4.3.0 | yes | High (D-10, dev only) |
| npm audit (npm 11.6.2) (tool) | 11.6.2 | yes | none |
| semgrep/semgrep (tool) | 1.177.0 | yes | none |
| zricethezav/gitleaks (tool) | v8.30.1 | yes | none |
| aldanial/cloc (tool) | 2.10 | yes | none |

## Asked the human

| When | What was missing | Answer | Re-check |
|---|---|---|---|
| not asked (standing instruction) | `.env` (D-06), `NVD_API_KEY` (D-07), dev-tool advisories (D-10) | resolved by agent default under the standing instruction | `verify.sh 0`: all 19 tools exit 0 |
