package com.fitlens.companion.ui

import android.app.Application
import com.fitlens.companion.R
import com.fitlens.companion.data.MeasurementGoals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The graph key says what its colours mean for the graph (#330). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h3200dp-mdpi")
class ChartKeyModeTest : FunctionTest() {

    @Test
    fun theModeFollowsWhichWayIsGood() {
        assertEquals(ChartKeyMode.UP, chartKeyMode(::higherIsBetter))
        assertEquals(ChartKeyMode.DOWN, chartKeyMode(::lowerIsBetter))
        assertEquals(ChartKeyMode.DOWN, chartKeyMode { f, t -> MeasurementGoals.verdict(MeasurementGoals.DECREASE, 0.0, f, t, false) })
        assertEquals(ChartKeyMode.UP, chartKeyMode { f, t -> MeasurementGoals.verdict(MeasurementGoals.INCREASE, 0.0, f, t, false) })
        assertEquals(ChartKeyMode.NONE, chartKeyMode { f, t -> MeasurementGoals.verdict(MeasurementGoals.NONE, 0.0, f, t, true) })
        assertEquals(ChartKeyMode.NONE, chartKeyMode { _, _ -> null })
    }

    @Test
    fun theBestPointFollowsTheRuleAndThereIsNoneWithoutOne() {
        val points = listOf(ChartPoint(1L, 80.0, "a"), ChartPoint(2L, 90.0, "b"), ChartPoint(3L, 85.0, "c"))
        assertEquals(2L, bestPoint(points, ::higherIsBetter)?.x)
        assertEquals(1L, bestPoint(points, ::lowerIsBetter)?.x)
        assertNull(bestPoint(points) { _, _ -> null })
        assertNull(bestPoint(points) { f, t -> MeasurementGoals.verdict(MeasurementGoals.NONE, 0.0, f, t, true) })
        assertEquals(2L, bestPoint(points) { f, t -> MeasurementGoals.verdict(MeasurementGoals.NONE, 0.0, f, t, false) }?.x)
    }

    @Test
    fun aSpecificValueKeySaysCloserAndFurther() {
        openScreen(LongHistoryFixture.snapshot()) { ChartKey(true, true, ChartKeyMode.TARGET) }
        assertShows(app.getString(R.string.ch_key_closer))
        assertShows(app.getString(R.string.ch_key_further))
        assertNotShows(app.getString(R.string.ch_key_better))
    }

    @Test
    fun aDownIsGoodKeyStillSaysBetterAndWorse() {
        openScreen(LongHistoryFixture.snapshot()) { ChartKey(true, true, ChartKeyMode.DOWN) }
        assertShows(app.getString(R.string.ch_key_better))
        assertShows(app.getString(R.string.ch_key_worse))
        assertShows(app.getString(R.string.ch_key_best))
        assertNotShows(app.getString(R.string.ch_key_closer))
    }

    @Test
    fun aKeyWithNoColoursLeavesOutBetterWorseAndBest() {
        openScreen(LongHistoryFixture.snapshot()) { ChartKey(true, true, ChartKeyMode.NONE) }
        assertNotShows(app.getString(R.string.ch_key_better))
        assertNotShows(app.getString(R.string.ch_key_worse))
        assertNotShows(app.getString(R.string.ch_key_best))
        assertShows(app.getString(R.string.ch_key_latest))
    }

    @Test
    fun theKeyNamesWhatIsBetterAndWorseWhenLowerIsGood() {
        openScreen(LongHistoryFixture.snapshot()) { ChartKey(true, true, ChartKeyMode.DOWN) }
        assertShows(app.getString(R.string.ch_key_better_lower))
        assertShows(app.getString(R.string.ch_key_worse_higher))
        assertNotShows(app.getString(R.string.ch_key_better_higher))
    }

    @Test
    fun theKeyNamesWhatIsBetterAndWorseWhenHigherIsGood() {
        openScreen(LongHistoryFixture.snapshot()) { ChartKey(true, true, ChartKeyMode.UP) }
        assertShows(app.getString(R.string.ch_key_better_higher))
        assertShows(app.getString(R.string.ch_key_worse_lower))
        assertNotShows(app.getString(R.string.ch_key_better_lower))
    }

    @Test
    fun theKeyForABarChartStillNamesCloserAndFurther() {
        openScreen(LongHistoryFixture.snapshot()) { ChartKey(true, false, ChartKeyMode.TARGET, bars = true) }
        assertShows(app.getString(R.string.ch_key_closer))
        assertShows(app.getString(R.string.ch_key_further))
        assertNotShows(app.getString(R.string.ch_key_latest))
    }
}
