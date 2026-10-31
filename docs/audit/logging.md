# Function audit: logging

> No full audit yet: rows from build gates only.
> Build gate 1.0.198: 12 functions checked.
> Audited 2026-10-31 against 1.0.198 (working tree over 830f7e1, before CI ran the tests named). Files read in full: 2 of the 2 this gate needed; not the area.
> Results: 7 Proved, 3 Read OK, 0 Fails, 1 Gap, 1 Not checked.

`Proved` here names a test that drives the function. Nothing was run: a green release is what confirms it.

## Files

| File | Read in full |
| --- | --- |
| ui/RestTimer.kt | Yes |
| ui/TimerService.kt | Yes |

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
| Rest alarm when settings don't load in 10 s | A retry, so the alert isn't lost | RestTimer.kt:259 | Gap | `withTimeoutOrNull(10_000)` then `result.finish()`: no retry and no alert. Criterion for #346. Very unlikely, and the sibling receivers retry | #346 |
| Rest over on time with the screen off, on a real phone | Sound and vibration at the end | RestTimer.kt:236 | Not checked | The owner ruled no phone check first, and the alarm is proved set. Whether sound and vibration reach a dozing phone needs a device. With vibrate off there is no notification (RestTimer.kt:238), as before | |
