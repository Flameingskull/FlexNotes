# FlexNotes 1.0.197

## Overview

This update makes logging easier on small phones and at large font sizes. Every day log and exercise screen is now drawn and checked automatically at the width of a small and a large phone, at normal and 200% font, and this release fixes what those checks found. The biggest change is on the exercise screen: the Track form now starts straight under the tabs.

## Improved

- **Track form at the top:** on the exercise screen, and on the Body tracker and Medications tabs, a short page now starts at the top. Before, an exercise with few sets showed its form lower down the screen, with a gap above it.
- **Long names in full:** long exercise, measurement and workout day names wrap instead of being cut short, on the day log, in the exercise library, in a workout's days and in the workout drawer.
- **Every figure shown:** a body value's change and a workout day's sets and rest now show in full at any font size, instead of ending in "...".
- **Comments in full:** set comments and an exercise's earlier notes are shown whole. At large fonts a set's comment and effort use the row's full width, so words no longer break in the middle.
- **Exercise stats at large fonts:** tiles go one per row, so labels stay whole and a figure such as 2425 kg keeps its unit.
- **Rest timer:** Pause and Resume have a row of their own, so they are easy to hit between sets. The rest length chips are bigger.
- **Easier to tap:** the comment, superset options and graph buttons are now the full 48dp size.

## Fixed

- **Records filter:** at large fonts on a small phone, the period choice on the Records tab was squeezed away and could not be reached. It now moves to its own line.
- **Exercise history:** a day's totals and Copy to today no longer squeeze each other into a narrow column.
- **Choices on two lines:** a long choice, such as a Session's intensity, no longer loses the corner of its text.

## Your data

- No database change. Your workouts, measurements, photos, medications and settings are kept, and older backups still restore.

## Known limitations

- The title at the top of the exercise screen and the library is still cut short when it's long. It's the next fix (#325).
- Set rows on the History tab are smaller than the 48dp touch size. A choice between Compact and Comfortable rows is planned (#326).
- These checks cover the day log, the exercise screen and the library so far. Workouts, Analysis, Body, Photos, Medications and Settings follow in the next builds, and the review (#251 to #258) closes after a short check on a real phone.
