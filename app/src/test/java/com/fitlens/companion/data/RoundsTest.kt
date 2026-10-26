package com.fitlens.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rounds (#246): the stored values and the sums, with worked examples. */
class RoundsTest {

    private fun set(rounds: Int, total: Int, rest: Int?, live: Int?) = SetRow(
        id = 1, exerciseId = 1, date = "2026-10-27", weightKg = 0.0, reps = rounds, distance = 0.0, durationSec = total,
        isPr = false, comment = null, restSeconds = rest, liveSec = live
    )

    @Test
    fun totalTimeIsTheRoundsPlusTheRestsBetweenThem() {
        // 6 rounds of 3 minutes with 1 minute between: 18 + 5 = 23 minutes.
        assertEquals(23 * 60, Rounds.totalSec(6, 180, 60))
        // No rest after the last round, and one round has no rest at all.
        assertEquals(300, Rounds.totalSec(1, 300, 60))
        assertEquals(540, Rounds.totalSec(3, 180, 0))
    }

    @Test
    fun withoutRoundsOrALengthThereIsNoTime() {
        assertEquals(0, Rounds.totalSec(0, 180, 60))
        assertEquals(0, Rounds.totalSec(5, 0, 60))
        assertEquals(0, Rounds.roundSec(0, 600, 60))
        assertEquals(0, Rounds.roundSec(5, 0, 60))
    }

    @Test
    fun theRoundLengthComesBackFromTheStoredTotal() {
        val total = Rounds.totalSec(6, 180, 60)
        assertEquals(180, Rounds.roundSec(6, total, 60))
        assertEquals(180, Rounds.roundSec(set(6, total, 60, null)))
        // A set with no stored rest was saved with none.
        assertEquals(150, Rounds.roundSec(set(4, Rounds.totalSec(4, 150, 0), null, null)))
        // Half-minute rounds survive.
        assertEquals(150, Rounds.roundSec(5, Rounds.totalSec(5, 150, 45), 45))
    }

    @Test
    fun hardRoundsAreTheHardTimeOverTheRoundLength() {
        val total = Rounds.totalSec(6, 180, 60)
        val s = set(6, total, 60, 2 * 180)
        assertEquals(2, Rounds.hardRounds(s))
        assertEquals(0, Rounds.hardRounds(set(6, total, 60, null)))
        // Never more rounds than there were.
        assertEquals(6, Rounds.hardRounds(6, 99 * 180, 180))
        assertEquals(0, Rounds.hardRounds(0, 180, 180))
    }

    @Test
    fun hardTimeIsStoredOnlyWhenThereAreHardRounds() {
        assertEquals(360, Rounds.hardSec(6, 2, 180))
        assertNull(Rounds.hardSec(6, 0, 180))
        // More hard rounds than rounds is capped at the rounds.
        assertEquals(6 * 180, Rounds.hardSec(6, 9, 180))
    }

    @Test
    fun aRoundsSetCountsAsATimedSession() {
        assertTrue(ExerciseTypes.isTimedSession(ExerciseTypes.ROUNDS))
        assertTrue(ExerciseTypes.isTimedSession(ExerciseTypes.SESSION))
        assertFalse(ExerciseTypes.isTimedSession(ExerciseTypes.WEIGHT_REPS))
        assertTrue(ExerciseTypes.isRounds(ExerciseTypes.ROUNDS))
        assertFalse(ExerciseTypes.isSession(ExerciseTypes.ROUNDS))
        assertTrue(ExerciseTypes.ROUNDS in ExerciseTypes.all)
    }

    @Test
    fun theTrainingLoadOfARoundsSetUsesItsTotalTime() {
        // RPE 8 over 23 minutes.
        assertEquals(8.0 * 23, SessionLoad.of(8.0, Rounds.totalSec(6, 180, 60))!!, 1e-9)
        assertTrue(TrainingLoad.isSessionType(ExerciseTypes.ROUNDS))
    }
}
