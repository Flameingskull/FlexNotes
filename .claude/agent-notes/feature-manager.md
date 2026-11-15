# feature-manager notes

What earlier runs learned that isn't obvious from the code or the code map (`docs/CODEMAP.md`, `docs/codemap/`). One
line each; delete lines that stop being true, and keep the file under about 150 lines. Lessons about writing work
orders go in `coder-orders.md`. The repository is public: no personal data, secrets or `FIL.n` keys.
Pruned 2026-11-14: finished-issue status, superseded facts, tool tips for hand-editing code and lines the code map
already states were removed.

## Build and release

- No local Android SDK. CI is the compiler, so check imports, named arguments and `@OptIn`s by hand. `FitTopBar` needs
  `@OptIn(ExperimentalMaterial3Api::class)` on the calling composable (1.0.17 and 1.0.57 failed on it).
- A release's number is run number + 3 and jumps when a run is used up: read the actual name from `gh release list`
  after the build rather than assuming latest + 1. The README refresh is checked against the version the release gets.
- Release runs share one concurrency group and skip a commit that already has a `build-*` tag.
- `gh issue view --json` takes `state`, not `status`. Write issue bodies to a file and pass `--body-file`.
- Unused-resources lint is a warning-only `lint {}` block plus a non-blocking `lintDebug` CI step. Removing a function
  can orphan a private val, a DAO method or a string: re-count those names afterwards.

## Database and Room

- A schema change means: the entity in `data/Schema.kt`, `Db.VERSION`, and an `if (oldVersion < N)` step in
  `Db.upgrade` that leaves the table exactly as Room would create it (same types, NOT NULLs and `defaultValue`s), or
  Room refuses to open. `RoomSchemaTest` catches a mismatch in CI. New tables need no `CREATE` in `Db.upgrade`: the
  `reconcile` rebuild creates every table Room knows. `Db.VERSION` is 32.
- Every database change adds an upgrade test to `DbMigrationTest`. Migration steps that touch a table older test
  schemas lack check `hasTable` first.
