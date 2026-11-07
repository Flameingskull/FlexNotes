# Function audit: settings-data

> No full audit yet: rows from build gates only.
> Build gate 1.0.198: 17 functions checked (re-checked after fix round 2).
> Audited 2026-10-31 against 1.0.198 (working tree over 830f7e1, before CI ran the tests named). Files read in full: 2 of the 2 this gate needed; not the area.
> Build gate 1.0.200: 3 functions checked (#330, the no-goal colour setting; working tree over e67bae8, before CI ran the tests named).
> Results: 15 Proved, 5 Read OK, 0 Fails, 0 Gap, 0 Not checked.

`Proved` here names a test that drives the function. Nothing was run: a green release is what confirms it.

## Files

| File | Read in full |
| --- | --- |
| data/LogReminders.kt | Yes |
| ui/RemindersPage.kt | Yes |
| data/Settings.kt | No (build gate: changed functions only) |
| ui/SettingsScreen.kt | No (build gate: changed functions only) |

## Functions

| Function | Expected | Where | Result | Evidence | Issue |
| --- | --- | --- | --- | --- | --- |
| Next logging alarm | The exact next chosen weekday and time, from the shared clock | LogReminders.kt:186 | Proved | ReminderHarnessTest.onceTodaysTimeHasPassedTheNextLoggingAlarmIsTheNextChosenWeekdayAndTime (a Friday after 08:00) | |
| No wall-clock read in the reminder files | Swept | LogReminders.kt:186 | Proved | MedReminderNotificationsTest.noReminderFileReadsTheWallClockDirectly; a search of `data/*Reminders.kt` agrees | |
| A logging reminder | An alarm clock at its exact minute | LogReminders.kt:193 | Proved | ReminderHarnessTest.aLoggingReminderIsAnAlarmClockAtItsExactMinute | |
| A logging alarm rings | Posts its notification, sets the next alarm | LogReminders.kt:214 | Proved | ReminderHarnessTest.aLoggingAlarmPostsItsNotificationAndSetsTheNextAlarm | |
| Already logged that day | Stays quiet, others still ring | LogReminders.kt:221 | Proved | ReminderHarnessTest.aLoggingReminderForSomethingAlreadyLoggedStaysQuiet | |
| Notifications refused | Nothing posted, next alarm still set | LogReminders.kt:229 | Proved | ReminderHarnessTest.withoutPermissionAReminderPostsNothingButTheNextAlarmIsStillSet | |
| `retryLater` | Same key a minute later | LogReminders.kt:204 | Proved | ReminderHarnessTest.aLoggingAlarmThatCannotFinishRingsAgainAMinuteLaterWithTheSameKey, which calls it directly | |
| Logging alarm that times out or fails | The receiver sets a retry that backs off | LogReminders.kt:305 | Proved | ReminderHarnessTest.aLoggingAlarmThatOverrunsItsTimeSetsARetryAndTheGapGrows | |
| Logging alarm with no data | Rings anyway, without the already-logged check | LogReminders.kt:221 | Read OK | `snap == null` shows every kind due, then arms. No test | |
| Several kinds at one minute | One notification each, each to its own screen | LogReminders.kt:211, 248 | Read OK | Own id and request code per kind | |
| After a reboot that doesn't finish | Both chains are left an alarm | MedReminders.kt:804 | Proved | aBootThatOverrunsItsTimeLeavesBothChainsAnAlarm, aBootWithoutDataSetsBothChainsToTryAgain | |
| A reboot where only medication data fails, with a logging reminder due within the minute | That logging reminder still rings | MedReminders.kt:810 | Read OK | Fixed: each chain retries only for its own failed step, and a timeout in the medication stage leaves the logging alarm alone (`stage`). Traced; I didn't find a test that boots with a reminder a minute ahead and asserts its key, so not Proved | |
| Clock goes back an hour with a logging reminder in that hour | No loop; the alarm is for the 01:30 still to come | LogReminders.kt:210 | Proved | MedReminderNotificationsTest.anAlarmForATimeThatHappensTwiceIsSetForTheOneStillToCome | |
| Settings › Reminders exact-alarm row | Only on Android 12 or 12L when revoked, opens the request | RemindersPage.kt:96 | Proved | ExactAlarmSettingsAndroid13Test.settingsRemindersHasNoExactAlarmRowEvenWithAReminderOn, ExactAlarmSettingsAndroid12Test.settingsRemindersShowsTheRowWhenAReminderIsOn | |
| Allowing exact alarms on Android 12 or 12L | Both chains are set again | MedReminders.kt:773 | Proved | ExactAlarmPermissionTest.allowingExactAlarmsAgainSetsBothChainsAgainAsAlarmClocks, through the platform's broadcast. Whether a phone sends it needs an Android 12 phone | |
| Settings says reminders may be late | Only where it's true: Android 12 or 12L with the permission revoked; search matches the page | strings.xml:1988, SettingsScreen.kt:285 | Proved | ExactAlarmSettingsAndroid13Test.searchingForExactAlarmsFindsNoRowThatIsNotThere, ExactAlarmSettingsAndroid12Test.searchFindsTheRow, and the page tests' `assertNotShows("ten minutes")` | |
| The code map's note on reminder clocks | Tells a test author the truth | docs/CODEMAP.md:330 | Read OK | Now says the rest timer's `now()` is still `System.currentTimeMillis()` (#343); the `ExactAlarms.kt` row names `epochMillis`, `retryDelayMs`, `retryAt`, `ACTION_PERMISSION_CHANGED` and `LiveClock` | |
| Plain choice stored and read back | Kept across restarts and backups; off by default | Settings.kt:280, :488, :794 | Read OK | Saved as "1" or left out, read with bool(); MeasurementGoalsTest.thePlainChoiceDefaultsToOff checks only the default, no test round-trips it through the store |  |
| Settings › Colour changes with no goal | Rise green, fall red (default) or Plain, saved and shown at once | SettingsScreen.kt:388-395 | Proved | NoGoalColourTest.choosingPlainInSettingsSavesIt; the effect on colours, NoGoalColourTest.theChangeColourFollowsTheSettingAtOnce |  |
| Search finds it | Typing words a person would use lists the row | strings.xml settings_no_goal_colour_kw | Proved | NoGoalColourTest.theRowIsOnSettingsAndSearchFindsIt ("which way"), and round 1 adds the American spelling: NoGoalColourTest.searchingTheAmericanSpellingFindsTheRow |  |
