# Code audit: logging

> No full audit yet: rows from build gates only.
> Build gate 1.0.198: 11 functions checked, re-checked after fix rounds 1 and 2.
> Audited 2026-10-31 against 1.0.198 (working tree on 830f7e1, nothing committed). Files read in full: 0 of 1 in scope; the rest of the area was not read.
> Results: 8 Verified, 0 Read OK, 0 Defect, 1 Risk, 0 Improve, 2 Not checked.
> `Verified` names a test that would fail if this broke. CI run 41336801625 ran this code: every reminder, alarm and rest test passed.

## Files

| File | Read in full |
| --- | --- |
| ui/RestTimer.kt | No (build gate: changed functions only) |

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
