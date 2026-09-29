---
name: preflight
description: Check on the human's behalf that everything the run needs is present and working, ask for whatever is missing, and record the result (phase 0, before bootstrap).
---

> Owner: Team lead · Read in: phase 0 · Agent: read-only

- Reads: `project/01_setup/*`, `project/03_technical/*`, `general/security/security-baseline.md`
- Writes: `docs/00_preflight-report.md`, `docs/00_input-manifest.sha256`, decision records for anything only the human can fix

## Procedure

1. Check `run-config.md` is complete and names an existing workflow.
2. For every `platforms` and `tooling` entry in `tech-stack.md`: confirm it is installed at exactly the listed version.
3. For every environment and service in `environments.md` needed locally: confirm it is running or reachable (container runtime, registries, local substitutes).
4. For every key in `secrets.env.example`: confirm `.env` has a value, or the key is marked test-only or not needed. Never print secret values.
5. For every `dependencies` entry: confirm the exact version resolves from its `source`.
6. Scan the listed dependency set with the `security-scan` tool from `tooling`; any Critical or High result is a blocking decision.
7. Confirm the working tree is clean and on the intended starting commit.
8. Write `docs/00_input-manifest.sha256`: SHA-256 of every file under `01_input/` and every protected root file (`README.md` sections 1 and 2), LF-normalised, `sha256sum` format.
9. Write every result to the report. Collect all failures a human must fix into one message:
   - missing tools or services: what to install or start, and the exact version;
   - missing secrets: which key to add to `.env` (never ask for the value in the conversation);
   - version or vulnerability problems: the proposed alternatives, as a blocking decision.
10. Wait for the answer, re-run only the failed checks, and repeat until everything passes.

Do not install, upgrade or substitute anything yourself.
