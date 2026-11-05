package com.fitlens.companion.ui

import android.app.Application
import android.os.Looper
import com.fitlens.companion.R
import com.fitlens.companion.data.Routine
import com.fitlens.companion.data.RoutineDay
import com.fitlens.companion.data.Routines
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.ui.FunctionTest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.robolectric.Shadows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Clock
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h3200dp-mdpi")
class WorkoutsScreenTest : FunctionTest() {

    private val ppl = "Push Pull Legs"
    private val long = "Upper and lower strength block with a very long name that has to wrap"

    /** Two saved workouts in the real database: three days, and one day. */
    private fun seed() {
        runBlocking {
            Routines.save(Routine(0L, ppl, days = listOf(RoutineDay(0L, "Push Day"), RoutineDay(0L, "Pull Day"), RoutineDay(0L, "Leg Day"))))
            Routines.save(Routine(0L, long, days = listOf(RoutineDay(0L, "Monday"))))
        }
    }

    private fun summary(days: String, exercises: String) = app.getString(R.string.wks_summary, days, exercises)

    private fun top(): Screen = compose.runOnIdle { nav.top }

    /** Opens the day log on the stored data, so a test starts where a person does. */
    private fun openDayLog() =
        openScreen(stored()) { snap -> NavHarness(nav, snap, Screen.Day(LongHistoryFixture.END)) }

    private fun openWorkouts(snapshot: com.fitlens.companion.data.Snapshot = stored(), clock: java.time.Clock = LongHistoryFixture.clock) =
        openScreen(snapshot, clock) { snap -> NavHarness(nav, snap, Screen.Workouts) }

    @Test
    fun theMenuOpensTheWorkoutsListAndCreateOpensTheEditor() {
        seed()
        openDayLog()
        tap(app.getString(R.string.cmn_more_options))
        tap(app.getString(R.string.day_menu_workouts))
        assertEquals(Screen.Workouts, top())
        assertShows(ppl)
        assertShows(summary("3 days", "0 exercises"))
        assertShows(long)
        assertShows(summary("1 day", "0 exercises"))
        tap(app.getString(R.string.lib_create_workout))
        assertEquals(Screen.WorkoutEditor(0L), top())
        assertShows(app.getString(R.string.workout_new_title))
    }

    @Test
    fun tappingAWorkoutOpensItInTheEditor() {
        seed()
        openWorkouts()
        val id = stored().routines.first { it.name == ppl }.id
        tap(ppl)
        assertEquals(Screen.WorkoutEditor(id), top())
        assertShows(app.getString(R.string.workout_edit_title))
    }

    @Test
    fun aRowSaysHowManyDaysAndExercisesAndALongNameIsShownInFull() {
        // The fixture's workout has a long name, three days and four planned exercises.
        openWorkouts(ScreenFixture.snapshot(), ScreenFixture.clock)
        assertShows("Upper and lower strength block with a long name")
        assertShows(summary("3 days", "4 exercises"))
    }

    @Test
    fun duplicateAddsACopyToTheList() {
        seed()
        openWorkouts()
        tap(app.getString(R.string.cmn_options_for, ppl))
        tap(app.getString(R.string.workout_duplicate))
        val copy = app.getString(R.string.workout_copy_name, ppl)
        compose.waitUntil(5_000) { stored().routines.any { it.name == copy } }
        assertEquals(3, stored().routines.first { it.name == copy }.days.size)
        show(stored())
        assertShows(copy)
        assertShows(ppl)
    }

    @Test
    fun deleteAsksFirstThenRemovesTheWorkoutAndUndoBringsItBack() {
        seed()
        openWorkouts()
        val before = stored().routines
        val undo = app.getString(R.string.workout_undo)
        val messages = CopyOnWriteArrayList<UiMessage>()
        val collector = CoroutineScope(Dispatchers.Unconfined).launch { UiEvents.messages.collect { messages.add(it) } }
        try {
            compose.waitUntil(5_000) { UiEvents.messages.subscriptionCount.value > 0 }
            tap(app.getString(R.string.cmn_options_for, ppl))
            tap(app.getString(R.string.workout_delete))
            // Asked first: nothing is deleted until it is confirmed.
            assertShows(app.getString(R.string.workout_delete_title, ppl))
            assertTrue(stored().routines.any { it.name == ppl })
            tap(app.getString(R.string.workout_delete))
            compose.waitUntil(5_000) { stored().routines.none { it.name == ppl } }
            show(stored())
            assertNotShows(ppl)
            assertShows(long)
            // Undo puts it back with its days.
            compose.waitUntil(5_000) { messages.any { it.actionLabel == undo } }
            compose.runOnUiThread { messages.first { it.actionLabel == undo }.onAction!!.invoke() }
            compose.waitUntil(10_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                stored().routines.any { it.name == ppl }
            }
            val after = stored().routines
            assertEquals(before.map { it.id }, after.map { it.id })
            assertEquals(before.first { it.name == ppl }.days.map { it.id }, after.first { it.name == ppl }.days.map { it.id })
            show(stored())
            assertShows(ppl)
        } finally {
            collector.cancel()
        }
    }

    @Test
    fun withNoWorkoutsTheScreenSaysSoAndOffersCreate() {
        openWorkouts()
        assertShows(app.getString(R.string.wks_empty_title))
        tap(app.getString(R.string.lib_create_workout))
        assertEquals(Screen.WorkoutEditor(0L), top())
        assertShows(app.getString(R.string.workout_new_title))
    }
}
