# Resolved versions (generated)

Generated from the actual build resolution on 2026-09-28 (`mvn dependency:list`, `help:effective-pom`, `npm ls`).
The fixed-stack ranges come from `01_Input_files/TECH_STACK.md`; this file records their realization.

## Toolchain

| Item | Version |
|---|---|
| Java | OpenJDK 21.0.12.1 (host build/test); eclipse-temurin 21 JDK/JRE images in containers |
| Maven Wrapper | wrapper 3.3.4 (only-script), Maven 3.9.16 |
| Spring Boot parent | 4.1.1 (authorized ST-01 deviation; previously 3.5.16) |
| Node / npm | Node 22.23.3 (host, via nvm), npm 10.9.9 bundled; `node:22-bookworm-slim` in the frontend build image. Lockfile v3; the vitest 4.1.11 subtree was resolved once with npm 11.11.0 on Node 22 because npm 10.9.9 crashed (arborist `edgesOut`), `npm ci` verified with npm 10 |
| Playwright browsers | chromium-headless-shell 1243 (Playwright 1.63.0) |
| Semgrep | 1.178.0 |
| OWASP Dependency-Check | dependency-check-maven 13.0.0, NVD JSON 2.0 data feeds |

## Container images (pinned by digest)

| Tag (recorded) | Digest |
|---|---|
| postgres:16 (16.15) | sha256:1a6ab3f5345eb6dbe04a1349529caabdb0ab09293a09590fad07b2246bfa4b54 |
| axllent/mailpit:v1.29 | sha256:757f22b56c1da03570afdb3d259effe5091018008a81bbedc8158cee7e16fdbc |
| eclipse-temurin:21-jdk-noble | sha256:d0aa6704a67ac080591815bb7734dbe56171dd7a8e9b767e9989b95dd37b3085 |
| eclipse-temurin:21-jre-noble | sha256:7edbe8532195c8222735caca437e56780e1fc7db08a4b4b4af77fe188cccc15f |
| node:22-bookworm-slim | sha256:43ac6c60b8f89723f746e8a92ce91abd5017e627ce1ddfe4238355d3a30b772c |
| nginxinc/nginx-unprivileged:1.30-alpine | sha256:ed04ec1ff34502c339ee5c3ae3f855442398edc1d05591e2b98981dcbbd20b1e |

## Within-range changes after the baseline

| Change | Reason | Fresh checks |
|---|---|---|
| tomcat 10.1.55→10.1.60, postgresql 42.7.11→42.7.13, jackson-bom 2.21.4→2.21.7, log4j2 2.24.3→2.25.5, commons-lang3 3.17.0→3.20.0 (Boot property overrides) | Dependency-Check advisories fixed in public patch releases (first complete suite) | backend verify, dependency-check, final suite |
| eslint/@eslint/js 9.39.5→10.11.0/10.0.1 (before feature work) | ESLint 9 reported as unsupported at install | lint |
| vitest/@vitest/coverage-v8 3.2.7→4.1.11 | GHSA-82fw-gwwq-j7x9 (moderate) in @vitest/mocker | frontend unit/coverage, npm audit, final suite |

| Spring Boot 3.5.16 → 4.1.1 (authorized stack change); Boot-3 overrides for pgjdbc/jackson/log4j/commons-lang3 removed (Boot 4.1.1 manages fixed versions); tomcat override now 11.0.26 | spring-core 6.2.19 / spring-security-core 6.5.11 advisories had no public 3.x fix | full backend suite, static, dependency-check, final suite |

## Maven plugins (effective POM)

