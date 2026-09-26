# Run Summary — Single Agent / Classical SDD

| Item | Value |
| --- | --- |
| Run id | `opus5.5_single_agent_classic_sdd_run-20260926T170001` |
| Model / harness | Claude Opus 5.5 / Cursor agent mode (harness version not observable) |
| Branch | `opus5.5_single_agent_classic_sdd_run`, created from the baseline commit because the checked-out branch was named for another model's experiment |
| Start commit | `2be8619cbd9191826ee8ddcb773a2a579c808e42` |
| Final commit | recorded in `run-log.json` → `finalCommit` (see "Git" below) |
| Experiment start / end | 2026-09-26 17:00:01 / see `run-log.json` → `experimentEnd` (+02:00) |

## Outcome

The conference registration system was implemented end to end on the
frozen scaffold: external and student registration, configurable options,
confirmation, PostgreSQL + raw JSON backup, participant and organizer
emails (with JSON attachment), and organizer-only Excel export. Final
complete test run **140 passed / 0 failed**. Static checks, ArchUnit and
all nine container demonstrations passed.

**The Definition of Done is not fully met.** OWASP Dependency-Check
reports Critical/High CVEs in the backend framework dependencies. After
the human decided to upgrade from the frozen Spring Boot 3.4.4 to 3.5.16,
the newest 3.x release, 22 Critical / 27 High matches (counted per jar)
remain. They are documented as an exception (finding V-09) because the
fixes require leaving "Spring Boot 3.x".

## Phases (all timestamps in `run-log.json`)

| Phase | Duration | Notes |
| --- | --- | --- |
| Acceptance criteria | 0:01:16 | 48 criteria, AC-001-01 … AC-008-07 |
| Specification | 0:03:53 | Layered backend with ports; HTTP Basic organizer export; YAML option config |
| Implementation | 0:11:57 | First working happy path 0:17:55 after experiment start (via frontend nginx → backend → DB, backup, Mailpit) |
| Tests | 0:14:10 | 140 tests written; first complete run 112 passed / 28 failed |
| Verification | 1:00:44 | Includes 0:16:18 waiting for the human decision and the first NVD data download (~36 min, running alongside other checks) |
| Finalization | see `run-log.json` | Release notes, summary, log |

## Tests

| Run | Passed | Failed | Notes |
| --- | --- | --- | --- |
| First complete run | 112 | 28 | Backend 73/27 (25 integration setup errors: Testcontainers vs Docker Engine 29; 1 test stubbing defect; 1 implementation defect, no-break space not trimmed), Vitest 33/1 (implementation defect, captcha reset), E2E 6/0 |
| Final complete run | 140 | 0 | Backend unit 75, integration/API 25, Vitest 34, E2E 6 — on Spring Boot 3.5.16 |

Self-measured coverage (the authoritative values come from the external
audit): backend unit 69.5 % lines, backend integration 88.7 % lines,
frontend 82.4 % statements.

## Fix loops (9, total 0:12:03)

| # | Trigger | Change | Duration |
| --- | --- | --- | --- |
| 1 | Testcontainers could not connect to Docker Engine 29 | `docker-java.properties` `api.version=1.44` (test only) | 0:36.6 |
| 2 | Test stubbing defect | `doThrow(...).when(...)` | 0:16.8 |
| 3 | No-break space not trimmed (implementation) | Unicode-aware trimming | 0:15.0 |
| 4 | Captcha stayed checked after rejection (implementation) | Remount test-mode captcha via `key` | 0:42.3 |
| 5 | Prettier check failed (CRLF checkout, generated files) | `.gitattributes`, `.prettierignore` | 0:26.0 |
| 6 | Semgrep: nginx forwarded client `Host` | Header removed | 0:14.8 |
| 7 | Duplicate security headers on `/api` | nginx headers scoped to `location /` | 0:25.8 |
| 8 | E2E 6/6 failed: stale Vite dev-server proxy | Dev server restarted (environment only) | 4:48.7 |
| 9 | OWASP Critical/High in the frozen dependency tree | Spring Boot 3.4.4 → 3.5.16, full re-verification | 4:17.1 |

## Verification findings

1 Critical (V-09, open, documented exception), 0 Major, 8 Minor (V-01 to V-08:
four fixed, three accepted with justification, one frozen-scaffold
defect worked around). Details in `02_Implementation/docs/verification-report.md`.

## Human effort

- 1 clarifying question / 1 human intervention (18:12:11 → 18:28:29,
  0:16:18): how to handle the OWASP Critical/High findings that cannot be
  resolved within the frozen stack. Decision: upgrade to Spring Boot
  3.5.16 and document the remainder.
- 0 manual code fixes.

## Deviations and notes

- Frozen-scaffold defects found: the SpotBugs plugin version 10.12.15
  does not exist (SpotBugs was run with explicit version 4.10.4.1), and the
  Spring Boot 3.4.4 dependency tree carries many known CVEs.
- Tools not available locally were run from Docker (Semgrep). The OWASP
  OSS Index analyzer needs credentials and was disabled; the NVD analyzers
  ran without an API key.
- The copied scaffold README replaced the placeholder
  `02_Implementation/README.md` during the literal scaffold copy.
- Raw evidence for every check is under `03_Run-Statistics/raw/`.
- The inputs in `01_Input_Files/` were not modified (`git diff` against the
  start commit is empty), and nothing outside `02_Implementation/` and
  `03_Run-Statistics/` changed.
- Metrics below `_selfReportNote` in `run-log.json` are left `null` for the
  external audit.

## Git

Commits after each phase are listed in `run-log.json` → `commits`. The
final-commit SHA can't be written into the commit it identifies, so it is
recorded by one extra bookkeeping commit that changes only `run-log.json`
(`finalCommit` plus the Phase 6 entry in `commits`). That bookkeeping
commit is not listed in `commits` itself, for the same reason. No reverts,
no merge conflicts, no squash or rebase.
