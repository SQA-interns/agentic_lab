# Test strategy (as executed)

> Written in: phases 3, 5 and 6 · Source: `standards/testing.md` · Procedure: `skills/write-acceptance-tests`, `skills/unit-tests`, `skills/verify-release` · Agent: writes

## Test levels as executed

| Level | Component | Location | Public interface used | Run with |
|---|---|---|---|---|
| Acceptance | backend | `backend/src/test/java/si/konferenca/registration/acceptance/` | REST API over HTTP on a random port; PostgreSQL (`db-schema.sql`), JSON copy files (`registration-copy.schema.json`) and Mailpit's HTTP API as the observable outputs | `verify.sh <phase> be-test` |
| Acceptance | frontend | `frontend/tests/acceptance/` | the rendered page (`App`), accessible names of `ui-registration-form.json`; `fetch` mocked with `api.openapi.yaml` responses | `verify.sh <phase> fe-test` |
| End-to-end | frontend + backend | `frontend/tests/e2e/` | browser against the running local stack (`http://frontend:8080`), Mailpit API (`http://mailpit:8025`) | `verify.sh <phase> e2e` (needs `docker compose up`) |

Harness rules fixed by the freeze:

- Backend acceptance tests configure the backend only through the settings of `02_specification.md` §9 (environment-variable names as Spring properties). They also set `spring.datasource.*` to the same values, so the context starts on code that does not map `DB_URL` yet.
- Test configuration: `backend/src/test/resources/acceptance/conference-config.json`, a copy of `conference-config.example.json`. Test-mode token `test-pass`. Organizer test credentials live only in `TestEnvironment`; none is a real secret.
- Fault injection uses only the environment. JSON copy failure: the copy directory is replaced by a regular file. Database failure: a deferred constraint trigger fails the commit after the JSON copy was written. SMTP failure: SMTP points at a closed port. reCAPTCHA live path: a local mock of the verification endpoint (`CaptchaMock`, DoD-P05).
- "Nothing stored" means no database row and no file in the copy directory. "No email" means Mailpit is empty after a 3-second quiet period.
- End-to-end tests read options and consents from `GET /api/form-config` and use unique email addresses per run, so they work with any local configuration and with data from earlier runs.
- The helpers were exercised once against real data with temporary probes (database queries and triggers, Mailpit with a UTF-8 multipart mail and attachment, JSON copy files, POI workbook, reCAPTCHA mock, stand-in page). The probes were deleted before the freeze.

## Freeze run (phase 3)

Run on the bootstrap code (skeleton backend with Spring Security defaults; frontend page with a heading only). Logs: `02_output/logs/3_be-test.log`, `3_fe-test.log`, `3_e2e.log`.

| Component | Level | Tests | Passed | Failed |
|---|---|---|---|---|
| backend | acceptance | 86 | 0 | 86 |
| frontend | acceptance | 20 | 0 | 20 |
| frontend + backend | end-to-end | 9 | 0 | 9 |

Failure groups:

- backend, 86: `AssertionFailedError` on the first status assertion (expected 200/201/400/409/500/503, was 401: every endpoint is still behind the default security and none exists).
- frontend acceptance, 20: `Unable to find role="button" and name "Register"`: the form does not exist yet.
- end-to-end, 9: `getaddrinfo ENOTFOUND frontend`: the local stack (phase 4) does not exist yet. The run used a temporary empty `registration_default` network, removed afterwards.

Passing on bootstrap code: none. The skeleton's own unit test `src/App.test.tsx` (1, passing) is not an acceptance test.

Every AC has at least one test whose name contains its id (51 of 51 ACs).

## First complete run (phase 5, before any fix)

## Measures (phase 5)

| Component | Level | Tests passed / failed | Line coverage | Branch coverage | Mutation score (scope) | Tools | Log |
|---|---|---|---|---|---|---|---|

## Final run (phase 6)

| Component | Level | Tests passed / failed | Line coverage | Branch coverage | Mutation score (scope) | Tools | Log |
|---|---|---|---|---|---|---|---|
