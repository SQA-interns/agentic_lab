# Verification Report

## Status

**PASS** after one Verification → Fix → Verification loop and a successful post-merge container follow-up.

Verification began at 2026-09-21T13:09:45+02:00. The verifier re-read `USER_STORIES.md`, `PROJECT_CONSTRAINTS.md`, `FORM_SCHEMA.md`, all 50 Acceptance Criteria, and the complete technical specification before inspecting the final implementation. The two reference application URLs were also requested, but the browsing environment could not access them; the frozen repository form schema remained the authoritative field source as required by the inputs.

## Acceptance Criteria traceability

| Acceptance Criteria | Final evidence | Result |
| --- | --- | --- |
| AC-001–AC-005 | Exact external context contract; external REST happy path; frontend/backend invalid-value checks; Unicode/trim persistence checks | Pass |
| AC-006–AC-010 | Exact student context contract; student REST happy path with no optional selection; schema and Unicode checks | Pass |
| AC-011–AC-015 | Active/inactive option API checks; live configuration reload with unchanged fields; unknown/inactive rejection; stored option snapshots; empty selection | Pass |
| AC-016–AC-018 | Frontend component assertions prove confirmation is behind a successful backend result, error/retry text is present, and submit is disabled in flight | Pass |
| AC-019–AC-024 | Database/JSON correlation, backup-integrity, rollback-on-backup-failure, concurrent submissions, Unicode snapshots, and new-database-instance persistence tests | Pass |
| AC-025–AC-028 | Participant recipient/content MIME test; no outbox rows on failed persistence; durable worker retry without duplicate registration | Pass |
| AC-029–AC-032 | Organizer recipient/body and exact verified JSON attachment test; no jobs on failed persistence; worker retry/recovery tests | Pass |
| AC-033–AC-038 | Authorized/unauthorized XLSX API tests, empty workbook, variant/Unicode/selection columns, and formula neutralization | Pass |
| AC-039 | Strict variant schemas, unexpected-field rejection, field bounds, consent, and option validation tests | Pass |
| AC-040 | Parameterized ORM persistence, serializer/path design inspection, safe DOM text rendering, MIME construction, control-character rejection, and formula tests | Pass |
| AC-041 | Oversized, unsupported-media, malformed-JSON, unexpected-field, and length validation checks | Pass |
| AC-042 | Signed/tamper-evident expiring challenge, honeypot, nonce uniqueness, atomic database rate limit, and 429 test | Pass |
| AC-043 | Same-origin rejection and authenticated export/CORS design review | Pass |
| AC-044 | Bounded errors, safe structured logger, secret-free logs, safe request-ID syntax, Bandit, and hostile-input review | Pass |
| AC-045 | Production settings reject insecure secrets/origin/database/SMTP; security headers and organizer bearer auth are tested; Compose uses external secrets | Pass |
| AC-046–AC-047 | Responsive/mobile CSS, semantic labels/groups, live regions, inline errors, focus indicators, keyboard-native controls, and reduced-motion component checks | Pass |
| AC-048 | Docker image build, non-root runtime startup, live/readiness/frontend HTTP probes, Compose validation, production-settings import, Python wheel build, and operational documentation | Pass |
| AC-049 | Readiness dependency probes, persistence failure behavior, transaction compensation, retryable outbox, and restart persistence | Pass |
| AC-050 | Safe structured events for form, validation, registration, rate limiting, export authorization/outcome, backup reconciliation, email, and readiness; request correlation headers | Pass after Fix Loop 1 |

First verification evaluation: **49/50 AC passed**; AC-050 was incomplete. Final verification: **50/50 AC passed**.

## Verification findings and fix loop

