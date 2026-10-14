package com.fitlens.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The measured TDEE (#176): intake against the bodyweight trend, and when there's too little to measure. */
class IntakeTest {

    private val end = 20_000L

    /** [days] days of [kcal] before [end]. */
    private fun intake(days: Int, kcal: Double) = (1..days).associate { (end - it) to kcal }

    @Test
    fun steadyWeightMeansTdeeIsTheIntake() {
        val weights = listOf(end - 27 to 80.0, end - 14 to 80.0, end to 80.0)
        val (m, p) = Intake.measure(intake(20, 2500.0), weights, end)
        assertTrue(p.enough)
        assertEquals(2500.0, m!!.tdee, 1e-6)
        assertEquals(0.0, m.kgPerWeek, 1e-9)
    }

    @Test
    fun losingWeightAddsTheReleasedEnergy() {
        // 0.5 kg a week lost: 0.5 × 7,700 / 7 = 550 kcal a day more than eaten.
        val weights = (0..4).map { w -> (end - 28 + w * 7L) to 80.0 - 0.5 * w }
        val (m, _) = Intake.measure(intake(28, 2000.0), weights, end)
        assertEquals(-0.5, m!!.kgPerWeek, 1e-9)
        assertEquals(2550.0, m.tdee, 1e-6)
    }

    @Test
    fun theTrendLineIgnoresOneHeavyMorning() {
        val weights = listOf(end - 21 to 80.0, end - 14 to 80.0, end - 13 to 81.5, end - 7 to 80.0, end to 80.0)
        val slope = Intake.slope(weights)!!
        // A 1.5 kg spike on one morning barely moves the fitted line.
        assertTrue(kotlin.math.abs(slope * 7) < 0.15)
    }

    @Test
    fun tooFewDaysOrWeighInsMeasureNothing() {
        val weights = listOf(end - 20 to 80.0, end - 10 to 80.0, end to 80.0)
        val (few, p) = Intake.measure(intake(10, 2500.0), weights, end)
        assertNull(few)
        assertEquals(10, p.intakeDays)
        assertFalse(p.enough)
        // Weigh-ins only a week apart.
        val (close, q) = Intake.measure(intake(20, 2500.0), listOf(end - 7 to 80.0, end - 3 to 80.0, end to 80.0), end)
        assertNull(close)
        assertEquals(7, q.spanDays)
    }

    @Test
    fun theDayItselfAndOldDaysAreLeftOut() {
        val kcal = intake(28, 2500.0) + mapOf(end to 9000.0, end - 40 to 9000.0)
        val weights = listOf(end - 27 to 80.0, end - 14 to 80.0, end to 80.0)
        val (m, p) = Intake.measure(kcal, weights, end)
        assertEquals(28, p.intakeDays)
        assertEquals(2500.0, m!!.tdee, 1e-6)
    }

    @Test
    fun anImpossibleResultIsNotATdee() {
        // 3 kg a week gained on 1,500 kcal a day can't happen.
        val weights = listOf(end - 21 to 80.0, end - 14 to 83.0, end - 7 to 86.0, end to 89.0)
        val (m, p) = Intake.measure(intake(20, 1500.0), weights, end)
        assertTrue(p.enough)
        assertNull(m)
    }

    @Test
    fun intakeNamesMatchWhateverTheirCapitals() {
        assertTrue(Intake.isIntake("calories EATEN "))
        assertTrue(Intake.Kind.PROTEIN.matches("Protein eaten"))
        assertFalse(Intake.isIntake("Body fat"))
    }
}
