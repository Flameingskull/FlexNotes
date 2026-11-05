# Code audit: analysis

> No full audit yet: rows from build gates only.
> Build gate 1.0.199: 19 functions checked (#328, graph ranges; working tree over 527d379). Written by the main session from the auditor's report (the harness stopped the subagent writing files); re-checked after fix rounds 1 and 2.
> Results: 10 Verified, 8 Read OK, 2 Defect, 1 Risk, 1 Improve, 1 Not checked.

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
