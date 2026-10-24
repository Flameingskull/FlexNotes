package com.fitlens.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Water and caffeine (#221): times, bedtime and the caffeine still active then. */
class DrinksTest {

    @Test
    fun timesReadAsMinutes() {
        assertEquals(8 * 60 + 30, Drinks.minutesOf("08:30:15"))
        assertNull(Drinks.minutesOf("25:00"))
        assertNull(Drinks.minutesOf("later"))
    }

    @Test
    fun bedtimeAfterMidnightCountsAsThatNight() {
        assertEquals(23 * 60, Drinks.bedtimeMinutes("23:00"))
        assertEquals(24 * 60 + 30, Drinks.bedtimeMinutes("00:30"))
        assertEquals(23 * 60, Drinks.bedtimeMinutes("nonsense"))
    }

    @Test
    fun oneHalfLifeHalvesTheCaffeine() {
        val bed = 23 * 60
        val doses = listOf(Drinks.Dose(bed - 5 * 60, 100.0))
        assertEquals(50.0, Drinks.activeAt(doses, bed), 1e-9)
    }

    @Test
    fun drinksAddUpAndLaterOnesDoNotCount() {
        val bed = 22 * 60
        val doses = listOf(Drinks.Dose(bed - 10 * 60, 200.0), Drinks.Dose(bed, 40.0), Drinks.Dose(bed + 30, 95.0))
        // 200 × 0.25 + 40.
        assertEquals(90.0, Drinks.activeAt(doses, bed), 1e-9)
    }

    @Test
    fun namesAreRecognised() {
        assertTrue(Drinks.isDrink(" water "))
        assertTrue(Drinks.Kind.CAFFEINE.matches("CAFFEINE"))
    }
}