| ID | Severity | Affected US/AC | Description | Fix performed | Re-verification result |
| --- | --- | --- | --- | --- | --- |
| V-001 | Minor | General quality | Ruff found import ordering, unused test imports, line length, formatting, and test-fixture false positives. | Formatted all Python sources, removed unused imports, sorted imports, and narrowly annotated non-production fixture secrets. | Ruff lint and format checks pass with 0 errors and 0 warnings. |
| V-002 | Major | US-001–US-008 / AC-050 | Operational events did not cover several relevant successful and rejected form/registration/export outcomes. | Added participant-data-free structured outcome logging for form context, request validation, anti-automation/media/size/rate rejection, idempotency conflict, and export authorization/success. | Code review plus 33-test suite passes; no participant values are logged. |
| V-003 | Minor | AC-041 / Specification 5 | Form context lacked `Cache-Control: no-store`, and malformed JSON returned 422 rather than the specified 400. | Added no-store response header and distinct bounded `invalid_json` 400 mapping; added contract assertions. | API contract tests pass. |
| V-004 | Minor | US-006 / AC-026, AC-045 | Participant email omitted the configured conference name, and production settings permitted unauthenticated SMTP despite the specification. | Read conference name from the integrity-verified registration backup for confirmation content and require authenticated STARTTLS SMTP in production. | MIME and production-configuration tests pass. |
| V-005 | Minor | US-005 / AC-024, Specification 7 | Startup did not reconcile interrupted temporary or unreferenced JSON files as specified. | Added startup reconciliation that preserves referenced backups and moves incomplete/unreferenced files into a restrictive quarantine. | Unit quarantine test and complete suite pass. |
| V-006 | Minor | AC-044–AC-045 | Caller request IDs accepted all ASCII, broader than the specified syntactically safe correlation format. | Restricted caller IDs to 1–100 characters from `[A-Za-z0-9._:-]`; unsafe values receive generated UUIDs. | Security contract test passes; Bandit reports no findings. |
| V-007 | Minor | AC-048 | The first observable standalone container run failed because the non-root runtime could not create its default `/app/data` directory; the image prepared only Compose's `/data/backups` path. | Pre-created and assigned the `app` user ownership of both `/app/data/backups` and `/data/backups`; added a deployment regression contract. | Rebuilt image `sha256:e00c8fd67a1efa22a2e4ca4336e43e8d7f606b6a0d896b16b201e49b61f2a75b` stays running as `app`; `/health/live`, `/health/ready`, and `/` return HTTP 200. |

Fix Loop 1 ran from 2026-09-21T13:10:45+02:00 to 2026-09-21T13:20:05+02:00. After Docker became available, the requested post-merge follow-up found and resolved V-007 on 2026-09-21. There are no unresolved Critical, Major, or Minor findings.

## Automated verification results

| Check | Result |
| --- | --- |
| Complete test suite | 34 passed, 0 failed in 6.27 seconds |
| Combined line/branch coverage | 84.15178571428571% (`experiment/coverage-final.json`) |
| Unit-only coverage | 55.580357142857146% |
| API/integration-only coverage | 78.125% |
| Ruff lint | Pass; 0 errors, 0 warnings |
| Ruff formatting | Pass; 29 Python files formatted |
| mypy strict type check | Pass; 0 errors in 21 application modules |
| JavaScript syntax (`node --check`) | Pass |
| Bandit static security scan | Pass; 0 findings across 1,909 analyzed LOC |
| Production dependency audit | Pass; 0 known vulnerabilities across the resolved production dependency set |
| Alembic migration apply/check | Pass; initial migration applies and no model drift is detected |
| Docker Compose validation | Pass |
| Python wheel build | Pass; `conference_registration-1.0.0-py3-none-any.whl` |
| Production settings/application import | Pass; API loads with documentation disabled |
| Container image build | Pass; image `sha256:e00c8fd67a1efa22a2e4ca4336e43e8d7f606b6a0d896b16b201e49b61f2a75b` |
| Container runtime smoke test | Pass; non-root `app` runtime remains running and live, readiness, and frontend endpoints return HTTP 200 through Uvicorn |

The Docker daemon was unavailable during the original Verification phase, so no success was inferred then. Once the daemon became available, the image built, the first standalone runtime probe exposed V-007, and the rebuilt image passed the observed build and runtime checks above. Host-loopback access from the restricted shell was unavailable, so the HTTP probes ran inside the container against Uvicorn's loopback listener.

## Architecture conformance

- Fixed schemas and normalization remain in the domain package and import no API, database, SMTP, filesystem, or workbook module.
- Registration orchestration is centralized in the application service; API routes delegate persistence/backup behavior rather than implementing it.
- Database models, JSON storage, SMTP, workbook creation, and throttling remain isolated infrastructure adapters.
- Web composition and worker composition are separate entry points using the same image and durable database/outbox design.
- Configuration changes do not alter fixed fields; immutable option/consent snapshots isolate historical records.
- No forbidden payment, attendee-account, capacity, badge, organizer-editing, or configuration-UI functionality was introduced.

Deterministic AST import analysis found 21 Python modules, 39 internal dependency edges, average efferent coupling 1.8571428571428572, and **0 dependency cycles**. Raw per-module Ca/Ce and edges are recorded in `experiment/run-log.json`. The public HTTP surface is eight route registrations: two REST resource areas plus export, two health endpoints, and three frontend paths. No file moves/renames or architectural refactorings occurred.

## Requirement and regression review

- Missing requirements after re-verification: 0
- Incorrectly implemented requirements after re-verification: 0
- Unrequested functionality: 0
- Requirement violations: 0
- Specification violations after re-verification: 0
- Regressions detected during original verification: 0; post-merge container findings: 1, resolved
- Security findings: 2 manual hardening findings (1 Medium, 1 Low), both resolved; automated findings: 0

At the close of the original Verification phase, all required artefacts existed except `RELEASE_NOTES.md` and `experiment/run-summary.md`, which were then created in Merge. Those artefacts now also include the post-merge container follow-up; verification remains passing.
