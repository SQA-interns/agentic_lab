# Run Summary — Single Agent / Classical SDD

| Item | Value |
| --- | --- |
| Run id | `opus5.5_tanej_claude_desktop-2026-09-28` |
| Model | `claude-opus-5-5` |
| Harness | Claude Code (Claude desktop app, Code tab); version not observable in-session |
| Branch | `opus5.5_tanej_claude_desktop` |
| Start commit | `8d2e4f529bea095a85680ddfd799a97903cf33e4` (clean working tree confirmed) |
| Experiment start | 2026-09-28T21:52:41Z |
| Experiment end / final commit | see `run-log.json` (`experimentEnd`, `finalCommit`) |

All values below are taken from `run-log.json`, command output observed
during the run, or git. Durations are derived from the logged timestamps
(not stored as extra fields).

## Phases

| Phase | Start (UTC) | End (UTC) | Duration | Commit |
| --- | --- | --- | --- | --- |
| Acceptance criteria (incl. scaffold copy + run-log setup before it) | 21:53:00 | 21:54:02 | 1 m 02 s | `20d0f9e` |
| Specification | 21:54:08 | 21:57:21 | 3 m 13 s | `2503c4d` |
| Implementation | 21:57:27 | 22:08:32 | 11 m 05 s | `a1648de` |
| Tests | 22:08:41 | 22:19:49 | 11 m 08 s | `46c6c80` |
| Verification | 22:19:58 | 22:40:55 | 20 m 57 s | `5cfe713` |
| Finalization | 22:41:07 | see `run-log.json` | — | see `run-log.json` |

The phases ran strictly in order; each was committed separately (no
squash, no rebase, 0 reverts, 0 merge conflicts).

## Key measurements

- **First working happy path:** 2026-09-28T22:06:33Z — external
  registration via the REST API against a locally running backend
  (PostgreSQL + Mailpit): 201, database row, JSON backup, two emails.
  (≈ 13 min 52 s after experiment start.)
- **First complete test run (before repairs):** 153 passed, 4 failed
  (backend 122/126, frontend 26/26, E2E 5/5). Failures: 1 implementation
  defect (NBSP not trimmed), 3 test/testability defects; also Vitest
  collected the Playwright spec as a failed suite (0 tests). Details in
  `docs/test-strategy.md` §6.
- **Final complete test run:** 164 passed, 0 failed (backend unit 69,
  backend integration 64, frontend 26, E2E 5).
- **Coverage (final):** backend unit — lines 75.3 %, instructions 78.1 %;
  backend integration — lines 88.2 %, instructions 89.9 %; frontend —
  statements/lines 92.74 %, branches 88.98 %.
- **Verification findings:** 1 Critical, 1 Major, 7 Minor.
- **Fix loops:** 6 (total ≈ 4 min 02 s by logged start/end):
  1. Spotless violation in a test file (VF-01)
  2. SpotBugs could not run — scaffold pins an unpublished plugin version (VF-02)
  3. Prettier check failing on CRLF scaffold files / generated output (VF-04)
  4. 22 Critical / 23 High dependency CVEs → Tomcat 10.1.60, pgjdbc 42.7.13,
     justified triage of Spring CVEs with an enforcing guard test (VF-06)
  5. Possible personal data in unexpected-error logs (VF-07)
  6. Production mode started without reCAPTCHA site key (VF-08)
- **Human interventions:** none. **Clarifying questions:** 0.
  **Manual code fixes:** 0.

## Definition of Done

| Area | Status |
| --- | --- |
| Static checks (Spotless, SpotBugs, PMD, CPD, ESLint, tsc, jscpd, Prettier) | Pass |
| Complete automated test suite | Pass (164/164) |
| Security (Semgrep, Dependency-Check, npm audit) — no unresolved Critical/High | Pass (self-verification, not an external review) |
| Architecture (ArchUnit rules for the declared architecture, cycle check) | Pass (0 violations) |
| Container execution (9 demonstrations) | Pass |
| Documentation | Present |
| Experiment record | `run-log.json`, `run-summary.md` |

## Notable decisions and caveats

- Spring Boot upgraded within 3.x (3.4.4 → 3.5.16). Spring Framework 6.2.19
  and Spring Security 6.5.11 CVEs have no open-source fix for Spring Boot 3.x;
  they were triaged as not applicable (unused features) and suppressed with
  per-CVE justification, enforced by `VulnerableFeatureGuardTest`. This is
  the agent's own risk assessment and should be reviewed by a human.
- The scaffold's `spotbugs-maven-plugin` version 10.12.15 does not exist on
  Maven Central; it was changed to 4.10.4.1 in the copied `pom.xml` so that
  SpotBugs could execute. The `01_Input_Files/05_Scaffold` original is
  unchanged. External auditors using the frozen scaffold version will hit
  the same resolution failure.
- OWASP Dependency-Check ran without an NVD API key using a local NVD cache
  that was incrementally updated during the run.
- Semgrep's frozen `.semgrep.yml` contains no rules; the registry ruleset
  `p/default` was additionally executed for meaningful results.
- `experimentEnd` is recorded immediately before the finalization commit;
  a last bookkeeping commit then writes `finalCommit` (the SHA of the
  finalization commit) into `run-log.json`. That bookkeeping commit is not
  listed in `commits[]` because a commit cannot contain its own SHA.

## Immutable inputs

`git diff 8d2e4f5..HEAD -- 01_Input_Files` is empty and the working tree
has no changes under `01_Input_Files/`. Only `02_Implementation/` and
`03_Run-Statistics/` were changed.

## Externally audited metrics

Tokens, cost, tool calls, approval prompts, code quality, security and
architecture metrics below `_selfReportNote` remain `null` with their
template reasons, to be filled by the external audit.
