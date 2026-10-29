package com.fitlens.companion.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import com.fitlens.companion.ui.design.SetCell
import com.fitlens.companion.ui.design.SetRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The TalkBack checks from #157 that don't need a phone, run on every build against what TalkBack is handed: the
 * semantics tree of each design component in [DesignSamples]. Every component must
 *
 * - give every control something to say, in words (not only "−" or "+"), and no two controls the same words;
 * - announce the state of every checkbox, switch, radio button and tab, and group radio buttons and tabs so TalkBack
 *   reads "1 of 3";
 *
 * and the components keep the readings #41 gave them: section labels are headings, a row reads as one sentence (title,
 * value or state, explanation), and a number row's value announces its change. Listening on a phone is still part of
 * #157; this catches a component that stops handing TalkBack the right things.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w420dp-h2000dp-xhdpi")
class AccessibilityTest {

    @get:Rule val compose = createComposeRule()

    private fun show(content: @Composable () -> Unit) {
        compose.setContent { FlexNotesTheme { Column { content() } } }
        compose.waitForIdle()
    }

    private fun nodes(unmerged: Boolean = false): List<SemanticsNode> =
        compose.onAllNodes(SemanticsMatcher("any node") { true }, useUnmergedTree = unmerged).fetchSemanticsNodes()

