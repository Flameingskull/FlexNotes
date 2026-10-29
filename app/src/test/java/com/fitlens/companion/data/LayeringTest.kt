package com.fitlens.companion.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Keeps the layers apart (#309, #320): `data/`, `report/` and `video/` never reach up into the screens in `ui/`, and
 * never use Compose. What they need from the app goes through `AppHooks`, `DisplayText.kt` and `CanvasBrand.kt`.
 *
 * Plain JVM test: it reads the Kotlin sources under `src/main/java`, relative to the module directory (the working
 * directory Gradle runs unit tests in), as `RawMaterialControlsTest` does.
 */
class LayeringTest {

    @Test
    fun dataReportAndVideoDontImportUiOrCompose() {
        val files = LAYERS.flatMap { dir -> File(SOURCES, dir).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
        assertTrue("No sources found under ${SOURCES.absolutePath}", files.size > 20)
        val found = mutableListOf<String>()
        for (file in files) {
            file.readLines().forEachIndexed { i, line ->
                val code = line.substringBefore("//")
                val trimmed = code.trim()
                if (trimmed.startsWith("*") || trimmed.startsWith("/*")) return@forEachIndexed
                FORBIDDEN.find(code)?.let { found += "${file.invariantSeparatorsPath.substringAfter("src/main/java/")}:${i + 1}: ${it.value}" }
            }
        }
        assertTrue(
            "data/, report/ and video/ must not use ui/ or Compose. Move the helper into data/ (DisplayText.kt, " +
                "CanvasBrand.kt) or pass it in through AppHooks:\n" + found.joinToString("\n"),
            found.isEmpty()
        )
    }

    private companion object {
        val SOURCES = File("src/main/java")
        val LAYERS = listOf("com/fitlens/companion/data", "com/fitlens/companion/report", "com/fitlens/companion/video")
        val FORBIDDEN = Regex("""com\.fitlens\.companion\.ui\b|androidx\.compose\.""")
    }
}
