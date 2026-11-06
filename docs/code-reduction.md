# Code reduction: estimates vs. actuals

Retrospective on the core compaction epic (#46) and the desktop compaction epic (#52).
Estimates come from the issue bodies; actuals are the change in the `prod` column of
`loc-history.csv` from the previous merge to the step's merge commit. The CSV is
generated and git-ignored, so the numbers are copied here.

## Per step (production lines)

| Step | Estimate | Actual | Verdict |
|---|---|---|---|
| core 1: JsonNodes helpers (#53) | ~40 | -194 | Beat it by a lot; the estimate was badly off. |
| core 2: NamedObjects (#54) | ~250 | -111 | Less than half. |
| core 3: ComponentSection (#55) | ~200 | -71 | About a third. |
| core 4: Check interface (#57) | 80-100 | -7 | Missed. |
| core 6: comment cleanup (#60) | not estimated | 0 | Nothing measurable. |
| desktop 1: referable list (#61) | 400-500 | -256 | About half. |
| desktop 2: component list pane (#62) | 350-450 | -398 | On target. |
| desktop 3: data-driven outline (#63) | 150-200 | -46 | About a quarter. |
| desktop 4: field helpers (#64) | 80-120 | -30 | About a third. |

## Totals

- **Production:** 10,190 -> 9,134, a reduction of 1,056 lines. The two epics projected
  about 1,800 (core ~600, desktop ~1,200).
- **Tests:** 9,509 -> 9,701, up about 190 lines. The projection was a cut of 1,200-2,400.
  The test-collapse steps (#44, #51) either were not done or reduced nothing. #44 is
  ticked in the epic but the CSV shows no drop; the cause has not been checked.
- **Overall:** 25,743 -> 24,759 total lines, a 3.8% reduction.

## Evaluation

- The direction was right and the refactors held up: behaviour was preserved and the
  suite stayed green.
- The estimates were not reliable. Of nine steps, one hit its estimate, and the typical
  result was 30-50% of it.
- The Check interface (core 4) is the clearest miss. The commit message itself says the
  two tree walks differ too much to merge, and that was most of the estimated saving.
  This was foreseeable when estimating.
- Estimates counted duplicated lines and ignored what the shared abstraction costs. The
  generic base, the descriptor and the thin wrappers kept for compatibility all add
  lines back. Keeping the old method names so desktop stayed untouched was a deliberate
  choice that limited core's savings.
- Future estimates should be 40-60% of the first figure. The remaining test
  consolidation (~1,300 lines across #44 and #51) is where most of the real saving is.
