package com.fitlens.companion.ui

import android.app.Application
import com.fitlens.companion.data.PlannedExercise
import com.fitlens.companion.data.PlannedSet
import com.fitlens.companion.data.Routine
import com.fitlens.companion.data.RoutineDay
import com.fitlens.companion.data.Routines
import com.fitlens.companion.data.Workouts
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h3200dp-mdpi")
class RoutineRestoreTest : FunctionTest() {

    @Test
    fun undoPutsAWorkoutBackWithItsOwnIdsAndPlace() {
        runBlocking {
            Routines.save(Routine(0L, "Push Pull Legs", days = listOf(RoutineDay(0L, "Push Day"), RoutineDay(0L, "Pull Day"), RoutineDay(0L, "Leg Day"))))
            Routines.save(Routine(0L, "Upper Lower", days = listOf(RoutineDay(0L, "Upper"), RoutineDay(0L, "Lower"))))
            Routines.save(Routine(0L, "Full Body", days = listOf(RoutineDay(0L, "Monday"))))
        }
        val before = stored().routines
        val middle = before.first { it.name == "Upper Lower" }
        runBlocking { Routines.delete(middle.id) }
        assertNull(stored().routinesById[middle.id])
        runBlocking { Routines.restore(middle) }
        val after = stored().routines
        assertEquals(before.map { it.id }, after.map { it.id })
        assertEquals(before.map { it.name }, after.map { it.name })
        assertEquals(middle.days.map { it.id }, after.first { it.id == middle.id }.days.map { it.id })
        assertEquals(middle.days.map { it.name }, after.first { it.id == middle.id }.days.map { it.name })
        assertNotNull(stored().routinesById[middle.id])
        runBlocking { Routines.restore(middle) }
        assertEquals(before.map { it.id }, stored().routines.map { it.id })
    }

    @Test
    fun undoLeavesADayNowOwnedByAnotherWorkoutAlone() {
        val benchId = runBlocking {
            val chest = Workouts.createCategory("Chest")
            val bench = Workouts.createExercise("Bench Press", chest)
            Routines.save(Routine(0L, "Push Pull Legs", days = listOf(RoutineDay(0L, "Push Day"), RoutineDay(0L, "Pull Day"))))
            Routines.save(
                Routine(
                    0L, "Upper Lower",
                    days = listOf(RoutineDay(0L, "Upper", listOf(PlannedExercise(bench, sets = listOf(PlannedSet(weightKg = 80.0, reps = 8), PlannedSet(weightKg = 85.0, reps = 6))))))
                )
            )
            bench
        }
        val ppl = stored().routines.first { it.name == "Push Pull Legs" }
        val other = stored().routines.first { it.name == "Upper Lower" }
        val otherDay = other.days.first()
        runBlocking { Routines.delete(ppl.id) }
        val clash = ppl.copy(days = listOf(ppl.days.first().copy(id = otherDay.id)) + ppl.days.drop(1))
        runBlocking { Routines.restore(clash) }
        val after = stored()
        assertEquals(listOf("Upper"), after.routinesById[other.id]!!.days.map { it.name })
        assertEquals(listOf(otherDay.id), after.routinesById[other.id]!!.days.map { it.id })
        assertEquals(listOf(ppl.days[1].id), after.routinesById[ppl.id]!!.days.map { it.id })
        val upper = after.routinesById[other.id]!!.days.first()
        assertEquals(listOf(benchId), upper.exercises.map { it.exerciseId })
        assertEquals(listOf(8, 6), upper.exercises.first().sets.map { it.reps })
    }
}
