# Input

> Owner: Team lead · Read in: phase 0 · Agent: read-only

Which files humans fill in, which are left alone and which the agent writes: `README.md` at the template root.

## Folder map

| Folder | Content | Owner |
|---|---|---|
| `00_general/` | Rules, phases, skills, quality and security baselines for every project | Team lead |
| `01_project/01_setup/` | Run configuration, list of secrets (every run) | Human operator |
| `01_project/02_business/` | Stories, business rules, scope, open questions | Product owner |
| `01_project/03_technical/` | Tech stack (all versions), architecture, environments | Architect |
| `01_project/04_security/` | Project security and privacy requirements | Security officer |
| `01_project/05_quality/` | Project quality targets, extra done criteria | QA lead |

Outputs go to `02_output/`; experiment measurement to `03_statistics/` (not part of the project).

Path aliases used in all files: `general/` = `01_input/00_general/`, `project/` = `01_input/01_project/`, `out/` = `02_output/`, `docs/` = `02_output/docs/`.

## General vs. project

- A project file adds to the general file on the same topic; it never repeats it.
- A project file overrides a general item only with an explicit line `Overrides: <ID>, reason`.
- Precedence: `AGENTS.md` > `general/working-rules.md` > `general/phase-rules.md` > `general/phases.md` > other `general/` files > `project/` files, except for explicit overrides.
- A human approval recorded in a decision record (`Human response`) replaces the input entry it changes, for that item only. The agent never edits `01_input/` to apply it.
- A conflict that no rule resolves is a decision record, never a silent choice.

## Order for humans

1. Fill `01_project/02_business/` → `05_quality/`.
2. Fill `01_project/01_setup/run-config.md`.
3. Start the agent. It checks everything else in phase 0 and asks for whatever is missing.

## Rules for editing this template

- Start every Markdown file with `> Owner: … · Read in: … · Agent: …` (after the front matter in a `SKILL.md`; agent-written skeletons use `> Written in: … · Source: … · Agent: writes`) and add it to the right list in the root `README.md`.
- State each rule once; link to it elsewhere. Write constraints ("Do not …"); procedures go in skills.
- One topic per file, no fixed length: split a file when it covers two topics or a reader needs only half of it. Versions appear only in `tech-stack.md`.
