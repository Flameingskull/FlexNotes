# Code audit: medications

> No full audit yet: rows from build gates only.
> Build gate 1.0.198: 54 functions checked, re-checked after fix rounds 1 and 2.
> Build gate 1.0.204: 7 functions checked (#334, the sheet call sites in MedicationAsNeeded, MedicationExtras, MedicationToday and ScheduleEditorScreen; working tree over 8a66dc5). CI on these files (draft PR #380) compiled and SheetGuardTest passed.
> Audited 2026-10-31 against 1.0.198 (working tree on 830f7e1, nothing committed). Files read in full: 3 of 3 in scope; the rest of the area was not read.
> Results: 41 Verified, 15 Read OK, 2 Defect, 2 Risk, 0 Improve, 2 Not checked.
> `Verified` names a test that would fail if this broke. CI run 41336801625 ran this code: every reminder, alarm and rest test passed.

## Files

| File | Read in full |
| --- | --- |
| data/MedReminders.kt | Yes |
| data/ExactAlarms.kt | Yes |
| ui/MedicationsPage.kt | Yes |
| ui/MedicationAsNeeded.kt | No (build gate: changed functions only) |
| ui/MedicationExtras.kt | No (build gate: changed functions only) |
| ui/MedicationToday.kt | No (build gate: changed functions only) |
| ui/ScheduleEditorScreen.kt | No (build gate: changed functions only) |

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
| Taken button: site fixed when posted | Logic | MedReminders.kt:629 | Verified | Fix round 2: a shown dose keeps the site it named (shownSites), the rest count the shown sites as used. Traced the three-day sequence: Thu Left, Fri Right, Sat (Thu cleared first) Left. Widget, handle and show all pass shownSites; handle cancels after the write, so its own notification still counts. CI run 41336801625 passed it | #327 |
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
| ExactAlarms.set: fallback on Android 12 or 12L | Failure | ExactAlarms.kt:65 | Risk | Lateness of up to an hour remains accepted. withExactAlarmsRevokedAnAlarmIsStillSetAtTheRightMinute now asserts nextAlarmClock is null, so the fallback branch is proved. CI run 41336801625 passed it | #346 |
| ExactAlarms.canExact, show intents | Lifecycle | ExactAlarms.kt:23, :50 | Read OK | Followed to MainActivity.handleIntent: the Medications extra returns early, and a data URI with a null action never reaches the import branch at MainActivity.kt:419 | |
| ExactAlarms.cancel | Lifecycle | ExactAlarms.kt:40 | Read OK | Same intent identity as set | |
| MedicationsPage: exact row, rearm on resume | Lifecycle | MedicationsPage.kt:42, :57 | Read OK | Row only when not allowed; resume re-reads it and rearms the medication chain. No test draws this page | #346 |
| Alarms ring on time in doze, on a real phone | Lifecycle | ExactAlarms.kt | Not checked | Needs a device; no phone, emulator or SDK here | #346 |
| Several notifications folded by Android keep their own buttons | Lifecycle | MedReminders.kt:448 | Not checked | No group or summary is set, so Android's own bundling applies. Needs a device | #327 |
| Dates.clockSource default: zone fixed at process start | Dates and time | ExactAlarms.kt:29; Models.kt:535 | Verified | Fix round 2: LiveClock reads the zone each call. afterATimeZoneChangeInARunningAppNoAlarmIsSetInThePastAndOneRingDoesNotRefire; MedRemindersTest.kt:222. CI run 41336801625 passed it. Every reader goes through Dates.todayDate, nowDateTime or clockSource.millis; nothing else in main uses systemDefaultZone | #346 |
| chainLock: ring, arm, deadlock | Concurrency | MedReminders.kt:107, :406, :672 | Read OK | Not re-entrant, and nothing under it calls arm or ring: ring calls armLocked. saveDevice's writeLock never takes chainLock, so no inversion. Suspend calls under it (awaitLoaded, updateDeviceNow) are cancellable, withLock releases on cancellation, receivers time out into a retry, and ring cancels a debounced arm, the only caller with no timeout | #327 |
| planStamp: stable, and what it covers | Data safety | MedReminders.kt:207 | Verified | MedRemindersTest planStamp assertions. String.hashCode is specified and the same across processes; the DAO queries are ORDER BY; Double.toString isn't locale-bound. Covers everything dueOn reads for times (runs, pauses, phases, doses, days); dose changes alter only the amount, so they rightly stay out. No test reloads through a real restart; read only for that part | #327 |
| Test seams left in the release build | Security | MedReminders.kt:92; LogReminders.kt:105; Settings.kt:432; RestTimer.kt:214 | Read OK | All internal; no intent, setting or file can set them; the main code calls no resetForTests. Each costs a volatile read, and posted++ runs under chainLock. workTimeoutMs, settingsTimeoutMs and awaitSettings hold the production values. Harmless | #346 |
| Receivers rethrow CancellationException | Failure | MedReminders.kt:780; LogReminders.kt:316 | Verified | aCancellationFromInsideTheWorkIsAFailureWithARetryNotTheEndOfTheChain; ReminderHarnessTest.kt:115. withTimeoutOrNull returns null on its own timeout rather than throwing, the receiver scope is never cancelled, and finish() is in finally, so nothing outlives the broadcast. CI run 41336801625 passed it | #346 |
| MedBootReceiver: failure tracked per chain | Failure | MedReminders.kt:808 | Verified | aBootWhoseMedicationStepOverrunsRetriesOnlyThatChain; aBootThatOverrunsItsTimeLeavesBothChainsAnAlarm. On a timeout the inner catches take the cancellation and set the flags, and the next suspension throws at once, so nothing hangs and each failed chain gets its retry. CI run 41336801625 passed it | #346 |
| Locked notification extras carry the injection site | Security | MedReminders.kt:570 | Risk | With Lock Medications on, the button hides the site, but the notification extras still hold EXTRA_SITE (for example abdomen_upper_left), readable by any notification listener. It names no medication, but it says the dose is an injection. Keep it while locked (shownSites needs it) and accept that, or keep the map in memory | #327 |

## Build gate 1.0.204, #334 (typed values kept on a tap outside)

| Function, class or concern | Check | Where | Result | Evidence | Issue |
| --- | --- | --- | --- | --- | --- |
| AsNeededSheet: dirty | Data safety | MedicationAsNeeded.kt:153, :159 | Read OK | On and the most a day, both saveable | #334 |
| SupplySheet: dirty | Data safety | MedicationExtras.kt:214, :236 | Read OK | On hand, refill, warning days and expiry (from PickDateDialog). Stop tracking and Save close through onDismiss | #334 |
| SupplySheet: an untouched Save restarts the count | Data safety | MedicationExtras.kt:216, :242; Models.kt:596; MedExtras.kt:249 | Defect | Already there. onHand starts as fmtNum(left), two decimals, and `recount = left == null \|\| abs(hand - left) > 1e-9 \|\| add > 0` compares that rounded text with the exact left. Trigger: a supply whose remainder has more than two decimals (30 mL, two doses of 0.333 mL logged: left 29.334); open Supply, change only the warning days, Save: recount is true, MedExtras.count stores supply 29.33 and supplyFrom now, so 0.004 is lost and the count restarts on every such Save. Expected: no recount when the on-hand field wasn't changed. Fix: `val recount = left == null \|\| onHand.trim() != left.let { fmtNum(it) } \|\| add > 0`. Test: a medication with supply 30 and two 0.333 doses, open SupplySheet, change warning days, Save; assert the stored supply is still 30 and supplyFrom unchanged. Size S | #382 |
| SymptomSheet: no dirty | Data safety | MedicationExtras.kt:467, :469, :479 | Defect | This build: #334's first criterion. Ratings save at once, but the sheet has two text fields, a new symptom's name (newName) and a rename (renameText), and passes no dirty. Trigger: Body tracker, symptoms, type `Headache` in the add field, tap above the sheet: it closes and the name is gone. Expected: it stays open with the text. Fix: pass `dirty = newName.isNotBlank() \|\| renaming != null` to the FitSheet at :479 (Done still closes it). Test: SheetGuardTest case `SiteHost { close -> SymptomSheet(snap, LongHistoryFixture.END, close) }`, type `Headache`, tapOutside, assert it still shows and `Guard test closed` doesn't. Size S | #334 |
| DoseChangeSheet: dirty | Data safety | MedicationToday.kt:411, :418 | Read OK | Amount and this day only. Reached from DueDoseSheet's early return (:341), so each Change amount starts a new baseline; its Cancel closes both sheets through the shared onDismiss, as before | #334 |
| DueDoseSheet: no typed input | Data safety | MedicationToday.kt:344 | Read OK | Taken, Skip and Change amount act at once; the only held value is the injection site before Taken (see the logging ledger's choice-only row) | #334 |
| PhaseSheet: dirty | Data safety | ScheduleEditorScreen.kt:809, :819 | Read OK | On, length, unit; both hosts (:374, :380) close on save | #334 |
| DoseSheet: dirty | Data safety | ScheduleEditorScreen.kt:879, :919 | Read OK | All twelve fields, weekdays sorted so set order can't matter. Save with a missing field shows a message and keeps the sheet; the host (:388-394) closes on a good save | #334 |
