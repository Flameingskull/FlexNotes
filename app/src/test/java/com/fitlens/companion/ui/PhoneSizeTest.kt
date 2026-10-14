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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The phone-size checks from #157 that don't need a phone, run on every build: each design component, drawn at
 * 320dp wide (the smallest phone FlexNotes supports) with Android's largest font size (200%), must
 *
 * - give every control a touch target of at least 48 × 48dp;
 * - show its text whole: no line cut off or ellipsised, so titles and labels wrap instead;
 * - fit the screen's width, with nothing pushed off the right-hand edge.
 *
 * The TalkBack, switch access and contrast checks in #157 still need a real phone.
 */
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
        val out = mutableListOf<String>()
        val px = compose.density.density
        val minTouch = MIN_TOUCH * px - 0.5f
        val right = compose.onNodeWithTag(TAG).fetchSemanticsNode().boundsInRoot.right + 0.5f

        compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach { node ->
            // Material's controls grow their own layout to the minimum touch size, so the node's size is its target.
            val w = node.size.width.toFloat()
            val h = node.size.height.toFloat()
            if (w < minTouch || h < minTouch) {
                out += "$name: \"${node.label()}\" has a ${dp(w, px)} × ${dp(h, px)}dp touch target"
            }
        }
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes().forEach { node ->
                val layouts = mutableListOf<TextLayoutResult>()
                node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
                if (layouts.firstOrNull()?.hasVisualOverflow == true) out += "$name: \"${node.label()}\" is cut off"
                // Unclipped, so text pushed past the edge counts even where a parent hides it.
                val end = node.positionInRoot.x + node.size.width
                if (end > right) out += "$name: \"${node.label()}\" runs ${dp(end - right, px)}dp off the screen"
            }
        return out
    }

    private fun SemanticsNode.label(): String =
        config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
            ?: config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
            ?: "(unlabelled)"

    private fun dp(value: Float, px: Float) = "%.0f".format(value / px)

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

    private companion object {
        const val TAG = "phone"
        const val WIDTH = 320
        const val FONT_SCALE = 2f
        const val MIN_TOUCH = 48f
    }
}
