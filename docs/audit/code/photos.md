# Code audit: photos

> No full audit yet: rows from build gates only.
> Build gate 1.0.200: 4 functions checked (#330, which way is good; working tree over e67bae8, before CI ran the tests named).
> Build gate 1.0.200, fix rounds 1 and 2 re-checked: the findings below and the rows they touch. CI on the round-1 tree compiled and passed 739 of 741 tests; CI on the round-2 tree (c4e8a86) passed 740 of 741, including every #330 test; the one failure is the known WholeScreen320Test.librarySearch (#349).
> Build gate 1.0.202: 4 functions checked in this ledger (#370, #371; working tree over 3589faf, before CI ran the tests named). 20 functions across body, analysis, photos and calendar-shell.
> Results: 1 Verified, 4 Read OK, 0 Defect, 1 Risk, 0 Improve, 0 Not checked.

`Verified` here names a test. A green release is what confirms it.

## Files

| File | Read in full |
| --- | --- |
| ui/PhotoViewerScreen.kt | No (build gate: changed functions only) |

## Checks

| Function, class or concern | Check | Where | Result | Evidence | Issue |
| --- | --- | --- | --- | --- | --- |
| compareRows: each change by the measurement's rule | Logic | PhotoViewerScreen.kt:376-390 | Verified | PhotoCompareVerdictTest: down, up, closer, further, default, plain, a goal over plain. 1.0.202 (#371): the signed change and the verdict run from the earlier date to the later one (`da <= db` on yyyy-MM-dd strings), while the left and right columns stay under their photos. theChangeRunsEarlierToLaterWhicheverSideEachPhotoIsOn (forward and reversed give the same change and verdict, the columns swap; the old right-minus-left gives +2 against -2 and fails it) and equalDistanceFromATargetHasNoColourInPhotoCompare (92 and 90 around 91, both orders). With one side missing there is no change and no verdict, as before. Equal dates give the same record both sides, so no change | #330, #371 |
| CompareScreen colours from the user's choice | Android lifecycle | PhotoViewerScreen.kt:331, :344 | Read OK | plainWhenNoGoal() read in composition, so a change shows at once; null uses onSurface. The Swap action now leaves the change and its colour as they were, only the columns move | #330, #371 |
| compareRows' default and CompareRow.delta | Structure | PhotoViewerScreen.kt:374, :376 | Read OK | Resolved in 1.0.202 (#370): `plainNoGoal` has no default and `delta` is gone. grep of app/src: the callers are CompareScreen (plainWhenNoGoal()), shareCompare (Settings.portable.value) and PhotoCompareVerdictTest, each passing it; CompareRow is built in one place, with `better` named; FrameRenderer's `line?.delta` is another type | #370 |
| shareCompare image | Project rules | PhotoViewerScreen.kt:439; FrameRenderer.kt:310-340 | Read OK | 1.0.202: passes the person's plainNoGoal (StateFlow value, read in the coroutine) instead of the old default. The image still draws no verdict colour, so nothing can disagree with the screen | #330, #370 |
| The change note, on screen only | Project rules | PhotoViewerScreen.kt:345-348; strings.xml pv_change_note | Read OK | Build gate 1.0.202. New string, in res, no apostrophe to escape, one values folder; shown under the rows only when there are rows, in bodySmall and onSurfaceVariant (theme roles) | #371 |
| The shared image puts an earlier-to-later change in the right column | Logic | PhotoViewerScreen.kt:439-440; FrameRenderer.kt:332-335 | Risk | Build gate 1.0.202, this build. The image draws each row as label, left value, then "right value  change". When the newer photo is on the left, the change (later minus earlier) now sits beside the older value: Waist 90 left (October), 92 right (September) is drawn "92  -2", which reads as the right photo being 2 lower. The screen explains the direction with pv_change_note; the image has no such note. Trigger: Compare with the newer photo on the left, then Share image or Save to gallery. Fix: give the change its own column or a "since <earlier date>" label in the image, or add the note to the footer. Test: renderCompare's rows for reversed dates carry the direction (a pure test on the Triple built by shareCompare once it's a function) | #371 |
