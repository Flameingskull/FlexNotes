# Function audit: library-workouts

> No full audit yet: rows from build gates only.
> Build gate 1.0.199: 14 functions checked (#328, graph ranges; working tree over 527d379). Written by the main session from the auditor's report (the harness stopped the subagent writing files); re-checked after fix rounds 1 and 2.
> Build gate 1.0.204: 10 functions checked (#334, sheets keep typed values on a tap outside, a drag and Back; the sheet call sites; working tree over 8a66dc5; CI on draft PR #380 compiled and passed all of SheetGuardTest).
> Results: 6 Proved, 13 Read OK, 1 Fails, 2 Gap, 2 Not checked.

`Proved` here names a test that drives the function. A green release is what confirms it.

## Files

| File | Read in full |
| --- | --- |
| ui/TrainingScreen.kt | No (build gate: changed functions only) |
| ui/CustomTypeSheet.kt | No (build gate: changed functions only) |
| ui/ExerciseLibrary.kt | No (build gate: changed functions only) |
| ui/GoalsTab.kt | No (build gate: changed functions only) |
| ui/TrainingBlocksUi.kt | No (build gate: changed functions only) |
| ui/WorkoutEditorScreen.kt | No (build gate: changed functions only) |

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
| Exercise type sheet: name, values, own metric | Stays open on a tap outside once changed; Save closes only after it's saved | CustomTypeSheet.kt:54, :99 | Read OK | All eight fields passed; `rememberDirty` sits above the delete-confirm branch, so cancelling Delete keeps the start point. A refused save keeps it open with the text. Not driven by a test | |
| Category editor: name and colour | Same | ExerciseLibrary.kt:1290, :1298 | Read OK | `rememberDirty(name.trim(), colour)`; closes only once saved. Not driven by a test | |
| Goal editor: kind and target | Same | GoalsTab.kt:195, :216 | Read OK | Kind and trimmed target passed. Not driven by a test | |
| Training block sheet | Same | TrainingBlocksUi.kt:146, :158 | Read OK | All seven fields passed; stepping + then - returns to the start and reads as unchanged. Not driven by a test | |
| Workout editor: Rename day | Same | WorkoutEditorScreen.kt:320 | Read OK | `rememberDirty(dayName.trim())` inside `renaming?.let`; the sheet leaves composition between days. Not driven by a test | |
| Workout editor: a day's planned sets and rest (PlannedSetsSheet) | Same | WorkoutEditorScreen.kt:686, :707 | Read OK | `rows.toList()` of the data class `SetDraft` gives a stable text, also after the JSON saver restores it, so rotation doesn't make it falsely dirty. Not driven by a test | |
| Workout editor: Set rest for every exercise (DayRestSheet) | Changed rests stay on a tap outside, as in the block and planned-sets sheets | WorkoutEditorScreen.kt:895-905 | Gap | No `dirty`: two rest steppers and the Copy/Set choice are lost on a tap outside, a drag or Back. Cause: this build (criterion 1 sweep) | #334 |
| Estimated 1RM settings sheet: typed rep limit | Stays open on a tap outside once typed | ExerciseStats.kt:276-292 (not in the changed files) | Fails | Typed text in `remember`, no `dirty`: type 8, tap outside, the sheet closes and nothing is saved. Criterion 1 asks for every sheet with input. The set and 1RM calculators (Calculators.kt:104, ExerciseStats.kt:197) hold scratch values only and the plate calculator saves its setup on close (Calculators.kt:199), so those are fine | #334 |
| Typed values survive turning the phone: Goal editor | Kept (#304) | GoalsTab.kt:191-194 | Gap | `remember`, not `rememberSaveable`: a rotation drops the typed target. Already there | #381 |
| Drag down and Back on a sheet holding typed values (every guarded sheet in this ledger) | The sheet stays open with the values kept | design/Sheets.kt FitSheet `confirmValueChange` and `onDismissRequest` | Not checked | Read only: a drag settles through `confirmValueChange(Hidden)`, which returns false while dirty; Back runs Material 3's `settleToDismiss`, i.e. `sheetState.hide()` then `onDismissRequest()`. `hide()` vetoes through `confirmValueChange` in androidx-main `SheetDefaults.kt`, and FitSheet's `onDismissRequest` is guarded by `sheetMayClose`, so both should leave it open. Not confirmed for the Material 3 in BOM 2026.09.00, and no test drives a drag or Back (SheetGuardTest says "Back is not driven here"). If `hide()` there does not veto, Back would hide the sheet without closing it. Settled by a Robolectric test that types into CustomTypeSheet or BlockSheet, presses Back (`Espresso.pressBack()`) and swipes down (`performTouchInput { swipeDown() }` on the title), then asserts the title and the text are still shown | #334 |
