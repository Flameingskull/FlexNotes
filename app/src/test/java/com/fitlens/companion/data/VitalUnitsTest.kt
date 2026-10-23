package com.fitlens.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vitals' units (#212) and reference ranges (#211): blood glucose and body temperature convert within their own kind
 * only, and a value is flagged against its measurement's range.
 */
class VitalUnitsTest {

    @Test
    fun glucoseConvertsBothWays() {
        assertEquals(90.08, VitalUnits.convert(5.0, "mmol/L", "mg/dL"), 0.001)
        assertEquals(5.0, VitalUnits.convert(90.08, "mg/dl", VitalUnits.MMOL), 0.01)
        assertEquals(5.0, MeasureUnits.convert(5.0, "mmol/L", "mmol/L"), 1e-9)
    }

    @Test
    fun temperatureConvertsBothWays() {
        assertEquals(98.6, VitalUnits.convert(37.0, "°C", "°F"), 1e-9)
        assertEquals(37.0, MeasureUnits.convert(98.6, "°F", "°C"), 1e-9)
        assertEquals(VitalUnits.CELSIUS, VitalUnits.of("C"))
        assertEquals(VitalUnits.FAHRENHEIT, VitalUnits.of(" fahrenheit "))
    }

    @Test
    fun kindsNeverMix() {
        assertFalse(MeasureUnits.sameKind("mmol/L", "°C"))
        assertFalse(MeasureUnits.sameKind("mg/dL", "kg"))
        assertTrue(MeasureUnits.sameKind("mg/dL", "mmol/L"))
        assertEquals(5.0, MeasureUnits.convert(5.0, "mmol/L", "°F"), 1e-9)
        assertNull(VitalUnits.of("mmHg"))
        assertEquals(listOf(VitalUnits.MMOL, VitalUnits.MGDL), MeasureUnits.choices("mmol/l"))
        assertTrue(MeasureUnits.choices("mmHg").isEmpty())
    }

    @Test
    fun shownConvertsTheRange() {
        val d = MeasurementDef("Blood glucose", "mmol/L", 0, 0, 0.0, true, displayUnit = "mg/dL", refLow = 4.0, refHigh = 7.0)
        val shown = MeasureUnits.shown(d, "kg", "cm")
        assertEquals("mg/dL", shown.unit)
        assertEquals(72.06, shown.refLow!!, 0.01)
        assertEquals(126.11, shown.refHigh!!, 0.01)
    }

    @Test
    fun valuesAreFlaggedAgainstTheRange() {
        val d = MeasurementDef("LDL", "mmol/L", 0, 0, 0.0, true, refLow = null, refHigh = 3.0)
        assertEquals(0, d.rangeFlag(1.0))
        assertEquals(0, d.rangeFlag(3.0))
        assertEquals(1, d.rangeFlag(3.2))
        val both = d.copy(refLow = 1.0)
        assertEquals(-1, both.rangeFlag(0.9))
        assertTrue(both.hasRange)
        assertFalse(d.copy(refHigh = null).hasRange)
    }
}
