# FlexNotes 1.0.193

## Overview

This update keeps FlexNotes looking the way it should, however Android's design library changes. Every switch,
slider, checkbox, chip, button, card, progress bar and dialog now has its black and gold colours set by FlexNotes
itself, and automated screenshot checks cover each one. It also brings weekday dates to your photos and the
medication report, and fixes moving a workout that finished after midnight.

## Improved

- **Controls keep the FlexNotes look:** switches, sliders, checkboxes, radio buttons, chips, text buttons, cards,
  progress bars and dialogs now draw in FlexNotes's own black and gold, whatever a future Android library update
  changes. Before, a library update could quietly turn them grey or another colour.
- **Easier to see:** a switch that's on is now light gold on a deep gold track, and the empty part of a slider (the
  rest timer's sound, the camera's photo opacity, the slideshow) is clearly visible on black. A chip you haven't
  chosen reads in ivory rather than grey.

## Fixed

- **Photo dates:** photo tiles and the photo chooser show the date with the weekday ("Mon 27 Oct"), with the year for
  older photos, on its own line so it's never cut off. The pose, where there is one, goes underneath.
- **Medication report:** the dose log shows weekday dates, and the due and taken times follow your phone's 12-hour
  or 24-hour clock. The schedule list uses your clock style too, and the ends of each chart show the year, so a range
  across New Year is clear.
- **Moving a workout:** moving a workout that finished after midnight to another date keeps its finish on the
  following day, so its length stays right instead of turning negative.

## Your data

- No database change. Your workouts, measurements, photos, medications and settings are kept, and older backups
  still restore.

## Known limitations

- A few smaller sheets and pickers still reset when the phone turns: the library's delete, merge and new-category
  sheets, the search inside exercise pickers, the rest timer's length and Analysis's add-goal sheet (#311).
- A few graph end labels in shared images and the progress PDF still show dates without the year. They're part of
  the next audit pass (#309).
