package com.fitlens.companion.data

import com.fitlens.companion.data.BodyFat.Sex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.log10

/** Body fat (#153): the US Navy formula must give the published method's values exactly. */
class BodyFatTest {

    private fun ok(r: BodyFat.Result): Double = (r as BodyFat.Result.Ok).percent

    @Test
    fun manMatchesTheMetricDensityForm() {
        // 70 in tall, neck 15 in, waist 34 in, in centimetres.
        val p = ok(BodyFat.navy(Sex.MALE, 177.8, 38.1, 86.36, null))
        assertEquals(17.4347, p, 1e-3)
        // The Navy's published inch formula agrees to within a few tenths.
        val inches = 86.010 * log10(34.0 - 15.0) - 70.041 * log10(70.0) + 36.76
        assertEquals(inches, p, 0.3)
    }

    @Test
    fun womanMatchesTheMetricDensityForm() {
        // 65 in tall, neck 12.5 in, waist 28 in, hips 38 in, in centimetres.
        val p = ok(BodyFat.navy(Sex.FEMALE, 165.1, 31.75, 71.12, 96.52))
        assertEquals(26.3228, p, 1e-3)
        val inches = 163.205 * log10(28.0 + 38.0 - 12.5) - 97.684 * log10(65.0) - 78.387
        assertEquals(inches, p, 0.3)
    }

    @Test
    fun womenNeedHips() {
        assertTrue(BodyFat.navy(Sex.FEMALE, 165.0, 32.0, 70.0, null) is BodyFat.Result.Invalid)
        assertEquals(listOf("Height", "Neck", "Waist", "Hips"), BodyFat.inputs(Sex.FEMALE).map { it.label })
        assertEquals(listOf("Height", "Neck", "Waist"), BodyFat.inputs(Sex.MALE).map { it.label })
    }

    @Test
    fun impossibleInputsAreRefused() {
        assertTrue(BodyFat.navy(Sex.MALE, 180.0, 40.0, 38.0, null) is BodyFat.Result.Invalid)
        // Height typed in inches but read as centimetres.
        assertTrue(BodyFat.navy(Sex.MALE, 70.0, 38.0, 86.0, null) is BodyFat.Result.Invalid)
        // A waist barely larger than the neck gives an impossible result.
        assertTrue(BodyFat.navy(Sex.MALE, 180.0, 40.0, 41.0, null) is BodyFat.Result.Invalid)
    }

    @Test
    fun recognisesBodyFatAndInputNames() {
        assertTrue(BodyFat.isBodyFat("Body Fat"))
        assertTrue(BodyFat.isBodyFat("body fat %"))
        assertFalse(BodyFat.isBodyFat("Bodyweight"))
        assertTrue(BodyFat.Input.HIPS.matches(" Hip "))
        assertTrue(BodyFat.Input.WAIST.matches("WAIST"))
    }

    private fun rec(name: String, date: String, value: Double, unit: String = "cm") =
        MRecord(0, name, unit, date, "08:00:00", value, null, Sources.FLEXNOTES)

    @Test
    fun anEstimateUsesTheLatestInputsOnOrBeforeItsDate() {
        // #194: the day being logged chooses the inputs, so a back-dated entry uses the ones it had then.
        val records = mapOf(
            "Neck" to listOf(rec("Neck", "2026-09-01", 40.0), rec("Neck", "2026-10-10", 38.0)),
            "Waist" to listOf(rec("Waist", "2026-09-01", 90.0), rec("Waist", "2026-10-10", 34.0, "in")),
            "Height" to listOf(rec("Height", "2026-01-01", 180.0))
        )
        val names = listOf("Bodyweight", "Neck", "Waist", "Height", "Body fat")
        val now = BodyFat.estimateOn("2026-10-16", BodyFat.Sex.MALE, records, names, null)!!
        assertEquals(listOf(38.0, 34.0, 180.0).sorted(), now.used.map { it.record!!.value }.sorted())
        val inches = (BodyFat.navy(BodyFat.Sex.MALE, 180.0, 38.0, 34.0 * 2.54, null) as BodyFat.Result.Ok).percent
        assertEquals(Math.round(inches * 10) / 10.0, now.percent!!, 1e-9)
        assertTrue(now.used.first { it.input == BodyFat.Input.HEIGHT }.stale)
        assertFalse(now.used.first { it.input == BodyFat.Input.NECK }.stale)

        val before = BodyFat.estimateOn("2026-09-15", BodyFat.Sex.MALE, records, names, null)!!
        assertEquals(40.0, before.used.first { it.input == BodyFat.Input.NECK }.record!!.value, 0.0)
        assertEquals(90.0, before.used.first { it.input == BodyFat.Input.WAIST }.record!!.value, 0.0)

        // Nothing logged by then: the missing inputs are named and there's no figure.
        val early = BodyFat.estimateOn("2026-08-01", BodyFat.Sex.MALE, records, names, null)!!
        assertEquals(listOf(BodyFat.Input.NECK, BodyFat.Input.WAIST), early.missing)
        assertNull(early.percent)
    }

    @Test
    fun anEstimateFallsBackToTheProfileHeightAndNeedsHipsForWomen() {
        val records = mapOf("Neck" to listOf(rec("Neck", "2026-10-10", 33.0)), "Waist" to listOf(rec("Waist", "2026-10-10", 72.0)))
        val names = listOf("Neck", "Waist")
        val e = BodyFat.estimateOn("2026-10-16", BodyFat.Sex.FEMALE, records, names, 168.0)!!
        assertEquals(listOf(BodyFat.Input.HIPS), e.missing)
        assertEquals(168.0, e.used.first { it.input == BodyFat.Input.HEIGHT }.profileCm!!, 0.0)
        assertNull(e.percent)
        assertNull(BodyFat.estimateOn("2026-10-16", null, records, names, 168.0))
    }
}
