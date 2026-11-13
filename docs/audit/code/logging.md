# Code audit: logging

> No full audit yet: rows from build gates only.
> Build gate 1.0.198: 11 functions checked, re-checked after fix rounds 1 and 2.
> Build gate 1.0.199: 1 function checked (the day log's ⋮ menu, its new Workouts entry), on the working tree over 527d379, before CI run 41925517281 finished.
> Build gate 1.0.202: 1 function checked (BodyValuesCard, #370 and #372; working tree over 3589faf, before CI ran the tests named). Also read in full: the new tests ChangeFromTest.kt and ChangeFromScreensTest.kt.
> Audited 2026-10-31 against 1.0.198 (working tree on 830f7e1, nothing committed). Files read in full: 0 of 2 in scope; the rest of the area was not read.
> Results: 11 Verified, 2 Read OK, 0 Defect, 1 Risk, 1 Improve, 2 Not checked.
> `Verified` names a test that would fail if this broke. CI run 41336801625 ran this code: every reminder, alarm and rest test passed.

## Files

| File | Read in full |
| --- | --- |
| ui/RestTimer.kt | No (build gate: changed functions only) |
| ui/DayScreen.kt | No (build gate: changed functions only) |

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
