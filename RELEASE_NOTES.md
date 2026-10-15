## Overview

This update makes the calorie estimate from your steps more accurate and makes falls easier to read. A walk or run you
log as a workout is no longer counted a second time through your phone's steps. The red used for falls is now a
little brighter, so it reads clearly on every card.

Install it over the current app as usual. Everything you've logged is kept, and the database doesn't change.

## Improved

- **Logged walks and runs aren't counted twice.** With Daily activity from my steps on, a walk or run you log while
  carrying your phone was counted twice: once as the exercise's own calories and again in the day's steps. Now the
  steps that walk or run took come off the day's steps, worked out from its distance and your stride (a longer stride
  for a run). The day's calories and your average daily total (TDEE) both use this.
- **What was left out is shown.** The calories sheet on the day log shows the day's steps and how many were left out
  for logged walks and runs. The TDEE calculator says when its average steps leave them out.
- **Clearer falls.** The red for a value that went down, and for graph lines, is slightly brighter. It now meets the
  standard contrast for text on every card and surface, as the gold, ivory and green already did. The PDF report and
  progress videos use the same red.
- **Contrast is checked on every build.** Each new version now checks every text and control colour against every
  background it's drawn on, so a hard-to-read colour can't ship unnoticed.

## Known limitations

- A walk or run counts as one when the exercise's activity is set to Walking or Running, or when it has a distance and
  a time at walking or running pace. Other exercises, such as a hike logged only as a time, are counted as before.
- If you logged a walk without carrying your phone, its steps are still taken off, so that day's activity can be
  slightly lower than it should be.
- Cardio distances from FitNotes are still imported as logged, without converting between kilometres and miles
  (#180). That fix needs a FitNotes backup with cardio sets to confirm where FitNotes keeps the distance unit.
- The rest of the accessibility checks (TalkBack, switch access and whole screens on a small phone) still need a phone
  (#157).
