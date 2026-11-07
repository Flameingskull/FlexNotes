# Function audit: body

> No full audit yet: rows from build gates only.
> Build gate 1.0.199: 9 functions checked (#328, graph ranges; working tree over 527d379). Written by the main session from the auditor's report (the harness stopped the subagent writing files); re-checked after fix rounds 1 and 2.
> Build gate 1.0.200: 25 functions checked (#330, which way is good; working tree over e67bae8, before CI ran the tests named).
> Fix rounds 1 and 2 (1.0.200): 8 rows re-checked or added.
> Results: 20 Proved, 15 Read OK, 0 Fails, 0 Gap, 0 Not checked.

`Proved` here names a test that drives the function. A green release is what confirms it.

## Files

| File | Read in full |
| --- | --- |
| ui/MeasurementGoals.kt | Yes |
| ui/BodyScreen.kt | No (build gate: changed functions only) |
| ui/BodyMeasurementScreen.kt | No (build gate: changed functions only; the top bar menu, Track tab and sheets read in full) |

## Functions

| Function | Expected | Where | Result | Evidence | Issue |
| --- | --- | --- | --- | --- | --- |
| Body graph ranges | Each range's own period from today | BodyScreen.kt:454-455 | Proved | BodyGraphRangeTest.eachRangeDrawsItsOwnPeriodFromToday, theOldDefinitionOfTheLatestValueNoLongerDecides |  |
| Body All time | First value to latest, FitNotes and typed | BodyScreen.kt:455 | Proved | BodyGraphRangeTest.eachRangeDrawsItsOwnPeriodFromToday (All step) |  |
| Body axis spans the period | Inline and full screen | BodyScreen.kt:535, :579 | Read OK | period passed |  |
| Body empty range | Panel with the last day, Show all time | BodyScreen.kt:519 | Proved | BodyGraphRangeTest.aRangeWithNothingInItSaysSoAndAllTimeBringsBackTheGraph |  |
| Body full screen with an empty range | Same panel as inline | BodyScreen.kt:562 | Proved | BodyGraphRangeTest.anEmptyRangeShowsTheSamePanelInFullScreen |  |
| Body stats block | Start, latest, change, low, high, entries in the range | BodyScreen.kt:593 | Read OK | Uses shown |  |
| Body trend | Over the period | BodyScreen.kt:530 | Read OK |  |  |
| Medication and symptom overlay | Within the period (medication graphs use this pane) | BodyScreen.kt:466-482 | Read OK |  |  |
| Body full screen reset | On a range change | BodyScreen.kt:542 | Read OK | resetKey = rangeIdx |  |
| measurementVerdict | A measurement's direction decides; none or a null def follows the Settings choice; a medication like any other | MeasurementGoals.kt:48 | Proved | MeasurementVerdictTest (5 tests) |  |
| changeColour follows the Settings choice at once | Rise green, fall red by default; plain (normal text) once Plain is chosen, without reopening; a direction ignores Plain | MeasurementGoals.kt:61 | Proved | NoGoalColourTest.theChangeColourFollowsTheSettingAtOnce (round 2: switches the shown preference with Settings.showWith, so no background save races the test database closing), aMeasurementWithADirectionIgnoresThePlainChoice. Failed in CI on the round-1 tree; the round-2 run confirms it |  |
| verdictColour | Green, red or the given plain colour | MeasurementGoals.kt:65 | Proved | MeasurementVerdictTest.verdictColourMapsEachVerdict |  |
| Track list row change colour | The latest change coloured by the shared rule | BodyScreen.kt:351 | Read OK | changeColour(m, prev, last); arrow and amount in changeText. No screen test asserts the colour |  |
| History (All) row change colour | Same rule per row | BodyScreen.kt:432 | Read OK | changeColour(def, prev, r) with def from the list shown |  |
| Measurement History change colour | Same rule per value | BodyScreen.kt:712 | Read OK | changeColour(def, prev, r) |  |
| Day log body value change colour | Same rule | DayScreen.kt:653 | Read OK | changeColour(def, prev, r), def from snap.allMeasurements. Already there: the day log's "previous" is the last value on an earlier date, the Track row's is the value before (same day possible), so two same-day values can colour differently on the two screens |  |
| Graph points and bar tops | Green/red triangles and bar tops by the shared rule; plain markers when Plain | BodyScreen.kt:518-519; Charts.kt:787, :825 | Read OK | better = measurementVerdict(def, plainWhenNoGoal()); inline and full screen both pass it. No test reads the drawn colours |  |
| Graph Best star with Plain chosen | No "Best" claim where no direction says what best is | Charts.kt:307-309, :359, :886 | Proved | Fixed in round 1: bestPoint is null when the rule has no side, and the key drops Best. ChartKeyModeTest.theBestPointFollowsTheRuleAndThereIsNoneWithoutOne, aKeyWithNoColoursLeavesOutBetterWorseAndBest; GoalGraphTest.plainWithNoDirectionHasNoBestNoBetterAndNoVerdict. The Readiness noBetter graphs lose their first-point star with it |  |
| Body graph key mode | Up/Down from the direction, Closer/Further for a specific value with a target, no Better/Worse when Plain | BodyScreen.kt:521, :527, :559 | Read OK | keyMode TARGET only when goalValue > 0, else derived from `better` (ChartKeyModeTest proves the derivation). The specific-value key itself fails: see the analysis ledger |  |
| Stats Change and per-week colour | First to last value judged by the shared rule; signed figure stays | BodyScreen.kt:634-635, :641, :653 | Read OK | One verdict for both; plain falls back to Color.Unspecified |  |
| Body trend figure colour | By the measurement's direction, closer to a specific value, plain when Plain | Charts.kt:514; BodyScreen.kt:519 | Read OK | trendVerdict(t, better) on the fitted ends; TrendVerdictTest proves the rule, not the body pane |  |
| "Which way is good?" line on the Track tab | Shows the answer, opens the sheet, updates after Save | BodyMeasurementScreen.kt:254; MeasurementGoals.kt:123 | Proved | GoalReachTest.theTrackTabGoalLineOpensTheSheetAndDownIsGoodMakesAFallGood (taps, saves Down, reads the stored goal, line says Down) |  |
| The line on History | Present and opens the sheet | BodyScreen.kt:673 | Proved | GoalReachTest.theHistoryTabHasTheLine (present); GoalSheetTest.theGoalLineOpensTheSheet (same composable opens) |  |
| The line on Graph | Present and opens the sheet | BodyScreen.kt:492 | Proved | GoalReachTest.theGraphTabHasTheLine; GoalSheetTest.theGoalLineOpensTheSheet |  |
| Medications get the same line and sheet | Medication screens keep working | BodyMeasurementScreen.kt:254 | Proved | GoalReachTest.aMedicationGetsTheSameLine (present only); MeasurementVerdictTest.aMedicationWithNoDirectionBehavesLikeAnyOther |  |
| goalAnswer | Up, Up to 85 kg, Down, Closer to 80 kg, Not set | MeasurementGoals.kt:107 | Proved | GoalSheetTest.theAnswerSaysWhichWayInAFewWords |  |
| goalAnswer for a specific value of 0 | Says what the colours do | MeasurementGoals.kt:105 | Proved | Fixed in round 1: a specific value with no target reads "Not set, tap to choose", as verdict treats it. GoalSheetTest.aSpecificValueWithNoTargetReadsAsNotSetAndTheColourRuleAgrees. Still already there: a specific value of 0 can't be chosen (sheet :150, Models.kt:425) |  |
| Sheet asks which way is good | Title asks; four answers | MeasurementGoals.kt:152, :163-167 | Proved | GoalSheetTest.theSheetAsksWhichWayIsGood |  |
| Sheet answer order | Up is good, Down is good, A specific value, Neither (the issue's decision) | MeasurementGoals.kt:160 | Proved | Fixed in round 1: explicit list. GoalSheetTest.theAnswersComeInTheOrderUpDownSpecificNeither |  |
| Sheet Save stores the direction | Stored for that measurement, every screen follows | MeasurementGoals.kt:156-161 | Proved | GoalReachTest.theTrackTabGoalLineOpensTheSheetAndDownIsGoodMakesAFallGood (storedGoal) |  |
| A goal set here survives a re-import | Not replaced by FitNotes | Daos.kt:138; FitNotesImporter.kt:531 | Read OK | setGoal marks edited = 1; the importer refreshes only edited == 0. Unchanged by this build; no test re-imports after a set goal |  |
| Neither shows the no-direction choice inline | Rise green/fall red or Plain, written on Save, untouched on Cancel, hidden for a direction | MeasurementGoals.kt:142-143, :155, :174-194 | Proved | Round 1 holds the choice until Save. GoalSheetTest.neitherShowsTheNoDirectionChoiceInline, pickingPlainThenCancelLeavesTheSettingAlone, pickingPlainThenSaveSetsIt, aDirectionHidesTheChoiceAgain |  |
| Settings › Measurements goal | Opens the same sheet, worded as the line | MeasurementSettings.kt:145, :204; MeasurementGoals.kt:85 | Proved | Same MeasurementGoalSheet; goalText now reads "Which way is good? …". GoalSheetTest.theSettingsListSaysTheSameAsTheTrackLine |  |
| Track tab menu Goal | Opens the same sheet | BodyMeasurementScreen.kt:178, :323 | Read OK | Unchanged path |  |
| Plain moves on the graph | With Plain and no direction, no green or red marks; the triangle still shows the direction | Charts.kt:253-258, :866 | Proved | Round 1 drew these in the series colour, Brand.Fall, the same red as "worse"; round 2 passes the muted text colour (onSurfaceVariant = Brand.Muted, Theme.kt:156). MoveMarkerTest.aMoveWithNoVerdictIsNeitherGreenNorRed, with the real chart colours; the plot's argument at :866 traced |  |
