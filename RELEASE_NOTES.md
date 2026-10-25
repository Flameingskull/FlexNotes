## Overview

Training blocks can now do more than remind you. A block, such as a deload week, can **change the planned sets**:
lighter or heavier weights by a percentage, and fewer or more sets per exercise. FlexNotes applies the change when you
log a day of that workout during the block, and every set stays yours to edit. This update also makes the automatic
checks behind each release steadier, so updates reach you without needless delays.

## What's new

- **Blocks that change your planned sets.** In a workout's editor, open a training block and switch on **Change the
  planned sets**. Set a **weight change in percent** (for example −10 for a lighter week, or +5 for a heavier one)
  and the **sets per exercise** to add or take away. For a deload block it starts at 10% lighter and one set fewer.
- **Applied when you log the day.** When you add a day of that workout during the block, with **Add workout** or the
  library's **Log all**, its planned weights and sets change before they're logged. Weights are rounded to the
  exercise's own weight step, in its own unit, and every exercise always keeps at least one set.
- **You see it before you log.** The review in **Add workout** names the block and what it changes, and every
  exercise shows the sets as they'll be logged. The message after logging says what was changed too.

## Improved

- The day log's training block card says what the current block does to your planned sets, for example "Logged
  workout days this block: weights 10% lighter, 1 set fewer per exercise".
- The workout editor's block list shows each block's change next to its length.
- Exercises timed or measured by distance take the set change only, and exercises with no planned sets stay
  without.
- The check that keeps saving a set fast with years of history now counts what each save reads back instead of
  timing it, so a busy build machine can no longer hold up a release.

## Known limitations

- The change applies to workout days logged in one go. Sets you type one at a time are logged exactly as you type them.
- Days logged before this update, or before you switched the change on, keep the sets they were logged with.
- The accessibility checks that need a real phone (TalkBack, switch access and very large text on whole screens)
  are still to be done by hand.
