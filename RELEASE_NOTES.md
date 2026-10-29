# FlexNotes 1.0.192

## Overview

This update keeps your work in place when the phone turns. Turning the screen, switching between light and dark,
or Android closing FlexNotes in the background used to throw away anything you hadn't saved yet. Mid-workout, in
the workout and schedule editors and on several other screens, what you typed and the sheet you had open now stay
exactly as you left them.

## Fixed

- **Mid-workout:** on the exercise screen, the weight, reps, time, rounds and other values you've typed but not
  saved stay put when you turn the phone. Open sheets stay open too: a half-typed set or exercise comment, the delete
  question, the set and plate calculators, the rest timer and the exercise info.
- **The day log:** an exercise's comment, swap, delete and superset dialogs stay open when you turn the phone, and
  so do its expanded sets.
- **Workouts:** the workout editor keeps its unsaved changes, including the name, notes, days, exercises, planned sets
  and training blocks. Open sheets stay open too, such as the planned sets, a day's rest and renaming a day. Back
  still asks before discarding them.
- **Medication schedules:** the schedule editor keeps its unsaved phases and doses, the dose sheet (taper fields
  included), the date pickers and the earlier-run dialogs.
- **The exercise editor:** a new or edited exercise keeps everything you've entered.
- **Backups:** the restore confirmation and the PDF report options stay open when you turn the phone.
- **Analysis:** a pinned graph opened from the Overview opens once, with its settings. It now also does so after
  Android has closed the app in the background.

## Improved

- Behind the scenes, the rules set entry follows when you save a set are now checked by automated tests on every
  build: supersets moving to the next exercise, starting the rest and workout timers, selecting the next set and
  asking about an unusually short or long session. They work exactly as before.

## Your data

- No database change. Your workouts, measurements, photos, medications and settings are kept, and older backups
  still restore.

## Known limitations

- A few smaller sheets and pickers still reset when the phone turns: the library's delete, merge and new-category
  sheets, the search inside exercise pickers, the rest timer's length and Analysis's add-goal sheet (#311).
- Photo tiles, the photo viewer and the medication report still show some dates without the weekday (#308).
- Some controls (chips, switches, sliders) still use Material's default colours. They're being pinned to the brand
  look with screenshot checks (#306).
