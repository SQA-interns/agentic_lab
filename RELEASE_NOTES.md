# Release Notes — Conference Registration System 1.0.0

## Participant registration

- Added responsive external-participant and student registration forms using the immutable fixed field schemas.
- Added configurable workshops, events, meals, other activities, and mandatory consent definitions without coupling programme changes to fixed participant fields.
- Added frontend and authoritative backend validation with Unicode normalization, actionable field errors, safe retry, idempotent submissions, and backend-gated success confirmation.

## Reliable processing and notifications

- Added transactional relational registration storage with atomic, checksummed JSON backups on persistent storage.
- Added startup backup reconciliation and recovery-oriented operational documentation.
- Added a durable email outbox and worker for participant confirmation and organizer notifications. Organizer messages include the integrity-verified registration JSON attachment; failures remain retryable without duplicating registrations.

## Organizer export

- Added bearer-protected Excel export of the current registration set, including both participant variants, immutable configured-option snapshots, consent evidence, and Unicode content.
- Added spreadsheet-formula neutralization for participant-controlled cells and a valid header-only workbook when no registrations exist.

## Security and operations

- Added strict schemas, bounded JSON requests, parameterized persistence, same-origin enforcement, trusted-host checks, signed expiring challenges, honeypot checks, nonce replay prevention, database-backed throttling, safe correlation IDs, security headers, structured privacy-safe logs, and production configuration validation.
- Added Alembic migrations, a multi-stage non-root container image, PostgreSQL-backed Compose deployment, read-only container filesystems, external secrets/volumes, health endpoints, and separate migration/web/worker services.
- Added pinned production/development dependencies and documentation for configuration changes, export access, SMTP recovery, backup/restore, deployment, and secret rotation.

## Quality evidence

- 50/50 Acceptance Criteria passed final traceability review.
- 34/34 automated tests pass across unit, API/contract, integration, component/acceptance, security, regression, edge, failure/recovery, and deployment-contract levels.
- Combined line-and-branch coverage is 84.15178571428571%.
- Ruff formatting/lint, strict mypy, JavaScript syntax, Bandit, production dependency audit, migration parity, Compose validation, Python package build, and production-settings import pass.
- The Docker image builds successfully and its non-root runtime passes live, readiness, and frontend HTTP smoke checks. A follow-up fixed ownership of the standalone image's default `/app/data` path and added a regression contract.
