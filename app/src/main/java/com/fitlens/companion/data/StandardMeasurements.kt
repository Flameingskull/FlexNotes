package com.fitlens.companion.data

/**
 * The standard body measurements (#27), for people who start without a FitNotes backup. They're offered in first-run
 * setup and on the Measurements screen, and added as FlexNotes's own measurements. A name that already exists (ignoring
 * capitals), imported or not, is left as it is, so adding the set twice or after an import changes nothing.
 */
object StandardMeasurements {
    /** Name and unit, in the order they're listed. Lengths use centimetres until #7 adds length units. */
    val all: List<Pair<String, String>> = listOf(
        "Bodyweight" to "kg",
        "Body fat" to "%",
        // Height is one of the body fat formula's inputs (#153).
        "Height" to "cm",
        "Waist" to "cm",
        "Chest" to "cm",
        "Hips" to "cm",
        "Arms" to "cm",
        "Thighs" to "cm",
        "Calves" to "cm",
        "Neck" to "cm",
        "Shoulders" to "cm"
    )

    /**
     * The vitals (#212): blood pressure, blood glucose, blood oxygen, HRV and body temperature, the names Health Connect
     * fills, so values typed by hand and read from it share one measurement. Offered by "Add vitals".
     */
    val vitals: List<Pair<String, String>> get() = HealthConnect.Names.vitals

    /** The measurements of [set] (the standard ones unless given) not yet among [existing] names (ignoring capitals). */
    fun missing(existing: Collection<String>, set: List<Pair<String, String>> = all): List<Pair<String, String>> {
        val have = existing.map { it.trim().lowercase() }.toSet()
        return set.filter { it.first.lowercase() !in have }
    }
}
