# AGENTS.md

> Owner: Team lead · Read in: every phase · Agent: read-only

Humans start at `README.md`.

## Read, in this order

1. `01_input/01_project/00_setup/run-config.md`: model, effort, run id
2. `01_input/00_general/rules.md`: the rules of every phase
3. `01_input/00_general/phases.md`: phase order; for each phase its skill, what to read and write, its gate and its commits
4. The current phase's skill in `01_input/00_general/skills/`, and only the files `phases.md` lists for that phase

Start with phase 0. If `02_output/docs/00_progress.md` shows work in progress, resume from it instead of starting over.

## Paths and precedence

- Aliases used in all files: `general/` = `01_input/00_general/` (so `skills/` = `general/skills/`, `standards/` = `general/standards/`), `project/` = `01_input/01_project/`, `out/` = `02_output/`, `docs/` = `02_output/docs/`.
- Precedence: `AGENTS.md` > `rules.md` > `phases.md` > skill > `standards/` > `project/`, except a line `Overrides: <ID>, reason` in `project/constraints.md` ("Overrides").
- A project file adds to the general files and never repeats them.
- A human answer recorded in a decision replaces the input entry it changes, for that item only. Never edit `01_input/` to apply it.
- A conflict that no rule resolves is a decision, never a silent choice.

## Never

- Do not create, modify or delete anything under `01_input/`, or any file in sections 1 and 2 of `README.md`. Write only the files in section 3.
- Do not edit a file listed in `docs/03_acceptance-manifest.sha256`, and do not change `docs/00_input-manifest.sha256` after phase 0.
- Do not change a technology, version, service or secret named in `project/stack.md` or `project/secrets.env.example`, and do not install or upgrade host software, without human approval.
- Do not read `.env` or print its values; only `general/tools/secrets.sh` reads it. Do not ask the human to paste a secret; ask them to put it in `.env`.
- Do not commit secrets or environment-specific values.
- Do not start a phase before the previous gate has passed, and do not stop after a passed gate: continue with the next phase (`rules.md`, "Progress").
- Do not finish while a Critical or High finding is open.
- Do not touch `03_statistics/` except as `rules.md` ("Statistics") says.
