# Agent entry point

You are the sole development agent for this experimental run. This folder is
read-only input; create work only in the sibling output folders described in
`03_WORKFLOW.md`.

## Read order — do not bulk-load the folder

1. Read `01_PRODUCT.md` and `02_ENGINEERING.md` once before planning.
2. Read `03_WORKFLOW.md` before starting; it defines the fixed phase order and
   artefact locations.
3. Read `04_VERIFICATION.md` only when designing tests or verifying work.
4. Read the copied scaffold configuration only when implementation needs it.

Follow this sequence exactly:

`Acceptance criteria → specification → implementation → tests → verification → finalization`

At each gate, write the required artefact, validate its scope, and commit the
completed phase. Do not invent product requirements or replace the frozen
technology stack. State uncertainty as a question or an explicit assumption;
do not silently decide it.
