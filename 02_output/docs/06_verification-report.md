# Verification report

> Written in: phase 6 · Source: `general/standards.md`, `project/02_design/*` · Procedure: `general/phases/6-verify.md` · Agent: writes

This is a self-check by the development agent, not an independent review.

## Definition of Done

| DoD | Result | Evidence |
|---|---|---|

## Runtime demonstration

| Component | Environment | Flows exercised | Result |
|---|---|---|---|

## Traceability

| AC | Tests | Commits |
|---|---|---|

## Findings

| F | Severity | Source | Finding | Resolution |
|---|---|---|---|---|
| F-01 | High as found (Semgrep ERROR); lowered to Low by D-16 | Semgrep 1.177.0, rule `yaml.openapi.security.use-of-basic-authentication`, `docs/02_contracts/openapi.yaml` (`organizerBasic`), found in phase 3, raw report `logs/3_semgrep-raw.log` | The organizer export uses HTTP Basic authentication. | Kept by human decision D-16 (2026-10-02T11:28:29Z). Evidence: credentials are refused over plain HTTP before they are read (SR-06); one account, stored only as a bcrypt hash; failed logins are rate limited; no session or cookie; identity providers are out of scope (`security-requirements.md`). That line only is suppressed with the D-16 id. |
| F-02 | High as found (Semgrep ERROR) | Semgrep 1.177.0, rule `generic.nginx.security.missing-internal`, `frontend/nginx.conf`, found in phase 4, `logs/4_semgrep.log` history | The `/api` route forwarded to a destination held in a variable, which the rule treats as possible request forgery. | Fixed in phase 4 (D-18): the destination is the fixed host name `backend`; re-scan 0 findings. |
| F-03 | Low (false positive) | gitleaks v8.30.1, rule `generic-api-key`, 5 matches in `logs/3_backend-test.log` at commit `7dec4b8`, raw report `logs/4_gitleaks-raw.log` | The generated start-up password of the bootstrap application's throwaway user was printed into a committed test log. | D-17: not a credential of the shipped system; fingerprints ignored with the decision id, current log redacted. |

## Fix loops

| Loop | Findings | Change | Re-verification |
|---|---|---|---|
