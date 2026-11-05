package com.fitlens.companion.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BarWidthTest {
    @Test
    fun fourWeeklyBarsOnAYearAreNarrowerThanTheirSpacing() {
        val w = barWidthFor(1000f, 365.0, listOf(0L, 7L, 14L, 21L), 2f, 28f)
        val spacing = 1000f * 7 / 365
        assertTrue("width should be less than spacing", w < spacing)
        assertTrue("width should be at least 2f", w >= 2f)
    }

    @Test
    fun denseBarsAreAsNarrowAsTheClosestPair() {
        val w = barWidthFor(1000f, 100.0, listOf(0L, 1L, 50L, 100L), 2f, 28f)
        assertEquals(7f, w, 0.01)
    }

    @Test
    fun aSingleBarTakesTheCapNotTheWholeAxis() {
        val w = barWidthFor(1000f, 30.0, listOf(5L), 2f, 28f)
        assertEquals(28f, w, 0.01)
    }

    @Test
    fun barsOnTheSameDayCountOnce() {
        val w1 = barWidthFor(1000f, 100.0, listOf(10L, 10L, 60L), 2f, 28f)
        val w2 = barWidthFor(1000f, 100.0, listOf(10L, 60L), 2f, 28f)
        assertEquals(w2, w1, 0.01)
    }
}
