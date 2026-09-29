# Preflight report

> Written in: phase 0 · Source: `project/01_setup/*`, `project/03_technical/*` · Procedure: `general/skills/preflight` · Agent: writes

| Check | Source of truth | Result |
|---|---|---|
| Run configuration complete | `project/01_setup/run-config.md` | |
| Platforms and tools installed at the listed versions | `project/03_technical/tech-stack.md` | |
| Local environments and services running or reachable | `project/03_technical/environments.md` | |
| Secrets present in `.env` or marked test-only | `project/01_setup/secrets.env.example` | |
| Every listed dependency resolves | `project/03_technical/tech-stack.md` | |
| No listed dependency has a known Critical or High vulnerability | `project/03_technical/tech-stack.md` | |
| Clean working tree on the starting commit | repository | |
| Input manifest written | `docs/00_input-manifest.sha256` | |

## Dependency results

| id | version | Resolves | Highest vulnerability |
|---|---|---|---|

## Asked the human

| When | What was missing | Answer | Re-check |
|---|---|---|---|
