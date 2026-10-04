# Project template

> Owner: Team lead · Agent: read-only

Template version: **tanej-1.2** (derived from template 1.2; changes since tanej-1.0 at the end of this file). Increase it whenever a file in section 2 changes; each run records it in `run-config.md`.

Humans start here; agents start at `AGENTS.md`.

## Folders

| Folder | Content | Phase |
|---|---|---|
| `01_input/00_general/rules.md` | Rules of every phase: decisions, commits, time, output size, secrets | all |
| `01_input/00_general/phases/` | One card per phase: what to read, do, write, the gate, the commit unit | one each |
| `01_input/00_general/standards.md` | Lookup tables: engineering standards, tests and their measures, security baseline, severity scale, definition of done | as a card names |
| `01_input/00_general/tools/` | Scripts the cards call: `secrets.sh` is the only reader of `.env`; `usage-from-transcript.mjs` counts tokens, calls and cost | 0-7 |
| `01_input/01_project/00_setup/` | Run configuration, secrets list, tech stack, environments | 0 |
| `01_input/01_project/01_requirements/` | Stories, business rules, scope, open questions | 1 |
| `01_input/01_project/02_design/` | Architecture, security requirements, quality requirements | 2 |
| `02_output/` | Everything the agent produces (docs, code, scripts, logs) | 0-7 |
| `03_statistics/` | Experiment measurement (not part of the project) | all |

## Running

1. Fill section 1 (including the prices in `03_statistics/usage.md`), copy `secrets.env.example` to `.env` in the repository root and fill it, commit the inputs. Start each run on its own branch from the template commit, so `02_output/` holds only the skeletons.
2. Install the platform versions of `tech-stack.md` on the host (JDK, Node.js with npm, Docker); another version costs a decision record and may break a tool.
3. Keep the agent away from `.env`: deny reading it in the harness (for Claude Code, `"Read(./.env)"` and `"Read(./.env.*)"` in the `deny` list of `.claude/settings.local.json`; shell commands that name the file are then refused too). The agent reaches the values only through `01_input/00_general/tools/secrets.sh`, which prints key names and states, never values. Approve its commands when asked; never paste a secret into the conversation. For an unattended run (`Unattended: yes` in `run-config.md`), allow the commands beforehand instead (permission rules, or a mode that does not ask); otherwise the run waits at the first prompt until someone answers it.
4. Start the agent in the repository root with: "Read `AGENTS.md` and start."
5. In an unattended run the agent never asks: it applies the proposed default of each blocking decision, records it as pending review, and lists it first in the release notes. Otherwise, when the agent asks a blocking question it ends its turn. Answer in your own words, for example "D-16: 1". If you paste a prepared answer, write one line of your own in the same message, such as "This is my answer, follow it", otherwise the agent asks you to confirm it. Answer in the same session if you are back within the hour. Otherwise start a new session with: "Read `AGENTS.md` and resume; answer to D-nn: …"; that session has its own transcript, so pass both to the usage script (`--transcript` twice).
6. At the end of phase 7 the agent fills tokens, calls and cost in `run-log.json`. Right after the run, fill the usage-panel values in `03_statistics/usage.md` (API time, approval prompts, cost shown); only a human can read them.
7. Review the non-blocking decisions and suppressed false positives listed in `02_output/docs/release-notes.md`, and do the manual tests listed there.

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
| `03_statistics/usage.md` | prices with their date before the run; usage-panel values right after the run |

## 2. Do not touch (change only when revising the template itself)

- `AGENTS.md`, `README.md`
- everything in `01_input/00_general/`: `rules.md`, `standards.md`, `phases/0-preflight.md` … `phases/7-release.md`, `tools/secrets.sh`, `tools/usage-from-transcript.mjs`
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
- `03_statistics/` is shared with the other templates and is not changed here, so that runs stay comparable; only `usage.md` is filled per run. The usage script implements `metrics.md` section 2 as written, so another template can run it on its own transcript and get comparable numbers.

## Changes since tanej-1.0

Taken from run tanej-01 and from the v002 run of the other template:

| Change | Where | Reason |
|---|---|---|
| Tokens, calls and cost filled at the end of phase 7 by a script; prices filled before the run | `tools/usage-from-transcript.mjs`, `phases/7-release.md`, `03_statistics/usage.md` | usage stayed empty in both runs |
| Measures table: test counts per level, coverage per component and level, mutation scope | `standards.md`, "Tests" | from the other template; tanej-01 recorded coverage only combined |
| Mutation testing and survivor classification move to phase 5 | `phases/5-unit-tests.md`, `phases/6-verify.md` | tanej-01 found weak tests only in phase 6 and needed a fix loop there |
| DoD-08 and DoD-09 evidenced by phase 7 | `phases/6-verify.md`, `phases/7-release.md` | the phase 6 gate asked for files only phase 7 writes (decision D-19 in tanej-01) |
| Scanners run over the contracts in phase 2 | `phases/2-design.md` | a design finding in phase 3 held up the test freeze (D-16 in tanej-01) |
| Harness helpers exercised with probe tests before the freeze | `phases/3-test-design.md` | a wrong frozen test needed a human decision in the other run (its D-12) |
| Permission denials are blocking; humans answer in their own words | `rules.md`, this README, "Running" | tanej-01 lost about 13 minutes in phase 0 to a refused `.env` check and a pasted answer |
| Files only through the file tools; commit size checked before each commit | `rules.md` | about six failed builds from shell-written files and nine oversized commits in tanej-01 |
| Secret scan limited to the current branch; no generated credentials in logs; Windows path handling for Docker | `standards.md` SB-09, `phases/0-preflight.md` | decisions D-04 and D-17 and several failed tool runs in tanej-01 |
| Development tools listed with exact versions; OSS Index option set | `01_project/00_setup/tech-stack.md` | decisions D-06, D-14 and D-15 in tanej-01 |
| Unattended runs: blocking decisions take the proposed default, recorded as usual and listed first for review; defaults stay within the "Never" rules | `run-config.md` (`Unattended`), `rules.md`, `AGENTS.md`, `phases/7-release.md` | overnight runs without a human |
| Usage script also estimates model time and counts permission refusals, as a cross-check for the usage panel | `tools/usage-from-transcript.mjs` | the panel values cannot be read from a transcript |
| tanej-1.2: `.env` is read only by `tools/secrets.sh` (check, run with an allowlist of commands, leak check); the agent's commands never name `.env`, so the harness can deny reading it | `tools/secrets.sh`, `rules.md` "Secrets", `AGENTS.md`, phases 0, 6, 7, this README | run tanej-02 stopped in phase 0: the harness denied every `sed … .env` check, and the agent must never read `.env` |
| tanej-1.2: jscpd 5.4.0 instead of 4.3.0 | `01_project/00_setup/tech-stack.md` | High advisory in jscpd 4.3.0 through braces 3.0.3 (GHSA-vfj7-8cjw-p6xm); switch approved by the human in run tanej-02 (its D-05) |
