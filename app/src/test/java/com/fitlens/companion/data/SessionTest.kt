package com.fitlens.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sessions (#240) and cardio zones (#241): the maths and the stored values, with worked examples. */
class SessionTest {

    @Test
    fun sessionLoadIsRatingTimesMinutes() {
        // RPE 7 for 60 minutes is a load of 420.
        assertEquals(420.0, SessionLoad.of(7.0, 3600)!!, 1e-9)
        assertEquals(375.0, SessionLoad.of(7.5, 3000)!!, 1e-9)
        // A rest rating of 0 is a real value: no load, but it is recorded.
        assertEquals(0.0, SessionLoad.of(0.0, 3600)!!, 1e-9)
        // A session with no rating doesn't count.
        assertNull(SessionLoad.of(null, 3600))
    }

    @Test
    fun hardMinutesNeverExceedTheSession() {
        assertEquals(25.0, SessionLoad.hardMinutes(1500, 3600)!!, 1e-9)
        assertEquals(30.0, SessionLoad.hardMinutes(5400, 1800)!!, 1e-9)
        assertNull(SessionLoad.hardMinutes(null, 3600))
    }

    @Test
    fun tanakaEstimateIsTwoOhEightMinusPointSevenTimesAge() {
        assertEquals(180, CardioZones.tanakaMaxHr(40))
        // 208 - 0.7 * 33 = 184.9, rounded to a whole beat.
        assertEquals(185, CardioZones.tanakaMaxHr(33))
    }

    @Test
    fun ageOnTheSessionsDateDecidesTheEstimate() {
        // Born 1989-10-27: 36 on 2026-10-26 (208 - 25.2 = 182.8) and 37 the next day (208 - 25.9 = 182.1).
        val before = CardioZones.maxHr(null, "1989-10-27", "2026-10-26")!!
        assertEquals(183, before.bpm)
        assertTrue(before.estimated)
        assertEquals(182, CardioZones.maxHr(null, "1989-10-27", "2026-10-27")!!.bpm)
    }

    @Test
    fun ownMaxHeartRateComesFirstAndIsNotAnEstimate() {
        val own = CardioZones.maxHr(190, "1990-06-15", "2026-10-26")!!
        assertEquals(190, own.bpm)
        assertFalse(own.estimated)
    }

    @Test
    fun withNeitherThereIsNoMaxAndNothingIsGuessed() {
        assertNull(CardioZones.maxHr(null, null, "2026-10-26"))
        assertNull(CardioZones.maxHr(null, "not a date", "2026-10-26"))
        // A birth date after the session gives no age.
        assertNull(CardioZones.maxHr(null, "2030-01-01", "2026-10-26"))
    }

    @Test
    fun maxHeartRateOutsideTheRangeIsIgnored() {
        assertEquals(100..230, CardioZones.maxHrRange)
        assertNull(CardioZones.maxHr(99, null, "2026-10-26"))
        assertNull(CardioZones.maxHr(231, null, "2026-10-26"))
        assertEquals(100, CardioZones.maxHr(100, null, "2026-10-26")!!.bpm)
        assertEquals(230, CardioZones.maxHr(230, null, "2026-10-26")!!.bpm)
        // An out-of-range figure falls back to the estimate when there is a date of birth.
        assertTrue(CardioZones.maxHr(250, "1990-06-15", "2026-10-26")!!.estimated)
    }

    @Test
    fun zoneRangesFollowThePercentages() {
        assertEquals(60..70, CardioZones.percentRange(2))
        assertEquals(90..100, CardioZones.percentRange(5))
        // 190 bpm max: zone 3 is 70 to 80 percent.
        assertEquals(133..152, CardioZones.bpmRange(3, 190))
        assertEquals(171..190, CardioZones.bpmRange(5, 190))
    }

    @Test
    fun storedValuesNeverChangeMeaning() {
        assertEquals(listOf(0, 1, 2, 3), SessionIntensity.all)
        assertEquals(listOf(0.8, 1.0, 1.2, 1.4), SessionIntensity.all.map { SessionIntensity.multiplier(it) })
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6), SessionTypes.all)
        assertEquals(10, ExerciseTypes.SESSION)
        assertNull(SessionIntensity.valid(7))
        assertNull(SessionTypes.valid(-1))
        assertNull(CardioZones.valid(0))
        assertEquals(5, CardioZones.valid(5))
    }

    @Test
    fun aSessionRecordsATimeButNoWeightRepsOrDistance() {
        assertTrue(ExerciseTypes.isSession(ExerciseTypes.SESSION))
        assertTrue(ExerciseTypes.usesDuration(ExerciseTypes.SESSION))
        assertFalse(ExerciseTypes.usesWeight(ExerciseTypes.SESSION))
        assertFalse(ExerciseTypes.usesReps(ExerciseTypes.SESSION))
        assertFalse(ExerciseTypes.usesDistance(ExerciseTypes.SESSION))
        assertTrue(ExerciseTypes.SESSION in ExerciseTypes.all)
        assertEquals("Session", ExerciseTypes.label(ExerciseTypes.SESSION))
    }

    @Test
    fun aPredefinedSessionWithOnlyAZoneIsNotEmpty() {
        assertTrue(PlannedSet().isEmpty)
        assertFalse(PlannedSet(zone = 3).isEmpty)
        assertFalse(PlannedSet(liveSec = 600).isEmpty)
    }

    @Test
    fun zoneStandsInForIntensityInCalories() {
        // Zones 1 and 2 are light, 3 moderate, 4 and 5 vigorous.
        assertEquals(Energy.Effort.LIGHT, Energy.Effort.fromRpe(Energy.zoneRpe(1)))
        assertEquals(Energy.Effort.LIGHT, Energy.Effort.fromRpe(Energy.zoneRpe(2)))
        assertEquals(Energy.Effort.MODERATE, Energy.Effort.fromRpe(Energy.zoneRpe(3)))
        assertEquals(Energy.Effort.VIGOROUS, Energy.Effort.fromRpe(Energy.zoneRpe(4)))
        assertEquals(Energy.Effort.VIGOROUS, Energy.Effort.fromRpe(Energy.zoneRpe(5)))
    }
}
