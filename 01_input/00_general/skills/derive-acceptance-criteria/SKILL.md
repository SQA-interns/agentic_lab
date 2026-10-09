---
name: derive-acceptance-criteria
description: Derive testable acceptance criteria from the user stories and business rules (phase 1).
---

> Owner: Product owner · Read in: phase 1 · Agent: read-only

## Do

1. Derive acceptance criteria `AC-nnn-nn` from the stories: Given/When/Then, one observable behaviour each, covering the accepted case, each rejection and each business edge the stories and rules state. Each cites its `US` and any `BR`.
2. Write them as one table per story: `| AC | BR | Given | When | Then |`. No other text.
3. For every unanswered open question (`OQ`), record a non-blocking decision with the more conservative behaviour and write the criteria accordingly.

## Do not

- Do not invent behaviour that no story or rule states; record the gap as a decision.
- Do not name a technology, endpoint or data structure in a criterion.
