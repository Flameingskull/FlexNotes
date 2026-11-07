# Function audit: analysis

> No full audit yet: rows from build gates only.
> Build gate 1.0.199: 27 functions checked (#328, graph ranges; working tree over 527d379). Written by the main session from the auditor's report (the harness stopped the subagent writing files); re-checked after fix rounds 1 and 2.
> Build gate 1.0.200: 7 functions checked (#330, the graph key and trend colours; working tree over e67bae8, before CI ran the tests named).
> Fix rounds 1 and 2 (1.0.200): 2 rows re-checked or added.
> Results: 17 Proved, 16 Read OK, 0 Fails, 2 Gap, 0 Not checked. (Recounted over every row at 1.0.200; the 1.0.199 line had undercounted.)

`Proved` here names a test that drives the function. A green release is what confirms it.

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

| Function | Expected | Where | Result | Evidence | Issue |
| --- | --- | --- | --- | --- | --- |
| Range 1/3/6/12 months | Ends today, whole calendar months back; 31 Mar to 28 Feb | GraphRange.kt:26 | Proved | GraphRangeTest.eachRangeCountsBackWholeCalendarMonthsFromToday, aShortMonthIsClamped, theFourRangesAreDifferentPeriods, thePeriodUsesTheAppsClock |  |
| Range All time | No period; RANGE_ALL is the menu's last entry | GraphRange.kt:10, :26 | Proved | GraphRangeTest.allTimeHasNoPeriod; GraphRangeIndexTest |  |
| Entries in a period | Both ends kept | GraphRange.kt:41 | Proved | GraphRangeTest.inPeriodKeepsBothEndsAndNothingOutside, aValueFortyDaysAgoIsInThreeMonthsButNotOne |  |
| Range start ISO and day | Agree; All falls back to the first day | GraphRange.kt:33, :37 | Proved | GraphRangeTest.startIsoAndStartDayAgree |  |
| Axis spans the period | A year with 4 weeks of data draws as a year | Charts.kt:443 | Proved | Pure logic: ChartViewportTest.aPeriodWiderThanTheDataSetsTheAxis, zoomIsAFractionOfThePeriod. No screen test reads the drawn axis (#361) |  |
| Bar width | Bars never overlap, whatever the period | Charts.kt:431 (barWidthFor); ShareImages.kt:126 | Proved | BarWidthTest (4 tests) |  |
| Trend over the axis span | Same viewRange as the plot | Charts.kt:394, :533 | Read OK | ChartViewportTest.theTrendRangeAndThePlotRangeAreTheSame can't fail (#361) |  |
| Full screen zoom reset | Reset on a range change, kept on rotation | ChartViews.kt:387 | Proved | GraphRangeEmptyTest.fullScreenKeepsItsZoomUntilTheRangeChanges |  |
| Empty-range panel | Names the last day; Show all time works | ChartViews.kt:626 | Proved | GraphRangeEmptyTest.theEmptyRangeNamesTheLastLoggedDayAndOffersAllTime, withNoKnownDayItFallsBackToNoData |  |
| Analysis range start | Calendar months from today | AnalysisScreen.kt:287 | Proved | AnalysisRangeTest.eachRangeStartsAtItsCalendarMonthsBack |  |
| Analysis totals clock | Follows Dates.clockSource | Analysis.kt:105 | Proved | AnalysisRangeTest.totalsFollowTheAppsClockNotTheSystemOne, monthlyWorkoutTotalsRunFromTheRangeStartToTheCurrentMonth, allTimeMonthlyTotalsStartAtTheFirstLoggedMonth |  |
| Analysis graph spans the range | Zero-filled periods | AnalysisScreen.kt:415 | Read OK | Axis runs from the first period's start to the current period's start |  |
| Analysis per-year and first-period totals | A range shows only that range's training | Analysis.kt:111-130 | Gap | Already there: the first bucket starts at periodStart(from). A clipping fix was held back in round 2: it broke the compile and mislabelled the point | #363 |
| Analysis empty-range panel | Only when nothing is logged on or after the range start | AnalysisScreen.kt:318-322, :409-410 | Proved | AnalysisPanelTest.setsInTheRangeThatTotalZeroSayNothingToTotalNotNothingLogged |  |
| Analysis full screen reset | A range change resets the zoom | AnalysisScreen.kt:499 | Read OK | resetKey = rangeIdx |  |
| Analysis full screen with an empty range | Panel with All time | AnalysisScreen.kt:493-526 | Gap | Already there: a flat zero line | #363 |
| Analysis Duration, Each workout | Axis is the whole period; an empty range offers All time | AnalysisScreen.kt:548-560, :575, :608 | Read OK | period passed inline and in full screen; no test drives it |  |
| Analysis summary and trend | Same periods as the axis | AnalysisScreen.kt:466-476 | Read OK | Same totals as the chart |  |
| Pinned exercise card | Range from today, period axis | PinnedGraphs.kt:222-229, :295 | Read OK | No test drives a card with a range |  |
| Pinned totals card | Same rule as Analysis | PinnedGraphs.kt:227, :295 | Read OK | rangeFrom; period null |  |
| Pinned card summary | Within the range, range named | PinnedGraphs.kt:240-250 | Read OK |  |  |
| Pinned card empty range | Says so; All time reachable | PinnedGraphs.kt:242, :285 | Read OK | A tap opens the graph with the panel |  |
| Readiness score, ratio and monotony graphs | Calendar months, period axis | ReadinessTab.kt:178-181, :213, :266, :287 | Read OK | ReadinessRangeTest doesn't read the graphs' period |  |
| Readiness Effort against work done | Covers the range; All time from the first entry | ReadinessTab.kt:41 (weekCountFor), :292-296 | Proved | ReadinessRangeTest.theWeeklyGraphReachesBackToTheFirstLoggedWeekOnAllTime, shortRangesStillCoverAtLeastFourWeeks |  |
| Readiness empty range | Says so, offers All time | ReadinessTab.kt:210-216 | Read OK | Panel when the range is empty and an earlier score exists; no test drives it |  |
| Readiness today | App clock | ReadinessTab.kt:170 | Read OK |  |  |
| Shared image axis | The period's first and last day | ShareImages.kt:118, :140-143, :340 | Proved | ShareGraphAxisTest (4); the labels Read OK |  |
| chartKeyMode | A graph's rule gives Up, Down or no colours | Charts.kt:285 | Proved | ChartKeyModeTest.theModeFollowsWhichWayIsGood (pure logic) |  |
| Key when down is good | Better and Worse, with Better's triangle pointing down | Charts.kt:313-317 | Proved | ChartKeyModeTest.aDownIsGoodKeyStillSaysBetterAndWorse asserts the words; the triangle directions are Read OK |  |
| Key with no colours | No Better/Worse items | Charts.kt:313 | Proved | ChartKeyModeTest.aKeyWithNoColoursLeavesOutBetterAndWorse |  |
| Key for a specific value | Its symbols match the graph's marks and say closer or further | Charts.kt:322-334, :347-350 | Read OK | Fixed in round 1: Closer and Further draw the plot's own triangles (both directions) or the bar-top line, not circles. Words proved by ChartKeyModeTest.aSpecificValueKeySaysCloserAndFurther, theKeyForABarChartStillNamesCloserAndFurther and GoalGraphTest.aSpecificValueKeyReadsCloserAndFurtherAndTheChangeIsCloser; no test reads the drawn shapes |  |
| trendVerdict | The trend's fitted ends judged by the graph's own rule | Charts.kt:475 | Proved | TrendVerdictTest (5 tests: down good, default and Plain, specific value, pace, flat) |  |
| Trend figure colour on every graph | Follows each graph's rule | Charts.kt:440, :514 | Read OK | Sibling change in this build: a falling pace trend (TrainingScreen.kt:648, :702) is now green, and the Readiness ratio, monotony and effort trends (noBetter) are now plain where they were slope-coloured. Higher-is-better graphs unchanged. No screen test reads the colour |  |
| Pace graph key | Better points down for pace | TrainingScreen.kt:648; Charts.kt:437 | Read OK | chartKeyMode(lowerIsBetter) = DOWN; before this build Better always pointed up |  |
| Moves with no verdict on any single-series graph | Never green or red where no side is better (Readiness ratio, monotony, effort: ReadinessTab.kt:37) | Charts.kt:253-258, :866 | Read OK | Round 2: muted direction triangles, where they were plain markers before this build. MoveMarkerTest.aMoveWithNoVerdictIsNeitherGreenNorRed proves the colour rule; no Readiness screen test reads it |  |
