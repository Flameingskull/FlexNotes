package com.fitlens.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The incremental snapshot update (#60): after a small set write, [mergeSets] swaps in the re-read sets of the
 * exercises or dates the write named and keeps every other set. Whatever it produces must equal what a full reload
 * (`ORDER BY date, position, id`) would have read.
 */
class StoreMergeTest {

    private fun set(id: Long, exercise: Long, date: String, position: Long, weight: Double = 100.0, reps: Int = 5) =
        SetRow(id, exercise, date, weight, reps, 0.0, 0, false, null, position = position)

    /** What a full reload reads from these rows. */
    private fun fullReload(rows: List<SetRow>) = rows.sortedWith(SET_ORDER)

    private val before = fullReload(
        listOf(
            set(1, 10, "2026-09-01", 1), set(2, 10, "2026-09-01", 2), set(3, 20, "2026-09-01", 3),
            set(4, 20, "2026-09-03", 1), set(5, 10, "2026-09-03", 2), set(6, 30, "2026-09-05", 1)
        )
    )

    @Test
    fun savingASetReReadsOnlyItsExercise() {
        val added = set(7, 10, "2026-09-05", 2, weight = 110.0)
        val database = before + added
        val fresh = database.filter { it.exerciseId == 10L }.sortedWith(SET_ORDER)

        val merged = mergeSets(before, fresh) { it.exerciseId == 10L }

        assertEquals(fullReload(database), merged)
        // Sets of other exercises are the very same objects, not copies.
        assertSame(before.first { it.id == 6L }, merged.first { it.id == 6L })
    }

    @Test
    fun editingADeletingAndMovingBetweenExercises() {
        // Set 2 is edited and moved from exercise 10 to 30; set 5 is deleted.
        val database = before.filter { it.id != 5L }.map { if (it.id == 2L) it.copy(exerciseId = 30, weightKg = 90.0) else it }
        val named = setOf(10L, 30L)
        val fresh = database.filter { it.exerciseId in named }.sortedWith(SET_ORDER)

        val merged = mergeSets(before, fresh) { it.exerciseId in named }

        assertEquals(fullReload(database), merged)
    }

    @Test
    fun aDayReorderedAsAWhole() {
        // A superset or a new order on 3 September changes both exercises' sets that day.
        val database = before.map { if (it.date == "2026-09-03") it.copy(position = 3 - it.position, superset = 1) else it }
        val fresh = database.filter { it.date == "2026-09-03" }.sortedWith(SET_ORDER)

        val merged = mergeSets(before, fresh) { it.date.take(10) == "2026-09-03" }

        assertEquals(fullReload(database), merged)
        assertEquals(listOf(5L, 4L), merged.filter { it.date == "2026-09-03" }.map { it.id })
    }

    @Test
    fun nothingNamedChangesNothing() {
        assertEquals(before, mergeSets(before, emptyList()) { false })
    }

    // ---------- SetPart.patched: a day-scoped write patches the lookups instead of rebuilding them ----------

    private fun warmup(id: Long, exercise: Long, date: String, position: Long) =
        set(id, exercise, date, position).copy(setType = SetTypes.WARMUP)

    /** Every lookup of [actual] equals a full rebuild from the same sets. */
    private fun assertSameAsRebuild(database: List<SetRow>, countWarmups: Boolean, actual: SetPart) {
        val expected = SetPart(fullReload(database), countWarmups)
        assertEquals(expected.sets, actual.sets)
        assertEquals(expected.setsByDate, actual.setsByDate)
        assertEquals(expected.setsByDate.keys.toList(), actual.setsByDate.keys.toList())
        assertEquals(expected.setsByExercise, actual.setsByExercise)
        assertEquals(expected.statSets, actual.statSets)
        assertEquals(expected.statSetsByExercise, actual.statSetsByExercise)
        assertEquals(expected.lastUsedByExercise, actual.lastUsedByExercise)
        assertEquals(expected.workoutsByExercise, actual.workoutsByExercise)
    }

    /** Patches [old] for a write that changed [pairs] (exercise to day), reading them back from [database]. */
    private fun patch(old: SetPart, database: List<SetRow>, vararg pairs: Pair<Long, String>): SetPart {
        val keys = pairs.toSet()
        val fresh = database.filter { (it.exerciseId to it.date.take(10)) in keys }.sortedWith(SET_ORDER)
        return old.patched(keys.map { it.second }.toSet(), fresh) { (it.exerciseId to it.date.take(10)) in keys }
    }

    @Test
    fun aSetSavedOnAnExistingDayPatchesOnlyItsDayAndExercise() {
        val old = SetPart(before, false)
        val database = before + set(7, 10, "2026-09-03", 3, weight = 105.0)
        val patched = patch(old, database, 10L to "2026-09-03")
        assertSameAsRebuild(database, false, patched)
        // Untouched days and exercises keep the very same lists.
        assertSame(old.setsByDate["2026-09-01"], patched.setsByDate["2026-09-01"])
        assertSame(old.setsByExercise[30L], patched.setsByExercise[30L])
    }

    @Test
    fun aSetOnANewDayBetweenOthersAndAFirstSetOfANewExercise() {
        val old = SetPart(before, false)
        val database = before + set(7, 40, "2026-09-02", 1) + set(8, 10, "2026-09-02", 2)
        assertSameAsRebuild(database, false, patch(old, database, 40L to "2026-09-02", 10L to "2026-09-02"))
    }

    @Test
    fun deletingAnExercisesLastSetDropsItFromEveryLookup() {
        val old = SetPart(before, true)
        val database = before.filter { it.id != 6L }
        val patched = patch(old, database, 30L to "2026-09-05")
        assertSameAsRebuild(database, true, patched)
        assertEquals(null, patched.setsByExercise[30L])
        assertEquals(null, patched.setsByDate["2026-09-05"])
    }

    @Test
    fun aSetMovedToAnotherDayAndExercise() {
        val old = SetPart(before, false)
        val database = before.map { if (it.id == 1L) it.copy(exerciseId = 20, date = "2026-09-05", position = 9) else it }
        assertSameAsRebuild(database, false, patch(old, database, 10L to "2026-09-01", 20L to "2026-09-05"))
    }

    @Test
    fun warmUpsStayOutOfTheStatListsWhenTheyDontCount() {
        val withWarmup = fullReload(before + warmup(7, 20, "2026-09-03", 0))
        for (count in listOf(false, true)) {
            val old = SetPart(withWarmup, count)
            val database = withWarmup + warmup(8, 20, "2026-09-03", 4) + set(9, 20, "2026-09-03", 5)
            assertSameAsRebuild(database, count, patch(old, database, 20L to "2026-09-03"))
        }
    }

    @Test
    fun aWholeDayWithTimesInItsDates() {
        // Dates can carry a time; a day still means every date that starts with it.
        val timed = fullReload(before + set(7, 10, "2026-09-03 18:30:00", 1) + set(8, 20, "2026-09-04", 1))
        val old = SetPart(timed, false)
        val database = timed.filter { it.id != 4L }.map { if (it.id == 7L) it.copy(weightKg = 120.0) else it }
        val fresh = database.filter { it.date.take(10) == "2026-09-03" }.sortedWith(SET_ORDER)
        val patched = old.patched(setOf("2026-09-03"), fresh) { it.date.take(10) == "2026-09-03" }
        assertSameAsRebuild(database, false, patched)
        assertSame(old.setsByDate["2026-09-04"], patched.setsByDate["2026-09-04"])
    }

    @Test
    fun spliceDayKeepsTheListWhenNothingChanges() {
        assertSame(before, spliceDay(before, "2026-09-02", emptyList()))
        assertEquals(3 to 5, before.dayRange("2026-09-03"))
    }
}
