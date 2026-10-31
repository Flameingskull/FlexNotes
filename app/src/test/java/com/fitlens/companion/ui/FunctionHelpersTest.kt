package com.fitlens.companion.ui

import android.app.Application
import com.fitlens.companion.R
import com.fitlens.companion.data.MedSchedules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Proves the helpers the function tests are built on (#333), so a broken helper is caught here and not mistaken for a
 * broken screen: [LongHistoryFixture] holds what it promises, [FunctionTest] opens a screen on it, taps through to
 * a value from the oldest year and reads it back.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h3200dp-mdpi")
class FunctionHelpersTest : FunctionTest() {

    private val f = LongHistoryFixture

    @Test
    fun theFixtureHoldsYearsOfThinHistoryWithTheOldExerciseLongAgo() {
        val snap = f.snapshot()
        val dates = snap.sets.map { it.date }.distinct()
        assertEquals(f.START, dates.first())
        assertEquals(f.END, dates.last())
        // Thin: weekly, not daily, over more than three years.
        assertTrue("${dates.size} days of sets", dates.size in 100..250)
        // The squat was last logged over two years before the end; the bench press every week since.
        assertEquals(f.OLD_LAST, snap.lastUsedByExercise[f.EX_SQUAT])
        assertTrue(f.OLD_LAST < f.TWO_YEARS_AGO)
        assertEquals(f.END, snap.lastUsedByExercise[f.EX_BENCH])
        // Nothing logged in a gap, and both sources on the last day.
        assertTrue(snap.sets.none { s -> f.GAPS.any { s.date in it } })
        assertEquals(setOf(true, false), snap.setsByDate.getValue(f.END).map { it.imported }.toSet())
        // Body values and photos cover the same years.
        assertEquals(f.FIRST_PHOTO, snap.datedPhotos.first().date)
        assertEquals(f.LAST_PHOTO, snap.datedPhotos.last().date)
        assertTrue(snap.recordsByName.getValue(f.BODYWEIGHT).first().date < f.TWO_YEARS_AGO)
        // Three doses due at 08:00 on the last day (two of one medication, one injected), another five minutes later.
        val due = MedSchedules.dueOn(snap.medPlan, f.END)
        assertEquals(listOf(f.METFORMIN, f.METFORMIN, f.SEMAGLUTIDE), due.filter { it.due == f.KEY_0800 }.map { it.dose.medication })
        assertEquals(listOf(f.VITAMIN_D), due.filter { it.due == f.KEY_0805 }.map { it.dose.medication })
        assertTrue(snap.medPlan.infoOf(f.SEMAGLUTIDE).injected)
    }

    @Test
    fun aScreenOnTheFixtureShowsAValueFromItsOldestYearAfterTappingThroughToIt() {
        openScreen(f.snapshot()) { snap -> SetEntryScreen(snap, nav, f.END, f.EX_SQUAT) }
        tap(app.getString(R.string.set_tab_history))
        // The squat's first and last days, two years and more before the end, each with the note written that day.
        assertShows(historyDay(f.START))
        assertShows(f.OLD_FIRST_NOTE, substring = true)
        assertShows(historyDay(f.OLD_LAST))
        assertShows(f.OLD_LAST_NOTE, substring = true)
        // The screen is drawn from the snapshot it was given; the database behind it is empty, and stored() says so.
        assertTrue(stored().sets.isEmpty())
    }
}
