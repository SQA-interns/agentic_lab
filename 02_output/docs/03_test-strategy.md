# Test strategy (as executed)

> Written in: phases 3, 5 and 6 · Source: `general/standards.md` ("Tests"), phase cards 3 and 5 · Agent: writes

## Test levels as executed

| Level | Location | Interface | Environment | Run with |
|---|---|---|---|---|
| Acceptance (frozen) | `backend/src/test/java/si/konferenca/registration/acceptance/` | REST API over HTTP on a random port; read-only checks of database rows, JSON copy files, Mailpit API, export workbook | Testcontainers `postgres:16.15-alpine` and `axllent/mailpit:v1.31.1` (chaos enabled), JSON copies in a temp directory, options file = `docs/02_contracts/conference-options.example.json`, reCAPTCHA test mode; configured only through the environment names of specification section 5 | `verify.sh <phase> be-test` |
| End-to-end (frozen) | `frontend/e2e/` | browser on the page (roles and labels of `registration-form.ui.json`), Mailpit API, organizer export over `/api` | local stack (`docker compose`), Playwright container in the compose network; organizer keys via `secrets.sh run` | `verify.sh <phase> e2e` |
| Unit / integration | other test paths | — | — | phase 5 |

Harness choices: failure injection without production hooks: JSON copy directory replaced by a plain file (AC-004-03, AC-005-03), a `BEFORE INSERT` trigger that raises (AC-005-03), Mailpit chaos refusing every recipient (AC-006-03, AC-007-03); restarts and other configurations start a second application instance from `RegistrationApplication` (AC-003-03, AC-003-04, AC-005-04, AC-008-04 on a fresh database).

Harness probes (phase 3, deleted before the freeze): backend helpers (`Mailpit`, `Database`, `JsonCopies`, `Workbook`, `RunningApp`, `ApiClient`, base-class problem parser) 6 tests passed (`out/logs/3_be-harness-probe.log`); end-to-end helpers (`contract`, `mailpit`, `xlsx` on a POI-written workbook, `organizer` against a stub server, page object on the served bootstrap page) 1 test passed (`out/logs/3_e2e-harness-probe.log`, workbook from `out/logs/3_be-xlsx-probe-writer.log`).

## Coverage of acceptance criteria

| Story | Acceptance tests (backend) | End-to-end tests |
|---|---|---|
| US-001 | AC-001-01 … AC-001-14 (all) | AC-001-01, -02, -03, -05, -06 (NFR-01), -11, -12, -14 |
| US-002 | AC-002-01 … AC-002-11 (all) | AC-002-01, -02, -08 |
| US-003 | AC-003-01 … AC-003-04 | AC-003-01, -02 |
| US-004 | AC-004-01 … AC-004-03 | AC-004-01, -02 |
| US-005 | AC-005-01, -02, -03 (JSON and database failure), -04 | — |
| US-006 | AC-006-01 … AC-006-03 | (participant email in AC-001-06) |
| US-007 | AC-007-01 … AC-007-03 | (organizer email and attachment in AC-001-06) |
| US-008 | AC-008-01 … AC-008-06 | (export in AC-001-06) |

## Phase 3 run on bootstrap code (before any production code)

Acceptance: 65 run, 0 passed, 65 failed, 0 errors (`out/logs/3_be-test.log`). End-to-end: 15 run, 0 passed, 15 failed (`out/logs/3_e2e.log`; run against the bootstrap page served by nginx in a probe network). No test passes on bootstrap code.

| Failures | Tests | Reason (behavioural) |
|---|---|---|
| 33 | AC-001-02, -03, -05, -07, -08, -10, -12, -13, AC-002-02, -03, -05, -07, -10, -11, AC-004-02, AC-006-02, AC-007-02 (with parameter variants) | expected 400, got 403: registration endpoint not built (bootstrap denies all) |
| 20 | AC-001-01, -04, -06, -09, -14, AC-002-01, -04, -06, -09, AC-003-04, AC-004-01, AC-005-01, -02, -04, AC-006-01, AC-007-01, AC-008-01, -02, -03, -05 | expected 201, got 403: registration endpoint not built |
| 5 | AC-001-11, AC-002-08, AC-003-01, -02, -03 | expected 200, got 403: form configuration endpoint not built |
| 2 | AC-004-03, AC-005-03 (JSON copy failure) | expected 503, got 403: storage not built |
| 3 | AC-005-03 (database failure), AC-006-03, AC-007-03 | precondition "application serves the form": expected 200, got 403 |
| 2 | AC-008-04, AC-008-06 | organizer token: expected 200, got 403: token endpoint not built |
| 12 | end-to-end form tests | registration type choice not on the page (timeout waiting for the radio group) |
| 3 | end-to-end AC-001-14, AC-002-08, AC-003-01 | `/api/form-config` 404: not built |

## First complete run (before any fix)

## Final run (phase 6)
