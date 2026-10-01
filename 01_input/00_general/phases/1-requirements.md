# Phase 1: Requirements

> Owner: Product owner · Read in: phase 1 · Agent: read-only

- Read: `project/01_requirements/*`
- Write: `docs/01_acceptance-criteria.md`

## Do

1. Derive acceptance criteria `AC-nnn-nn` from the stories: Given/When/Then, one observable behaviour each, covering the accepted case, each rejection and each business edge the stories and rules state.
2. Write them as one table per story: `| AC | BR | Given | When | Then |`. No other text.
3. For every unanswered open question (`OQ`), record a non-blocking decision with the more conservative behaviour and write the criteria accordingly.

## Do not

- Do not invent behaviour that no story or rule states.
- Do not name a technology, endpoint or data structure in a criterion.

## Gate

- Every story has at least one AC; every AC cites its story.
- Every open question is answered or has a decision.

## Commits

One for the document.
