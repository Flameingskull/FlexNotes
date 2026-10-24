## Overview

This update makes Medications fit more kinds of treatment. A new schedule now starts with one simple question,
"How do you take it?", instead of phases and cycles. Doses can taper down or step up over time on their own. Medicines
you take only when needed, such as pain relief or an inhaler, now have their own place on Today with a one-tap
**Take** button.

## What's new

- **Simple schedule setup.** **New schedule** first asks how you take it: **Every day**, **On set days of the week**,
  **Every few days** (each with no end date) or **In cycles** (a stretch on, then a break, repeating). FlexNotes
  builds the schedule and opens the dose straight away, already set to those days. Everything can still be changed
  in the schedule editor afterwards.
- **Tapering and step-up doses.** In a dose's sheet, **Change over time** can be **Taper down** or **Step up**: by
  how much, every how many days, and the lowest (or highest) it goes. A preview shows the amounts, for example
  "20 → 15 → 10 mg". Today, reminders, the supply forecast, the emergency card, CSV files and the report for a doctor
  all use the right amount for each day. A pause holds the taper back, and a dose change on Today still overrides it.
- **As-needed medications.** From a medication's ⋮ menu, **Taken as needed…** marks it as taken only when needed,
  with an optional most a day. Today lists these under **As needed**, with the time since your last dose, how many
  you've had today and a **Take** button that logs your usual amount at once, with Undo. Once today's most is reached,
  FlexNotes asks before logging another.
- **As needed in the doctor's report.** The Medications report gives as-needed medications their own section: how
  many times each was taken, on how many days, the total and the most in one day. They aren't counted in adherence.

## Improved

- The Medications screen opens on **Today** when you have an as-needed medication, as well as when a dose is due.
- A tapering dose's row in the schedule editor and its sheet on Today say how it changes, for example "Down 5 mg
  every 7 days, to 10 mg".

## Known limitations

- A taper counts from the start of its phase and starts again in each phase of a repeating cycle.
- **Take** repeats the amount of your last logged dose. To log a different amount, or the very first dose, use the
  medication's own page.
- As-needed medications don't have reminders.
