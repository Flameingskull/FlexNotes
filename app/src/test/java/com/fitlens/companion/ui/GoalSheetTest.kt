package com.fitlens.companion.ui

import android.app.Application
import com.fitlens.companion.R
import com.fitlens.companion.data.MeasurementDef
import com.fitlens.companion.data.MeasurementGoals
import com.fitlens.companion.data.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The goal sheet asks "Which way is good?" and offers the no-direction colour choice inline (#330). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h3200dp-mdpi")
class GoalSheetTest : FunctionTest() {

    private fun open(goalType: Int) = openScreen(LongHistoryFixture.snapshot()) { snap ->
        MeasurementGoalSheet(snap.measurementDefs.first { it.name == LongHistoryFixture.BODYWEIGHT }.copy(goalType = goalType)) {}
    }

    @Test
    fun theSheetAsksWhichWayIsGood() {
        open(MeasurementGoals.INCREASE)
        assertShows(app.getString(R.string.mg_type_increase))
        assertShows(app.getString(R.string.mg_type_decrease))
        assertShows(app.getString(R.string.mg_type_target))
        assertShows(app.getString(R.string.mg_type_none))
        assertShows("Which way is good", substring = true)
        assertNotShows(app.getString(R.string.mg_plain_title))
    }

    @Test
    fun neitherShowsTheNoDirectionChoiceInline() {
        open(MeasurementGoals.INCREASE)
        tap(app.getString(R.string.mg_type_none))
        assertShows(app.getString(R.string.mg_plain_title))
        assertShows(app.getString(R.string.mg_plain_off))
        assertShows(app.getString(R.string.mg_plain_on))
        assertShows(app.getString(R.string.mg_plain_note))
    }

    @Test
    fun pickingPlainThenCancelLeavesTheSettingAlone() {
        open(MeasurementGoals.NONE)
        assertFalse(Settings.portable.value.plainNoGoal)
        tap(app.getString(R.string.mg_plain_on))
        compose.waitForIdle()
        assertFalse(Settings.portable.value.plainNoGoal)
        tap(app.getString(R.string.cancel))
        compose.waitForIdle()
        assertFalse(Settings.portable.value.plainNoGoal)
    }

    @Test
    fun pickingPlainThenSaveSetsIt() {
        open(MeasurementGoals.NONE)
        tap(app.getString(R.string.mg_plain_on))
        tap(app.getString(R.string.goals_save))
        compose.waitForIdle()
        assertTrue(Settings.portable.value.plainNoGoal)
    }

    @Test
    fun aDirectionHidesTheChoiceAgain() {
        open(MeasurementGoals.NONE)
        assertShows(app.getString(R.string.mg_plain_title))
        tap(app.getString(R.string.mg_type_decrease))
        assertNotShows(app.getString(R.string.mg_plain_title))
    }

    @Test
    fun theAnswerSaysWhichWayInAFewWords() {
        val res = app.resources
        fun answer(type: Int, value: Double) = goalAnswer(res, MeasurementDef("Weight", "kg", 0, type, value, true))
        assertEquals("Down", answer(MeasurementGoals.DECREASE, 0.0))
        assertEquals("Up, to 85 kg", answer(MeasurementGoals.INCREASE, 85.0))
        assertEquals("Closer to 80 kg", answer(MeasurementGoals.TARGET, 80.0))
        assertEquals("Not set, tap to choose", answer(MeasurementGoals.NONE, 0.0))
        assertEquals("Not set, tap to choose", goalAnswer(res, null))
    }

    @Test
    fun theGoalLineOpensTheSheet() {
        openScreen(LongHistoryFixture.snapshot()) { snap ->
            GoalLine(snap.measurementDefs.first { it.name == LongHistoryFixture.BODYWEIGHT })
        }
        assertShows("Which way is good? Not set", substring = true)
        tap("Which way is good?", substring = true)
        assertShows(app.getString(R.string.mg_type_decrease))
    }

    @Test
    fun theAnswersComeInTheOrderUpDownSpecificNeither() {
        open(MeasurementGoals.INCREASE)
        val shown = drawn()
        val order = listOf(R.string.mg_type_increase, R.string.mg_type_decrease, R.string.mg_type_target, R.string.mg_type_none)
            .map { shown.indexOf(app.getString(it)) }
        assertTrue("Every answer is shown: $order", order.all { it >= 0 })
        assertEquals(order.sorted(), order)
    }

    @Test
    fun aSpecificValueWithNoTargetReadsAsNotSetAndTheColourRuleAgrees() {
        val res = app.resources
        assertEquals(app.getString(R.string.mg_answer_none), goalAnswer(res, MeasurementDef("Weight", "kg", 0, MeasurementGoals.TARGET, 0.0, true)))
        assertEquals(
            MeasurementGoals.verdict(MeasurementGoals.NONE, 0.0, 80.0, 81.0, false),
            MeasurementGoals.verdict(MeasurementGoals.TARGET, 0.0, 80.0, 81.0, false)
        )
        assertEquals(
            MeasurementGoals.verdict(MeasurementGoals.NONE, 0.0, 80.0, 81.0, true),
            MeasurementGoals.verdict(MeasurementGoals.TARGET, 0.0, 80.0, 81.0, true)
        )
        assertEquals("Closer to 80 kg", goalAnswer(res, MeasurementDef("Weight", "kg", 0, MeasurementGoals.TARGET, 80.0, true)))
    }

    @Test
    fun theSettingsListSaysTheSameAsTheTrackLine() {
        val res = app.resources
        val down = MeasurementDef("Weight", "kg", 0, MeasurementGoals.DECREASE, 80.0, true)
        assertEquals("Which way is good? Down, to 80 kg", goalText(res, down))
        assertEquals(app.getString(R.string.mg_ask, goalAnswer(res, down)), goalText(res, down))
        assertEquals("Which way is good? Not set, tap to choose", goalText(res, null))
    }
}
