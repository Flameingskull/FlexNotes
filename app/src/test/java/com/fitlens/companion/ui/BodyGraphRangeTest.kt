package com.fitlens.companion.ui

import android.app.Application
import com.fitlens.companion.R
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.Snapshot
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    application = Application::class,
    qualifiers = "w411dp-h3200dp-mdpi"
)
class BodyGraphRangeTest : FunctionTest() {

    private val f = LongHistoryFixture

    /** The Bodyweight days, oldest first. */
    private fun days(snap: Snapshot): List<String> = snap.dailySeries(f.BODYWEIGHT).map { it.date }

    /** The first Bodyweight day on or after [from]. */
    private fun firstFrom(snap: Snapshot, from: String): String = days(snap).first { it >= from }

    /** Opens the range menu (its button reads the current choice) and picks [next]. */
    private fun chooseRange(current: String, next: String) {
        tap(app.getString(R.string.cmn_change_cd, app.getString(R.string.ch_range), current))
        tap(next)
    }

    /** The graph names its first and last day (its TalkBack description, built from the points it draws). */
    private fun waitForFirstDay(date: String) = compose.waitUntil(5_000) { shows(Dates.day(date), substring = true) }

    @Test
    fun eachRangeDrawsItsOwnPeriodFromToday() {
        val snap = f.snapshot()
        openScreen(snap) { s -> BodyGraphPane(s, nav, f.BODYWEIGHT) }
        val oldest = days(snap).first()
        waitForFirstDay(oldest)
        assertShows(Dates.day(days(snap).last()), substring = true)
        var current = app.getString(R.string.an_all_time)
        for ((label, start) in listOf(
            R.string.range_1m to f.ONE_MONTH_AGO, R.string.range_3m to f.THREE_MONTHS_AGO,
            R.string.range_6m to f.SIX_MONTHS_AGO, R.string.range_1y to f.ONE_YEAR_AGO
        )) {
            val next = app.getString(label)
            chooseRange(current, next)
            waitForFirstDay(firstFrom(snap, start))
            assertTrue("$next must not reach back to the first value", !shows(Dates.day(oldest), substring = true))
            assertShows(Dates.day(days(snap).last()), substring = true)
            current = next
        }
    }

    @Test
    fun theOldDefinitionOfTheLatestValueNoLongerDecides() {
        val snap = f.snapshot()
        openScreen(snap, f.clockAt("10:00", "2026-11-08")) { s -> BodyGraphPane(s, nav, f.BODYWEIGHT) }
        val first = firstFrom(snap, "2026-10-08")
        waitForFirstDay(first)
        val sep = first != firstFrom(snap, "2026-09-25")
        if (sep) assertTrue(!shows(Dates.day(firstFrom(snap, "2026-09-25")), substring = true))
    }

    @Test
    fun aRangeWithNothingInItSaysSoAndAllTimeBringsBackTheGraph() {
        val snap = f.snapshot()
        openScreen(snap, f.clockAt("10:00", "2027-06-30")) { s -> BodyGraphPane(s, nav, f.BODYWEIGHT) }
        val oldest = days(snap).first()
        chooseRange(app.getString(R.string.an_all_time), app.getString(R.string.range_1m))
        compose.waitUntil(5_000) { shows(app.getString(R.string.ch_range_empty_title)) }
        assertShows(app.getString(R.string.ch_range_empty_body, Dates.day(days(snap).last())))
        tap(app.getString(R.string.ch_range_show_all))
        waitForFirstDay(oldest)
        assertTrue(!shows(app.getString(R.string.ch_range_empty_title)))
    }
}
