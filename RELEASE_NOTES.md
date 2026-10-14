## Overview

This update adds your **Profile** and a **TDEE calculator**. The profile keeps your sex, date of birth, height and
daily activity in one place, for every calculation that needs them. The TDEE calculator works out how many calories
you burn on an average day from your profile and what you log. It then sets a daily calorie target and macros for
your goal.

Install it over the current app as usual. Everything you've logged is kept, and there's no database upgrade. Your
profile and target travel in your backups.

## What's new

- **Settings › Profile.** Set your sex, date of birth (your age is worked out for each date), height and daily
  activity outside training. Each activity level is described in plain words, from Mostly sitting to Heavy physical
  work. The Sex row on the main Settings list has moved here, and search finds each value.
- **The TDEE calculator**, from the Body tracker's ⋮ menu or Settings › Profile:
  - **Your details:** the profile, plus your latest bodyweight and body fat. Change any of them right there, or log
    a new value without leaving the screen. Anything missing is named.
  - **Result:** resting energy, using Mifflin-St Jeor, or Katch-McArdle when a body fat value from the last two
    weeks is logged (you can choose either). Then your daily activity, your training averaged over the last 28 days
    of logs, and the daily total.
  - **Goal:** maintain; lose 0.25 to 1% of your bodyweight a week, or 10, 20 or 25% under your total; recomposition;
    lean bulk; bulk; or a custom target. Each shows its calories and the expected weekly change. Targets below your
    resting energy, below a safe minimum, or losing more than 1% a week are flagged.
  - **Macros:** protein, fat and carbohydrate in grams and calories for the goal.
  - **Set as my target** saves the calories and macros, with Undo.

## Improved

- **The body fat calculator uses your profile height** when no Height measurement is logged.
- **Until two weeks of training are logged,** the calculator uses a planned week instead: sessions, minutes and
  effort, which you can change on the screen.

## Known limitations

- Every figure is an estimate. Resting energy equations are usually within about 10% for a person, so watch your
  bodyweight trend for two to three weeks and adjust.
- Exercise calories use effort (from RPE where logged) and, for walking, running and cycling, your distance and time.
  Choosing an activity for each exercise, and a daily calories card on the day log, come in the next update.
