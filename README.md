# Project template

> Owner: Team lead · Agent: read-only

Template version: **tanej-1.0** (derived from template 1.2). Increase it whenever a file in section 2 changes; each run records it in `run-config.md`.

Humans start here; agents start at `AGENTS.md`.

## Folders

| Folder | Content | Phase |
|---|---|---|
| `01_input/00_general/rules.md` | Rules of every phase: decisions, commits, time, output size, secrets | all |
| `01_input/00_general/phases/` | One card per phase: what to read, do, write, the gate, the commit unit | one each |
| `01_input/00_general/standards.md` | Lookup tables: engineering standards, tests, security baseline, severity scale, definition of done | as a card names |
| `01_input/01_project/00_setup/` | Run configuration, secrets list, tech stack, environments | 0 |
| `01_input/01_project/01_requirements/` | Stories, business rules, scope, open questions | 1 |
| `01_input/01_project/02_design/` | Architecture, security requirements, quality requirements | 2 |
| `02_output/` | Everything the agent produces (docs, code, scripts, logs) | 0-7 |
| `03_statistics/` | Experiment measurement (not part of the project) | all |

## Running

1. Fill section 1, copy `secrets.env.example` to `.env` in the repository root and fill it, commit the inputs.
2. Start the agent in the repository root with: "Read `AGENTS.md` and start."
3. When the agent asks a blocking question it ends its turn. Answer in the same session if you are back within the hour. Otherwise start a new session with: "Read `AGENTS.md` and resume; answer to D-nn: …". A new session has its own transcript; the post-run usage session must count both.
4. After the run, review the non-blocking decisions and suppressed false positives listed in `02_output/docs/release-notes.md`.

## 1. Fill in (human, for each project or run)

| File | What goes in it |
|---|---|
| `01_input/01_project/00_setup/run-config.md` | model, effort, run id (every run) |
| `01_input/01_project/00_setup/secrets.env.example` | every secret the project needs (names only); copy to `.env` and fill the values there |
| `01_input/01_project/00_setup/tech-stack.md` | every platform, dependency and tool with exact versions |
| `01_input/01_project/00_setup/environments.md` | environments, settings, external services |
| `01_input/01_project/01_requirements/user-stories.md` | user stories |
| `01_input/01_project/01_requirements/business-rules.md` | business rules, data fields, glossary |
| `01_input/01_project/01_requirements/scope.md` | in/out of scope, priorities, open questions |
| `01_input/01_project/02_design/architecture.md` | components, constraints, interfaces |
| `01_input/01_project/02_design/security-requirements.md` | security level, authentication, personal data, project requirements |
| `01_input/01_project/02_design/quality-requirements.md` | non-functional requirements, thresholds, extra done criteria |
| `03_statistics/usage.md` | prices before the post-run session; usage-panel values after the run |

## 2. Do not touch (change only when revising the template itself)

- `AGENTS.md`, `README.md`
- everything in `01_input/00_general/`: `rules.md`, `standards.md`, `phases/0-preflight.md` … `phases/7-release.md`
- `03_statistics/metrics.md`, `03_statistics/run-log.template.json`

## 3. Written by the agent (read, do not edit)

- `02_output/README.md` and one README per component
- `02_output/docs/`: `00_preflight-report.md`, `00_input-manifest.sha256` (frozen once written), `00_progress.md`, `01_acceptance-criteria.md`, `02_specification.md`, `02_contracts/`, `03_acceptance-manifest.sha256`, `03_test-strategy.md`, `06_verification-report.md`, `decisions-log.md`, `release-notes.md`
- `02_output/scripts/`, `02_output/logs/`, all source code and tests in `02_output/`
- repository files required by ES-03 at the repository root: `.gitattributes`, `.gitignore`
- `03_statistics/run-log.json`, `03_statistics/run-summary.md`

Any change to sections 1 and 2 during a run is detected in phase 6 by comparing `00_input-manifest.sha256`.

## Rules for editing this template

- Start every Markdown file with `> Owner: … · Read in: … · Agent: …` (agent-written skeletons use `> Written in: … · Source: … · Agent: writes`) and add it to the right list above.
- A rule that holds in every phase goes in `rules.md`; a step of one phase goes on that phase's card; a table that is looked up by ID goes in `standards.md`. State each rule once.
- A phase card is self-sufficient: an agent that has read `AGENTS.md`, `rules.md` and the card knows everything to read for that phase.
- Versions appear only in `tech-stack.md`.
- `03_statistics/` is shared with the other templates and is not changed here, so that runs stay comparable.
