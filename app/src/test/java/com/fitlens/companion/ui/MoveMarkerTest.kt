package com.fitlens.companion.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A changed value keeps its triangle, with or without a good or bad verdict (#330). */
class MoveMarkerTest {

    private val series = Color(0xFF112233)
    private val good = Color(0xFF00AA00)
    private val bad = Color(0xFFAA0000)

    @Test
    fun aGoodMoveIsGreen() {
        assertEquals(good, moveTriangleColour(80.0, 81.0, true, series, good, bad))
    }

    @Test
    fun aBadMoveIsRed() {
        assertEquals(bad, moveTriangleColour(81.0, 80.0, false, series, good, bad))
    }

    @Test
    fun aMoveWithNoVerdictIsNeitherGreenNorRed() {
        // The plot passes the theme's muted text colour; the chart's own series colour is the brand red.
        val neutral = Brand.Muted
        val colours = ChartColors(Brand.Fall, Brand.Gold, Brand.Ivory)
        val up = moveTriangleColour(80.0, 81.0, null, neutral, colours.better, colours.worse)
        val down = moveTriangleColour(81.0, 80.0, null, neutral, colours.better, colours.worse)
        assertEquals(neutral, up)
        assertEquals(neutral, down)
        for (c in listOf(up, down)) {
            assertNotEquals(colours.better, c)
            assertNotEquals(colours.worse, c)
            assertNotEquals(colours.series, c)
            assertNotEquals(Brand.Fall, c)
        }
    }

    @Test
    fun theFirstPointAndAnUnchangedValueKeepThePlainMarker() {
        assertNull(moveTriangleColour(null, 80.0, true, series, good, bad))
        assertNull(moveTriangleColour(80.0, 80.0, true, series, good, bad))
        assertNull(moveTriangleColour(80.0, 80.0, null, series, good, bad))
    }
}
