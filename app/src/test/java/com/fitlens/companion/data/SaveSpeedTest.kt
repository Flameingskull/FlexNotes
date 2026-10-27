package com.fitlens.companion.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * #60's measured criterion, run on every build: saving a set, through `Workouts` and `Store` as the app does it,
 * costs about the same with four years of history and a large photo library as with one. The guard is a count (#236):
 * a save reads only its own day's set rows back from the database, however long the history, so a slow or busy build
 * machine can't fail it. The time is still measured, best of three rounds, as a loose check. Also checks, end to end,
 * that the patched snapshot a set write leaves behind equals a full reload. Synthetic data only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SaveSpeedTest {

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        app.deleteDatabase(Db.NAME)
        Store.init(app)
        Settings.init(app)
        runBlocking { Settings.awaitLoaded() }
    }

    @After
    fun tearDown() {
        Store.db.close()
        app.deleteDatabase(Db.NAME)
    }

    private fun <T> io(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { block() }

    /** Ten exercises, created once. */
    private fun library(): List<Long> = io {
        Store.db.transaction {
            val w = Store.db.workoutDao
            val cat = w.addCategory("Strength", 0, w.lastCategoryOrder() + 1, Sources.FLEXNOTES)
            (1..EXERCISES).map { w.addExercise("Exercise $it", cat, 0, null, Sources.FLEXNOTES) }
        }
    }

    /**
     * A year of training ending the day before [end]: three workouts a week, four exercises a day, five sets each
     * (about 3,100 sets), and [PHOTOS_PER_YEAR] dated photos.
     */
    private fun seedYear(exercises: List<Long>, end: LocalDate) = io {
        Store.db.transaction {
            val w = Store.db.workoutDao
            val start = end.minusYears(1)
            var day = start
            var n = 0
            while (day.isBefore(end)) {
                val date = day.toString()
                repeat(4) { k ->
                    val ex = exercises[(n + k) % exercises.size]
                    repeat(5) { s ->
                        w.addSet(
                            ex, date, 60.0 + (n % 40), 5 + s, 0.0, 0, isPr = 0, comment = null, source = Sources.FLEXNOTES,
                            setType = SetTypes.WORKING, rpe = null, metric = null, position = 0L, superset = 0, done = 0,
                            restSeconds = null, liveSeconds = null, intensity = null, sessionType = null, zone = null,
                            countA = null, countB = null
                        )
                    }
                }
                n++
                day = day.plusDays(if (n % 3 == 0) 3 else 2)
            }
            repeat(PHOTOS_PER_YEAR) { i ->
                val date = start.plusDays((i % 365).toLong()).toString()
                Store.db.photoDao.add(
                    PhotoRow(0, "photo_${start.year}_$i.jpg", date, null, DateSources.EXIF, "", null, null, "h${start.year}_$i", 0)
                )
            }
        }
    }

    /** What saving and ticking sets costs: the median time in nanoseconds, and set rows read per save. */
    private class SaveCost(val medianNs: Long, val rowsPerSave: Long)

    /** Saves and then ticks [SAVES] sets today, after a warm-up, timing each and counting the set rows read back. */
    private fun measureSaves(exercise: Long): SaveCost = io {
        val times = ArrayList<Long>()
        var rowsBefore = 0L
        repeat(WARM_UP + SAVES) { i ->
            if (i == WARM_UP) rowsBefore = Store.setRowsRead
            val start = System.nanoTime()
            val id = Workouts.addSet(exercise, TODAY, 100.0 + i, 5)
            Workouts.setDone(id, true)
            if (i >= WARM_UP) times += System.nanoTime() - start
        }
        SaveCost(times.sorted()[times.size / 2], (Store.setRowsRead - rowsBefore) / SAVES)
    }

    /** The lowest median of [ROUNDS] rounds, so one busy moment on the build machine doesn't count. */
    private fun bestOf(exercise: Long): SaveCost {
        val rounds = List(ROUNDS) { measureSaves(exercise) }
        return SaveCost(rounds.minOf { it.medianNs }, rounds.maxOf { it.rowsPerSave })
    }

    @Test
    fun savingASetStaysFlatAsHistoryGrows() {
        val exercises = library()
        val today = LocalDate.parse(TODAY)
        seedYear(exercises, today)
        io { Store.reload() }
        val setsOneYear = Store.snapshot.value!!.sets.size
        val oneYear = bestOf(exercises[0])

        // Three earlier years, so the history is four times as long.
        (1..3).forEach { seedYear(exercises, today.minusYears(it.toLong())) }
        io { Store.reload() }
        val fourYears = bestOf(exercises[1])
        val snap = Store.snapshot.value!!

        println(
            "#60 save speed: ${oneYear.medianNs / 1000} µs and ${oneYear.rowsPerSave} rows read with $setsOneYear sets, " +
                "${fourYears.medianNs / 1000} µs and ${fourYears.rowsPerSave} rows with ${snap.sets.size} sets and ${snap.photos.size} photos"
        )
        assertTrue(snap.sets.size > 3 * setsOneYear)
        // The guard (#236): a save reads back only its exercise's sets on its own day. Today holds at most the sets
        // saved here, read once when a set is added and once when it's ticked, so a save reads no more than twice that:
        // never a whole exercise's history (over a thousand rows a year here) or the whole history.
        val todayAtMost = 2L * (WARM_UP + SAVES) * ROUNDS
        assertTrue("A set save read ${oneYear.rowsPerSave} set rows with 1 year of history", oneYear.rowsPerSave in 1..todayAtMost)
        assertTrue("A set save read ${fourYears.rowsPerSave} set rows with 4 years of history", fourYears.rowsPerSave in 1..todayAtMost)
        // A loose check on time as well, best of three rounds: four times the history may not triple it.
        assertTrue(
            "Saving a set took ${fourYears.medianNs / 1000} µs with 4 years of history, ${oneYear.medianNs / 1000} µs with 1",
            fourYears.medianNs <= oneYear.medianNs * 3 + 10_000_000
        )
    }

    @Test
    fun aPatchedSnapshotMatchesAFullReload() {
        val exercises = library()
        val today = LocalDate.parse(TODAY)
        seedYear(exercises, today)
        io { Store.reload() }
        val before = Store.snapshot.value!!

        val added = io { Workouts.addSet(exercises[0], TODAY, 500.0, 1) }
        val afterAdd = Store.snapshot.value!!
        // Only today's day and exercise were patched; every other list is the very same object.
        assertSame(before.setsByExercise[exercises[5]], afterAdd.setsByExercise[exercises[5]])
        val yesterday = before.setsByDate.keys.last()
        assertSame(before.setsByDate[yesterday], afterAdd.setsByDate[yesterday])
        assertTrue(afterAdd.setsByDate.getValue(TODAY).single().isPr)

        io {
            Workouts.setDone(added, true)
            Workouts.setComment(added, "Heavy single")
            // Moved to an earlier day and another exercise: both days, and PR marks after it, are re-read.
            val moved = Store.snapshot.value!!.sets.first { it.id == added }.copy(exerciseId = exercises[1], date = yesterday)
            Workouts.updateSet(moved)
            Workouts.addSet(exercises[2], TODAY, 40.0, 8, setType = SetTypes.WARMUP)
            Workouts.deleteSet(before.setsByDate.getValue(yesterday).first().id)
        }
        val patched = Store.snapshot.value!!.setPart
        io { Store.reload() }
        val full = Store.snapshot.value!!.setPart
        assertEquals(full.sets, patched.sets)
        assertEquals(full.setsByDate, patched.setsByDate)
        assertEquals(full.setsByExercise, patched.setsByExercise)
        assertEquals(full.statSets, patched.statSets)
        assertEquals(full.statSetsByExercise, patched.statSetsByExercise)
        assertEquals(full.lastUsedByExercise, patched.lastUsedByExercise)
        assertEquals(full.workoutsByExercise, patched.workoutsByExercise)
    }

    private companion object {
        const val TODAY = "2026-10-14"
        const val EXERCISES = 10
        const val PHOTOS_PER_YEAR = 500
        const val WARM_UP = 10
        const val SAVES = 30
        const val ROUNDS = 3
    }
}
