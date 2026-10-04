# Run configuration

> Owner: Human operator · Read in: phase 0 · Agent: read-only

The only file a human fills before starting the agent; the agent checks everything else in phase 0 (`general/phases/0-preflight.md`) and asks for what is missing.

- Model: claude-opus-5-5
- Effort: medium
- Template version: tanej-1.1
- Run ID: tanej-02_conference-registration_opus5.5_sdd_template-tanej-1.1
- Unattended: yes (no human answers during the run; blocking decisions take the proposed default and are recorded for review, see "Unattended runs" in `general/rules.md`)
- Starting commit: git HEAD when the agent starts (recorded in phase 0; it must contain these filled inputs)
