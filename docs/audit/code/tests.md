# Code audit: tests

> No full audit yet: rows from build gates only.
> Build gate 1.0.198: 17 functions checked, re-checked after fix round 1.
> Audited 2026-10-31 against 1.0.198 (working tree on 830f7e1, nothing committed). Files read in full: 5 of 10 in scope; the rest of the area was not read.
> Results: 0 Verified, 11 Read OK, 1 Defect, 0 Risk, 2 Improve, 3 Not checked.
> No test has been run: there's no SDK here, and CI hasn't seen this working tree.

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
| Risky code with no test: the rest | Tests | ExactAlarmPermissionTest.kt:46 | Improve | The exact row, sort key, late schedule and repeated hour now have tests. Left: the fallback test can pass without running the fallback (assert nextAlarmClock is null). A real zone change has no test | #346 |
| The tests pass in CI, in acceptable time | Build | testDebugUnitTest | Not checked | Not run: no SDK here. About 60 new tests, each with real sleeps of at least 600 ms when settling. The Actions run will show the time added | #333 |
| The Kotlin compiles | Build | All changed files | Not checked | Read for it, not compiled | |
| Tests independent of time zone | Dates and time | LongHistoryFixture.kt:65 | Not checked | The fixed clock is UTC and alarms convert through the JVM's default zone; both go through local time, so it reads right. Only run on CI's zone | #333 |
| setCanScheduleExactAlarms: two call forms | Build | ReminderHarness.kt:254; ExactAlarmPermissionTest.kt:41; ExactAlarmSettingsTest.kt:31, :74 | Defect | The harness calls it on the class, the new tests on an instance. A Java method is one or the other, and Kotlin rejects a static called through an instance, so one of the forms can't compile and the test build fails. From memory it's static, making the instance calls wrong; no Robolectric jar here to confirm | #346 |
| FunctionTest leaves phone settings behind | Tests | ExactAlarmSettingsTest.kt:47, :90 | Improve | logReminders stays in the DataStore for later tests in the same sandbox, as closeSeams doesn't reset device settings. No screen test reads it today, so nothing breaks yet. Reset them in closeSeams | #333 |
