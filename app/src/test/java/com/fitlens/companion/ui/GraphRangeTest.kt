package com.fitlens.companion.ui

import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.LiveClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class GraphRangeTest {

    private val today = LocalDate.of(2026, 10, 30)

    private fun day(s: String) = LocalDate.parse(s).toEpochDay()

    @Test
    fun eachRangeCountsBackWholeCalendarMonthsFromToday() {
        assertEquals(ChartPeriod(day("2026-09-30"), day("2026-10-30")), rangePeriod(0, today))
        assertEquals(ChartPeriod(day("2026-07-30"), day("2026-10-30")), rangePeriod(1, today))
        assertEquals(ChartPeriod(day("2026-04-30"), day("2026-10-30")), rangePeriod(2, today))
        assertEquals(ChartPeriod(day("2025-10-30"), day("2026-10-30")), rangePeriod(3, today))
    }

    @Test
    fun allTimeHasNoPeriod() {
        assertNull(rangePeriod(4, today))
        assertNull(rangePeriod(9, today))
        assertNull(rangePeriod(-1, today))
        assertNull(rangeStartIso(4, today))
    }

    @Test
    fun theFourRangesAreDifferentPeriods() {
        val froms = (0..3).map { rangePeriod(it, today)!!.from }
        assertTrue(froms[0] > froms[1])
        assertTrue(froms[1] > froms[2])
        assertTrue(froms[2] > froms[3])
    }

    @Test
    fun aShortMonthIsClamped() {
        val shortMonthDay = LocalDate.of(2026, 3, 31)
        assertEquals("2026-02-28", rangeStartIso(0, shortMonthDay))
    }

    @Test
    fun startIsoAndStartDayAgree() {
        assertEquals("2026-07-30", rangeStartIso(1, today))
        assertEquals(day("2026-07-30"), rangeStartDay(1, 5L, today))
        assertEquals(5L, rangeStartDay(4, 5L, today))
    }

    @Test
    fun thePeriodUsesTheAppsClock() {
        Dates.clockSource = Clock.fixed(Instant.parse("2026-10-30T10:00:00Z"), ZoneOffset.UTC)
        try {
            assertEquals(ChartPeriod(day("2026-07-30"), day("2026-10-30")), rangePeriod(1))
        } finally {
            Dates.clockSource = LiveClock
        }
    }

    @Test
    fun inPeriodKeepsBothEndsAndNothingOutside() {
        val items = listOf(day("2026-09-29"), day("2026-09-30"), day("2026-10-15"), day("2026-10-30"), day("2026-10-31"))
        val period = rangePeriod(0, today)
        assertEquals(listOf(day("2026-09-30"), day("2026-10-15"), day("2026-10-30")), inPeriod(items, period) { it })
        assertEquals(items, inPeriod(items, null) { it })
    }

    @Test
    fun aValueFortyDaysAgoIsInThreeMonthsButNotOne() {
        val v = today.minusDays(40).toEpochDay()
        assertEquals(emptyList<Long>(), inPeriod(listOf(v), rangePeriod(0, today)) { it })
        assertEquals(listOf(v), inPeriod(listOf(v), rangePeriod(1, today)) { it })
    }
}
