# FlexNotes 1.0.186

## Overview

This update works through what a screen-by-screen review of the whole app found: places where FlexNotes technically worked but didn't suit a person using it. Times are easier to set and read, mistakes are easier to undo, failures explain themselves, and graphs make better use of a turned phone.

## What's new

- **Graphs turn with your phone:** a full-screen graph now follows the phone into landscape even when auto-rotate is off, with the status bars hidden for more room. A new **Rotate** button next to Reset zoom switches between landscape and portrait, and your zoom is kept through every turn.
- **Medication days on the calendar:** days when you took a dose get their own dot and legend entry, and the chosen day lists each dose with its amount and time. A tap opens Medications. With Lock Medications on, none of this is shown.
- **The widget confirms what it logged:** tapping **Taken** or **Take** on the home-screen widget shows which medication was logged and at what time, and the as-needed row says when it was last taken today.
- **Copy a workout from any date:** Copy previous workout lists earlier workouts first, newest at the top, and **Choose a date** reaches any older day.

## Improved

- **Times follow your phone:** every time in the app, from doses and reminders to the widget, the calendar, workout time and backups, now reads in your phone's 12 or 24 hour style.
- **Times are picked on a clock:** body values, measurements and dose times are chosen on a clock dial instead of typed, so a time can no longer be saved as something other than what you meant.
- **Undo in the workout editor:** deleting a day or removing an exercise now offers Undo.
- **A safety copy before deleting an exercise:** deleting an exercise that has history first keeps a safety copy, so you can undo it from Settings › Backup for 7 days.
- **Plain failure messages:** when a backup, restore, import or export fails, the message says what went wrong and what to do next ("Choose it again", "Free some space"), not technical error text.
- **Analysis from day one:** Analysis opens even before any sets are logged, on Readiness, so the daily check-in is there from the start.
- **Clearer wording:** effort choices read the same in the picker and in your sets ("3 RIR", "RPE 8") with a line explaining them; training load explains Monotony and Strain and shows Strain's unit; the Calories card says "estimated from the activity" or "from distance and time" instead of MET and equation terms.
- **Personal records open their day:** tap a record to go to the day it was set. Days with photos in an exercise's History show a camera icon.
- **Choosing several exercises:** the library now says "Touch and hold an exercise to choose several."
- **Workout drawer:** the date, sets and time sit on one line and the reorder tip on its own.
- **Weekday dates** on the emergency card PDF and the Data tools date range.
- **About** no longer shows the technical Save speed readout.

## Fixed

- Turning the phone closed a full-screen graph on Analysis and Readiness.
- Saving a workout with no name left Save greyed out with no explanation. It now tells you a name is needed and marks the field.
- A typed time that couldn't be read was quietly saved as "now" or 08:00.

## Known limitations

- Turning the phone doesn't yet open a graph full screen by itself; use the expand button, then turn.
- The large-font check of every screen on a real phone is still to come, and anything it finds will be fixed in a later update.
