package com.fitlens.companion.ui

import android.app.Application
import com.fitlens.companion.R
import com.fitlens.companion.data.Dates
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h3200dp-mdpi")
class ReadinessRangeTest : FunctionTest() {

    @Test
    fun chooseEachRangeAndTheTabStillDrawsItsSections() {
        openScreen(LongHistoryFixture.snapshot()) { s -> ReadinessTab(s) }
        assertShows(app.getString(R.string.rd_title))
        assertShows(app.getString(R.string.load_title))
        var current = app.getString(R.string.range_3m)
        for (id in listOf(R.string.range_1m, R.string.range_6m, R.string.range_1y, R.string.an_all_time)) {
            val next = app.getString(id)
            tap(app.getString(R.string.cmn_change_cd, app.getString(R.string.ch_range), current))
            tap(next)
            current = next
            assertShows(app.getString(R.string.rd_title))
            assertShows(app.getString(R.string.load_title))
        }
    }

    @Test
    fun theStartOfEachRangeIsTheSharedRule() {
        Dates.clockSource = LongHistoryFixture.clock
        assertEquals(LocalDate.parse(LongHistoryFixture.THREE_MONTHS_AGO).toEpochDay(), rangeStartDay(1, 0L))
        assertEquals(123L, rangeStartDay(4, 123L))
        assertEquals(
            ChartPeriod(LocalDate.parse(LongHistoryFixture.ONE_YEAR_AGO).toEpochDay(), LocalDate.parse(LongHistoryFixture.END).toEpochDay()),
            rangePeriod(3)
        )
    }
}
