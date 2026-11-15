# FlexNotes code map: conventions

One part of the code map; the index is `docs/CODEMAP.md`. Source root: `app/src/main/java/com/fitlens/companion/` (paths are relative to it).
**Keep it current:** a build that adds, moves or renames a file here, or changes what one does, updates this part in
the same commit.

## Conventions

- **Vocabulary** (owner decisions on #79 and #106): an *exercise* is one movement; a user-made *workout* (FitNotes's
  routine, `Routine` in code) is exercises grouped by days the user names; a *logged workout* is one date's sets. A
  control that adds one exercise says "Add exercise", never "workout".
- **Facts about the person come from the profile** (owner, 2026-10-26): sex, age (`Energy.age(prefs.profileBirthDate, date)`),
  height and any other fixed value are read from `PortableSettings.profile*` (`ui/ProfilePage.kt` `ProfileRows`), never
  asked for on another screen. A feature needing one the profile lacks adds it there (a checked `PortableSettings` value,
  backed up, in Settings search); a screen missing a value links to the Profile page. Body weight is the logged
  bodyweight on the date, not a profile value.
- **No black text (#104):** filled controls carry gold text on a dark fill. Use `GoldButton` for primary actions and
  `errorContainer` / `onErrorContainer` for destructive ones; never fill a button or chip with the gold `primary`.

- **Screens** are `@Composable fun XScreen(snap: Snapshot, nav: Nav, …)`. `AppRoot` gives each screen a view from `rememberScreenSnapshot` (#60), replaced only when an area the screen has read (or a unit) changes; read `Store.snapshot.value` for data right after a write. To add a destination, add a `@Serializable`
  class or object to `Screen` in `MainActivity.kt` (arguments must serialise: ids, strings, numbers, enums, lists of
  ids) and a line to `ScreenContent`, and reach it from the day log's ⋮ menu (`DayScreen`) or another screen. There
  are no tabs: every screen but the day log is pushed and uses a back arrow. `nav.home(date)` returns to the day log;
  `nav.replace(screen)` swaps the top screen. Never hold a `NavController` in a screen: go through `Nav` (#37).
- **State:** a sheet, dialog or selection that should outlive rotation lives in the screen's `ScreenState` ViewModel
  (`viewModel<XState>()`, then `state.saved("key", initial)`; new screens with real state get one, #37).
  Drafts typed into a field use `rememberSaveable(keys)` (a `listSaver` for a small class, as `RoundsDraftSaver`); a set, exercise or file in
  a saved sheet is kept as its id or text and looked up again each time, never the object or a `Uri` (#304).
  A draft with nested lists (the workout and schedule editors) uses `state.savedList` / `state.savedJson`, so its data classes
  must be `@Serializable` (`DraftSerializationTest` round-trips them).
  `rememberSaveable` for other UI choices (each back-stack entry keeps its own, so a screen you return to keeps its
  scroll and choices), `remember(keys)` for derived lists.
  Launch writes with `AppScope` / `runBusy`, and report results through `UiEvents`.
- **Brand:** black and vibrant gold, no purple (1.0.71). Colours come from `MaterialTheme.colorScheme`, `Brand` or `LocalChartColors`; a shown change uses `deltaColour`; a body value's change uses `changeColour` / `measurementVerdict` (its direction, or the user's no-goal choice, #330), and a graph's key follows its `better` rule through `ChartKeyMode`. Charts may use `ChartHues` too (#198). Never write `Color(0x…)`
  outside `Theme.kt`. **Type is Manrope** (#135): use `MaterialTheme.typography` roles, never a `.sp` size outside `Theme.kt`; Canvas text uses `BrandFonts.typeface()`. Section headings are `SectionLabel`, dividers `GoldHairline`.
- **Glass depth (#102):** surfaces use `ui/design/Glass.kt`: `raisedGlass` for cards and tiles,
  `GoldButton` / `GlassOutlinedButton` instead of `Button` / `OutlinedButton` (destructive buttons with
  error colours stay plain `Button`). The page glow comes from `ambientBackdrop` on the root `Scaffold`, so screens
  and bars stay transparent rather than painting `Brand.Black`.
- **Sets are shown as columns (#101, #112):** list a day's or history's sets with `SetRow(cells = setCells(snap, fields,
  s))`, `fields = setFields(snap, exerciseId, sets)`: each value right-aligned with its unit, no heading row. Lists
  outside set entry pass `showIndex = false`. Keep "×" formulas out of set rows.
- **Stats use `snap.statSets` / `statSetsByExercise`**, which leave out warm-ups unless the setting counts them
  (#43). Lists and history use `sets`. New records, graphs or analysis must use the stat sets.
- **Order within a day** is `SetRow.position` (#70): the snapshot is sorted by date then position, and exercises follow
  their first set's position. Never order a day by set id.
- **Units:** store kg, and show values with `snap.weight(kg)` / `snap.fmtWeight(kg)` plus `snap.weightUnit`. Body
  values keep their own unit in the database; `BodyPart` shows weight units in `weightUnit` and lengths in `lengthUnit` (`MeasureUnits` in
  `Models.kt`), and `Store` converts typed values back before saving (#117, #7). **Distances** are stored as typed,
  with no unit: show them with `snap.distanceUnit(exerciseId)` (the exercise's own unit, else
  `snap.globalDistanceUnit`); a unit change relabels them and never converts (#7). **Weights on screens about one
  exercise** use `snap.weightUnitOf(exId)` and the `weight` / `toKg` / `fmtWeight(…, exId)` overloads (its own unit, else
  the global one, #7); totals across exercises use the global `weightUnit`. A measurement's own `displayUnit` (same kind
  only) wins over the global unit in `MeasureUnits.display`. The plate list is converted on a unit
  change (`withPlatesConverted`).
- **Text (#94):** new screens take their text from `res/values/strings.xml` through `stringResource` and counts through
  `pluralStringResource` (`CustomTypeSheet`, `WorkoutEditorScreen` and `SettingsScreen` are the models; every Settings page, Backups, FitNotes import, Help (its `GUIDES` are resource ids) and About included, since 1.0.103 the day log, workout drawer, calendar and set entry's Track tab, whose shared keys are `day_*`, `drawer_*`, `cal_*` and `set_*`, and since 1.0.104 the exercise screen's History, Graph and Records (`ex_*`), the library, exercise editor and category sheets (`lib_*`) and the workout sheets and tools in `WorkoutSheets.kt`, `WorkoutTools.kt` and `WorkoutEditing.kt` (`wk_*`), and since 1.0.105 Analysis (`an_*`, `rb_*`, `pin_*`), exercise Stats and Goals (`st_*`, `goals_*`, `goal_*`), the calendar filter (`cf_*`), the body tracker (`body_*`, `ms_*`, `bm_*`, `mse_*`, `mg_*`, `cm_*`, `bf_*`), the chart controls (`ch_*`, `ck_*`, `range_*`, `gr_*`) and the day bar (`dn_*`, `day_today`), and since 1.0.107 the photo screens (`ph_*`, `pv_*`, `ss_*`, `tl_*`, `pose_*`, `grp_*`, `ds_*`), setup (`su_*`), FitNotes import (`fi_*`), the rest timer and its notification (`rt_*`, `tn_*`), set cells (`sc_*`, `sd_*`), calculators (`calc_*`), the PDF report (`pdf_*`), body fat (`bfx_*`), trends (`tr_*`) and the design components' defaults (`cmn_*`, `sv_*`, `rp_*`; a `@Composable`'s default parameter may call `stringResource`); shared counts are the `reps_count` and `sets_count` plurals, #94); messages shown from callbacks use `LocalContext.current.resources`.
  The data layer's messages (#156) come from `strings.xml` too: functions that already take a `Context` word their
  results with it (`Backups` `bk_*`, the automatic-backup notification `ab_*`, `FitNotesImporter` and `BackupSync`
  `imp_*`; `ImportPlan.addedLines`/`skippedLines`/`describe` take a `Resources`), and plain results are worded in
  `ui/` (`importResultText`, `pir_*`; `ModelText.kt`; `Slice.text`; `SeedResult.text(res)`). A refused workout
  write throws `WorkoutDataException(R.string.wde_*, args)` (or `.counted` for a plural); screens show `e.text(res)`,
  and generic handlers `e.userText(res)`. Backup and CSV file contents stay English.

  Plain helpers that build text (
`planSummary`, `restSummary`, `restLabel`, `Routines.describe`, `describeSet`, `exerciseLine`, `logWorkoutDay`, `WorkoutClock.stop`, `PdfReport.create`, `setCells`, `spokenDuration`, `trendText`, `ShareImages.renderGraph`/`renderWorkout`) take a `Resources` first. A string that starts or ends with a space, or holds two in a row, is wrapped in double quotes in `strings.xml`, or Android trims it. Graph names (`GRAPH_*`) stay English constants: they're stored as keys in pinned graphs and chart-type choices.
  Every screen is now on resources; messages built in `data/` (backup, import and photo-import results) are still English (#156).
- **Comments** explain why and cite the issue (`// … (#69)`), like the code around them.
- **Toolchain (#293):** Gradle 9.8, AGP 9.4.1 compiling Kotlin 2.4.21 itself (built-in Kotlin: no `kotlin-android` plugin; the root
  build file pins the Kotlin version, and the Compose, serialization and KSP plugins move with it), Compose with Material 3,
  `compileSdk` 37 (the AndroidX libraries need it), `targetSdk` 36 (#61), `minSdk` 29. There's no local Android SDK,
  so CI is the compiler.
- **CI code checks (#260, #261):** `.github/scripts/unused_private.py` (Python standard library, no plugin) runs before the build and fails it, with
  `error: unused private path:line: kind name` lines that reach `errors.txt`, when a `private` Kotlin declaration's name appears only once in its own
  file (a mention in a comment counts as a use; `@Preview` ones are skipped). Run it locally: `python -I .github/scripts/unused_private.py app/src`.
  A warning-only `lintDebug` step runs `UnusedResources` after the APK is built, prints `lint-results-debug.txt` in the run log and writes it to `lint.txt` on `ci-logs`.
- **Tests (#40):** JVM unit tests with Robolectric in `app/src/test` (`./gradlew testDebugUnitTest`), run by CI before
  every release build; a failure stops the release and its names land in `errors.txt`. `data/DbMigrationTest.kt`
  builds older databases by hand (v1, v2, v12–v19; schemas in `OldSchemas.kt`, including the full `V20`) and opens them with `Db`; `RoomSchemaTest` opens a fresh install, a full v20 database, a replayed rebuild, a newer file and every older version through Room, which checks each against `Schema.kt`, reads every snapshot area of an upgraded database through the DAOs, and checks a main-thread query is refused (DAO calls in tests go through `offMain`);
  `WorkoutDaoTest` runs the workout and routine queries on a real database (triggers, history ranges, import rules, moving a day's notes, a scoped PR replay matching the full one), and `ExportedSchemaTest` uses Room's `MigrationTestHelper` to upgrade a v20 database against the committed `21.json` (`app/schemas` is a debug assets folder, which Robolectric loads; a `test` source set's assets aren't merged); `FitNotesImporterTest` merges a synthetic FitNotes backup through `FitNotesImporter.runMerge` (dry run, body values, custom metrics) and a body CSV through `mergeBodyCsv`; `StoreMergeTest` checks `mergeSets` against a full reload (#60); `ui/ChartViewportTest` covers full-screen zoom; `BackupsTest` covers
  `Backups.writeArchive`, `unpack` and `installDatabase`. All use a plain `Application`, so `Store` and `Settings`
  don't start: test seams take a database or file, not the singletons. **Every database change adds an upgrade test**,
  and every change to the importer's rules or the archive format adds a case.
- **Phone-size checks (#157):** `ui/PhoneSizeTest.kt` draws the same samples at 320dp and 200% font; the rules live
  once in `ui/LayoutChecks.kt` (shared with the whole-screen test below) and fail on a
  control under 48dp with no room for Compose to stretch it, text cut short or clipped, two texts overlapping, or text
  past the right edge (a scrolling row is exempt). The 320dp images are kept in the `screenshots-N` artifact
  (`roborazzi/phone/`), not compared. The samples live in `ui/DesignSamples.kt`, shared with the screenshot tests: a
  new component adds its sample there and a test in both.
- **Whole-screen checks (#323):** `ui/WholeScreenTest.kt` (each shot first `settle()`s the screen, menus open by their click action and search text is set without focus, so goldens are deterministic, #349) draws real screens (so far the day log and exercise screen, #251, and the exercise
  library, editor, custom type sheet, info and overview sheets and the details screen's Records, Stats and Goals, #252,
  plus their menus and sheets, opened by clicking, long-pressing or typing into the control that opens them; the
  details screen is drawn directly because the harness's `Nav` has no back stack) from the seeded phone in
  `ui/ScreenFixture.kt` (long names, FitNotes and FlexNotes sets, kg and lb, a Session, comments, body values, photos,
  a workout with named days, fixed on Tue 27 Oct 2026). The window itself is 320dp or 411dp wide (`WholeScreen320Test`,
  `WholeScreen411Test` share the tests in `WholeScreenChecks`, window `h3200dp-mdpi`), each at 100% and 200% font, so sheets and menus are
  laid out at that width too. Each drawing runs `LayoutChecks.problems(..., scrollingScreen = true)` (text scrolled past
  a list's edge, or laid out beside the window like a closed drawer, isn't a problem; only the open pane of a window is
  checked, unplaced measuring copies are skipped, and a word broken across lines fails) and a golden
  `screen_<name>_<width>dp[_font2].png`, with the same Record/Verify/`SCREENSHOT_STRICT` rules as `ScreenshotTest`. A
  test collects every problem and asserts once at the end; one already filed goes in `KNOWN` (line prefix to issue).
  A new screen adds a `@Test` to `WholeScreenChecks`. **Test seams in the app:** `Dates.clockSource` (`todayDate()`,
  `nowDateTime()`: "today", "this year" and the workout timer) and `Settings.showWith(prefs)`; tests set a fixed clock
  and reset it. A running rest timer isn't deterministic, so draw one paused (`RestTimer.start`, `pause`, `adjust`).
- **Function tests (#333, test-only helpers; a test that drives a function and asserts its outcome):**
  `ui/LongHistoryFixture.kt` is a second phone beside `ScreenFixture` (which must not change: its goldens): three years and
  eight weeks of thin weekly data from Mon 4 Sep 2023 to Fri 30 Oct 2026 (`START`, `END`, clock `LongHistoryFixture.clock`
  or `clockAt("08:00")`), sets with three sparse stretches (`GAPS`), a squat last logged over two years before the end
  (`OLD_LAST`), FitNotes sets up to `FITNOTES_UNTIL` and typed ones after, body values with a gap, monthly photos, and a
  medication plan (`MED_RUNS`, `MED_INFOS`) with three doses due at 08:00 on Fridays (`KEY_0800`: two of one medication
  and an injected one) and another at 08:05 (`KEY_0805`); `ONE_MONTH_AGO` … `TWO_YEARS_AGO` are the dates a graph range
  starts on. `LongHistoryFixture.snapshot()` is an in-memory `Snapshot`. `ui/FunctionTest.kt` is the base class for a
  screen test: `openScreen(snapshot, clock, preferences) { snap -> Screen(snap, nav, ...) }`, `tap("label")`,
  `longPress`, `type`, `drawn()`, `shows`/`assertShows`/`assertNotShows`, `show(snapshot)` and `stored()` (re-reads the real
  database); it resets `Dates.clockSource`, `Settings.showWith`, the rest timer and the database after each test (#373: `closeSeams` waits for `AppScope` jobs with `awaitAppScopeJobs`, moving the looper clock on, then `settleAppScopeJobs` warns and cancels any left before `Settings.awaitWrites()` and the close), and a
  subclass repeats the three Robolectric annotations (`@RunWith`, `@GraphicsMode(NATIVE)`, `@Config(... qualifiers =
  "w411dp-h3200dp-mdpi")`). `ui/NavHarness.kt` (test) draws real screens (`ScreenContent`, internal) behind a real `NavHost` wired to the test's `nav`, so a tap that pushes or goes home can be asserted through `nav.top`: `openScreen(snap) { s -> NavHarness(nav, s, Screen.Day(date)) }`. `data/ReminderHarness.kt` drives notifications and alarms over a real Room database:
  `ReminderHarness.start(app, clock)` / `stop()`, `loadPlan(MED_RUNS, MED_INFOS)`, `loadRecords`, `take(key)`,
  `setLogReminders`, `fire(h.medAlarm(key) | h.logAlarm(key)) { until }` (`sendBroadcast`, because the receivers use
  `goAsync` on `Dispatchers.IO`; always pass `until`, see its note), `notifications()` (title, text, button labels),
  `press(posted, "Skip")` (delivers the button's saved intent), `alarms()` / `nextAlarm()` (time, receiver, due key),
  `allowNotifications(false)`, `openAt(key)`, `snapshot()`, `nextAlarmClock()` (the platform's own answer for the
  next alarm clock) and `cancelAllAlarms()`; `settle()` also waits for a debounced re-arm. `LogReminders`,
  and `MedReminders` read `Dates.clockSource` (`Dates.nowDateTime()`; the rest timer's `now()` is still `System.currentTimeMillis()`, #343), so a test fixes the clock
  and asserts exact dates, and sets `Dates.clockSource` again mid-test to let time pass. `MedReminders`, `LogReminders`,
  `RestTimer` and `Settings` have internal test seams (`raceHook`, `loadOverride`, `writeFault`, `workTimeoutMs`,
  `overrun`, `deviceWriteFault`, `posted`) and `resetForTests()`, which the harness calls from `stop()`. `FunctionHelpersTest`
  and `ReminderHarnessTest` prove the helpers; copy their shape for a new function test.
- **Contrast checks (#157):** `ui/ContrastTest.kt` checks every `Brand` text colour (rises and falls included) against
  every background at 4.5:1, text on coloured containers, and `Brand.Outline` at 3:1. A new text colour or background
  joins its lists.
- **TalkBack checks (#157):** `ui/AccessibilityTest.kt` reads the semantics tree of the same samples and fails on a
  control with nothing to say in words (only "−"), two controls that say the same thing, a checkbox, switch, radio
  button or tab that doesn't announce its state, or a radio button or tab outside a `selectableGroup`. It also pins
  the readings #41 set up: section labels are headings, a settings row reads as one sentence, a number row's value is
  a polite live region, and a set row's done toggle is a "Mark done" custom action. A new component adds its sample
  here too.
- **Screenshot tests (#95):** `ui/ScreenshotTest.kt` draws the shared design components with Roborazzi on
  Robolectric's native graphics at 360dp and 411dp, font scale 1.0 and 2.0, with made-up data. Goldens are in
  `app/src/test/screenshots/`: an existing one is verified (a change fails the build; diffs land in
  `app/build/outputs/roborazzi/`), a missing one is recorded. CI keeps both as the `screenshots-N` artifact. A new
  component adds a sample to `DesignSamples` and a `@Test fun x() = shoot("x") { DesignSamples.x() }`; commit the golden from the artifact. To accept an intended
  change, delete the old golden and commit the recorded one. A screen added later uses fake data only. **Strict gate
  (#306):** `build.yml` sets `SCREENSHOT_STRICT=true` on the release build step, and `shoot` then fails a variant whose
  golden isn't committed (a PR run or a local run still records it), so a new sample's goldens must be committed from
  the PR run's artifact before the push to `main`. `DesignSamples.materialDefaults()` shows every `Fit` control in each
  state for the screenshot, phone-size and accessibility tests. **Raw-control guard (#306):** `RawMaterialControlsTest`
  (plain JVM, reads `src/main/java`) fails on a raw `Switch(`, `Slider(`, `Checkbox(`, `RadioButton(`, `AssistChip(`,
  `SuggestionChip(`, `LinearProgressIndicator(`, `Card(`, `AlertDialog(`, `FilterChip(` or `TextButton(` outside `ui/design/`; its `RAW` list names every wrapped control.
- **Derived data (#60):** work over the whole history goes through `rememberDerived(name, snap.trainingKey, …)`
  (`Charts.kt`): off the main thread, cached across screens, and not redone by photo or body writes. Work that also
  reads body values adds `snap.bodyKey` (#56). A plain `remember` never keys on the whole `snap`: it keys on the area
  keys it reads (`libraryKey`, `setsKey`, `notesKey`, `bodyKey`, `photosKey`, or `trainingKey` when it formats
  weights or mixes training areas), since 1.0.121. Put every
  input in the keys (the Records board, the exercise graph, the Records tab and pinned cards include the 1RM formula
  and rep limit). Keys must compare by value: use a graph's label, never a data class holding lambdas.

- **Graph preferences in `meta`:** chart kinds, pins and comparisons are text values in `PortableSettings`, so
  they restore from backups without a schema change. Decoders skip anything they don't understand.
