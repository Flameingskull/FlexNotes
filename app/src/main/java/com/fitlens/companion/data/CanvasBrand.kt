package com.fitlens.companion.data

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.fitlens.companion.R

/*
 * The brand as plain ARGB ints and Android typefaces (#320), for what is drawn on an Android `Canvas` (the PDF reports,
 * slideshow frames, share images), so `report/` and `video/` never import `ui/`. `ui.Brand` builds its matching
 * colours from these, so the two can't drift apart.
 */

/** The brand colours canvas drawers use, as ARGB ints (the same values as `ui.Brand`). */
object CanvasColors {
    const val BLACK = 0xFF050505.toInt()
    const val SURFACE = 0xFF141414.toInt()
    const val GOLD = 0xFFF2BE22.toInt()
    const val GOLD_LIGHT = 0xFFFFDB6E.toInt()
    const val IVORY = 0xFFF7F3EA.toInt()
    const val MUTED = 0xFFBDB6A8.toInt()
    const val HAIRLINE = 0xFF38342B.toInt()
    const val FALL = 0xFFF05E62.toInt()

    /** [argb] at [alpha] (0 to 1), rounded as Compose's `Color.copy(alpha)` rounds it. */
    fun withAlpha(argb: Int, alpha: Float): Int = ((alpha * 255f + 0.5f).toInt() shl 24) or (argb and 0x00FFFFFF)
}

/**
 * Manrope for the screens drawn on an Android `Canvas` (share images, the PDF report, video frames), so they match
 * the app (#135). [init] runs from `App`; before it (or in JVM tests) the system sans-serif stands in.
 */
object BrandFonts {
    @Volatile private var regular: Typeface? = null
    @Volatile private var bold: Typeface? = null

    fun init(context: Context) {
        regular = runCatching { ResourcesCompat.getFont(context, R.font.manrope_regular) }.getOrNull()
        bold = runCatching { ResourcesCompat.getFont(context, R.font.manrope_bold) }.getOrNull()
    }

    fun typeface(bold: Boolean = false, italic: Boolean = false): Typeface {
        val base = (if (bold) this.bold else regular) ?: Typeface.SANS_SERIF
        // Manrope has no italic: Android slants it, and only fakes bold when the bold file is missing.
        val style = when {
            italic && bold && this.bold == null -> Typeface.BOLD_ITALIC
            italic -> Typeface.ITALIC
            bold && this.bold == null -> Typeface.BOLD
            else -> Typeface.NORMAL
        }
        return if (style == Typeface.NORMAL) base else Typeface.create(base, style)
    }
}
