package com.fitlens.companion.data

import com.fitlens.companion.data.Readiness.Item
import com.fitlens.companion.data.Readiness.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The readiness score (#216): the check-in, HRV and resting heart rate against the usual, and sleep. */
class ReadinessTest {

    private val day = 20_000L

    private fun inputs(
        checkIn: Map<Item, Double> = emptyMap(),
        hrv: Map<Long, Double> = emptyMap(),
        rhr: Map<Long, Double> = emptyMap(),
        sleep: Map<Long, Double> = emptyMap()
    ) = Readiness.Inputs(if (checkIn.isEmpty()) emptyMap() else mapOf(day to checkIn), hrv, rhr, sleep)

    /** [n] days of [v] before [day]. */
    private fun usual(n: Int, v: Double) = (1..n).associate { (day - it) to v }

    @Test
    fun nothingLoggedHasNoScore() {
        assertNull(Readiness.score(inputs(), day))
    }

    @Test
    fun ratingsCountTheRightWay() {
        assertEquals(1.0, Readiness.ratingScore(Item.ENERGY, 5.0), 1e-9)
        assertEquals(0.0, Readiness.ratingScore(Item.ENERGY, 1.0), 1e-9)
        assertEquals(1.0, Readiness.ratingScore(Item.SORENESS, 1.0), 1e-9)
        assertEquals(0.5, Readiness.ratingScore(Item.STRESS, 3.0), 1e-9)
    }

    @Test
    fun bestCheckInAloneScoresAHundred() {
        val best = Item.entries.associateWith { if (it.higherIsBetter) 5.0 else 1.0 }
        val s = Readiness.score(inputs(checkIn = best), day)!!
        assertEquals(100, s.score)
        assertEquals(listOf(Source.CHECK_IN), s.parts.map { it.source })
    }

    @Test
    fun hrvNeedsAUsualBeforeItCounts() {
        val few = usual(Readiness.MIN_BASELINE - 1, 50.0) + (day to 60.0)
        assertNull(Readiness.score(inputs(hrv = few), day))
        val enough = usual(Readiness.MIN_BASELINE, 50.0) + (day to 60.0)
        val s = Readiness.score(inputs(hrv = enough), day)!!
        // 20% above the usual is fully ready.
        assertEquals(100, s.score)
        assertEquals(50.0, s.part(Source.HRV)!!.usual!!, 1e-9)
    }

    @Test
    fun higherRestingHeartRateLowersTheScore() {
        val rhr = usual(10, 60.0) + (day to 63.0)
        val s = Readiness.score(inputs(rhr = rhr), day)!!
        assertEquals(20, s.score)
    }

    @Test
    fun partsAreWeighted() {
        // Check-in all middling (0.5, weight 0.5) and 8 hours' sleep (1.0, weight 0.1): (0.25 + 0.1) / 0.6.
        val mid = Item.entries.associateWith { 3.0 }
        val s = Readiness.score(inputs(checkIn = mid, sleep = mapOf(day to 8.0)), day)!!
        assertEquals(58, s.score)
    }

    @Test
    fun weekAverageLeavesTheDayOut() {
        val history = listOf(Readiness.Score(day - 8, 10, emptyList()), Readiness.Score(day - 2, 60, emptyList()),
            Readiness.Score(day - 1, 80, emptyList()), Readiness.Score(day, 0, emptyList()))
        assertEquals(70.0, Readiness.weekAverage(history, day)!!, 1e-9)
        assertNull(Readiness.weekAverage(history, day - 20))
    }

    @Test
    fun checkInNamesAreRecognised() {
        assertTrue(Readiness.isCheckIn(" sleep quality "))
        assertNotNull(Item.entries.firstOrNull { it.matches("MOOD") })
    }
}
