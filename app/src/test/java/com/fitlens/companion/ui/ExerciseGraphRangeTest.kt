package com.fitlens.companion.ui

import android.app.Application
import com.fitlens.companion.R
import com.fitlens.companion.data.Dates
import org.junit.Assert.assertEquals
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
class ExerciseGraphRangeTest : FunctionTest() {

    private val f = LongHistoryFixture

    /** An exercise's first graph over range [range], with the phone's clock at the fixture's last day. */
    private fun series(range: Int, ex: Long = f.EX_BENCH, relative: Boolean = false): List<ChartPoint> {
        val snap = f.snapshot()
        Dates.clockSource = f.clock
        return comparedSeries(snap, ex, graphLabelsFor(snap, ex).first(), emptyList(), range, relative).first().points
    }

    /** Opens the range menu (its button reads the current choice) and picks [next]. */
    private fun chooseRange(current: String, next: String) {
        tap(app.getString(R.string.cmn_change_cd, app.getString(R.string.ch_range), current))
        tap(next)
    }

    private fun waitForDay(date: String) = compose.waitUntil(5_000) { shows(Dates.day(date), substring = true) }

    @Test
    fun eachRangeStartsAtItsCalendarMonthsBackFromToday() {
        for ((idx, start) in listOf(0 to f.ONE_MONTH_AGO, 1 to f.THREE_MONTHS_AGO, 2 to f.SIX_MONTHS_AGO, 3 to f.ONE_YEAR_AGO)) {
            val pts = series(idx)
            assertTrue("range $idx starts on or after $start", pts.first().date >= start)
            assertTrue("range $idx starts within a week of $start", pts.first().date <= LocalDate.parse(start).plusDays(7).toString())
            assertEquals(f.END, pts.last().date)
        }
    }

    @Test
    fun theRangesAreDifferentAndAllTimeReachesTheFirstLoggedDay() {
        val sizes = (0..4).map { series(it).size }
        for (i in 0 until 4) assertTrue("range $i is shorter than range ${i + 1}: $sizes", sizes[i] < sizes[i + 1])
        assertEquals(f.START, series(4).first().date)
        assertEquals(f.END, series(4).last().date)
    }

    @Test
    fun anOldExerciseHasNothingInAShortRangeAndEverythingInAllTime() {
        assertTrue(series(0, f.EX_SQUAT).isEmpty())
        assertTrue(series(3, f.EX_SQUAT).isEmpty())
        assertEquals(f.START, series(4, f.EX_SQUAT).first().date)
        assertEquals(f.OLD_LAST, series(4, f.EX_SQUAT).last().date)
    }

    @Test
    fun relativeStartsAtOneHundredPercentOfTheFirstValueInTheRange() {
        assertEquals(100.0, series(1, relative = true).first().y, 1e-9)
    }

    @Test
    fun theScreenSaysNothingWasLoggedAndAllTimeBringsTheGraphBack() {
        openScreen(f.snapshot()) { s -> ExerciseGraphPane(s, nav, f.EX_SQUAT) }
        waitForDay(f.START)
        assertShows(Dates.day(f.OLD_LAST), substring = true)
        chooseRange(app.getString(R.string.an_all_time), app.getString(R.string.range_1m))
        compose.waitUntil(5_000) { shows(app.getString(R.string.ch_range_empty_title)) }
        assertShows(app.getString(R.string.ch_range_empty_body, Dates.day(f.OLD_LAST)))
        tap(app.getString(R.string.ch_range_show_all))
        waitForDay(f.START)
    }

    @Test
    fun aYearOfBenchPressShowsItsFirstDayInTheYearNotTheFirstEver() {
        val snap = f.snapshot()
        openScreen(snap) { s -> ExerciseGraphPane(s, nav, f.EX_BENCH) }
        waitForDay(f.START)
        chooseRange(app.getString(R.string.an_all_time), app.getString(R.string.range_1y))
        val firstInYear = snap.statSetsByExercise.getValue(f.EX_BENCH).map { it.date }.first { it >= f.ONE_YEAR_AGO }
        waitForDay(firstInYear)
        assertTrue(!shows(Dates.day(f.START), substring = true))
    }
}
