# Code audit: analysis

> No full audit yet: rows from build gates only.
> Build gate 1.0.199: 19 functions checked (#328, graph ranges; working tree over 527d379). Written by the main session from the auditor's report (the harness stopped the subagent writing files); re-checked after fix rounds 1 and 2.
> Build gate 1.0.200: 7 functions checked (#330, which way is good; working tree over e67bae8, before CI ran the tests named).
> Build gate 1.0.200, fix rounds 1 and 2 re-checked: the findings below and the rows they touch. CI on the round-1 tree compiled and passed 739 of 741 tests; CI on the round-2 tree (c4e8a86) passed 740 of 741, including every #330 test; the one failure is the known WholeScreen320Test.librarySearch (#349).
> Results: 14 Verified, 8 Read OK, 2 Defect, 1 Risk, 2 Improve, 1 Not checked.

`Verified` here names a test. A green release is what confirms it.

## Files

| File | Read in full |
| --- | --- |
| ui/GraphRange.kt | Yes |
| data/Analysis.kt | No (build gate: changed functions only) |
| ui/AnalysisScreen.kt | No (build gate: changed functions only) |
| ui/ChartViews.kt | No (build gate: changed functions only) |
| ui/Charts.kt | No (build gate: changed functions only) |
| ui/PinnedGraphs.kt | No (build gate: changed functions only) |
| ui/ReadinessTab.kt | No (build gate: changed functions only) |
| ui/ShareImages.kt | No (build gate: changed functions only) |

## Functions

| Function, class or concern | Check | Where | Result | Evidence | Issue |
| --- | --- | --- | --- | --- | --- |
| A tap picks a point against an old axis | Android lifecycle | Charts.kt:600, :613-614 | Defect | The pointerInput keys leave out period, xMin and xMax. With every value inside 3 months, switching 6 months to 3 months keeps series, lo and hi equal, so taps map to the old axis. Released by the owner's choice and filed | #364 |
| Analysis totals: the first bucket starts before the range | Logic | Analysis.kt:100-109 | Defect | Already there. Per year on 1 month is the whole year to date. The clipping fix was held back in round 2 | #363 |
| Analysis full-screen empty range | Logic | AnalysisScreen.kt:496-520 | Risk | Filed by the gate; not checked by the code-auditor | #363 |
| Bar width over a period | Logic | Charts.kt:427-436, :566-569; ShareImages.kt:126 | Verified | BarWidthTest.fourWeeklyBarsOnAYearAreNarrowerThanTheirSpacing, denseBarsAreAsNarrowAsTheClosestPair | #328 |
| WorkoutsTab range panel only when no set is in the range | Logic | AnalysisScreen.kt:318-322, :409-414 | Verified | AnalysisPanelTest.setsInTheRangeThatTotalZeroSayNothingToTotalNotNothingLogged; worked out off the main thread | #328 |
| DurationPerWorkout period and panel | Logic | AnalysisScreen.kt:541-621 | Read OK | Period passed inline and in full screen; panel only with an earlier timed workout. A first-bucket workout before from can show the panel under a non-zero total | #363 |
| Readiness weekly graph back to the first week | Logic | ReadinessTab.kt:41, :292-296 | Verified | ReadinessRangeTest.theWeeklyGraphReachesBackToTheFirstLoggedWeekOnAllTime, shortRangesStillCoverAtLeastFourWeeks | #328 |
| ReadinessTab range rule and empty panel | Logic | ReadinessTab.kt:174-221, :266, :287 | Read OK | App clock, rangeStartDay, period on every graph; not driven by a test | #328 |
| rangePeriod, rangeStartIso, rangeStartDay | Logic, Dates and time | GraphRange.kt:26-38 | Verified | GraphRangeTest | #328 |
| inPeriod | Logic | GraphRange.kt:41 | Verified | GraphRangeTest.inPeriodKeepsBothEndsAndNothingOutside | #328 |
| viewRange with a period | Logic | Charts.kt:443 | Verified | ChartViewportTest.aPeriodWiderThanTheDataSetsTheAxis, zoomIsAFractionOfThePeriod | #328 |
| graphAxisDays and the shared image's end labels | Logic | ShareImages.kt:118, :140-143, :340 | Verified | ShareGraphAxisTest | #328 |
| Analysis.totals reads the app's clock | Dates and time | Analysis.kt:105 | Verified | AnalysisRangeTest.totalsFollowTheAppsClockNotTheSystemOne | #328 |
| FullScreenChart resets zoom on a range change | Android lifecycle | ChartViews.kt:376, :387-393 | Verified | GraphRangeEmptyTest.fullScreenKeepsItsZoomUntilTheRangeChanges | #328 |
| GraphRangeEmpty | Project rules | ChartViews.kt:626-642 | Verified | GraphRangeEmptyTest.theEmptyRangeNamesTheLastLoggedDayAndOffersAllTime | #328 |
| ChartPeriod public | Build | GraphRange.kt:7 | Read OK | Needed by the public FitChart and GraphImage | #328 |
| FitChart and LinePlot pass the period | Logic | Charts.kt:381, :394, :407, :533 | Read OK | Trend and plot share one viewRange (the tap row above aside) | #328 |
| PinnedGraphCard | Dates and time | PinnedGraphs.kt:222-230, :295 | Read OK | Dates.today() in the cache key | #328 |
| RANGES' day counts are dead | Structure | BodyScreen.kt:87 | Improve | GraphRangeIndexTest guards RANGE_ALL == RANGES.lastIndex; the 30L to 0L values still mislead | #328 |
| Cause of All time stopping short | Logic | range path | Not checked | The Readiness cap and the full-screen zoom are fixed; no other limit found. Needs a phone with a multi-year import | #328 |
| chartKeyMode | Logic | Charts.kt:285 | Verified | ChartKeyModeTest.theModeFollowsWhichWayIsGood: higher, lower, DECREASE, INCREASE, plain and all-null rules | #330 |
| chartKeyMode infers the mode from one probe | Structure | Charts.kt:285, :307, :474 | Improve | better(1.0, 0.0) can't tell a specific value (1 to 0 against a target of 85 reads as UP), which is why BodyGraphPane must also pass keyMode. bestPoint now uses the same probe. A future caller with a target rule that forgets keyMode gets Better/Worse arrows over Closer/Further triangles. Passing the mode with the rule removes the second source | #370 |
| ChartKey: Closer/Further for a specific value, nothing for no colours | Project rules | Charts.kt:310-318 | Verified | ChartKeyModeTest.aSpecificValueKeySaysCloserAndFurther, aKeyWithNoColoursLeavesOutBetterAndWorse | #330 |
| ChartKey: arrows point down when down is good | Logic | Charts.kt:350-358 | Verified | The triangles and the new spoken words ("Better: lower", "Worse: higher") come from the same betterUp, so ChartKeyModeTest.theKeyNamesWhatIsBetterAndWorseWhenLowerIsGood and theKeyNamesWhatIsBetterAndWorseWhenHigherIsGood fail if it is wrong; GoalGraphTest asserts it on the real pane. The drawn direction itself is not compared | #330 |
| trendVerdict | Logic | Charts.kt:475 | Verified | TrendVerdictTest: down, default, plain, target, pace, flat | #330 |
| TrendReadout coloured by the graph's rule | Logic | Charts.kt:440, :514 | Read OK | Was the slope's sign. Same for higherIsBetter graphs; a rising pace trend is now red, which is right. Multi-series charts apply one rule to every series, as their points already did | #330 |
| FitChart keyMode parameter | Structure | Charts.kt:398, :437 | Read OK | Default null keeps every other caller (Analysis, Readiness, Training, pinned) on the inferred mode | #330 |
| ChartKey: symbols for bars and a specific value, spoken words | Android lifecycle | Charts.kt:316-330, :340 | Read OK | Fix round 1: the key draws the bar-top line on a bar chart and both triangles for a specific value, matching the plot. Each item merges into one node whose description is the fuller words, so TalkBack reads "Better: lower". ChartKeyModeTest.theKeyForABarChartStillNamesCloserAndFurther | #330 |
