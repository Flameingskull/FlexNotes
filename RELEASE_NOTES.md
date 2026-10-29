# FlexNotes 1.0.195

## Overview

This update finishes the work on keeping your place when you turn the phone: the body tracker's Track tab and the
remaining sheets in Medications, Measurements, Readiness and the exercise library now keep what you've typed. The
Records board now always shows its unit. In Medications, you now choose how long after its time a dose counts as
missed, and removing a phase or a dose in a schedule can be undone.

## What's new

- **When a dose counts as missed:** choose 30 minutes, 1 hour, 2 hours or 4 hours after its time, or at the end of
  the day. The default is 1 hour, so a dose due at 08:00 no longer reads "Missed" at 08:01. Today, adherence, the
  medication report and the CSV export all follow the same choice. Find it in Settings › Medications › Count a dose
  as missed, or open a missed dose on the Today tab and change it there.
- **Undo in the schedule editor:** deleting a phase or removing a dose now shows Undo, which puts it back where it
  was. A phase comes back with all its doses. Nothing changes until you save the schedule.

## Improved

- **Records board:** the unit (kg or lbs) is always on the screen, once above the rows, or under each exercise's
  name when your exercises use different units. Each exercise's records are shown in its own unit, and the line for
  a selected record shows the unit on both weights.

## Fixed

- **Body tracker Track tab:** a value, time, comment or injection site you've typed is no longer lost when you turn
  the phone. Changing the day still fills in that day's value.
- **Sheets that reset when you turned the phone:** the as-needed, supply, symptom, dose and dose-change sheets in
  Medications; the measurement entry sheet (including a Body fat estimate), goals, reference ranges and the custom
  measurement editor; the readiness check-in; the Records board's category, chosen exercises and selected record;
  exercises picked with a long press in the library; and the exercise editor's activity picker all keep their
  input.

## Your data

- No database change. Your workouts, measurements, photos, medications and settings are kept, and older backups
  still restore. The new missed-dose choice travels with your backups.
- Doses you've taken or skipped never change. Only doses that are still open follow the new missed-dose window, so
  today's adherence may now count a dose as missed before the day ends.

## Known limitations

- The human-first review of every screen (#251 to #258) still waits for a check on a real phone at 200% font size.
- The PDF and video code still uses a few screen-side helpers. Moving them is planned for the next small build
  (#320), and nothing you can see changes.
