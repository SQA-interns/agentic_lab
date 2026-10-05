# Preflight report

> Written in: phase 0 · Source: `project/00_setup/run-config.md`, `project/stack.md`, `project/secrets.env.example` · Procedure: `skills/preflight` · Agent: writes

| Check | Source of truth | Result |
|---|---|---|
| Run configuration complete | `project/00_setup/run-config.md` | |
| Platforms and tools installed at the listed versions | `project/stack.md` | |
| Local environments and services running or reachable | `project/stack.md` ("Environments") | |
| Secrets present in `.env` or marked test-only | `project/secrets.env.example` | |
| Every listed dependency resolves | `project/stack.md` | |
| No listed dependency has a known Critical or High vulnerability | `project/stack.md` | |
| Clean working tree on the starting commit | repository | |
| Input manifest written | `docs/00_input-manifest.sha256` | |

## Dependency results

| id | version | Resolves | Highest vulnerability |
|---|---|---|---|

## Asked the human

| When | What was missing | Answer | Re-check |
|---|---|---|---|
