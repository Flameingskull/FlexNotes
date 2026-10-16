## Overview

Health Connect now brings more of what your scale, watch and health apps record into FlexNotes. Next to steps and
food, you can now read bodyweight and body fat, active calories, resting heart rate and sleep. Each one is switched on
separately in Settings › Health Connect. Active calories can also drive the daily activity part of the calorie
estimate, without counting your logged workouts twice.

As before, Health Connect runs on your phone and FlexNotes only reads from it. It still has no internet permission.
Install this update over the current app as usual. Everything you've logged is kept, and the database doesn't change.

## What's new

- **Bodyweight and body fat from Health Connect.** Choose Health Connect for Bodyweight or Body fat, and each reading
  from a smart scale or health app appears in the Body tracker at its own time, in your unit. A morning and an evening
  weigh-in stay as two readings. Syncing again never adds a reading twice, and a weigh-in that two apps share counts
  once.
- **Active calories as your daily activity.** Choose Health Connect for Active calories, and the calories your watch
  or phone counts as burned in movement are kept as a daily **Active calories** value. After 3 days of them, the
  calorie estimate uses them instead of your steps or activity level. Logged exercise only adds what goes beyond that
  day's active calories, so a workout your watch already counted isn't added again. The calories sheet and the TDEE
  calculator show the figure they used, and how much logged exercise covered.
- **Resting heart rate and sleep.** Choose Health Connect for either, and each day gets a **Resting heart rate**
  value (the day's average, in bpm) and a **Sleep** value (hours asleep, counted on the day you wake up). Both appear
  in the Body tracker with their own graphs.

## Improved

- **Settings › Health Connect** now groups the types into Sources (steps, food and macros, active calories) and Body
  values (bodyweight, body fat, resting heart rate, sleep). Each asks for its own permission only when you switch it
  on, and each can be found from the Settings search.
- **Your own values still stay.** Health Connect's values are kept beside what you type, marked Health Connect. A sync
  replaces only Health Connect's values, never yours, and editing one makes it yours.
- A Bodyweight or Body fat measurement you named slightly differently, such as "Body Weight", is found and used.

## Known limitations

- Bodyweight readings from Health Connect sit beside the ones you type. The calorie estimate uses the reading nearest
  the day, whichever source it came from.
- Only the last 30 days are read, which is what Health Connect allows by default. Reading the full history is
  planned (#192). FlexNotes doesn't write to Health Connect yet (#191).
- Workouts recorded by a watch (runs, rides and so on) aren't read yet. They're planned for the next update (#190).
- The rest of the accessibility checks (TalkBack, switch access and whole screens on a small phone) still need a phone
  (#157).
