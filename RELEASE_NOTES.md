## Overview

This update shows the calories you burn and need on every day in the day log. A new **Calories** card sits under
the day's body values: what you burned, from your resting energy, daily activity and the exercise logged that day,
and what the day needs for your goal. Tap it for the full breakdown. Each exercise can also choose its own
**Activity**, so its calories are more accurate.

Install it over the current app as usual. Everything you've logged is kept. The database gets one small upgrade,
an empty Activity for each exercise, which keeps every row.

## What's new

- **The Calories card on the day log**, on any date: "Burned ≈ 2,640 kcal · Need ≈ 2,240 kcal". Until your
  profile and a recent bodyweight are set, it says what's missing and opens your profile.
- **The calories breakdown.** Tap the card to see resting energy and its equation, daily activity, each exercise's
  calories with its time and how they were worked out, and the total burned. Below that is what the day needs.
- **Choose how each day's need is set,** right in the breakdown: **the same every day** (your daily target), or
  **follow each day's training** (what you burned that day, plus your goal's change), so a training day needs more
  than a rest day.
- **An Activity for each exercise.** In the exercise form, choose from weight training (light, moderate or
  vigorous), circuit training, bodyweight exercises, intervals, walking, running, cycling, rowing, elliptical, stair
  climber, swimming, sports, yoga or stretching, each with its value from the 2024 Compendium of Physical
  Activities. **Automatic** keeps working it out from your logged effort, distance and time.

## Improved

- **The TDEE calculator** uses each exercise's Activity in its training average.
- **Home Screen Settings › Show calories** hides the card if you'd rather not see it.
- **The README** on GitHub now describes everything added since the rename.

## Known limitations

- Every figure is an estimate. Exercise calories count only what's above resting, so they're lower than a fitness
  watch's total, and a strength session's time comes from the workout timer when it was used, otherwise from each set
  and its rest.
- Food intake and steps aren't tracked yet. Both are on the feature list (#176, #175).
