# Code audit: tests

> No full audit yet: rows from build gates only.
> Build gate 1.0.198: 17 functions checked, re-checked after fix rounds 1 and 2.
> Build gate 1.0.199: 3 test files checked in full (NavHarness, CalendarScreenTest, WorkoutsScreenTest) plus the re-recorded day-menu goldens, re-checked after fix round 1 (RoutineRestoreTest, the calendar goldens, the Undo test), on the working tree over 527d379, before CI run 41925517281 finished.
> Audited 2026-10-31 against 1.0.198 (working tree on 830f7e1, nothing committed). Files read in full: 9 of 15 in scope; the rest of the area was not read.
> Results: 0 Verified, 21 Read OK, 0 Defect, 1 Risk, 2 Improve, 3 Not checked.
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
