# Backend

> Written in: phases 0, 7 · Source: `project/02_design/architecture.md` · Agent: writes

Spring Boot REST API of the conference registration. Completed in phase 7 (ES-06).

## Commands (ES-05)

Run in `02_output/backend`.

| Purpose | Command |
|---|---|
| build | `./mvnw -B -DskipTests package` |
| test | `./mvnw -B verify` |
| check | `./mvnw -B compile spotless:check pmd:check spotbugs:check` |
| run | `./mvnw spring-boot:run` |

Format the sources with `./mvnw spotless:apply`.
