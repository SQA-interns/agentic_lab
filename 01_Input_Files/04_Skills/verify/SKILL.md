---
name: verify
description: Verify the complete solution independently against requirements.
---

# Procedure

Re-read:

- User Stories;
- Business Rules;
- Form Schema;
- Acceptance Criteria;
- Specification;
- all technical constraints.

Execute every Definition of Done check, including the mutation-testing
pass it requires (§4a).

Confirm the acceptance-test freeze held: every file listed in
`docs/acceptance/MANIFEST.sha256` still hashes to the value recorded
there. A mismatch is itself a Critical finding, regardless of why the
file changed.

Confirm `docs/decisions-log.md` accounts for every escalation raised
during the run (`CONSTITUTION.md` §3) and that none was silently
resolved instead.

Inspect specifically for:

- missing requirements;
- incorrect requirements;
- unrequested functionality;
- security failures;
- architecture violations;
- regressions;
- runtime/container failures;
- surviving mutants in security- or domain-critical code that the
  automated suite should have caught (§4a).

Classify every finding using exactly the four levels in
`03_Process/SEVERITY_TAXONOMY.md` — Critical, High, Medium, Low.

For every Critical or High failure:

Verify → Fix → Re-verify

Record each loop separately. Medium and Low findings are recorded and
triaged (fixed, accepted with written justification, or logged as an
open item) but do not require a loop by themselves.

State explicitly, in the report itself, that this is a self-scan and
not an independent review (`CONSTITUTION.md` §4).

## Output

Create exactly:

`<IMPLEMENTATION_ROOT>/docs/verification-report.md`

Update `fixLoops` and `verificationFindings` in:

`<STATISTICS_ROOT>/run-log.json`
