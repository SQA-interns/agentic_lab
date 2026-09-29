# Architecture

> Owner: Architect · Read in: phases 0, 2, 4, 6, 7 · Agent: read-only

## Components

| Component | Type/folder | Platform/build ID | Bootstrap | Deployment/dependencies |
|---|---|---|---|---|
| backend | API; 02_output/backend | java / maven | No scaffold; generate pinned build and wrapper after preflight | Container; PostgreSQL, JSON volume, SMTP |
| frontend | web-app; 02_output/frontend | node / npm-vite | No scaffold; create React/TypeScript/Vite project after preflight | Container; backend REST |

Build/test/check/run commands and component READMEs are agent-authored once source
exists, per ES-05. Record them in output docs; do not modify this input or claim
untested commands work. Shared Compose/runtime files belong directly in 02_output.

## Constraints

- AR-01: Separate frontend and REST backend; authoritative validation/storage server-side.
- AR-02: Single backend deployable with registration, options, notifications and export
  modules; no module dependency cycles. Verify boundaries with ArchUnit.
- AR-03: PostgreSQL authoritative index; Flyway migrations. Separate persistent raw
  JSON volume. No production auto-DDL or ephemeral-only persistence.
- AR-04: Acceptance requires committed DB plus matching durable JSON. Validate/captcha
  before writes; stage/publish JSON atomically and commit DB/notification intent.
  Handle crash leftovers/reconciliation. These stores are not one ACID transaction.
- AR-05: Durable asynchronous email retry decouples SMTP availability from acceptance.
  Document retry/duplicate-delivery semantics; demonstrate recovery.
- AR-06: Organizer export uses Spring Security HTTP Basic, environment-supplied user
  and slow password hash, external HTTPS. No new accounts or IdP.
- AR-07: External startup catalog; change/restart without recompilation.
- AR-08: External nginx terminates TLS/Let's Encrypt, routes frontend and /api.
  App containers use internal HTTP; explicitly constrain forwarded-header trust.
- AR-09: Stable client request ID with uniqueness enforcement prevents duplicate
  acceptance; server-generated registration ID identifies DB/JSON/mail representations.

## Interfaces

| Interface | Style |
|---|---|
| Browser/frontend ↔ backend | REST JSON; registration, catalog, authorized Excel response |
| Backend ↔ PostgreSQL | JPA plus migration-managed schema |
| Backend ↔ durable JSON volume | atomic file publication and reconciliation |
| Backend ↔ SMTP | participant mail; organizer mail with JSON attachment |
| Browser/backend ↔ captcha provider | production challenge and server verification; deterministic local substitute |
| Proxy ↔ application | HTTPS externally, constrained internal HTTP |

Define concrete contracts in docs/02_contracts/ during phase 2; mechanically validate
them before tests rely on them. Endpoint/package/file-format details remain choices.