```
com.diffplug.spotless:spotless-maven-plugin:3.10.3
com.github.spotbugs:spotbugs-maven-plugin:4.10.4.1
io.github.ascopes:protobuf-maven-plugin:5.1.8
io.github.git-commit-id:git-commit-id-maven-plugin:9.2.0
io.grpc:protoc-gen-grpc-java:1.83.1
org.apache.maven.plugins:maven-antrun-plugin:3.2.0
org.apache.maven.plugins:maven-assembly-plugin:3.8.0
org.apache.maven.plugins:maven-clean-plugin:3.5.0
org.apache.maven.plugins:maven-compiler-plugin:3.15.0
org.apache.maven.plugins:maven-dependency-plugin:3.10.0
org.apache.maven.plugins:maven-deploy-plugin:3.1.4
org.apache.maven.plugins:maven-enforcer-plugin:3.6.3
org.apache.maven.plugins:maven-failsafe-plugin:3.5.6
org.apache.maven.plugins:maven-help-plugin:3.5.2
org.apache.maven.plugins:maven-install-plugin:3.1.4
org.apache.maven.plugins:maven-invoker-plugin:3.9.1
org.apache.maven.plugins:maven-jar-plugin:3.5.1
org.apache.maven.plugins:maven-javadoc-plugin:3.12.0
org.apache.maven.plugins:maven-pmd-plugin:3.28.0
org.apache.maven.plugins:maven-release-plugin:3.0.1
org.apache.maven.plugins:maven-resources-plugin:3.5.0
org.apache.maven.plugins:maven-shade-plugin:3.6.2
org.apache.maven.plugins:maven-site-plugin:3.12.1
org.apache.maven.plugins:maven-source-plugin:3.4.0
org.apache.maven.plugins:maven-surefire-plugin:3.5.6
org.apache.maven.plugins:maven-war-plugin:3.5.1
org.codehaus.mojo:build-helper-maven-plugin:3.6.1
org.codehaus.mojo:versions-maven-plugin:2.21.0
org.codehaus.mojo:xml-maven-plugin:1.2.1
org.cyclonedx:cyclonedx-maven-plugin:2.9.3
org.flywaydb:flyway-maven-plugin:12.4.0
org.graalvm.buildtools:native-maven-plugin:1.1.8
org.jacoco:jacoco-maven-plugin:0.8.15
org.jetbrains.kotlin:kotlin-maven-plugin:2.3.21
org.jooq:jooq-codegen-maven:3.21.7
org.liquibase:liquibase-maven-plugin:5.0.3
org.owasp:dependency-check-maven:13.0.0
org.springframework.boot:spring-boot-maven-plugin:4.1.1
```

## Maven dependencies (all scopes, 177 resolved incl. inherited/transitive)

