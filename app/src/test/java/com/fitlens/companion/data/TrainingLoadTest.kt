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

    private val session = { id: Long -> id == 9L }

    private fun sessionSet(id: Long, date: String, rpe: Double?, minutes: Int, exercise: Long = 9L) =
        SetRow(id, exercise, date, 0.0, 0, 0.0, minutes * 60, false, null, rpe = rpe)

    @Test
    fun sessionLoadIsRatingTimesMinutesAndSkipsUnrated() {
        val sets = listOf(
            sessionSet(1, "2026-10-01", 7.0, 60),
            sessionSet(2, "2026-10-01", 5.0, 30),
            // No rating: it doesn't count.
            sessionSet(3, "2026-10-01", null, 90),
            // A gym set is not a session.
            SetRow(4, 1, "2026-10-01", 100.0, 5, 0.0, 0, false, null, rpe = 9.0)
        )
        val day = Dates.epochDay("2026-10-01")
        assertEquals(570.0, TrainingLoad.daily(sets, TrainingLoad.Measure.SESSION, session)[day]!!, 1e-9)
        // Without knowing which exercises are sessions, nothing counts.
        assertTrue(TrainingLoad.daily(sets, TrainingLoad.Measure.SESSION).isEmpty())
        assertEquals(1, TrainingLoad.unrated(sets, session, day, day))
        assertEquals(0, TrainingLoad.unrated(sets, session, day + 1, day + 5))
    }

    @Test
    fun bandEdgesFollowTheRatioBands() {
        assertEquals(TrainingLoad.Band.LOW, TrainingLoad.band(0.79))
        assertEquals(TrainingLoad.Band.GOOD, TrainingLoad.band(0.8))
        assertEquals(TrainingLoad.Band.GOOD, TrainingLoad.band(1.3))
        assertEquals(TrainingLoad.Band.CAUTION, TrainingLoad.band(1.31))
        assertEquals(TrainingLoad.Band.CAUTION, TrainingLoad.band(1.5))
        assertEquals(TrainingLoad.Band.HIGH, TrainingLoad.band(1.51))
    }

    @Test
    fun dayToDayJumpOverFiftyPercentIsFlagged() {
        val load = mapOf(100L to 400.0, 103L to 590.0, 104L to 900.0, 110L to 1400.0)
        // 590 over 400 is up 47.5%: not flagged.
        assertNull(TrainingLoad.jumpOn(load, 103L))
        // 900 over 590 is up about 52.5%.
        assertEquals(0.525, TrainingLoad.jumpOn(load, 104L)!!, 0.001)
        // The comparison is with the previous training day, however long ago: 1400 over 900.
        assertEquals(0.5556, TrainingLoad.jumpOn(load, 110L)!!, 0.001)
        // The first day, and a day without training, have nothing to compare.
        assertNull(TrainingLoad.jumpOn(load, 100L))
        assertNull(TrainingLoad.jumpOn(load, 105L))
    }

    @Test
    fun weeklyInternalAndExternalLoad() {
        val day = Dates.epochDay("2026-10-26")
        val iso = { d: Long -> java.time.LocalDate.ofEpochDay(d).toString() }
        val snapSets = listOf(
            sessionSet(1, iso(day), 6.0, 60).copy(liveSec = 1200),
            sessionSet(2, iso(day - 8), 5.0, 40)
        )
        // Hard minutes never exceed the session's own length.
        assertEquals(20.0, SessionLoad.hardMinutes(snapSets[0].liveSec, snapSets[0].durationSec)!!, 1e-9)
        assertEquals(360.0, TrainingLoad.daily(snapSets, TrainingLoad.Measure.SESSION, session)[day]!!, 1e-9)
    }
}

