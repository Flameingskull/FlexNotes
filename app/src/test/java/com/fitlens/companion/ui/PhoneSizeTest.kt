package com.fitlens.companion.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
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
import androidx.compose.ui.unit.toSize
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
        val out = mutableListOf<String>()
        val px = compose.density.density
        val minTouch = MIN_TOUCH * px - 0.5f
        val right = compose.onNodeWithTag(TAG).fetchSemanticsNode().boundsInRoot.right + 0.5f

        // Compose stretches a smaller control's touch area to 48dp, as long as no other control is in the way.
        val controls = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().map { it to it.unclipped() }
        controls.forEach { (node, r) ->
            if (r.width >= minTouch && r.height >= minTouch) return@forEach
            val target = Rect(
                r.center.x - maxOf(r.width, minTouch) / 2, r.center.y - maxOf(r.height, minTouch) / 2,
                r.center.x + maxOf(r.width, minTouch) / 2, r.center.y + maxOf(r.height, minTouch) / 2
            ).deflate(0.5f)
            val crowded = controls.any { (other, o) ->
                other.id != node.id && !o.encloses(r) && !r.encloses(o) && o.overlaps(target)
            }
            if (crowded) {
                out += "$name: \"${node.label()}\" has a ${dp(r.width, px)} × ${dp(r.height, px)}dp touch target with no room to grow"
            }
        }
        val texts = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes()
        // Two pieces of text drawn over each other, such as a value running into the next column.
        texts.forEachIndexed { i, a ->
            texts.drop(i + 1).forEach { b ->
                if (a.unclipped().deflate(1f).overlaps(b.unclipped().deflate(1f))) {
                    out += "$name: \"${a.label()}\" overlaps \"${b.label()}\""
                }
            }
        }
        texts.forEach { node ->
            val layouts = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
            val layout = layouts.firstOrNull()
            if (layout != null && layout.lineCount > 0 && layout.isLineEllipsized(layout.lineCount - 1)) {
                out += "$name: \"${node.label()}\" is cut short with an ellipsis"
            }
            // Text in a row that scrolls sideways (a scrolling tab row) is meant to run past the edge.
            if (node.inHorizontalScroll()) return@forEach
            // A parent that clips the text shows less of it than it laid out.
            val r = node.unclipped()
            val shown = node.boundsInRoot
            if (shown.width < r.width - 1f || shown.height < r.height - 1f) out += "$name: \"${node.label()}\" is clipped"
            if (r.right > right) out += "$name: \"${node.label()}\" runs ${dp(r.right - right, px)}dp off the screen"
        }
        // Kept for a look at 320dp; not compared (the screenshot tests compare their own widths).
        File("build/outputs/roborazzi/phone").mkdirs()
        compose.onNodeWithTag(TAG).captureRoboImage(
            File("build/outputs/roborazzi/phone/${name.replace(' ', '_')}.png"),
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record)
        )
        return out
    }

    /** Whether [other] lies wholly inside this rectangle (a control nested in another, such as a row's switch). */
    private fun Rect.encloses(other: Rect) =
        left <= other.left + 0.5f && top <= other.top + 0.5f && right >= other.right - 0.5f && bottom >= other.bottom - 0.5f

    private fun SemanticsNode.inHorizontalScroll(): Boolean {
        var p = parent
        while (p != null) {
            if (p.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange) != null) return true
            p = p.parent
        }
        return false
    }

    /** Where the node is laid out, before any parent clips it. */
    private fun SemanticsNode.unclipped(): Rect = Rect(positionInRoot, size.toSize())

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
