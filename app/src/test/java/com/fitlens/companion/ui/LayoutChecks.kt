package com.fitlens.companion.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.toSize

/**
 * The layout rules from #157, in one place: [PhoneSizeTest] runs them on the shared design components and
 * `WholeScreenTest` (#323) on whole screens, so there is a single copy of what "fits a small phone" means.
 *
 * A drawn UI must
 *
 * - give every control a touch target of at least 48 × 48dp (a smaller control counts when Compose can stretch its
 *   touch area to 48dp without running into another control);
 * - show its text whole: not clipped by a parent or cut short with an ellipsis, so titles and labels wrap instead;
 * - keep text apart: no two pieces of text drawn over each other;
 * - fit the screen's width, with nothing pushed off the right-hand edge.
 *
 * A sheet, menu or dialog is a window of its own with its own coordinates, so nodes are checked against the others in
 * their own window only.
 */
internal object LayoutChecks {

    private const val MIN_TOUCH = 48f

    /**
     * What is wrong with what is drawn now, one line per problem, so a single run lists all of them.
     *
     * [rootTag] is the test tag of the content's root. [scrollingScreen] is for a whole screen: text in a list that
     * extends past the list's top or bottom edge is simply scrolled out of view, not clipped, and text laid out
     * entirely beyond the window's left, top or bottom edge (a closed drawer, say) is not on screen at all.
     */
    fun problems(
        compose: ComposeContentTestRule,
        name: String,
        rootTag: String,
        scrollingScreen: Boolean = false
    ): List<String> {
        val out = mutableListOf<String>()
        val px = compose.density.density
        val minTouch = MIN_TOUCH * px - 0.5f
        val tag = compose.onNodeWithTag(rootTag).fetchSemanticsNode()
        val mainRootId = tag.rootNode().id
        val mainRight = tag.boundsInRoot.right + 0.5f

        // Compose stretches a smaller control's touch area to 48dp, as long as no other control is in the way.
        val controls = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
            .filter { !scrollingScreen || it.onScreen() }
            .map { it to it.unclipped() }
        controls.forEach { (node, r) ->
            if (r.width >= minTouch && r.height >= minTouch) return@forEach
            val target = Rect(
                r.center.x - maxOf(r.width, minTouch) / 2, r.center.y - maxOf(r.height, minTouch) / 2,
                r.center.x + maxOf(r.width, minTouch) / 2, r.center.y + maxOf(r.height, minTouch) / 2
            ).deflate(0.5f)
            val crowded = controls.any { (other, o) ->
                other.id != node.id && other.rootNode().id == node.rootNode().id &&
                    !o.encloses(r) && !r.encloses(o) && o.overlaps(target)
            }
            if (crowded) {
                out += "$name: \"${node.label()}\" has a ${dp(r.width, px)} × ${dp(r.height, px)}dp touch target with no room to grow"
            }
        }
        val texts = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .filter { !scrollingScreen || it.onScreen() }
        // Two pieces of text drawn over each other, such as a value running into the next column.
        texts.forEachIndexed { i, a ->
            val window = a.rootNode().id
            texts.drop(i + 1).forEach { b ->
                if (b.rootNode().id == window && a.unclipped().deflate(1f).overlaps(b.unclipped().deflate(1f))) {
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
            val r = node.unclipped()
            // Text in a row that scrolls sideways (a scrolling tab row) is meant to run past the edge. On a whole
            // screen only text that really extends past its scroller counts: a pager's page is a scroller too.
            val sideways = node.horizontalScroller()
            if (sideways != null) {
                val box = sideways.boundsInRoot
                if (!scrollingScreen || r.right > box.right + 0.5f || r.left < box.left - 0.5f) return@forEach
            }
            val root = node.rootNode()
            val right = if (root.id == mainRootId) mainRight else root.boundsInRoot.right + 0.5f
            // A parent that clips the text shows less of it than it laid out.
            val shown = node.boundsInRoot
            val scrolledOut = scrollingScreen && node.scrolledPastEdge(r)
            if (!scrolledOut && (shown.width < r.width - 1f || shown.height < r.height - 1f)) {
                out += "$name: \"${node.label()}\" is clipped"
            }
            if (r.right > right) out += "$name: \"${node.label()}\" runs ${dp(r.right - right, px)}dp off the screen"
        }
        return out.distinct()
    }

    /** Whether [other] lies wholly inside this rectangle (a control nested in another, such as a row's switch). */
    private fun Rect.encloses(other: Rect) =
        left <= other.left + 0.5f && top <= other.top + 0.5f && right >= other.right - 0.5f && bottom >= other.bottom - 0.5f

    /** The top of the node's window: nodes in a sheet, menu or dialog have a different one from the screen's. */
    private fun SemanticsNode.rootNode(): SemanticsNode = generateSequence(this) { it.parent }.last()

    /** The nearest row or pager that scrolls sideways around this node, or null. */
    private fun SemanticsNode.horizontalScroller(): SemanticsNode? {
        var p = parent
        while (p != null) {
            if (p.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange) != null) return p
            p = p.parent
        }
        return null
    }

    /** The nearest list or column that scrolls up and down around this node, or null. */
    private fun SemanticsNode.verticalScroller(): SemanticsNode? {
        var p = parent
        while (p != null) {
            if (p.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null) return p
            p = p.parent
        }
        return null
    }

    /** Whether the node extends past the top or bottom edge of the list it scrolls in (so part of it is scrolled out of view). */
    private fun SemanticsNode.scrolledPastEdge(laidOut: Rect): Boolean {
        val box = verticalScroller()?.boundsInRoot ?: return false
        return laidOut.bottom > box.bottom + 0.5f || laidOut.top < box.top - 0.5f
    }

    /** Whether any of the node is inside its window: false for a closed drawer's content, laid out beside the screen. */
    private fun SemanticsNode.onScreen(): Boolean {
        val r = unclipped()
        val window = rootNode().boundsInRoot
        return !(r.right <= window.left + 0.5f || r.bottom <= window.top + 0.5f || r.top >= window.bottom - 0.5f)
    }

    /** Where the node is laid out, before any parent clips it. */
    private fun SemanticsNode.unclipped(): Rect = Rect(positionInRoot, size.toSize())

    private fun SemanticsNode.label(): String =
        config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
            ?: config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
            ?: "(unlabelled)"

    private fun dp(value: Float, px: Float) = "%.0f".format(value / px)
}
