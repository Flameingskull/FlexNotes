# Code audit: settings-data

> No full audit yet: rows from build gates only.
> Build gate 1.0.198: 13 functions checked, re-checked after fix rounds 1 and 2.
> Audited 2026-10-31 against 1.0.198 (working tree on 830f7e1, nothing committed). Files read in full: 0 of 4 in scope; the rest of the area was not read.
> Results: 8 Verified, 5 Read OK, 0 Defect, 0 Risk, 0 Improve, 0 Not checked.
> `Verified` names a test that would fail if this broke. CI run 41336801625 ran this code: every reminder, alarm and rest test passed.

## Files

| File | Read in full |
| --- | --- |
| data/LogReminders.kt | No (build gate: changed functions only) |
| data/Settings.kt | No (build gate: changed functions only) |
| ui/RemindersPage.kt | No (build gate: changed functions only) |
| ui/SettingsScreen.kt | No (build gate: changed functions only) |

## Checks

| Function, class or concern | Check | Where | Result | Evidence | Issue |
| --- | --- | --- | --- | --- | --- |
| LogReminders.arm: time from the shared clock | Dates and time | LogReminders.kt:186 | Verified | ReminderHarnessTest.onceTodaysTimeHasPassedTheNextLoggingAlarmIsTheNextChosenWeekdayAndTime; noReminderFileReadsTheWallClockDirectly | #345 |
| LogReminders.arm: alarm clock at the minute | Logic | LogReminders.kt:193 | Verified | aLoggingReminderIsAnAlarmClockAtItsExactMinute | #346 |
| LogReminders.arm, retryLater: repeated hour | Dates and time | ExactAlarms.kt:33 | Verified | anAlarmForATimeThatHappensTwiceIsSetForTheOneStillToCome covers a logging reminder too | #346 |
| LogReminders.alarmIntent | Lifecycle | LogReminders.kt:197 | Read OK | One identity whatever the key; request code 0 doesn't clash with the other alarms (another receiver class) | |
| LogReminders.retryLater | Failure | LogReminders.kt:127 | Verified | aLoggingAlarmThatCannotFinishRingsAgainAMinuteLaterWithTheSameKey; the receiver branch is driven at ReminderHarnessTest.kt:115 | #346 |
| LogAlarmReceiver.onReceive | Failure | LogReminders.kt:298 | Verified | ReminderHarnessTest.kt:115 overruns the receiver. An exception now sets a retry too |  |
| LogReminders.ring through the harness | Logic | LogReminders.kt:214 | Verified | aLoggingAlarmPostsItsNotificationAndSetsTheNextAlarm; aLoggingReminderForSomethingAlreadyLoggedStaysQuiet; withoutPermissionAReminderPostsNothingButTheNextAlarmIsStillSet | |
| DeviceSettings.medRungThrough: stored and read back | Data safety | Settings.kt:91, :680, :721 | Read OK | deviceFrom and toMap match; withPreferencesFrom keeps it on a reset. No test reads it back from disk | |
| KDoc of medSnoozes and medRungThrough | Structure | Settings.kt:87 | Read OK | Both now match the code | #327 |
| RemindersPage: exact permission granted | Lifecycle | MedReminders.kt:773 | Verified | allowingExactAlarmsAgainSetsBothChainsAgainAsAlarmClocks, through the permission-state broadcast (written; see the compile row in the tests ledger). A rest in progress keeps its inexact alarm until it ends; the in-process timer still runs. Not worth a finding | #346 |
| SettingsScreen: search lists the exact row only when it exists | Performance | SettingsScreen.kt:280 | Read OK | Remembered, re-read on resume | #346 |
| Exact row on a phone with Android 12 | Lifecycle | RemindersPage.kt:96 | Read OK | ExactAlarmSettingsAndroid12Test written (see the compile row in the tests ledger); a real phone is still not checked | #346 |
| LogReminders.arm: clock after a zone change | Dates and time | LogReminders.kt:204 | Verified | LiveClock; the time-zone test sets a logging reminder too. CI run 41336801625 passed it | #345 |
