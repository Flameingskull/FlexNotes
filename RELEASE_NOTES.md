## Overview

This update fixes body values imported from FitNotes in inches or pounds, which showed unconverted as centimetres or
kilograms. Saving a set now takes the same short time however many years of history you have, and the workout screens
fit properly on small phones with Android's largest font size.

Install it over the current app as usual. Everything you've logged is kept, and the database doesn't change.

## Fixed

- **Imported body values are converted to your units.** A FitNotes import now reads the unit each body value was
  logged in and compares it with the unit you use in FlexNotes for that measurement. When they differ (inches and
  centimetres, pounds and kilograms) the value is converted before it's recorded; when they match it's recorded as it
  is. Before, a neck logged in inches could show the same figure as centimetres. Your FlexNotes units are never
  changed by an import.
- **Repairing earlier imports.** Import your FitNotes backup again from Settings to convert values that came in
  without their unit. Nothing is duplicated, and the summary says how many it fixed.

## Improved

- **Saving a set stays fast as your history grows.** Saving, editing, deleting or ticking off a set, adding a set
  comment and reordering a day now refresh only the day and exercise you changed, instead of every set you've ever
  logged. Every build now times a set save with one year and then four years of history plus 2,000 photos, and fails
  if it slows down.
- **Set values at large font sizes.** When weight and reps don't fit side by side, they now stack, each
  right-aligned on its own line, instead of running into each other.
- **Exercise names wrap in full** on the day log instead of being cut short with "…".
- **The PR mark stays on one line** beside a set at large font sizes.
- **Tabs scroll at large font sizes**, so Track, History and Graph keep their full names.
- **Top bar titles shrink a little to fit**, so a date such as "Thursday, 2 Oct" stays whole on a small phone.
- **Set comments show up to four lines** at large font sizes, where each line holds half as much.

## Under the hood

- Every build now checks each of the app's building blocks at 320dp wide with the largest font: touch targets of
  at least 48dp, no text cut off or overlapping, nothing past the edge of the screen.

## Known limitations

- The TalkBack, switch access and contrast checks still need a pass on a real phone (#157 stays open for them).
- Settings › About › Save speed still shows how long your own set saves take, if you'd like to compare.
