package com.fitlens.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The one rule for whether a move in a body value is good, bad or has no colour (#330). */
class MeasurementGoalsTest {

    private fun v(type: Int, target: Double, from: Double, to: Double, plain: Boolean = false): Boolean? =
        MeasurementGoals.verdict(type, target, from, to, plain)

    @Test
    fun upIsGood() {
        assertEquals(true, v(MeasurementGoals.INCREASE, 0.0, 80.0, 81.0))
        assertEquals(false, v(MeasurementGoals.INCREASE, 0.0, 81.0, 80.0))
        assertEquals(true, v(MeasurementGoals.INCREASE, 0.0, 80.0, 81.0, plain = true))
        assertEquals(false, v(MeasurementGoals.INCREASE, 0.0, 81.0, 80.0, plain = true))
    }

    @Test
    fun downIsGood() {
        assertEquals(true, v(MeasurementGoals.DECREASE, 0.0, 81.0, 80.0))
        assertEquals(false, v(MeasurementGoals.DECREASE, 0.0, 80.0, 81.0))
        assertEquals(true, v(MeasurementGoals.DECREASE, 0.0, 81.0, 80.0, plain = true))
        assertEquals(false, v(MeasurementGoals.DECREASE, 0.0, 80.0, 81.0, plain = true))
    }

    @Test
    fun aSpecificValueIsCloserOrFurther() {
        assertEquals(true, v(MeasurementGoals.TARGET, 80.0, 85.0, 83.0))
        assertEquals(false, v(MeasurementGoals.TARGET, 80.0, 83.0, 85.0))
        assertEquals(true, v(MeasurementGoals.TARGET, 80.0, 75.0, 78.0))
        assertEquals(false, v(MeasurementGoals.TARGET, 80.0, 78.0, 75.0))
        assertEquals(true, v(MeasurementGoals.TARGET, 80.0, 85.0, 83.0, plain = true))
        assertEquals(false, v(MeasurementGoals.TARGET, 80.0, 83.0, 85.0, plain = true))
    }

    @Test
    fun noDirectionFollowsTheSetting() {
        assertEquals(true, v(MeasurementGoals.NONE, 0.0, 80.0, 81.0))
        assertEquals(false, v(MeasurementGoals.NONE, 0.0, 81.0, 80.0))
        assertNull(v(MeasurementGoals.NONE, 0.0, 80.0, 81.0, plain = true))
        assertNull(v(MeasurementGoals.NONE, 0.0, 81.0, 80.0, plain = true))
    }

    @Test
    fun aSpecificValueWithNoTargetFollowsTheSetting() {
        assertEquals(true, v(MeasurementGoals.TARGET, 0.0, 80.0, 81.0))
        assertNull(v(MeasurementGoals.TARGET, 0.0, 80.0, 81.0, plain = true))
    }

    @Test
    fun equalValuesNeverGetAColour() {
        for (type in MeasurementGoals.all) {
            for (plain in listOf(false, true)) {
                assertNull(v(type, 80.0, 80.0, 80.0, plain))
            }
        }
    }

    @Test
    fun equalDistanceEitherSideOfASpecificValueGetsNoColour() {
        for (plain in listOf(false, true)) {
            assertNull(v(MeasurementGoals.TARGET, 80.0, 82.0, 78.0, plain))
            assertNull(v(MeasurementGoals.TARGET, 80.0, 78.0, 82.0, plain))
        }
        assertEquals(true, v(MeasurementGoals.TARGET, 80.0, 82.0, 79.0))
        assertEquals(false, v(MeasurementGoals.TARGET, 80.0, 79.0, 82.0))
        assertNull(MeasurementGoals.isImprovement(MeasurementGoals.TARGET, 80.0, 82.0, 78.0))
    }

    @Test
    fun thePlainChoiceDefaultsToOff() {
        assertFalse(PortableSettings().plainNoGoal)
        assertTrue(PortableSettings().copy(plainNoGoal = true).plainNoGoal)
    }
}
