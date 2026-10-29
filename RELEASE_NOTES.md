# FlexNotes 1.0.196

## Overview

This is a maintenance update with no visible changes. The code that draws the PDF reports and slideshow videos now
stands apart from the app's screens, so a change to a screen can't change a report or a video by accident. The
README on GitHub has also been brought up to date.

## Improved

- **Reports and videos stand on their own:** the progress report, the Medications report and the slideshow frames
  now take their colours, fonts and wording from shared code, not from the screens. Each looks exactly as before:
  the same black and gold, the same Manrope type, and sets, schedules, tapers and reference ranges in the same words
  as the app.
- **Checked on every build:** a new test stops the data, report and video code from using the screens again, and
  another checks that the report and video colours match the app's to the last digit.

## Your data

- No database change. Your workouts, measurements, photos, medications and settings are kept, and older backups
  still restore.

## Known limitations

- The human-first review of every screen (#251 to #258) and the accessibility pass (#157) still wait for a check
  on a real phone at 200% font size.
