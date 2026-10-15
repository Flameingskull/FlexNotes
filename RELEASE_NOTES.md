## Overview

FlexNotes can now count your steps with your phone's own step counter and use them in your calorie estimate. Until
now, daily activity outside training was a level you picked (Mostly sitting, Some walking and so on). With step
counting on, the calories you burn each day follow how much you actually walked.

Everything stays on your phone. FlexNotes reads the phone's step counter directly. It doesn't use Health Connect or
any online service, and it still has no internet access.

Install it over the current app as usual. Everything you've logged is kept, and the database doesn't change.

## What's new

- **Daily activity from my steps.** In Settings, Profile (and on the TDEE calculator) there's a new switch under
  Daily activity. Turning it on asks once for the Physical activity permission. If your phone has no step counter,
  the switch says so.
- **Your steps in the Body tracker.** Each day's steps are recorded as a **Steps** value in the Body tracker, so you
  can see them, graph them, and edit or delete a day like any other value.
- **A sharper calorie estimate.** Once three full days are counted, the TDEE calculator uses the Mostly sitting level
  plus the walking in your average steps above 2,500 a day, instead of the level you picked. Until then it keeps using
  your chosen level. The figure takes your bodyweight and height into account.
- **Each day's own steps.** The calories sheet on the day log shows that day's steps and the activity calories they
  add, so a busy day burns more than a quiet one. Today's figure grows as you walk.

## Known limitations

- Steps are counted only while step counting is on and FlexNotes is installed. FlexNotes reads the counter when you
  open it and, when the phone allows, every 15 minutes in the background. If the phone doesn't let it read in the
  background, steps between two readings that span midnight are shared between the days by time.
- Restarting the phone loses the steps taken since FlexNotes last read the counter.
- A walk or run you log as a workout while carrying your phone is also counted in your steps, so on those days the
  estimate can count some of that walking twice (#181).
- Cardio distances from FitNotes are still imported as logged, without converting between kilometres and miles
  (#180). That fix needs a FitNotes backup with cardio sets to confirm where FitNotes keeps the distance unit.
