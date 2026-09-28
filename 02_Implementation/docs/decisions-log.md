# Decisions Log

Every escalation raised during this run, per `CONSTITUTION.md` §3.
Each entry has `{timestamp, category, trigger, proposedAlternatives,
humanResponse, resolution}`. Entries are mirrored in
`03_Run-Statistics/run-log.json` → `escalations`.

The run was started with an instruction to work autonomously and to
stop only for tool/version/secret substitutions. Requirement
ambiguities are therefore resolved with the most conservative
alternative and marked `agent-default, pending human review`, as §3
permits when no human is available synchronously.

---

## D-1 — Option availability for students

- **timestamp:** 2026-09-28T23:28:57Z
- **category:** requirement-ambiguity
- **trigger:** US-002 says a student registers "so that I can attend
  the conference and activities available to students". No input
  defines per-registration-type eligibility for options; FORM_SCHEMA
  gives options only an identifier, display name and active status.
  One implementation would restrict some options to one type, another
  would offer every active option to both — a visible difference.
- **proposedAlternatives:**
  1. Every active option is available to both registration types
     (no extra attribute invented). *(conservative — chosen)*
  2. Add a per-option "available to" attribute (external / student /
     both) and filter by it.
- **humanResponse:** none (autonomous run; no synchronous human)
- **resolution:** agent-default, pending human review — alternative 1.

## D-2 — Which mandatory consents exist

- **timestamp:** 2026-09-28T23:28:57Z
- **category:** requirement-ambiguity
- **trigger:** FORM_SCHEMA "Consent" requires support for mandatory
  consent fields "where required" but does not name any.
- **proposedAlternatives:**
  1. One mandatory, non-preselected consent on both forms: consent to
     processing of the participant's personal data for the purpose of
     conference registration. *(conservative — chosen; it is the
     minimum that makes "mandatory consent" observable and matches
     storing personal data)*
  2. No consent at all (the rule would then be untestable).
  3. Several consents (e.g. photography, publication of attendee
     list) — invents requirements.
- **humanResponse:** none (autonomous run; no synchronous human)
- **resolution:** agent-default, pending human review — alternative 1.

## D-3 — Email/notification failure after a stored registration

- **timestamp:** 2026-09-28T23:28:57Z
- **category:** requirement-ambiguity
- **trigger:** US-004/US-005 require confirmation after successful
  storage; US-006/US-007 require emails. No input says what happens
  when the registration is stored but an email cannot be sent.
- **proposedAlternatives:**
  1. Registration stays accepted; the email failure is recorded for the
     operator and the participant still sees the confirmation.
     *(conservative for US-005 — chosen; no stored registration is lost
     or reported as failed)*
  2. Roll back the registration and report failure.
- **humanResponse:** none (autonomous run; no synchronous human)
- **resolution:** agent-default, pending human review — alternative 1.

## D-4 — Number of option selections

- **timestamp:** 2026-09-28T23:28:57Z
- **category:** requirement-ambiguity
- **trigger:** No input limits or requires option selections (e.g. at
  most one meal, at least one workshop).
- **proposedAlternatives:**
  1. Any number of active options, including none. *(chosen — invents
     no rule)*
  2. Per-set min/max limits.
- **humanResponse:** none (autonomous run; no synchronous human)
- **resolution:** agent-default, pending human review — alternative 1.

## D-5 — Duplicate registrations with the same email

- **timestamp:** 2026-09-28T23:28:57Z
- **category:** requirement-ambiguity
- **trigger:** No input says whether the same email may register more
  than once. Rejecting vs. accepting duplicates is user-visible.
- **proposedAlternatives:**
  1. Accept every valid submission as its own registration; the
     organizer sees duplicates in the export. *(chosen — invents no
     rule and never loses a registration)*
  2. Reject a second registration with the same email.
- **humanResponse:** none (autonomous run; no synchronous human)
- **resolution:** agent-default, pending human review — alternative 1.
