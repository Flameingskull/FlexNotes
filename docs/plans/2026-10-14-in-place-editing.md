# In-place editing and grouped screens: plan across builds (2026-10-14)

> **Status (2026-10-14): Done.** Step 0: issues #164–#176 filed and imported; tracker plan item "In-place editing
> and grouped screens: plan across builds (2026-10-14)", completed. Build 1 (1.0.125: #164, #166, #167, #168, #169).
> Build 2 (1.0.126: #165, #170). Build 3 (1.0.127: #171, the audit; no large findings). Build 4 (1.0.129: #172,
> #173; its first run, 1.0.128, failed CI and published nothing). Build 5 (1.0.130: #174, with the README refresh).
> Nothing remains. Filed under rule 5, not in this plan: #175 (step count), #176 (intake logging), both
> `needs-triage`.
>
> Issue map: (1) #167 · (2) #164 · (3) #165 · (4) #170 · (5) #168 · (6) #166 · (7) #169 · audit #171 ·
> (8) #172 · (9) #173 · (10) #174.

## Context

The owner's report (2026-10-14) lists seven problems that share one cause: work is pushed onto separate screens
instead of being done where the data is shown. Their **design principle**, to apply across the app: related pages and
functions are grouped together, and every entry can be viewed, edited, moved or deleted from the screen that shows
it, on any date, whatever its source (FitNotes import or typed in).

What the code does today:

| # | Problem | Cause |
| --- | --- | --- |
| 1 | "Add measurement" is a separate entry in the day log's ⋮ menu | `DayScreen.kt:180` opens `AddMeasurementDialog` → `MeasurementEntrySheet` |
| 2 | Tapping a body value on the day log opens the measurement on today, not that value | `DayScreen.kt:296` pushes `Screen.BodyMeasurement(r.name)`; the screen starts on `Dates.today()` with nothing selected (`BodyMeasurementScreen.kt:82-83`) |
| 3 | The exercise screen's Home button does nothing | the workout drawer's `DrawerAction(Home)` (`WorkoutDrawer.kt:279`), `onDayLog` loop in `SetEntry.kt:430-433` |
| 4 | Reordering needs a separate step | workout editor rows and days use ⋮ Move up / Move down (`WorkoutEditorScreen.kt:194, 398, 454`); day log cards use ⋮ Move up / down or the drawer; measurements use `MeasurementOrderSheet`; categories use `CategoryManagerSheet` |
| 5 | A past day's entries can't be changed on that day | same as 2, plus long-press delete only for hand-entered values (`DayScreen.kt:460`) |
| 6 | Values imported from FitNotes can't be edited | `editable = source == "manual"` in `BodyMeasurementScreen.kt:214` and `MeasurementSheets.kt:71`; `Store.updateRecord` / `deleteRecord` only touch manual rows (`BodyDao.updateManualRecord`, `deleteManualRecord`) |
| 7 | Body history entries can't be changed from History | `BodyHistoryAll` (`BodyScreen.kt:130`) pushes the measurement screen; `BodyHistoryPane` switches to Track |

Owner decisions (2026-10-14):

- **Logging a body value:** removed from the day log's ⋮ menu. The Body tracker gets a + that logs any measurement in
  place, and creating or editing custom metrics moves into the Body tracker (no separate Measurements screen). The
  day log's body values card gets a small + that logs a value for that day without leaving it.
- **Tapping a body value** (day log, any date) opens it in a sheet right there: view, edit, change date or time,
  delete. Long-press deletes. Same on every date.
- **Reorder in place** with side drag handles, **hidden until a Reorder toggle is switched on**, in: the workout
  editor (exercises within a day, and the days), the day log's exercise cards, the Body tracker's measurement order,
  and the library's categories.
- **Shipping:** a plan across 2–3 `/safe-build`s.

## Step 0: backlog and plan records (no code)

Per standing rules 3 and 5 and the Plans rules in `CLAUDE.md`:

1. Copy this plan to `docs/plans/2026-10-14-in-place-editing.md`; replace this file with a one-line pointer.
2. File the issues (bugs via `bug-manager`, features via `feature-manager`), each imported into the tracker:
   - bug: tapping a day-log body value opens the wrong day / no value (2);
   - bug: exercise screen's Home does nothing (3);
   - bug: FitNotes-imported body values can't be edited or deleted (6);
   - enhancement: body value logging and custom metrics live in the Body tracker (1);
   - enhancement: edit any entry in place on any day log date (5);
   - enhancement: edit and delete body values from History (7);
   - enhancement: reorder in place with a Reorder toggle and drag handles (4);
   - enhancement (epic): app-wide audit for separate screens and dead controls, the design principle (3, 1);
   - enhancement: Profile page with date of birth, height, sex and daily activity, used by every formula (8);
   - enhancement: TDEE calculator with goals and macros, sets the calorie target (9);
   - enhancement: calories burned per day from logged exercise, and that day's calorie need (10);
   - enhancement (rule 5, not in this plan): step count or other daily movement from the phone, which would
     sharpen item 10's daily activity (needs a permission; owner to decide);
   - enhancement (rule 5, not in this plan): food or intake logging, which would let the TDEE be measured from
     the bodyweight trend instead of estimated.
3. Create the tracker `plan` item with the same title, `dependsOn` = those items' `import_...` ids.
4. Record the design principle as an owner decision in `CLAUDE.md` (Product direction) and a memory.

## Build 1: body tracker, in place (items 1, 2, 5, 6, 7)

**Data (item 6), no schema change.** Imported values become editable without a re-import bringing them back:

- `data/Workouts.kt`: add `RULE_RECORD = "record"` beside `RULE_SET`. Key = the importer's `rk(name, date, time,
  value)` (move that helper out of `FitNotesImporter.merge` into a shared `internal fun recordKey(...)`).
- `data/Daos.kt` `BodyDao`: `updateRecord` / `deleteRecord` without the `source = 'manual'` filter; keep the manual
  ones only if other callers need them (grep). Add a `record(id)` read for the original row.
- `data/Store.kt` `updateRecord` / `deleteRecord`: in one `db.transaction`, if the row's source is `fitnotes`, insert
  an `import_rule(RULE_RECORD, recordKey(original))` (the existing `WorkoutDao` rule inserts), then edit or delete.
  Mark an edited imported row's source `manual` so its new value is also protected by `manualKeys`.
  Undo of a delete (`addManualRecord`) stays as today.
- `data/FitNotesImporter.kt` `addRecord`: `consumeSkip(Workouts.RULE_RECORD, rk(...))` before adding, counted as
  skipped.
- Remove the "imported, not editable" guards: `BodyMeasurementScreen.kt:213-229`, `MeasurementSheets.kt:71` (and the
  sheet's read-only branch), `DayScreen.kt:460-461`; drop `bm_from_fitnotes` if unused. Update the KDoc on
  `Store.updateRecord` and `MeasurementEntrySheet`.
- Test: a JVM/Robolectric test beside the existing importer tests: import, edit an imported value, re-import → one
  row with the edited value; delete, re-import → still gone.

**Day log (items 1, 2, 5).**

- `DayScreen.kt`: remove the ⋮ `day_add_measurement` entry. `BodyValuesCard`: tap opens
  `MeasurementEntrySheet(snap, date, existing = r)` in place (held in `DayState` via `state.saved`, keyed by record
  id, so it survives rotation); long-press deletes any value with Undo; a small + in the card's header (and in
  `EmptyDay`'s actions when there are no values) opens `MeasurementEntrySheet(snap, date)` for that day.
- Check the other day-log entries on a past date work in place: exercise cards already open
  `Screen.SetEntry(date, …)` on that date; photos and comments use the date. Anything found sent elsewhere goes to
  the audit issue.

**Body tracker (items 1, 7).**

- `BodyScreen.kt`: a + in the top bar opens `MeasurementEntrySheet` (any measurement, today, date changeable).
  `BodyHistoryAll` rows open `MeasurementEntrySheet(existing = r)` in place; long-press deletes with Undo.
- `BodyMeasurementScreen.kt`: `BodyHistoryPane` rows open the same sheet in place instead of jumping to Track;
  the top bar's pencil no longer pushes `Screen.Measurements`.
- Fold `MeasurementsScreen.kt` into the Body tracker: its list (tracking checkbox, unit, goal, custom metrics via
  `CustomMetricEditor`, add the standard set) becomes the Body tracker's TRACK tab edit mode (the pencil toggles
  it), which is also where the reorder toggle of Build 2 lands. Remove `Screen.Measurements` and its
  `ScreenContent` line; keep `@Serializable` safety by checking no saved back stack can hold it (if one can,
  map it to `Screen.Body` instead of deleting the class).
- `strings.xml`: new `body_*` / `mse_*` keys; remove unused ones.

## Build 2: reorder in place, dead controls (items 3, 4)

- **Item 3:** remove the drawer's Home row (`WorkoutDrawer.kt:279`, the `onDayLog` parameter, `drawer_home`
  string, the `Home` import) and the loop in `SetEntry.kt:430-433`. Back and the day log stay reachable by the phone's
  back.
- **Item 4:** one shared pattern from `ui/design/Rows.kt`'s existing `DragHandle` (already used by the drawer and
  pinned cards) plus a `ReorderToggle` top-bar action (a new small composable in `design/`, an icon that turns gold
  when on, `rememberSaveable`). While on, rows show handles on the right and drag; Move up / Move down menu items
  and the separate sheets go. Reuse the drawer's drag code (`WorkoutDrawer.kt`, saved once on release) rather than
  writing a second one.
  - Workout editor: exercises within a day (and between supersets via `moveInOrder`'s rules), and the day cards.
  - Day log: exercise cards, saved with `saveExerciseOrder` / `Workouts.reorderDay`; supersets move together.
  - Body tracker: measurement order (replaces `MeasurementOrderSheet`), saved with `Store.reorderMeasurements`.
  - Library: categories (replaces the reorder part of `CategoryManagerSheet`; add/rename/recolour/delete stay
    in the category's ⋮), saved with `Workouts.reorderCategories`.
  - TalkBack: keep Move up / Move down as custom accessibility actions on each row, not visible menu items.

## Build 3 (if needed): the audit

Sweep every screen for (a) controls that do nothing or duplicate another, (b) a separate screen or sheet doing what
the screen showing the data could do, (c) entries that can't be edited where they're shown. Fix the small ones in
this build; file the large ones as `enhancement` issues under the audit epic (rule 5).

## Items 8–10: profile, TDEE and daily calories (owner, 2026-10-14)

Owner request: store height, age, sex and activity level in one profile location for every current and future
formula; a TDEE calculator that sets the calorie target from the user's goal, using the most accurate formula and
the user's recorded data; and a daily estimate of calories burned from the logged exercise, combined with the
chosen target to give that day's calorie need.

Owner decisions: **date of birth** (age worked out per date); **height is its own profile value**, separate from the
Height measurement; activity is **split**: the profile holds daily activity outside training, and exercise
calories come from what's logged; the calculator offers fat loss by rate and by deficit, maintenance,
recomposition, lean bulk and bulk, a custom kcal target, and protein, fat and carb targets; the daily figure is a
**card on the day log** for any date, opening the breakdown.

### The science (one plain-Kotlin file, `data/Energy.kt`, unit-tested in `EnergyTest`)

- **Resting energy (BMR):** Mifflin-St Jeor (1990), the most accurate for the general population in the ADA's 2005
  review: 10 × kg + 6.25 × cm − 5 × age + 5 (male) or − 161 (female). When a body fat value is logged within
  `BodyFat.STALE_DAYS`, **Katch-McArdle** (370 + 21.6 × lean kg) is used instead, as it's more accurate for lean
  and trained people; the screen shows which was used and lets the user pick either.
- **Daily activity outside training (lifestyle factor, applied to BMR, includes digestion):** Mostly sitting 1.2,
  Sitting with some walking 1.35, On your feet most of the day 1.5, Physical work 1.7, Heavy physical work 1.9,
  each described in plain words.
- **Exercise:** net calories only (MET − 1, since resting is already counted), kcal = (MET − 1) × kg × hours, METs
  from the 2024 Adult Compendium of Physical Activities. Resistance training by effort (light 3.5, moderate 5.0,
  vigorous 6.0; the set's RPE/RIR picks it when logged). Running and walking with distance and time use the
  **ACSM metabolic equations** (VO₂ from speed; 5 kcal per litre O₂), which beat a flat MET. Other cardio by speed
  band where the Compendium gives one. Duration: the logged workout time when there is one, shared across the day's
  exercises by their set counts; otherwise each set's work time (timed sets as logged, about 40 s per strength set)
  plus its resolved rest (`restFor`).
- **Bodyweight** is the logged Bodyweight nearest the date (as `relativeStrength` does), never a guess; missing
  inputs are named, as the body fat calculator does.
- **Goals:** fat loss by rate (0.25 / 0.5 / 0.75 / 1 % of bodyweight per week, × 7700 kcal per kg, ÷ 7), by deficit
  (10 / 20 / 25 % of TDEE), maintenance, recomposition (10 % deficit), lean bulk (+5–10 %), bulk (+15–20 %), custom
  kcal. Warnings: a target under BMR, or under 1200 (female) / 1500 (male) kcal; rates over 1 % a week.
- **Macros:** protein 1.6–2.2 g per kg (the top of the range in a deficit and for recomposition), fat at least
  0.8 g per kg and 20 % of calories, carbohydrate the rest; shown in grams and kcal.
- Every figure is an estimate (typically ±10 % for BMR); the screen says so and shows its inputs.

### Build 4: Profile and the TDEE calculator (items 8, 9)

- `data/Settings.kt` `PortableSettings` (in `meta`, so it travels in backups and restores; no `Db.VERSION` change):
  `profileBirthDate` (ISO), `profileHeightCm`, `profileLifestyle` (key), plus the calculator's choice:
  `calorieGoal` (kind and amount, encoded), `calorieTargetKcal`, `macroProtein`/`macroFat`/`macroCarb`,
  `bmrFormula` (auto, Mifflin-St Jeor, Katch-McArdle), `calorieMode` (same every day, or follow each day's
  training). Encode/decode, defaults and `resetToDefaults` (keeps the profile, as it keeps body fat sex).
- **Profile page:** a new `SettingsSection.Profile` (`ProfilePage`, built from `SettingsRows`: date of birth with
  the date picker and the age beside it, height as `SettingsNumberRow` in the length unit, sex (moved from the main
  list), daily activity as `SettingsChoiceRow` with each level described). The main list's Sex row becomes a
  Profile row; `CATALOGUE` entries for each field so search finds them. The body fat calculator and the TDEE screen
  show and edit the same values in place (design principle).
- Body fat (`BodyFat.kt` / `BodyFatSheet.kt`): use the profile height when no Height measurement is logged.
- **TDEE calculator:** `ui/TdeeScreen.kt`, `Screen.Tdee`, reached from the Body tracker's ⋮, the Profile page and
  the day log's calorie card. Sections: YOUR DETAILS (profile values and latest bodyweight / body fat, editable in
  place, missing ones named), RESULT (BMR and formula, daily activity, average training calories from the last 28
  days of logs, or a planned sessions × minutes × effort estimate when fewer than two weeks are logged, TDEE), GOAL
  (the options above as a radio list with each one's kcal and expected weekly change), MACROS, then **Set as my
  target** (Undo). Text in `strings.xml` (`tdee_*`, `prof_*`).

### Build 5: calories per day (item 10)

- `Energy.dayEstimate(snap, date, prefs)`: resting, daily activity, each exercise's net calories, total burned;
  and the day's need: the target, either the same every day or (follow training) TDEE for that day ± the goal's
  daily change. Cached on `snap.trainingKey` + `bodyKey` (the #60 pattern).
- Optional per-exercise accuracy: `exercise.met` (REAL, null = automatic) set in `ExerciseEditorSheet` as an
  "Activity" choice from the Compendium list. **`Db.VERSION` 22** with an `onUpgrade` step adding the column
  (rows kept), the entity in `Schema.kt`, the new schema JSON committed from `ci-logs`, and `.flexnotes` restores
  going through the same upgrade.
- Day log: a `CaloriesCard` after the body values (any date; hidden until the profile is filled, with one line
  offering to set it up), "Burned ≈ 2,640 kcal · Need ≈ 2,240 kcal". Tapping opens `CaloriesSheet` with the
  breakdown per exercise, the inputs used and a link to the calculator. Home Screen Settings gets a switch to hide it
  (`PortableSettings`, HOME group).

## After each build

- `RELEASE_NOTES.md`, `docs/CODEMAP.md` (BodyScreen, BodyMeasurementScreen, MeasurementSheets, MeasurementsScreen
  removal, WorkoutDrawer, WorkoutEditorScreen, design/ reorder toggle), the plan's Status block and the tracker plan
  item's progress; `Fixes #N` per issue. README refresh isn't due until 1.0.129.
