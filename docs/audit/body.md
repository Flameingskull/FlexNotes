# Function audit: body

> No full audit yet: rows from build gates only.
> Build gate 1.0.199: 9 functions checked (#328, graph ranges; working tree over 527d379). Written by the main session from the auditor's report (the harness stopped the subagent writing files); re-checked after fix rounds 1 and 2.
> Results: 4 Proved, 5 Read OK, 0 Fails, 0 Gap, 0 Not checked.

`Proved` here names a test that drives the function. A green release is what confirms it.

## Files

| File | Read in full |
| --- | --- |
| ui/BodyScreen.kt | No (build gate: changed functions only) |

## Functions

| Function | Expected | Where | Result | Evidence | Issue |
| --- | --- | --- | --- | --- | --- |
| Body graph ranges | Each range's own period from today | BodyScreen.kt:454-455 | Proved | BodyGraphRangeTest.eachRangeDrawsItsOwnPeriodFromToday, theOldDefinitionOfTheLatestValueNoLongerDecides |  |
| Body All time | First value to latest, FitNotes and typed | BodyScreen.kt:455 | Proved | BodyGraphRangeTest.eachRangeDrawsItsOwnPeriodFromToday (All step) |  |
| Body axis spans the period | Inline and full screen | BodyScreen.kt:535, :579 | Read OK | period passed |  |
| Body empty range | Panel with the last day, Show all time | BodyScreen.kt:519 | Proved | BodyGraphRangeTest.aRangeWithNothingInItSaysSoAndAllTimeBringsBackTheGraph |  |
| Body full screen with an empty range | Same panel as inline | BodyScreen.kt:562 | Proved | BodyGraphRangeTest.anEmptyRangeShowsTheSamePanelInFullScreen |  |
| Body stats block | Start, latest, change, low, high, entries in the range | BodyScreen.kt:593 | Read OK | Uses shown |  |
| Body trend | Over the period | BodyScreen.kt:530 | Read OK |  |  |
| Medication and symptom overlay | Within the period (medication graphs use this pane) | BodyScreen.kt:466-482 | Read OK |  |  |
| Body full screen reset | On a range change | BodyScreen.kt:542 | Read OK | resetKey = rangeIdx |  |
