# Code audit: tests

> No full audit yet: rows from build gates only.
> Build gate 1.0.198: 17 functions checked, re-checked after fix rounds 1 and 2.
> Audited 2026-10-31 against 1.0.198 (working tree on 830f7e1, nothing committed). Files read in full: 5 of 10 in scope; the rest of the area was not read.
> Results: 0 Verified, 16 Read OK, 0 Defect, 0 Risk, 0 Improve, 1 Not checked.
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
