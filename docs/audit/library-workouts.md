# Function audit: library-workouts

> No full audit yet: rows from build gates only.
> Build gate 1.0.199: 14 functions checked (#328, graph ranges; working tree over 527d379). Written by the main session from the auditor's report (the harness stopped the subagent writing files); re-checked after fix rounds 1 and 2.
> Results: 6 Proved, 7 Read OK, 0 Fails, 0 Gap, 1 Not checked.

`Proved` here names a test that drives the function. A green release is what confirms it.

## Files

| File | Read in full |
| --- | --- |
| ui/TrainingScreen.kt | No (build gate: changed functions only) |

## Functions

| Function | Expected | Where | Result | Evidence | Issue |
| --- | --- | --- | --- | --- | --- |
| Exercise ranges, Compare included | Calendar months from today, every series | TrainingScreen.kt:358-371 | Proved | ExerciseGraphRangeTest.eachRangeStartsAtItsCalendarMonthsBackFromToday |  |
| Exercise All time | First logged to latest, FitNotes and typed | TrainingScreen.kt:366 | Proved | ExerciseGraphRangeTest.theRangesAreDifferentAndAllTimeReachesTheFirstLoggedDay, anOldExerciseHasNothingInAShortRangeAndEverythingInAllTime |  |
| Compare relative | 100% at the first value in the range | TrainingScreen.kt:367-368 | Proved | ExerciseGraphRangeTest.relativeStartsAtOneHundredPercentOfTheFirstValueInTheRange |  |
| 1 year on screen | Starts at the year's first day | TrainingScreen.kt:636 | Proved | ExerciseGraphRangeTest.aYearOfBenchPressShowsItsFirstDayInTheYearNotTheFirstEver |  |
| This graph's last day before the range | Latest earlier point, or null | TrainingScreen.kt:374-375 (lastDayBefore) | Proved | ExerciseGraphRangeTest.lastDayBeforeNamesTheLatestEarlierPoint, lastDayBeforeIsNullWithoutAPeriodOrAnEarlierPoint |  |
| Exercise empty range, old exercise | Panel, Show all time | TrainingScreen.kt:641-642 | Proved | ExerciseGraphRangeTest.theScreenSaysNothingWasLoggedAndAllTimeBringsTheGraphBack |  |
| Exercise empty, the graph type has no values | Plain empty chart, no dead control | TrainingScreen.kt:592-594, :641 | Read OK | Panel only when this graph has a value before the range; on All time lastDayBefore is null |  |
| Exercise axis spans the period | Inline and full screen | TrainingScreen.kt:663, :710 | Read OK |  |  |
| Exercise full screen with an empty range | Same panel as inline | TrainingScreen.kt:699 | Read OK | No test drives it |  |
| Exercise full screen reset | On a range change | TrainingScreen.kt:671 | Read OK |  |  |
| Exercise summary and trend | Over the range | TrainingScreen.kt:579-603 | Read OK |  |  |
| Exercise share image | Range named; points and axis are the period | TrainingScreen.kt:604-622 | Read OK |  |  |
| Graph cache after midnight | Dates.today() in the key | TrainingScreen.kt:547 | Read OK |  |  |
| All time stopping short: the cause | Found and fixed | (no data-loading change) | Not checked | The Readiness 52-week cap and the kept full-screen zoom are fixed; no other limit found. Needs the phone with a multi-year FitNotes import, comparing the graphs with History | #328 |
