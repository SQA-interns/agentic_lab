# Standards

> Owner: Team lead, QA lead, Security officer · Read in: the sections a phase card names · Agent: read-only

Lookup tables for every project. Project additions are in `project/02_design/`.

## Engineering standards

| ID | Standard |
|---|---|
| ES-01 | Every value that differs between environments comes from configuration; secrets have no default in code. |
| ES-02 | Secrets are never committed; `.env` is ignored by git; `project/00_setup/secrets.env.example` lists every key. |
| ES-03 | The repository has `.gitattributes` normalising text files to LF and `.gitignore` excluding build output, dependencies and local config. |
| ES-04 | Dependency versions are exact and lock files are committed. |
| ES-05 | Each component offers one command each to build, test, check (format, lint, type check) and run; its README lists them. |
| ES-06 | Root README: purpose, components, quick start, documentation map. Component README: prerequisites, configuration (settings and secrets, with source), build, run, test, troubleshooting. |
| ES-07 | Logs contain no secrets or personal data; errors shown to users contain no internal details. |
| ES-08 | Persistent data structures are versioned (migrations or equivalent). |
| ES-09 | Deployable services expose health and readiness information. |
| ES-10 | Changes are limited to the task; do not refactor unrelated code. |

## Tests

| Level | Written in phase | Written from | Frozen | Location |
|---|---|---|---|---|
| Acceptance | 3 | acceptance criteria + contracts | yes, from the freeze commit | path contains `acceptance` |
| End-to-end | 3 | acceptance criteria | yes, from the freeze commit | path contains `e2e` |
| Unit / integration | 5 | implementation | no | any other test path |

- Do not test acceptance behaviour through internals; use the component's public interface (API, UI, CLI, messages).
- Do not depend on live external services in automated tests; use the local substitutes in `project/00_setup/environments.md` and list the real services in the release notes for manual testing.
- Do not leave flaky, skipped or disabled tests; fix them or record a decision.
- Do not weaken an assertion to make an incorrect implementation pass.
- Do not edit, delete or weaken a test listed in `docs/03_acceptance-manifest.sha256`.
- Test names contain the AC id they verify.

## Security baseline

| Component type | Standard | Default level |
|---|---|---|
| Web app, API, service | OWASP ASVS (latest) | Level 1; Level 2 when handling authentication, payments or sensitive personal data |
| Mobile app | OWASP MASVS (latest) | L1 |
| Desktop app, CLI, library | Applicable OWASP ASVS chapters | Level 1 |

| ID | Control |
|---|---|
| SB-01 | All input is validated on the trusted side (server or core), whatever the client does. |
| SB-02 | Every non-public operation checks authentication and authorization. |
| SB-03 | Credentials are stored only as salted, slow hashes; secrets come from configuration (ES-01, ES-02). |
| SB-04 | Data in transit uses TLS outside the local machine. |
| SB-05 | Output is encoded for its context; queries are parameterised. |
| SB-06 | Public and authentication endpoints are rate limited. |
| SB-07 | Errors and logs expose no internals, secrets or personal data (ES-07). |
| SB-08 | Dependencies are scanned; no known Critical or High vulnerability ships. |
| SB-09 | Source is scanned with static analysis and a secret scanner. |
| SB-10 | Web responses carry security headers (content security policy, framing, content-type options). |
| SB-11 | Deployed processes run with least privilege (e.g. non-root containers). |
| SB-12 | Collect only the personal data a requirement needs; the project lists it. |
| SB-13 | Each personal-data item has a stated purpose and retention period. |
| SB-14 | Consent, where required, is explicit and never preselected. |

## Severity scale

The only levels used anywhere. Critical and High block release.

| Level | Definition | Example |
|---|---|---|
| Critical | Exploitable now, or total loss of a core user story with no workaround | Unauthenticated access to protected data; accepted input that is silently not stored |
| High | Exploitable under realistic conditions, or a core user story degraded for a plausible group of users | Missing authorization check on one operation; a limit that locks out legitimate users |
| Medium | A real defect with limited impact, or a control weaker than required | A dependency vulnerability with no reachable exploit path; a duplicated validation rule |
| Low | Cosmetic, stylistic, or a theoretical or false-positive finding | A lint warning; a scanner match on constant input |

- Tools that report Critical/High/Medium/Low (or CVSS) are counted as reported.
- Lowering a real Critical or High needs written evidence and is a blocking decision. A scanner false positive follows "Non-blocking decisions" in `rules.md`.
- A surviving mutant in security-, validation- or persistence-related code is classified individually.

| Tool type | Tool's level | Maps to |
|---|---|---|
| CVSS-based (dependency scanners) | 9.0–10.0 / 7.0–8.9 / 4.0–6.9 / 0.1–3.9 | Critical / High / Medium / Low |
| Rule-based SAST with ERROR/WARNING/INFO | ERROR / WARNING / INFO | High / Medium / Low |
| Rank or priority 1–5 scales | 1 / 2 / 3–5 | High / Medium / Low |
| Bug-rank 1–20 scales | 1–4 / 5–9 / 10–20 | High / Medium / Low |

## Definition of Done

Each item needs evidence in `docs/06_verification-report.md`. Project additions are `DoD-Pnn`.

| ID | Criterion | Evidence |
|---|---|---|
| DoD-01 | The full test suite passes; frozen test hashes match the manifest | test report, hash check |
| DoD-02 | Format, lint, type check and static analysis report no errors | tool output |
| DoD-03 | Coverage and mutation score are recorded and meet the project thresholds, if any | coverage and mutation reports |
| DoD-04 | Architecture constraints (`AR`) are checked automatically where possible; no dependency cycles | architecture test output |
| DoD-05 | No open Critical or High finding from the security review and scanners | scanner summaries, findings table |
| DoD-06 | Every component runs in its target environment and the core user flows work at runtime, not only in tests | runtime demonstration log |
| DoD-07 | Every AC traces to at least one test and one commit | traceability table |
| DoD-08 | Root and component READMEs (ES-06) work when followed from a clean checkout | clone log |
| DoD-09 | Release notes list everything a human must test manually | `docs/release-notes.md` |
| DoD-10 | Every decision has a resolution or is listed as pending review; inputs and protected files are unchanged | decisions log, `docs/00_input-manifest.sha256` check |
| DoD-11 | The evidence checks on the phase 6 card hold | `general/phases/6-verify.md`, "Evidence" |
