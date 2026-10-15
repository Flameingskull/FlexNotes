## Overview

This update makes FitNotes imports put every body value into your own units. Before, a measurement logged in
inches in FitNotes, or a bodyweight in pounds, could arrive as the same figure labelled centimetres or kilograms.
Now the import converts it for you, so you never have to go through your data correcting units by hand.

Install it over the current app as usual. Everything you've logged is kept, and the database doesn't change.

## Fixed

- **Imported body values are converted to your units.** A FitNotes import reads the unit each body value was logged
  in and compares it with the unit you use for that measurement in FlexNotes. When they differ (inches and
  centimetres, pounds and kilograms) the value is converted before it's recorded; when they match it's recorded as
  it is. An import never changes your units.
- **Repairing earlier imports.** Import your FitNotes backup again from Settings to convert values an earlier import
  brought in without their unit. Nothing is duplicated, and the import summary says how many values it converted.

## Known limitations

- Cardio distances from FitNotes are still imported as logged, without converting between kilometres and miles.
  That's next (#180).
