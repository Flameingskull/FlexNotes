# FlexNotes 1.0.181

## Overview

This build finishes sport counts and continues the screen-by-screen review, this time for workouts, training blocks and analysis. You can now plan your counts in a workout, follow them on a graph and take them out in a CSV export. More dates read with the weekday, and long values shorten neatly instead of being cut off.

## What's new

- **Plan your counts in a workout.** A Session or Rounds exercise's counts, such as Takedowns or Points, can now be set in a workout day's planned sets. The workout shows them in the exercise's summary (for example "3 Takedowns"), and logging the day fills them in.
- **A graph for each count.** A Session or Rounds exercise's Graph tab now ends with a graph for each of its counts, such as "Total Takedowns", showing each day's total. Days without the count draw no point. You can pin these graphs, compare them and make one the exercise's default.
- **Counts in the CSV export.** The workouts CSV now ends with four columns: each count's name and its number.

## Improved

- **Training blocks show dates.** An ongoing block reads "Maintain · since Mon 27 Oct" instead of a week count, on the day log, in the workout and on the home-screen widget. A plan's start and the next block's start also show the weekday.
- **Dates with the weekday in analysis.** The Load tab's week and spike notes, the trend summary under a graph, the bodyweight and body fat dates on the TDEE page and the spoken chart descriptions now read like "Mon 27 Oct". The year only appears when it isn't this year.
- **Long values stay tidy.** Long values in the set lists of Save as a workout day and Share, and in the pie chart's centre, now end with "…" rather than being clipped at large font sizes.

## Your data

- No database change. Your exercises, sets, workouts and settings are kept, and older backups still restore.

## Known limitations

- The workouts and routines review and the analysis review (issues #253 and #254) stay open until they've been checked on a phone at a large font size, as do the day log and exercise screen reviews (#251 and #252).
- Count graphs use the names you gave the counts. If two counts share a name, only one graph is shown.
- Next up: the review of body tracking, photos and the camera, medications, settings and the calendar (issues #255 to #258).
