# FlexNotes 1.0.198

## Overview

This update is about reminders you can rely on. Medication reminders now arrive as one notification per dose, each with its own Taken, Snooze and Skip. Every alarm in the app, for doses, logging and the rest timer, now rings at the minute it's set for, even when the phone is locked or idle.

## What's new

- **One reminder per dose:** three medications due at the same time give three notifications, each showing its own name and amount in full. Before, they were squeezed into one, and some names could be cut off.
- **Taken, Snooze and Skip for that dose only:** logging one dose from a reminder no longer marks the others as taken. The others stay open until you answer them.
- **Reminders name the day:** a reminder reads, for example, "Medication due Fri 30 Oct, 8:00". One left unanswered for more than a day is cleared, and the dose stays on Medications.

## Improved

- **Alarms on the minute:** medication reminders, their snoozes and follow-ups, logging reminders and the rest timer now use the phone's alarm-clock alarms, which ring on time even when the phone is idle. Android shows its alarm icon in the status bar while one is set, and some lock screens show the next alarm's time. No medication name is shown there.
- **Nothing to allow on Android 13 and later:** FlexNotes can ring at exact times without asking. On Android 12, Settings still offers to allow it if it has been switched off.
- **Rest timer with the screen off:** a rest now ends on a real alarm, so its sound and vibration come on time even if the phone has gone to sleep.
- **Injection sites:** each Taken button names the site it will record, and two doses of the same injected medication always suggest different sites, including after a snooze, for a dose left from yesterday, and from the home-screen widget.

## Fixed

- **No reminders for times already past:** starting, resuming, editing or restoring a schedule no longer sends reminders for doses whose time had already passed that day.
- **Reminders aren't lost:** a reminder that comes while FlexNotes can't load or save its data tries again shortly afterwards, and the next reminder is always set. A reminder missed while the phone was switched off appears when it's switched back on, if it's within 12 hours.
- **Two reminders in the same minute,** such as a follow-up and a new dose, now both appear.
- **Snoozing several at once** brings each one back at the time you chose.
- **Clock and time zone changes:** reminders set in the hour when clocks go back no longer repeat, and a time zone change while the app is open no longer makes a reminder ring too early or not at all.
- **If saving fails,** a reminder's Taken, Snooze or Skip keeps the reminder showing so you can try again.

## Your data

- No database change. Your workouts, measurements, photos, medications and settings are kept, and older backups still restore.

## Known limitations

- Do Not Disturb and silent mode can still silence a reminder that rings on time. A choice to let dose reminders sound through is planned (#347).
- A switched-off phone can't ring. A dose whose time passed while it was off is reminded when the phone is back on, if it's within 12 hours.
- On the night clocks go back, a snooze or follow-up that crosses the end of the repeated hour can ring up to an hour late (#352).
- Switching on Lock Medications while reminders are showing leaves their names visible until each is answered (#350).
