# Function audit: calendar-shell

> No full audit yet: rows from build gates only.
> Build gate 1.0.198: 3 functions checked (re-checked after fix round 2).
> Audited 2026-10-31 against 1.0.198 (working tree over 830f7e1, before CI ran the tests named). Files read in full: 3 of the 3 this gate needed; not the area.
> Build gate 1.0.199: 11 functions checked (#329 the calendar's selected day, and the Workouts route for #353; working tree over 527d379, before CI ran the tests named). Re-checked after fix round 1: the golden, the outline colours and the today/selected distinction.
> Build gate 1.0.200: 2 functions checked (#330, the shared verdict in data/Models.kt; working tree over e67bae8, before CI ran the tests named).
> Build gate 1.0.201: 4 functions checked (#349, the top bar title refitting; working tree over 3adada9, before CI ran the tests named).
> Build gate 1.0.202: 2 functions checked (#371 equal distance from a specific value, in data/Models.kt; working tree over 3589faf, before CI ran the tests named).
> Results: 9 Proved, 10 Read OK, 0 Fails, 0 Gap, 1 Not checked.

## Files

| File | Read in full |
| --- | --- |
| App.kt | Yes |
| data/AppHooks.kt | Yes |
| ui/TodayWidget.kt | Yes |
| ui/MainActivity.kt | No (build gate: changed functions only) |
| ui/CalendarScreen.kt | No (build gate: changed functions only; `CalendarScreen` and `DayCell` read in full) |
| ui/design/TopBar.kt | No (build gate: changed functions only; `FitTopBar`, `iconsThatFit` and `FittedTitle` read in full) |
| data/Models.kt | No (build gate: changed functions only; `MeasurementGoals` read in full) |

## Functions

| Function | Expected | Where | Result | Evidence | Issue |
| --- | --- | --- | --- | --- | --- |
| Widget Taken | Logs that dose at the shared clock's time, says so, and doesn't repeat another open dose's site | TodayWidget.kt:204, 213 | Proved | MedReminderNotificationsTest.theWidgetsTakenButtonDoesNotRepeatYesterdaysUnansweredDosesSite, through `TodayWidgetActions`: the dose and its site. The recorded time and the message aren't asserted | |
| Widget Taken clears the notification | Only that dose's goes | TodayWidget.kt:231 | Read OK | `handle` → `MedReminders.arm` → `clearStale`. The same clearing is proved in the medications ledger, not through the widget. Its site clash is the medications ledger's site row | |
| Notification and alarm-icon intents carry a data URI | Never read as a file to import | MainActivity.kt:396, 415 | Read OK | `Intent(context, MainActivity)` has no action, so `handleIntent` returns at `else -> return` after the extras | |
| Select a day with a photo | Its thumbnail stays | CalendarScreen.kt:312-317 | Proved | CalendarScreenTest.aSelectedPhotoDayKeepsItsPhotoAndASecondTapOpensTheDay: two `cal_photo` layers while today (a photo day) is selected, after selecting the 20th (the other photo day) and after selecting a plain day. Fails on the old `!isSelected` condition | |
| The selected photo day's dimming, number and dots | The 45% black layer stays under the number and dots, so they read on any photo | CalendarScreen.kt:315, 319-333 | Read OK | The layer sits inside the tagged box, before the number and dot row, as for an unselected photo day. Nothing asserts what is drawn | |
| The selected square's background | The one it would have unselected (no gold dusk fill) | CalendarScreen.kt:306 | Read OK | `SurfaceHigh` for a workout day, else `Surface`, whatever `isSelected` | |
| The selected outline | On every kind of day: photo, workout, empty, today, dimmed by a filter | CalendarScreen.kt:337, 308 | Read OK | A 3dp border drawn last, over the photo, now in the theme's `colorScheme.secondary` (GoldLight) with today's ring in `primary` (Gold), as the issue decided; a selected dimmed day isn't faded (`dimmed && !isSelected`). The new goldens cover a selected photo day that is also today and a selected photo day alone; a selected workout day, an empty day and a filtered day have no shot | |
| Today, selected, and both | Told apart without relying on colour | CalendarScreen.kt:336-337 | Not checked | Today alone: 2dp ring at the edge. Selected alone: 3dp at the edge. Both: the today ring inset 4dp inside the outline. Today alone and selected alone differ by 1dp of width and a lighter gold. The new goldens show all three (`screen_calendar_*`: the 27th both; `screen_calendar_selected_photo_*`: the 20th selected, the 27th today only) but weren't recorded when this was written (CI run 42004052381 in progress). Settled by looking at those images | |
| Second tap on the selected day | Opens that day in the day log | CalendarScreen.kt:184 | Proved | Same test: `Screen.Day(plainDay)` on top after the second tap | |
| TalkBack on the selected day | Says selected | CalendarScreen.kt:310 | Proved | Same test: `assertIsSelected` on the chosen day and `assertIsNotSelected` on the one left | |
| Calendar opens on the phone's month | The month of today's clock, today selected | CalendarScreen.kt:92-94 | Proved | Same test, on the fixture's clock (27 Oct 2026): the month title shows and today is selected. `Dates.todayDate()` replaced `LocalDate.now()`, which ignored the clock source | |
| Top bar Today (home icon) | Back to today's month with today selected | CalendarScreen.kt:116-118 | Read OK | Same `Dates.todayDate()`. Not driven | |
| The calendar's golden, with a selected photo day | Updated, so how a selected photo day looks is recorded | WholeScreenTest.kt:241-246 | Read OK | Fix round 1: `calendar` (today, a photo day, selected) and `calendarAnotherPhotoDaySelected` (taps 20 Oct, the other photo day) at both widths and both font sizes, on the fixture's clock. A golden records the look; it proves only that later builds don't change it, not that it is right. Files not yet in `app/src/test/screenshots`: CI is recording them | #329 |
| Workouts route | `Screen.Workouts` survives the back stack's encoding and draws the Workouts screen | MainActivity.kt:128, 643 | Proved | WorkoutsScreenTest drives it through `NavHarness`, which uses `Nav.encode`/`Nav.decode` and `ScreenContent` as `AppRoot` does | |
| MeasurementGoals.verdict | One rule: a direction decides (closer to a specific value); none, or a specific value with no target, follows the Plain choice; equal values never coloured | Models.kt:443-450 | Proved | MeasurementGoalsTest (8 tests). 1.0.202 (#371): a specific value with a target now decides alone, so equal distance can't fall back to "a rise is good" |  |
| A move that ends as far from a specific value as it started | No colour: it came no closer and went no further | Models.kt:423-433, :447 | Proved | Fixed in 1.0.202: isImprovement gives null when the two distances differ by under 1e-9, and verdict returns it for a specific value without the rise fallback. MeasurementGoalsTest.equalDistanceEitherSideOfASpecificValueGetsNoColour (80 kg, 82 to 78 and back, both choices; 82 to 79 still green). Traced: 82.1 and 77.9 around 80 give equal floating distances. Side effect: the graph's best star, see the analysis ledger | #371 |
| Top bar title shrinks to fit | One line, a step smaller at a time down to 80% of the standard size; only a title still too long then ends in an ellipsis (#157, #325) | TopBar.kt:268-289 | Proved | Unchanged rule, now inside BoxWithConstraints(modifier). WholeScreen320Test and WholeScreenTest at 100% and 200% font report any new title cut short with an ellipsis; only #325's known titles are allowed. A green round-2 run confirms it |  |
| Title starts again from full size when its room changes | Rotating, icons moving into the ⋮ menu, or the library's categories then search giving a different width: the title refits to the new room instead of staying shrunk | TopBar.kt:272 | Read OK | scale is remembered on constraints.maxWidth. The width comes from the bar's title slot (TopAppBar) or the remaining Row space beside the ▾ (weight, fill = false), never from the title's own size, so a reset can't loop. No test changes the room and reads the title's size |  |
| Centred tab-screen titles | Still centred | TopBar.kt:114-118 | Read OK | The box wraps the text, so the Column's centring is unchanged. The ellipsis checks cover these screens; no test reads the position |  |
| Library title with its ▾ switcher | The name shrinks or ellipsizes and the ▾ stays visible | TopBar.kt:122-128 | Read OK | weight(1f, fill = false) moved from the Text to the box, a direct Row child, so the arrow keeps its room. #325's library names are the known ellipses in WholeScreenTest |  |
