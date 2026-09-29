---
name: security-review
description: Check the implementation against the security baseline and project security requirements and run the security scanners (phase 6).
---

> Owner: Security officer · Read in: phase 6 · Agent: read-only

- Reads: `general/security/security-baseline.md`, `project/04_security/security-requirements.md`, `general/quality/severity-scale.md`, `project/03_technical/tech-stack.md` (scanner tools), source code
- Writes: findings (`F-nn`) in `docs/06_verification-report.md`

## Procedure

1. For every `SB` and `SR` item, record where it is implemented and how it was checked (test, scan, or inspection). An item without evidence is a finding.
2. Run every scanner listed under `tooling` with a security purpose (dependency, static analysis, secrets); archive raw output in `out/logs/`.
3. Classify each finding with the severity scale.
4. For a dependency vulnerability, check whether the vulnerable feature is used. If you propose lowering a Critical or High on that basis, record the evidence and raise a blocking decision.
5. Check logs and error responses produced during testing for personal data, secrets and internals.
6. For every Critical or High finding, run verify → fix → re-verify, and log each loop.
