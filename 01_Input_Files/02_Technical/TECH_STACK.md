# Frozen Technology Stack

The technology stack in this file is fixed for all experimental runs.

Agents must not replace technologies listed here with alternatives.

## Backend

- Java 21 LTS
- Spring Boot 3.x
- Maven with Maven Wrapper

Required Spring capabilities:

- Spring Web
- Spring Data JPA
- Bean Validation
- Spring Mail
- Spring Boot Actuator

Spring Security may be used for HTTP security controls such as headers,
CORS and access control where required.

## Database

- PostgreSQL 16
- Spring Data JPA / Hibernate
- Flyway for database migrations

## Frontend

- React
- TypeScript
- Vite

Frontend and backend are separate applications communicating through
REST.

## Email

Development and test:

- local SMTP server

Production:

- external SMTP configuration

Email infrastructure must be configurable through environment settings.

## Anti-automation

Google reCAPTCHA v2 is the selected anti-automation technology.

Verification must occur on both:

- frontend interaction;
- backend token validation.

In local development and automated testing, reCAPTCHA verification must
use a deterministic test mode. Production mode must perform real
server-side reCAPTCHA verification. The test mode must be enabled only
through environment-specific configuration and must never be enabled by
default in production.

Automated tests, local development and containerized Definition of Done
checks must not depend on calling the live Google reCAPTCHA service.

## Frozen implementation scaffold

`IMPLEMENTATION_ROOT` contains a tooling scaffold only:

- Maven / Spring Boot bootstrap and configured analysis plugins;
- React / Vite / TypeScript bootstrap and configured frontend tools;
- Docker Compose service placeholders.

The scaffold does **not** freeze software architecture, package layout,
REST design, persistence mapping or business behaviour.

ArchUnit is available as a test dependency. Agents must not inherit
pre-written layering tests from the scaffold. After Specification, the
agent writes ArchUnit rules that verify the architecture they declared.

## Backend testing

- JUnit 5
- Mockito
- Spring Boot Test
- MockMvc or WebTestClient
- Testcontainers for PostgreSQL integration tests
- JaCoCo for coverage

## Frontend testing

- Vitest
- React Testing Library
- Playwright for end-to-end verification
- coverage provider supported by Vitest

## Backend static analysis

- Spotless
- SpotBugs
- PMD
- PMD CPD
- ArchUnit

## Frontend static analysis

- ESLint
- typescript-eslint
- Prettier
- TypeScript compiler (`tsc --noEmit`)
- jscpd

## Security analysis

- Semgrep
- OWASP Dependency-Check for backend dependencies
- npm audit for frontend dependencies

## Containers

- backend container
- frontend container
- PostgreSQL container

Docker Compose is used for local container orchestration.