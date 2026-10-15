package com.fitlens.companion.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Contrast of the `Brand` colours (#157, #49), checked on every build so it no longer needs a phone: text needs
 * 4.5:1 against every background it's drawn on (WCAG 2.1 AA, 1.4.3), and the outline that defines a control 3:1
 * (1.4.11). A new text colour or background belongs in these lists.
 */
class ContrastTest {
    /** The page, cards, raised glass, top bar and primary button. */
    private val backgrounds = mapOf(
        "Black" to Brand.Black,
        "Onyx" to Brand.Onyx,
        "Surface" to Brand.Surface,
        "SurfaceHigh" to Brand.SurfaceHigh,
        "SurfaceHighest" to Brand.SurfaceHighest,
        "Graphite" to Brand.Graphite
    )

    /** Colours that carry text, including rises and falls (`deltaColour`). */
    private val text = mapOf(
        "Ivory" to Brand.Ivory,
        "Muted" to Brand.Muted,
        "Gold" to Brand.Gold,
        "GoldLight" to Brand.GoldLight,
        "Rise" to Brand.Rise,
        "Fall" to Brand.Fall
    )

    /** Text on a coloured container: chosen items, destructive buttons and the gold primary button. */
    private val pairs = listOf(
        Triple("GoldLight on GoldDusk", Brand.GoldLight, Brand.GoldDusk),
        Triple("Gold on GoldDusk", Brand.Gold, Brand.GoldDusk),
        Triple("GoldLight on Wine", Brand.GoldLight, Brand.Wine),
        Triple("Black on Gold", Brand.Black, Brand.Gold)
    )

    private fun channel(c: Float): Double = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    /** WCAG relative luminance of an opaque colour. */
    private fun luminance(c: Color): Double = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    private fun ratio(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    @Test
    fun textIsReadableOnEveryBackground() {
        val failures = buildList {
            for ((t, fg) in text) for ((b, bg) in backgrounds) {
                val r = ratio(fg, bg)
                if (r < 4.5) add("$t on $b: ${"%.2f".format(r)}:1")
            }
            for ((name, fg, bg) in pairs) {
                val r = ratio(fg, bg)
                if (r < 4.5) add("$name: ${"%.2f".format(r)}:1")
            }
        }
        assertTrue("Text below 4.5:1: $failures", failures.isEmpty())
    }

    @Test
    fun controlOutlinesStandOut() {
        val failures = backgrounds.mapNotNull { (b, bg) ->
            val r = ratio(Brand.Outline, bg)
            if (r < 3.0) "Outline on $b: ${"%.2f".format(r)}:1" else null
        }
        assertTrue("Control outlines below 3:1: $failures", failures.isEmpty())
    }
}
