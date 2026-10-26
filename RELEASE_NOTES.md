# FlexNotes 1.0.177

## Overview

Your sports now get their own calorie estimate. Pick your sport for an exercise, and FlexNotes uses that sport's figures. It also adjusts the estimate for how hard the session was and how many minutes were live. Sessions now show the total they burned and how much of that was above resting. Sets in one card also share one layout at large font sizes.

## What's new

- **Sports and martial arts in the Activity list.** An exercise's Activity can now be Brazilian jiu-jitsu, judo, wrestling, boxing, Muay Thai / kickboxing, MMA, karate / taekwondo, grappling drills, technical sparring, team sports, court and racket sports, fencing, or CrossFit / Hyrox. The list is grouped by kind of activity, and you can search it.
- **The built-in sports know what they are.** Brazilian Jiu-Jitsu, Boxing, Tennis, Football and the other ready-made sports from 1.0.176 now have their matching Activity set. If you had already chosen an Activity for one, your choice is kept.
- **Total and above-resting calories.** The Calories sheet lists each exercise as "X kcal (Y above resting)", and a session on the day log shows the same line under its sets. The first figure is everything the session burned. The second leaves out what you would have burned at rest anyway. Only the second is added to your day, so nothing is counted twice.

## Improved

- **Calories follow how hard you trained.** A sport session's calories go up or down with the intensity you log: Technical, Class, Hard live or Fight pace. If you logged a cardio zone instead, the zone is used. Hard or live minutes count at the sport's harder pace, and the rest of the session counts at its normal pace.

## Fixed

- **Sets in one card now line up the same way.** At large font sizes, one wide set could stack its weight and reps while the sets around it stayed on one line. Now, if one set in a card has to stack, they all do. This applies on the day log, the exercise's Track tab and its History.

## Your data

- No database change. Your sets, exercises, workouts and settings are kept, and older backups still restore. Exercises you set to an Activity before keep it, and so does the old "Sports, general".

## Known limitations

- Calorie figures are estimates from published averages for each sport, scaled to your logged bodyweight. They need your profile and a bodyweight logged within two weeks.
- Rounds for sparring and bag work, and per-sport counts such as takedowns or points, come in the next update (issues #246 and #247).
- The rest timer in the notification, a review of every screen and a code clean-up follow after that (issues #250, #263 and #264).
