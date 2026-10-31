# Code audit: tests

> No full audit yet: rows from build gates only.
> Build gate 1.0.198: 15 functions checked.
> Audited 2026-10-31 against 1.0.198 (working tree on 830f7e1, nothing committed). Files read in full: 7 of 8 in scope; the rest of the area was not read.
> Results: 0 Verified, 6 Read OK, 1 Defect, 2 Risk, 3 Improve, 3 Not checked.
> No test has been run: there's no SDK here, and CI hasn't seen this working tree.

## Files

| File | Read in full |
| --- | --- |
| app/src/test/java/com/fitlens/companion/data/MedRemindersTest.kt | No (build gate: changed functions only) |
| app/src/test/java/com/fitlens/companion/data/MedReminderNotificationsTest.kt | Yes |
| app/src/test/java/com/fitlens/companion/data/ReminderHarness.kt | Yes |
| app/src/test/java/com/fitlens/companion/data/ReminderHarnessTest.kt | Yes |
| app/src/test/java/com/fitlens/companion/ui/FunctionHelpersTest.kt | Yes |
| app/src/test/java/com/fitlens/companion/ui/FunctionTest.kt | Yes |
| app/src/test/java/com/fitlens/companion/ui/LongHistoryFixture.kt | Yes |
| app/src/test/java/com/fitlens/companion/ui/RestTimerAlarmTest.kt | Yes |

## Checks

| Function, class or concern | Check | Where | Result | Evidence | Issue |
| --- | --- | --- | --- | --- | --- |
| MedReminderNotificationsTest: could each fail if the code broke? | Tests | MedReminderNotificationsTest.kt | Read OK | Real receiver, Room and buttons; asserts text, tags, records, stored snoozes, the next alarm. Every "nothing happened" check waits first (settle or a later live button) | #327 |
| MedRemindersTest additions | Tests | MedRemindersTest.kt | Read OK | Pure next, catchUp, followUps and snooze decoding, with exact values. Time is passed in | #327 |
| RestTimerAlarmTest | Tests | RestTimerAlarmTest.kt | Read OK | Compares with the state's endAt on the platform's nextAlarmClock; the receiver runs on Main and the looper is idled, so the negative checks are real | #342 |
| ReminderHarness | Tests | ReminderHarness.kt | Read OK | Delivers by sendBroadcast, so an unregistered receiver in the manifest would fail. Clock, permission and database reset per test | #333 |
| ReminderHarness.settle: quiet window | Tests | ReminderHarness.kt:208 | Risk | Returns after 600 ms unchanged, while a rearm is a 300 ms debounce on Dispatchers.Default. On a loaded CI machine that arm can land after a test has moved the clock, and the fingerprint ignores titles, so a re-post with another title doesn't count as a change. Could flake, or pass early. Wait on MedReminders' pending job | #333 |
| ReminderHarness.stop: reflection in runCatching | Structure | ReminderHarness.kt:247 | Improve | Renaming pending or app makes this silently stop isolating tests. An internal reset on MedReminders and LogReminders fails at compile time | #333 |
| LongHistoryFixture | Tests | LongHistoryFixture.kt | Read OK | Fixed clock, ISO strings compared as text, ScreenFixture unchanged in git. FunctionHelpersTest.theFixtureHoldsYearsOfThinHistoryWithTheOldExerciseLongAgo checks what it promises | #333 |
| FunctionTest, FunctionHelpersTest | Tests | FunctionTest.kt | Read OK | Seams reset after each test; a failed lookup says what's on screen. tap takes the first match by design and the KDoc says so | #333 |
| CODEMAP: how to use the harness | Structure | docs/CODEMAP.md:328 | Defect | Says "LogReminders and MedReminders still read LocalDateTime.now() for the next alarm, so assert the rule (weekday, time, key), not a fixed date". They now read the clock (#345) and these very tests assert fixed dates. A new test written from it would be weaker for nothing. It's the documentation #333 asks for | #333 |
| onlyTheExactAlarmHelperSetsAnAlarm, noReminderFileReadsTheWallClockDirectly | Tests | MedReminderNotificationsTest.kt:835, :850 | Improve | Both fail if the sources aren't found, so they can't pass empty. The first misses set(, setRepeating and setInexactRepeating; the second names two files, so a third data/*Reminders.kt escapes it. A wider pattern and a glob close that | #346 |
| Risky code with no test: receiver timeouts | Tests | MedReminders.kt:600, :626; LogReminders.kt:288 | Risk | The finished == null branches never run: the medication tests reach retry through loadOverride returning null, and the logging test calls retryLater directly. Removing any of the three branches would pass. A test seam for the timeout would drive them | #346 |
| Risky code with no test: the rest | Tests | ExactAlarms.kt:35; MedicationsPage.kt:57; RemindersPage.kt:96; MedReminders.kt:462 | Improve | Nothing drives the SecurityException fallback, draws the exact row (the #346 Settings criterion rests on reading), checks the sort key under Lock, starts a schedule after its dose times, or changes the time zone or the repeated hour. One Robolectric API 31 test and a few harness tests would | #346 |
| The tests pass in CI, in acceptable time | Build | testDebugUnitTest | Not checked | Not run: no SDK here. About 60 new tests, each with real sleeps of at least 600 ms when settling. The Actions run will show the time added | #333 |
| The Kotlin compiles | Build | All changed files | Not checked | Read for it, not compiled | |
| Tests independent of time zone | Dates and time | LongHistoryFixture.kt:65 | Not checked | The fixed clock is UTC and alarms convert through the JVM's default zone; both go through local time, so it reads right. Only run on CI's zone | #333 |