- **Never drop a table in a migration:** older builds must still open the database (#77). The old `saved_workout*`
  tables stay empty rather than dropped.
- The exported schema `app/schemas/.../<N>.json` carries Room's hash, so it can only come from a CI build: commit it
  from `ci-logs` (`schemas/`) after the release. `ExportedSchemaTest` checks later migrations against it. The schemas
  folder is a `debug` assets folder (Robolectric loads debug assets; `test` source-set assets are not merged).
  `app/build.gradle.kts` runs `kspReleaseKotlin` after `kspDebugKotlin` and makes the debug asset merges depend on it,
  so a new version needs no second CI run.
- App code never writes SQL directly: `Db.writableDatabase` is `internal`, used only by `Db.upgrade` steps and tests.
  New reads of an existing table belong in a DAO (`data/Daos.kt`), mapped with `toModel()`. Group multi-row edits with
  `Db.transaction { }`, and chunk id lists by `Db.MAX_IDS`. Room `@Query` bind names avoid SQL keywords (`order`,
  `key`, `from`, `to`).
- `getMeta` / `setMeta` are Room queries and **must run off the main thread** (tests wrap them in `offMain`; tests
  call DAO-backed code in `runBlocking(Dispatchers.IO)`).
- `workout_set` and the other non-autogenerate tables are inserted with explicit-column `@Query("INSERT ...")`
  returning `Long` (an `@Insert` of the entity would write id 0). A `@Query` INSERT can return the new row's id
  (`MedDao.addAsNeeded`, `addTaken`): use that for Undo instead of looking the row up afterwards.
- An optional range or list is one query with `(:fromDate IS NULL OR ...)` and `(:everyExercise OR x IN (:ids))`.
- A dry run inside `Db.transaction { }` rolls back by throwing a private exception that carries the result
  (`FitNotesImporter.DryRun`); Room rethrows a RuntimeException unchanged.
- Cleaning `meta` rows that DataStore's one-time migration copies must happen *after* DataStore loads, never in
  `Db.onUpgrade` (the upgrade runs first). Data-only clean-ups need no `Db.VERSION` bump.
- Extra per-block fields go in the existing `routine.blocks` text (old lines read back unchanged), so no version bump.

## Writes, records and imports

- Writes refresh only the named `Area`s: `Workouts.write(areas)`; set-only writes whose changed sets are known use
  `writeSets(kind)`, naming exercises, dates and extra `scope.areas` (look up a set's exercise *before* deleting it).
  A write that forgets an area leaves that part of the screen stale. Don't reload again from the UI.
- A write that can move a record ends with `replayPrs(w, scope.exercises)`. Never call the full `replayPrs(w)` from a
  per-day or per-exercise write: it reads every weighted set in the history.
- 1RM and PR logic lives only in `data/Records.kt`. Every e1RM goes through `Records.factor(reps, formula = chosen())`;
  never inline a formula. A set is a PR when it's strictly heavier than every earlier set of at least as many reps.
- `Workouts.kt` and `Routines.kt` write only through `WorkoutDao` and `RoutineDao`; the DAO `addSet` / `updateSet` take
  the Session columns with no defaults, so a new caller must pass them.
- Anything that re-points data from one exercise id to another (merge) must also re-point `import_rule` exercise links
  and re-key `set` skip rules, or the next FitNotes import brings back duplicates. `Workouts.mergeExercises` is the
  reference.
- A value the user sets on a FitNotes-owned row needs a marker the importer checks (`measurement.edited`), or the next
  import silently overwrites it. An import rule per external record id (`HealthSessions.RULE`) keeps a deleted or
  edited import from coming back.
- Anything that deletes user data in bulk takes `Backups.safetyCopy` first and aborts if it fails, so Settings ›
  Backups › Undo can reverse it.
- Any new path that copies, moves or deletes sets must carry exercise comments (`exercise_comment`), and its Undo must
  restore them (`Workouts.setExerciseComments`). `workout_origin` (one row per date) drives the next-day suggestion:
  `deleteWorkout` clears it and `moveWorkout` moves it.
- A workout is `Routine` in code (`data/Routines.kt`); its days own their exercises. Changes to a logged day's planned
  sets belong in `blockAdjusted`, called by both `logWorkoutDay` and `ReviewWorkoutSheet`.
- Session and Rounds sets: a Session set is a normal `workout_set` row plus four nullable columns; its length is typed
  in minutes (`SessionFields`), and its list line comes from `sessionMarks`. A Rounds set keeps rounds in `reps`, rest
  in `restSeconds`, total in `durationSec`, hard time in `liveSec`; use `ExerciseTypes.isTimedSession` for anything
  that reads a session's length. `restSeconds` means "rest after the set" for every other type, so `restFor`,
  `Routines.resolve` and the day-wide rest sheet special-case Rounds; `Workouts.updateSet` writes it only with
  `writeRest = true`.
- A dose's day amount is `DueDose.amount` (taper, then dose change); `DueDose.planned` is the tapered amount before a
  change, so "changed from" text uses `planned`, never `dose.amount`.
- Health Connect values are body values with source `health_connect`, one per day, replaced by
  `Store.writeHealthDays`. Anything that sums a day's values goes through `Sources.dayValues(day, hcPrimary)`;
  anything that edits "the user's" values leaves `health_connect` rows out. A new type: a `HealthConnect.Kind`, its
  manifest `READ_*` permission and a `PortableSettings.hc*` flag.
- Writing to Health Connect without duplicates: a client record id per record, read back the app's own records by
  `dataOriginFilter`, write a changed record at the old `clientRecordVersion` + 1, and make every read path skip
  records whose data origin is FlexNotes.
- A new unit family goes into `VitalUnits` and is folded into `MeasureUnits.sameKind` / `convert` / `choices` /
  `convertible`. Anything stored on `measurement` in a unit (goal, `ref_low`, `ref_high`) is converted in
  `MeasureUnits.shown(def)` and back on save. Body values in kg or lbs are converted for display in `BodyPart` and back
  in `Store` writes, so stored values never change.

## Settings and stored options

- Never call `Db.getMeta` / `setMeta` for a setting; use `data/Settings.kt`. The importer's `weight_unit` write is the
  one deliberate exception. The name `Settings` clashes with the Material icon: don't import both in one file.
- Settings pages use `ui/design/SettingsRows.kt` (switch, choice, action, folder, danger, number, plus
  `SettingsStatusCard`). A page row needs an entry in `CATALOGUE` (`SettingsScreen.kt`) so search finds it; rows on the
  main list (`mainRows`) search themselves. A new main row changes the `screen_settings*` goldens.
- A row that can't be used stays visible with `disabledReason`. A problem always shows an icon and text, never colour
  alone. Put `focusRing()` before the row's `clickable`.
- A stored option list or blob goes in `PortableSettings` as text with a plain-Kotlin codec that validates on read
  (`data/MediaPrefs.kt`), unit-tested. A screen that remembers its options saves a moment after the last change and on
  leaving, and only once something changed, so opening it never turns defaults into saved values.
- Reading `Settings.portable.collectAsState()` inside one composable keeps a setting change on screen at once without
  threading it through callers.
- A stepper that writes settings or the database debounces its commit and flushes on dispose (`RestTimerSheet`).

## Screens, navigation and state

- Navigation is FitNotes-style: no bottom bar, `Screen.Day` is the root, new destinations go in the day log's ⋮ menu.
  Never write to a stack directly: use `nav.replace(screen)`. `nav.top` / `atHome` update synchronously. Screen state
  that must survive rotation goes in a `ScreenState` ViewModel.
- + opens `Screen.Library(date)`, which is the picker too; tapping an exercise replaces it with `Screen.SetEntry`, so
  Back returns to the day log. The library title is the routine switcher (`PortableSettings.lastRoutineId`).
- A left-edge swipe to open a drawer conflicts with Android's back gesture: open drawers from a button.
- Never start `ui/TimerService.kt` from the background: `refresh` updates a running instance. A notification channel's
  sound can't be changed once created: use a new channel id and delete the old one.
- New glyphs go in `ui/design/Icons.kt` (`FitIcons`), because only `material-icons-core` is a dependency.
- Panes reused in a `ModalBottomSheet` must not sit inside `FitSheet` (its content scrolls, so a LazyColumn pane would
  crash). `FitSheet` keeps `skipPartiallyExpanded = true` and models "full screen" as its own saved `full` state
  (`expandable`): M3 offers a half stop only when content is taller than half the screen, and then the buttons sit off
  screen. Don't add a `Row` to `FitSheet` for every sheet (goldens): branch on the parameter.

## Text, dates and strings

- Date helpers in `Dates` (`data/Models.kt`): `day` ("Mon 27 Oct", the human-first standard), `long` (weekday and
  year); `medium` has no weekday, so it's wrong for dates people read. For anything displayed use
  `Dates.todayDate()` / `nowDateTime()` (the `Dates.clockSource` seam), never `LocalDate.now()`.
- The data layer has no `Resources`: plain results carry a typed value the UI words; refusals throw
  `WorkoutDataException(R.string.x, args)` and screens show `e.userText(res)` (its `message` is null). Data-layer enums
  keep their `label`; the UI maps them in `ui/AnalysisText.kt`.
- `strings.xml`: apostrophes and quotes are escaped; Android trims leading and trailing spaces and collapses double
  spaces unless the text is wrapped in double quotes. A string read with `getString(id)` and no arguments isn't
  formatted, so `%%` shows literally: use `formatted="false"` and a bare `%`.
- Text inside `semantics { }`, `onClick` or `remember { }` can't call `stringResource`: read it into a `val` first.
  Inside `Modifier.semantics { }`, a local named `selected` shadows the semantics property: write `this.selected`.
- `GRAPH_*` labels and `metricGraphLabels` are stored keys (pinned graphs, chart kinds): translate them only through a
  display mapping. Add new graphs only at the end of `graphLabels`, because `exercise.default_graph` stores the index.
  Count graphs are `Total <name>` labels appended by `graphLabels(type, timeBased, countNames)`.

## Charts

- Every graph uses `ui/Charts.kt` / `ui/ChartViews.kt`, with colours from `LocalChartColors.palette` via
  `seriesColor(i)`. `FrameRenderer` has its own unrelated `ChartSeries` (video): don't reuse the name.
- `categoryColour` and `exercisePickerItems` are `@Composable`: never call them inside `remember { }` or
  `rememberChartData { }`. Analysis maths lives in `data/Analysis.kt` (pure Kotlin); screens only format.
- `FitChart` takes `range` (a shaded band with two dashed lines) next to `goal`. Mixed-unit series go on one graph as a
  share of each series' own best, with the real figures in the picked point's note. Full-screen graphs zoom values via
  `ChartViewport.yFrom` / `yTo`; bar charts pass `valueZoom = false`.
- A graph rule carries its key mode (`MoveRule`, `measurementRule`): `bestPoint` and `FitChart` both read
  `keyModeOf(better)`; never probe `better(1.0, 0.0)` directly for a key decision (a 0.5 target gives null).
  `FitChart`'s `better` still accepts any lambda at 13 call sites: #370's last criterion, in Build 26j.

## Tests and goldens

- In whole-screen tests a running rest timer isn't deterministic: draw it paused. Sheets, menus and drawers are
  separate windows, grouped by their semantics root.
- Changing a design sample changes its golden. New or changed goldens are recorded on the check branch's pull-request
  run and committed from its `screenshots-pr-<n>` artifact **before** `main`, because the release build sets
  `SCREENSHOT_STRICT=true`.
- In sample composables a control needs `semantics(mergeDescendants = true) { contentDescription }` and a chip a
  `stateDescription`, or `AccessibilityTest` flags it.
- `FunctionTest.tap` takes the first match, so a chip name that also appears as a row behind a sheet needs the sheet
  drawn alone. Plain JUnit tests that call a helper taking `Resources` need Robolectric.

## Open work worth knowing

- #367 (Workouts screen in sections by kind) needs a `routine` category column (a migration) and a kinds list in
  `PortableSettings`; pair it with #357 (same screen, Build 26k). #357 needs no migration.
- #332: `LogReminders.nextFor` looks 8 days ahead and `dueAt` matches weekday and time only; both need rewriting for
  reminder patterns. `MeasurementEntrySheet` has 5 call sites and is the Medications sheet too.
- The launcher icon and branding art are the owner's, unedited (`branding/`): never redraw them.
