# Security standards

> Owner: Security officer · Fill: once per organisation; review when OWASP publishes a new ASVS or the severity policy changes · Read in: phases 0, 2, 6 · Agent: read-only

Project requirements (`SR`) and the chosen level are in `project/constraints.md` ("Security").

## Verification standard

| Component type | Standard | Default level |
|---|---|---|
| Web app, API, service | OWASP ASVS (latest) | Level 1; Level 2 when handling authentication, payments or sensitive personal data |
| Mobile app | OWASP MASVS (latest) | L1 |
| Desktop app, CLI, library | Applicable OWASP ASVS chapters | Level 1 |

## Baseline controls

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
| SB-09 | Source is scanned with static analysis and a secret scanner. The secret scanner covers the working tree and the history of the current branch; other branches belong to other runs. |
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
- Lowering a real Critical or High needs written evidence and is a blocking decision. A scanner false positive is non-blocking (`skills/decisions`).
- A surviving mutant in security-, validation- or persistence-related code is classified individually.

| Tool type | Tool's level | Maps to |
|---|---|---|
| CVSS-based (dependency scanners) | 9.0–10.0 / 7.0–8.9 / 4.0–6.9 / 0.1–3.9 | Critical / High / Medium / Low |
| Rule-based SAST with ERROR/WARNING/INFO | ERROR / WARNING / INFO | High / Medium / Low |
| Rank or priority 1–5 scales | 1 / 2 / 3–5 | High / Medium / Low |
| Bug-rank 1–20 scales | 1–4 / 5–9 / 10–20 | High / Medium / Low |
