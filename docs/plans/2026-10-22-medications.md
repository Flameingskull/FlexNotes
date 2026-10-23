# Medications: plan across builds (2026-10-22)

> **Status (2026-10-23): Done.** Step 0 done: issues #199–#206 filed (label `area: medications`) and imported;
> tracker plan item "Medications: plan across builds (2026-10-22)". Parity fix done (duplicate #157 item archived);
> parity rule added to CLAUDE.md and the build skills. All 16 suggestions picked: #207–#222, in the follow-up plan
> `docs/plans/2026-10-22-health-and-medications-follow-ups.md`.
> Build A done in 1.0.152 (#199, #200: the medication flag, database v24, the body split through `BodyPart` and
> `Snapshot.medicationView`, and the Medications screen reusing the Body tracker's panes).
> Build B done in 1.0.154 (#201, #202: database v25, `MedSchedules.kt`, the schedule editor, runs with pauses, and
> TODAY on the Track tab; v25 also holds `med_dose_change` for #204, so Build C needs no migration). README refreshed. Its first run, 1.0.153, failed CI: debug and release KSP raced on the new schema file.
> Deviation: schedules are listed in a SCHEDULES section on the Track tab rather than a title switcher.
> Build C done in 1.0.156 (#203, #204: `MedReminders.kt` alarm chain, notification with Taken / Snooze / Skip,
> Settings › Medications, and dose changes for a day or the rest of a run; no database change).
> Build D done in 1.0.157 (#205: `report/MedicationReport.kt`, `CsvExport.medications`, the dose log and adherence in
> `MedSchedules`, and the Report for a doctor sheet), closing epic #206. Nothing remains; follow-ups are in
> `docs/plans/2026-10-22-health-and-medications-follow-ups.md`.
>
> Issue map: Epic #206 · M1 #199 · M2 #200 · M3 #201 · M4 #202 · M5 #203 · M6 #204 · M7 #205.

## Context

Users have been logging prescribed medications on the Body tracker as custom measurements. The owner wants proper
support (2026-10-22). It should copy what already works and reuse it:

1. **Mark any metric as a medication.** Medications get their own **Medications** screen, a near copy of the
   Body tracker (Track / History / Graph). They show **only** there: never on the Body tracker, the day log's body
   values card, body graphs or overlays, energy estimates, Health Connect writes or body exports. Each value is stored
   once, so nothing is recorded or shown twice.
2. **Dose schedules built like workouts.** A schedule is made of **cycles**: any number of on and off phases, each
   lasting days, weeks, months or years. On phases hold doses (medication, amount, times of day, which days). The user
   activates a schedule from a start date of their choice.
3. **Reminders through Android notifications.** You confirm a dose the way you tick a set: Taken (or Snooze or
   Skip) from the notification or the screen.
4. **Change a dose on any day.** The change can apply to that day only, or to the rest of the active schedule. The
   schedule template itself stays unchanged.
5. **A Medications-only PDF** to share with a doctor.

Owner naming decision (2026-10-22): the app calls them **Medications** ("Medications" screen, "This is a medication"
switch). In this plan, "medication" means anything prescribed, including supplements.

Owner process rule (2026-10-22): **local tracker and GitHub issues must always match.** Every open issue has exactly
one tracker item, and every open tracker bug or feature has an open issue. Nothing gets lost and nothing is
duplicated. The parity check today found a duplicate: #157 has two tracker items, the pre-rename `FIL.132` and
`import_0a70…`.

## Step 0 (this session, after approval)

1. Save this plan to `docs/plans/2026-10-22-medications.md` with a `> **Status**` block, and replace this file with a
   one-line pointer to it.
2. Create the label `area: medications`. File the GitHub issues below: an epic, plus M1–M7, each `enhancement`,
   `needs-triage`, `area: medications`, with `Depends on #N` lines and acceptance criteria.
3. Import each one through the `github-issues` importer. Set the fields as in CLAUDE.md, with `dependsOn` using
   `import_…` ids. Create the tracker `plan` item with the same title, pointing at the file, `dependsOn` every issue.
4. **Parity fix:** archive the duplicate #157 item (`FIL.132`, which has no origin link) and keep `import_0a70…`.
5. **Parity rule:** add it to CLAUDE.md's tracker section as a standing rule. Each build skill (`/new-build`,
   `/safe-build`, `/slice-build`) runs a check at its start and before its push: list open issues and open tracker
   items, import missing ones, archive duplicates, and close items whose issue has closed. Also save a feedback memory.
6. Show the suggestion list below as a multi-select. Every pick becomes an issue plus a tracker item, added to this
   plan or to a follow-up plan.

## Issues

| Key | Title | Depends on |
| --- | --- | --- |
| Epic | Medications: tracking, cycle schedules, reminders and a doctor's PDF | M1–M7 |
| M1 | Mark a measurement as a medication; keep it out of every body view (database v24) | — |
| M2 | Medications screen: Track, History and Graph, a copy of the Body tracker | M1 |
| M3 | Medication schedules: on/off cycles with doses, built like a workout | M1 |
| M4 | Activate, pause and end a schedule; today's doses with a Taken tick | M2, M3 |
| M5 | Dose reminders: notifications with Taken, Snooze and Skip | M4 |
| M6 | Change a dose: this day only, or the rest of the schedule | M4 |
| M7 | Medications PDF (and CSV) for a doctor | M2 (schedule and adherence sections M4) |

### M1: Medication flag and the split (data)
- `Db.VERSION` 24: `measurement.medication INTEGER NOT NULL DEFAULT 0` in `Db.upgrade`. Add the field to the
  `Schema.kt` entity, `MeasurementDef.medication` in `Models.kt`, the `toModel` mapper, a `DbMigrationTest` v23→v24
  case, and the 24.json schema from `ci-logs`.
- Values stay in their existing table. Switching a measurement to medication moves its whole history to the
  Medications screen without copying it, and switching it back returns it. This is what stops double recording.
- **One chokepoint:** `BodyPart` splits `allMeasurements` into body measurements and `medications`.
  `usedMeasurements` and every body consumer read only the body list: `BodyScreen`, the day log's `BodyValuesCard`,
  `BodyGraphPane` overlays (#56), `EnergyEstimates`, `HealthWrite.readings`, body CSV in `CsvExport`, `PdfReport`'s
  body section, the calendar and `TimelineScreen`. Grep `allMeasurements` and `usedMeasurements` to find them all.
- The switch is offered on custom measurements (`CustomMetricEditor`, `MeasurementsEditor`). It is refused for
  Bodyweight, Body fat, Height, the `Intake` kinds and `HealthConnect.Names`, which other features depend on.
- Tests: the split, and the refused names.

### M2: Medications screen
- `Screen.Medications` and `Screen.Medication(name)` in `MainActivity.kt`, reached from the day log's ⋮ menu after
  Body tracker. Add `MedicationsScreen.kt`, cloned from `BodyScreen.kt`, and `MedicationScreen.kt`, cloned from
  `BodyMeasurementScreen.kt`. They reuse `BodyGraphPane`, `BodyHistoryPane`, `MeasurementEntrySheet` and
  `MeasurementsEditor`, with a `medication` filter parameter instead of new copies.
- Units: free text such as mg, mL, IU or tablets. Add new strings with an `md_*` prefix. Edit in place (#164), with
  Reorder behind the toggle. Update CODEMAP.

### M3: Schedule builder
- New tables: `med_schedule` (name, notes, `repeat` whole cycle yes/no), `med_phase` (schedule, position,
  `on`/`off`, length, unit days/weeks/months/years) and `med_dose` (phase, medication name, amount, unit, time of
  day, days: every day, chosen weekdays or every N days, comment). Add them with `MedSchedules.kt` in `data/` (models
  plus `load`, `save`, `copy`, `delete`) and a `ScheduleDao`. This mirrors `Routines.kt`.
- Pure Kotlin `MedSchedules.dosesOn(schedule, activation, date)` works out the phase and the doses due on any date.
  Calendar-month lengths use `java.time` `plusMonths` / `plusYears`. Test with `MedSchedulesTest`, covering leap
  years, month ends and a repeating cycle.
- `ScheduleEditorScreen.kt` is cloned from `WorkoutEditorScreen.kt`. Phases are FitNotes cards (ON · 8 weeks /
  OFF · 4 weeks) with + Add dose. Doses are rows like exercises. A `DoseSheet` works like `PlannedSetsSheet`. The
  ReorderToggle moves phases and doses. The ⋮ menu has Duplicate and Delete. Schedules are listed on the Medications
  screen through a title switcher, as in the library (#106).

### M4: Activation and today's doses
- `med_activation` (schedule, start date, paused from, ended on). Several schedules can run at once.
- Taking a dose writes a normal medication value (`measurement_record`). New nullable columns `dose_id` and `due`
  (date and time) let a tick find its own value, so it's idempotent. A `med_dose_status` row records skipped doses.
- Track tab, top section **TODAY**: the doses due, with time, amount and a done checkbox like `SetRow` (#19). Missed
  doses are flagged, and the phase is shown ("On cycle · day 12 of 56", "Off cycle · resumes 3 Dec"). Ticking uses
  `Haptics.confirm` and Undo.

### M5: Reminders
- `MedReminders.kt` holds the next alarm only, as a chain. It uses `AlarmManager.setExactAndAllowWhileIdle`, asking
  for `SCHEDULE_EXACT_ALARM` through the `NotificationAccess` pattern, and falls back to a 10-minute window when that
  isn't allowed. The alarm is re-armed after every write and on `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED` and
  `MY_PACKAGE_REPLACED`.
- A new high-importance channel, **Medication reminders**. The notification has **Taken** / **Snooze 15 min** /
  **Skip**, and a `BroadcastReceiver` writes the result through `Store` with no screen open. If a dose is still not
  taken, a follow-up nudge comes after N minutes (a setting). Doses due at the same time share one notification.
- Privacy: the notification setting "Show medication names on the lock screen" is off by default
  (`VISIBILITY_PRIVATE` with a generic public version). Add a Settings › Medications page with a `CATALOGUE` entry.
- The app stays local only: no internet, and `AlarmManager` is on-device.

### M6: Dose changes
- Editing a due or taken dose's amount asks **This day only** or **This day and the rest of this schedule**. The
  second writes a `med_dose_change` row (activation, dose, from date, amount) that `dosesOn` applies. The template is
  untouched, and the change shows in History with its date. Undo is supported.

### M7: Doctor's PDF
- `report/MedicationReport.kt` reuses `PdfReport`'s page drawing (dark or light) and `ReportDialog`-style options:
  date range, which medications, and include schedule, adherence, comments and graphs.
- Sections: patient line (name optional), current medications and schedules, a dose log table (due, taken, amount,
  changes), adherence % per medication, and comments. It contains no workout, body or photo data.
- Save or share through the existing share sheet. A Medications CSV (`CsvExport`) is offered next to it.

## Builds (each through `/safe-build` unless the owner says otherwise)

- **A:** M1 + M2: the split and the screen. This is the first useful release and fixes double display straight away.
- **B:** M3 + M4: schedules, activation, today's ticks.
- **C:** M5 + M6: reminders and dose changes.
- **D:** M7: PDF and CSV.

Each build updates this plan's Status block, the plan item's `progress`, CODEMAP and the README if it's due.

## Verification

- CI is the compiler. New unit tests run there: `MedSchedulesTest` (phases, months and years, repeats, changes), the
  v24 migration in `DbMigrationTest` / `RoomSchemaTest`, the body split (no medication in `usedMeasurements`,
  `HealthWrite.readings` or the body CSV), and `MedRemindersTest` for the next-alarm choice (Robolectric
  `ShadowAlarmManager`). New components add `DesignSamples` entries for the screenshot, phone-size and TalkBack tests.
- On the phone, with the owner: mark an existing custom measurement as a medication and check it leaves the Body
  tracker and day log and appears on Medications with its full history. Build a 2-week on / 1-week off schedule
  starting today and confirm reminders arrive (including after a reboot). Tick Taken from the notification. Change a
  dose for the rest of the schedule. Export the PDF.

## Suggestions to offer the owner (multi-select after approval)

These are gaps that Health Connect-linked apps don't cover well, ordered by value to the most users:

1. **Side effects and symptoms log**: rate symptoms 0–10 against doses and see them next to dose changes.
2. **Dose vs outcome graphs**: overlay a medication's dose on bodyweight, strength, resting HR or sleep graphs,
   reusing #56's overlay. No other app links prescriptions to training and body results.
3. **Supply and refill tracking**: count doses left, warn N days before running out, and track expiry dates.
4. **Injection site rotation**: a body map of recent sites and a suggestion for the next one (for GLP-1s, insulin
   and TRT).
5. **Lab results / bloodwork**: markers with reference ranges, out-of-range flags, trends, and a PDF section. Later,
   read Health Connect's medical records (FHIR) where available.
6. **Vitals from Health Connect**: blood pressure, blood glucose, SpO₂, HRV and body temperature, read and logged by
   hand.
7. **Appointment summary PDF**: one report for a chosen period with medications, adherence, symptoms, vitals, labs
   and bodyweight.
8. **App lock for Medications**: PIN or fingerprint, with private notifications. This health data is sensitive.
9. **Emergency card**: current medications, allergies and conditions, printable and shareable.
10. **Daily readiness check-in**: sleep quality, soreness, mood, stress and energy, plus HRV and resting HR from
    Health Connect, as a readiness score with no subscription.
11. **Training load for athletes**: acute:chronic workload ratio and monotony from logged volume and sessions, with a
    spike warning.
12. **Training blocks / periodisation**: reuse the cycle engine for mesocycles and deload weeks on workouts.
13. **Reminders for other logging**: weigh-ins, measurements, progress photos and planned workout days, on the same
    scheduler.
14. **Home-screen widget**: today's doses with Taken, plus today's planned workout.
15. **Hydration and caffeine log**: timed intake, with caffeine still active at bedtime set against sleep from
    Health Connect.
16. **Menstrual cycle overlay**: read Health Connect's cycle data and show phases on body and strength graphs.

Deliberately left out: drug interaction or dosing advice. That would be medical advice and needs an online,
licensed database, which breaks the local-only rule.
