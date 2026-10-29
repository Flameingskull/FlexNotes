package com.fitlens.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Moving a workout keeps its length, including one that finished after midnight (#307). */
class MoveWorkoutTimesTest {
    @Test fun startAndFinishShiftByTheSameDays() {
        assertEquals("2026-10-10 23:30:00", Dates.shiftStamp("2026-10-03 23:30:00", 7))
        assertEquals("2026-10-11 00:45:00", Dates.shiftStamp("2026-10-04 00:45:00", 7))
    }

    @Test fun aFinishAfterMidnightStaysAfterTheStart() {
        val start = Dates.shiftStamp("2026-10-03 23:30:00", 2)
        val finish = Dates.shiftStamp("2026-10-04 00:45:00", 2)
        assertEquals(75 * 60L, Dates.secondsBetween(start, finish))
    }

    @Test fun movingBackwardsAndAcrossAMonthOrYear() {
        assertEquals("2026-09-30 18:00:00", Dates.shiftStamp("2026-10-02 18:00:00", -2))
        assertEquals("2027-01-01 00:10:00", Dates.shiftStamp("2026-12-31 00:10:00", 1))
    }

    @Test fun missingOrUnreadableStampsAreLeftAlone() {
        assertNull(Dates.shiftStamp(null, 3))
        assertEquals("not a stamp", Dates.shiftStamp("not a stamp", 3))
    }
}
