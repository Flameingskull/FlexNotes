package com.fitlens.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The set entry screen's save decisions (#305): superset round-robin, the workout timer, auto-select next and more. */
class SetEntryRulesTest {

    // ---- Unusual length (#243) ----

    @Test
    fun aSessionUnderFifteenOrOverTwoHundredFortyMinutesIsAskedAbout() {
        assertTrue(SetEntryRules.isUnusualLength(true, false, 10 * 60, null, false))
        assertTrue(SetEntryRules.isUnusualLength(true, false, 241 * 60, null, false))
        assertFalse(SetEntryRules.isUnusualLength(true, false, 15 * 60, null, false))
        assertFalse(SetEntryRules.isUnusualLength(true, false, 240 * 60, null, false))
        assertFalse(SetEntryRules.isUnusualLength(true, false, 60 * 60, null, false))
    }

    @Test
    fun roundsOnlyAskAboutAnUpperLimit() {
        // A 6-minute round of bag work is normal; four hours and one minute is not.
        assertFalse(SetEntryRules.isUnusualLength(true, true, 6 * 60, null, false))
        assertTrue(SetEntryRules.isUnusualLength(true, true, 241 * 60, null, false))
        assertFalse(SetEntryRules.isUnusualLength(true, true, 240 * 60, null, false))
    }

    @Test
    fun confirmedNoLengthOrAnUnchangedEditIsNotAskedAgain() {
        assertFalse(SetEntryRules.isUnusualLength(true, false, 10 * 60, null, confirmed = true))
        assertFalse(SetEntryRules.isUnusualLength(true, false, 0, null, false))
        // Editing a saved 10-minute session without changing its length.
        assertFalse(SetEntryRules.isUnusualLength(true, false, 10 * 60, 10 * 60, false))
        // Changing it to another unusual length asks again.
        assertTrue(SetEntryRules.isUnusualLength(true, false, 5 * 60, 10 * 60, false))
    }

    @Test
    fun otherExerciseTypesAreNeverAskedAboutLength() {
        assertFalse(SetEntryRules.isUnusualLength(false, false, 10, null, false))
        assertFalse(SetEntryRules.isUnusualLength(false, false, 999 * 60, null, false))
    }

    // ---- Nothing to save, hard time ----

    @Test
    fun aTimedSetNeedsALengthOrAnEffort() {
        assertTrue(SetEntryRules.nothingToSave(true, false, 80.0, 5, 0.0, 0, false))
        assertFalse(SetEntryRules.nothingToSave(true, true, 0.0, 0, 0.0, 0, false))
        assertFalse(SetEntryRules.nothingToSave(true, false, 0.0, 0, 0.0, 600, false))
    }

    @Test
    fun anOrdinarySetNeedsAnyOneValueAndAZeroMetricCounts() {
        assertTrue(SetEntryRules.nothingToSave(false, true, 0.0, 0, 0.0, 0, false))
        assertFalse(SetEntryRules.nothingToSave(false, false, 60.0, 0, 0.0, 0, false))
        assertFalse(SetEntryRules.nothingToSave(false, false, 0.0, 8, 0.0, 0, false))
        assertFalse(SetEntryRules.nothingToSave(false, false, 0.0, 0, 1.5, 0, false))
        assertFalse(SetEntryRules.nothingToSave(false, false, 0.0, 0, 0.0, 30, false))
        assertFalse(SetEntryRules.nothingToSave(false, false, 0.0, 0, 0.0, 0, true))
    }

    @Test
    fun hardTimeCannotBeLongerThanTheSessionButRoundsHaveTheirOwnCheck() {
        assertTrue(SetEntryRules.hardLongerThanSession(false, 61 * 60, 60 * 60))
        assertFalse(SetEntryRules.hardLongerThanSession(false, 60 * 60, 60 * 60))
        assertFalse(SetEntryRules.hardLongerThanSession(false, null, 60 * 60))
        assertFalse(SetEntryRules.hardLongerThanSession(true, 61 * 60, 60 * 60))
    }

    // ---- Superset round-robin (#18, #20) ----

    @Test
    fun savingInTheMiddleOfASupersetMovesOnWithoutStartingTheRest() {
        val step = SetEntryRules.supersetStep(listOf(10, 20, 30), 10)
        assertEquals(20L, step.nextExercise)
        assertFalse(step.endOfRound)
        val second = SetEntryRules.supersetStep(listOf(10, 20, 30), 20)
        assertEquals(30L, second.nextExercise)
        assertFalse(second.endOfRound)
    }

    @Test
    fun theLastExerciseOfARoundStartsTheRestAndWrapsToTheFirst() {
        val step = SetEntryRules.supersetStep(listOf(10, 20, 30), 30)
        assertEquals(10L, step.nextExercise)
        assertTrue(step.endOfRound)
    }

    @Test
    fun noGroupOrAGroupOfOneStaysPutAndStartsTheRest() {
        for (members in listOf<List<Long>>(emptyList(), listOf(10L))) {
            val step = SetEntryRules.supersetStep(members, 10)
            assertNull(step.nextExercise)
            assertTrue(step.endOfRound)
        }
    }

    @Test
    fun anExerciseOutsideItsGroupDoesNotMoveOnOrStartTheRest() {
        val step = SetEntryRules.supersetStep(listOf(10, 20), 99)
        assertNull(step.nextExercise)
        assertFalse(step.endOfRound)
    }

    // ---- Workout timer (#12) ----

    @Test
    fun theWorkoutTimerStartsOnlyWithTheFirstSetOfTodayWhenOnAndNotAlreadyTimed() {
        assertTrue(SetEntryRules.startsWorkoutTimer(firstOfDay = true, isToday = true, autoSetting = true, alreadyTimed = false))
        assertFalse(SetEntryRules.startsWorkoutTimer(firstOfDay = false, isToday = true, autoSetting = true, alreadyTimed = false))
        assertFalse(SetEntryRules.startsWorkoutTimer(firstOfDay = true, isToday = false, autoSetting = true, alreadyTimed = false))
        assertFalse(SetEntryRules.startsWorkoutTimer(firstOfDay = true, isToday = true, autoSetting = false, alreadyTimed = false))
        assertFalse(SetEntryRules.startsWorkoutTimer(firstOfDay = true, isToday = true, autoSetting = true, alreadyTimed = true))
    }

    // ---- Auto-select next (#97) ----

    @Test
    fun savingAnEditSelectsTheNextSetWhenOn() {
        assertEquals(2L, SetEntryRules.nextSelected(listOf(1, 2, 3), 1, true))
        assertEquals(3L, SetEntryRules.nextSelected(listOf(1, 2, 3), 2, true))
    }

    @Test
    fun theLastSetTheSettingOffOrAnUnknownSetSelectsNothing() {
        assertNull(SetEntryRules.nextSelected(listOf(1, 2, 3), 3, true))
        assertNull(SetEntryRules.nextSelected(listOf(1, 2, 3), 1, false))
        assertNull(SetEntryRules.nextSelected(listOf(1, 2, 3), 99, true))
        assertNull(SetEntryRules.nextSelected(emptyList(), 1, true))
    }
}
