# FlexNotes code map

Read this first: how data flows, and where things usually go. Then open **only the part you need** below, and `grep`
for the exact code. Don't read whole folders, and don't read every part.
Source root: `app/src/main/java/com/fitlens/companion/` (paths are relative to it).

| Part | Holds |
| --- | --- |
| `docs/codemap/data.md` | Every file in `data/`: the database, stores, models, imports, backups, calculations |
| `docs/codemap/ui.md` | Every file in `ui/` and `ui/design/`: screens, sheets, charts, shared components |
| `docs/codemap/other.md` | `report/` (the PDFs), `video/`, `branding/`, the app shell (`App.kt`, `AppHooks.kt`), `docs/plans/` and `docs/reference/` |
| `docs/codemap/conventions.md` | The patterns to follow: vocabulary, screens and state, brand and glass, units, text, the toolchain, CI checks, and every kind of test and its harness |

**Keep it current:** any build that adds, moves or renames a file, or changes a pattern, updates the matching part in
the same commit, and this index if data flow or "where things go" changed.

Last updated: 1.0.204.

## How data flows

- **One SQLite database**, `fitlens.db`, opened through Room by `data/Db.kt` since 1.0.113 (#36); the tables are Room entities in `data/Schema.kt`. `Db.VERSION` is 32 (v32 added nullable `exercise.count_names` (a Session or Rounds exercise's up to two count names joined by `|`, `SportCounts` in `SportLibrary.kt`) and `count_a`, `count_b` on `workout_set` and `routine_day_set`, sport counts #266; `SetRow.countA/countB`, `PlannedSet.countA/countB`, `Exercise.countNames`; `CountFields` in `SessionFields.kt` is the Track tab's steppers, `countParts` in `SessionText.kt` the set line; v31 added nullable `exercise.session_kind` and `session_fields`, a Session exercise's kind of sport and the optional fields it records, #249; v30 added nullable `live_seconds`, `intensity`, `session_type` and `zone` to `workout_set` and `routine_day_set`: a Session's hard minutes, intensity, type and cardio zone, #240, #241; v29 added `routine.blocks`, `block_start` and `block_repeat`, a workout's training blocks, #218; v28 added `med_dose.step`, `step_days` and `step_limit` (a taper or step-up, #234) and `med_info.as_needed` and `max_per_day` (#233); v27 added `measurement.ref_low`, `ref_high` (a reference range in the stored unit) and `lab` (a lab marker), #211; v26 added `mrecord.site`, the site of an injected dose, and the `med_info`, `symptom` and `symptom_rating` tables, #207, #209, #210; v25 added the `med_schedule`, `med_phase`, `med_dose`, `med_activation`, `med_dose_status` and `med_dose_change` tables, created by the `reconcile` rebuild, and `mrecord.dose_id` / `due`, #201, #202, #204; v24 added `measurement.medication`, #199; v23 added `workout_set.external_id`, the Health Connect record a set came from, #190; v22 added `exercise.activity`, an `Energy.Activity` key, #174; v5 added `workout_set.set_type` and `rpe`; v6 added `exercise_goal`, `exercise.weight_step` and `default_graph`, and `measurement.edited`; v7 added `saved_workout`, `saved_workout_exercise` and `saved_workout_set`; v8 added `routine`, `routine_day` and `workout_origin`; v9 added `workout_set.position` and the `set_position` trigger that gives each new set the next position; v10 added `workout_set.superset`, `saved_workout_exercise.superset` and the `set_superset` trigger that puts a new set into its exercise's group; v11 added `workout_set.done` for "mark sets complete", #19; v12 added `exercise.rest_seconds`, #15; v13 (#106) added `routine_day_exercise` and `routine_day_set`, copied every saved workout into the routine days that used it (unused ones became one-day workouts), re-pointed `workout_origin` at workout and day, and left the `saved_workout*` tables empty so older builds still open the database, #77; v14 added `exercise_comment`, one comment per exercise per date, #107; v15 added `exercise.distance_unit`, #7; v16 added `exercise.weight_unit` and `measurement.display_unit`, #7; v17 added prescribed rest, #138: `routine_day_set.rest_seconds`, `routine_day_exercise.rest_seconds` and `rest_after_seconds`, `workout_set.rest_seconds` and the `workout_rest` table (date, exercise, rest between sets, rest after); v18 added the default Body fat and Height measurements, `addDefaultMeasurements`, also run by `onCreate`, #153; v19 added `exercise_type` (user-defined types, ids from 100) and `workout_set.metric`, #14; v20 added `routine_day_set.metric`, so predefined sets hold a custom type's metric, #155; v21 (#36) rebuilt every table from Room's own SQL, `Db.reconcile`, so Room's schema check passes: UNIQUE constraints became the unique indices `idx_photo_hash` and `idx_exercise_comment_day`, and text keys became NOT NULL). Schema changes
  bump it, change the entity in `Schema.kt` to match, and add an `if (oldVersion < N)` block in `Db.upgrade` that keeps every row (Room checks the result against `Schema.kt` and refuses to open on any difference; a newer file opens anyway, #77). Since 1.0.118 the whole app reads and writes `fitlens.db` through typed DAOs in `data/Daos.kt` (`Db.snapshotDao`, `metaDao`, `bodyDao`, `photoDao`, `goalDao`, `workoutDao`, `routineDao`, `importDao` for the FitNotes merge and body CSV, `maintenanceDao` behind `Db.checkpoint()`; `Db.transaction { }` groups DAO calls; bind at most `Db.MAX_IDS` ids per query; bind names must not be SQL keywords, so `fromDate`/`toId`/`ruleKey`, never `from`/`to`/`key`/`group`/`offset`). DAO calls must run off the main thread (Room refuses otherwise). `Db.writableDatabase` (Room's `SupportSQLiteDatabase`, with the `rawQuery`/`insert`/`update` shims at the end of `Db.kt`) is `internal`, left to `Db.upgrade` steps and tests; raw `android.database.sqlite.SQLiteDatabase` is used only to read foreign files (the FitNotes backup, a staged `.flexnotes` database in `Backups.unpack`). Room exports its schema to `app/schemas/` on every build (committed: `app/schemas/com.fitlens.companion.data.FlexNotesDatabase/21.json`; a new `Db.VERSION` commits its new JSON from the `ci-logs` branch), and CI copies it to `ci-logs`. `.flexnotes` restores of older
  backups go through the same upgrade.
- **Settings** (#38) go through `data/Settings.kt` only. Phone-only settings (`DeviceSettings`: folders, schedules,
  last import, safety copy, last result) live in DataStore and never travel in backups. The user's preferences
  (`PortableSettings`: units, logging (keep screen on, `autofillSource`, `autoSelectNext`), PR celebrations, `graphKinds` for chart types, #137, `pinnedGraphs` and `graphCompare`, #55, #53) live in
  `meta`, so they restore from backups (day log: `homeShowCategories`, `homeSetsShown`, #8). The old phone-only `meta` rows are deleted once DataStore has loaded, and
  again after a restore (`Settings.dropLegacyDeviceRows`, #98). Both are
  loaded once at start-up and held in memory: read `Settings.current()` / `currentPortable()`, observe
  `Settings.device` / `portable` in Compose, and change them with `updateDevice` / `updatePortable` (or
  `updateDeviceNow` from background work). The only other `meta` writer is the FitNotes importer's weight unit,
  inside its own transaction; `Store.reload()` re-reads the preferences after it.
- **Reads:** `data/Store.kt` loads everything into one immutable `Snapshot` and publishes it as
  `Store.snapshot: StateFlow<Snapshot?>`. Screens take `snap: Snapshot` as a parameter. `Snapshot` has the lookups
  (`exercises`, `categories`, `setsByExercise`, `setsByDate`, `photosByDate`, `recordsByName`) and unit helpers
  (`weight(kg)`, `toKg(shown)`, `fmtWeight(kg)`, `weightUnit`). Since 1.0.55 (#60) the snapshot is built from one
  part per `Area` (`LibraryPart`, `SetPart`, `NotesPart`, `BodyPart`, `PhotoPart`, each with its own lookups); its
  public properties delegate to them, so screens don't see the split.
- **Writes:** workout data goes through `data/Workouts.kt`. Its private `write(areas) { w -> }` (`w` is the `WorkoutDao`) runs one Room transaction
  on `Dispatchers.IO`, then `Store.refresh(areas)` re-reads only those areas and shares the rest (default
  `Area.WORKOUT`; library-only writes pass `LIBRARY`, comments and times `NOTES`). Every write that changes the sets of
  known exercises or dates (one set, a logged workout day, swap, merge, copy, move, deleting a workout, and their undos)
  uses `writeSets(kind) { w, scope -> }`: name the touched exercises or dates in `scope` (before a delete, while the
  rows exist), plus any other `scope.areas` it changed (`NOTES`, `LIBRARY`); `Store.refreshSets` re-reads just those
  sets and merges them (`mergeSets`), with those areas, in one update. A single-set write (save, edit, delete, tick,
  comment, reorder a day, undo a delete) names only `scope.exerciseDays` (`DayExercise`; `addDaysOfSets` before a
  delete, and for the sets whose PR marks `replayPrs(..., changed = )` flipped): those are re-read through the date
  index (`SnapshotDao.setsOn` / `setsOfOn`) and patched in by `SetPart.patched`, which keeps every other day's and
  exercise's list as the same object, so a set save doesn't grow with history (#60, `SaveSpeedTest`, which checks it by `Store.setRowsRead`, the set rows read into snapshots, not by wall-clock time, #236). A write that can move a PR calls
  `replayPrs(w, scope.exercises)` (only those exercises, `WorkoutDao.prCandidatesOf`); only "Recalculate PRs" and a
  history delete for every exercise use the plain `write` and replay everything (#60). Photo and measurement writes live
  in `Store` and refresh `PHOTOS` / `BODY`; `Goals` and `Routines` refresh `LIBRARY`; a preference change calls
  `Store.refresh()` (no read). `Store.reload()` (everything, preferences included) is for start-up, imports and restores.
  **A new write must name every area it changes**, or the screen shows stale data.
- **Ownership:** each row has `source` = `Sources.FLEXNOTES` or `Sources.FITNOTES` (`data/Models.kt`). FitNotes
  imports merge and never overwrite FlexNotes rows. Editing or deleting an imported row records an `import_rule` so
  re-imports respect the change. Body values too (#166): `Store.updateRecord` / `deleteRecord` (`editRecord` /
  `removeRecord` on a given `Db`, for tests) edit any value, leave a `Workouts.RULE_RECORD` rule keyed by
  `Workouts.recordKey` for an imported original, and mark an edited value `manual`; the FitNotes merge and
  `mergeBodyCsv` skip those keys.
- **Work where the data is shown** (owner, 2026-10-14, plan `docs/plans/archive/2026-10-14-in-place-editing.md`): an entry is
  viewed, edited and deleted from the screen showing it, as a sheet over it (`MeasurementEntrySheet` for body values;
  long-press deletes with Undo, `deleteBodyValue`). Don't add a screen or menu item for what that screen can do.
- **Dates** are ISO `yyyy-MM-dd` strings. Use `Dates` in `data/Models.kt` (`today()`, `parse`, `epochDay`, `long`,
  `medium`, `short`, `plusDays(iso, n)`, the one calendar step; never write another; `dayMonth`, "3 Mar" with the year only outside this year, for short range labels). ISO strings compare correctly as text. Where people read a date, use `Dates.day` ("Mon 27 Oct",
  the year only when it isn't this year), and `Dates.dayTime(epochMs)` for a moment ("Mon 27 Oct, 14:05": backups,
  imports, Health Connect reads).

## Where things usually go

| Change | Touch |
| --- | --- |
| New workout data field | `Db.kt` (VERSION and `upgrade`), `Schema.kt` (the entity), `Models.kt`, the `SnapshotDao` mapper in `Daos.kt` (`toModel`), `Workouts.kt`, and `Backups.kt` if it's a new table |
| New setting | A field in `DeviceSettings` (phone-only) or `PortableSettings` (travels in backups) in `data/Settings.kt`, with its key and default, then a `MainRow` in `mainRows` under the matching heading (SettingsScreen.kt), or a row on one of its pages plus a `CATALOGUE` entry |
| Records or 1RM logic | `data/Records.kt` only. Screens and the PDF call it |
| New graph | Build its points in `rememberChartData(keys) { … }`, get `val (kind, setKind) = rememberChartKind("<area>:<graph>")`, draw with `FitChart(kind = kind)` (line, bar, area or step, #137) or `DonutChart`, pass `kind`/`onKind` to `GraphOptionChips`, add an `ExpandGraphButton` and a `FullScreenChart` (#96, `valueZoom = kind != ChartKind.BAR`) with `GraphOptionChips` as its `controls`, and a `ChartHint` under it; share images take the `kind` too |
| New screen | A `@Serializable` entry in `Screen` and a line in `ScreenContent` (`MainActivity.kt`), a new `ui/XScreen.kt` built from `ui/design/`, and an `XState` in `ScreenState.kt` if it has sheets or a selection to keep |
- 1.0.66 (#22, #7, #11 closed): branded share images live in `ui/ShareImages.kt` (Canvas, `Brand` colours via
  `toArgb()`); reuse it rather than drawing another. `MenuAction` already exists in `ui/design/TopBar.kt` (label,
  `enabled`, `onClick`): pass `onClick` by name. Database v16 adds `exercise.weight_unit` and
  `measurement.display_unit`; `saveCustomMetric` replaces its row, so it carries `display_unit` over. Migration steps
  that touch a table older test schemas lack check `hasTable` first.
