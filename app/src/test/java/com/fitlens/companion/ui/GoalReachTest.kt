package com.fitlens.companion.ui

import android.app.Application
import com.fitlens.companion.R
import com.fitlens.companion.data.MeasurementDef
import com.fitlens.companion.data.MeasurementGoals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** "Which way is good?" is reachable from the measurement's Track, History and Graph tabs and changes the verdict (#330). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h3200dp-mdpi")
class GoalReachTest : FunctionTest() {

    private fun openMeasurement(page: Int = 0) =
        openScreen(LongHistoryFixture.snapshot()) { snap -> NavHarness(nav, snap, Screen.BodyMeasurement(LongHistoryFixture.BODYWEIGHT, page)) }

    /** The goal as stored in the database, once the sheet's background save has landed. */
    private fun storedGoal(type: Int): MeasurementDef {
        repeat(100) {
            val d = stored().allMeasurements.firstOrNull { it.name == LongHistoryFixture.BODYWEIGHT }
            if (d != null && d.goalType == type) return d
            Thread.sleep(50)
        }
        throw AssertionError("The goal $type was not stored")
    }

    @Test
    fun theTrackTabGoalLineOpensTheSheetAndDownIsGoodMakesAFallGood() {
        openMeasurement()
        assertShows("Which way is good? Not set", substring = true)
        tap("Which way is good?", substring = true)
        tap(app.getString(R.string.mg_type_decrease))
        tap(app.getString(R.string.goals_save))
        val d = storedGoal(MeasurementGoals.DECREASE)
        assertEquals(true, measurementVerdict(d, false, 82.0, 81.0))
        assertEquals(false, measurementVerdict(d, false, 81.0, 82.0))
        show(stored())
        assertShows("Which way is good? Down", substring = true)
    }

    @Test
    fun theHistoryTabHasTheLine() {
        openMeasurement(page = 1)
        assertShows("Which way is good?", substring = true)
    }

    @Test
    fun theGraphTabHasTheLine() {
        openMeasurement(page = 2)
        assertShows("Which way is good?", substring = true)
    }

    @Test
    fun aMedicationGetsTheSameLine() {
        openScreen(LongHistoryFixture.snapshot().medicationView) { snap -> NavHarness(nav, snap, Screen.Medication(LongHistoryFixture.METFORMIN)) }
        assertShows("Which way is good?", substring = true)
    }
}
