package com.fitlens.companion.ui

import android.app.Application
import com.fitlens.companion.data.Analysis
import com.fitlens.companion.data.Dates
import org.junit.Assert.assertEquals
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
}
