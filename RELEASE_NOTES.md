# FlexNotes 1.0.182

## Overview

This build continues the screen-by-screen review, this time for body tracking, photos, the slideshow, the PDF reports and medications. More dates read the way you plan your week, like "Mon 27 Oct", and long names keep a tidy layout at large font sizes.

## Improved

- **Weekday dates across body tracking and photos.** The body tracker's history, graph points and Start, Latest, Lowest and Highest figures, the body fat sheet, measurement goals, the photos grid's day and week headers, the photo viewer, Compare, export and the slideshow's date range now show the weekday. The year only appears when it isn't this year.
- **The progress PDF reads by date.** Each day in the report is headed by its weekday date, and the "DAY 12" counter beside it is gone. While the report is being made, the progress line names the day it's on.
- **Medication dates with the weekday.** The emergency card's last as-needed dose and the medication report's date range and "generated" date now show the weekday.
- **Tidy rows at large font sizes.** Long measurement, medication and symptom names now wrap to two lines and end with "…" if longer, so they no longer push into the values beside them. The same goes for a dose's amount and its Skipped or Missed note, and for the labels above the body tracker's figures. The figures themselves are never cut off.

## Your data

- No database change. Your measurements, photos, medications and settings are kept, and older backups still restore.

## Known limitations

- The body and photos review and the medications review (issues #255 and #256) stay open until they've been checked on a phone at a large font size, as do the earlier reviews (#251 to #254).
- Small date labels on photo thumbnails, chart axes and the medication report's daily table stay short ("27 Oct") to fit their space.
- The slideshow's optional "Day 12 · Week 2" stamp is unchanged for now; how it should read is a decision for a later build (issue #267).
- Next up: the review of settings, profile, backup and import, then the calendar, widget and notifications (issues #257 and #258).
