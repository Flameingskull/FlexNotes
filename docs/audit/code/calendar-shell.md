# Code audit: calendar-shell

> No full audit yet: rows from build gates only.
> Build gate 1.0.198: 2 functions checked, re-checked after fix rounds 1 and 2.
> Build gate 1.0.199: 5 functions checked (CalendarScreen, DayCell, Screen.Workouts, ScreenContent, the Today button), on the working tree over 527d379, before CI run 41925517281 finished.
> Audited 2026-10-31 against 1.0.198 (working tree on 830f7e1, nothing committed). Files read in full: 0 of 3 in scope; the rest of the area was not read.
> Results: 4 Verified, 5 Read OK, 1 Defect, 1 Risk, 2 Improve, 1 Not checked.
> `Verified` names a test that would fail if this broke. For 1.0.199 those tests had not yet run when the rows were written: a green release confirms them.

## Files

| File | Read in full |
| --- | --- |
| ui/TodayWidget.kt | No (build gate: changed functions only) |
| ui/CalendarScreen.kt | No (build gate: changed functions only) |
| ui/MainActivity.kt | No (build gate: changed functions only) |

## Checks

| Function, class or concern | Check | Where | Result | Evidence | Issue |
| --- | --- | --- | --- | --- | --- |
| TodayWidgetActions.handle: time from the shared clock | Dates and time | TodayWidget.kt:204 | Read OK | Same value, now through the seam; ends in MedReminders.arm, whose clearStale removes the dose's notification. No test presses the widget | |
| TodayWidgetActions: no catch around the work | Failure | TodayWidget.kt:258 | Risk | Already there, not in this round. An exception in handle (a full disk on take) is uncaught in a plain CoroutineScope, so the process closes with no message. The receivers changed in this build now catch theirs | #354 |
| CalendarScreen: first month from the clock seam | Dates and time | CalendarScreen.kt:94 | Verified | Was LocalDate.now(), so the month and the selected day could disagree with a test clock or across midnight. CalendarScreenTest.aSelectedPhotoDayKeepsItsPhotoAndASecondTapOpensTheDay asserts the fixed clock's month (Oct 2026), which LocalDate.now() would not give on CI | #329 |
| CalendarScreen: Today button from the clock seam | Dates and time | CalendarScreen.kt:118 | Read OK | Same change as the first month; the button isn't pressed by any test. `today` is still read once per composition, as before | #329 |
| DayCell: a selected photo day keeps its thumbnail and dimming layer | Logic | CalendarScreen.kt:312 | Verified | The `!isSelected` guard and the GoldDusk fill are gone; the square keeps its unselected background. CalendarScreenTest counts two `cal_photo` layers while today (a photo day) is selected, then while the 20th and then a plain day are; the old code would give 1 | #329 |
| DayCell: TalkBack says selected; a second tap opens the day | Android lifecycle | CalendarScreen.kt:310 | Verified | `selected` semantics unchanged. CalendarScreenTest asserts assertIsSelected / assertIsNotSelected on each tap and Screen.Day(plainDay) on the second tap through the real NavHost | #329 |
| DayCell: outline on every kind of day; selected, today and both told apart without colour | Project rules | CalendarScreen.kt:336 | Not checked | Drawn last, so it sits over the photo and its scrim. Selected alone is a 3dp GoldLight edge, today alone a 2dp Gold edge: apart from the shade they differ by 1dp (one pixel at mdpi). Both shows two rings, 4dp apart. Whether that reads at a glance needs a phone or a golden: there is no calendar golden, and no test looks at the outline at all | #329 |
| DayCell: outline colour | Project rules | CalendarScreen.kt:337 | Improve | `Brand.GoldLight` and `Brand.Gold` directly. #329's decision is that the outline comes from the theme so it follows a later colour scheme; the theme's `colorScheme.secondary` is GoldLight. Using the scheme role buys that, at no visual change today | #329 |
| DayCell: today's ring now inside the dimming layer | Logic | CalendarScreen.kt:308 | Read OK | The ring moved from a border before `.alpha` to a child, so on a filtered calendar a non-matching today now fades to 30% with its square, where its ring used to stay full. Consistent with the other marks; noted as a change, not a fault | #329 |
| DayCell: the merged description also reads the photo's own label | Security and privacy / accessibility | CalendarScreen.kt:310, Components.kt:187 | Defect | Already there for unselected photo days; this build extends it to the selected one. The cell sets `contentDescription = spoken` and merges descendants, and ContentDescription merges by appending, so the AsyncImage's "Progress photo 2026-10-27 Front" is read after "…, photo, …, today": "photo" twice and a raw ISO date (owner rule: real dates). Trigger: TalkBack on any calendar day with a photo. Fix: `clearAndSetSemantics` on the cell, or a null description on the thumb inside it. Test: assert the merged cell's ContentDescription has one entry and no "Progress photo" | to file |
| CalendarScreen KDoc and indentation | Structure | CalendarScreen.kt:85, :182 | Improve | KDoc still says "the selected day filled gold dusk", which this build removed. The DayCell call block was re-indented by one stray space, so its braces no longer line up with the code around them | #329 |
| Screen.Workouts and its line in ScreenContent | Logic | MainActivity.kt:128, :643 | Verified | New `@Serializable data object` in the sealed Screen, routed to WorkoutsScreen. WorkoutsScreenTest.theMenuOpensTheWorkoutsListAndCreateOpensTheEditor reaches it through NavHarness's real NavHost and asserts nav.top == Screen.Workouts and the list drawn | #353 |
| Screen.Workouts after rotation or process death | Android lifecycle | MainActivity.kt:271 | Read OK | Encoded as JSON in the back stack like every screen; an object round-trips by its class name. Not driven by a test | #353 |
| ScreenContent made internal | Structure | MainActivity.kt:631 | Read OK | Only so NavHarness can call it; no behaviour change. See the tests ledger: the harness passes the live snapshot, not rememberScreenSnapshot's view | #353 |
