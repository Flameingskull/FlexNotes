package com.fitlens.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Training load (#217): the acute:chronic ratio, monotony and the spike warning. */
class TrainingLoadTest {

    private val end = 20_000L

    @Test
    fun steadyTrainingHasARatioOfOne() {
        // The same load every day for eight weeks.
        val load = (0 until 56).associate { (end - it) to 1000.0 }
        val today = TrainingLoad.series(load, end, end).single()
        assertEquals(1.0, today.ratio!!, 1e-9)
        assertFalse(today.spike)
    }

    @Test
    fun aHeavyWeekIsASpike() {
        val load = (0 until 56).associate { (end - it) to if (it < 7) 3000.0 else 1000.0 }
        val today = TrainingLoad.series(load, end, end).single()
        // Acute 3000; chronic (7 × 3000 + 21 × 1000) / 28 = 1500.
        assertEquals(2.0, today.ratio!!, 1e-9)
        assertTrue(today.spike)
    }

    @Test
    fun ratioWaitsForFourWeeksOfLogs() {
        val load = (0 until 10).associate { (end - it) to 1000.0 }
        assertNull(TrainingLoad.series(load, end, end).single().ratio)
    }

    @Test
    fun monotonyIsMeanOverDeviation() {
        // A week of 100, 0, 100, 0, 100, 0, 100: mean 400/7, sd ≈ 49.5.
        val load = (0 until 7).filter { it % 2 == 0 }.associate { (end - it) to 100.0 }
        val d = TrainingLoad.series(load, end, end).single()
        val mean = 400.0 / 7
        val sd = kotlin.math.sqrt((4 * (100 - mean) * (100 - mean) + 3 * mean * mean) / 7)
        assertEquals(mean / sd, d.monotony!!, 1e-9)
        assertEquals(d.monotony!! * 400, d.strain!!, 1e-6)
    }

    @Test
    fun identicalDaysHaveNoMonotony() {
        val load = (0 until 7).associate { (end - it) to 100.0 }
        assertNull(TrainingLoad.series(load, end, end).single().monotony)
    }

    @Test
    fun dailyLoadByMeasure() {
        val sets = listOf(
            SetRow(1, 1, "2026-10-01", 100.0, 5, 0.0, 0, false, null),
            SetRow(2, 1, "2026-10-01", 0.0, 0, 5000.0, 1500, false, null)
        )
        val day = Dates.epochDay("2026-10-01")
        assertEquals(500.0, TrainingLoad.daily(sets, TrainingLoad.Measure.VOLUME)[day]!!, 1e-9)
        assertEquals(2.0, TrainingLoad.daily(sets, TrainingLoad.Measure.SETS)[day]!!, 1e-9)
    }
}
