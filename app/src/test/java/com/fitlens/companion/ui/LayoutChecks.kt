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

        // An open drawer or sheet covers what is under it, and covered content is not seen: on a whole screen only
        // the topmost modal pane of each window is checked while one is open (a closed drawer sits beside the window).
        val panes: Map<Int, SemanticsNode> = if (!scrollingScreen) emptyMap() else
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .filter { it.onScreen() }
                .groupBy { it.rootNode().id }
                .mapValues { it.value.last() }
        fun SemanticsNode.seen(): Boolean = !scrollingScreen || (placed() && onScreen() && uncovered(panes))
        // What is drawn: on a whole screen only the part a parent doesn't clip, so text scrolled under a footer isn't
        // counted as touching it.
        fun SemanticsNode.area(): Rect = if (scrollingScreen) boundsInRoot else unclipped()

        // Compose stretches a smaller control's touch area to 48dp, as long as no other control is in the way.
        val controls = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
            .filter { it.seen() }
            .map { it to it.unclipped() }
        controls.forEach { (node, r) ->
            if (r.width >= minTouch && r.height >= minTouch) return@forEach
            val target = Rect(
                r.center.x - maxOf(r.width, minTouch) / 2, r.center.y - maxOf(r.height, minTouch) / 2,
                r.center.x + maxOf(r.width, minTouch) / 2, r.center.y + maxOf(r.height, minTouch) / 2
            ).deflate(0.5f)
            val crowded = controls.any { (other, _) ->
                val o = other.area()
                other.id != node.id && other.rootNode().id == node.rootNode().id &&
                    !o.encloses(r) && !r.encloses(o) && o.overlaps(target)
            }
            if (crowded) {
                out += "$name: \"${node.label()}\" has a ${dp(r.width, px)} × ${dp(r.height, px)}dp touch target with no room to grow"
            }
        }
        val texts = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .filter { it.seen() }
        // Two pieces of text drawn over each other, such as a value running into the next column.
        texts.forEachIndexed { i, a ->
            val window = a.rootNode().id
            texts.drop(i + 1).forEach { b ->
                if (b.rootNode().id != window) return@forEach
                val ra = a.area().deflate(1f)
                val rb = b.area().deflate(1f)
                if (scrollingScreen) {
                    if (ra.isEmpty || rb.isEmpty || !ra.overlaps(rb)) return@forEach
                    // A touch of one pixel or two is not an overlap anyone can see.
                    val both = ra.intersect(rb)
                    if (both.width < 2f * px || both.height < 2f * px) return@forEach
                    // A text field's own node covers its placeholder, label and typed text.
                    // Its label floats on its top border, so only the width is compared.
                    if ((a.isTextField() && a.unclipped().spans(b.unclipped())) ||
                        (b.isTextField() && b.unclipped().spans(a.unclipped()))
                    ) return@forEach
                } else if (!ra.overlaps(rb)) return@forEach
                out += "$name: \"${a.label()}\" overlaps \"${b.label()}\""
            }
        }
        texts.forEach { node ->
            val layouts = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
            val layout = layouts.firstOrNull()
            if (layout != null && layout.lineCount > 0 && layout.isLineEllipsized(layout.lineCount - 1)) {
                out += "$name: \"${node.label()}\" is cut short with an ellipsis"
            }
            // A column too narrow for its words: "Posi/tional sparr/ing". A single word too long for a whole line of
            // the screen is the one case this can't be helped; none of the fixture's words are.
            if (scrollingScreen && layout != null) {
                val text = layout.layoutInput.text.text
                for (line in 0 until layout.lineCount - 1) {
                    val end = layout.getLineEnd(line)
                    if (end in 1 until text.length && text[end - 1].isLetter() && text[end].isLetter()) {
                        out += "$name: \"${node.label()}\" breaks a word across lines"
                        break
                    }
                }
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

    /** Whether [other] lies within this rectangle's width. */
    private fun Rect.spans(other: Rect) = left <= other.left + 0.5f && right >= other.right - 0.5f

    /**
     * Whether the node and everything above it were placed. A layout can measure a copy it never places, as `SetCells`
     * does to choose between its sizes, and that copy isn't on the screen.
     */
    private fun SemanticsNode.placed(): Boolean =
        generateSequence<androidx.compose.ui.layout.LayoutInfo>(layoutInfo) { it.parentInfo }.all { it.isPlaced }

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

    /** An editable text field: its own node reads as text but holds its placeholder and typed text. */
    private fun SemanticsNode.isTextField(): Boolean = config.getOrNull(SemanticsActions.SetText) != null

    /** Whether this node is inside [panes]' open pane for its window, or its window has none open. */
    private fun SemanticsNode.uncovered(panes: Map<Int, SemanticsNode>): Boolean {
        val pane = panes[rootNode().id] ?: return true
        return generateSequence(this) { it.parent }.any { it.id == pane.id }
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
