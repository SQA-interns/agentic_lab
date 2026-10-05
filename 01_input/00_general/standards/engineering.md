# Engineering standards

> Owner: Team lead · Fill: once per organisation; review when the stack or tooling policy changes · Read in: phases 0, 4, 6, 7 · Agent: read-only

| ID | Standard |
|---|---|
| ES-01 | Every value that differs between environments comes from configuration; secrets have no default in code. |
| ES-02 | Secrets are never committed; `.env` is ignored by git; `project/secrets.env.example` lists every key. |
| ES-03 | The repository has `.gitattributes` normalising text files to LF and `.gitignore` excluding build output, dependencies and local config. |
| ES-04 | Dependency versions are exact and lock files are committed. |
| ES-05 | Each component offers one command each to build, test, check (format, lint, type check) and run; its README lists them. |
| ES-06 | Root README: purpose, components, quick start, documentation map. Component README: prerequisites, configuration (settings and secrets, with source), build, run, test, troubleshooting. |
| ES-07 | Logs contain no secrets or personal data; errors shown to users contain no internal details. |
| ES-08 | Persistent data structures are versioned (migrations or equivalent). |
| ES-09 | Deployable services expose health and readiness information. |
| ES-10 | Changes are limited to the task; do not refactor unrelated code. |

## Versions

`project/stack.md` is the single source of truth for every platform, dependency and tool version. Build manifests and lock files in `02_output/` must match it exactly.

- Exact versions only: no ranges, wildcards, `latest` or floating tags.
- Changing an entry is a blocking decision. The human's answer replaces the entry; nobody edits `project/stack.md` during a run.
- A pin that resolves and runs but whose tool reports another version (for example an image tag) is a non-blocking decision; the listed pin stays authoritative. A pin that does not resolve or run is blocking.
- A dependency or tool not listed may be added only with an exact version and a non-blocking decision; it is scanned in phase 6 like any other.
- Every dependency's licence must be compatible with the project licence stated in `project/stack.md`.
- A `platforms` or `tooling` entry may carry `version_check` (a command whose output contains the version) and, for `tooling`, `options` (flags, analysers or rule sets to enable or disable). Preflight uses them.
- `tooling` includes at least one tool for each purpose: build, format, lint, type check (if the language has one), test, coverage, mutation, static analysis, dependency scan, secret scan, code metrics, duplication.
