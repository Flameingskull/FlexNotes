# FlexNotes 1.0.199

## Overview

This update fixes three problems you reported. Your saved workouts have their own screen again, graph time ranges now show exactly the period you choose, and the calendar keeps a day's photo when you select it.

## What's new

- **The Workouts screen is back:** open the day log's menu and choose Workouts to see every saved workout, with its number of days and exercises. Tap one to open it in the workout editor. Use + or **Create new workout** to start a new one, and each workout's menu offers Edit, Duplicate and Delete.
- **Undo really undoes:** deleting a workout, from the Workouts screen or the workout editor, asks first. Undo then puts back the very same workout, in its place in the list, with its days. The library's current workout and the next-day suggestion carry on as before.

## Improved

- **Graph ranges mean what they say:** 1 month, 3 months, 6 months and 1 year now end today and count back whole calendar months, on every graph: body values, medications, exercises, Analysis, Readiness and pinned graphs. All time starts at your first logged value.
- **The whole period on the axis:** a graph now spans the full range you pick, even when you have only a few values in it, so a year with four weeks of data looks like a year.
- **Nothing logged in a range?** The graph says so, names the day you last logged, and offers **Show all time**. In full screen too, on body and exercise graphs.
- **Zoom resets with the range:** changing the range in full screen goes back to the whole graph, so an old zoom can't hide part of the new period.
- **Readiness on All time:** the weekly "Effort against work done" graph now goes back to your first entry. Before, it stopped after 52 weeks.
- **Bars stay apart:** bar charts size their bars to the gap between values, so a few bars spread over a long period no longer run into one block. Shared graph images follow the same period and spacing.

## Fixed

- **Calendar:** selecting a day with a photo no longer hides the photo or turns the square dark. The selected day is outlined in gold, and today keeps its own thin ring.
- **Analysis totals that only add up to zero,** such as duration for workouts without a timer, now say there's nothing to total, rather than "nothing logged".
- **Exercise graphs** no longer show "Nothing logged in this period" with a button that does nothing when the chosen graph has no values at all.

## Known limitations

- On Analysis, the first period of a range still counts the whole period. For example, "Workouts per year" on 1 month shows the year so far. A fix is planned.
- After changing a graph's range, a tap can occasionally select a different point from the one under your finger. Changing the range again or reopening the graph corrects it. A fix is planned.
- On the calendar at the largest font sizes, weekday headings and the legend can break mid-word.
- If All time still stops short on your graphs, please compare it with the History list and report what you see.

## Your data

No database change. Updating keeps all your workouts, sets, body values, photos and medications.
