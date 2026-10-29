package com.fitlens.companion.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The phone-size checks from #157 that don't need a phone, run on every build: each design component, drawn at
 * 320dp wide (the smallest phone FlexNotes supports) with Android's largest font size (200%), must
 *
 * - give every control a touch target of at least 48 × 48dp (a smaller control counts when Compose can stretch its
 *   touch area to 48dp without running into another control);
 * - show its text whole: not clipped by a parent or cut short with an ellipsis, so titles and labels wrap instead;
 * - keep text apart: no two pieces of text drawn over each other;
 * - fit the screen's width, with nothing pushed off the right-hand edge.
 *
 * The TalkBack, switch access and contrast checks in #157 still need a real phone.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
// Real text measurement, as in the screenshot tests, so a line that doesn't fit is seen.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w420dp-h2000dp-xhdpi")
class PhoneSizeTest {

    @get:Rule val compose = createComposeRule()

    /** What failed, one line per problem, so a single run lists all of them. */
    private fun problems(name: String, content: @Composable () -> Unit): List<String> {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, FONT_SCALE)) {
                FlexNotesTheme {
                    Box(Modifier.testTag(TAG).width(WIDTH.dp).background(Brand.Black)) { content() }
                }
            }
        }
        compose.waitForIdle()
        // The rules live in LayoutChecks, shared with the whole-screen checks (#323).
        val out = LayoutChecks.problems(compose, name, TAG)
        // Kept for a look at 320dp; not compared (the screenshot tests compare their own widths).
        File("build/outputs/roborazzi/phone").mkdirs()
        compose.onNodeWithTag(TAG).captureRoboImage(
            File("build/outputs/roborazzi/phone/${name.replace(' ', '_')}.png"),
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record)
        )
        return out
    }

    private fun check(name: String, content: @Composable () -> Unit) {
        val found = problems(name, content)
        assertTrue("At ${WIDTH}dp and ${(FONT_SCALE * 100).toInt()}% font:\n" + found.joinToString("\n"), found.isEmpty())
    }

    @Test fun topBar() = check("Top bar") { DesignSamples.topBar() }
    @Test fun exerciseCard() = check("Exercise card") { DesignSamples.exerciseCard() }
    @Test fun setRows() = check("Set rows") { DesignSamples.setRows() }
    @Test fun statTiles() = check("Stat tiles") { DesignSamples.statTiles() }
    @Test fun settingsRows() = check("Settings rows") { DesignSamples.settingsRows() }
    @Test fun settingsRowVariants() = check("Settings row variants") { DesignSamples.settingsRowVariants() }
    @Test fun tabsAndButtons() = check("Tabs and buttons") { DesignSamples.tabsAndButtons() }
    @Test fun materialDefaults() = check("Material defaults") { DesignSamples.materialDefaults() }

    private companion object {
        const val TAG = "phone"
        const val WIDTH = 320
        const val FONT_SCALE = 2f
    }
}