    /** What TalkBack reads for the node: its description, text and state. */
    private fun SemanticsNode.spoken(): String = listOfNotNull(
        config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" "),
        config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text },
        config.getOrNull(SemanticsProperties.EditableText)?.text,
        config.getOrNull(SemanticsProperties.StateDescription)
    ).joinToString(" ").trim()

    private fun SemanticsNode.isControl(): Boolean =
        config.getOrNull(SemanticsActions.OnClick) != null ||
            config.getOrNull(SemanticsActions.OnLongClick) != null ||
            config.getOrNull(SemanticsProperties.ToggleableState) != null

    private fun SemanticsNode.inSelectableGroup(): Boolean {
        var p = parent
        while (p != null) {
            if (p.config.getOrNull(SemanticsProperties.SelectableGroup) != null) return true
            p = p.parent
        }
        return false
    }

    /** The general rules, one line per problem, so a single run lists all of them. */
    private fun problems(name: String, content: @Composable () -> Unit): List<String> {
        show(content)
        val out = mutableListOf<String>()
        val controls = nodes().filter { it.isControl() }
        controls.forEach { node ->
            val said = node.spoken()
            if (said.none { it.isLetterOrDigit() }) out += "$name: a control says only \"$said\""
        }
        controls.groupBy { it.spoken() }.filter { (said, same) -> said.isNotEmpty() && same.size > 1 }.keys.forEach {
            out += "$name: ${controls.count { c -> c.spoken() == it }} controls all say \"$it\""
        }
        nodes(unmerged = true).forEach { node ->
            val role = node.config.getOrNull(SemanticsProperties.Role) ?: return@forEach
            val said = node.spoken().ifEmpty { "(unlabelled)" }
            when (role) {
                Role.Checkbox, Role.Switch -> {
                    if (node.config.getOrNull(SemanticsProperties.ToggleableState) == null &&
                        node.config.getOrNull(SemanticsProperties.StateDescription) == null
                    ) out += "$name: $role \"$said\" doesn't say whether it's on"
                }
                Role.RadioButton, Role.Tab -> {
                    if (node.config.getOrNull(SemanticsProperties.Selected) == null) {
                        out += "$name: $role \"$said\" doesn't say whether it's chosen"
                    }
                    if (!node.inSelectableGroup()) out += "$name: $role \"$said\" isn't in a group"
                }
                else -> Unit
            }
        }
        return out
    }

    private fun check(name: String, content: @Composable () -> Unit) {
        val found = problems(name, content)
        assertTrue("TalkBack:\n" + found.joinToString("\n"), found.isEmpty())
    }

    @Test fun topBar() = check("Top bar") { DesignSamples.topBar() }
    @Test fun exerciseCard() = check("Exercise card") { DesignSamples.exerciseCard() }
    @Test fun statTiles() = check("Stat tiles") { DesignSamples.statTiles() }
    @Test fun settingsRowVariants() = check("Settings row variants") { DesignSamples.settingsRowVariants() }
    @Test fun materialDefaults() = check("Material defaults") { DesignSamples.materialDefaults() }

    @Test
    fun setRowsReadAsOneSet() {
        val found = problems("Set rows") { DesignSamples.setRows() }
        assertTrue(found.joinToString("\n"), found.isEmpty())
        assertHeading("TRACK")
        val warmUp = compose.onNode(hasContentDescription("Set 1, warm-up, 40.0 kilograms, 12 reps")).fetchSemanticsNode()
        assertTrue(warmUp.spoken(), warmUp.spoken().isNotEmpty())
        val effort = compose.onNode(hasContentDescription("Set 2", substring = true)).fetchSemanticsNode()
        assertTrue(effort.spoken(), effort.spoken().contains("2 reps in reserve"))
        val chosen = compose.onNode(hasContentDescription("Set 3", substring = true)).fetchSemanticsNode()
        assertEquals(true, chosen.config.getOrNull(SemanticsProperties.Selected))
    }

    @Test
    fun setDoneAndCommentAreReachable() {
        var done: Boolean? = null
        show {
            SetRow(
                1, "60 kg, 10 reps",
                cells = listOf(SetCell("60.0", "kg", "60.0 kilograms"), SetCell("10", "reps", "10 reps")),
                onClick = {}, done = false, onDoneChange = { done = it }, onComment = {}
            )
        }
        val row = compose.onNode(hasContentDescription("Set 1", substring = true)).fetchSemanticsNode()
        assertEquals("Not done", row.config.getOrNull(SemanticsProperties.StateDescription))
        val mark = row.config.getOrNull(SemanticsActions.CustomActions).orEmpty().single { it.label == "Mark done" }
        compose.runOnIdle { mark.action() }
        assertEquals(true, done)
        compose.onNode(hasContentDescription("Add comment to set 1")).fetchSemanticsNode()
    }

    @Test
    fun settingsRowsReadAsOneSentence() {
        val found = problems("Settings rows") { DesignSamples.settingsRows() }
        assertTrue(found.joinToString("\n"), found.isEmpty())
        assertHeading("GENERAL")

        val switch = compose.onNode(hasText("Count warm-up sets")).fetchSemanticsNode()
        assertEquals(Role.Checkbox, switch.config.getOrNull(SemanticsProperties.Role))
        assertEquals(ToggleableState.On, switch.config.getOrNull(SemanticsProperties.ToggleableState))
        assertTrue(switch.spoken(), switch.spoken().contains("Include warm-ups in totals and records"))

        val choice = compose.onNode(hasText("Weight unit")).fetchSemanticsNode()
        assertEquals("kg", choice.config.getOrNull(SemanticsProperties.StateDescription))
        assertEquals("Change Weight unit", choice.config.getOrNull(SemanticsActions.OnClick)?.label)

        val action = compose.onNode(hasText("Back up now")).fetchSemanticsNode()
        assertTrue(action.spoken(), action.spoken().contains("Today") && action.spoken().contains(".flexnotes backup"))
    }

    @Test
    fun numberRowAnnouncesItsValue() {
        show { DesignSamples.settingsRowVariants() }
        assertHeading("AUTOMATIC BACKUPS")
        val title = compose.onNode(hasText("Photos per day")).fetchSemanticsNode()
        assertTrue(title.spoken(), title.spoken().contains("In the PDF report"))
        val value = compose.onNode(hasContentDescription("Photos per day")).fetchSemanticsNode()
        assertEquals("2", value.config.getOrNull(SemanticsProperties.StateDescription))
        assertEquals(LiveRegionMode.Polite, value.config.getOrNull(SemanticsProperties.LiveRegion))
        compose.onNode(hasContentDescription("Decrease Photos per day")).fetchSemanticsNode()
        compose.onNode(hasContentDescription("Increase Photos per day")).fetchSemanticsNode()
        // A problem in the status card is announced when it appears.
        val status = compose.onNode(hasText("The folder can't be reached")).fetchSemanticsNode()
        assertEquals(LiveRegionMode.Polite, status.config.getOrNull(SemanticsProperties.LiveRegion))
        val danger = compose.onNode(hasText("Delete workout history")).fetchSemanticsNode()
        assertEquals(Role.Button, danger.config.getOrNull(SemanticsProperties.Role))
    }

    @Test
    fun tabsAreAGroupOfThree() {
        val found = problems("Tabs and buttons") { DesignSamples.tabsAndButtons() }
        assertTrue(found.joinToString("\n"), found.isEmpty())
        val tabs = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).fetchSemanticsNodes()
        assertEquals(3, tabs.size)
        assertEquals(listOf(true, false, false), tabs.map { it.config.getOrNull(SemanticsProperties.Selected) })
    }

    private fun assertHeading(text: String) {
        val node = compose.onNode(hasText(text)).fetchSemanticsNode()
        assertTrue("\"$text\" isn't a heading", node.config.getOrNull(SemanticsProperties.Heading) != null)
    }
}
