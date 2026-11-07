package com.fitlens.companion.ui

import androidx.compose.ui.graphics.Color
import com.fitlens.companion.data.MeasurementDef
import com.fitlens.companion.data.MeasurementGoals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The one rule for whether a move in a body value is good, bad or has no colour, and its colour (#330). */
class MeasurementVerdictTest {

    private fun def(type: Int, target: Double = 0.0) = MeasurementDef("Weight", "kg", 0, type, target, true)

    @Test
    fun downIsGoodMakesAFallGood() {
        assertEquals(true, measurementVerdict(def(MeasurementGoals.DECREASE), false, 82.0, 81.0))
        assertEquals(false, measurementVerdict(def(MeasurementGoals.DECREASE), false, 81.0, 82.0))
    }

    @Test
    fun upIsGoodMakesARiseGood() {
        assertEquals(true, measurementVerdict(def(MeasurementGoals.INCREASE), false, 81.0, 82.0))
        assertEquals(false, measurementVerdict(def(MeasurementGoals.INCREASE), false, 82.0, 81.0))
    }

    @Test
    fun aNullMeasurementFollowsTheSetting() {
        assertEquals(true, measurementVerdict(null, false, 1.0, 2.0))
        assertNull(measurementVerdict(null, true, 1.0, 2.0))
    }

    @Test
    fun aMedicationWithNoDirectionBehavesLikeAnyOther() {
        val medDef = MeasurementDef("Dose", "mg", 0, MeasurementGoals.NONE, 0.0, true, medication = true)
        assertEquals(true, measurementVerdict(medDef, false, 5.0, 6.0))
        assertEquals(false, measurementVerdict(medDef, false, 6.0, 5.0))
    }

    @Test
    fun verdictColourMapsEachVerdict() {
        val gray = Color(0xFF888888)
        assertEquals(Brand.Rise, verdictColour(true, gray))
        assertEquals(Brand.Fall, verdictColour(false, gray))
        assertEquals(gray, verdictColour(null, gray))
    }
}