```
ch.qos.logback:logback-classic:jar:1.5.38:compile -- module ch.qos.logback.classic
ch.qos.logback:logback-core:jar:1.5.38:compile -- module ch.qos.logback.core
com.fasterxml.jackson.core:jackson-annotations:jar:2.21:compile -- module com.fasterxml.jackson.annotation
com.fasterxml:classmate:jar:1.7.3:compile -- module com.fasterxml.classmate
com.github.docker-java:docker-java-api:jar:3.7.1:test -- module com.github.dockerjava.api [auto]
com.github.docker-java:docker-java-transport-zerodep:jar:3.7.1:test -- module com.github.dockerjava.transport.zerodep [auto]
com.github.docker-java:docker-java-transport:jar:3.7.1:test -- module com.github.dockerjava.transport [auto]
com.github.virtuald:curvesapi:jar:1.08:compile -- module com.github.virtuald.curvesapi [auto]
com.icegreen:greenmail-junit5:jar:2.1.14:test -- module greenmail.junit5 (auto)
com.icegreen:greenmail:jar:2.1.14:test -- module greenmail (auto)
com.jayway.jsonpath:json-path:jar:2.10.0:test -- module json.path [auto]
com.sun.istack:istack-commons-runtime:jar:4.1.2:runtime -- module com.sun.istack.runtime
com.tngtech.archunit:archunit-junit5-api:jar:1.5.1:test -- module com.tngtech.archunit.junit5.api [auto]
com.tngtech.archunit:archunit-junit5-engine-api:jar:1.5.1:test -- module com.tngtech.archunit.junit5.engineapi [auto]
com.tngtech.archunit:archunit-junit5-engine:jar:1.5.1:test -- module com.tngtech.archunit.junit5.engine [auto]
com.tngtech.archunit:archunit-junit5:jar:1.5.1:test -- module com.tngtech.archunit.junit5 [auto]
com.tngtech.archunit:archunit:jar:1.5.1:test -- module com.tngtech.archunit [auto]
com.vaadin.external.google:android-json:jar:0.0.20131108.vaadin1:test -- module android.json (auto)
com.zaxxer:HikariCP:jar:7.0.2:compile -- module com.zaxxer.hikari
com.zaxxer:SparseBitSet:jar:1.3:compile -- module com.zaxxer.sparsebitset [auto]
commons-codec:commons-codec:jar:1.21.0:compile -- module org.apache.commons.codec
commons-io:commons-io:jar:2.21.0:compile -- module org.apache.commons.io
commons-logging:commons-logging:jar:1.3.6:compile -- module org.apache.commons.logging
io.micrometer:micrometer-commons:jar:1.17.1:compile -- module micrometer.commons [auto]
io.micrometer:micrometer-core:jar:1.17.1:compile -- module micrometer.core [auto]
io.micrometer:micrometer-jakarta9:jar:1.17.1:compile -- module micrometer.jakarta9 [auto]
io.micrometer:micrometer-observation:jar:1.17.1:compile -- module micrometer.observation [auto]
jakarta.activation:jakarta.activation-api:jar:2.1.4:compile -- module jakarta.activation
jakarta.annotation:jakarta.annotation-api:jar:3.0.0:compile -- module jakarta.annotation
jakarta.inject:jakarta.inject-api:jar:2.0.1:runtime -- module jakarta.inject
jakarta.mail:jakarta.mail-api:jar:2.1.5:compile -- module jakarta.mail
jakarta.persistence:jakarta.persistence-api:jar:3.2.0:compile -- module jakarta.persistence
jakarta.transaction:jakarta.transaction-api:jar:2.0.1:compile -- module jakarta.transaction
jakarta.validation:jakarta.validation-api:jar:3.1.1:compile -- module jakarta.validation
jakarta.xml.bind:jakarta.xml.bind-api:jar:4.0.5:runtime -- module jakarta.xml.bind
junit:junit:jar:4.13.2:test -- module junit [auto]
net.bytebuddy:byte-buddy-agent:jar:1.18.11:test -- module net.bytebuddy.agent
net.bytebuddy:byte-buddy:jar:1.18.11:runtime -- module net.bytebuddy
net.java.dev.jna:jna:jar:5.18.1:test -- module com.sun.jna [auto]
net.minidev:accessors-smart:jar:2.6.0:test -- module accessors.smart (auto)
net.minidev:json-smart:jar:2.6.0:test -- module json.smart (auto)
org.antlr:antlr4-runtime:jar:4.13.2:compile -- module org.antlr.antlr4.runtime [auto]
org.apache.commons:commons-collections4:jar:4.5.0:compile -- module org.apache.commons.collections4
org.apache.commons:commons-compress:jar:1.28.0:compile -- module org.apache.commons.compress
org.apache.commons:commons-lang3:jar:3.20.0:compile -- module org.apache.commons.lang3
org.apache.commons:commons-math3:jar:3.6.1:compile -- module commons.math3 (auto)
org.apache.logging.log4j:log4j-api:jar:2.25.5:compile -- module org.apache.logging.log4j
org.apache.logging.log4j:log4j-to-slf4j:jar:2.25.5:compile -- module org.apache.logging.log4j.to.slf4j
org.apache.poi:poi-ooxml-lite:jar:5.5.1:compile -- module org.apache.poi.ooxml.schemas
org.apache.poi:poi-ooxml:jar:5.5.1:compile -- module org.apache.poi.ooxml
org.apache.poi:poi:jar:5.5.1:compile -- module org.apache.poi.poi
org.apache.tomcat.embed:tomcat-embed-core:jar:11.0.26:compile -- module org.apache.tomcat.embed.core
org.apache.tomcat.embed:tomcat-embed-el:jar:11.0.26:compile -- module org.apache.tomcat.embed.el
org.apache.tomcat.embed:tomcat-embed-websocket:jar:11.0.26:compile -- module org.apache.tomcat.embed.websocket
org.apache.xmlbeans:xmlbeans:jar:5.3.0:compile -- module org.apache.xmlbeans
org.apiguardian:apiguardian-api:jar:1.1.2:test -- module org.apiguardian.api
org.aspectj:aspectjweaver:jar:1.9.25.1:compile -- module org.aspectj.weaver [auto]
org.assertj:assertj-core:jar:3.27.7:test -- module org.assertj.core
org.awaitility:awaitility:jar:4.3.0:test -- module awaitility (auto)
org.checkerframework:checker-qual:jar:3.55.1:runtime -- module org.checkerframework.checker.qual
org.eclipse.angus:angus-activation:jar:2.0.3:runtime -- module org.eclipse.angus.activation
org.eclipse.angus:angus-mail:jar:2.0.5:runtime -- module org.eclipse.angus.mail
org.eclipse.angus:jakarta.mail:jar:2.0.5:test -- module jakarta.mail
org.flywaydb:flyway-core:jar:12.4.0:compile -- module flyway.core (auto)
org.flywaydb:flyway-database-postgresql:jar:12.4.0:compile -- module flyway.database.postgresql (auto)
org.glassfish.jaxb:jaxb-core:jar:4.0.9:runtime -- module org.glassfish.jaxb.core
org.glassfish.jaxb:jaxb-runtime:jar:4.0.9:runtime -- module org.glassfish.jaxb.runtime
org.glassfish.jaxb:txw2:jar:4.0.9:runtime -- module com.sun.xml.txw2
org.hamcrest:hamcrest:jar:3.0:test -- module org.hamcrest [auto]
org.hdrhistogram:HdrHistogram:jar:2.2.2:runtime -- module HdrHistogram (auto)
org.hibernate.models:hibernate-models:jar:1.1.1:runtime -- module org.hibernate.models [auto]
org.hibernate.orm:hibernate-core:jar:7.4.5.Final:compile -- module org.hibernate.orm.core [auto]
org.hibernate.validator:hibernate-validator:jar:9.1.3.Final:compile -- module org.hibernate.validator
org.jboss.logging:jboss-logging:jar:3.6.3.Final:compile -- module org.jboss.logging
org.jetbrains:annotations:jar:17.0.0:test -- module org.jetbrains.annotations [auto]
org.jspecify:jspecify:jar:1.0.1:compile -- module org.jspecify
org.junit.jupiter:junit-jupiter-api:jar:6.0.3:test -- module org.junit.jupiter.api
org.junit.jupiter:junit-jupiter-engine:jar:6.0.3:test -- module org.junit.jupiter.engine
org.junit.jupiter:junit-jupiter-params:jar:6.0.3:test -- module org.junit.jupiter.params
org.junit.jupiter:junit-jupiter:jar:6.0.3:test -- module org.junit.jupiter
org.junit.platform:junit-platform-commons:jar:6.0.3:test -- module org.junit.platform.commons
org.junit.platform:junit-platform-engine:jar:6.0.3:test -- module org.junit.platform.engine
org.mockito:mockito-core:jar:5.23.0:test -- module org.mockito
org.mockito:mockito-junit-jupiter:jar:5.23.0:test -- module org.mockito.junit.jupiter
org.objenesis:objenesis:jar:3.3:test -- module org.objenesis [auto]
org.opentest4j:opentest4j:jar:1.3.0:test -- module org.opentest4j
org.ow2.asm:asm:jar:9.7.1:test -- module org.objectweb.asm
org.postgresql:postgresql:jar:42.7.13:runtime -- module org.postgresql.jdbc [auto]
org.rnorth.duct-tape:duct-tape:jar:1.0.8:test -- module duct.tape (auto)
org.skyscreamer:jsonassert:jar:1.5.3:test -- module jsonassert (auto)
org.slf4j:jul-to-slf4j:jar:2.0.18:compile -- module jul.to.slf4j
org.slf4j:slf4j-api:jar:2.0.18:compile -- module org.slf4j
org.snakeyaml:snakeyaml-engine:jar:3.0.1:compile -- module org.snakeyaml.engine
org.springframework.boot:spring-boot-actuator-autoconfigure:jar:4.1.1:compile -- module spring.boot.actuator.autoconfigure [auto]
org.springframework.boot:spring-boot-actuator:jar:4.1.1:compile -- module spring.boot.actuator [auto]
org.springframework.boot:spring-boot-autoconfigure:jar:4.1.1:compile -- module spring.boot.autoconfigure [auto]
org.springframework.boot:spring-boot-data-commons:jar:4.1.1:compile -- module spring.boot.data.commons [auto]
org.springframework.boot:spring-boot-data-jpa:jar:4.1.1:compile -- module spring.boot.data.jpa [auto]
org.springframework.boot:spring-boot-flyway:jar:4.1.1:compile -- module spring.boot.flyway [auto]
org.springframework.boot:spring-boot-health:jar:4.1.1:compile -- module spring.boot.health [auto]
org.springframework.boot:spring-boot-hibernate:jar:4.1.1:compile -- module spring.boot.hibernate [auto]
org.springframework.boot:spring-boot-http-client:jar:4.1.1:compile -- module spring.boot.http.client [auto]
org.springframework.boot:spring-boot-http-converter:jar:4.1.1:compile -- module spring.boot.http.converter [auto]
org.springframework.boot:spring-boot-jackson:jar:4.1.1:compile -- module spring.boot.jackson [auto]
org.springframework.boot:spring-boot-jdbc:jar:4.1.1:compile -- module spring.boot.jdbc [auto]
org.springframework.boot:spring-boot-jpa:jar:4.1.1:compile -- module spring.boot.jpa [auto]
org.springframework.boot:spring-boot-mail:jar:4.1.1:compile -- module spring.boot.mail [auto]
org.springframework.boot:spring-boot-micrometer-metrics:jar:4.1.1:compile -- module spring.boot.micrometer.metrics [auto]
org.springframework.boot:spring-boot-micrometer-observation:jar:4.1.1:compile -- module spring.boot.micrometer.observation [auto]
org.springframework.boot:spring-boot-persistence:jar:4.1.1:compile -- module spring.boot.persistence [auto]
org.springframework.boot:spring-boot-restclient-test:jar:4.1.1:test -- module spring.boot.restclient.test [auto]
org.springframework.boot:spring-boot-restclient:jar:4.1.1:compile -- module spring.boot.restclient [auto]
org.springframework.boot:spring-boot-resttestclient:jar:4.1.1:test -- module spring.boot.resttestclient [auto]
org.springframework.boot:spring-boot-security-test:jar:4.1.1:test -- module spring.boot.security.test [auto]
org.springframework.boot:spring-boot-security:jar:4.1.1:compile -- module spring.boot.security [auto]
org.springframework.boot:spring-boot-servlet:jar:4.1.1:compile -- module spring.boot.servlet [auto]
org.springframework.boot:spring-boot-sql:jar:4.1.1:compile -- module spring.boot.sql [auto]
org.springframework.boot:spring-boot-starter-actuator:jar:4.1.1:compile -- module spring.boot.starter.actuator [auto]
org.springframework.boot:spring-boot-starter-data-jpa:jar:4.1.1:compile -- module spring.boot.starter.data.jpa [auto]
org.springframework.boot:spring-boot-starter-flyway:jar:4.1.1:compile -- module spring.boot.starter.flyway [auto]
org.springframework.boot:spring-boot-starter-jackson-test:jar:4.1.1:test -- module spring.boot.starter.jackson.test [auto]
org.springframework.boot:spring-boot-starter-jackson:jar:4.1.1:compile -- module spring.boot.starter.jackson [auto]
org.springframework.boot:spring-boot-starter-jdbc:jar:4.1.1:compile -- module spring.boot.starter.jdbc [auto]
org.springframework.boot:spring-boot-starter-logging:jar:4.1.1:compile -- module spring.boot.starter.logging [auto]
org.springframework.boot:spring-boot-starter-mail:jar:4.1.1:compile -- module spring.boot.starter.mail [auto]
org.springframework.boot:spring-boot-starter-micrometer-metrics:jar:4.1.1:compile -- module spring.boot.starter.micrometer.metrics [auto]
org.springframework.boot:spring-boot-starter-restclient-test:jar:4.1.1:test -- module spring.boot.starter.restclient.test [auto]
org.springframework.boot:spring-boot-starter-restclient:jar:4.1.1:compile -- module spring.boot.starter.restclient [auto]
org.springframework.boot:spring-boot-starter-security-test:jar:4.1.1:test -- module spring.boot.starter.security.test [auto]
org.springframework.boot:spring-boot-starter-security:jar:4.1.1:compile -- module spring.boot.starter.security [auto]
org.springframework.boot:spring-boot-starter-test:jar:4.1.1:test -- module spring.boot.starter.test [auto]
org.springframework.boot:spring-boot-starter-tomcat-runtime:jar:4.1.1:compile -- module spring.boot.starter.tomcat.runtime [auto]
org.springframework.boot:spring-boot-starter-tomcat:jar:4.1.1:compile -- module spring.boot.starter.tomcat [auto]
org.springframework.boot:spring-boot-starter-validation:jar:4.1.1:compile -- module spring.boot.starter.validation [auto]
org.springframework.boot:spring-boot-starter-webmvc-test:jar:4.1.1:test -- module spring.boot.starter.webmvc.test [auto]
org.springframework.boot:spring-boot-starter-webmvc:jar:4.1.1:compile -- module spring.boot.starter.webmvc [auto]
org.springframework.boot:spring-boot-starter:jar:4.1.1:compile -- module spring.boot.starter [auto]
org.springframework.boot:spring-boot-test-autoconfigure:jar:4.1.1:test -- module spring.boot.test.autoconfigure [auto]
org.springframework.boot:spring-boot-test:jar:4.1.1:test -- module spring.boot.test [auto]
org.springframework.boot:spring-boot-testcontainers:jar:4.1.1:test -- module spring.boot.testcontainers [auto]
org.springframework.boot:spring-boot-tomcat:jar:4.1.1:compile -- module spring.boot.tomcat [auto]
org.springframework.boot:spring-boot-transaction:jar:4.1.1:compile -- module spring.boot.transaction [auto]
org.springframework.boot:spring-boot-validation:jar:4.1.1:compile -- module spring.boot.validation [auto]
org.springframework.boot:spring-boot-web-server:jar:4.1.1:compile -- module spring.boot.web.server [auto]
org.springframework.boot:spring-boot-webmvc-test:jar:4.1.1:test -- module spring.boot.webmvc.test [auto]
org.springframework.boot:spring-boot-webmvc:jar:4.1.1:compile -- module spring.boot.webmvc [auto]
org.springframework.boot:spring-boot:jar:4.1.1:compile -- module spring.boot [auto]
org.springframework.data:spring-data-commons:jar:4.1.1:compile -- module spring.data.commons [auto]
org.springframework.data:spring-data-jpa:jar:4.1.1:compile -- module spring.data.jpa [auto]
org.springframework.security:spring-security-config:jar:7.1.1:compile -- module spring.security.config [auto]
org.springframework.security:spring-security-core:jar:7.1.1:compile -- module spring.security.core [auto]
org.springframework.security:spring-security-crypto:jar:7.1.1:compile -- module spring.security.crypto [auto]
org.springframework.security:spring-security-test:jar:7.1.1:test -- module spring.security.test [auto]
org.springframework.security:spring-security-web:jar:7.1.1:compile -- module spring.security.web [auto]
org.springframework:spring-aop:jar:7.0.9:compile -- module spring.aop [auto]
org.springframework:spring-aspects:jar:7.0.9:compile -- module spring.aspects [auto]
org.springframework:spring-beans:jar:7.0.9:compile -- module spring.beans [auto]
org.springframework:spring-context-support:jar:7.0.9:compile -- module spring.context.support [auto]
org.springframework:spring-context:jar:7.0.9:compile -- module spring.context [auto]
org.springframework:spring-core:jar:7.0.9:compile -- module spring.core [auto]
org.springframework:spring-expression:jar:7.0.9:compile -- module spring.expression [auto]
org.springframework:spring-jdbc:jar:7.0.9:compile -- module spring.jdbc [auto]
org.springframework:spring-orm:jar:7.0.9:compile -- module spring.orm [auto]
org.springframework:spring-test:jar:7.0.9:test -- module spring.test [auto]
org.springframework:spring-tx:jar:7.0.9:compile -- module spring.tx [auto]
org.springframework:spring-web:jar:7.0.9:compile -- module spring.web [auto]
org.springframework:spring-webmvc:jar:7.0.9:compile -- module spring.webmvc [auto]
org.testcontainers:testcontainers-database-commons:jar:2.0.5:test -- module testcontainers.database.commons (auto)
org.testcontainers:testcontainers-jdbc:jar:2.0.5:test -- module testcontainers.jdbc (auto)
org.testcontainers:testcontainers-junit-jupiter:jar:2.0.5:test -- module testcontainers.junit.jupiter (auto)
org.testcontainers:testcontainers-postgresql:jar:2.0.5:test -- module testcontainers.postgresql (auto)
org.testcontainers:testcontainers:jar:2.0.5:test -- module testcontainers (auto)
org.xmlunit:xmlunit-core:jar:2.11.0:test -- module org.xmlunit [auto]
org.yaml:snakeyaml:jar:2.6:compile -- module org.yaml.snakeyaml
tools.jackson.core:jackson-core:jar:3.1.5:compile -- module tools.jackson.core
tools.jackson.core:jackson-databind:jar:3.1.5:compile -- module tools.jackson.databind
tools.jackson.dataformat:jackson-dataformat-yaml:jar:3.1.5:compile -- module tools.jackson.dataformat.yaml
```

## Frontend direct dependencies (npm ls --depth=0)

| Package | Version |
|---|---|
| @eslint/js | 10.0.1 |
| @playwright/test | 1.63.0 |
| @testing-library/jest-dom | 6.10.0 |
| @testing-library/react | 16.3.3 |
| @testing-library/user-event | 14.6.7 |
| @types/node | 22.20.4 |
| @types/react | 19.3.0 |
| @types/react-dom | 19.3.0 |
| @vitejs/plugin-react | 5.2.0 |
| @vitest/coverage-v8 | 4.1.11 |
| eslint | 10.11.0 |
| eslint-plugin-react-hooks | 7.1.1 |
| eslint-plugin-react-refresh | 0.4.26 |
| globals | 16.5.0 |
| jscpd | 5.3.3 |
| jsdom | 26.1.0 |
| prettier | 3.9.9 |
| react | 19.3.0 |
| react-dom | 19.3.0 |
| typescript | 5.9.3 |
| typescript-eslint | 8.70.1 |
| vite | 6.4.3 |
| vitest | 4.1.11 |

Full transitive tree: 271 installed packages, exact versions in `frontend/package-lock.json`.
