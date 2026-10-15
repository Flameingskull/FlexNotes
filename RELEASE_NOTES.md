## Overview

This update is about speed and small screens. Saving a set now takes the same short time however many years of
history you have, and the workout screens fit properly on small phones with Android's largest font size.

Install it over the current app as usual. Everything you've logged is kept, and the database doesn't change.

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
