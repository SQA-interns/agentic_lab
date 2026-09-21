# Test Strategy

**Phase 4 artefact.** Written after the implementation was complete, as the process requires.

Each level below states *why it exists* — what it can demonstrate that no cheaper level can.
The same justification appears at the top of every test file, next to the tests it explains.

## Source artefact per level

| Level | Written from | Location |
| --- | --- | --- |
| Acceptance | User Stories + Acceptance Criteria | `backend/tests/acceptance/` |
| End-to-end | User Stories + Acceptance Criteria | `e2e/specs/` |
| REST API / contract | Specification § 4 + Acceptance Criteria | `backend/tests/api/`, `frontend/tests/api.test.ts` |
| Integration | Specification § 6 + Acceptance Criteria | `backend/tests/integration/` |
| Component | Specification + implementation | `backend/tests/component/`, `frontend/tests/registrationPage.test.ts` |
| Unit | Specification + implementation | `backend/tests/unit/`, `frontend/tests/validation.test.ts` |
| Security | Project constraints + Specification § 11 | `backend/tests/security/` |
| Failure / recovery | Specification § 6.2, § 10 | `backend/tests/integration/failureRecovery.test.ts` |

## Why each level was selected

**Unit** — normalisation, the field-rule table, reference generation, form tokens, email
escaping, the options catalogue and environment parsing are pure or near-pure functions with
large input spaces. Only at this level can the boundary cases be covered exhaustively and
cheaply: NFC composition of Slovenian characters, exotic whitespace, control characters,
exactly-at versus one-over maximum length, every rejection reason of the anti-automation
token with an injected clock. Driving the same cases through HTTP would be far slower and
would obscure which rule fired.

**Component** — the repository, the JSON backup store, the Excel writer and the Nodemailer
adapter are tested against the real collaborator (a temporary SQLite file, a temporary
directory, a workbook read back with the same library, the `json` transport). Substituting a
mock here would only prove the mock was called; the behaviour that matters — the unique
constraint, the composite primary key, the atomic rename, the OOXML output — lives in the
collaborator. The frontend page component is tested in jsdom because the confirmation rule,
the error rendering, the double-submit guard and the unchecked consent are properties of the
rendered document.

**Integration** — US-005's guarantees span three collaborators at once: a database row, a
file on disk and two emails. No single-component test can show "either both artefacts exist
or neither does", and the HTTP contract cannot show it either, because from outside the only
evidence is a `201`. These tests assert on what is actually on disk and what was actually
sent.

**REST API / contract** — the frontend branches on status code and error code; the whole of
AC-004 rests on those distinctions being real. These tests drive the production Express
application over HTTP and pin every documented status, code and envelope. The frontend's own
contract tests feed the client each documented response and assert which branch it takes, so
both sides of the contract are checked against the same document.

**Acceptance** — one suite written from the stories rather than the code, asserting through
the public API and the artefacts a user would inspect: a stored file, an email, a workbook.
This is the suite that fails if the system is rebuilt differently but wrongly.

**End-to-end** — the only level that runs a real browser against the real backend. It is what
actually demonstrates AC-004-01 ("the confirmation appears only after a success response")
and AC-G-12 (no horizontal overflow at 375 px and 1280 px), neither of which can be observed
without a browser doing layout and a real network round trip. Run at both viewport sizes.

**Security** — attacker-perspective tests that submit hostile input through the public API and
then inspect what reached storage and what came back: script payloads, SQL metacharacters,
CRLF, null bytes, traversal in an option identifier, oversized bodies, mass assignment, plus
the header, CORS and disclosure checks. A control is only real at the boundary an attacker
uses.

**Negative and edge cases** — distributed across the levels rather than isolated, so each
negative case sits next to the positive case it contrasts with.

**Failure and recovery** — AC-005-04 and AC-005-07 are promises about what happens when
something breaks or the process stops. They are demonstrated by actually breaking the backup
directory and by actually rebuilding the application against the same data directory.

**Regression** — every fixed defect is covered by a test at the level where it occurred;
the whole suite runs on every change, which is what turns it into a regression suite.

## Levels deliberately not used

* **Contract tests against a broker (Pact and similar)** — there is one consumer and one
  provider in one repository, both built together. The shared source of truth is
  `docs/specification.md` § 4, and both sides are tested against it.
* **Load and performance tests** — no performance requirement is stated in the User Stories
  or the Project Constraints, and inventing one would be unrequested functionality.
* **Mutation testing** — valuable, but no requirement asks for it and it would materially
  change the run's shape.

## What is not covered by automated tests

| Area | Why | How it is covered instead |
| --- | --- | --- |
| Real SMTP delivery | Needs a live mail server and a real mailbox | The adapter is tested against Nodemailer's `json` transport, which composes the real message; delivery itself is an operational concern |
| Container build and `docker compose up` | Docker is not available in this environment | Recorded as an unverified item in `docs/verification-report.md` |
| Visual appearance | No stated visual requirement | Layout is checked structurally (overflow, control size, labels) at both viewports |

## Running the tests

```bash
npm test                                  # unit, component, integration, API, acceptance, security
npm run test:coverage --workspace backend # the same backend suites with coverage
npm run test:e2e                          # browser end-to-end at 1280 px and 375 px
```
