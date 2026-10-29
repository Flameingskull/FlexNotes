# FlexNotes 1.0.194

## Overview

This update is about not losing what you're in the middle of. Sheets, pickers and half-made choices across the app
now stay put when you turn the phone, and a name that's already taken no longer wipes the exercise or category you
were typing. The rest timer's Start button now uses the rest your workout sets, just like the automatic start, and
dates on graphs and in the custom date range are clear across New Year.

## Improved

- **Turning the phone keeps your place:** the exercise library's delete, merge and new-category sheets, the search,
  section and ticked exercises in every exercise picker, the rest timer's length, Analysis's add-goal sheet and
  overlay picker, the exercise screen's calculator and goal sheets, training blocks in the workout editor, the
  medication report's ticked medications and Breakdown's period and custom dates all stay as you left them.
- **Rest timer:** starting the timer by hand now uses the rest your workout sets for that exercise or set, the same
  as when it starts after a saved set. You can still change the length for this rest; the sheet tells you that the
  workout sets it, and your exercise and default rest lengths stay as they were.
- **One clock:** Workout Time, reminders and every other time field now use the same clock dial, in your phone's
  12-hour or 24-hour style.
- **Records board:** a record estimated from a set of more than 10 reps is marked "≈", as it already is on the
  exercise's Records tab.

## Fixed

- **Names already in use:** saving an exercise or category with a name that's already taken now keeps the sheet
  open with everything you typed, instead of closing it and losing your work.
- **Medication report:** turning the phone while choosing where to save the report no longer brings back medications
  you had unticked.
- **Dates with the year:** the ends of graphs in shared images and the progress PDF show the year, and the custom
  date range shows it for any date outside this year, so a range across New Year is never ambiguous.

## Your data

- No database change. Your workouts, measurements, photos, medications and settings are kept, and older backups
  still restore.
- Updating from a very old version is now stricter: if an upgrade would ever have to drop a row that isn't an exact
  duplicate, it stops and leaves your data untouched instead.

## Known limitations

- A few sheets still reset when the phone turns: the body tracker's Track tab (#315) and some sheets in Medications,
  Measurements, Records and Readiness (#316). They're next on the roadmap.
- The Records board doesn't show the weight unit yet (#319).
