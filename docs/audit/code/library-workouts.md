# Code audit: library-workouts

> No full audit yet: rows from build gates only.
> Build gate 1.0.199: 6 functions checked, re-checked after fix rounds 1 and 3 (Routines.restore, the shared helpers) (WorkoutsScreen, WorkoutRow, its Duplicate, Delete and Undo actions, its strings), followed into Routines.save, copy, delete and write, TodayPlan.workout, Routines.nextDay and the library's switcher. On the working tree over 527d379, before CI run 41925517281 finished.
> Build gate 1.0.204: 9 functions checked (#334, the sheet call sites in CustomTypeSheet, ExerciseLibrary, GoalsTab, TrainingBlocksUi and WorkoutEditorScreen; working tree over 8a66dc5). CI on these files (draft PR #380) compiled and SheetGuardTest passed.
> Audited 2026-11-04 against 1.0.199 (working tree on 527d379, nothing committed). Files read in full: 1 of 3 in scope; the rest of the area was not read.
> Results: 8 Verified, 17 Read OK, 1 Defect, 0 Risk, 0 Improve, 0 Not checked.
> `Verified` names a test that would fail if this broke. Those tests had not yet run when the rows were written: a green release confirms them.

## Files

| File | Read in full |
| --- | --- |
| ui/WorkoutsScreen.kt | Yes |
| data/Routines.kt | No (build gate: changed functions only) |
| ui/WorkoutEditorScreen.kt | No (build gate: changed functions only) |
| ui/CustomTypeSheet.kt | No (build gate: changed functions only) |
| ui/ExerciseLibrary.kt | No (build gate: changed functions only) |
| ui/GoalsTab.kt | No (build gate: changed functions only) |
| ui/TrainingBlocksUi.kt | No (build gate: changed functions only) |

## Checks

| Function, class or concern | Check | Where | Result | Evidence | Issue |
| --- | --- | --- | --- | --- | --- |
| WorkoutsScreen: the list follows the database | Data safety | WorkoutsScreen.kt:61, :67 | Read OK | Reads `snap.routines` (library area, ordered by sort_order then id), keyed by id. Every write goes through Routines.write: one transaction on IO, then Store.refresh(LIBRARY), so the screen's view is replaced. The tests push stored() by hand, so in-app refresh itself isn't driven | #353 |
| Tapping a workout opens it | Logic | WorkoutsScreen.kt:70 | Verified | WorkoutsScreenTest.tappingAWorkoutOpensItInTheEditor asserts Screen.WorkoutEditor(id) and the editor's title | #353 |
| +, Create new workout and the empty state's Create | Logic | WorkoutsScreen.kt:53, :62, :84 | Verified | theMenuOpensTheWorkoutsListAndCreateOpensTheEditor and withNoWorkoutsTheScreenSaysSoAndOffersCreate assert WorkoutEditor(0L) and "New workout" | #353 |
| WorkoutRow: "N days · N exercises" | Logic | WorkoutsScreen.kt:114 | Verified | Plurals for both counts. aRowSaysHowManyDaysAndExercises… asserts "3 days · 4 exercises" on the fixture, and the menu test "1 day · 0 exercises" | #353 |
| Duplicate | Data safety | WorkoutsScreen.kt:73 | Verified | Routines.copy with new ids, one transaction. duplicateAddsACopyToTheList asserts the copy stored with its 3 days and both rows drawn | #353 |
| Duplicate and Delete when the write fails | Failure | WorkoutsScreen.kt:74, :100 | Read OK | AppScope's handler logs, clears busy and shows the plain-words failure; no success message after a throw, since the message follows the write in the same coroutine | #353 |
| Delete asks first | Data safety | WorkoutsScreen.kt:79, :92 | Verified | deleteAsksFirstThenRemovesTheWorkoutAndUndoBringsItBack asserts the sheet's title and that the workout is still stored before Confirm, gone after | #353 |
| Undo after Delete really restores | Data safety | WorkoutsScreen.kt:111, Routines.kt:201 | Verified | Fix round 1: Undo is now `Routines.restore(w)`, which re-inserts the workout and its days under their own ids and sort_order, with notes and training blocks encoded as `save` does and exercises through `writeExercises`, in one transaction, then refreshes LIBRARY. workout_origin and `lastRoutineId` were never touched by delete, so they resolve again. A second Undo hits the IGNORE on the routine and adds nothing. RoutineRestoreTest.undoPutsAWorkoutBackWithItsOwnIdsAndPlace (deletes the middle of three: ids, order, day ids and names, and the second restore) and WorkoutsScreenTest.deleteAsksFirstThenRemovesTheWorkoutAndUndoBringsItBack (ids and day ids through the real Undo) would both fail with the old copy | #353 |
| The workout awaiting Delete across rotation | Android lifecycle | WorkoutsScreen.kt:52 | Read OK | Kept as its id in rememberSaveable; the sheet shows only while the workout still exists, so one deleted elsewhere hides it | #353 |
| Duplicate, Delete and Undo shared with the editor | Structure | WorkoutsScreen.kt:103, :111; WorkoutEditorScreen.kt:193, :392 | Read OK | Fix round 1: `duplicateWorkout` and `deleteWorkoutWithUndo` are the one copy, called by both screens. The editor passes `original`, its routine as it was when opened (remember(id)); save pops the editor, so that is the stored workout | #353 |
| Labels and long names | Project rules | WorkoutsScreen.kt:121, :125, :128 | Read OK | The row is one clickable merging name and summary; the ⋮ says "Options for <name>"; the name has no maxLines, so it wraps. Type from typography roles, colours from the theme and Brand.Hairline | #353 |
| Strings | Project rules | strings.xml:767, :1048-1055 | Read OK | `day_menu_workouts`, `wks_title`, `wks_summary`, `wks_days` (plural), `wks_empty_title`, `wks_empty_body` in resources; the rest reuse `lib_*` and `workout_*`. No hard-coded text | #353 |
| @OptIn(ExperimentalMaterial3Api) | Build | WorkoutsScreen.kt:47 | Read OK | Added after the first CI round with its import; file-level opt-in has no runtime effect. CI run 41925517281 decides whether it compiles | #353 |
| Routines.restore: a day id already in use | Data safety | Routines.kt:213 | Read OK | Fix round 3: a day whose restoreDay returns -1 is skipped before writeExercises, so another workout's day is never cleared. RoutineRestoreTest.undoLeavesADayNowOwnedByAnotherWorkoutAlone gives no day any exercises, so it passes on the old code too (the old bug only wiped exercises and planned sets): give Upper an exercise with planned sets and assert they survive | #353 |

## Build gate 1.0.199, #328 (graph ranges)

Written by the main session from the code-auditor's report; re-checked after fix rounds 1 and 2. Files: `ui/TrainingScreen.kt`, No (build gate: changed functions only). Rows: 2 Verified, 2 Read OK.

| Function, class or concern | Check | Where | Result | Evidence | Issue |
| --- | --- | --- | --- | --- | --- |
| comparedSeries, the period from today | Logic | TrainingScreen.kt:358-370 | Verified | ExerciseGraphRangeTest.eachRangeStartsAtItsCalendarMonthsBackFromToday, anOldExerciseHasNothingInAShortRangeAndEverythingInAllTime, relativeStartsAtOneHundredPercentOfTheFirstValueInTheRange | #328 |
| ExerciseGraphPane empty panel | Logic | TrainingScreen.kt:374-375, :592-594, :641-642, :699 | Verified | lastDayBefore; no panel in All time. ExerciseGraphRangeTest.lastDayBeforeNamesTheLatestEarlierPoint, theScreenSaysNothingWasLoggedAndAllTimeBringsTheGraphBack | #328 |
| Cache key and share | Dates and time | TrainingScreen.kt:546-548, :621 | Read OK | Dates.today() in the key; the share passes the period | #328 |
| Full-screen resetKey | Android lifecycle | TrainingScreen.kt:679 | Read OK | | #328 |

## Build gate 1.0.204, #334 (typed values kept on a tap outside)

| Function, class or concern | Check | Where | Result | Evidence | Issue |
| --- | --- | --- | --- | --- | --- |
| CustomTypeSheet: dirty | Data safety | CustomTypeSheet.kt:54, :99 | Read OK | All eight fields. rememberDirty sits before the Delete confirmation's early return (:69-91), so its slot is stable and Cancel on the confirmation returns to the form with values and baseline kept. Save closes only after the write; a refused name keeps the sheet and the text. The confirmation sheet has no input and is rightly unguarded | #334 |
| CategoryEditorSheet: dirty | Data safety | ExerciseLibrary.kt:1290, :1298 | Read OK | Name and colour, both saveable. Save closes after the write, a taken name keeps the sheet open. Three hosts (:379, :519, :1127) each remove it on close | #334 |
| Sheets without input | Data safety | ExerciseLibrary.kt:753; WorkoutEditorScreen.kt:356 | Read OK | ExerciseInfoSheet only shows values; Copy day to a workout acts on the tap. Neither needs dirty | #334 |
| GoalEditor: dirty | Data safety | GoalsTab.kt:195, :216 | Read OK | Kind and target text. Hosts GoalsTab.kt:156 and AnalysisTabs.kt:154 hold `editing` in plain remember, so rotation closes the sheet (and loses the text, as before this build) | #334 |
| BlockSheet: dirty | Data safety | TrainingBlocksUi.kt:146, :158 | Read OK | Seven values. The fields are rememberSaveable(block) and rememberDirty is not keyed; they would part only if `block` changed while the sheet is open, and `editing` is set only from the list behind the modal sheet. A new block's default TrainingBlock is rebuilt equal after rotation, so the keys match | #334 |
| Rename day: dirty | Data safety | WorkoutEditorScreen.kt:320-322 | Read OK | Inside renaming?.let; the sheet leaves composition on close, so each rename starts a new baseline. Named `unsaved`, so it doesn't shadow the screen's own `dirty` (:154) | #334 |
| PlannedSetsSheet: dirty | Data safety | WorkoutEditorScreen.kt:686, :707 | Read OK | fill, rows, copy rest, same rest, both rests. SetDraft (:589) is a data class, so rows.toList().toString() is stable, and SetRowsSaver's JSON round trip gives equal rows, so rotation leaves an untouched sheet untouched | #334 |
| DayRestSheet: no dirty | Data safety | WorkoutEditorScreen.kt:895-905 | Defect | This build: #334's first criterion. The sheet holds input (Copy previous rest or Set rest, and two RestLengthStepper fields you can type into) but passes no dirty. Trigger: workout editor, a day's menu, Set rest for every exercise, type 120 in Rest between sets, tap above the sheet: it closes and nothing is applied. Expected: it stays open with 120. Fix: after :900 add `val unsaved = rememberDirty(copyRest, rest, after)` and pass `dirty = unsaved` to the FitSheet at :901. Test: a SheetGuardTest case that opens WorkoutEditorScreen for a fixture workout, opens the day's Set rest sheet, presses + on the rest stepper, taps outside and asserts the sheet's title still shows. Size S | #334 |
