package com.fitlens.companion.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.PortableSettings
import com.fitlens.companion.data.Settings
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The body value sheet on a 320dp phone, at normal and 200% font, at half height and full screen (#331, #323). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w320dp-h3200dp-mdpi")
class BodyEntrySheetLayoutTest : FunctionTest() {

    private fun problemsWith(full: Boolean, fontScale: Float, where: String): List<String> {
        Dates.clockSource = LongHistoryFixture.clock
        Settings.showWith(PortableSettings(bodyEntryFullScreen = full))
        val snap = LongHistoryFixture.snapshot()
        var scale by mutableStateOf(fontScale)
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, scale)) {
                FlexNotesTheme {
                    Box(Modifier.testTag(TAG).fillMaxSize().background(Brand.Black)) {
                        MeasurementEntrySheet(snap, LongHistoryFixture.END, initialName = LongHistoryFixture.BODYWEIGHT) {}
                    }
                }
            }
        }
        compose.waitForIdle()
        repeat(3) {
            Thread.sleep(100)
            compose.waitForIdle()
            compose.mainClock.autoAdvance = false
            compose.mainClock.advanceTimeBy(500)
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
        }
        return LayoutChecks.problems(compose, where, TAG, scrollingScreen = true)
    }

    private fun assertClean(problems: List<String>) =
        assertTrue("Layout problems:\n" + problems.joinToString("\n"), problems.isEmpty())

    @Test
    fun halfHeightAtNormalFont() = assertClean(problemsWith(full = false, fontScale = 1f, where = "Body value sheet, half height [320 dp, 100% font]"))

    @Test
    fun halfHeightAtDoubleFont() = assertClean(problemsWith(full = false, fontScale = 2f, where = "Body value sheet, half height [320 dp, 200% font]"))

    @Test
    fun fullScreenAtNormalFont() = assertClean(problemsWith(full = true, fontScale = 1f, where = "Body value sheet, full screen [320 dp, 100% font]"))

    @Test
    fun fullScreenAtDoubleFont() = assertClean(problemsWith(full = true, fontScale = 2f, where = "Body value sheet, full screen [320 dp, 200% font]"))

    private companion object {
        const val TAG = "body-entry-sheet"
    }
}
