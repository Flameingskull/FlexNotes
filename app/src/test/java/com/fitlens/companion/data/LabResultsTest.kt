package com.fitlens.companion.data

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

/** Lab results from Health Connect's medical records (#225): FHIR Observations read as results. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LabResultsTest {
    private val utc = ZoneId.of("UTC")

    private val ldl = """
        {"resourceType":"Observation","id":"obs-1","status":"final",
         "category":[{"coding":[{"system":"http://terminology.hl7.org/CodeSystem/observation-category","code":"laboratory"}]}],
         "code":{"coding":[{"system":"http://loinc.org","code":"18262-6","display":"LDL Cholesterol"}],"text":"LDL cholesterol"},
         "effectiveDateTime":"2026-03-04T08:15:00+01:00",
         "valueQuantity":{"value":2.9,"unit":"mmol/L"},
         "referenceRange":[{"low":{"value":0.5,"unit":"mmol/L"},"high":{"value":3.0,"unit":"mmol/L"}}]}
    """.trimIndent()

    @Test
    fun anObservationBecomesAResult() {
        val r = LabResults.parse(ldl, "source-a", utc)!!
        assertEquals("source-a/obs-1", r.key)
        assertEquals("LDL cholesterol", r.name)
        assertEquals("mmol/L", r.unit)
        // 08:15 at +01:00 is 07:15 UTC.
        assertEquals("2026-03-04", r.date)
        assertEquals("07:15:00", r.time)
        assertEquals(2.9, r.value, 1e-9)
        assertEquals(0.5, r.refLow!!, 1e-9)
        assertEquals(3.0, r.refHigh!!, 1e-9)
    }

    @Test
    fun theCodingNamesItWhenThereIsNoText() {
        val json = """{"resourceType":"Observation","id":"x","code":{"coding":[{"display":"Ferritin"}]},
            "effectiveDateTime":"2026-01-02","valueQuantity":{"value":80,"code":"ug/L"}}"""
        val r = LabResults.parse(json, "s", utc)!!
        assertEquals("Ferritin", r.name)
        assertEquals("ug/L", r.unit)
        assertEquals("00:00:00", r.time)
        assertNull(r.refLow)
    }

    @Test
    fun textResultsAndOtherResourcesAreLeftOut() {
        val text = """{"resourceType":"Observation","code":{"text":"Covid PCR"},"effectiveDateTime":"2026-01-02","valueString":"negative"}"""
        val patient = """{"resourceType":"Patient","id":"p"}"""
        val undated = """{"resourceType":"Observation","code":{"text":"CRP"},"valueQuantity":{"value":3}}"""
        val withdrawn = ldl.replace("\"final\"", "\"entered-in-error\"")
        listOf(text, patient, undated, withdrawn, "not json").forEach { assertNull(it, LabResults.parse(it, "s", utc)) }
    }

    @Test
    fun seenKeysRoundTrip() {
        val stored = LabResults.store(setOf("a/1"), listOf("b/2", "a/1"))
        assertEquals(setOf("a/1", "b/2"), LabResults.seen(stored))
        assertEquals(emptySet<String>(), LabResults.seen(null))
    }
}
