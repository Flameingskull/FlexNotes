# Code audit: body

> No full audit yet: rows from build gates only.
> Build gate 1.0.199: 2 functions checked (#328, graph ranges; working tree over 527d379). Written by the main session from the auditor's report (the harness stopped the subagent writing files); re-checked after fix rounds 1 and 2.
> Results: 3 Verified, 4 Read OK, 0 Defect, 0 Risk, 0 Improve, 0 Not checked.

`Verified` here names a test. A green release is what confirms it.

## Files

| File | Read in full |
| --- | --- |
| ui/BodyScreen.kt | No (build gate: changed functions only) |

## Functions

| Function, class or concern | Check | Where | Result | Evidence | Issue |
| --- | --- | --- | --- | --- | --- |
| BodyGraphPane values filtered to the period | Logic | BodyScreen.kt:454-455 | Verified | BodyGraphRangeTest.eachRangeDrawsItsOwnPeriodFromToday | #328 |
| Empty range offers All time | Logic | BodyScreen.kt:519 | Verified | BodyGraphRangeTest.aRangeWithNothingInItSaysSoAndAllTimeBringsBackTheGraph | #328 |
| Full screen shows the empty-range panel | Logic | BodyScreen.kt:562 | Verified | BodyGraphRangeTest.anEmptyRangeShowsTheSamePanelInFullScreen | #328 |
| Overlay limited to the period's ends | Logic | BodyScreen.kt:466-468 | Read OK |  | #328 |
| Full-screen resetKey and period | Android lifecycle | BodyScreen.kt:542, :579 | Read OK |  | #328 |
| StatsBlock covers the shown values | Logic | BodyScreen.kt:597 | Read OK |  | #328 |
| Removing inRange left no callers | Structure | BodyScreen.kt:87-89 | Read OK | grep of app/src | #328 |
