# Code audit: build-release

> No full audit yet: rows from build gates only.
> Build gate 1.0.198: 6 functions checked, re-checked after fix rounds 1 and 2.
> Audited 2026-10-31 against 1.0.198 (working tree on 830f7e1, nothing committed). Files read in full: 0 of 2 in scope; the rest of the area was not read.
> Build gate 1.0.204: 1 functions checked in this ledger (#334 unsaved sheets, #331 body value sheet; working tree over 8a66dc5, plus the LayoutChecks.isTextField correction). CI on draft PR #380 (as reported to the auditor, before that correction): compiled, 800 of 804 unit tests passed, the 4 failures all in BodyEntrySheetLayoutTest. 35 functions across body, photos, calendar-shell, settings-data, build-release and tests.
> Build gate 1.0.205: 3 functions checked in this ledger (#332 strings; working tree over 1310900, before CI ran).
> Results: 0 Verified, 6 Read OK, 1 Defect, 1 Risk, 1 Improve, 1 Not checked.

## Files

| File | Read in full |
| --- | --- |
| app/src/main/AndroidManifest.xml | No (build gate: changed functions only) |
| app/src/main/res/values/strings.xml | No (build gate: changed functions only) |

## Checks

| Function, class or concern | Check | Where | Result | Evidence | Issue |
| --- | --- | --- | --- | --- | --- |
| Exact-alarm permissions | Lifecycle | AndroidManifest.xml:59 | Read OK | USE_EXACT_ALARM from Android 13 (targetSdk 36, so honoured); SCHEDULE_EXACT_ALARM capped at SDK 32 for 12 and 12L. Nothing on 10 or 11 needs one. minSdk 29 | #346 |
| USE_EXACT_ALARM if the app ever goes on Google Play | Build | AndroidManifest.xml:59 | Risk | Play limits this permission to alarm and calendar apps. Not a problem for APKs today, but a store listing could be refused, and on Play SCHEDULE_EXACT_ALARM isn't granted by default from Android 14. Worth knowing before any store plan | #346 |
| RestAlarmReceiver in the manifest | Security | AndroidManifest.xml:153 | Read OK | Not exported, no filter. RestTimerAlarmTest would fail if it were missing | #342 |
| Strings removed and changed | Project rules | strings.xml:1979 | Read OK | mr_exact_allowed and mr_taken_sites have no references left in app/src; only values/ exists; no golden covers these pages. "Ten minutes" appears nowhere in app/src/main. No BUILD_OFFSET, applicationId, signing or Db.VERSION change in this diff | #346 |
| Merged manifest | Build | Build output | Not checked | A library could declare the exact-alarm permission without the cap. Needs a build | #346 |
| Exact-alarm permission-state action | Lifecycle | AndroidManifest.xml:181 | Read OK | Same string as AlarmManager's constant; on the existing boot receiver, which system broadcasts already reach | #346 |
| 1.0.204 strings | Project rules | strings.xml:202-204, :1619, :2774-2775 | Read OK | Build gate 1.0.204. Five new names, each used in code (grep), nothing to escape, one values folder | #331 |
| lr_quiet_note: promises a choice with no control | Project rules | strings.xml:2078; RemindersPage.kt:112 | Defect | Build gate 1.0.205, gate finding F2: "you choose in its schedule", but no screen sets LogReminder.quiet in this build; put back the 1.0.204 wording until the schedule sheet ships | #332 |
| 1.0.205 schedule strings with no code using them | Structure | strings.xml:2090-2123 | Improve | Build gate 1.0.205, gate finding F9: 28 names (lr_schedule through the lr_sum_weeks plural) have no R.string or R.plurals reference in app/src (grep); lr_next_none says "the coming year" while nextFor scans 400 days | #332 |
| lr_measure_named and the plurals | Project rules | strings.xml:2084, :2116-2123 | Read OK | Build gate 1.0.205. Apostrophes escaped; positional %1$d / %2$s, the "one" items leave %1$d out, which getQuantityString allows; one values folder. No BUILD_OFFSET, applicationId, signing, Gradle or Db.VERSION change in this build | #332 |
