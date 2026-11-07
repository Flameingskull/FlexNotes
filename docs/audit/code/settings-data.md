# Code audit: settings-data

> No full audit yet: rows from build gates only.
> Build gate 1.0.198: 13 functions checked, re-checked after fix rounds 1 and 2.
> Build gate 1.0.200: 2 functions checked (#330, which way is good; working tree over e67bae8, before CI ran the tests named).
> Build gate 1.0.200, fix rounds 1 and 2 re-checked: the findings below and the rows they touch. CI on the round-1 tree compiled and passed 739 of 741 tests; CI on the round-2 tree (c4e8a86) passed 740 of 741, including every #330 test; the one failure is the known WholeScreen320Test.librarySearch (#349).
> Audited 2026-10-31 against 1.0.198 (working tree on 830f7e1, nothing committed). Files read in full: 0 of 4 in scope; the rest of the area was not read.
> Results: 9 Verified, 7 Read OK, 0 Defect, 0 Risk, 0 Improve, 0 Not checked.
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
| PortableSettings.plainNoGoal: stored and read back | Data safety | Settings.kt:280, :488, :619, :794 | Read OK | toMap writes "1" or nothing, portableFrom reads it with bool(); key unique; absent reads false, so existing installs and older backups keep rise green. resetToDefaults resets it with the other preferences. No test reads it back from disk | #330 |
| Settings row and search | Logic | SettingsScreen.kt:388-395 | Verified | NoGoalColourTest.theRowIsOnSettingsAndSearchFindsIt ("which way"), choosingPlainInSettingsSavesIt | #330 |
| Settings.awaitWrites (test seam) | Concurrency | Settings.kt:341-343 | Read OK | Fix round 2: joins a snapshot of the settings scope's children (SupervisorJob + IO). Its only children are the start-up load (scope.async, finite: DataStore first() and one cleanup) and the two save launches (writeLock, a write, then Store.refresh, which is a suspend call, so it is inside the joined job). No collector or loop runs in the scope, so it can't wait forever unless a write itself hangs. A save launched while it waits is missed, which doesn't matter at teardown: closeSeams has already set preferences with showWith (no write) and awaited updateDeviceNow, no settings job starts another, and Store.refresh's AppHooks.dataChanged is a no-op under the tests' plain Application. Internal, and main code doesn't call it | #330 |
