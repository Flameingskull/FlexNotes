# Code audit: settings-data

> No full audit yet: rows from build gates only.
> Build gate 1.0.198: 12 functions checked.
> Audited 2026-10-31 against 1.0.198 (working tree on 830f7e1, nothing committed). Files read in full: 1 of 4 in scope; the rest of the area was not read.
> Results: 4 Verified, 3 Read OK, 2 Defect, 0 Risk, 2 Improve, 1 Not checked.
> `Verified` names a test that would fail if this broke. The tests have not run yet (no SDK here): a green release confirms those rows.

## Files

| File | Read in full |
| --- | --- |
| data/LogReminders.kt | Yes |
| data/Settings.kt | No (build gate: changed functions only) |
| ui/RemindersPage.kt | No (build gate: changed functions only) |
| ui/SettingsScreen.kt | No (build gate: changed functions only) |

## Checks

| Function, class or concern | Check | Where | Result | Evidence | Issue |
| --- | --- | --- | --- | --- | --- |
| LogReminders.arm: time from the shared clock | Dates and time | LogReminders.kt:186 | Verified | ReminderHarnessTest.onceTodaysTimeHasPassedTheNextLoggingAlarmIsTheNextChosenWeekdayAndTime; noReminderFileReadsTheWallClockDirectly | #345 |
| LogReminders.arm: alarm clock at the minute | Logic | LogReminders.kt:193 | Verified | aLoggingReminderIsAnAlarmClockAtItsExactMinute | #346 |
| LogReminders.arm, retryLater: repeated hour | Dates and time | LogReminders.kt:192, :205 | Defect | App start at 02:10 in the second pass of a 03:00 to 02:00 change, reminder at 02:30: an instant already past, fires at once, ring posts it, arm repeats, until 02:30 comes round. Same cause as the medications row. Rings early and in a tight loop | #346 |
| LogReminders.alarmIntent | Lifecycle | LogReminders.kt:197 | Read OK | One identity whatever the key; request code 0 doesn't clash with the other alarms (another receiver class) | |
| LogReminders.retryLater | Failure | LogReminders.kt:204 | Verified | aLoggingAlarmThatCannotFinishRingsAgainAMinuteLaterWithTheSameKey calls it directly; the receiver's branch isn't driven (see the tests ledger) | #346 |
| LogAlarmReceiver.onReceive | Failure | LogReminders.kt:276 | Read OK | Timeout sets the retry; an exception in ring would skip the next alarm, but App.onCreate re-arms this chain on the next process start, as it needs no data | |
| LogReminders.ring through the harness | Logic | LogReminders.kt:214 | Verified | aLoggingAlarmPostsItsNotificationAndSetsTheNextAlarm; aLoggingReminderForSomethingAlreadyLoggedStaysQuiet; withoutPermissionAReminderPostsNothingButTheNextAlarmIsStillSet | |
| DeviceSettings.medRungThrough: stored and read back | Data safety | Settings.kt:91, :680, :721 | Read OK | deviceFrom and toMap match; withPreferencesFrom keeps it on a reset. No test reads it back from disk | |
| KDoc of medSnoozes and medRungThrough | Structure | Settings.kt:87, :89 | Improve | medSnoozes still says "due time to when it rings again" (it's now a token, per dose); medRungThrough says "however late" where the limit is 12 hours. Correcting them stops the next reader trusting the old rule | #327 |
| RemindersPage: exact permission granted | Lifecycle | RemindersPage.kt:58 | Defect | Android 12 or 12L, permission revoked, logging reminders on: the alarm is set inexact. The user taps the row, allows it, comes back: the row goes, but nothing rearms LogReminders (no rearm in this resume effect, no receiver for the permission-state broadcast), so the alarm stays inexact until it fires. MedicationsPage rearms only its own chain. Already there | #346 |
| SettingsScreen: search lists the exact row only when it exists | Performance | SettingsScreen.kt:278 | Improve | canExact is a call to the system on every recomposition of the screen, on the main thread, including each search keystroke. Remember it and re-read on resume, as the pages do | #346 |
| Exact row on a phone with Android 12 | Lifecycle | RemindersPage.kt:96 | Not checked | Needs a device, or a Robolectric test at API 31, which doesn't exist yet | #346 |
