# Function audit: logging

> No full audit yet: rows from build gates only.
> Build gate 1.0.198: 13 functions checked (re-checked after fix round 2).
> Audited 2026-10-31 against 1.0.198 (working tree over 830f7e1, before CI ran the tests named). Files read in full: 2 of the 2 this gate needed; not the area.
> Build gate 1.0.199: 19 functions checked (#353, the Workouts screen; working tree over 527d379, before CI ran the tests named). Re-checked after fix round 1: Undo after Delete, and the editor's Delete.
> Build gate 1.0.202: 7 functions checked (#372 and #370, the day log's body values; working tree over 3589faf, before CI ran the tests named).
> Results: 20 Proved, 16 Read OK, 0 Fails, 2 Gap, 1 Not checked.

`Proved` here names a test that drives the function. Nothing was run: a green release is what confirms it.

## Files

| File | Read in full |
| --- | --- |
| ui/RestTimer.kt | Yes |
| ui/TimerService.kt | Yes |
| ui/WorkoutsScreen.kt | Yes |
| ui/DayScreen.kt | No (build gate: changed functions only) |

## Functions

| Function | Expected | Where | Result | Evidence | Issue |
| --- | --- | --- | --- | --- | --- |
| Start a rest | An alarm clock at its end | RestTimer.kt:223 | Proved | RestTimerAlarmTest.aRestSetsAnAlarmClockAtItsEnd | |
| −15 s, +15 s, a new length | The alarm moves with the end | RestTimer.kt:158, 176 | Proved | RestTimerAlarmTest.anAdjustmentAndANewLengthMoveTheAlarm | |
| Pause, Resume, Stop | Pause and Stop cancel it, a paused rest sets none, Resume sets it again | RestTimer.kt:189, 198, 204 | Proved | RestTimerAlarmTest.pauseAndStopCancelItAndResumeSetsItAgain | |
| The alarm ends the rest | Alerts once; a later ring does nothing | RestTimer.kt:138, 228 | Proved | RestTimerAlarmTest.theAlarmEndingTheRestAlertsOnceAndALaterRingingDoesNothing | |
| The in-process timer ends it first | The alarm adds nothing | RestTimer.kt:221 | Proved | RestTimerAlarmTest.theInProcessTimerEndingTheRestFirstMeansTheAlarmAddsNothing. Both run on `AppScope`'s main thread, so they can't interleave | |
| A stale alarm, or one for a paused rest | Ignored, rest and alarm stay | RestTimer.kt:141 | Proved | RestTimerAlarmTest.aStaleAlarmForARestThatWasMovedDoesNothing | |
| The app killed during a rest | The alarm still alerts | RestTimer.kt:144 | Proved | RestTimerAlarmTest.anAlarmForARestTheProcessNoLongerKnowsStillMeansItEnded | |
| Restart while a rest runs | Replaces the alarm | RestTimer.kt:152 | Read OK | `start` → `schedule`; same `PendingIntent`, so one alarm | |
| −15 s past zero | Ends now, one alert | RestTimer.kt:167 | Read OK | `delay(0)` may run `finish` before `setAlarm`; that alarm fires at once and `lastFinish` ignores it | |
| Android 12 or 12L with the permission revoked | Falls back, may be late | ExactAlarms.kt:35 | Read OK | Same helper. The rest timer's sheet has no note, only the Settings pages | |
| Rest alarm when settings don't load in 10 s | The alert still comes | RestTimer.kt:259 | Proved | RestTimerAlarmTest.anAlarmAlertsEvenWhenTheSettingsNeverLoad | |
| An alarm already on its way when the rest is stopped | No alert | RestTimer.kt:204 | Proved | RestTimerAlarmTest.anAlarmArrivingAfterTheRestWasStoppedDoesNotAlert. Setting `lastFinish` in `stop` doesn't affect a new rest: an active state takes the other branch | |
| Rest over on time with the screen off, on a real phone | Sound and vibration at the end | RestTimer.kt:236 | Not checked | The owner ruled no phone check first, and the alarm is proved set. Whether sound and vibration reach a dozing phone needs a device. With vibrate off there is no notification (RestTimer.kt:238), as before | |
| Day log ⋮ › Workouts | Opens the saved workouts list | DayScreen.kt:203, MainActivity.kt:643 | Proved | WorkoutsScreenTest.theMenuOpensTheWorkoutsListAndCreateOpensTheEditor: taps More options, then Workouts, asserts `Screen.Workouts` on top and both workouts listed | |
| A workout's row | Its name in full and "N days · N exercises", for any count including 0 and 1 | WorkoutsScreen.kt:113 | Proved | WorkoutsScreenTest.aRowSaysHowManyDaysAndExercisesAndALongNameIsShownInFull ("3 days · 4 exercises"), and the menu test ("1 day · 0 exercises"). Name has no line limit, so a long one wraps | |
| Tap a workout | Opens that workout in the editor | WorkoutsScreen.kt:70 | Proved | WorkoutsScreenTest.tappingAWorkoutOpensItInTheEditor: `Screen.WorkoutEditor(id)` and the Edit title | |
| Row ⋮ › Edit workout | Opens that workout in the editor | WorkoutsScreen.kt:72 | Read OK | Same `Screen.WorkoutEditor(w.id)` as the row tap. No test opens a row's menu and taps Edit | |
| Top bar + | Opens a new, empty workout in the editor | WorkoutsScreen.kt:59 | Proved | The menu test and the empty test tap "Create new workout"; the top bar's + is the first match in tree order, so it is the control driven | |
| "Create new workout" under the list | Same as + | WorkoutsScreen.kt:84 | Read OK | Same `create()` lambda. Not driven: the test's tap takes the first match, the top bar's + | |
| No workouts yet | Says so, explains a workout, offers Create | WorkoutsScreen.kt:61 | Proved | WorkoutsScreenTest.withNoWorkoutsTheScreenSaysSoAndOffersCreate asserts the title and that Create opens the editor | |
| The empty state's Create button | Opens a new workout | WorkoutsScreen.kt:63 | Read OK | Same `create()`. Not driven, for the same first-match reason | |
| Save a new workout made from here | Back on the list with it shown; it also becomes the library's current workout | WorkoutEditorScreen.kt:163-178 | Read OK | `nav.pop()` then `Routines.save`; a new one sets `lastRoutineId`, as every other Create path does. The list redraws when the library area changes (`rememberScreenSnapshot`, MainActivity.kt:615). The tests' harness skips that view and calls `show(stored())`, so the refresh itself is unproved | |
| Row ⋮ › Duplicate | "<name> (copy)" with every day, exercise, planned set and training block; says so | WorkoutsScreen.kt:73, Routines.kt:193 | Proved | WorkoutsScreenTest.duplicateAddsACopyToTheList: the copy exists with 3 days and is listed. The seeded days have no exercises, so copying exercises and sets is Read OK (`save` → `writeExercises`); the message isn't asserted | |
| Row ⋮ › Delete workout asks first | A confirm sheet; nothing deleted until confirmed | WorkoutsScreen.kt:79, 92 | Proved | WorkoutsScreenTest.deleteAsksFirstThenRemovesTheWorkoutAndUndoBringsItBack: the title shows and the workout is still stored | |
| Confirm Delete | The workout and its days go, the list no longer shows it | WorkoutsScreen.kt:98, Routines.kt:187 | Proved | Same test: gone from the database and the screen, the other workout still shown | |
| Logged days after a delete | Every date logged from it keeps its sets | Routines.kt:187 | Read OK | `delete` touches only `routine*` tables. The test's seed has no logged sets, so not driven | |
| Undo after Delete | The workout back as it was: same ids, same place, still the library's workout, its next day and logged-from links intact | WorkoutsScreen.kt:111-117, Routines.kt:201-215, Daos.kt:906-912 | Proved | Fix round 1: `deleteWorkoutWithUndo` → `Routines.restore`, which re-inserts the routine row under its own id and `sort_order` (with its training blocks) and each day under its own id, then rewrites exercises and planned sets, in one transaction; `IGNORE` makes a second Undo a no-op. Delete never touches `workout_origin` or `lastRoutineId`, so with the same ids the library, `Routines.nextDay` and `TodayPlan` find it again. WorkoutsScreenTest.deleteAsksFirstThenRemovesTheWorkoutAndUndoBringsItBack asserts the list order of ids and the day ids after Undo through the screen; RoutineRestoreTest.undoPutsAWorkoutBackWithItsOwnIdsAndPlace asserts the middle workout returns to its place with its day ids and names, and a second restore adds nothing. Read OK only: exercises, planned sets and training blocks coming back (the seeds have none) | |
| Editor ⋮ › Delete workout, then Undo | Same as from the list | WorkoutEditorScreen.kt:392 | Read OK | Fix round 1: the editor calls the same `deleteWorkoutWithUndo(context, original)`; `original` is the stored workout as the editor opened it, so unsaved edits aren't what comes back. Not driven from the editor | |
| Tap outside, or Back, on the confirm sheet | Nothing deleted | WorkoutsScreen.kt:97 | Read OK | `onDismiss` clears `deleting` only | |
| Turn the phone while the confirm sheet is open | Still asking about the same workout | WorkoutsScreen.kt:52 | Read OK | `rememberSaveable` id; looked up again in `routinesById`, so a workout deleted meanwhile just closes it | |
| The list after a change in the app | Duplicate, Delete, Undo and a save elsewhere show without leaving | WorkoutsScreen.kt:61, 67 | Read OK | Reads `snap.routines`/`routinesById`, the library area, which replaces the screen's view on change. Not proved: the harness hands the snapshot directly | |
| Put workouts in my own order | A Reorder toggle with drag handles, as other lists have | WorkoutsScreen.kt:66, Routines.kt:167 | Gap | `sort_order` is set once at creation and nothing in the app changes it; the list (and the library's switcher, ExerciseLibrary.kt:280) is always creation order. Breaks "reordering is in place" and user choice. Already there; this list is the natural place for it | #357 |
| Day log body value: what its change is measured from (default) | With two values on one day and one the day before, both of the day's values compare with the earlier day's | DayScreen.kt:594-597, MeasurementGoals.kt:78 | Proved | ChangeFromScreensTest.theDayLogComparesBothValuesWithTheEarlierDayByDefault: shows "since Wed 28 Oct" and not "since Thu 29 Oct". `previousValue` itself: ChangeFromTest | |
| Day log body value with "The value just before it" | The second value of the day compares with the first | DayScreen.kt:597 | Proved | ChangeFromScreensTest.theDayLogComparesWithTheValueJustBeforeWhenChosen: both "since" days shown. Records are sorted by date then time (Store.kt:237), so "just before" follows the time logged | |
| Day log change colour | Same rule as the Track row and History: `changeColour(def, plainNoGoal, prev, value)`, the no-goal choice read once for the card | DayScreen.kt:594, 656 | Read OK | Same `previousValue` and `changeColour` as BodyScreen's Track row and both History lists, with the def found by name in `allMeasurements`. No test reads the colour on the day log | |
| Same verdict on the Track row, History and the day log (#372 acceptance) | A test with two values on one day and one the day before asserts the same verdict on each screen | ChangeFromScreensTest.kt | Gap | The screen tests assert only which day the change is "since", on the Track tab and the day log. None opens History (`BodyHistoryAll` or `BodyHistoryPane`) with this data, and none asserts the colour or the verdict, so the acceptance test the issue asks for isn't there. Caused by this build | #372 |
| Changing the choice while the day log is open | The day's change lines follow at once | DayScreen.kt:595-597 | Read OK | `changeFromEarlierDay()` collects `Settings.portable`; `remember` is keyed on it and on `bodyKey`, so an edit or the choice redraws the line. Not driven: the tests set the choice before opening | |
| A day with the first value ever logged, or the earliest day's second value (default) | No change line | DayScreen.kt:597, 652 | Read OK | `previousValue` returns null, and the line shows only when there's a previous value | |
| Change line for a same-day comparison | Readable for a person | MeasurementGoals.kt:102-109 | Read OK | "▼ 1 kg since Thu 29 Oct · was 89 kg" under a value on Thu 29 Oct: a real weekday date and the full figure, but no time, so with three values that day the reader can't tell which one it came from. Fits the owner rules as they stand | |
