# Project template

> Owner: Team lead · Agent: read-only

Template version: **1.0**. Increase it whenever a file in section 2 changes; each run records it in `run-config.md`.

Humans start here; agents start at `AGENTS.md`. How the folders fit together: `01_input/README.md`.

## 1. Fill in (human, for each project or run)

| File | What goes in it |
|---|---|
| `01_input/01_project/01_setup/run-config.md` | workflow, model, effort, run id (every run) |
| `01_input/01_project/01_setup/secrets.env.example` | every secret the project needs (names only); copy to `.env` and fill the values there |
| `01_input/01_project/02_business/user-stories.md` | user stories |
| `01_input/01_project/02_business/business-rules.md` | business rules, data fields, glossary |
| `01_input/01_project/02_business/scope.md` | in/out of scope, priorities, open questions |
| `01_input/01_project/03_technical/tech-stack.md` | every platform, dependency and tool with exact versions |
| `01_input/01_project/03_technical/architecture.md` | components, constraints, interfaces |
| `01_input/01_project/03_technical/environments.md` | environments, settings, external services |
| `01_input/01_project/04_security/security-requirements.md` | security level, authentication, personal data, project requirements |
| `01_input/01_project/05_quality/quality-requirements.md` | non-functional requirements, thresholds, extra done criteria |
| `03_statistics/usage.md` | prices before the post-run session; usage-panel values after the run |

## 2. Do not touch (change only when revising the template itself)

- `AGENTS.md`, `README.md`, `01_input/README.md`
- everything in `01_input/00_general/`:
  - `working-rules.md`, `decision-record.md`, `engineering-standards.md`
  - `quality/definition-of-done.md`, `quality/severity-scale.md`, `quality/test-strategy.md`
  - `security/security-baseline.md`
  - `skills/preflight/SKILL.md`, `skills/security-review/SKILL.md`, `skills/verify-release/SKILL.md`
  - `workflows/sdd/phases.md`, `workflows/sdd/rules.md`, `workflows/sdd/skills/write-acceptance-tests/SKILL.md`
  - `workflows/tdd/phases.md`, `workflows/tdd/rules.md`, `workflows/tdd/skills/tdd-cycle/SKILL.md`
- `03_statistics/metrics.md`, `03_statistics/run-log.template.json`

## 3. Written by the agent (read, do not edit)

- `02_output/README.md` and one README per component
- `02_output/docs/`: `00_preflight-report.md`, `00_input-manifest.sha256` (frozen once written), `00_progress.md`, `01_acceptance-criteria.md`, `02_specification.md`, `02_contracts/`, `03_acceptance-manifest.sha256`, `03_test-strategy.md`, `06_verification-report.md`, `decisions-log.md`, `release-notes.md`
- `02_output/logs/`, all source code and tests in `02_output/`
- `03_statistics/run-log.json`, `03_statistics/run-summary.md`

Any change to sections 1 and 2 during a run is detected in phase 6 by comparing `00_input-manifest.sha256`.
