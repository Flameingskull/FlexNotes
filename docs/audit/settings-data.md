# Function audit: settings-data

> No full audit yet: rows from build gates only.
> Build gate 1.0.198: 16 functions checked.
> Audited 2026-10-31 against 1.0.198 (working tree over 830f7e1, before CI ran the tests named). Files read in full: 2 of the 2 this gate needed; not the area.
> Results: 7 Proved, 5 Read OK, 3 Fails, 1 Gap, 0 Not checked.

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
| Logging alarm that times out | The receiver sets the retry | LogReminders.kt:288 | Read OK | `finished == null` → `retryLater`. No test reaches the receiver's timeout | |
| Logging alarm with no data | Rings anyway, without the already-logged check | LogReminders.kt:221 | Read OK | `snap == null` shows every kind due, then arms. No test | |
| Several kinds at one minute | One notification each, each to its own screen | LogReminders.kt:211, 248 | Read OK | Own id and request code per kind | |
| After a reboot, with settings not loading | The logging chain is set again too | MedReminders.kt:619 | Fails | `MedBootReceiver` calls `LogReminders.arm` inside one 25 s timeout. On timeout only `MedReminders.retryLater` is set, and that alarm's `ring` arms medication only, so no logging reminder is set until the app next starts. The timeout branch is new in this build. Criterion: an alarm that can't load its data sets a retry, so the chain never ends. Unlikely (settings taking over 25 s), one line to fix | #346 |
| Clock goes back an hour with a logging reminder in that hour | Rings once | LogReminders.kt:192 | Fails | Same `atZone` path as the medication row in the medications ledger: in the second 01:00 to 01:30, an arm for a 01:30 reminder sets a past instant, it fires at once, shows, arms the same, and repeats. Already there before this build. Needs a reminder set in that hour. No test | #346 |
| Settings › Reminders exact-alarm row | Only on Android 12 or 12L when revoked, opens the request | RemindersPage.kt:96 | Read OK | Wording in strings.xml:1988 and 1990. No test draws it | |
| Allowing exact alarms on Android 12 or 12L, then Back | The alarm already set becomes exact | RemindersPage.kt:58 | Gap | Resume re-reads `exact` but doesn't call `LogReminders.rearm`, and Settings › Medications rearms only medication alarms. There's no receiver for the permission's state change. The pending alarm stays inexact (it may be late) until it rings or a setting changes. Already there before this build | #346 |
| Settings says reminders may be late | Only where it's true: Android 12 or 12L with the permission revoked | strings.xml:1988 | Read OK | Nothing under `app/src/main` says "ten minutes" any more, and SettingsScreen.kt:278 hides the search entry too. A search of the sources: no test opens either page | |
| The code map's note on reminder clocks | Tells a test author the truth | docs/CODEMAP.md:328 | Fails | "`LogReminders` and `MedReminders` still read `LocalDateTime.now()` for the next alarm, so assert the rule (weekday, time, key), not a fixed date." Both now read `Dates.nowDateTime()` and the tests assert fixed dates, so the guidance #333 asks for is wrong | #333 |
