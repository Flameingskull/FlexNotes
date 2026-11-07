package com.fitlens.companion.ui

import com.fitlens.companion.data.MeasurementGoals
import com.fitlens.companion.ui.TrendLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrendVerdictTest {

    private fun line(slope: Double) = TrendLine(slope, 10.0, 80.0, 0.9, 5, 0L, 20L)

    @Test
    fun aFallingTrendIsGoodWhenDownIsGood() {
        val line1 = line(-0.5)
        val result = trendVerdict(line1) { f, t ->
            MeasurementGoals.verdict(MeasurementGoals.DECREASE, 0.0, f, t, false)
        }
        assertEquals(true, result)
        val line2 = line(0.5)
        val result2 = trendVerdict(line2) { f, t ->
            MeasurementGoals.verdict(MeasurementGoals.DECREASE, 0.0, f, t, false)
        }
        assertEquals(false, result2)
    }

    @Test
    fun aRisingTrendIsGoodByDefault() {
        val line1 = line(0.5)
        val result1 = trendVerdict(line1) { f, t ->
            MeasurementGoals.verdict(MeasurementGoals.NONE, 0.0, f, t, false)
        }
        assertEquals(true, result1)
        val line2 = line(0.5)
        val result2 = trendVerdict(line2) { f, t ->
            MeasurementGoals.verdict(MeasurementGoals.NONE, 0.0, f, t, true)
        }
        assertNull(result2)
    }

    @Test
    fun aTrendTowardsASpecificValueIsGood() {
        val line1 = line(-0.5)
        val result1 = trendVerdict(line1) { f, t ->
            MeasurementGoals.verdict(MeasurementGoals.TARGET, 70.0, f, t, false)
        }
        assertEquals(true, result1)
        val line2 = line(0.5)
        val result2 = trendVerdict(line2) { f, t ->
            MeasurementGoals.verdict(MeasurementGoals.TARGET, 70.0, f, t, false)
        }
        assertEquals(false, result2)
    }

    @Test
    fun lowerIsBetterMakesAFallingPaceGood() {
        val line1 = line(-0.5)
        val result1 = trendVerdict(line1, ::lowerIsBetter)
        assertEquals(true, result1)
        val line2 = line(0.5)
        val result2 = trendVerdict(line2, ::lowerIsBetter)
        assertEquals(false, result2)
    }

    @Test
    fun aFlatTrendHasNoColour() {
        val line = line(0.0)
        val result = trendVerdict(line, ::higherIsBetter)
        assertNull(result)
    }
}
