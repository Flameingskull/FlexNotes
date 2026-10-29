# bug-manager notes

What earlier runs learned that isn't obvious from the code or `docs/CODEMAP.md`. One dated line each; delete lines
that stop being true. The repository is public: no personal data, secrets or `FIL.n` keys.

- 2026-09-25: No local Android SDK. CI is the compiler, so check imports, named arguments and `@OptIn`s by hand.
  1.0.17 failed on a missing experimental opt-in for the Material 3 top bar.
- 2026-09-25: Every workout write goes through `Workouts.write`, which reloads the whole `Snapshot`. Don't reload again
  from the UI.
- 2026-09-25: 1RM and PR logic lives only in `data/Records.kt` (#23). 1.0.19 and 1.0.20 shipped everything except
  the PR celebration, which shipped in 1.0.21 (#23 closed).
- 2026-09-25: PR rule: a set is a PR when it's strictly heavier than every earlier set of at least as many reps
  (ties aren't PRs). New sets get it on save (`Workouts.addSet`); `Workouts.recalculatePrs` replays history.
- 2026-09-25: Settings (1.0.21, #38): never call `Db.getMeta` / `setMeta` for a setting; use `data/Settings.kt`.
  The phone-only `meta` rows left for downgrades are cleaned up by #98. The importer's `weight_unit` write is the
  one deliberate exception.
- 2026-09-25: The name `Settings` clashes with `androidx.compose.material.icons.filled.Settings`. Don't import both
  in one file; `FitTopBar(onSettings = …)` already draws the gear.
- 2026-09-25: Charts (1.0.22, #50): every graph uses `ui/Charts.kt` / `ui/ChartViews.kt`, with colours from
  `LocalChartColors.palette` via `seriesColor(i)`. `FrameRenderer` has its own unrelated `ChartSeries` (video), so
  don't reuse that name. #96 still needs Y-axis zoom and the buttons on the redesign's future graph screens.

- Trend lines (#152): every trend, on screen and in shared images, comes from `ui/Trend.kt` (`trendOf`, `trendText`), fitted to the points in view. Never fit against point indices or draw a trend without its figures.
- 2026-10-29 (#304 stage A): a `rememberSaveable` draft is not enough if a `LaunchedEffect` refills it on entry (SetEntry's fill effect): keep a saved `filled` flag and skip that effect's first run after a restore. Sheets keep a set as its id and look it up in the current list. Several files are CRLF and others LF, so edit through a line-ending-aware script or the Edit tool.
- 2026-10-29 (#304 stage B): editor drafts (workout, schedule) live in `ScreenState.savedList` / `savedJson` as JSON; a field added to `PlannedSet`, `PlannedExercise`, `TrainingBlock`, `TrainingPlan`, `MedDays`, `MedDose` or `MedPhase` must stay serialisable (`DraftSerializationTest`). A new row key must start after the restored maximum.
- 2026-10-29 (#307, #308): workout start/finish are shifted in Kotlin with `Dates.shiftStamp(stamp, days)` (the DAO's `moveTimes` still rewrites only the date part, so don't use it for a move). `MedicationReport.create` takes `is24Hour` from `DateFormat.is24HourFormat(ctx)` because it only has `Resources`. Left on `Dates.short`: `ShareImages.kt` ~137, `report/PdfReport.kt` ~307 (progress report axis labels) and `ui/design/CompactControls.kt:147` (custom range chip).
- 2026-10-29 (#309 A-C): `data/` must not import `ui` Brand/MainActivity/TodayWidgets: use `AppHooks` (set first in `App.onCreate`). Still importing `ui`: `siteName` (MedReminders, MedicationReport), `poseText` (PhotoExport), `countParts` (Routines), and `PdfReport` (several): candidates for moving into `data/`. `Db.rebuild` now checks `COUNT(DISTINCT from-exprs)`: exact duplicates merge, different rows sharing a key stop the upgrade (test: `DbMigrationTest.rebuild*`, uses a v29 file whose `workout_origin` has no key). One date step only: `Dates.plusDays`.
- 2026-10-29 (#309 E): `data/` imports no `ui` now (`AppHooks`, `data/DisplayText.kt`: poseText, countParts, siteName). `report/` and `video/` still do (#320). Editor sheets must close only after the write succeeds (a duplicate name used to close `ExerciseEditorSheet` / `CategoryEditorSheet` and lose everything typed). `rememberSaveable` takes a `Set<String>` or `Pair<String,String>` (both Serializable). Plain `remember` sheets still left: #316; Track tab draft: #315. Tracker: a bug item's `area` field is an array (`["ui"]`), a feature's is a string.
