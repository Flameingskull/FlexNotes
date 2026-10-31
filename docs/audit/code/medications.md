# Code audit: medications

> No full audit yet: rows from build gates only.
> Build gate 1.0.198: 47 functions checked.
> Audited 2026-10-31 against 1.0.198 (working tree on 830f7e1, nothing committed). Files read in full: 3 of 3 in scope; the rest of the area was not read.
> Results: 25 Verified, 8 Read OK, 5 Defect, 7 Risk, 0 Improve, 2 Not checked.
> `Verified` names a test that would fail if this broke. The tests have not run yet (no SDK here): a green release confirms those rows.

## Files

| File | Read in full |
| --- | --- |
| data/MedReminders.kt | Yes |
| data/ExactAlarms.kt | Yes |
| ui/MedicationsPage.kt | Yes |

## Checks

| Function, class or concern | Check | Where | Result | Evidence | Issue |
| --- | --- | --- | --- | --- | --- |
| Snoozes.decode, entryOf: old key-only form | Data safety | MedReminders.kt:145 | Verified | MedRemindersTest.aSnoozeIsPerDoseAndTheOldKeyOnlyFormStillDecodes; MedReminderNotificationsTest.aSnoozeStoredByTheOldVersionStillRingsEveryOpenDoseAtThatTime | |
| Snoozes.covers, with, encode | Logic | MedReminders.kt:114 | Verified | aSnoozeIsPerDoseAndTheOldKeyOnlyFormStillDecodes | |
| Snoozes.without | Concurrency | MedReminders.kt:129 | Verified | aRingKeepsASnoozeSavedWhileItWorkedAndNeverMovesTheMarkBackwards; snoozingTwoInTheSameMinuteRingsBothWhenTheTimeComes | |
| Snoozes.pruned: never dropped unrung, gone after 2 days or dose closed | Logic | MedReminders.kt:136 | Verified | aSnoozeWhoseTimeHasPassedRingsNowAndIsNeverDroppedUnrung | |
| A snooze left over while reminders were off | Logic | MedReminders.kt:136, :241, :314 | Defect | Snooze at 08:02 (until 08:17), reminders off 08:05, on again the next morning. pruned keeps it (under 2 days), next gives Ring(now, snoozed), ring posts yesterday's dose as first reminder. Contradicts the comment at :313 "reminders off for a while: nothing old rings". withTheReminderOffNothingIsOwedWhenItIsSwitchedBackOn has no snooze in it | #327 |
| openAt | Logic | MedReminders.kt:171 | Verified | MedRemindersTest.aSnoozedDoseLeavesOutOfItsTimesRingUntilItsSnoozeRings; aRealFollowUpSaysStillDueAndOnlyForDosesStillOpen | |
| catchUp: window, taken, skipped, snoozed, 12 hours | Logic | MedReminders.kt:182 | Verified | aDoseDuePastTheRungThroughTimeIsOwedItsNotificationUntilTwelveHoursOld; aDoseOverTwelveHoursOldIsNotRungOnlyShownOnMedications | |
| catchUp: dose that didn't exist or wasn't due when its time passed | Logic | MedReminders.kt:189, :243, :314 | Defect | The mark moves only on a ring, so it can be a day or more old. Last ring Thu 21:00; Fri 14:00 a new schedule is started today with doses at 08:00 and 12:00 (or a paused run resumed, or a dose moved from 21:00 to 08:00). Store.refresh(BODY) rearms, both are after the mark and under 12 hours old, so Ring(now) posts "due 08:00" and "due 12:00" seconds after saving. Someone who already took today's by hand is told to take it again. No test | #327 |
| followUps | Logic | MedReminders.kt:205 | Verified | followUpsThatCameDueSinceTheLastRingAreOwed; aFollowUpAndAFirstReminderDueInTheSameMinuteBothRing | |
| next: earliest of doses, follow-ups, snoozes, owed | Logic | MedReminders.kt:233 | Verified | aSnoozedDoseLeavesOutOfItsTimesRingUntilItsSnoozeRings; aDoseDueRightNowIsOwedItsNotificationNotPassedOver; overYearsOfRecordsTheNextReminderComesFromTheFullPlan | |
| rearm: debounce, cancel | Concurrency | MedReminders.kt:276 | Read OK | runCatching also catches the cancellation, harmless here because the coroutine ends straight after. pending is unguarded across threads; the worst case is two arms, which agree | |
| chainIntent: one alarm whatever its extras | Lifecycle | MedReminders.kt:289 | Read OK | Action and receiver fixed, extras not part of identity, FLAG_UPDATE_CURRENT replaces them. Separate from the log and rest alarms (other receiver classes), so request code 0 doesn't clash | |
| retryLater: same extras, a minute on | Failure | MedReminders.kt:303 | Verified | anAlarmThatCannotLoadItsDataRingsAgainAMinuteLaterWithTheSameExtras; aBootWithoutDataLeavesAnAlarmToTryAgain | #346 |
| retryLater: no back-off | Failure | MedReminders.kt:81, :303 | Risk | A database that never opens (a failed migration, corruption: Store.open returns false): an alarm clock every 60 s plus a load attempt, for ever, each waking the phone from doze and starting the process, until the app is opened. Back off, for example to 15 minutes | #346 |
| reconciled: mark on first run and while off | Data safety | MedReminders.kt:309 | Verified | withNoMarkYetNothingOldRingsAndTheMarkIsSetToNow; withTheReminderOffNothingIsOwedWhenItIsSwitchedBackOn. Followed the switch: MedicationsPage's resume effect arms while off, so the mark moves before the switch can be tapped | |
| Mark never moves backwards | Lifecycle | MedReminders.kt:314, :572 | Risk | Clock wrongly set ahead then corrected, or travel west: the mark stays in the future, so catchUp and followUps are inert until that time. Alarms still ring by key, but the phone-off backstop is off, and of a follow-up and a dose in one minute only the alarm's key shows. No reset when the time changes | #346 |
| arm: written from the stored value | Concurrency | MedReminders.kt:329 | Verified | anArmKeepsASnoozeSavedWhileItWorkedAndNeverMovesTheMarkBackwards | |
| arm: reminders off cancels | Lifecycle | MedReminders.kt:338 | Verified | withTheReminderOffNothingIsOwedWhenItIsSwitchedBackOn | |
| arm, retryLater: local time to epoch in the repeated hour | Dates and time | MedReminders.kt:304, :342 | Defect | Sequence: an open dose due 02:30 where clocks go 03:00 to 02:00; any arm at 02:10 in the second pass (app start, a BODY refresh from Health Connect). at.isAfter(now), so Ring(02:30); atZone takes the earlier offset, an instant already 40 minutes past, so the alarm fires at once; ring re-posts it with sound, the mark can't move back, arm repeats. Alerts in a tight loop for up to an hour. Old code had the same conversion, but setAlarmClock removes all rate limiting. Same at LogReminders.kt:192 and :205 | #346 |
| clearStale: per dose, old grouped notification replaced | Data safety | MedReminders.kt:358 | Verified | aDoseTickedOnTheMedicationsPathRemovesOnlyItsNotification; anAlarmForDosesAllTakenMeanwhileClearsTheirNotifications; anOldVersionsGroupedNotificationIsReplacedByOnePerDose | #327 |
| ensureLoaded | Failure | MedReminders.kt:377 | Read OK | Store.open catches everything but cancellation and returns false, so a load failure reaches ring as null | |
| show: one per dose, unique tag and intents | Logic | MedReminders.kt:393 | Verified | threeDosesDueAtOneMinuteAreThreeNotificationsEachNamingItsOwnMedication; everyNotificationAndButtonIsDistinctAcrossDosesAndDays | #327 |
| show: Lock Medications, text and buttons | Security | MedReminders.kt:426 | Verified | lockedMedicationsNameNothingInAnyOfTheThreeNotifications | |
| show: sort key under Lock Medications | Security | MedReminders.kt:462 | Defect | Lock on: setSortKey("$key ${medication} ${id}") still puts the medication's name in the notification, readable by any notification listener (a watch app, an automation app) and in dumpsys. The rule is that a locked notification names nothing. The test checks title, text, big text and buttons, not the sort key | #327 |
| show: lock-screen visibility and public version | Security | MedReminders.kt:413, :457 | Verified | namesOnTheLockScreenSetEachNotificationsVisibility | |
| show: Android's 50-per-app limit | Failure | MedReminders.kt:472 | Risk | Notifications are never timed out, and one per dose reaches the limit faster (3 or 4 doses a day unanswered: about two weeks). Over it Android refuses new ones silently, so a due dose isn't notified. Time out or clear those days old | #327 |
| sitesFor: doses shown together differ | Logic | MedReminders.kt:411, :484 | Verified | twoDosesOfOneInjectedMedicationRecordTheirOwnSites and ...TakenInTheOtherOrder; aTakenButtonWithoutAReadableSiteWorksOutTheSuggestedOne | #327 |
| Taken button: site fixed when posted | Logic | MedReminders.kt:423, :445, :515 | Risk | Doses not posted together can name one site. Daily injection, Thursday's left unanswered: Thursday's and Friday's both say "Taken, Left"; pressing both records Left twice, where the old code rotated. Same when one of two doses at one time is snoozed past the other's follow-up. Re-post the other open doses of that medication quietly after a Taken | #327 |
| handle: acts on one dose, at the current time | Logic | MedReminders.kt:500 | Verified | takenSkipAndSnoozeEachActOnTheirOneDoseAtTheCurrentTime; aSecondPressOfTakenOrAStaleButtonRecordsNothingMore | #327 |
| handle: old notification's button | Data safety | MedReminders.kt:503, :524 | Verified | aButtonOnAnOldVersionsNotificationActsOnEveryDoseAtThatTimeAsBefore (Taken only; Skip and Snooze read through the same branches) | |
| handle: notification cancelled before the write | Failure | MedReminders.kt:503 | Risk | A write that fails or times out leaves the notification gone and nothing recorded, with no message. Already there, same order before this build | #327 |
| ring: which doses, in what wording | Logic | MedReminders.kt:539 | Verified | aRingAlsoNotifiesADoseWhoseAlarmWasNeverDelivered; aSnoozeComingBackInTheSameMinuteAsAFollowUpBringsBoth; aSnoozedDoseRingsAloneAndDoesNotBringBackTheOthers | #327 |
| ring: no permission still moves on | Lifecycle | MedReminders.kt:564 | Verified | withoutPermissionNothingIsPostedButTheRingStillMovesOn | |
| ring: settings write fails | Failure | MedReminders.kt:568, :575, :591 | Defect | Every ring now writes the mark. Storage full: store.edit throws IOException, through updateDeviceNow and past withTimeoutOrNull (only a timeout gives null), so no retry and arm never runs; the uncaught exception closes the process. A fresh process doesn't restore it: arm returns at :322 with no snapshot. The chain is dead until the app is opened. arm's own write at :332 comes before the alarm at :343 too, and rearm's runCatching hides it | #346 |
| ring and the rearm from Store.open on a cold start | Concurrency | MedReminders.kt:381, :563 | Risk | Alarm starts a dead process; Store.reload schedules an arm 300 ms on. If posting and the write take longer, that arm reads the old mark, gets Ring(now), and can set it after ring's own arm: the same doses alert twice. Heals itself | #327 |
| MedAlarmReceiver: extras, timeout | Lifecycle | MedReminders.kt:580 | Read OK | Not exported, no filter; a missing dose extra means the old notification. The timeout branch has no driven test (see the tests ledger) | |
| MedBootReceiver | Lifecycle | MedReminders.kt:609 | Verified | aPhoneOffThroughADoseTimeRingsItWhenItIsBackOn; aBootWithoutDataLeavesAnAlarmToTryAgain. Log reminders armed first, as they need no data | |
| Clock seam and locale in stored text | Dates and time | MedReminders.kt:82, :304 | Verified | noReminderFileReadsTheWallClockDirectly. ofPattern uses standard digits in any language, so stored keys always parse | #345 |
| Medical data in logs; exported components; string-built SQL | Security | MedReminders.kt, ExactAlarms.kt | Read OK | No logging, receivers not exported, no SQL here, every PendingIntent immutable | |
| ExactAlarms.set: an alarm clock at the minute | Logic | ExactAlarms.kt:32 | Verified | eachDoseHasAnAlarmClockAtItsOwnMinute; aFollowUpAndASnoozeAreAlarmClocksAtTheirExactMinute | #346 |
| Only the helper sets an alarm | Structure | ExactAlarms.kt:18 | Verified | onlyTheExactAlarmHelperSetsAnAlarm | #346 |
| ExactAlarms.set: fallback on Android 12 or 12L | Failure | ExactAlarms.kt:35 | Risk | setAndAllowWhileIdle has no 10-minute window: by the platform's inexact rule it can be up to an hour late, against the old setWindow's 10 minutes (doze aside). Accepted as the last resort, but no test drives the SecurityException branch | #346 |
| ExactAlarms.canExact, show intents | Lifecycle | ExactAlarms.kt:23, :50 | Read OK | Followed to MainActivity.handleIntent: the Medications extra returns early, and a data URI with a null action never reaches the import branch at MainActivity.kt:419 | |
| ExactAlarms.cancel | Lifecycle | ExactAlarms.kt:40 | Read OK | Same intent identity as set | |
| MedicationsPage: exact row, rearm on resume | Lifecycle | MedicationsPage.kt:42, :57 | Read OK | Row only when not allowed; resume re-reads it and rearms the medication chain. No test draws this page | #346 |
| Alarms ring on time in doze, on a real phone | Lifecycle | ExactAlarms.kt | Not checked | Needs a device; no phone, emulator or SDK here | #346 |
| Several notifications folded by Android keep their own buttons | Lifecycle | MedReminders.kt:448 | Not checked | No group or summary is set, so Android's own bundling applies. Needs a device | #327 |