- Brand: gold toggle and handles from `Brand` / theme; Manrope typography roles; ivory text.

## Verification

- CI is the compiler (no Android SDK locally): push, `gh run watch`, read `ci-logs` `errors.txt` on failure. The JVM
  screenshot tests (#95) and the new importer test run in CI.
- New importer test (Build 1) proves edits and deletes of imported values survive a re-import.
- Owner's phone test, Build 1: on a past date, tap a body value → sheet on that value; edit, change its date,
  delete with Undo; + on the body card logs for that date; edit a FitNotes-imported value, then re-import FitNotes →
  the edit stays and no duplicate appears; Body tracker History rows edit in place; ⋮ no longer has Add measurement;
  custom metrics are created from the Body tracker.
- Builds 4–5: `EnergyTest` checks Mifflin-St Jeor and Katch-McArdle against published worked examples, ACSM
  running/walking, MET net calories, goal deltas, macro splits and the warnings. Phone test: fill the Profile, open
  the calculator, pick a goal, set the target; the day log's card on a training day and a rest day differs by that
  day's exercise; edit an exercise's Activity and see its calories change; restore a backup and the profile returns.
- Build 2: the drawer has no Home row; Reorder toggle shows handles in the editor, day log, body tracker and library;
  drags persist after leaving and reopening; supersets stay together; TalkBack offers Move up / down.
