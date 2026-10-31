# Function audit: medications

> No full audit yet: rows from build gates only.
> Build gate 1.0.198: 49 functions checked.
> Audited 2026-10-31 against 1.0.198 (working tree over 830f7e1, before CI ran the tests named). Files read in full: 4 of the 4 this gate needed; not the area.
> Results: 31 Proved, 8 Read OK, 4 Fails, 1 Gap, 5 Not checked.

`Proved` here names a test that drives the function. Nothing was run: a green release is what confirms it.

## Files

| File | Read in full |
| --- | --- |
| data/ExactAlarms.kt | Yes |
| data/MedReminders.kt | Yes |
| data/MedSchedules.kt | Yes |
| ui/MedicationsPage.kt | Yes |
| data/MedExtras.kt | No (build gate: changed functions only) |

## Functions

| Function | Expected | Where | Result | Evidence | Issue |
| --- | --- | --- | --- | --- | --- |
| Three doses due in one minute | Three notifications, each naming one medication and amount in full, on the screen and expanded | MedReminders.kt:393 | Proved | MedReminderNotificationsTest.threeDosesDueAtOneMinuteAreThreeNotificationsEachNamingItsOwnMedication | |
| Taken on one notification | Logs that dose only, at the current time, no screen open; others stay open and shown | MedReminders.kt:508 | Proved | takenSkipAndSnoozeEachActOnTheirOneDoseAtTheCurrentTime (08:03:00 recorded for a dose due 08:00) | |
| Skip on one notification | Skips that dose only, takes down only its notification | MedReminders.kt:519 | Proved | takenSkipAndSnoozeEachActOnTheirOneDoseAtTheCurrentTime | |
| Snooze 15 min on one notification | Snoozes that dose only, for 15 minutes from now | MedReminders.kt:520 | Proved | takenSkipAndSnoozeEachActOnTheirOneDoseAtTheCurrentTime | |
| A second press, or a stale button | Records nothing more | MedReminders.kt:500 | Proved | aSecondPressOfTakenOrAStaleButtonRecordsNothingMore | |
| Taken pressed for a dose due on an earlier day | Logged on the dose's date at its due time | MedReminders.kt:516 | Read OK | `d.date == today` else `d.time + ":00"`, as the widget does. No test presses it across midnight | |
| Each notification and button is its own | Same-dose or other-dose presses never replace or fire another, across doses and days | MedReminders.kt:352 | Proved | everyNotificationAndButtonIsDistinctAcrossDosesAndDays. Identity is the tag and a `flexnotes-med://` data URI over one fixed id and request code 0, not numeric ids: same effect (`Intent.filterEquals`, Android's tag + id) | |
| Android folds several from the app together | Each keeps its own actions | MedReminders.kt:448 | Not checked | No group or summary is set, so folding is Android's auto-group. Needs a phone with 4 or more showing | |
| A dose whose alarm never came (delayed, replaced) | Still notified when an alarm is delivered | MedReminders.kt:559 | Proved | aRingAlsoNotifiesADoseWhoseAlarmWasNeverDelivered, anAlarmReplacedTheMomentItWasDueRingsStraightAway; MedRemindersTest.aDoseDuePastTheRungThroughTimeIsOwedItsNotificationUntilTwelveHoursOld, aDoseDueRightNowIsOwedItsNotificationNotPassedOver | |
| Phone off through a dose time | Rings when it is back on | MedReminders.kt:609 | Proved | aPhoneOffThroughADoseTimeRingsItWhenItIsBackOn | |
| Dose over 12 hours old | Not rung, only on Medications | MedReminders.kt:182 | Proved | aDoseOverTwelveHoursOldIsNotRungOnlyShownOnMedications | |
| First run after the update | Nothing old rings, mark set to now | MedReminders.kt:314 | Proved | withNoMarkYetNothingOldRingsAndTheMarkIsSetToNow | |
| Reminders switched off, then on | Nothing owed from the time off | MedReminders.kt:314 | Proved | withTheReminderOffNothingIsOwedWhenItIsSwitchedBackOn. Relies on an arm while off; the page's resume effect (MedicationsPage.kt:44) gives one | |
| Notifications refused | Nothing posted, chain moves on, no loop, doses still on Medications | MedReminders.kt:564 | Proved | withoutPermissionNothingIsPostedButTheRingStillMovesOn | |
| A dose un-ticked after its time | No new first reminder | MedReminders.kt:182 | Proved | aDoseUntickedAfterItsTimeDoesNotNotifyAgain | |
| A schedule started, resumed or edited after a dose time today | Reminders start from now; a dose time that passed before the schedule held it is not "owed", and catching up is only for a phone that was off | MedReminders.kt:182, 243 | Fails | Steps: reminders on, last ring in the morning or none. At 20:00 make a schedule with doses at 09:00, 13:00 and 19:00, start today (the editor's default, ScheduleEditorScreen.kt:144). `activate` → `Store.refresh(BODY)` → `rearm` → `next`: all three are after `medRungThrough`, not after now and within 12 hours, so `Ring(now, …)`, and `ring` posts three high-importance "Medication due at 09:00 / 13:00 / 19:00" at once. `arm` never moves the mark, so with no ring since it can be days old. Same for Resume (`pausedOn` lets today's earlier doses back in), moving a dose's time earlier and a restore. No test | #327 |
| Two doses of one injected medication shown together | Each button names its own site, and records it | MedReminders.kt:411, 484 | Proved | twoDosesOfOneInjectedMedicationRecordTheirOwnSites, …InTheOtherOrder, anInjectedDoseNamesItsSiteOnItsOwnButtonAndRecordsExactlyThat, theTakenButtonRecordsTheSiteItSaidEvenIfTheSuggestionMovedOn, aTakenButtonWithoutAReadableSiteWorksOutTheSuggestedOne | |
| Two doses of one injected medication when one comes back alone | Still different sites | MedReminders.kt:411 | Fails | `sitesFor` runs over the doses in this one `show` call, not every open dose. Creatine 5 mg and 10 mg due 09:00: buttons say Upper left and Upper right. Snooze the 10 mg; at 09:15 it is posted alone, `sitesFor` sees nothing used and names Upper left, as the 5 mg's still-open button does. Taken on both records one site twice. Same when a follow-up re-shows one of them while the other is snoozed, and when the widget takes one (TodayWidget.kt:212) while the other's button is showing. No test | #327 |
| Old unanswered doses left in the shade | A reminder doesn't outlive its use: old ones go or read as old, and a new dose always gets through | MedReminders.kt:358, 425 | Fails | `clearStale` removes a notification only when its dose is closed, and none has a timeout. everyNotificationAndButtonIsDistinctAcrossDosesAndDays shows yesterday's stay beside today's. Steps: five doses a day, not answered for ten days (away, or taken without logging): 50 notifications, every title "Medication due at 08:00" with no day. Android allows a package 50 at once and refuses more, so new doses then post nothing (platform limit from its source, not run here). The old version reached this about a third as fast, one per due time | #327 |
| Lock Medications, per notification | Locked: no name, amount or site on any notification or button | MedReminders.kt:407 | Proved | lockedMedicationsNameNothingInAnyOfTheThreeNotifications | |
| Lock Medications switched on with named notifications showing | They stop naming the medication | MedicationsPage.kt:108 | Gap | `rearm` → `clearStale` only removes closed doses, so the three already posted keep their names until answered or followed up. Already there before this build | #350 |
| Names on the lock screen | Sets each notification's visibility; the public version names nothing | MedReminders.kt:457 | Proved | namesOnTheLockScreenSetEachNotificationsVisibility | |
| Stored snoozes after the update | Old `<key>=<time>` values still decode and ring every open dose then | MedReminders.kt:145, 154 | Proved | MedRemindersTest.aSnoozeIsPerDoseAndTheOldKeyOnlyFormStillDecodes; aSnoozeStoredByTheOldVersionStillRingsEveryOpenDoseAtThatTime | |
| Snooze comes back | Only what was snoozed, as a first reminder; two with one time both ring; never dropped unrung | MedReminders.kt:562 | Proved | aSnoozedDoseRingsAloneAndDoesNotBringBackTheOthers, snoozingTwoInTheSameMinuteRingsBothWhenTheTimeComes, MedRemindersTest.aSnoozeWhoseTimeHasPassedRingsNowAndIsNeverDroppedUnrung | |
| A snooze saved while another receiver works | Kept; the mark never goes back | MedReminders.kt:568, 332 | Proved | aRingKeepsASnoozeSavedWhileItWorkedAndNeverMovesTheMarkBackwards, anArmKeepsASnoozeSavedWhileItWorkedAndNeverMovesTheMarkBackwards | |
| Follow-up | One per dose still open, says "Still due" | MedReminders.kt:205 | Proved | aRealFollowUpSaysStillDueAndOnlyForDosesStillOpen; MedRemindersTest.followUpsThatCameDueSinceTheLastRingAreOwed | |
| Follow-up, first reminder and snooze in one minute | All ring | MedReminders.kt:551 | Proved | aFollowUpAndAFirstReminderDueInTheSameMinuteBothRing, whicheverOfTheTwoAlarmsIsSetBringsBothReminders, aSnoozeComingBackInTheSameMinuteAsAFollowUpBringsBoth | |
| Dose ticked on the Medications screen | Only its notification goes | MedReminders.kt:370 | Proved | aDoseTickedOnTheMedicationsPathRemovesOnlyItsNotification, anAlarmForDosesAllTakenMeanwhileClearsTheirNotifications. Drives `take` + `arm`, not the screen. Widget path: calendar-shell ledger | |
| An old version's grouped notification | Replaced by one per dose; its button acts on every dose then | MedReminders.kt:367, 503 | Proved | anOldVersionsGroupedNotificationIsReplacedByOnePerDose, aButtonOnAnOldVersionsNotificationActsOnEveryDoseAtThatTimeAsBefore | |
| Doses at 08:00 and 08:05 | Each an alarm clock at its own minute | MedReminders.kt:342 | Proved | eachDoseHasAnAlarmClockAtItsOwnMinute (AlarmManager.nextAlarmClock) | |
| Snooze and follow-up alarms | Alarm clocks at their exact minute | MedReminders.kt:343 | Proved | aFollowUpAndASnoozeAreAlarmClocksAtTheirExactMinute | |
| An alarm with no data | Sets itself a minute later with the same extras, chain continues | MedReminders.kt:303, 541 | Proved | anAlarmThatCannotLoadItsDataRingsAgainAMinuteLaterWithTheSameExtras, aBootWithoutDataLeavesAnAlarmToTryAgain | |
| A medication alarm that times out | Retry a minute later | MedReminders.kt:600 | Read OK | `finished == null` → `retryLater`. Not driven: no test reaches the 25 s timeout | |
| Every alarm through the helper | No other caller, enforced by a test | ExactAlarms.kt:29 | Proved | onlyTheExactAlarmHelperSetsAnAlarm, and a search of `app/src/main` agrees. Its regex doesn't cover `am.set(`, `setRepeating` or `setInexactRepeating`, which nothing uses today | |
| Android 12 or 12L with the permission revoked | Falls back to the best left; Settings says so | ExactAlarms.kt:33 | Read OK | `SecurityException` → `setAndAllowWhileIdle`. Tests run on SDK 34 only, so nothing drives it | |
| `canExact` | True before Android 12 and from 13 | ExactAlarms.kt:23 | Read OK | `USE_EXACT_ALARM` and `SCHEDULE_EXACT_ALARM` (max SDK 32) in the manifest; `canScheduleExactAlarms` answers for either | |
| Settings › Medications exact-alarm row | Only on Android 12 or 12L when revoked, opens the request; re-read on return | MedicationsPage.kt:57 | Read OK | Hidden otherwise; returning re-reads `exact` and rearms medication alarms (logging ones: settings-data ledger). No test draws it | |
| Tap on the status-bar alarm icon | Opens Medications through its lock; never starts an import | ExactAlarms.kt:45 | Read OK | `EXTRA_OPEN_MEDICATIONS` → `MainActivity.handleIntent` → `MedLockGate`. No action on the intent, so its data URI isn't read as a file (MainActivity.kt:419) | |
| Dose due just before midnight | Follow-up after midnight; snooze across midnight | MedReminders.kt:250, 171 | Read OK | `next` starts yesterday; `openAt` takes the date from the key. Pure paths only | |
| Clock goes back an hour with a dose open in that hour | Rings once at its minute | MedReminders.kt:342 | Fails | `ring.at.atZone` takes the earlier instant of a repeated local time. Dose open at 01:30 on the night clocks go back, reached in the second 01:00 to 01:30 (any arm: a press, the app opening, a body refresh): `at.isAfter(now)` holds, the instant is an hour past, so the alarm fires at once, `ring` notifies with its alert, `arm` sets the same, repeating until the second 01:30. The same `atZone` was there before this build, but alarm clocks aren't throttled as `setExactAndAllowWhileIdle` was. No clock-change test | #346 |
| Clock goes forward over a dose time | Rings just after the gap | MedReminders.kt:342 | Read OK | `atZone` moves 02:30 to 03:30; the key still finds the dose. No test | |
| Years of history | Next reminder and owed doses from the full plan | MedReminders.kt:233 | Proved | MedRemindersTest.overYearsOfRecordsTheNextReminderComesFromTheFullPlan | |
| Test fixture for years of history (#333 slice, listed here for want of a test area) | Over three years, sparse stretches, an exercise last logged long ago, both sources, several doses in one minute; ScreenFixture unchanged | LongHistoryFixture.kt:54 | Proved | FunctionHelpersTest.theFixtureHoldsYearsOfThinHistoryWithTheOldExerciseLongAgo; ScreenFixture isn't in the diff | |
| Screen test helpers (#333) | Open a screen, tap a control, read back what's drawn and stored, today fixed | FunctionTest.kt:70 | Proved | FunctionHelpersTest.aScreenOnTheFixtureShowsAValueFromItsOldestYearAfterTappingThroughToIt. Slice only: no screen's function is driven with it yet beyond this one | |
| Notification and alarm harness (#333) | Fire both receivers, assert what's posted, press an action, assert what's recorded and the next alarm | ReminderHarness.kt:80 | Proved | ReminderHarnessTest (both receivers), MedReminderNotificationsTest | |
| These tests in CI | Run with the rest, without adding much time | ReminderHarness.kt:262 | Not checked | About 50 Robolectric tests, most with a real database and a 600 ms settle or two. CI's run time settles it | |
| Rings at its minute with the screen off, in doze, on a real phone | The notification alerts on time | ExactAlarms.kt:32 | Not checked | Needs a phone: no device here | |
| Android's alarm icon and next-alarm time on the lock screen | Acceptable to read; no names | ExactAlarms.kt:13 | Not checked | The icon shows while any reminder is set, and phones differ in what they show, including "until next alarm" Do Not Disturb. Needs a phone | |
| Reminder sound under Do Not Disturb | The owner expects alarms to ring | MedReminders.kt:400 | Not checked | The alarm is an alarm clock, but what alerts is a `CATEGORY_REMINDER` notification on a high-importance channel, not an alarm sound. Needs a phone | |
