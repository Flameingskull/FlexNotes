package com.fitlens.companion.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.fitlens.companion.data.Area
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.Db
import com.fitlens.companion.data.PortableSettings
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.Store
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock

/**
 * The base for tests that drive a screen the way a person does and assert what happened (#333): open a screen on a
 * [Snapshot] and a clock, tap, long-press or type into its controls by their text, read back what is drawn
 * ([drawn], [shows]) and what is stored ([stored]). The set-up and tear-down are those of `WholeScreenChecks` (Store,
 * Settings, `AppScope`, `Dates.clockSource`, `Settings.showWith`, the rest timer), and every seam is put back after
 * each test.
 *
 * A subclass repeats the three annotations Robolectric reads, as `WholeScreen411Test` does:
 * ```
 * @RunWith(RobolectricTestRunner::class)
 * @GraphicsMode(GraphicsMode.Mode.NATIVE)
 * @Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h3200dp-mdpi")
 * class MyScreenTest : FunctionTest() {
 *     @Test fun rangeShowsOldestYear() {
 *         openScreen(LongHistoryFixture.snapshot()) { snap -> SetEntryScreen(snap, nav, LongHistoryFixture.END, LongHistoryFixture.EX_SQUAT) }
 *         tap("History")
 *         assertShows("MONDAY, SEPTEMBER 4, 2023")
 *     }
 * }
 * ```
 * The window is tall (`h3200dp`), so a list shows most of its rows without scrolling. A failed lookup says what the
 * screen did show.
 *
 * The screen is drawn from the snapshot it is given, not from the database: a tap that writes goes through the real
 * functions into the real Room database, so [stored] reads it back, and [show] puts a new snapshot on screen (for
 * example [stored]'s) the way the app does when the Store publishes one.
 */
@RunWith(RobolectricTestRunner::class)
abstract class FunctionTest {

    @get:Rule val compose = createComposeRule()

    protected lateinit var app: Application
    protected val nav = Nav()
    private val onScreenSnapshot = mutableStateOf<Snapshot?>(null)

    @Before
    fun openSeams() {
        app = ApplicationProvider.getApplicationContext()
        app.deleteDatabase(Db.NAME)
        Store.init(app)
        Settings.init(app)
        runBlocking { Settings.awaitLoaded() }
        AppScope.init(app)
    }

    @After
    fun closeSeams() {
        Dates.clockSource = Clock.systemDefaultZone()
        RestTimer.stop()
        Settings.showWith(PortableSettings())
        Store.db.close()
        app.deleteDatabase(Db.NAME)
    }

    /**
     * Draws [screen] from [snapshot] with the phone's clock at [clock] and these [preferences], and waits for it to
     * settle. [screen] is given the snapshot currently shown, so [show] redraws it.
     */
    protected fun openScreen(
        snapshot: Snapshot,
        clock: Clock = LongHistoryFixture.clock,
        preferences: PortableSettings = PortableSettings(),
        screen: @Composable (Snapshot) -> Unit
    ) {
        Dates.clockSource = clock
        Settings.showWith(preferences)
        onScreenSnapshot.value = snapshot
        compose.setContent {
            FlexNotesTheme {
                Box(Modifier.fillMaxSize().background(Brand.Black)) { onScreenSnapshot.value?.let { screen(it) } }
            }
        }
        compose.waitForIdle()
    }

    /** Puts [snapshot] on the open screen, as the app does when the Store publishes a new one. */
    protected fun show(snapshot: Snapshot) {
        compose.runOnIdle { onScreenSnapshot.value = snapshot }
        compose.waitForIdle()
    }

    // ---- driving ------------------------------------------------------------------------------------------------

    private fun control(label: String, substring: Boolean) =
        hasClickAction() and (hasText(label, substring, true) or hasContentDescription(label, substring, true))

    /**
     * Taps the control whose text or description is [label] (the first, when several match), failing with what is on
     * screen when there is none. With [scroll] the control is first scrolled into view in the list it sits in.
     */
    protected fun tap(label: String, substring: Boolean = false, scroll: Boolean = false) {
        val matches = compose.onAllNodes(control(label, substring))
        if (matches.fetchSemanticsNodes().isEmpty()) throw AssertionError("No control \"$label\" to tap. ${onScreen()}")
        if (scroll) runCatching { matches[0].performScrollTo() }
        matches[0].performClick()
        compose.waitForIdle()
    }

    /** A long press on the control whose text or description is [label], which opens a card's menu. */
    protected fun longPress(label: String, substring: Boolean = false) {
        val matches = compose.onAllNodes(control(label, substring))
        if (matches.fetchSemanticsNodes().isEmpty()) throw AssertionError("No control \"$label\" to press. ${onScreen()}")
        matches[0].performTouchInput { longClick() }
        compose.waitForIdle()
    }

    /**
     * Scrolls the list holding [text] to it, for a row a long list hasn't drawn yet. Innermost list first, so a screen's
     * tabs don't turn while its list is searched.
     */
    protected fun scrollTo(text: String, substring: Boolean = false) {
        val lists = compose.onAllNodes(hasScrollToNodeAction())
        val found = lists.fetchSemanticsNodes().indices.reversed().any { i ->
            runCatching { lists[i].performScrollToNode(hasText(text, substring, true)) }.isSuccess
        }
        if (!found) throw AssertionError("No list holds \"$text\". ${onScreen()}")
        compose.waitForIdle()
    }

    /** Types [text] into the screen's text field. */
    protected fun type(text: String) {
        val fields = compose.onAllNodes(hasSetTextAction())
        if (fields.fetchSemanticsNodes().isEmpty()) throw AssertionError("No text field to type \"$text\" into. ${onScreen()}")
        fields[0].performTextInput(text)
        compose.waitForIdle()
    }

    // ---- reading back -------------------------------------------------------------------------------------------

    /** Every text and description the screen shows now, in tree order. */
    protected fun drawn(): List<String> =
        compose.onAllNodes(SemanticsMatcher("any node") { true }).fetchSemanticsNodes().flatMap { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        }.filter { it.isNotBlank() }

    /** Whether some text or description on screen is [text], or contains it with [substring]; case is ignored. */
    protected fun shows(text: String, substring: Boolean = false): Boolean =
        compose.onAllNodes(hasText(text, substring, true) or hasContentDescription(text, substring, true))
            .fetchSemanticsNodes().isNotEmpty()

    protected fun assertShows(text: String, substring: Boolean = false) =
        assertTrue("Expected \"$text\" on screen. ${onScreen()}", shows(text, substring))

    protected fun assertNotShows(text: String, substring: Boolean = false) =
        assertTrue("Did not expect \"$text\" on screen. ${onScreen()}", !shows(text, substring))

    private fun onScreen(): String = "On screen: ${drawn().distinct().take(60)}"

    /**
     * What is stored now: every area re-read from the database (not the snapshot on screen). Preferences are left as
     * set by [openScreen].
     */
    protected fun stored(): Snapshot {
        runBlocking { Store.refresh(*Area.ALL.toTypedArray()) }
        return Store.snapshot.value ?: throw AssertionError("The Store holds nothing")
    }
}
