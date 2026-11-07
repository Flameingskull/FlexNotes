package com.fitlens.companion.ui

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import com.fitlens.companion.R
import com.fitlens.companion.data.BodyPart
import com.fitlens.companion.data.MeasurementGoals
import com.fitlens.companion.data.PortableSettings
import com.fitlens.companion.data.Snapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** A measurement's Graph tab says, in its key and its Change figure, which way is good (#330). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h3200dp-mdpi")
class GoalGraphTest : FunctionTest() {

    private val f = LongHistoryFixture

    /** The fixture with Bodyweight's goal replaced. */
    private fun snapshotWith(goalType: Int, goalValue: Double = 0.0): Snapshot {
        val base = f.snapshot()
        val def = base.allMeasurements.first { it.name == f.BODYWEIGHT }.copy(goalType = goalType, goalValue = goalValue)
        val records = base.body.allRecords.filter { it.name == f.BODYWEIGHT }
        return Snapshot(base.library, base.setPart, base.notes, BodyPart(listOf(def), records, "kg"), base.photoPart, "kg", File("photos"))
    }

    private fun openGraph(goalType: Int, goalValue: Double = 0.0, plain: Boolean = false) {
        openScreen(snapshotWith(goalType, goalValue), preferences = PortableSettings(plainNoGoal = plain)) { s -> BodyGraphPane(s, nav, f.BODYWEIGHT) }
        compose.waitUntil(5_000) { shows(app.getString(R.string.ch_key_latest)) }
    }

    /** Whether some node's spoken state is [text] (the Change figure's verdict). */
    private fun changeSays(text: String): Boolean =
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)).fetchSemanticsNodes().isNotEmpty()

    private val better get() = app.getString(R.string.body_change_better)
    private val worse get() = app.getString(R.string.body_change_worse)

    @Test
    fun downIsGoodNamesLowerAsBetterAndAFallAsBetter() {
        openGraph(MeasurementGoals.DECREASE)
        assertShows(app.getString(R.string.ch_key_better_lower))
        assertShows(app.getString(R.string.ch_key_worse_higher))
        assertShows(app.getString(R.string.ch_key_best))
        assertTrue(changeSays(better))
        assertFalse(changeSays(worse))
    }

    @Test
    fun upIsGoodNamesHigherAsBetterAndAFallAsWorse() {
        openGraph(MeasurementGoals.INCREASE)
        assertShows(app.getString(R.string.ch_key_better_higher))
        assertTrue(changeSays(worse))
        assertFalse(changeSays(better))
    }

    @Test
    fun aSpecificValueKeyReadsCloserAndFurtherAndTheChangeIsCloser() {
        openGraph(MeasurementGoals.TARGET, 80.0)
        assertShows(app.getString(R.string.ch_key_closer))
        assertShows(app.getString(R.string.ch_key_further))
        assertNotShows(app.getString(R.string.ch_key_better))
        assertTrue(changeSays(better))
    }

    @Test
    fun plainWithNoDirectionHasNoBestNoBetterAndNoVerdict() {
        openGraph(MeasurementGoals.NONE, plain = true)
        assertNotShows(app.getString(R.string.ch_key_best))
        assertNotShows(app.getString(R.string.ch_key_better))
        assertFalse(changeSays(better))
        assertFalse(changeSays(worse))
    }

    @Test
    fun noDirectionByDefaultStillMeansHigherIsBetter() {
        openGraph(MeasurementGoals.NONE)
        assertShows(app.getString(R.string.ch_key_better_higher))
        assertShows(app.getString(R.string.ch_key_best))
        assertTrue(changeSays(worse))
    }
}