# Workflow: specify, build in slices, verify, report

This is spec-first development with incremental implementation and testing.
It is not the original experiment's strict implementation-before-tests protocol.

## 1. Establish the contract before production code

Create `02_Implementation/docs/specification.md` containing:
- Observable Given–When–Then acceptance criteria, stable IDs
  `AC-<US>-<nn>`, traced to US-001…US-008; rejection and relevant edge cases.
- API requests/responses/errors, data model/migrations, module boundaries,
  backup partial-failure/recovery, email retry, idempotency, auth, captcha,
  option configuration, and deployment. Reference AR/ST IDs rather than
  copying the input documents.
- Material implementation choices, reasons, and a bounded slice plan.
- Acceptance-to-verification mapping and intended commands.

Check the specification against every fixed input. Do not invent features or
reopen settled architectural decisions. Do not require human sign-off for
ordinary conforming choices. Resolve genuine contradictions explicitly.
Fix cross-cutting interfaces/invariants and the milestone map initially;
detail each slice before implementing it. Do not design every internal class
up front. List material unknowns with consequences; isolate blocked work and
proceed on independent work. Consent policy/legal wording and production
secrets cannot be guessed into a claim of production readiness.

Preflight runtime, Docker, dependency access, browser tooling, scanner databases
and permissions. Record blockers early. Obey runner-supplied time/resource
limits; absent limits are unspecified, not an invented budget. At termination
preserve partial evidence and unfinished gates. Do not repeatedly rerun a
blocked external service without changed conditions or diagnostic purpose.

## 2. Implement and verify bounded slices

A milestone is a fixed observable outcome below. A work slice is a smaller
change that advances one milestone and has its own check. One milestone may
need several slices and commits. Do not equate a slice with a file, layer,
token allowance, session, or fixed number of lines.

Before editing, put a short slice entry in the specification: ID, target
milestone, behavior/invariant, exclusions, dependencies, and verification.
Split it if it contains independently testable outcomes with separate failure
or rollback boundaries. A shared migration may be a prerequisite slice.
Avoid speculative infrastructure for later features.

| Milestone | Dependencies | Completion contract and minimum evidence |
|---|---|---|
| M0 Runnable foundation | Initial specification | Both production builds pass; frontend reaches backend; backend readiness and a PostgreSQL migration/integration check pass. Generated setup/check commands are reproducible. This is an enabling milestone, not delivered registration functionality. |
| M1 External acceptance | M0 | External form reaches real API, PostgreSQL and persistent JSON; backend acceptance precedes UI confirmation; pending notification intent is durable. Valid Unicode input works; missing/blank fields, invalid email, invalid captcha and unknown/inactive selections cause no accepted registration. One real browser journey plus boundary/API/storage checks. No email-delivery claim yet. |
| M2 Student acceptance | M1 | Student fields and available options work through the same real stack; required student fields are enforced; the external journey still passes. Browser journey and backend rejection checks. |
| M3 Configuration and consent | M1 | Change option configuration, restart, and demonstrate the changed catalog without recompilation or changed fixed fields. Retired/unknown IDs are rejected. Demonstrate unchecked required consent and rejection when absent using a synthetic required-consent fixture; do not invent legal wording. Recheck both forms. |
| M4 Notifications | M1 | Real local SMTP capture contains participant confirmation and organizer notification with matching JSON attachment. SMTP outage preserves acceptance and pending work; recovery delivers pending mail. Report duplicate-delivery semantics honestly. Use integration evidence, not only mocked MailSender calls. |
| M5 Organizer export | M1 | Authorized export produces a readable Excel workbook containing accepted records from both forms (M2 is therefore required for final verification); unauthorized access fails without data disclosure. Parse workbook contents in a check. |
| M6 Release candidate | M0–M5 | All DoD gates assessed against the final revision, including container recreation, storage partial failure/recovery, duplicate-request handling, production captcha isolation, and complete regression. All gates must pass to mark M6 pass. |

Use M0 then M1; afterwards choose dependency-respecting order. M5 implementation
may start after M1, but M5 cannot pass before M2 evidence exists. Milestone IDs
and completion contracts are fixed; internal slice decomposition is autonomous.
Record additional slice IDs under their milestone, e.g. M1-a, without treating
their count as productivity. These are experimental milestones, not a claim
that seven stages are universally optimal.

Security, acceptance ordering, duplicate handling and storage consistency must
be designed with M1; do not retrofit their contracts in M6. A later exhaustive
failure probe does not excuse known earlier violations. All fixtures are
synthetic. Deterministic captcha and isolated SMTP are permitted substitutes;
the actual UI/API/PostgreSQL/filesystem path is not mocked at milestone gates.

A slice closes only when its checks pass, relevant earlier checks still pass,
the spec matches the change, and evidence/checkpoint is recorded. If blocked,
record the blocker and continue only independent work; never label it complete.
Use focused unit/API/integration/component checks as appropriate; use browser
checks for delivered user journeys. Do not run every heavyweight scanner after
every edit. Run full required checks at finalization and earlier when risk warrants.
Reuse commands and small scripts rather than rebuilding shell sequences.

Record evidence-backed milestones using `METRICS.md`. When a check fails,
form a concrete explanation, make a targeted change, and rerun the relevant
check. After two unchanged failures, inspect the cause/environment instead of
blindly repeating the command. Log substantive repair episodes, not every edit.

Update the specification when an implementation detail changes. A change to
a fixed input requires researcher authorization; record it as a deviation
and a condition change. Never weaken correct checks just to pass.

## Resume and rollback

Keep a short current-state section in the generated specification: active slice,
last verified revision, last check, blocker, and next action. Update it at slice
boundaries and before stopping; metrics remain the evidence history. On resume,
read START_HERE, this state, relevant contracts, and inspect actual Git/files;
run a smoke check before trusting a stale completion claim. Do not reread all
logs or repeat completed planning without evidence it is stale.

Before risky edits, preserve a checkpoint when Git is available. Revert only
the system's relevant changes and retain failure evidence; never reset unrelated
work. A code revert does not undo migrations, files, emails or external effects.
Use isolated test data and document recovery for these effects. Local checkpoints
do not imply permission to push.

## 3. Complete verification and reporting

Run all `DEFINITION_OF_DONE.md` gates against the final implementation.
Capture the first complete suite before its repairs; later reruns may run
only impacted tests, but final verification must run the complete required suite.
Fix failures within scope; record remaining blocked or failed gates honestly.

Write `02_Implementation/README.md` with setup/run/check commands, environment
variables, local versus production modes, and operational limitations.
Finish `03_Metrics/run.json` and `run-summary.md` using `METRICS.md`.
The run summary is the verification report; do not produce duplicate handoff,
release-note, test-report, and verification-report documents.

Keep coherent Git checkpoints per completed slice and before risky changes
when the runner supplies Git access. Record SHA and phase; do not push/merge
unless the runner explicitly requests it. Git metadata is managed outside
the four-directory layout. If Git is unavailable, record that and use a
content hash of the final implementation. Capture the implementation revision
before finalizing metrics so the report does not refer to its own commit.
