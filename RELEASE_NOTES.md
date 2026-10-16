## Overview

This update brings three things: runs, rides and other sessions from your watch now appear in the day log; a new
Body fat value starts from the figure your latest measurements give; and you can export your progress photos as
ordinary image files.

Install this update over the current app as usual. Everything you've logged is kept. The database gains one column,
which records where a set imported from Health Connect came from, and every existing set is kept as it is.

## What's new

- **Exercise sessions from Health Connect.** In Settings › Health Connect, under Workouts, choose Health Connect for
  Exercise sessions. Each run, ride, swim, walk or other session your watch or fitness app recorded in the last 30
  days becomes one cardio set on its day, with its time and distance, under the matching exercise in the Cardio
  category, such as Outdoor Run, Cycling or Swimming. If that exercise is missing, FlexNotes creates it. The session's
  title goes in the exercise comment, and the set's comment names the app it came from. Distances are in your unit.
- **Each session is added once.** Syncing again never adds a session twice. If you edit, move or delete an imported
  set, it stays as you left it, and a session deleted in Health Connect stays in FlexNotes. Strength sessions are
  left out, as you log those set by set.
- **Body fat starts from your measurements.** When you log a new Body fat value, on its Track tab or in the Log
  value sheet, the field is filled with the figure the US Navy formula gives from your latest neck, waist, hips
  (women) and height on or before that day. If no Height is logged, your profile height is used. The comment
  records how it was calculated. A line underneath names the measurements and dates used, and flags any over 14 days
  old. You can change the figure, and nothing is saved until you press Save.
- **Export photos as image files.** In Photos, ⋮ › Export as image files saves copies of all your photos, or those
  in a date range, into a folder you choose. Selected photos can be exported from the selection bar's menu too. Files
  are named by date and pose, such as 2026-10-16_front.jpg, with _2, _3 for more than one. Each copy keeps its
  FlexNotes date and time, pose and comment in its photo details. You can follow the progress, stop the export, or
  hide it while it runs.

## Improved

- When the measurements can't give a Body fat figure, the line under the field says which ones are missing or what
  looks wrong, and the value starts from your last logged one as before.
- Files already in the export folder are never replaced or deleted, and your photos and backups in FlexNotes don't
  change.

## Known limitations

- Automatic backups don't copy photos as image files yet. That's the next part of the export work (#195).
- Some storage providers don't let apps set a file's modified date, so an exported file may show the export time
  there. Its photo details always carry the date it was taken.
- Photos in formats whose details can't be written, such as HEIC, are exported unchanged.
- Only the last 30 days are read from Health Connect, which is what it allows by default. Reading the full history
  is planned (#192), and FlexNotes doesn't write to Health Connect yet (#191).
