package com.fitlens.companion.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Keeps the `Fit` controls the only way to draw these Material controls (#306). Material 3 moves its default colours
 * between releases (the outlined button's text went grey in the platform update, #293), so every control is drawn
 * through a wrapper in `ui/design/FitControls.kt` that pins its colours. A raw Material call anywhere outside
 * `ui/design/` brings the default back, so it fails here.
 *
 * Plain JVM test: it reads the Kotlin sources under `src/main/java`, relative to the module directory (the working
 * directory Gradle runs unit tests in, as for `ScreenshotTest`'s goldens). Add a name to [RAW] when its wrapper has
 * every call site moved.
 */
class RawMaterialControlsTest {

    @Test
    fun noRawMaterialControlsOutsideTheDesignPackage() {
        val files = SOURCES.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("No sources found under ${SOURCES.absolutePath}", files.size > 50)
        val found = mutableListOf<String>()
        for (file in files) {
            val path = file.invariantSeparatorsPath
            if ("/ui/design/" in path) continue
            file.readLines().forEachIndexed { i, line ->
                val code = line.substringBefore("//")
                val trimmed = code.trim()
                if (trimmed.startsWith("*") || trimmed.startsWith("/*")) return@forEachIndexed
                CALL.findAll(code).forEach { m ->
                    found += "${path.substringAfter("src/main/java/")}:${i + 1}: ${m.groupValues[1]}( should be Fit${m.groupValues[1]}("
                }
            }
        }
        assertTrue(
            "Raw Material controls draw Material's default colours. Use the Fit wrapper from ui/design/FitControls.kt:\n" +
                found.joinToString("\n"),
            found.isEmpty()
        )
    }

    private companion object {
        val SOURCES = File("src/main/java")

        /** The Material controls that have a `Fit` wrapper. Every Material control #306 wraps is listed. */
        val RAW = listOf(
            "Switch", "Slider", "Checkbox", "RadioButton", "AssistChip", "SuggestionChip",
            "LinearProgressIndicator", "Card", "AlertDialog", "FilterChip", "TextButton"
        )

        /**
         * A call of one of [RAW] by its Material name: not `FitSwitch(` or `SegmentedSwitch(` (a letter before it), not a
         * member call such as `Role.Switch(` or `x.Card(` (a dot before it).
         */
        val CALL = Regex("""(?<![\w.])(${RAW.joinToString("|")})\(""")
    }
}
