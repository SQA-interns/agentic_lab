# Definition of Done

The run is not complete until all of the following pass. None of these
are optional, and none may be skipped because time is short — a run
that stops short of this list is an incomplete run, not a finished one
with lower scores.

## 1. Static checks

* Lint: 0 errors (tool defined in `TECH_STACK.md`).
* Type check: 0 errors.
* Duplication and complexity: measured and recorded, even if not
  gated on a threshold.

## 2. Tests

* Full test suite: 100% pass, or documented and justified exceptions.
* Coverage recorded as two separate numbers: unit-only and
  integration-only (do not report only a combined figure).

## 3. Security

* Run the security scanner named in `TECH_STACK.md` plus a dependency
  audit (`npm audit` or equivalent).
* 0 unresolved Critical or High findings before merge.
* This is a self-scan, not an independent review — state that
  explicitly in `docs/verification-report.md` rather than implying
  otherwise.

## 4. Execution, not just tests

* Build the container defined in `TECH_STACK.md`.
* Run it.
* Exercise the primary happy path (at minimum: one full external
  registration and one full student registration) against the
  **running container**, not only against test doubles or an
  in-process test server.
* Hit every declared health/readiness endpoint, if any exist.

This step exists because, in the prior run, both High-severity findings
(missing security headers, a rate-limit race) were invisible to 100%
passing tests and full static analysis, and only appeared once the
container was actually started. Treat "all tests pass" and "the
container runs correctly" as two separate claims — do not let the
first stand in for the second.

## 5. Documentation

* `RELEASE_NOTES.md` exists and is derived from the actual
  implementation, not from the User Stories alone.
* Anything not demonstrated (e.g. "no live mail server was available
  to confirm delivery") is recorded as an open item, not glossed over
  or implied to be complete.

## 6. Experimental record

* `03_Run-Statistics/run-log.json` and `03_Run-Statistics/run-summary.md`
  exist and are complete, per `RUN_INSTRUCTIONS.md`. A run missing
  either of these is void, regardless of how well the application
  itself turned out.