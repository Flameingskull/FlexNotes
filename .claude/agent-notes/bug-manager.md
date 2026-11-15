# bug-manager notes

What earlier runs learned that isn't obvious from the code or the code map (`docs/CODEMAP.md`, `docs/codemap/`). One
line each; delete lines that stop being true, and keep the file under about 150 lines. Lessons about writing work
orders go in `coder-orders.md`. The repository is public: no personal data, secrets or `FIL.n` keys.
Pruned 2026-11-14: finished-issue status, superseded facts and tool tips for hand-editing code were removed.

## Basics

- No local Android SDK. CI is the compiler, so check imports, named arguments and `@OptIn`s by hand.
- Writes refresh only the named `Area`s (`Workouts.write(areas)`, `writeSets(kind)`); a write that forgets an area
  leaves that part of the screen stale. Don't reload again from the UI.
- 1RM and PR logic lives only in `data/Records.kt`. A set is a PR when it's strictly heavier than every earlier set of
  at least as many reps (ties aren't PRs).
- Never call `Db.getMeta` / `setMeta` for a setting; use `data/Settings.kt`. The name `Settings` clashes with the
  Material icon: don't import both in one file.
- Every graph uses `ui/Charts.kt` / `ui/ChartViews.kt`, with colours via `seriesColor(i)`. `FrameRenderer` has its own
  unrelated `ChartSeries` (video).
- Every trend, on screen and in shared images, comes from `ui/Trend.kt` (`trendOf`, `trendText`), fitted to the points
  in view. Never fit against point indices or draw a trend without its figures.
- `data/`, `report/` and `video/` import no `ui`: use `AppHooks` (set first in `App.onCreate`) and
  `data/DisplayText.kt` (`poseText`, `countParts`, `siteName`). One date step only: `Dates.plusDays`.
- `gh issue view --json` takes `state`, not `status`.

## State that must survive rotation

- A `rememberSaveable` draft isn't enough if a `LaunchedEffect` refills it on entry (SetEntry's fill effect, the Body
  fat prefill in `MeasurementEntrySheet`, the fill in `BodyMeasurementScreen`): keep a saved marker and skip that
  effect's first run after a restore (`remember { booleanArrayOf(true) }` plus the marker).
- Sheets keep a set as its id and look it up in the current list; a sheet that holds a `MeasurementDef` keeps its name
  and looks it up in `snap.allMeasurements`.
- Editor drafts (workout, schedule) live in `ScreenState.savedList` / `savedJson` as JSON: a field added to
  `PlannedSet`, `PlannedExercise`, `TrainingBlock`, `TrainingPlan`, `MedDays`, `MedDose` or `MedPhase` must stay
  serialisable (`DraftSerializationTest`). A new row key must start after the restored maximum.
- `rememberSaveable` takes a `Set<String>`, a `Pair<String, String>` or an enum directly; a map or list needs a
  `listSaver` (see `ReadinessSheet.typed`, `ExerciseLibrary.picked`), with a marker first when empty would restore as
  "no state".
- Editor sheets close only after the write succeeds (a duplicate name used to close `ExerciseEditorSheet` and lose
  everything typed).

## Sheets that hold typed input (#334, #331)

- `FitSheet(dirty)` = `rememberDirty(vararg)` (each value's text, as a list, against its `rememberSaveable` start) +
  `confirmValueChange` + a guarded `onDismissRequest`. `dirty` goes through `rememberUpdatedState`, because the sheet
  state's lambda is captured once. Cancel calls the caller's `onDismiss` directly, so it still closes.
- Call sites name the local `unsaved` (the workout and schedule editors already have a screen-level `dirty`). Place
  `rememberDirty` above any early `return` in the sheet. `EmergencyCardSheet` saves on every close, so it isn't guarded.
- Not guarded on purpose: calculator and stats sheets, the rest timer, choice, filter and info sheets (#384 covers the
  choice sheets). Sheets still lose typed text on rotation where they use plain `remember` (#381).
- Tests can tap outside via the scrim node (content description contains "close"); Back can't be driven on the JVM
  (#385, #387 wait for the emulator, #335).

## Dates and clocks

- Workout start and finish are shifted in Kotlin with `Dates.shiftStamp(stamp, days)`; the DAO's `moveTimes` rewrites
  only the date part, so don't use it for a move.
- `MedicationReport.create` takes `is24Hour` from `DateFormat.is24HourFormat(ctx)`, because it only has `Resources`.
- Anything displayed uses `Dates.todayDate()` / `nowDateTime()` (the `Dates.clockSource` seam). `LocalDate.now()` still
  sits in some screens outside the graph path (#360, #383).
- `MeasurementGoals.verdict`, TARGET branch: with a target set it decides alone (an equal-distance move used to fall
  through to "a rise is good"). `previousValue()` / `changeFromEarlierDay()` in `ui/MeasurementGoals.kt` are the one
  "previous value" rule (#372); a settings choice that applies to body values but not medications is read once per
  pane as `changeFromEarlierDay() && !medications`.

## Graphs

- "All time stops short" (#328) had no code cap: the only hard limit is `Analysis.totals`' `guard < 2000` periods
  (about 5.5 years of Day periods). Reproduce range bugs with `LongHistoryFixture`. Range defaults differ: body and
  exercise 4 (All), Analysis Workouts and Readiness 1 (3M).
- `Db.rebuild` checks `COUNT(DISTINCT from-exprs)`: exact duplicates merge, different rows sharing a key stop the
  upgrade (`DbMigrationTest.rebuild*`).

## Tests and goldens

- `Nav` has no controller in `FunctionTest`, so a pushed screen can't be asserted there: use `ui/NavHarness.kt` (test).
- A new whole-screen shot (`WholeScreenTest.shoot`) has no golden, and the release build sets `SCREENSHOT_STRICT`, so
  it fails the release until its golden is recorded on the check branch's pull-request run and committed. Adding a ⋮
  menu item changes the `screen_day_menu*` goldens (8 files); a new main Settings row changes `screen_settings*`.
- A new whole-screen shot also fails on any fresh layout finding (ellipsis, a target under 48dp), so don't use
  `ListRowWithMenu` (title `maxLines` 2) for long names.
- The one whole-screen capture path is `shoot` in `WholeScreenTest.kt`; it `settle()`s first (#349). Whether a flake is
  cured can only be shown by repeated CI runs. `FittedTitle` (`TopBar.kt`) keys its shrink on the available width;
  `typeText` uses `performTextReplacement` (no focus, no cursor).
- The function-test teardown calls `awaitAppScopeJobs()` (idles the Robolectric main looper, bounded): `AppScope.scope`
  is on `Dispatchers.Main`, so a plain `join()` in `runBlocking` would deadlock.
- A Robolectric main-thread `delay()` wakes only when the looper clock moves: use
  `ShadowLooper.idleMainLooper(10, MILLISECONDS)` in a wait loop, not the no-argument form. `Job.isActive` is false for
  a cancelled job still in `finally`: use `!isCompleted`.
- Read a text node's colour in a function test via
  `node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(list)`, then `layoutInput.style.color`,
  on an unmerged tree (see `LayoutChecks.kt`).
- A test that reads the database on the main thread fails CI ("Cannot access database on the main thread"): 1.0.202's
  settings round-trip test did, was taken out and is #379.
