# FlexNotes 1.0.204

## Overview

This update stops sheets from losing what you typed. A drag down or a tap outside by accident no longer closes a sheet that holds unsaved text. The body value sheet can also go full screen, and it gains **Save and add another**, so you can enter several measurements in one go.

## What's new

- **Full-screen body value entry:** the sheet you log a body value or a medication dose on can go full screen. Tap the arrow beside its title, or drag up on its handle, title or buttons. Drag down, or tap the arrow again, to return to half height.
- **Save and add another:** when you log a new body value or medication dose, **Save and add another** saves it and keeps the sheet open with the value cleared, ready for the next one. The measurement, date and time stay as they were. Editing an existing value still shows Delete there.
- **Open body value entry full screen:** a new setting (off by default) opens the body value sheet full screen every time. Find it in Settings, or search "full screen".

## Fixed

- **Typed text is no longer lost by accident:** about 30 sheets where you type something now stay open on a drag down, a tap outside or Back while what you typed isn't saved. This covers comments, measurements, body fat, goals, reference ranges, calories, readiness, medication amounts, supply, symptoms, schedules, the medication report, workouts, rest times, training blocks, categories and typed settings. Use **Cancel** or **Save** to close them. A sheet you haven't changed still closes with a tap outside, as before.
- **New measurement names count as typed:** a new measurement or medication name typed on the body value sheet is kept like any other typed value.
- **Body fat estimates don't lock the sheet:** when the body fat sheet fills in an estimate from your tape measurements, you can still close it with a tap outside if you haven't changed anything.

## Known limitations

- Turning the phone while some sheets are open can still clear what you typed in them (the workout comment, workout time, Create workout, calories, the body fat calculator, the photo comment and a few settings) (#381).
- Sheets where you only tick or choose things, such as Copy workout and Share workout, still close on a tap outside (#384).
- Back with unsaved text is guarded, but not yet proved by an automated test on a real device (#385).
- Dragging the body value sheet's handle up, and Back while it's full screen, aren't yet proved by an automated test (#387). The arrow beside the title always works.
- Saving the medication Supply sheet without changing the amount can still restart the supply count (#382).
- Reminder schedules for photos and body measurements (#332) aren't in this update.
