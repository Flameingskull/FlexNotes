# Function audit: logging

> No full audit yet: rows from build gates only.
> Build gate 1.0.198: 13 functions checked (re-checked after fix round 2).
> Audited 2026-10-31 against 1.0.198 (working tree over 830f7e1, before CI ran the tests named). Files read in full: 2 of the 2 this gate needed; not the area.
> Build gate 1.0.199: 18 functions checked (#353, the Workouts screen; working tree over 527d379, before CI ran the tests named).
> Results: 17 Proved, 11 Read OK, 1 Fails, 1 Gap, 1 Not checked.

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
| Undo after Delete | The workout back as it was | WorkoutsScreen.kt:102-104, Routines.kt:193 | Fails | Undo calls `Routines.copy(w, w.name)`: a new workout with new workout and day ids, at the end of the list (`nextRoutineOrder`). Steps: pick workout A in the library's switcher, log its Push Day, then on Workouts delete A and tap Undo. Result: A is back with its days, but last in the list; the library no longer opens on it (`lastRoutineId` names the old id, ExerciseLibrary.kt:243); the suggested next day goes back to its first day and today's "logged from" link is lost (`workout_origin` rows hold the old ids; `Routines.nextDay` Routines.kt:251, `TodayPlan.workout` TodayPlan.kt:22-36), so the reminder and widget name the wrong day. Expected: same place, same ids and links. The test asserts only the name and the day count. Same code in the editor's own Delete (WorkoutEditorScreen.kt:398-401, already there) | #353 |
| Tap outside, or Back, on the confirm sheet | Nothing deleted | WorkoutsScreen.kt:97 | Read OK | `onDismiss` clears `deleting` only | |
| Turn the phone while the confirm sheet is open | Still asking about the same workout | WorkoutsScreen.kt:52 | Read OK | `rememberSaveable` id; looked up again in `routinesById`, so a workout deleted meanwhile just closes it | |
| The list after a change in the app | Duplicate, Delete, Undo and a save elsewhere show without leaving | WorkoutsScreen.kt:61, 67 | Read OK | Reads `snap.routines`/`routinesById`, the library area, which replaces the screen's view on change. Not proved: the harness hands the snapshot directly | |
| Put workouts in my own order | A Reorder toggle with drag handles, as other lists have | WorkoutsScreen.kt:66, Routines.kt:167 | Gap | `sort_order` is set once at creation and nothing in the app changes it; the list (and the library's switcher, ExerciseLibrary.kt:280) is always creation order. Breaks "reordering is in place" and user choice. Already there; this list is the natural place for it | #357 |
