# Constraints

> Owner: Architect, Security officer, QA lead · Fill: once per project · Read in: phases 0, 2, 4, 6, 7 · Agent: read-only

How the product must be built, secured and checked. Adds to `general/standards/`; repeats nothing from it. Ids: `AR`, `SR`, `NFR`, `DoD-Pnn`.

## Architecture

### Components

Each component gets its own folder and README in `02_output/`. Versions come only from `project/stack.md`; reference its ids, never repeat a version here.

```yaml
components:
  - name: backend
    type: api
    folder: 02_output/backend
    platform: java
    build: maven-wrapper
    bootstrap: Maven wrapper plus a hand-written pom.xml whose parent is org.springframework.boot:spring-boot-starter-parent; root package si.konferenca.registration
    commands:          # ES-05; filled in phase 0 if left empty
      build:
      test:
      check:
      run:
    depends_on: []
    deploys_as: container image (eclipse-temurin base, non-root)
  - name: frontend
    type: web-app
    folder: 02_output/frontend
    platform: node
    build: vite
    bootstrap: hand-written package.json with the exact versions of `project/stack.md` and a committed package-lock.json (no project generator)
    commands:
      build:
      test:
      check:
      run:
    depends_on: [backend]
    deploys_as: container image (static files served by nginx)
```

The local stack (both components, PostgreSQL, mail catcher) is started with `02_output/docker-compose.yml`.

### Architecture constraints

Checkable rules the code must respect (layering, allowed dependencies, patterns). Checked automatically in phase 6 where the stack allows (DoD-04).

| ID | Constraint |
|---|---|
| AR-01 | The frontend talks to the backend only through the REST API under `/api`. |
| AR-02 | The backend's internal architecture is not prescribed: the agent declares and justifies it in `docs/02_specification.md` and then writes ArchUnit rules that check exactly that declaration. |
| AR-03 | No package cycles in the backend (ArchUnit slice check). |
| AR-04 | Conference options change through configuration only, without code changes and without changing the fixed participant fields (BR-01). The mechanism is decided in phase 2. |
| AR-05 | A success response is sent only after both the database row and the JSON copy are written (BR-06, BR-07). |
| AR-06 | The database schema changes only through Flyway migrations (ES-08). |
| AR-07 | The frontend contains no secret; the reCAPTCHA site key is its only key and comes from configuration. |

### Interfaces

Every interface between components or with external systems is defined as a contract in `docs/02_contracts/` in phase 2.

| Interface | Between | Style (e.g. REST, gRPC, events, file, UI) |
|---|---|---|
| Registration form | participant and frontend | UI |
| Options, registration, export | frontend or organizer and backend | REST (OpenAPI) |
| Registration storage | backend and PostgreSQL | SQL (JPA) |
| JSON copy | backend and persistent volume | file (JSON schema) |
| Conference options | configuration and backend | file or configuration (schema defined in phase 2) |
| Emails | backend and SMTP server | SMTP |
| Anti-automation | frontend, backend and Google reCAPTCHA | JavaScript widget, HTTPS verify call |

## Security

### Verification standard

| Component | Standard | Level | Reason if above the default |
|---|---|---|---|
| backend | OWASP ASVS 5.0 | Level 1 | |
| frontend | OWASP ASVS 5.0 | Level 1 | |

Level 1 is a deliberate choice: the only authentication is one organizer role for one export operation, and the personal data is contact and study data, not sensitive data. Where a requirement below is more specific than ASVS, it applies as well.

### Authentication and authorization

- Participants are anonymous; there are no participant accounts.
- One organizer role may export registrations (BR-08). The mechanism is decided in phase 2, using the organizer credentials from `project/secrets.env.example`; identity providers are out of scope.
- Everything else (options, registration) is public but protected by SR-01 and SR-03.

### Personal data

| Data item | Purpose | Retention | Shown/exported to |
|---|---|---|---|
| First and last name | identify the participant | `OQ-06` | organizers (email, export) |
| Email | confirmation and contact | `OQ-06` | participant, organizers |
| Organization / institution | attendance records | `OQ-06` | organizers |
| Study institution, programme, student ID | student registration | `OQ-06` | organizers |
| Selected options | plan workshops, events and meals | `OQ-06` | participant (confirmation), organizers |
| Consents given, with timestamp | proof of consent (SB-14) | `OQ-06` | organizers |

### Security requirements

| ID | Requirement |
|---|---|
| SR-01 | Every registration is protected by Google reCAPTCHA v2 and verified on the backend; the frontend check alone is never enough. |
| SR-02 | reCAPTCHA test mode is enabled only by environment configuration, never by default; production refuses to start with test mode on or with empty keys. |
| SR-03 | The registration and export endpoints are rate limited, and request bodies have a size limit. |
| SR-04 | Option identifiers are checked against the active configured set (BR-04). |
| SR-05 | Email content is generated safely: user input cannot inject headers or markup. |
| SR-06 | Organizer credentials are never accepted over plain HTTP, except on localhost. |
| SR-07 | The export and the organizer email expose only registration data, never internal fields or other system data. |

## Quality

### Non-functional requirements

| ID | Category (performance, availability, accessibility, localisation, usability, …) | Requirement | How it is checked |
|---|---|---|---|
| NFR-01 | Localisation | Names and other text with Slovenian characters (č, š, ž) survive the form, database, JSON copy, emails and Excel export unchanged. | an end-to-end test with such input |
| NFR-02 | Reliability | Stored registrations and JSON copies survive recreation of every container. | runtime demonstration: `docker compose down` then `up`, data still present |
| NFR-03 | Usability | The frontend validates fields before submission and shows the backend's field errors next to the fields. | component tests |
| NFR-04 | Availability | The backend reports health and readiness, and the containers use them in health checks. | runtime demonstration |

### Thresholds

| Measure | Threshold or "record only" |
|---|---|
| Line coverage | record only |
| Branch coverage | record only |
| Mutation score | record only; a surviving mutant that a test should plausibly have caught is a finding |

### Additional done criteria

| ID | Criterion | Evidence |
|---|---|---|
| DoD-P01 | Against the running local stack: one external and one student registration succeed. | runtime demonstration log |
| DoD-P02 | Each registration is in PostgreSQL and has its JSON copy. | database query, file listing |
| DoD-P03 | The participant email and the organizer email (with the JSON attachment) arrive in Mailpit. | Mailpit API output |
| DoD-P04 | The organizer downloads a valid Excel workbook; the same request without organizer access is refused. | response codes, opened workbook |
| DoD-P05 | The production reCAPTCHA code path is tested against a mocked verification endpoint, including a rejected token. | test report |

## Overrides

Lines `Overrides: <ID>, reason` that replace a general item for this project. None.
