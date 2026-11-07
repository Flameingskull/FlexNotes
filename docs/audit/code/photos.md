# Code audit: photos

> No full audit yet: rows from build gates only.
> Build gate 1.0.200: 4 functions checked (#330, which way is good; working tree over e67bae8, before CI ran the tests named).
> Build gate 1.0.200, fix rounds 1 and 2 re-checked: the findings below and the rows they touch. CI on the round-1 tree compiled and passed 739 of 741 tests; CI on the round-2 tree (c4e8a86) passed 740 of 741, including every #330 test; the one failure is the known WholeScreen320Test.librarySearch (#349).
> Results: 1 Verified, 2 Read OK, 0 Defect, 0 Risk, 1 Improve, 0 Not checked.

`Verified` here names a test. A green release is what confirms it.

## Files

| File | Read in full |
| --- | --- |
| ui/PhotoViewerScreen.kt | No (build gate: changed functions only) |

## Checks

| Function, class or concern | Check | Where | Result | Evidence | Issue |
| --- | --- | --- | --- | --- | --- |
| compareRows: each change by the measurement's rule | Logic | PhotoViewerScreen.kt:372-383 | Verified | PhotoCompareVerdictTest: down, up, closer, further, default, plain, a goal over plain | #330 |
| CompareScreen colours from the user's choice | Android lifecycle | PhotoViewerScreen.kt:331, :344 | Read OK | plainWhenNoGoal() read in composition, so a change shows at once; null now uses onSurface, where it used gold before | #330 |
| compareRows' default and CompareRow.delta | Structure | PhotoViewerScreen.kt:370, :372 | Improve | plainNoGoal defaults to false, so a new caller that forgets it ignores the person's choice without a compile error; and CompareRow.delta has no reader left. Make the parameter required and drop delta | #370 |
| shareCompare image | Project rules | PhotoViewerScreen.kt:433; FrameRenderer.kt:321-338 | Read OK | Uses the default rule, but the image draws the change in ivory and muted only, so no verdict colour is shown to disagree | #330 |
