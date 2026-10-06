# Run summary

> Written in: phase 7 · Source: `run-log.json` · Agent: writes

## Run

| Item | Value |
|---|---|
| Run id | tanej-04_conference-registration_opus5.5_sdd_template-tanej-2.0 |
| Model, effort | claude-opus-5-5, medium |
| Template version | tanej-2.0 |
| Start commit | `20c720214a74a65b082724063222be2ee748caa3` (2026-10-05T20:02:30Z) |
| Final commit | `250dc4143d0a0c740efbbdf32c0e9206f3c0f288` (2026-10-06T09:03:48Z) |
| Sessions | one (`1a159aa4-f001-4ecb-93e9-074b9c324307`), stopped by the human overnight and resumed |

## Timeline

Wall time from the recorded phase boundaries; agent time subtracts the recorded human interventions inside the phase.

| Phase | Start | End | Wall | Human waits | Agent time |
|---|---|---|---|---|---|
| 0 Preflight and bootstrap | 20:02:30 | 20:40:37 | 38m 07s | 7m 56s (D-03) | 30m 11s |
| 1 Requirements | 20:40:37 | 20:42:28 | 1m 51s | — | 1m 51s |
| 2 Design | 20:42:28 | 20:56:18 | 13m 50s | 2m 10s (D-11) | 11m 40s |
| 3 Test design | 20:56:18 | 21:19:14 | 22m 56s | — | 22m 56s |
| 4 Build | 21:19:14 | 07:44:54 (+1 day) | 10h 25m 40s | 9h 32m 58s (stop) + 7m 26s (D-14, D-17) | 45m 16s |
| 5 Unit tests | 07:44:54 | 08:18:13 | 33m 19s | — | 33m 19s |
| 6 Verify | 08:18:13 | 08:42:35 | 24m 22s | — | 24m 22s |
| 7 Release | 08:42:35 | 09:03:48 | 21m 13s | — | 21m 13s |

## Outcome

| Measure | Value |
|---|---|
| First complete run (phase 5) | 263 passed, 0 failed |
| Final run (phase 6) | 292 passed, 0 failed |
| Findings as found | Critical 1, High 0, Medium 2, Low 4 |
| Open Critical or High | 0 |
| Fix loops | 1 (F-01: `tinypool` advisories, fixed by pinning 2.1.2, 7m 03s) |
| Code (production / test lines) | backend 2,307 / 3,748; frontend 545 / 1,727; duplication 0% |

## Test measures

| Component | Level | Tests passed / failed | Line coverage | Branch coverage | Mutation score (scope) | Tools |
|---|---|---|---|---|---|---|
| backend | acceptance | 86 / 0 | — | — | — | JUnit 6, Testcontainers |
| backend | integration | 15 / 0 | — | — | — | JUnit 6, Testcontainers |
| backend | architecture | 5 / 0 | — | — | — | ArchUnit |
| backend | unit | 107 / 0 | 67.0% | 82.4% | 95.8%, 298 of 311 (validation, security, storage order, business rules) | JUnit 6, JaCoCo, PIT |
| backend | all levels | 213 / 0 | 96.4% | 92.2% | — | JaCoCo |
| frontend | acceptance | 20 / 0 | — | — | — | Vitest |
| frontend | unit | 49 / 0 | — | — | — | Vitest |
| frontend | all levels (unit + acceptance) | 69 / 0 | 100% | 97.24% | 98.1%, 361 of 368 (all of `src` except `main.tsx`) | Vitest, v8, Stryker |
| frontend + backend | end-to-end | 10 / 0 | — | — | — | Playwright 1.63.0 |

## Decisions and human interventions

- 19 decisions: 5 blocking (D-03, D-11, D-14, D-17 answered by the human; all resolved), 14 non-blocking; 10 applied choices pending review (release notes).
- Blocking: D-03 downgrade of a false-positive High CVE, D-11 downgrade of the Semgrep High on Basic authentication, D-14 test-harness cleanup after the freeze, D-17 organizer password shorter than 16 characters in `.env`.
- Human interventions: 4 questions (D-03; D-11, confirmed in a second message; D-14 with D-17) and one stop of the run at the phase 4 US-003 test run (2026-10-05T21:30:02Z to 2026-10-06T07:03:00Z).
- Commits: 91 on the branch from the start commit to the final commit (`git rev-list --count`); one above the size guide (`35c3c77`, D-04).

## Not measured by the agent

Cost, tokens, API time and tool calls are added by a human from the harness usage report.

From the transcript (`usage` in `run-log.json`, prices of 2026-09-25): 268 model calls, 488 tool calls, 562 input, 458,505 output, 102,209,089 cache-read and 1,031,022 cache-write tokens, about USD 37.86 at API prices. The usage panel in `usage.md` is authoritative.
