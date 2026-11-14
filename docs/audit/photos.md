# Function audit: photos

> No full audit yet: rows from build gates only.
> Build gate 1.0.200: 3 functions checked (#330, Photo Compare colours; working tree over e67bae8, before CI ran the tests named).
> Build gate 1.0.202: 6 functions checked (#370 and #371, Photo Compare; working tree over 3589faf, before CI ran the tests named).
> Build gate 1.0.204: 1 function checked (#334, the photo comment sheet; working tree over 8a66dc5).
> Results: 3 Proved, 3 Read OK, 0 Fails, 1 Gap, 0 Not checked.

`Proved` here names a test that drives the function. A green release is what confirms it.

## Files

| File | Read in full |
| --- | --- |
| ui/PhotoViewerScreen.kt | No (build gate: changed functions only; `CompareScreen`, `compareRows`, `shareCompare` and the comment sheet in `PhotoViewerScreen` read in full) |

## Functions

| Function | Expected | Where | Result | Evidence | Issue |
| --- | --- | --- | --- | --- | --- |
| Compare row verdict | Each body value judged by its direction, closer to a specific value, or the Plain choice with none | PhotoViewerScreen.kt:376-390 | Proved | PhotoCompareVerdictTest (9 tests, through compareRows on a snapshot). 1.0.202 (#370): compareRows takes the choice with no default and CompareRow has no delta |  |
| Compare screen change colour | Same colour as the body tracker for the same move; the Settings choice applies at once; signed number stays | PhotoViewerScreen.kt:331, :344 | Read OK | compareRows(snap, da, db, plainWhenNoGoal()) then verdictColour(row.better, onSurface). No screen test reads the colour |  |
| Compare with the newer photo on the left | Change and colour describe progress from the earlier date to the later one | PhotoViewerScreen.kt:383-388 | Proved | Fixed in 1.0.202 (#371): earlier and later chosen by date (da <= db), so after Swap a waist from 92 to 90 cm reads -2 and, with Down is good, green. PhotoCompareVerdictTest.theChangeRunsEarlierToLaterWhicheverSideEachPhotoIsOn (same change both ways, columns stay under their photos) | #371 |
| The screen says which way the change runs | A line under the body values says the change runs from the earlier photo to the later one | PhotoViewerScreen.kt:348-351; strings.xml pv_change_note | Read OK | Shown whenever there are body value rows, above the nearest-value note. No test asserts it |  |
| Equal distance from a specific value in Photo Compare | No colour, either order | PhotoViewerScreen.kt:388 | Proved | PhotoCompareVerdictTest.equalDistanceFromATargetHasNoColourInPhotoCompare (92 and 90 around 91, both orders) | #371 |
| Shared or saved compare image's change | The figure reads the same way as the screen and says which way it runs | PhotoViewerScreen.kt:439-440; FrameRenderer.kt:332-335 | Gap | This build (#371). The image writes "<right value>  <change>" under the right photo, and the change now runs earlier to later. After Swap (newer photo left: 90 cm, right: 92 cm), the image's right column reads "92  -2", as if the right-hand 92 had fallen by 2; before this build it read "92  +2" (right minus left). The image has no line saying the change runs from the earlier photo, as the screen now has. Expected: the change beside the later photo's value, or the screen's note in the footer | #371 |
| Photo comment sheet keeps a typed comment | A changed comment survives a tap outside, a drag and Back; Cancel and Save still close | PhotoViewerScreen.kt:236-242 | Read OK | `rememberDirty(text.trim())` passed as `dirty`; the shared guard is proved in the body ledger. No test opens this sheet. Already there, not this build: `editNote` is saved (:116) but `text` is `remember(current.id)` (:235), so turning the phone reopens the sheet with the old comment and the typing is lost | #334 |
