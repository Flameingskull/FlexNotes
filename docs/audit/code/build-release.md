# Code audit: build-release

> No full audit yet: rows from build gates only.
> Build gate 1.0.198: 5 functions checked.
> Audited 2026-10-31 against 1.0.198 (working tree on 830f7e1, nothing committed). Files read in full: 0 of 2 in scope; the rest of the area was not read.
> Results: 0 Verified, 3 Read OK, 0 Defect, 1 Risk, 0 Improve, 1 Not checked.

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
