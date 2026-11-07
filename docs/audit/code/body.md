# Code audit: body

> No full audit yet: rows from build gates only.
> Build gate 1.0.199: 2 functions checked (#328, graph ranges; working tree over 527d379). Written by the main session from the auditor's report (the harness stopped the subagent writing files); re-checked after fix rounds 1 and 2.
> Build gate 1.0.200: 18 functions checked (#330, which way is good; working tree over e67bae8, before CI ran the tests named).
> Build gate 1.0.200, fix rounds 1 and 2 re-checked: the findings below and the rows they touch. CI on the round-1 tree compiled and passed 739 of 741 tests; CI on the round-2 tree (c4e8a86) passed 740 of 741, including every #330 test; the one failure is the known WholeScreen320Test.librarySearch (#349).
> Results: 14 Verified, 10 Read OK, 0 Defect, 0 Risk, 1 Improve, 0 Not checked.

`Verified` here names a test. A green release is what confirms it.

## Files

| File | Read in full |
| --- | --- |
| ui/BodyScreen.kt | No (build gate: changed functions only) |
| ui/BodyMeasurementScreen.kt | No (build gate: changed functions only) |
| ui/MeasurementGoals.kt | No (build gate: changed functions only) |

## Functions

| Function, class or concern | Check | Where | Result | Evidence | Issue |
| --- | --- | --- | --- | --- | --- |
| BodyGraphPane values filtered to the period | Logic | BodyScreen.kt:454-455 | Verified | BodyGraphRangeTest.eachRangeDrawsItsOwnPeriodFromToday | #328 |
| Empty range offers All time | Logic | BodyScreen.kt:519 | Verified | BodyGraphRangeTest.aRangeWithNothingInItSaysSoAndAllTimeBringsBackTheGraph | #328 |
| Full screen shows the empty-range panel | Logic | BodyScreen.kt:562 | Verified | BodyGraphRangeTest.anEmptyRangeShowsTheSamePanelInFullScreen | #328 |
| Overlay limited to the period's ends | Logic | BodyScreen.kt:466-468 | Read OK |  | #328 |
| Full-screen resetKey and period | Android lifecycle | BodyScreen.kt:542, :579 | Read OK |  | #328 |
| StatsBlock covers the shown values | Logic | BodyScreen.kt:597 | Read OK |  | #328 |
| Removing inRange left no callers | Structure | BodyScreen.kt:87-89 | Read OK | grep of app/src | #328 |
| measurementVerdict: one rule for every body figure | Logic | MeasurementGoals.kt:48 | Verified | MeasurementVerdictTest (down, up, null def, medication); delegates to MeasurementGoals.verdict (calendar-shell ledger). A null def has no direction | #330 |
| verdictColour: the one place a verdict becomes a colour | Project rules | MeasurementGoals.kt:65 | Verified | MeasurementVerdictTest.verdictColourMapsEachVerdict. Brand.Rise and Brand.Fall only | #330 |
| changeColour and plainWhenNoGoal follow the Settings choice at once | Android lifecycle | MeasurementGoals.kt:53, :61 | Verified | NoGoalColourTest.theChangeColourFollowsTheSettingAtOnce (recomposes after updatePortable), aMeasurementWithADirectionIgnoresThePlainChoice. Settings.portable is a StateFlow, so collectAsState needs no initial value | #330 |
| plainWhenNoGoal collected once per coloured row | Performance | MeasurementGoals.kt:53, :61; BodyScreen.kt:351, :432, :712; DayScreen.kt:653 | Improve | changeColour calls collectAsState for every visible change line (History, the day log, Track rows), each its own collector on the same flow. Lazy lists keep it to visible rows, so not slow today. Read the choice once per pane and pass it in, as BodyGraphPane and CompareScreen already do: one subscription, one value per frame | #370 |
| goalAnswer | Logic | MeasurementGoals.kt:107 | Verified | GoalSheetTest.theAnswerSaysWhichWayInAFewWords (Down, Up to 85 kg, Closer to 80 kg, Not set, null) | #330 |
| goalAnswer: a specific value stored with no target | Logic | MeasurementGoals.kt:105 | Verified | Fix round 1: TARGET with goalValue <= 0 now reads "Not set, tap to choose", agreeing with the colour rule. GoalSheetTest.aSpecificValueWithNoTargetReadsAsNotSetAndTheColourRuleAgrees. goalText (Settings, Measurements list) now delegates to goalAnswer, so it agrees too: theSettingsListSaysTheSameAsTheTrackLine | #330 |
| GoalLine reached from Track, History and Graph | Logic | MeasurementGoals.kt:123; BodyMeasurementScreen.kt:254; BodyScreen.kt:492, :673 | Verified | GoalSheetTest.theGoalLineOpensTheSheet; GoalReachTest.theTrackTabGoalLineOpensTheSheetAndDownIsGoodMakesAFallGood (saves DECREASE through the sheet into Room), theHistoryTabHasTheLine, theGraphTabHasTheLine, aMedicationGetsTheSameLine | #330 |
| GoalLine: touch target, label, rotation | Android lifecycle | MeasurementGoals.kt:126-136 | Read OK | 48dp minimum, onClickLabel from resources, `open` in rememberSaveable so the sheet survives rotation. Clickable Text without Role.Button: TalkBack says the action but not "button". Draws nothing for a null def; allMeasurements synthesises a def for a name seen only in values, and setMeasurementGoal ensures its row in the same transaction | #330 |
| MeasurementGoalSheet: the no-goal choice is saved before Save, and Cancel keeps it | Data safety | MeasurementGoals.kt:142-143, :155, :179-190 | Verified | Fix round 1: the chips set a local rememberSaveable seeded from the saved choice; onConfirm writes it only when Neither is the answer saved. GoalSheetTest.pickingPlainThenCancelLeavesTheSettingAlone, pickingPlainThenSaveSetsIt | #330 |
| MeasurementGoalSheet save path | Data safety | MeasurementGoals.kt:156-160; Store.kt:932 | Read OK | Unchanged by this build: one transaction, ensure then setGoal with edited = 1, so a later FitNotes import keeps it; the value is converted from the display unit. A medication always has its row (ensureMedication), so ensure never adds a body row for it | #330 |
| BodyGraphPane: the rule and the key mode | Logic | BodyScreen.kt:518-521, :527, :571 | Verified | GoalGraphTest draws the real pane with Down, Up, a specific value, Neither and Neither with Plain, and asserts the key's words and the Change verdict for each. Points on the canvas are covered only through moveTriangleColour (MoveMarkerTest) | #330 |
| The best star on a graph with no colours | Logic | Charts.kt:307, :347-361, :880 | Verified | Fix round 1: bestPoint returns null when the rule has no good or bad side, and the key drops Best in NONE mode. ChartKeyModeTest.theBestPointFollowsTheRuleAndThereIsNoneWithoutOne, aKeyWithNoColoursLeavesOutBetterWorseAndBest; GoalGraphTest.plainWithNoDirectionHasNoBestNoBetterAndNoVerdict. A target rule never probes as NONE (1 to 0 is never equal), so a specific value keeps its star | #330 |
| Graph markers lose their direction when Plain | Project rules | Charts.kt:253, :866 | Verified | Fix round 1 kept the triangle for a changed value with no verdict; round 2 colours it with the `neutral` argument, which the plot fills with textColor (colorScheme.onSurfaceVariant, Charts.kt:591), not the series colour (the brand red, which read as worse). MoveMarkerTest.aMoveWithNoVerdictIsNeitherGreenNorRed asserts the function returns neutral and that neutral is not better, worse, the series colour or Brand.Fall. The call at :866 passing textColor is read, not driven: the test passes Brand.Muted itself | #330 |
| StatsBlock: Change and per-week colour | Logic | BodyScreen.kt:629-660 | Verified | First to last through measurementVerdict; perWeek has the sign of last - first, so one colour is right. Fix round 1 adds "Change: better/worse" as the figure's state description, so colour isn't the only signal for TalkBack either. GoalGraphTest asserts the state for Down, Up, a specific value and Plain | #330 |
| Track rows, History rows and the day log use the one rule | Structure | BodyScreen.kt:351, :432, :712; DayScreen.kt:653 | Read OK | grep of app/src/main: every body change colour goes through changeColour, the compare through measurementVerdict, the graph through `better`. No other Brand.Rise/Fall use for body values (Rows.kt's StatCard Trend has no caller; the PDF, video and share images draw no verdict colour) | #330 |
| Track tab: the goal text became GoalLine | Structure | BodyMeasurementScreen.kt:254; MeasurementGoals.kt:86 | Read OK | goalText is now "Which way is good? ..." through goalAnswer and is still used by MeasurementSettings.kt:145, a tappable row that opens the sheet, so "tap to choose" fits there. The older goal sheet (editingGoal, :323) stays as a second way in | #330 |
| New strings | Project rules | res/values/strings.xml | Read OK | Every new name is used; apostrophes escaped; no text in code; one values folder. Fix round 1 removed mg_increase, mg_increase_to, mg_decrease, mg_decrease_to, mg_target and mg_none: grep of app/src finds no reference left in code or tests | #330 |
| Stored data untouched | Data safety | (no database change) | Read OK | No schema change, so Db.VERSION stays; goal_type values and meanings are unchanged; the new preference is absent on old installs and reads as false (the old rise-green default) | #330 |
