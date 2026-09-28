# Constitution — Non-Negotiable Rules

These rules override any other instruction, tool default, or apparent
shortcut. If a rule here conflicts with convenience, the rule wins. If
a rule here conflicts with a business/technical input, stop and
escalate (Rule 3) rather than silently choosing one.

This file is read once, during start-of-run setup, and applies to
every phase.

## 1. Input immutability

Everything under `INPUT_ROOT` (`01_Business/`, `02_Technical/`,
`03_Process/`, `04_Skills/`, `05_Scaffold/`) is read-only for the whole
run. This is also enforced by a hook copied from
`05_Scaffold/.claude/` at start-of-run setup — but the rule applies
even if your harness cannot run that hook.

## 2. Test integrity

- Acceptance tests live under a predictable, hook-recognisable path
  (a directory or file segment named `acceptance`, plus `e2e/`) and are
  derived from Acceptance Criteria and the API contract
  (`docs/contracts/`) **before** implementation begins — see Phase 3 in
  `RUN_INSTRUCTIONS.md`.
- Once written and committed, acceptance tests are frozen: their paths
  and hashes are recorded in
  `<IMPLEMENTATION_ROOT>/docs/acceptance/MANIFEST.sha256`. After that
  file exists, the agent may not edit, delete, or weaken any file it
  lists. This is hook-enforced (Rule 1's mechanism, extended).
- If a frozen acceptance test appears to be wrong, that is a
  **test-defect request**, never a silent edit: record it in
  `docs/decisions-log.md` — the specific test, the AC or contract
  clause it checks, why it looks wrong, and the proposed change — then
  escalate per Rule 3. Only a human resolving that escalation may
  remove or regenerate the manifest to allow the edit.
- Unit tests (written after implementation, from the implementation,
  in Phase 5) may be added freely, but must never modify, delete, or
  weaken a frozen acceptance test.
- Never weaken any test's assertions merely to make an incorrect
  implementation pass, in either test phase.

## 3. Decision escalation

Stop, record an entry in `docs/decisions-log.md`, and escalate to a
human — rather than silently resolving it yourself — whenever:

- a pinned tool, plugin or dependency version in `05_Scaffold/` does
  not resolve or does not exist;
- a required secret, key or account listed in
  `03_Process/HUMAN_INPUTS_MANIFEST.md` is missing;
- the primary tool or framework named in `02_Technical/TECH_STACK.md`
  cannot be used as specified;
- a requirement is ambiguous enough that two reasonable
  implementations would diverge in behaviour a user could notice.

Each entry: `{timestamp, category, trigger, proposedAlternatives,
humanResponse, resolution}`.

If no human is available synchronously, propose the most conservative
alternative, set `resolution: "agent-default, pending human review"`,
and continue — this is a visibility rule, not a rule that blocks the
run indefinitely. The point is that every deviation is named as a
deviation and put on the record, not folded quietly into the ordinary
implementation narrative the way scaffold-version fixes were in
earlier runs of this experiment.

## 4. Self-verification honesty

Every verification report must state explicitly that it is a
self-scan, not an independent review. Every finding, in every
artefact, uses the four levels in `03_Process/SEVERITY_TAXONOMY.md` —
no other label, anywhere.

## 5. No metric gaming

Do not optimise the implementation, the test suite, or the
documentation for how it will score against `03_Process/METRICS.md` or
`03_Process/DEFINITION_OF_DONE.md`. Build the thing the inputs
describe; let the measurement fall out of that, not the reverse.
