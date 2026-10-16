# Day photos, in-app camera and Health Connect: plan across builds (2026-10-16)

> **Status (2026-10-16): In progress.** Step 0 done: issues #182–#190 filed and imported; tracker plan item "Day
> photos, in-app camera and Health Connect: plan across builds (2026-10-16)". Filed under rule 5, not in this plan:
> #191 (write to Health Connect), #192 (full history). Build A done in 1.0.141 (#182, #183, with #180 from outside the plan; its first run, 1.0.140, failed CI and published nothing).
> Build B done in 1.0.142 (#184, #185, #186: Settings › Health Connect, steps and food from Health Connect).
> Left: Build C (#187, #188, #189), Build D (#190).
> Next command: `/safe-build` for Build C.
>
> Issue map: A1 #182 · A2 #183 · B1 #184 · B2 #185 · B3 #186 · C1 #187 · C2 #188 · C3 #189 · D1 #190.

## Context

The owner asked for three things (2026-10-16):

1. **Add photos to this day** is buried in the day log's ⋮ menu (`DayScreen.kt:197`). The day's photo strip
   (`DayScreen.kt:349`) only appears once the day already has photos, so the menu is the only way to add the first one.
2. **Take photos in the app** as another way to import, with the camera permission requested, a link to the phone's
   Settings if it's refused, and the shots saved as photo records like any other import.
3. **Google Health Connect**, so FlexNotes can use data other apps record: steps, food and macros (MacrosFirst writes
   nutrition to Health Connect in real time), specialised fitness apps' sessions and metrics. Each one is an
   **optional primary source** for what FlexNotes already records and calculates (Steps, the calorie calculator,
   the measured TDEE).

Owner decisions (2026-10-16):

- **Photos entry:** a camera icon in the day log's top bar, next to Calendar and +. It opens a small menu: **Take
  photo** / **Choose from phone**. The entry leaves the ⋮ menu.
- **Camera:** FlexNotes's own camera (CameraX), not the phone's camera app. It can show a faint outline of the last
  photo in the same pose. Shots stay in FlexNotes only (not the gallery) and go into backups like every photo.
- **Health Connect reads:** steps; calories, protein, fat and carbs eaten; bodyweight and body fat; active calories
  burned; exercise sessions; resting heart rate and sleep. **Read only.** Writing to Health Connect can come later
  (log under rule 5).
- **Source per type:** for each type, Settings chooses FlexNotes or Health Connect as the primary source. Health
  Connect values are saved as body values marked `health_connect`, one total a day (readings like weight keep their
  time), refreshed on each sync. Typed values are never overwritten, and values already recorded stay if the source
  changes.
- Health Connect runs on the phone, so this keeps to **local only**: no internet permission, accounts or cloud.
  CLAUDE.md's product direction and the `Steps.kt` header ("no Health Connect") are updated to record this decision.

## Step 0: issues, plan file and tracker

- Save this plan as `docs/plans/2026-10-16-photos-camera-health-connect.md`, leaving a pointer here.
- Have `feature-manager` file one issue per item below (`enhancement`, area labels, `Depends on #N` lines), import
  each one into the tracker, and create the `plan` tracker item with them as `dependsOn` (ids `import_...`).
  - A1 (#182) Day log: Add photos in the top bar (Take photo / Choose from phone)
  - A2 (#183) In-app camera with pose outline
  - B1 (#184) Health Connect connection: availability, permissions, Settings page, sync engine
  - B2 (#185) Steps from Health Connect (depends on B1)
  - B3 (#186) Food and macros from Health Connect for the calorie calculator and measured TDEE (depends on B1)
  - C1 (#187) Bodyweight and body fat from Health Connect (B1)
  - C2 (#188) Active calories burned as the daily activity source (B1)
  - C3 (#189) Resting heart rate and sleep as body values (B1)
  - D1 (#190) Exercise sessions from Health Connect as cardio sets (B1)
  - Rule 5 (not in this plan): write FlexNotes workouts and bodyweight to Health Connect.

## Build A: photos on the day log and the in-app camera (A1, A2)

**A1, top bar** (`ui/DayScreen.kt`)
- Add a `TopBarAction` with a camera icon between Calendar and +. Only `material-icons-core` is bundled, which has no
  camera icon, so add a vector drawable (or a `FitIcons` entry, `ui/design/`) in Brand gold.
- Tapping it opens a small dropdown in the existing `MenuAction` style: **Take photo** (hidden when the phone has
  no camera, `PackageManager.FEATURE_CAMERA_ANY`) and **Choose from phone** (the existing `importForDay`,
  `rememberPhotoImporter(forcedDate = date)` in `ui/ImportActions.kt:145`).
- Remove `MenuAction(day_add_photos)` from the overflow. The strip's Add tile (`PhotoStrip`, `DayScreen.kt:477`)
  opens the same chooser. Add **Take photo** to the Photos screen's + as well (`ui/PhotosScreen.kt`).

**A2, camera** (new `ui/CameraScreen.kt`, `Screen.Camera(date)` in the nav)
- Dependencies: `androidx.camera:camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view` (one version).
  Manifest: `CAMERA` permission and `<uses-feature android:name="android.hardware.camera.any" android:required="false"/>`.
- Permission: a `RequestPermission` launcher, the same pattern as `StepsRows` (`ui/ProfilePage.kt:148`). If it's
  refused, the screen says why and offers **Open settings** through the existing `openAppSettings`
  (`ui/ProfilePage.kt:133`). It rechecks on resume (`LifecycleResumeEffect`), so returning from Settings goes
  straight to the viewfinder.
- Viewfinder: `PreviewView` in an `AndroidView`, plus the controls:
  - a pose chip row (`Poses.all`, `data/Models.kt:279`), chosen before the shot;
  - an outline toggle that shows the last photo of that pose at about 30% alpha over the preview, so shots line up;
  - a front/back lens switch;
  - a self-timer (off / 3 s / 10 s) for full-body shots;
  - a shutter button.
  Black and gold `Brand` colours, Manrope roles only.
- Capture: `ImageCapture` writes to a file in the app's cache. A review step offers Retake or Use photo. Use photo
  passes the file's `Uri` to the existing import path (`PhotoImports.request(PendingPhotoImport(listOf(uri), date,
  ...))` → `PhotoImporter.importUris`, `data/PhotoImporter.kt:73`) with the date forced and the pose already chosen.
  If `PendingPhotoImport` can't carry a pose, add that field instead of a second import path. The cache file is
  deleted after the import copies it. Nothing goes into MediaStore.
- No database change. Strings in `res/values/strings.xml`. Code map and README updated.

## Build B: Health Connect foundation, steps and food (B1, B2, B3)

**B1, connection** (new `data/HealthConnect.kt`, new `ui/HealthConnectPage.kt` under Settings)
- Dependency: `androidx.health.connect:connect-client` (latest stable). Manifest:
  - `<queries><package android:name="com.google.android.apps.healthdata"/></queries>`;
  - the read permissions for the types in this plan (`android.permission.health.READ_STEPS`, `READ_NUTRITION`,
    `READ_WEIGHT`, `READ_BODY_FAT`, `READ_ACTIVE_CALORIES_BURNED`, `READ_EXERCISE`, `READ_DISTANCE`,
    `READ_RESTING_HEART_RATE`, `READ_SLEEP`) plus `READ_HEALTH_DATA_IN_BACKGROUND`;
  - the rationale intent `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE`, and an `activity-alias` for
    `android.intent.action.VIEW_PERMISSION_USAGE` with category `android.intent.category.HEALTH_PERMISSIONS`. Both
    open an in-app privacy page (`ui/AboutPages.kt`) saying the data is read on the phone, never sent anywhere, and
    used only for the listed values. Without these, Health Connect won't show the permission dialog.
  - No `INTERNET` permission.
- `HealthConnect.status(ctx)`: `HealthConnectClient.getSdkStatus`. If Health Connect isn't installed (Android 13 and
  earlier), the page offers its Play Store link. On Android 14 and later it's built in.
- Settings page (Settings home → **Health Connect**): a Connect row, one row per type with a source choice
  (FlexNotes / Health Connect), the permission state of each, last sync time, and Sync now. Asking uses
  `PermissionController.createRequestPermissionResultContract()` for the types switched to Health Connect. A refusal
  shows a note and opens Health Connect's own settings.
- Settings: new portable fields such as `hcSteps`, `hcIntake`, … (FlexNotes by default), following the
  `activityFromSteps` pattern (`data/Settings.kt:145`, `377`, `579`), and device fields for sync bookmarks (like
  `stepsLast`, `Settings.kt:63`).
- Sync (`HealthConnect.sync`):
  - runs when FlexNotes comes to the screen (beside `Steps.onAppVisible`), from Sync now, and from an hourly
    `PeriodicWorkRequest` when background reading is allowed (pattern: `Steps.schedule` / `StepWorker`). It's
    serialised with a `Mutex`.
  - It reads from the last sync back to the start of that day. The first sync backfills 30 days, the default
    history Health Connect allows. `READ_HEALTH_DATA_HISTORY` for longer goes under rule 5.
  - Daily totals use `aggregateGroupByPeriod` (by local day). Health Connect then applies the user's app priority,
    so a watch and a phone aren't counted twice.
- Store (`data/Store.kt`): a new `replaceHealthConnectDays(name, unit, byDate)` that, in one transaction,
  `ensure`s the measurement, deletes that measurement's `health_connect` rows on the synced dates, and inserts one
  row a day. Values are converted to the measurement's stored unit with `MeasureUnits.convert`, as
  `addManualRecord` does (the "imports convert to user units" rule). It refreshes only when a snapshot exists, as
  `addSteps` does (`Store.kt:698`). A `HealthConnect.SOURCE = "health_connect"` constant goes beside
  `Steps.SOURCE`. No schema change, so `Db.VERSION` stays 22.
- Editing a Health Connect value in place (#166's rule that every source can be edited) turns it into a `manual`
  value, so the next sync no longer replaces it.
- **Primary source rule:** a helper, `Sources.dayValues(records, name, date, hcPrimary)`. When Health Connect is the
  primary source and the day has a `health_connect` value, that value is used. Otherwise the FlexNotes values are
  used (typed, phone sensor, FitNotes). Both stay visible in the Body tracker, each marked with its source.

**B2, steps** (`data/Steps.kt`, `ui/ProfilePage.kt` `StepsRows`)
- The steps source becomes a choice: phone step counter / Health Connect / off. With Health Connect chosen, the
  phone counter is turned off (`Steps.turnOff`), and synced daily totals go into **Steps** through B1.
- `Steps.on` and `Steps.average` (`Steps.kt:210`, `224`) use the primary source rule, so the energy estimate
  (`data/EnergyEstimates.kt:159`) and #181's walk/run deduction work unchanged. `activityFromSteps` keeps meaning
  "use steps in the estimate".

**B3, food and macros** (`data/Intake.kt`, `ui/CaloriesCard.kt`, `ui/TdeeScreen.kt`)
- Synced `NutritionRecord` aggregates (energy, protein, total fat, total carbohydrate) go into the four
  `Intake.Kind` body values. Energy is read in kcal.
- Every place that sums a day's intake by name uses the primary source rule. When Health Connect is primary, the
  calories card and sheet show "From Health Connect" with the source app's name where Health Connect gives it
  (`dataOrigin`). Typing intake on that day still works and is kept, but isn't counted while a Health Connect
  value exists for the day. The sheet says so.
- The measured TDEE (`Intake` window, `MIN_INTAKE_DAYS`) then works from MacrosFirst's logs without typing anything.

## Build C: more body values (C1, C2, C3)

- **C1** `WeightRecord` and `BodyFatRecord` as individual readings with their time, written to the existing
  bodyweight and body fat measurements (convert kg to the user's unit). The replace-by-date rule is applied per
  reading time.
- **C2** `ActiveCaloriesBurnedRecord` daily totals as an **Active calories** body value. In the energy estimate
  (`EnergyEstimates.kt`) they become a third activity source beside the activity level and steps, so the walk/run
  overlap (#181) and logged workouts aren't counted twice. The rule for this goes in the issue.
- **C3** `RestingHeartRateRecord` (daily average, bpm) and `SleepSessionRecord` (hours a night, dated by the wake-up
  day) as **Resting heart rate** and **Sleep** body values: graphable, no calculations.

## Build D: exercise sessions (D1)

- `ExerciseSessionRecord` and its `DistanceRecord` become cardio sets (duration, distance) on the day log, under a
  FlexNotes exercise mapped from the session type (running, cycling, swimming, walking, rowing, and so on). Exercises
  are matched by name, and a missing one is created in the library like the starter exercises
  (`data/StarterLibrary.kt`). The session's title goes in the exercise comment.
- Re-syncs must not duplicate a session, so each set stores the Health Connect record id. That's a **database
  change**: `Db.VERSION` 23, an `onUpgrade` that adds a nullable `external_id` column and keeps every row, and the
  Room/exported schema committed (as in 1.0.131). Backups carry the column.
- A session deleted in Health Connect stays in FlexNotes once it's been imported (imports merge, never wipe).

## Cross-cutting

- Rule 5: file "Write FlexNotes workouts and bodyweight to Health Connect" and "Read full Health Connect history"
  as `enhancement` / `needs-triage`.
- Each build updates `docs/CODEMAP.md` and `RELEASE_NOTES.md`, and the plan Status and tracker progress. Build A or B
  lands on or after 1.0.141, so its README refresh is due then (rule 7).
- Never touch `applicationId`, signing or `BUILD_OFFSET`.

## Verification

- CI is the compiler. Read `errors.txt` on `ci-logs` after each push.
- Unit tests (JVM, beside `StepsTest` and `IntakeTest`):
  - the primary source rule;
  - `replaceHealthConnectDays`: replaces only `health_connect` rows on the synced dates, keeps manual and FitNotes
    rows, converts units;
  - the day bucketing of Health Connect aggregates across midnight and the time zone;
  - D1's migration test from 22 to 23 with existing rows (#40's migration tests).
- Screenshot goldens for the top-bar camera icon and the Health Connect Settings page.
- Owner's phone test:
  - (A) day log camera icon → Take photo → refuse permission → Open settings → allow → back in the viewfinder →
    shoot with the outline → the photo is on that day with its pose, and isn't in the gallery. Choose from phone
    still works on a past date.
  - (B) install MacrosFirst, log food, connect FlexNotes, choose Health Connect for food and steps → the calories
    card shows MacrosFirst's totals, Steps fills from Health Connect, typed values are kept, and switching back to
    FlexNotes restores the typed figures.
  - (C/D) a scale or watch app's weight, active calories and a run appear once, and not again after a second sync.
