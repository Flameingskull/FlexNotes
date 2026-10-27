# FlexNotes 1.0.183

## Overview

This build finishes the screen-by-screen review in code. It covers Settings, backups, imports and Health Connect, plus the calendar and the home-screen widget. More dates and times now read the way you plan your week, like "Mon 27 Oct, 14:05", and the widget keeps long medication names readable.

## Improved

- **Weekday dates in Settings.** The last and next automatic backup, the safety copy and its undo message, backup results, the last FitNotes import and the last Health Connect read now show the weekday and time. The year only appears when it isn't this year.
- **Restoring a backup.** The restore dialog shows when the backup was made and the dates it covers with the weekday. The PDF report's From and To dates show it too.
- **Calendar.** "Share" for the chosen day names it by its weekday date. Body values under the chosen day keep the number and its unit together on one line, so a long measurement name wraps in its own space.
- **Home-screen widget.** A long medication name and its dose now wrap to a second line instead of being cut short. The Taken and Take buttons grow with large font sizes instead of clipping their text.

## Your data

- No database change. Your workouts, measurements, photos, medications and settings are kept, and older backups still restore.

## Known limitations

- The settings review and the calendar, widget and notifications review (issues #257 and #258) stay open until they've been checked on a phone at a large font size, as do the earlier reviews (#251 to #256).
- The slideshow's optional "Day 12 · Week 2" stamp is unchanged until you choose how it should read (issue #267).
- A profile's date of birth stays as "12 Mar 1990". People don't think of a birthday by its weekday.
- Next up: the code clean-up (issues #259 to #262).
