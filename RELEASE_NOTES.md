## Overview

FlexNotes can now write to Health Connect as well as read from it. Your bodyweight, body fat and workouts can be
shared with other health and fitness apps on your phone. Each type is off until you switch it on.

Install this update over the current app as usual. Everything you've logged is kept, and the database doesn't change.

## What's new

- **Write to Health Connect.** Settings › Health Connect has a new section, **Write to Health Connect**, with three
  switches: **Write bodyweight**, **Write body fat** and **Write workouts**. When you switch one on, Health Connect
  asks whether FlexNotes may write that type. Nothing is written until you allow it.
- **Bodyweight and body fat.** Every Bodyweight and Body fat value you've logged or imported is written at the
  time it was logged. Bodyweight is converted to kilograms, as Health Connect stores it. A value without a time
  counts at noon.
- **Workouts.** Each day you've logged sets becomes a strength training session in Health Connect, called
  FlexNotes workout. It runs from the workout timer's start to its end, and its notes list the day's exercises in
  order. Health Connect needs a start and an end, so days without both are left out.
- **Kept up to date, never doubled.** Each value is written once. If you edit it in FlexNotes, the copy in Health
  Connect is replaced. If you delete it, it's deleted there too. This happens at the next sync: when FlexNotes opens,
  from **Sync now**, and every hour.

## Improved

- **No loops.** Values and sessions FlexNotes read from Health Connect are never written back. When FlexNotes reads
  bodyweight, body fat or exercise sessions, it skips the ones it wrote itself, so nothing appears twice in your
  Body tracker or day log.
- **Writing works in the background.** The hourly sync now also runs to keep what's written up to date, even if
  you haven't allowed background reading.
- **Clearer privacy note.** The privacy note in Settings › Health Connect now lists exercise sessions among the
  types FlexNotes can read, and says what it writes. Everything stays on your phone: FlexNotes still has no internet
  permission.

## Known limitations

- Switching a type off stops writing it, but what was already written stays in Health Connect. You can delete it
  in Health Connect's own settings.
- Every workout is written as strength training, even a day of only cardio exercises.
- Health Connect sessions show when the workout ran, not each set. Your sets, weights and reps stay in FlexNotes.
