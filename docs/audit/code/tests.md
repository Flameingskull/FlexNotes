# Code audit: tests

> No full audit yet: rows from build gates only.
> Build gate 1.0.198: 17 functions checked, re-checked after fix rounds 1 and 2.
> Build gate 1.0.199: 3 test files checked in full (NavHarness, CalendarScreenTest, WorkoutsScreenTest) plus the re-recorded day-menu goldens, re-checked after fix round 1 (RoutineRestoreTest, the calendar goldens, the Undo test), on the working tree over 527d379, before CI run 41925517281 finished.
> Build gate 1.0.200: 9 functions checked (#330, which way is good; working tree over e67bae8, before CI ran the tests named).
> Build gate 1.0.200, fix rounds 1 and 2 re-checked: the findings below and the rows they touch. CI on the round-1 tree compiled and passed 739 of 741 tests; CI on the round-2 tree (c4e8a86) passed 740 of 741, including every #330 test; the one failure is the known WholeScreen320Test.librarySearch (#349).
> Build gate 1.0.201: 3 functions checked (#349, deterministic goldens; working tree over 3adada9, with CI run on 5682876 and the three re-recorded goldens looked at).
> Audited 2026-10-31 against 1.0.198 (working tree on 830f7e1, nothing committed). Files read in full: 9 of 15 in scope; the rest of the area was not read.
> Build gate 1.0.204: 9 functions checked in this ledger (#334 unsaved sheets, #331 body value sheet; working tree over 8a66dc5, plus the LayoutChecks.isTextField correction). CI on draft PR #380 (as reported to the auditor, before that correction): compiled, 800 of 804 unit tests passed, the 4 failures all in BodyEntrySheetLayoutTest. 35 functions across body, photos, calendar-shell, settings-data, build-release and tests.
> Build gate 1.0.204 (second code-auditor, the #334 call sites): SheetGuardTest read in full; one row added below, the file's other rows are the ones above. CI on draft PR #380 passed all of SheetGuardTest.
> Results: 1 Verified, 43 Read OK, 0 Defect, 7 Risk, 5 Improve, 3 Not checked.
> `Verified` names a test that would fail if this broke. CI run 41336801625 ran this code: every reminder, alarm and rest test passed.

## Files

| File | Read in full |
| --- | --- |
| app/src/test/java/com/fitlens/companion/data/MedRemindersTest.kt | No (build gate: changed functions only) |
| app/src/test/java/com/fitlens/companion/data/MedReminderNotificationsTest.kt | No (build gate: changed functions only) |
| app/src/test/java/com/fitlens/companion/data/ReminderHarness.kt | No (build gate: changed functions only) |
| app/src/test/java/com/fitlens/companion/data/ReminderHarnessTest.kt | No (build gate: changed functions only) |
| app/src/test/java/com/fitlens/companion/ui/FunctionHelpersTest.kt | Yes |
| app/src/test/java/com/fitlens/companion/ui/FunctionTest.kt | Yes |
| app/src/test/java/com/fitlens/companion/ui/LongHistoryFixture.kt | Yes |
| app/src/test/java/com/fitlens/companion/ui/RestTimerAlarmTest.kt | No (build gate: changed functions only) |
| app/src/test/java/com/fitlens/companion/data/ExactAlarmPermissionTest.kt | Yes |
| app/src/test/java/com/fitlens/companion/ui/ExactAlarmSettingsTest.kt | Yes |
| app/src/test/java/com/fitlens/companion/ui/NavHarness.kt | Yes |
| app/src/test/java/com/fitlens/companion/ui/CalendarScreenTest.kt | Yes |
| app/src/test/java/com/fitlens/companion/ui/WorkoutsScreenTest.kt | Yes |
| app/src/test/java/com/fitlens/companion/ui/RoutineRestoreTest.kt | Yes |
| app/src/test/java/com/fitlens/companion/ui/WholeScreenTest.kt | No (build gate: changed functions only) |
| app/src/test/java/com/fitlens/companion/data/MeasurementGoalsTest.kt | Yes |
| app/src/test/java/com/fitlens/companion/ui/ChartKeyModeTest.kt | Yes |
| app/src/test/java/com/fitlens/companion/ui/GoalReachTest.kt | Yes |
| app/src/test/java/com/fitlens/companion/ui/GoalSheetTest.kt | Yes |
| app/src/test/java/com/fitlens/companion/ui/MeasurementVerdictTest.kt | Yes |
| app/src/test/java/com/fitlens/companion/ui/NoGoalColourTest.kt | Yes |
| app/src/test/java/com/fitlens/companion/ui/PhotoCompareVerdictTest.kt | Yes |
| app/src/test/java/com/fitlens/companion/ui/TrendVerdictTest.kt | Yes |
| app/src/test/java/com/fitlens/companion/ui/MoveMarkerTest.kt | Yes |
| app/src/test/java/com/fitlens/companion/ui/GoalGraphTest.kt | Yes |
| app/src/test/java/com/fitlens/companion/ui/SheetGuardTest.kt | Yes |
| app/src/test/java/com/fitlens/companion/ui/BodyEntrySettingTest.kt | Yes |
| app/src/test/java/com/fitlens/companion/ui/BodyEntryRowTest.kt | Yes |
| app/src/test/java/com/fitlens/companion/ui/BodyEntrySheetTest.kt | Yes |
| app/src/test/java/com/fitlens/companion/ui/BodyEntryAddAnotherTest.kt | Yes |
| app/src/test/java/com/fitlens/companion/ui/BodyEntrySheetLayoutTest.kt | Yes |
| app/src/test/java/com/fitlens/companion/ui/LayoutChecks.kt | Yes |

## Checks

| Function, class or concern | Check | Where | Result | Evidence | Issue |
| --- | --- | --- | --- | --- | --- |
| MedReminderNotificationsTest: could each fail if the code broke? | Tests | MedReminderNotificationsTest.kt | Read OK | Real receiver, Room and buttons; asserts text, tags, records, stored snoozes, the next alarm. Every "nothing happened" check waits first (settle or a later live button) | #327 |
| MedRemindersTest additions | Tests | MedRemindersTest.kt | Read OK | Pure next, catchUp, followUps and snooze decoding, with exact values. Time is passed in | #327 |
| RestTimerAlarmTest | Tests | RestTimerAlarmTest.kt | Read OK | Compares with the state's endAt on the platform's nextAlarmClock; the receiver runs on Main and the looper is idled, so the negative checks are real | #342 |
| ReminderHarness | Tests | ReminderHarness.kt | Read OK | Delivers by sendBroadcast, so an unregistered receiver in the manifest would fail. Clock, permission and database reset per test | #333 |
| ReminderHarness.settle: quiet window | Tests | ReminderHarness.kt:208 | Read OK | Now waits for rearmIdle and the fingerprint includes titles | #333 |
| ReminderHarness.stop: reflection in runCatching | Structure | ReminderHarness.kt:259 | Read OK | Uses resetForTests, so a rename fails at compile time | #333 |
| LongHistoryFixture | Tests | LongHistoryFixture.kt | Read OK | Fixed clock, ISO strings compared as text, ScreenFixture unchanged in git. FunctionHelpersTest.theFixtureHoldsYearsOfThinHistoryWithTheOldExerciseLongAgo checks what it promises | #333 |
| FunctionTest, FunctionHelpersTest | Tests | FunctionTest.kt | Read OK | Seams reset after each test; a failed lookup says what's on screen. tap takes the first match by design and the KDoc says so | #333 |
| CODEMAP: how to use the harness | Structure | docs/CODEMAP.md | Read OK | The wrong sentence is gone | #333 |
| onlyTheExactAlarmHelperSetsAnAlarm, noReminderFileReadsTheWallClockDirectly | Tests | MedReminderNotificationsTest.kt:1264, :1283 | Read OK | Wider pattern with the two named exceptions (checked: the only other .set( in main is RepsForGraph); a glob of *Reminders.kt | #346 |
| Risky code with no test: receiver timeouts | Tests | MedReminderNotificationsTest.kt:1173, :1185; ReminderHarnessTest.kt:115 | Read OK | All three branches are now driven | #346 |
| Risky code with no test: the rest | Tests | ExactAlarmPermissionTest.kt:54 | Read OK | Fallback asserted; a real zone change tested | #346 |
| The tests pass in CI, in acceptable time | Build | testDebugUnitTest | Read OK | 618 tests in CI run 41336801625: every reminder, alarm and rest test passed. The one failure, WholeScreen320Test.librarySearch, is the known chance failure #349. The time added was not checked | #333 |
| The Kotlin compiles | Build | All changed files | Read OK | CI run 41336801625 compiled app and tests |  |
| Tests independent of time zone | Dates and time | LongHistoryFixture.kt:65 | Not checked | The fixed clock is UTC and alarms convert through the JVM's default zone; both go through local time, so it reads right. Only run on CI's zone | #333 |
| setCanScheduleExactAlarms: two call forms | Build | ReminderHarness.kt:254 | Read OK | All four calls use the static form; CI run 41336801625 passed it (the test build compiled) | #346 |
| FunctionTest leaves phone settings behind | Tests | FunctionTest.kt:96 | Read OK | closeSeams resets device settings | #333 |
| NavHarness: the same host as the app? | Tests | NavHarness.kt:20 | Improve | Same Route, KEY, attach and sync as AppRoot, so taps really navigate. But it hands ScreenContent the live snapshot, where the app hands `rememberScreenSnapshot(s)` (#60's per-screen view, MainActivity.kt:614). A screen whose read isn't tracked, so it stays stale in the app after a write, passes every harness test. Making rememberScreenSnapshot internal and calling it here closes that | #353 |
| CalendarScreenTest: could it fail if #329 broke? | Tests | CalendarScreenTest.kt:72 | Read OK | Yes for the thumbnail (counts `cal_photo` layers: the old `!isSelected` gives 1, not 2), for selected semantics on each tap, and for the second tap opening Screen.Day. Fixed clock and fixed fixture, so no time or order dependence. `onNode(cell(today))` needs exactly one clickable whose description holds the long date; CI decides that | #329 |
| #329's look: outline, background, golden | Tests | WholeScreenTest.kt:241, :243 | Read OK | Fix round 1 added `calendar` (today, a photo day, selected) and `calendarAnotherPhotoDaySelected` (taps the 20th) at every width and font, on the fixed clock. Once recorded, removing the outline or bringing back the GoldDusk fill fails them. Their first images are recorded, not compared: whether they show the right thing is the Not checked row in the calendar-shell ledger | #329 |
| WorkoutsScreenTest: could it fail if #353 broke? | Tests | WorkoutsScreenTest.kt | Read OK | Starts from the real day log and the real ⋮, writes through Routines into Room and reads back with stored(); asserts nav.top, rows, counts, the confirm sheet and stored state. Undo is invoked from the collected message, after waiting for a subscriber, so it isn't racing the flow. Redundant same-package import and unused Snapshot/Clock imports are warnings only (warningsAsErrors = false) | #353 |
| WorkoutsScreenTest: "a long name is shown in full" | Tests | WorkoutsScreenTest.kt:177 | Improve | assertShows reads semantics text, which holds the whole string even when a Text is cut to one line with an ellipsis, so this can't fail if the name were truncated. Asserting the node's height spans more than one line, or a golden, would | #353 |
| WorkoutsScreenTest: Undo checks too little | Tests | WorkoutsScreenTest.kt:121 | Read OK | Fix round 1: asserts the whole list's ids in order and the restored workout's day ids, so the old copy-based Undo fails it | #353 |
| Time and order independence of the new tests | Dates and time | CalendarScreenTest.kt:74, WorkoutsScreenTest.kt:145 | Read OK | Every open passes a fixed clock (ScreenFixture.clock or LongHistoryFixture.clock); FunctionTest deletes the database and resets seams around each test | #353 |
| Re-recorded day-menu goldens | Tests | app/src/test/screenshots/screen_day_menu*.png | Not checked | CI re-records the eight from the new code, so they record whatever it draws. Someone has to look at them once: the menu gains a row, and at 320dp with font ×2 it may scroll | #353 |
| The new files compile and the tests pass | Build | WorkoutsScreen.kt, the three test files | Not checked | CI run 41925517281 was still running when this was written | #353 |
| Restore of exercises, planned sets and training blocks: no test | Tests | RoutineRestoreTest.kt:23, WorkoutsScreenTest.kt:98 | Risk | Both tests restore workouts with days but no exercises, planned sets, notes or blocks, so `restore` writing those wrongly (its blocks encoding is a second copy of `save`'s) passes them. Seed a day with two exercises, planned sets and a training plan, delete, restore, and assert the stored routine equals the one before, field for field | #353 |
| MeasurementGoalsTest, MeasurementVerdictTest, TrendVerdictTest | Tests | the three files | Read OK | Pure functions with exact expected values, both signs, equal values and plain; no time, order or platform dependence | #330 |
| PhotoCompareVerdictTest | Tests | PhotoCompareVerdictTest.kt | Read OK | Builds a real BodyPart and Snapshot (internal constructors, same module) and runs compareRows; would fail on the old isImprovement-only code for the two no-direction cases. assertTrue(x == true) gives a poor failure message | #330 |
| ChartKeyModeTest: down is good | Tests | ChartKeyModeTest.kt:68-82 | Read OK | Fix round 1: asserts the spoken words "Better: lower" / "Worse: higher" and their absence for Up, which come from the same flag as the triangles, so it now fails if the direction is wrong | #330 |
| ChartKeyModeTest: no colours keeps Best | Tests | ChartKeyModeTest.kt:31-38, :58-64 | Read OK | Now asserts no Best in NONE mode, and bestPoint's result for higher, lower, none and plain | #330 |
| GoalSheetTest | Tests | GoalSheetTest.kt | Read OK | Drives the real sheet and Settings flow: Cancel after Plain leaves the choice alone, Save writes it, answer order, the TARGET-0 text, goalText matching the Track line. pickingPlainThenSaveSetsIt's background save is now joined before teardown (row below) | #330 |
| GoalReachTest | Tests | GoalReachTest.kt | Read OK | Saves through the sheet into Room and reads back with stored(); storedGoal polls 100 x 50 ms, bounded and enough for one write. The History, Graph and Medication cases only assert the line is there | #330 |
| NoGoalColourTest | Tests | NoGoalColourTest.kt:46-67; FunctionTest.kt:97 | Read OK | Round 1's CI failure (theChangeColourFollowsTheSettingAtOnce: "attempt to re-open an already-closed SQLiteDatabase", a background save and Store.refresh outliving the test) is fixed two ways in round 2: that test now uses Settings.showWith, which writes nothing, and FunctionTest.closeSeams runs Settings.awaitWrites before Store.db.close, so choosingPlainInSettingsSavesIt and GoalSheetTest.pickingPlainThenSaveSetsIt (both through updatePortable on the settings scope) finish their writes first. The test still re-reads the colours after recomposition, so it can fail | #330 |
| Risky code with no test | Tests | BodyScreen.kt:519-521, :629-660; Charts.kt:866-880 | Read OK | Fix round 1: GoalGraphTest draws BodyGraphPane with each answer and asserts the key's words, Best and the Change verdict. Left undriven: the colour of a point on the canvas (moveTriangleColour is unit-tested) | #330 |
| The new tests compile and pass | Build | the ten files | Read OK | CI on the round-2 tree (c4e8a86) compiled them and passed 740 of 741: NoGoalColourTest, GoalSheetTest, MoveMarkerTest, GoalGraphTest, ChartKeyModeTest and the rest of #330's tests all passed. The one failure is the known flaky WholeScreen320Test.librarySearch golden (#349), being re-run | #330 |
| MoveMarkerTest | Tests | MoveMarkerTest.kt | Read OK | Pure: good, bad, no verdict both ways (round 2: with the real ChartColors, asserting neither green, red nor the series colour), first and unchanged points | #330 |
| GoalGraphTest | Tests | GoalGraphTest.kt | Read OK | Real pane on a fixture with only Bodyweight's goal replaced; waits for the key (5 s, bounded); asserts the words and the state description, both present and absent. Fixed clock and preferences from openScreen | #330 |
| GoalReachTest: a store write on AppScope outlives the test | Tests | GoalReachTest.kt:145-166; MeasurementGoals.kt:159; Store.kt:932-941 | Risk | The sheet saves with AppScope.scope.launch, not the settings scope, so awaitWrites doesn't join it. storedGoal returns once the row is written, but the same job then runs refresh(Area.BODY), which rebuilds the snapshot from the database and calls MedReminders.rearm. If teardown closes the database first, the same "already-closed" error can fail theTrackTabGoalLineOpensTheSheetAndDownIsGoodMakesAFallGood. It passed on CI. Waiting for stored() to show the goal and then for Store.snapshot to carry it (or joining AppScope's children in closeSeams too) closes it. Siblings: any FunctionTest that saves through AppScope | #373 |
| WholeScreenChecks.settle | Tests | WholeScreenTest.kt:404-419 | Risk | No tolerance changed (changeThreshold stays 0.001). It waits for other threads with Thread.sleep(100) three times, so it is still timed: a store or settings write slower than that on a busy runner can still land after the shot, the failure #349 describes. It also adds about 0.3 s of real time to each of some 90 shots per width. Waiting on the work itself (Settings.awaitWrites, the Store's refresh) instead of sleeping would make it deterministic. If advanceTimeBy throws, autoAdvance stays off for the rest of the test (try/finally) | #375 |
| WholeScreenChecks.typeText: replacement, not typing | Tests | WholeScreenTest.kt:335-344 | Read OK | SetText calls the field's onValueChange as typing does, without focus, so no blinking cursor. The recorded screen_lib_search_320dp shows the query and its two results under their categories (looked at). It differs from a phone mid-typing only in the missing cursor. performTextInput is now an unused import: a warning only | #349 |
| The three re-recorded goldens | Tests | screen_day_time_sheet_411dp_font2, screen_lib_category_411dp, top_bar_411dp_font2 | Read OK | Compared old and new by eye. Two: only the title, now full size and still fitting. day_time_sheet: the only change is a highlight circle on the top bar's ⋮, which the sheet was opened from. It is a state that holds after settle, so the old golden caught the screen before it landed (the #349 fault) and the new one is the settled screen, not a regression from this build's code. Whether a phone shows that highlight behind the sheet (Robolectric input mode may make the click take focus) needs a device | #349 |

## Build gate 1.0.199, #328 test files

Written by the main session from the code-auditor's report. Read in full: GraphRangeTest, GraphRangeEmptyTest, BodyGraphRangeTest, ExerciseGraphRangeTest, AnalysisRangeTest, AnalysisPanelTest, ReadinessRangeTest, ShareGraphAxisTest, BarWidthTest, GraphRangeIndexTest. Rows: 9 Read OK, 2 Risk, 1 Improve.

| Function, class or concern | Check | Where | Result | Evidence | Issue |
| --- | --- | --- | --- | --- | --- |
| GraphRangeEmptyTest compiles | Build | GraphRangeEmptyTest.kt:6 | Read OK | The wrong import is removed | #328 |
| theOldDefinitionOfTheLatestValueNoLongerDecides | Tests | BodyGraphRangeTest.kt | Read OK | Picks 1 month and would fail on the old latest-anchored code | #328 |
| BarWidthTest float deltas | Build | BarWidthTest.kt:19, :25, :32 | Read OK | assertEquals(Float, Float, Float) | #328 |
| ReadinessRangeTest drives the graphs | Tests | ReadinessRangeTest.kt | Risk | weekCountFor is proven; the graphs' period and the empty panel aren't driven | #361 |
| A screen test of the axis span | Tests | (none) | Risk | The date labels are drawn on the canvas only | #361 |
| theTrendRangeAndThePlotRangeAreTheSame | Tests | ChartViewportTest.kt:84 | Improve | Calls the same function twice with the same input | #361 |
| GraphRangeTest, AnalysisRangeTest, ExerciseGraphRangeTest, ShareGraphAxisTest, AnalysisPanelTest, GraphRangeIndexTest | Tests | | Read OK | Fixed dates and the clock seam reset after each test; AnalysisPanelTest has only the negative case | #328 |
| LayoutChecks.isTextField: read-only fields | Tests | LayoutChecks.kt:193-198; TimeField.kt:47-50; BodyEntrySheetLayoutTest.kt:61-71 | Read OK | Build gate 1.0.204, corrected in this build. CI reported all 4 BodyEntrySheetLayoutTest cases failing with "(unlabelled)" overlaps "Time". Traced: TimeField's read-only OutlinedTextField is the first one any whole-screen check lays out (WholeScreenTest opens no body screen); its node has EditableText and a text layout but no SetText action and no Text, so the old isTextField was false and the exemption at :103 for a field's own floating label didn't apply, at every height and font. The correction also accepts EditableText, which only text fields carry, so it widens the exemption to read-only fields and nothing else. A green run of BodyEntrySheetLayoutTest confirms it. label() still prints "(unlabelled)" for any field, which made the failure hard to read | #331 |
| No test that LayoutChecks still catches text over a read-only field | Tests | LayoutChecks.kt:103-105 | Improve | Build gate 1.0.204, this build. The exemption excuses any text inside a field's width, and now for read-only fields too; nothing checks that a Text drawn over a TimeField's value is still reported. Add two PhoneSizeTest-style cases: a labelled readOnly OutlinedTextField reports nothing, and a Text placed over it is reported. Also let label() fall back to EditableText so a field's line names it | #331 |
| SheetGuardTest | Tests | SheetGuardTest.kt:81-166 | Verified | Build gate 1.0.204. Each screen case fails if the guard broke: it asserts the scrim exists, clicks it, then asserts the title and text and that the closed marker is absent; the untouched cases fail if dirty started true. Real call sites: WorkoutCommentSheet, SetCommentSheet, ReferenceRangeSheet | #334 |
| #334 criteria not driven: Back and a drag | Tests | SheetGuardTest.kt:62-65 | Risk | Build gate 1.0.204, this build. The class KDoc says Back is not driven; nothing drags the sheet either, and the criterion names both. A change in which path Back takes would pass. Add: type, Espresso.pressBack(), assert open; performTouchInput { swipeDown() } on the title, assert open; and both closing an untouched sheet | #334 |
| #331 criteria with no test | Tests | BodyEntrySheetTest.kt | Risk | Build gate 1.0.204, this build. No test drags or flicks up, drags down from full screen, turns the phone with a value typed at either height, or chooses a time or a site. theButtonGoesUpAndBackDown and opensFullScreenWhenTheSettingIsOn assert only which label the arrow shows, so a sheet that swapped the label but never grew passes. Add: the sheet's height against the window after each toggle (full at least 90% of it, half less); swipeUp on the title; StateRestorationTester with 61.7 typed at full height, asserting the value and the collapse label | #331 |
| BodyEntryAddAnotherTest | Tests | BodyEntryAddAnotherTest.kt | Read OK | Build gate 1.0.204. Asserts Room (stored() reads the database, which starts empty, so 61.7 can only be the saved one), the cleared field and the value behind | #331 |
| BodyEntrySettingTest and BodyEntryRowTest | Tests | BodyEntrySettingTest.kt; BodyEntryRowTest.kt | Read OK | Build gate 1.0.204. Assert the preference after a reload from the database and after each tap | #331 |
| BodyEntrySheetTest: choosing a measurement or the date | Tests | BodyEntrySheetTest.kt:89-105 | Read OK | Build gate 1.0.204. A chip and the date picker at each height, asserting onDismiss never ran | #331 |
| BodyEntrySheetLayoutTest reads the wall clock | Tests | BodyEntrySheetLayoutTest.kt:32; TimeField.kt:44 | Read OK | Build gate 1.0.204. It sets Dates.clockSource, but the time shown comes from LocalTime.now(), so the text differs per run; HH:mm keeps its width, so the layout check isn't affected | #331 |

## Build gate 1.0.204, #334 test file (code-auditor, sheet call sites)

| Function, class or concern | Check | Where | Result | Evidence | Issue |
| --- | --- | --- | --- | --- | --- |
| tapOutside picks any node described as close | Tests | SheetGuardTest.kt:68-73 | Improve | This build. It clicks the first node whose description contains `close` in any case, so a sheet with a Close icon would be clicked instead of the scrim, and a click landing on the sheet passes every keeps-text test. SetCommentSheet has no untouched counterpart to rule that out. Also, the tap cases see only FitSheet's onDismissRequest guard: with the confirmValueChange guard gone they still pass, since onDismiss never runs while the sheet slides away, so the drag test in the row above should assert the Save button is still displayed, not only that the closed marker is absent. Fix: match the exact scrim label (`hasContentDescription("Close sheet")` and hasClickAction()), and add `theSetCommentClosesOnATapOutsideWhenNothingWasTyped`. Size S | #334 |
