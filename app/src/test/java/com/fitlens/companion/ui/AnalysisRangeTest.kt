package com.fitlens.companion.ui

import android.app.Application
import com.fitlens.companion.data.Analysis
import com.fitlens.companion.data.Dates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h3200dp-mdpi")
class AnalysisRangeTest : FunctionTest() {

    private val f = LongHistoryFixture

    @Test
    fun eachRangeStartsAtItsCalendarMonthsBack() {
        Dates.clockSource = f.clock
        assertEquals(f.ONE_MONTH_AGO, rangeFrom(0))
        assertEquals(f.THREE_MONTHS_AGO, rangeFrom(1))
        assertEquals(f.SIX_MONTHS_AGO, rangeFrom(2))
        assertEquals(f.ONE_YEAR_AGO, rangeFrom(3))
        assertNull(rangeFrom(4))
    }

    @Test
    fun monthlyWorkoutTotalsRunFromTheRangeStartToTheCurrentMonth() {
        Dates.clockSource = f.clock
        val t = Analysis.totals(f.snapshot(), Analysis.Metric.Workouts, Analysis.Period.Month, Analysis.Filter(), rangeFrom(1))
        assertEquals(LocalDate.parse(f.THREE_MONTHS_AGO).withDayOfMonth(1), t.first().start)
        assertEquals(LocalDate.parse(f.END).withDayOfMonth(1), t.last().start)
        assertTrue(t.last().current)
        assertEquals(4, t.size)
    }

    @Test
    fun allTimeMonthlyTotalsStartAtTheFirstLoggedMonth() {
        Dates.clockSource = f.clock
        val t = Analysis.totals(f.snapshot(), Analysis.Metric.Workouts, Analysis.Period.Month, Analysis.Filter(), null)
        assertEquals(LocalDate.parse(f.START).withDayOfMonth(1), t.first().start)
        assertTrue(t.last().current)
    }

    @Test
    fun totalsFollowTheAppsClockNotTheSystemOne() {
        Dates.clockSource = f.clockAt("10:00", "2027-02-10")
        val t = Analysis.totals(f.snapshot(), Analysis.Metric.Workouts, Analysis.Period.Month, Analysis.Filter(), null)
        assertEquals(LocalDate.of(2027, 2, 1), t.last().start)
    }

    @Test
    fun perYearOnShortRangesCountsOnlyTheRange() {
        Dates.clockSource = f.clock
        val days = f.snapshot().statSets.map { it.date.take(10) }.distinct()
        for (idx in 0..1) {
            val t = Analysis.totals(f.snapshot(), Analysis.Metric.Workouts, Analysis.Period.Year, Analysis.Filter(), rangeFrom(idx))
            assertEquals(1, t.size)
            assertTrue(t.first().clipped)
            assertEquals(days.count { it >= rangeFrom(idx)!! }.toDouble(), t.first().value)
        }
    }

    @Test
    fun perMonthOnOneMonthCountsOnlyFromTheStartDay() {
        Dates.clockSource = f.clock
        val snap = f.snapshot()
        val t = Analysis.totals(snap, Analysis.Metric.Workouts, Analysis.Period.Month, Analysis.Filter(), f.ONE_MONTH_AGO)
        assertTrue(t.first().clipped)
        val days = snap.statSets.map { it.date.take(10) }.distinct()
        assertEquals(days.count { it >= f.ONE_MONTH_AGO && it < "2026-10-01" }.toDouble(), t.first().value)
        val last = t.last()
        assertFalse(last.clipped)
        assertEquals(days.count { it >= "2026-10-01" }.toDouble(), last.value)
    }

    @Test
    fun allTimeIsNeverClipped() {
        Dates.clockSource = f.clock
        val t = Analysis.totals(f.snapshot(), Analysis.Metric.Workouts, Analysis.Period.Month, Analysis.Filter(), null)
        assertFalse(t.any { it.clipped })
    }
}
