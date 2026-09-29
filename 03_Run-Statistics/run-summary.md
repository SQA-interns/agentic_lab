# Run Summary — opus5.5-testrun-latest-2026-09-29

| Item | Value |
| --- | --- |
| Run ID | `opus5.5-testrun-latest-2026-09-29` (the operator's prompt left the run-identifier placeholder unfilled; the example value it gave was used) |
| Model | `claude-opus-5-5` |
| Harness | Claude Code (Claude desktop app, Code tab); version not observable from inside the session |
| Branch / start commit | `TestRun_Latest` / `65f4ad0e90c0ea85b5f5963713186b5e3dfb0d82` |
| Process | Classical SDD, single agent: AC → Specification + contract → frozen acceptance tests → implementation → unit tests → verification → finalization |
| Experiment start / end | 2026-09-28T23:27:11Z / see `run-log.json` → `experimentEnd` |

All numbers below are copied from `run-log.json` or the phase artefacts;
nothing here is estimated.

## Timeline

| Phase | Duration |
| --- | --- |
| 1 Acceptance criteria | 1.7 min |
| 2 Specification + API contract | 6.0 min (includes the 1.2 min D-6 human answer) |
| 3 Acceptance tests (frozen) | 16.7 min |
| 4 Implementation | 20.3 min |
| 5 Unit tests | 11.3 min |
| 6 Verification | 389.7 min — **350.1 min of it waiting for the operator's answer to D-8**; ≈ 40 min of verification work |
| 7 Finalization | see `run-log.json` |

- First executable registration happy path: 2026-09-29T00:00:19Z,
  33.1 min after start (first frozen acceptance run in Phase 4: 86/90
  green, happy path included).
- Fix loops: 2 (2.9 min and 3.0 min).
- Human interventions: 2 (1.2 min: human inputs D-6; 350.1 min: dependency
  CVE decision D-8 — the answer arrived about 5 h 50 min after the
  question, and the run waited).

## Outcome

- **Tests**: first complete run (Phase 5) 254 passed / 1 failed — an
  implementation defect (no-break space not trimmed) fixed in code. Final
  complete run 275 passed / 0 failed (90 frozen acceptance, 9 frozen E2E,
  112 backend unit, 16 backend integration, 9 ArchUnit, 39 frontend).
- **Acceptance-test freeze held**: 18/18 hashes match; no test-defect
  request. Three mechanical harness problems were fixed *before* freezing
  (Docker API version, POI test dependency, `@DynamicPropertySource`
  precedence) and are documented in `docs/test-strategy.md` §3.1.
- **Verification (self-scan, not an independent review)**: findings as
  found — Critical 1, High 1, Medium 5, Low 8. Critical: CVEs in the
  scaffold-pinned Spring Boot 3.4.4 stack (fix loop 1: upgrade to 3.5.16 +
  overrides; residual unreachable CVEs reclassified by the agent).
  High: Semgrep, HTTP Basic organizer auth (fix loop 2: HTTPS-only
  transport; residual reclassified Medium, D-9 pending review).
- **DoD**: static checks, tests, security scans, ArchUnit, mutation testing
  (PIT 84.7 %, Stryker 71.3 %), container demonstration (9/9 + persistence
  across recreation) and documentation (README verified by following it
  from a fresh clone) all executed; details in
  `02_Implementation/docs/verification-report.md`.

## Escalations (CONSTITUTION §3) — `docs/decisions-log.md`

| ID | Topic | Resolution |
| --- | --- | --- |
| D-1 … D-5 | Requirement ambiguities (option eligibility, consents, email failure, selection limits, duplicate emails) | agent-default, pending human review |
| D-6 | All HUMAN_INPUTS_MANIFEST rows unresolved at Phase 2 | resolved by human (test-mode reCAPTCHA, Mailpit, locally generated organizer credentials, NVD key provided) |
| D-7 | Testcontainers 1.20.6 vs Docker Engine 29 API | agent-default (configuration-only `api.version=1.44`), pending human review |
| D-8 | Critical/High dependency CVEs in the pinned Spring Boot 3.4.4 stack | resolved by human (upgrade within 3.x + reachability triage) |
| D-9 | Semgrep High on HTTP Basic organizer auth vs frozen spec/tests | agent-default, pending human review |

## Things worth knowing

- The scaffold's pinned **Spring Boot 3.4.4 was changed to 3.5.16** —
  only after escalation and explicit operator approval (D-8), unlike
  earlier runs in this series.
- A **configuration file** (`backend/src/test/resources/docker-java.properties`)
  was needed for Testcontainers to work with this machine's Docker Engine
  29; no version was changed for it (D-7).
- The machine held Docker images/volumes from earlier runs (including a
  PostgreSQL volume with a different password). They were not touched;
  this run uses its own compose project name.
- Git on this machine converts line endings (`core.autocrlf`), and the
  frozen Java tests were written with CRLF by Spotless. `.gitattributes`
  rules make every checkout reproduce the frozen bytes (verified with a
  fresh clone).
- Harness-level metrics — **tokens, cost, wall/API time, tool calls,
  approval prompts** — and the code-quality/security/architecture
  metrics below `_selfReportNote` stay `null` in `run-log.json`, pending
  the external audit pass in `04_External-Audit/` (at `WORKSPACE_ROOT`),
  which is filled in by a human after the run and was not touched by the
  agent.
