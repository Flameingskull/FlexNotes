## Overview

FlexNotes can now use what other apps record on your phone, through Google Health Connect. Choose Health Connect as
the source of your daily steps, or of what you ate, and FlexNotes fills them in for you. A watch's step count or a food
app such as MacrosFirst can then feed the calorie estimate and the measured TDEE without you typing anything.

Health Connect runs on your phone, so nothing leaves it: FlexNotes only reads, and it still has no internet
permission. Install this update over the current app as usual. Everything you've logged is kept, and the database
doesn't change.

## What's new

- **Settings › Health Connect.** A new page under DATA. For steps, and for food and macros, choose where FlexNotes gets
  them. It asks for Health Connect's permission only when you switch a type to it. The page also shows when FlexNotes
  last synced, has a **Sync now** button, opens Health Connect's own settings, and explains what's read and why.
- **Steps from Health Connect.** Daily activity from my steps (Settings › Profile, and the new page) now offers
  **Off**, **Phone's step counter** or **Health Connect**. With Health Connect, the phone's own counter stops and each
  day's total comes from Health Connect. Health Connect applies your app priority, so steps from a watch and the phone
  aren't counted twice. The calorie estimate uses them just as it did the phone's count.
- **Food and macros from Health Connect.** Choose Health Connect for food and macros, and each day's calories, protein,
  fat and carbs come from the apps that write them, such as MacrosFirst. The calories card shows where the figures came
  from, for example "From Health Connect · MacrosFirst". The measured TDEE then works from those logs as well.
- **Automatic syncing.** FlexNotes reads the last 30 days whenever it opens. If you allow background reading in Health
  Connect, it also syncs every hour while it's closed.

## Improved

- **Your own values always stay.** Each day's Health Connect total is kept in the Body tracker as its own value,
  marked Health Connect, beside anything you typed or the phone counted. A sync replaces only Health Connect's values,
  never yours. On a day with no Health Connect figures, what you typed counts. Editing a Health Connect value makes it
  yours, and later syncs leave it alone.
- **Typing intake while Health Connect is on.** The calories sheet still lets you type the day's totals. While
  Health Connect has figures for that day, it says they're the ones counted, and keeps what you typed for days without
  them. Switch back to Typed in FlexNotes at any time, and your typed figures count again.

## Known limitations

- Health Connect is built into Android 14 and later. On Android 13 and earlier, the page offers to install it from the
  Play Store.
- Only the last 30 days are read, which is what Health Connect allows by default. Reading the full history is
  planned (#192). FlexNotes doesn't write to Health Connect yet (#191).
- If you delete a Health Connect value while Health Connect is still the source, the next sync brings it back. To keep
  your own figure for that day, edit the value instead.
- Bodyweight, body fat, active calories, resting heart rate, sleep and workouts from Health Connect are planned for
  the next updates (#187 to #190).
- The rest of the accessibility checks (TalkBack, switch access and whole screens on a small phone) still need a phone
  (#157).
