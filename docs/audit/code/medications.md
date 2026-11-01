# Code audit: medications

> No full audit yet: rows from build gates only.
> Build gate 1.0.198: 52 functions checked, re-checked after fix round 1.
> Audited 2026-10-31 against 1.0.198 (working tree on 830f7e1, nothing committed). Files read in full: 3 of 3 in scope; the rest of the area was not read.
> Results: 37 Verified, 9 Read OK, 1 Defect, 2 Risk, 1 Improve, 2 Not checked.
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
| A snooze left over while reminders were off | Logic | MedReminders.kt:382 | Verified | aSnoozeLeftOverWhileRemindersWereOffDoesNotRingWhenTheyAreBackOn. Re-checked after fix round 1: a snooze whose time passed is dropped while reminders are off | #327 |
| openAt | Logic | MedReminders.kt:171 | Verified | MedRemindersTest.aSnoozedDoseLeavesOutOfItsTimesRingUntilItsSnoozeRings; aRealFollowUpSaysStillDueAndOnlyForDosesStillOpen | |
| catchUp: window, taken, skipped, snoozed, 12 hours | Logic | MedReminders.kt:182 | Verified | aDoseDuePastTheRungThroughTimeIsOwedItsNotificationUntilTwelveHoursOld; aDoseOverTwelveHoursOldIsNotRungOnlyShownOnMedications | |
| catchUp: dose that didn't exist or wasn't due when its time passed | Logic | MedReminders.kt:380 | Verified | startingAScheduleTodayDoesNotRingTheDosesAlreadyPast; movingADoseEarlierDoesNotRingItAtOnce; resumingAPauseDoesNotRingTheDosesOfTodayAlreadyPast; undoingAnEndDoesNotRingTheDosesOfTodayAlreadyPast; aRestoredRunDoesNotRingTheDosesOfTodayAlreadyPast; aRingAfterThePlanChangedOwesNothingAlreadyPast. Re-checked: a changed plan stamp moves the mark to now | #327 |
| followUps | Logic | MedReminders.kt:205 | Verified | followUpsThatCameDueSinceTheLastRingAreOwed; aFollowUpAndAFirstReminderDueInTheSameMinuteBothRing | |
| next: earliest of doses, follow-ups, snoozes, owed | Logic | MedReminders.kt:233 | Verified | aSnoozedDoseLeavesOutOfItsTimesRingUntilItsSnoozeRings; aDoseDueRightNowIsOwedItsNotificationNotPassedOver; overYearsOfRecordsTheNextReminderComesFromTheFullPlan | |
| rearm: debounce, cancel | Concurrency | MedReminders.kt:276 | Read OK | runCatching also catches the cancellation, harmless here because the coroutine ends straight after. pending is unguarded across threads; the worst case is two arms, which agree | |
| chainIntent: one alarm whatever its extras | Lifecycle | MedReminders.kt:289 | Read OK | Action and receiver fixed, extras not part of identity, FLAG_UPDATE_CURRENT replaces them. Separate from the log and rest alarms (other receiver classes), so request code 0 doesn't clash | |
| retryLater: same extras, a minute on | Failure | MedReminders.kt:303 | Verified | anAlarmThatCannotLoadItsDataRingsAgainAMinuteLaterWithTheSameExtras; aBootWithoutDataLeavesAnAlarmToTryAgain | #346 |
| retryLater: no back-off | Failure | ExactAlarms.kt:41 | Verified | theRetryGapGrowsAndAnAlarmIsAlwaysSet: 1, 2, 4, 8, then 15 minutes. A normal arm drops the attempt extra, so success resets it | #346 |
| reconciled: mark on first run and while off | Data safety | MedReminders.kt:309 | Verified | withNoMarkYetNothingOldRingsAndTheMarkIsSetToNow; withTheReminderOffNothingIsOwedWhenItIsSwitchedBackOn. Followed the switch: MedicationsPage's resume effect arms while off, so the mark moves before the switch can be tapped | |
| Mark never moves backwards | Lifecycle | MedReminders.kt:716 | Verified | aMarkAheadOfTheClockIsResetWhenTheClockOrZoneChangesButNotOnAReboot, on a fixed clock. On a real zone change clockChanged reads the stale clock (see the Dates.clockSource row), so that half isn't fixed yet | #346 |
| arm: written from the stored value | Concurrency | MedReminders.kt:329 | Verified | anArmKeepsASnoozeSavedWhileItWorkedAndNeverMovesTheMarkBackwards | |
| arm: reminders off cancels | Lifecycle | MedReminders.kt:338 | Verified | withTheReminderOffNothingIsOwedWhenItIsSwitchedBackOn | |
| arm, retryLater: local time to epoch in the repeated hour | Dates and time | ExactAlarms.kt:33 | Verified | anAlarmForATimeThatHappensTwiceIsSetForTheOneStillToCome; MedRemindersTest epochMillis values. Traced first pass, second pass, Ring(now) and a time outside the overlap: no instant left in the past while the local time is ahead. Only holds if the local time is right (see the Dates.clockSource row) | #346 |
| clearStale: per dose, old grouped notification replaced | Data safety | MedReminders.kt:358 | Verified | aDoseTickedOnTheMedicationsPathRemovesOnlyItsNotification; anAlarmForDosesAllTakenMeanwhileClearsTheirNotifications; anOldVersionsGroupedNotificationIsReplacedByOnePerDose | #327 |
| ensureLoaded | Failure | MedReminders.kt:377 | Read OK | Store.open catches everything but cancellation and returns false, so a load failure reaches ring as null | |
| show: one per dose, unique tag and intents | Logic | MedReminders.kt:393 | Verified | threeDosesDueAtOneMinuteAreThreeNotificationsEachNamingItsOwnMedication; everyNotificationAndButtonIsDistinctAcrossDosesAndDays | #327 |
| show: Lock Medications, text and buttons | Security | MedReminders.kt:426 | Verified | lockedMedicationsNameNothingInAnyOfTheThreeNotifications | |
| show: sort key under Lock Medications | Security | MedReminders.kt:561 | Verified | lockedMedicationsNameNothingInAnyOfTheThreeNotifications now asserts on sortKey | #327 |
| show: lock-screen visibility and public version | Security | MedReminders.kt:413, :457 | Verified | namesOnTheLockScreenSetEachNotificationsVisibility | |
| show: Android's 50-per-app limit | Failure | MedReminders.kt:459 | Verified | anUnansweredReminderGoesOnceItsDoseIsOlderThanYesterdayAndTitlesNameTheDay. Clears a dose due before yesterday | #327 |
| sitesFor: doses shown together differ | Logic | MedReminders.kt:411, :484 | Verified | twoDosesOfOneInjectedMedicationRecordTheirOwnSites and ...TakenInTheOtherOrder; aTakenButtonWithoutAReadableSiteWorksOutTheSuggestedOne | #327 |
| Taken button: site fixed when posted | Logic | MedReminders.kt:602 | Risk | siteFor fixes the cases found (yesterdaysUnansweredDoseAndTodaysNameDifferentSites, aSnoozedInjectedDoseReturnsWithADifferentSiteAndBothRecordTheirOwn). Still open: daily injection unanswered for 3 days. Thu says Left. Fri, group Thu and Fri, says Right. Sat, group Fri and Sat (Thu is cleared), gives Fri Left and Sat Right, but Fri still shows the Right it was posted with. Pressing Fri and Sat records Right twice | #327 |
| handle: acts on one dose, at the current time | Logic | MedReminders.kt:500 | Verified | takenSkipAndSnoozeEachActOnTheirOneDoseAtTheCurrentTime; aSecondPressOfTakenOrAStaleButtonRecordsNothingMore | #327 |
| handle: old notification's button | Data safety | MedReminders.kt:503, :524 | Verified | aButtonOnAnOldVersionsNotificationActsOnEveryDoseAtThatTimeAsBefore (Taken only; Skip and Snooze read through the same branches) | |
| handle: notification cancelled before the write | Failure | MedReminders.kt:651 | Verified | aButtonWhoseWriteFailsLeavesTheReminderToPressAgain | #327 |
| ring: which doses, in what wording | Logic | MedReminders.kt:539 | Verified | aRingAlsoNotifiesADoseWhoseAlarmWasNeverDelivered; aSnoozeComingBackInTheSameMinuteAsAFollowUpBringsBoth; aSnoozedDoseRingsAloneAndDoesNotBringBackTheOthers | #327 |
| ring: no permission still moves on | Lifecycle | MedReminders.kt:564 | Verified | withoutPermissionNothingIsPostedButTheRingStillMovesOn | |
| ring: settings write fails | Failure | MedReminders.kt:394 | Verified | aFailedSettingsWriteDoesNotEndTheChainOrRepeatTheAlerts; anExceptionInARingSetsARetryInsteadOfClosingTheApp. The change stays in memory and the alarm is set from it. With storage full across a cold start, the old mark on disk re-alerts open doses under 12 hours old; no loop | #346 |
| ring and the rearm from Store.open on a cold start | Concurrency | MedReminders.kt:665 | Verified | aColdStartsOwnArmDoesNotRingTheSameDosesAgain | #327 |
| MedAlarmReceiver: extras, timeout | Lifecycle | MedReminders.kt:727 | Verified | anAlarmThatOverrunsItsTimeSetsARetry; anExceptionInARingSetsARetryInsteadOfClosingTheApp |  |
| MedBootReceiver | Lifecycle | MedReminders.kt:768 | Verified | aPhoneOffThroughADoseTimeRingsItWhenItIsBackOn; aBootThatOverrunsItsTimeLeavesBothChainsAnAlarm; aBootWithoutDataSetsBothChainsToTryAgain; allowingExactAlarmsAgainSetsBothChainsAgainAsAlarmClocks |  |
| Clock seam and locale in stored text | Dates and time | MedReminders.kt:82, :304 | Verified | noReminderFileReadsTheWallClockDirectly. ofPattern uses standard digits in any language, so stored keys always parse | #345 |
| Medical data in logs; exported components; string-built SQL | Security | MedReminders.kt, ExactAlarms.kt | Read OK | No logging, receivers not exported, no SQL here, every PendingIntent immutable | |
| ExactAlarms.set: an alarm clock at the minute | Logic | ExactAlarms.kt:32 | Verified | eachDoseHasAnAlarmClockAtItsOwnMinute; aFollowUpAndASnoozeAreAlarmClocksAtTheirExactMinute | #346 |
| Only the helper sets an alarm | Structure | ExactAlarms.kt:18 | Verified | onlyTheExactAlarmHelperSetsAnAlarm | #346 |
| ExactAlarms.set: fallback on Android 12 or 12L | Failure | ExactAlarms.kt:65 | Risk | Up to an hour late remains accepted. withExactAlarmsRevokedAnAlarmIsStillSetAtTheRightMinute asserts only the time, so it also passes if the alarm clock branch ran and the fallback never did: it should assert nextAlarmClock is null | #346 |
| ExactAlarms.canExact, show intents | Lifecycle | ExactAlarms.kt:23, :50 | Read OK | Followed to MainActivity.handleIntent: the Medications extra returns early, and a data URI with a null action never reaches the import branch at MainActivity.kt:419 | |
| ExactAlarms.cancel | Lifecycle | ExactAlarms.kt:40 | Read OK | Same intent identity as set | |
| MedicationsPage: exact row, rearm on resume | Lifecycle | MedicationsPage.kt:42, :57 | Read OK | Row only when not allowed; resume re-reads it and rearms the medication chain. No test draws this page | #346 |
| Alarms ring on time in doze, on a real phone | Lifecycle | ExactAlarms.kt | Not checked | Needs a device; no phone, emulator or SDK here | #346 |
| Several notifications folded by Android keep their own buttons | Lifecycle | MedReminders.kt:448 | Not checked | No group or summary is set, so Android's own bundling applies. Needs a device | #327 |
| Dates.clockSource default: zone fixed at process start | Dates and time | Models.kt:535; MedReminders.kt:414; ExactAlarms.kt:50 | Defect | Clock.systemDefaultZone() keeps the zone it was created with, so after a zone change in a live process Dates.nowDateTime() stays in the old zone while ExactAlarms.epochMillis converts in the new one. East, London to Tokyo, real 21:00, stale clock 12:00, open dose 15:00: at.isAfter(now), Ring(15:00), an instant 6 hours past, fires at once, ring re-posts with sound, arm repeats: an alert loop for up to 3 hours, and the loop keeps the process alive. West, London to New York, real 07:00, stale 12:00: today's 08:00 dose counts as past and never rings. clockChanged and retryAt read the same clock. The old reminder code read LocalDateTime.now(), which was right. Fixed-clock tests can't see it. No test | #346 |
| chainLock: ring, arm, deadlock | Concurrency | MedReminders.kt:107, :406, :672 | Read OK | Not re-entrant, and nothing under it calls arm or ring: ring calls armLocked. saveDevice's writeLock never takes chainLock, so no inversion. Suspend calls under it (awaitLoaded, updateDeviceNow) are cancellable, withLock releases on cancellation, receivers time out into a retry, and ring cancels a debounced arm, the only caller with no timeout | #327 |
| planStamp: stable, and what it covers | Data safety | MedReminders.kt:207 | Verified | MedRemindersTest planStamp assertions. String.hashCode is specified and the same across processes; the DAO queries are ORDER BY; Double.toString isn't locale-bound. Covers everything dueOn reads for times (runs, pauses, phases, doses, days); dose changes alter only the amount, so they rightly stay out. No test reloads through a real restart; read only for that part | #327 |
| Test seams left in the release build | Security | MedReminders.kt:92; LogReminders.kt:105; Settings.kt:432; RestTimer.kt:214 | Read OK | All internal; no intent, setting or file can set them; the main code calls no resetForTests. Each costs a volatile read, and posted++ runs under chainLock. workTimeoutMs, settingsTimeoutMs and awaitSettings hold the production values. Harmless | #346 |
| Receivers rethrow CancellationException | Failure | MedReminders.kt:748; LogReminders.kt:308 | Improve | The receiver's own scope is never cancelled, so any such exception comes from inside the work and ends the coroutine with no retry. Treating it as a failure there keeps the chain | #346 |
