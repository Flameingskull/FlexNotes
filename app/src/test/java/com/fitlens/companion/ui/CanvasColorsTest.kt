package com.fitlens.companion.ui

import androidx.compose.ui.graphics.toArgb
import com.fitlens.companion.data.CanvasColors
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `report/` and `video/` read the brand as ARGB ints from `CanvasColors` (#320) instead of `Brand`. These pin that the
 * PDF and frames draw exactly the colours they drew through `Brand` before, so their goldens stay the same.
 */
class CanvasColorsTest {

    @Test
    fun brandIsBuiltFromCanvasColors() {
        assertEquals(CanvasColors.BLACK, Brand.Black.toArgb())
        assertEquals(CanvasColors.SURFACE, Brand.Surface.toArgb())
        assertEquals(CanvasColors.GOLD, Brand.Gold.toArgb())
        assertEquals(CanvasColors.GOLD_LIGHT, Brand.GoldLight.toArgb())
        assertEquals(CanvasColors.IVORY, Brand.Ivory.toArgb())
        assertEquals(CanvasColors.MUTED, Brand.Muted.toArgb())
        assertEquals(CanvasColors.HAIRLINE, Brand.Hairline.toArgb())
        assertEquals(CanvasColors.FALL, Brand.Fall.toArgb())
    }

    @Test
    fun withAlphaRoundsAsComposeDoes() {
        for (alpha in listOf(0f, 0.12f, 0.2f, 0.35f, 0.5f, 0.85f, 1f)) {
            assertEquals(Brand.Gold.copy(alpha = alpha).toArgb(), CanvasColors.withAlpha(CanvasColors.GOLD, alpha))
            assertEquals(Brand.Ivory.copy(alpha = alpha).toArgb(), CanvasColors.withAlpha(CanvasColors.IVORY, alpha))
        }
    }
}
