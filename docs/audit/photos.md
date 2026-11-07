# Function audit: photos

> No full audit yet: rows from build gates only.
> Build gate 1.0.200: 3 functions checked (#330, Photo Compare colours; working tree over e67bae8, before CI ran the tests named).
> Results: 1 Proved, 1 Read OK, 1 Fails, 0 Gap, 0 Not checked.

`Proved` here names a test that drives the function. A green release is what confirms it.

## Files

| File | Read in full |
| --- | --- |
| ui/PhotoViewerScreen.kt | No (build gate: changed functions only; `CompareScreen`, `compareRows` and `shareCompare` read in full) |

## Functions

| Function | Expected | Where | Result | Evidence | Issue |
| --- | --- | --- | --- | --- | --- |
| Compare row verdict | Each body value judged by its direction, closer to a specific value, or the Plain choice with none | PhotoViewerScreen.kt:372-383 | Proved | PhotoCompareVerdictTest (7 tests, through compareRows on a snapshot) |  |
| Compare screen change colour | Same colour as the body tracker for the same move; the Settings choice applies at once; signed number stays | PhotoViewerScreen.kt:331, :344 | Read OK | compareRows(snap, da, db, plainWhenNoGoal()) then verdictColour(row.better, onSurface). No screen test reads the colour. The shared image (:433) has no colour, so it needs no choice |  |
| Compare with the newer photo on the left | Change and colour describe progress from the earlier date to the later one | PhotoViewerScreen.kt:378-382 | Fails | Already there (the sign predates this build, the goal colour came with #303). The change is always right minus left, so after Swap, or picking the newer photo first, a waist that fell from 92 to 90 cm reads +2 and, with Down is good, red | #371 |
