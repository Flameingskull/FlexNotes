package com.fitlens.companion.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Screenshot tests for the shared design components (#95), rendered on the JVM by Robolectric's native graphics, so
 * there's no emulator. Each component is drawn at FitNotes's two common phone widths (360dp and 411dp) and at font
 * scale 1.0 and 2.0, with made-up data only.
 *
 * The golden images live in `app/src/test/screenshots/`. A component whose golden exists is verified against it, and
 * a change fails the build, with the diff in `app/build/outputs/roborazzi/` (CI keeps it as an artifact). A component
 * without a golden is recorded instead; CI keeps the new images as an artifact so they can be committed. To accept an
 * intended change, delete the old golden and commit the newly recorded one.
 */
@OptIn(ExperimentalRoborazziApi::class, ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w420dp-h2000dp-xhdpi")
class ScreenshotTest {

    @get:Rule val compose = createComposeRule()

    private data class Variant(val width: Int, val fontScale: Float, val label: String)

    private val variants = listOf(
        Variant(360, 1f, "360dp"),
        Variant(411, 1f, "411dp"),
        Variant(360, 2f, "360dp_font2"),
        Variant(411, 2f, "411dp_font2")
    )

    /** Draws [content] once, then captures it in every variant (a compose rule allows only one setContent per test). */
    private fun shoot(name: String, content: @Composable () -> Unit) {
        GOLDEN_DIR.mkdirs()
        var variant by mutableStateOf(variants.first())
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, variant.fontScale)) {
                FlexNotesTheme {
                    Box(Modifier.testTag(TAG).width(variant.width.dp).background(Brand.Black)) { content() }
                }
            }
        }
        for (v in variants) {
            compose.runOnIdle { variant = v }
            compose.waitForIdle()
            val golden = File(GOLDEN_DIR, "${name}_${v.label}.png")
            val options = RoborazziOptions(
                taskType = if (golden.exists()) RoborazziTaskType.Verify else RoborazziTaskType.Record,
                // Anti-aliasing can move a handful of pixels between runs; a real layout change moves far more.
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.001f)
            )
            compose.onNodeWithTag(TAG).captureRoboImage(golden, roborazziOptions = options)
        }
    }

    @Test fun topBar() = shoot("top_bar") { DesignSamples.topBar() }

    @Test fun exerciseCard() = shoot("exercise_card") { DesignSamples.exerciseCard() }

    @Test fun setRows() = shoot("set_rows") { DesignSamples.setRows() }

    @Test fun statTiles() = shoot("stat_tiles") { DesignSamples.statTiles() }

    @Test fun settingsRows() = shoot("settings_rows") { DesignSamples.settingsRows() }

    @Test fun settingsRowVariants() = shoot("settings_row_variants") { DesignSamples.settingsRowVariants() }

    @Test fun tabsAndButtons() = shoot("tabs_buttons") { DesignSamples.tabsAndButtons() }

    private companion object {
        const val TAG = "screenshot"
        /** Relative to the module directory, the working directory Gradle runs unit tests in. */
        val GOLDEN_DIR = File("src/test/screenshots")
    }
}
