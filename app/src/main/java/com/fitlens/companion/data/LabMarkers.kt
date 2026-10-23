package com.fitlens.companion.data

/**
 * Lab results (#211): common blood markers offered when a lab marker is made, each with the unit labs usually report
 * it in. They're suggestions only: any name and unit can be typed, and no reference range is filled in, because ranges
 * differ by lab, age and sex; the user copies theirs from the report. A marker is a body measurement marked
 * [MeasurementDef.lab], so it's logged, edited, graphed and exported like any other. Plain Kotlin.
 */
object LabMarkers {
    /** Name and usual unit, grouped as a lab report lists them. */
    val common: List<Pair<String, String>> = listOf(
        // Lipids
        "Total cholesterol" to "mmol/L",
        "LDL cholesterol" to "mmol/L",
        "HDL cholesterol" to "mmol/L",
        "Triglycerides" to "mmol/L",
        // Glucose
        "HbA1c" to "mmol/mol",
        "Fasting glucose" to "mmol/L",
        // Blood count
        "Haemoglobin" to "g/L",
        "Haematocrit" to "%",
        // Hormones
        "Total testosterone" to "nmol/L",
        "Free testosterone" to "pmol/L",
        "Oestradiol" to "pmol/L",
        "SHBG" to "nmol/L",
        "LH" to "IU/L",
        "FSH" to "IU/L",
        "Prolactin" to "mIU/L",
        "PSA" to "ng/mL",
        // Thyroid
        "TSH" to "mIU/L",
        "Free T4" to "pmol/L",
        // Liver
        "ALT" to "U/L",
        "AST" to "U/L",
        "GGT" to "U/L",
        // Kidney
        "Creatinine" to "µmol/L",
        "eGFR" to "mL/min/1.73m²",
        // Vitamins and iron
        "Vitamin D" to "nmol/L",
        "Vitamin B12" to "pmol/L",
        "Ferritin" to "µg/L",
        // Inflammation
        "CRP" to "mg/L"
    )

    /** The common markers not yet among [existing] names (ignoring capitals). */
    fun suggestions(existing: Collection<String>): List<Pair<String, String>> {
        val have = existing.map { it.trim().lowercase() }.toSet()
        return common.filter { it.first.lowercase() !in have }
    }
}
