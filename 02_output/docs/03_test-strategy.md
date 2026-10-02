# Test strategy (as executed)

> Written in: phases 3, 5 and 6 · Source: `general/standards.md` ("Tests"), phase cards 3 and 5 · Agent: writes

## Test levels as executed

| Level | Location (under `02_output/`) | Interface used | Runs with | Tests |
|---|---|---|---|---|
| Acceptance, backend | `backend/src/test/java/si/konferenca/registration/acceptance/`, fixtures in `backend/src/test/resources/acceptance/` | HTTP (`openapi.yaml`), SQL (`registration-schema.sql`), JSON copy files, Mailpit API, settings of specification section 5 | `verify.sh <phase> backend-test` (JUnit, Testcontainers PostgreSQL and Mailpit; backend started in-process through its entry point) | 79 |
| Acceptance, frontend | `frontend/src/acceptance/` | rendered page by role and label (`registration-form.ui.json`); network replaced by an in-memory backend that follows `openapi.yaml` | `verify.sh <phase> frontend-test` (Vitest, Testing Library, jsdom) | 29 |
| End-to-end | `frontend/e2e/` | browser against the running local stack, Mailpit API, export over HTTP | `verify.sh <phase> e2e` (Playwright container on the stack's network, D-15) | 3 |
| Unit / integration | phase 5 | | | |

- No test imports an application class except the two entry points (`RegistrationApplication`, `App`), used only to start the component.
- No live external service: anti-automation runs in test mode, mail goes to Mailpit.
- Failure cases are produced from outside: the JSON copy directory is replaced by a file, the `registration` table is renamed, the SMTP port is closed, the backend is restarted on the same database and directory.
- Every rejection test also checks that the database, the JSON copies and the mailbox are unchanged.

### AC to test

| AC | Backend acceptance | Frontend acceptance | End-to-end |
|---|---|---|---|
| AC-001-01, AC-002-01 | yes | yes | yes |
| AC-001-02, AC-001-14, AC-002-11, AC-002-12 | (AC-002-02: yes) | yes | |
| AC-001-03, AC-002-03, AC-001-05, AC-002-05 | yes | yes | AC-001-03 |
| AC-001-04, AC-001-06, AC-001-11, AC-001-13, AC-002-04, AC-002-06, AC-002-07 | yes | | |
| AC-001-07, AC-001-08, AC-001-09, AC-001-10, AC-001-12, AC-002-08, AC-002-09, AC-002-10 | yes | yes | |
| AC-003-01, AC-003-02, AC-003-03, AC-003-05 | yes | yes | AC-003-01 |
| AC-003-04 | yes | | |
| AC-004-01, AC-004-02 | | yes | yes |
| AC-004-03 | yes | yes | |
| AC-005-01 .. AC-005-05 | yes | | |
| AC-006-01, AC-006-02, AC-007-01, AC-007-02 | yes | | yes |
| AC-006-03, AC-007-03, AC-007-04 | yes | | |
| AC-008-01, AC-008-02, AC-008-05 | yes | | yes |
| AC-008-03, AC-008-04, AC-008-06 | yes | | |

## Phase 3 run (before any production code)

| Suite | Passed | Failed | Reason of the failures | Log |
|---|---|---|---|---|
| Backend acceptance | 0 | 79 | every test stops at its first HTTP status check: expected 201 (28), 400 (45) or 200 (6), the bootstrap answers 401 | `logs/3_backend-test.log` |
| Frontend acceptance | 0 | 29 | the registration type selector (`radiogroup` "Vrsta prijave") is not rendered | `logs/3_frontend-test.log` |
| End-to-end | 0 | 3 | the registration type radio button is not on the page (bootstrap frontend served by the pinned nginx image) | `logs/3_e2e.log` |

- No test passes on bootstrap code. No failure is a build, configuration or harness error: all suites compile, start their containers and reach the component.
- The helpers that run after the first failing check (database, mailbox, workbook and ZIP readers, the in-memory backend, the failure injections) were exercised once with temporary probe tests that were not kept.
- Tests are formatted and linted: `logs/3_backend-check.log`, `logs/3_frontend-check.log`.

## First complete run (before any fix)

## Final run (phase 6)
