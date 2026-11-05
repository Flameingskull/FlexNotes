# Code audit: library-workouts

> No full audit yet: rows from build gates only.
> Build gate 1.0.199: 6 functions checked (WorkoutsScreen, WorkoutRow, its Duplicate, Delete and Undo actions, its strings), followed into Routines.save, copy, delete and write, TodayPlan.workout, Routines.nextDay and the library's switcher. On the working tree over 527d379, before CI run 41925517281 finished.
> Audited 2026-11-04 against 1.0.199 (working tree on 527d379, nothing committed). Files read in full: 1 of 1 in scope; the rest of the area was not read.
> Results: 5 Verified, 6 Read OK, 1 Defect, 0 Risk, 1 Improve, 0 Not checked.
> `Verified` names a test that would fail if this broke. Those tests had not yet run when the rows were written: a green release confirms them.

## Files

| File | Read in full |
| --- | --- |
| ui/WorkoutsScreen.kt | Yes |

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
| Undo after Delete really restores | Data safety | WorkoutsScreen.kt:103 | Defect | Undo is `Routines.copy(w, w.name)`: a new workout id, new day ids and the next sort_order. Trigger: log Push Day from "PPL" today (workout_origin links the date to its day id), and PPL is the library's current workout (`lastRoutineId`); delete it here, tap Undo. Result: PPL returns at the bottom of the list; the library's switcher no longer finds `lastRoutineId`, so it falls back; TodayPlan no longer links today to Push Day or to PPL (every origin holds the old ids), and Routines.nextDay finds no logged day among the new ids, so wherever PPL is offered it suggests Push Day again instead of Pull Day. Sibling: WorkoutEditorScreen.kt:398-400 does the same (already there). Fix: a Routines.restore(routine) that re-inserts the routine, its days, items and planned sets with their original ids and sort_order in one transaction, used by both. Test: delete then Undo, assert the stored workout has the original id, day ids and position, and Routines.nextDay returns the day after the logged one | #353 |
| The workout awaiting Delete across rotation | Android lifecycle | WorkoutsScreen.kt:52 | Read OK | Kept as its id in rememberSaveable; the sheet shows only while the workout still exists, so one deleted elsewhere hides it | #353 |
| Duplicate, Delete and Undo copied from the editor | Structure | WorkoutsScreen.kt:73-106 | Improve | The same three actions, strings and Undo are written out again here and in WorkoutEditorScreen.kt:192-200, 389-403. One shared helper (for example `WorkoutActions.duplicate/delete`) means the Undo fix above, and any later change, lands in both | #353 |
| Labels and long names | Project rules | WorkoutsScreen.kt:121, :125, :128 | Read OK | The row is one clickable merging name and summary; the ⋮ says "Options for <name>"; the name has no maxLines, so it wraps. Type from typography roles, colours from the theme and Brand.Hairline | #353 |
| Strings | Project rules | strings.xml:767, :1048-1055 | Read OK | `day_menu_workouts`, `wks_title`, `wks_summary`, `wks_days` (plural), `wks_empty_title`, `wks_empty_body` in resources; the rest reuse `lib_*` and `workout_*`. No hard-coded text | #353 |
| @OptIn(ExperimentalMaterial3Api) | Build | WorkoutsScreen.kt:47 | Read OK | Added after the first CI round with its import; file-level opt-in has no runtime effect. CI run 41925517281 decides whether it compiles | #353 |
