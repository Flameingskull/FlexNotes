package com.fitlens.companion.data

/**
 * Medications (#199): measurements marked `measurement.medication`, shown only on the Medications screen. Plain
 * Kotlin, tested in `MedicationsTest`.
 *
 * The split itself is [BodyPart]: the body area holds the body measurements, and [BodyPart.medications] the
 * medications, from the same rows. A value is stored once, so switching a measurement moves its whole history.
 */
object Medications {
    /**
     * Whether [name] may be marked as a medication. The measurements other features read by name stay body
     * measurements, or switching one would quietly empty them: bodyweight and body fat (energy, relative strength,
     * Health Connect writes), height (the body fat and energy formulas), what was eaten (the Calories card), steps
     * and the values only Health Connect gives (active calories, resting heart rate, sleep).
     */
    fun canBeMedication(name: String): Boolean {
        val n = name.trim()
        if (n.isEmpty()) return false
        return !(
            n.equals(HealthConnect.Names.BODYWEIGHT, true) || n.equals("Body Weight", true) ||
                BodyFat.isBodyFat(n) || BodyFat.Input.HEIGHT.matches(n) || Intake.isIntake(n) ||
                n.equals(Steps.NAME, true) || n.equals(HealthConnect.Names.ACTIVE, true) ||
                n.equals(HealthConnect.Names.RESTING_HR, true) || n.equals(HealthConnect.Names.SLEEP, true) ||
                HealthConnect.Names.vitals.any { n.equals(it.first, true) }
            )
    }

    /** Units offered when a medication is made (#200). Any other text can be typed. */
    val UNITS: List<String> = listOf("mg", "mcg", "g", "mL", "IU", "units", "tablets", "capsules", "drops", "puffs")
}
