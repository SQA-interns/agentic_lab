# Working rules

> Owner: Team lead · Read in: every phase · Agent: read-only

## Ask the human (blocking)

Record a decision (format: `decision-record.md`) in `docs/decisions-log.md`, ask, and wait when:

- anything checked in phase 0 is missing or wrong and only a human can fix it;
- a technology, version or service from `project/03_technical/tech-stack.md` does not work or needs changing;
- a frozen acceptance test appears wrong (only a human may update the manifest afterwards);
- a Critical or High finding would be downgraded or accepted.

Ask all open questions of a phase in one message. While waiting, finish work that does not depend on the answer.

## Decide and record (non-blocking)

When a requirement allows two behaviours a user would notice, choose the more conservative one, record it as "pending review", and continue.

## Identifiers

| Prefix | Meaning | Defined in |
|---|---|---|
| `US-nnn` / `AC-nnn-nn` | User story / acceptance criterion | `project/02_business/user-stories.md` / `docs/01_acceptance-criteria.md` |
| `BR-nn` / `OQ-nn` | Business rule / open question | `project/02_business/` |
| `AR-nn` | Architecture constraint | `project/03_technical/architecture.md` |
| `SB-nn` / `SR-nn` | Security baseline / project security requirement | `general/security/` / `project/04_security/` |
| `ES-nn` | Engineering standard | `general/engineering-standards.md` |
| `NFR-nn` | Non-functional requirement | `project/05_quality/quality-requirements.md` |
| `DoD-nn` / `DoD-Pnn` | Done criterion, general / project | `general/quality/` / `project/05_quality/` |
| `F-nn` / `D-nn` | Finding / decision | `docs/06_verification-report.md` / `docs/decisions-log.md` |

Tests, commits and findings reference these IDs.

## Commits

- One commit per logical unit: a feature (US/AC), a fix (F), or a phase's documents; IDs in the message.
- Do not squash, rebase or rewrite history.

## Progress

Update `docs/00_progress.md` at every gate and before any stop, so a fresh session can resume.

## Output

- Write command output longer than 50 lines to `out/logs/`; report only a summary and the path.
- Use only the severity levels in `general/quality/severity-scale.md`.

## Statistics

At the start and end of each phase, and for every fix loop, human intervention and decision, update `03_statistics/run-log.json` as defined in `03_statistics/metrics.md`. Do nothing else in `03_statistics/`.
