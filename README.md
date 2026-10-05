# Project template

> Owner: Team lead · Agent: read-only

Template version: **tanej-2.0** (derived from tanej-1.2; changes at the end of this file). Increase it whenever a file in section 2 changes; each run records it in `run-config.md`.

Humans start here; agents start at `AGENTS.md`.

## Structure

```
AGENTS.md                      agent entry point: read order, precedence, never-list
01_input/
  00_general/                  filled once per organisation, reused by every project
    rules.md                   rules of every phase
    phases.md                  phase order: skill, reads, writes, gate, commits
    skills/<name>/SKILL.md     one procedure per file (9)
    standards/                 lookup tables: engineering, testing, security, done
    tools/                     secrets.sh, usage-from-transcript.mjs
  01_project/                  filled per project (5 files; run-config.md in 00_setup/ because metrics.md names that path)
02_output/                     everything the agent writes
03_statistics/                 experiment measurement, shared with the other templates
```

## Running

1. Fill section 1, copy `01_input/01_project/secrets.env.example` to `.env` in the repository root and fill it, commit the inputs. Start each run on its own branch from the template commit.
2. Install the platform versions of `project/stack.md` on the host (JDK, Node.js with npm, Docker); another version costs a decision.
3. Keep the agent away from `.env`: in `.claude/settings.local.json` deny `Read(./.env)`, `Read(./.env.*)`, `Edit(./.env)` and the shell commands that print it. The agent reaches the values only through `tools/secrets.sh`, which prints key names and states, never values. For an unattended run (`Unattended: yes`), allow the commands the skills use beforehand.
4. Start the agent in the repository root with: "Read `AGENTS.md` and start."
5. A blocking question ends the agent's turn. Answer in your own words, for example "D-16: 1"; a pasted answer needs one line of your own. After more than an hour, answer in a new session: "Read `AGENTS.md` and resume; answer to D-nn: …".
6. Right after the run, fill the usage-panel values in `03_statistics/usage.md` (API time, approval prompts, cost shown). That is the only task the run leaves you. "Decisions to sign off" and "Before production" in the release notes matter only if the product goes live.

## 1. Fill in (human)

| File | When | What goes in it |
|---|---|---|
| `01_input/01_project/00_setup/run-config.md` | every run | model, effort, run id, attended or not |
| `01_input/01_project/secrets.env.example` | per project | names of every secret (values go only in `.env`) |
| `01_input/01_project/requirements.md` | per project | user stories, business rules, data, glossary, scope, open questions |
| `01_input/01_project/stack.md` | per project; refresh versions before each run | platforms, dependencies, tooling with exact versions; environments and external services |
| `01_input/01_project/constraints.md` | per project | components, architecture constraints, interfaces, security and quality requirements, overrides |
| `03_statistics/usage.md` | prices before the run, panel values after | |

## 2. Do not touch (change only when revising the template)

- `AGENTS.md`, `README.md`
- everything in `01_input/00_general/`
- `03_statistics/metrics.md`, `03_statistics/run-log.template.json`

## 3. Written by the agent

- `02_output/README.md` and one README per component
- `02_output/docs/`: `00_preflight-report.md`, `00_input-manifest.sha256` (frozen once written), `00_progress.md`, `01_acceptance-criteria.md`, `02_specification.md`, `02_contracts/`, `03_acceptance-manifest.sha256`, `03_test-strategy.md`, `06_verification-report.md`, `decisions-log.md`, `release-notes.md`
- `02_output/scripts/`, `02_output/logs/`, all source code and tests in `02_output/`
- `.gitattributes` and `.gitignore` at the repository root (ES-03)
- `03_statistics/run-log.json`, `03_statistics/run-summary.md`

## Maintaining the general files

They are written once, but they are not frozen forever. Each file states in its header when to review it.

| Kind | Files | Changes |
|---|---|---|
| Process | `rules.md`, `phases.md`, `skills/` | when a run shows a process failure |
| Policy | `standards/` | per organisation; when ASVS or the test policy changes |
| Tools | `tools/` | when the harness or the statistics format changes |

- A project that needs a different rule writes `Overrides: <ID>, reason` in `project/constraints.md`; it never copies or edits a general file.
- Versions live only in `project/stack.md`. A general file names no version and no project fact; a project file states no general rule.
- Keep a rule only if it changes what the agent does. When a rule is added, note in "Changes" below which run failure it prevents. To find rules the model follows anyway, remove one at a time and compare a repeated run; one run is not enough to judge.

## Rules for editing this template

- Every Markdown file starts with `> Owner: … · Fill: … · Read in: … · Agent: …` (skills: after the front matter; agent-written skeletons: `> Written in: …`).
- An always-on rule goes in `rules.md`; a phase's contract (reads, writes, gate, commits) in `phases.md`; a procedure in one skill; a table looked up by ID in `standards/`. State each rule once.
- `03_statistics/metrics.md` and `run-log.template.json` are shared with the other templates and never change here.

## Changes since tanej-1.2

| Change | Where | Reason |
|---|---|---|
| Ten project files merged into three (`requirements.md`, `stack.md`, `constraints.md`); secrets list moved up; `00_setup/run-config.md` stays where the shared `metrics.md` expects it | `01_input/01_project/` | fewer files to fill per project; the owner-role split meant nothing with one operator. Content unchanged. |
| General rules that lived in project files moved to general files (version rules from the tech stack, AC format from the user stories) | `standards/engineering.md`, `skills/derive-acceptance-criteria` | they would have had to be copied into every project |
| Procedures moved out of the phase cards into one skill per file; cards replaced by one phase map | `skills/`, `phases.md` | skills were spread over eight cards; separate files are easier to maintain and to give to a single agent later |
| Standards split by topic | `standards/` | different owners and review cycles |
| "Pending review" replaced by "applied, to sign off"; only decisions a user would notice go to the release notes | `skills/decisions`, `skills/release`, `docs/release-notes.md` | run tanej-03 listed 16 decisions as if they were still open |
| "Must be tested manually" renamed "Before production", limited to real external services and out-of-run fixes; DoD-09 reworded | `skills/release`, `standards/done.md` | run tanej-03 presented go-live checks as tasks for the experiment |
| The run ends with one short message whose only request is the usage-panel values | `skills/release` | run tanej-03 ended with three tasks for the human |
| Test measures table (tests, coverage, mutation, tools) in the test strategy and the run summary | `docs/03_test-strategy.md`, `03_statistics/run-summary.md` | coverage was only in the agent's documents, not in the statistics |
