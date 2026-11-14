# Code audit: logging

> No full audit yet: rows from build gates only.
> Build gate 1.0.198: 11 functions checked, re-checked after fix rounds 1 and 2.
> Build gate 1.0.199: 1 function checked (the day log's ⋮ menu, its new Workouts entry), on the working tree over 527d379, before CI run 41925517281 finished.
> Build gate 1.0.202: 1 function checked (BodyValuesCard, #370 and #372; working tree over 3589faf, before CI ran the tests named). Also read in full: the new tests ChangeFromTest.kt and ChangeFromScreensTest.kt.
> Build gate 1.0.204: 10 functions checked (#334, the sheet call sites in WorkoutEditing, WorkoutSheets, WorkoutTools and SetViews; working tree over 8a66dc5). CI on these files (draft PR #380) compiled and SheetGuardTest passed.
> Audited 2026-10-31 against 1.0.198 (working tree on 830f7e1, nothing committed). Files read in full: 0 of 2 in scope; the rest of the area was not read.
> Results: 13 Verified, 5 Read OK, 0 Defect, 2 Risk, 2 Improve, 2 Not checked.
> `Verified` names a test that would fail if this broke. CI run 41336801625 ran this code: every reminder, alarm and rest test passed.

## Files

| File | Read in full |
| --- | --- |
| ui/RestTimer.kt | No (build gate: changed functions only) |
| ui/DayScreen.kt | No (build gate: changed functions only) |
| ui/WorkoutEditing.kt | No (build gate: changed functions only) |
| ui/WorkoutSheets.kt | No (build gate: changed functions only) |
| ui/WorkoutTools.kt | No (build gate: changed functions only) |
| ui/design/SetViews.kt | No (build gate: changed functions only) |

## Checks

| Function, class or concern | Check | Where | Result | Evidence | Issue |
| --- | --- | --- | --- | --- | --- |
| RestTimer.schedule, setAlarm: alarm at endAt | Logic | RestTimer.kt:215, :123 | Verified | RestTimerAlarmTest.aRestSetsAnAlarmClockAtItsEnd | #342 |
| adjust, setLength: alarm moves | Logic | RestTimer.kt:158, :176 | Verified | anAdjustmentAndANewLengthMoveTheAlarm | #346 |
| pause, stop, resume | Lifecycle | RestTimer.kt:189, :198, :204 | Verified | pauseAndStopCancelItAndResumeSetsItAgain, including changes made while paused | #342 |
| finish: alerts once, whichever comes first | Concurrency | RestTimer.kt:228 | Verified | theAlarmEndingTheRestAlertsOnceAndALaterRingingDoesNothing; theInProcessTimerEndingTheRestFirstMeansTheAlarmAddsNothing | #342 |
| alarmRang: stale or paused alarm | Logic | RestTimer.kt:138 | Verified | aStaleAlarmForARestThatWasMovedDoesNothing | #342 |
| alarmRang: process killed during the rest | Lifecycle | RestTimer.kt:144 | Verified | anAlarmForARestTheProcessNoLongerKnowsStillMeansItEnded | #342 |
| alarmRang after Stop | Concurrency | RestTimer.kt:205 | Verified | anAlarmArrivingAfterTheRestWasStoppedDoesNotAlert | #342 |
| RestAlarmReceiver, threads | Concurrency | RestTimer.kt:274 | Verified | anAlarmAlertsEvenWhenTheSettingsNeverLoad. AppScope is Dispatchers.Main, so state stays on one thread. If settings never load the alert comes with the defaults |  |
| An alarm clock for every rest: system side effects | Lifecycle | RestTimer.kt:125 | Risk | Each rest becomes the phone's "next alarm": the alarm icon and lock-screen time change on every set, and phones whose bedtime or Do Not Disturb schedule ends at the next alarm will read it. Named as the price in ExactAlarms' KDoc; the bedtime effect needs checking on a phone | #346 |
| Alert on time with the screen off | Lifecycle | RestTimer.kt:228 | Not checked | Needs a device; the tests prove the alarm is set, not Android's delivery | #342 |
| Sound after a cold start | Lifecycle | RestTimer.kt:236 | Not checked | A process the alarm started has no component left once the receiver finishes, so Android may kill it mid-sound. Needs a device | #342 |
| DayScreen ⋮ menu: Workouts entry | Logic | DayScreen.kt:203 | Verified | One MenuAction pushing Screen.Workouts, label from `day_menu_workouts`. WorkoutsScreenTest.theMenuOpensTheWorkoutsListAndCreateOpensTheEditor opens the real day log, taps More options then Workouts, and asserts nav.top and the list. The re-recorded day-menu goldens are in the tests ledger | #353 |
| BodyValuesCard: what the change is measured from | Logic | DayScreen.kt:597 | Verified | Calls the shared previousValue; ChangeFromScreensTest.theDayLogComparesBothValuesWithTheEarlierDayByDefault and theDayLogComparesWithTheValueJustBeforeWhenChosen (89 since the 28th, 88 since the 29th). remember is keyed on bodyKey, the record id and the choice, so changing the setting recomputes. The default keeps 1.0.201's day-log rule (`date.take(10)` gives the same result as the old `date <` for yyyy-MM-dd) | #372 |
| BodyValuesCard: settings read once per card | Performance | DayScreen.kt:593-594 | Read OK | plainWhenNoGoal and changeFromEarlierDay are read once above the loop, not per row | #370 |
| BodyValuesCard: change colour | Logic | DayScreen.kt:656 | Read OK | Passes the card's plainNoGoal. No test draws the day log's colour (NoGoalColourTest calls changeColour directly), so passing the wrong flag here would pass every test | #370 |
| ChangeFromTest | Tests | ChangeFromTest.kt | Verified | Pure tests of previousValue in both modes, with two values on one day and the first value of all; each assertion fails if either branch regressed to the other rule | #372 |
| ChangeFromScreensTest: what the screen tests prove | Tests | ChangeFromScreensTest.kt:46-70 | Improve | They assert the "since <date>" text (which value is compared), not the colour verdict #372 names, and no test opens History (BodyHistoryAll, BodyHistoryPane), so History falling back to the old rule passes. The Track checks rely on the pager composing only the shown page | #372 |

## Build gate 1.0.204, #334 (typed values kept on a tap outside)

| Function, class or concern | Check | Where | Result | Evidence | Issue |
| --- | --- | --- | --- | --- | --- |
| WorkoutCommentSheet: dirty | Data safety | WorkoutEditing.kt:188, :196 | Verified | rememberDirty(text.trim()). SheetGuardTest.theWorkoutCommentKeepsItsTextOnATapOutside fails if `dirty = unsaved` is dropped (the host shows `Guard test closed`), and theWorkoutCommentClosesOnATapOutsideWhenNothingWasTyped proves the same tap reaches the scrim. Delete comment and Save still call onDismiss directly | #334 |
| SetCommentSheet: dirty | Data safety | SetViews.kt:665, :670 | Verified | rememberDirty(text.trim()) over the rememberSaveable text, so the baseline and the text both survive rotation. SheetGuardTest.theSetCommentKeepsItsTextOnATapOutsideAndCancelStillCloses (no untouched counterpart for this sheet: see the tests ledger). Its three callers (SetEntry.kt:708, :720, DayScreen.kt:887) each remove it from composition on close, so a later comment starts a fresh baseline | #334 |
| ReviewWorkoutSheet: dirty | Data safety | WorkoutSheets.kt:281, :292 | Read OK | Ticks (sorted), Save as a new workout and its name. State is remember(choice); Choose another sets chosen = null, so the sheet leaves composition and a new choice gets a new baseline. Unticking an exercise counts as unsaved, which is intended | #334 |
| SaveAsWorkoutSheet: dirty | Data safety | WorkoutSheets.kt:391, :434 | Read OK | Ticks (sorted), fill, target workout and day, both names. Save and Edit call onDismiss before the write | #334 |
| WorkoutTimeSheet: dirty | Data safety | WorkoutTools.kt:122, :148 | Read OK | Start and end stamps; End now and either clock pick make it unsaved. Close, Start timer, Stop timer and Clear all close through onDismiss directly, so none is blocked | #334 |
| Choice-only sheets left unguarded | Data safety | WorkoutEditing.kt:303, :380; WorkoutTools.kt:308, :343; MedicationToday.kt:344 | Improve | This build guards ticks in ReviewWorkoutSheet and SaveAsWorkoutSheet but not the same kind of choice in CopyOrMoveWorkoutSheet (target day, unticked exercises), CopyPreviousWorkoutSheet (source day, ticks), Share workout (ticks) and its Options (format, includes, photo), or DueDoseSheet (the site picked before Taken). A tap outside after unticking six exercises loses them. CopyWorkoutSheet (WorkoutEditing.kt:72) has no input and is right as it is. Fix: one rule, any sheet holding a choice the person changed passes dirty: CopyOrMove `rememberDirty(target, checked.sorted())`, CopyPrevious `rememberDirty(source, checked.sorted())`, Share `rememberDirty(ticked.sorted())`, Options `rememberDirty(asImage, withDate, withDuration, withComment, withPrs, photoIdx)`, DueDose `rememberDirty(site)`. Or the owner decides choices stay unguarded and #334's wording says typed values only. Test: SheetGuardTest case for CopyOrMoveWorkoutSheet, untick one, tap outside, assert the sheet title still shows. Size S | #334 |
| Typed state held in remember: lost on rotation | Android lifecycle | WorkoutEditing.kt:187; WorkoutTools.kt:119-120; WorkoutSheets.kt:383-390; CaloriesCard.kt:219 | Risk | Already there. MainActivity has no configChanges, so rotation recreates the activity; the hosts keep these sheets open (DayScreen.kt:139, :141, :149, :150, state.saved), but the comment text, the times, the Create workout fields and the intake fields are plain remember, so they reset to the stored values. rememberDirty's baseline is saveable, so after rotation the sheet reads as untouched and closes on a tap: consistent, but its KDoc (Sheets.kt) promising the start point survives rotation only helps sheets whose own state is saveable. Fix: rememberSaveable(date) for WorkoutCommentSheet text; rememberSaveable for WorkoutTimeSheet start and end and SaveAsWorkoutSheet's fill, routineId, dayId, name, dayName and ticked (a listSaver of Long); CaloriesSheet's four fields as a rememberSaveable(date) list of strings. Test: StateRestorationTester on WorkoutCommentSheet: type, emulateSavedInstanceStateRestore, assert the text. Size M | #334 |
