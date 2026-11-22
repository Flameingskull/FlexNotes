package com.fitlens.companion.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.fitlens.companion.R
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.PortableSettings
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.ui.design.SettingsNumberRow
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val TURN_CLOSED = "Turn test closed"
private const val TURN_NUMBER = "Turn test number"

/** Shows a real sheet until it calls its close function, then [TURN_CLOSED]. Its open flag is saved, as the app's are. */
@Composable
private fun TurnHost(sheet: @Composable (close: () -> Unit) -> Unit) {
    var open by rememberSaveable { mutableStateOf(true) }
    if (open) sheet { open = false } else Text(TURN_CLOSED)
}

/**
 * Sheets keep what was typed when the phone turns (#381). Each test draws a real sheet through Compose's
 * [StateRestorationTester], types a value, turns the phone (the screen is thrown away and drawn again from its saved
 * state, as Android does), and checks the value is still in its field. A sheet that guards typed input (#334) is then
 * tapped outside and must stay open.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h3200dp-mdpi")
class SheetRotationTest : FunctionTest() {

    private lateinit var restorer: StateRestorationTester

    /** Draws [screen] on the fixture as `openScreen` does, but so that [turnPhone] can recreate it. */
    private fun openTurnable(preferences: PortableSettings = PortableSettings(), screen: @Composable (Snapshot) -> Unit) {
        val snap = LongHistoryFixture.snapshot()
        Dates.clockSource = LongHistoryFixture.clock
        Settings.showWith(preferences)
        restorer = StateRestorationTester(compose)
        restorer.setContent {
            FlexNotesTheme { Box(Modifier.fillMaxSize().background(Brand.Black)) { screen(snap) } }
        }
        compose.waitForIdle()
    }

    /** Turns the phone: the screen is thrown away and drawn again from its saved state. */
    private fun turnPhone() {
        restorer.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
    }

    /** Puts [text] in the first text field that matches [field]. */
    private fun replaceText(text: String, field: SemanticsMatcher = hasSetTextAction()) {
        val fields = compose.onAllNodes(field)
        assertTrue("No text field for \"$text\". On screen: ${drawn().distinct().take(40)}", fields.fetchSemanticsNodes().isNotEmpty())
        fields[0].performTextReplacement(text)
        compose.waitForIdle()
    }

    /** Fails unless some text field on screen holds exactly [text]. */
    private fun assertFieldHolds(text: String) {
        val holding = compose.onAllNodes(SemanticsMatcher("a text field holding \"$text\"") { node ->
            node.config.getOrNull(SemanticsProperties.EditableText)?.text == text
        })
        assertTrue("No text field holds \"$text\". On screen: ${drawn().distinct().take(40)}", holding.fetchSemanticsNodes().isNotEmpty())
    }

    /** Taps the dimmed area outside the sheet: the node Material 3 describes as "Close sheet" that can be clicked. */
    private fun tapOutside() {
        val scrim = compose.onAllNodes(hasContentDescription("Close sheet") and hasClickAction())
        assertTrue("No tap-outside (scrim) node found. On screen: ${drawn().distinct().take(40)}", scrim.fetchSemanticsNodes().isNotEmpty())
        scrim[0].performClick()
        compose.waitForIdle()
    }

    @Test
    fun theWorkoutCommentKeepsItsTextWhenThePhoneTurns() {
        openTurnable { snap -> TurnHost { close -> WorkoutCommentSheet(snap, LongHistoryFixture.END, close) } }
        replaceText("Felt strong today")
        assertFieldHolds("Felt strong today")
        turnPhone()
        assertFieldHolds("Felt strong today")
        tapOutside()
        assertFieldHolds("Felt strong today")
        assertNotShows(TURN_CLOSED)
    }

    @Test
    fun caloriesKeepTheTypedAmountWhenThePhoneTurns() {
        openTurnable { snap -> TurnHost { close -> CaloriesSheet(snap, nav, LongHistoryFixture.END, close) } }
        replaceText("2400")
        assertFieldHolds("2400")
        turnPhone()
        assertFieldHolds("2400")
        tapOutside()
        assertFieldHolds("2400")
        assertNotShows(TURN_CLOSED)
    }

    @Test
    fun theBodyFatCalculatorKeepsATypedValueWhenThePhoneTurns() {
        openTurnable(PortableSettings(profileSex = "male")) { snap ->
            TurnHost { close -> BodyFatCalculatorSheet(snap, LongHistoryFixture.END, onDismiss = close) { _, _ -> } }
        }
        replaceText("181")
        assertFieldHolds("181")
        turnPhone()
        assertFieldHolds("181")
        tapOutside()
        assertFieldHolds("181")
        assertNotShows(TURN_CLOSED)
    }

    @Test
    fun theBodyValueKeepsItsNumberAtFullHeightWhenThePhoneTurns() {
        openTurnable(PortableSettings(bodyEntryFullScreen = true)) { snap ->
            TurnHost { close -> MeasurementEntrySheet(snap, LongHistoryFixture.END, initialName = LongHistoryFixture.BODYWEIGHT) { close() } }
        }
        val valueField = hasSetTextAction() and hasText(app.getString(R.string.body_value), substring = true, ignoreCase = true)
        replaceText("81", valueField)
        assertFieldHolds("81")
        turnPhone()
        assertFieldHolds("81")
        assertNotShows(TURN_CLOSED)
    }

    @Test
    fun createWorkoutKeepsItsTypedNameWhenThePhoneTurns() {
        openTurnable { snap -> TurnHost { close -> SaveAsWorkoutSheet(snap, nav, LongHistoryFixture.END, close) } }
        replaceText("Leg day plan")
        assertFieldHolds("Leg day plan")
        turnPhone()
        assertFieldHolds("Leg day plan")
        tapOutside()
        assertFieldHolds("Leg day plan")
        assertNotShows(TURN_CLOSED)
    }

    @Test
    fun aSettingsNumberKeepsItsSheetAndTextWhenThePhoneTurns() {
        openTurnable { SettingsNumberRow(title = TURN_NUMBER, value = 5, range = 1..99) {} }
        tap(TURN_NUMBER)
        replaceText("42")
        assertFieldHolds("42")
        turnPhone()
        assertFieldHolds("42")
        tapOutside()
        assertFieldHolds("42")
    }

    @Test
    fun theOneRepMaxCalculatorKeepsItsTypedValueWhenThePhoneTurns() {
        openTurnable { snap -> TurnHost { close -> OneRepMaxSheet(snap, null, close) } }
        replaceText("120")
        assertFieldHolds("120")
        turnPhone()
        assertFieldHolds("120")
        assertNotShows(TURN_CLOSED)
    }

    @Test
    fun estimatedOneRmSettingsKeepItsTypedRepsWhenThePhoneTurns() {
        openTurnable { TurnHost { close -> EstimatedOneRmSettingsSheet(close) } }
        replaceText("8")
        assertFieldHolds("8")
        turnPhone()
        assertFieldHolds("8")
        tapOutside()
        assertFieldHolds("8")
        assertNotShows(TURN_CLOSED)
    }

    @Test
    fun theCalendarFilterKeepsATypedLimitWhenThePhoneTurns() {
        openTurnable { snap -> TurnHost { close -> CalendarFilterSheet(snap, CalendarFilter(), onApply = {}, onDismiss = close) } }
        replaceText("42")
        assertFieldHolds("42")
        turnPhone()
        assertFieldHolds("42")
        assertNotShows(TURN_CLOSED)
    }

    @Test
    fun anExercisesGoalEditorStaysOpenWithItsTargetWhenThePhoneTurns() {
        openTurnable { snap -> GoalsTab(snap, LongHistoryFixture.EX_BENCH, timeBased = false) }
        tap(app.getString(R.string.ex_add_goal))
        replaceText("150")
        assertFieldHolds("150")
        turnPhone()
        assertFieldHolds("150")
        tapOutside()
        assertFieldHolds("150")
    }

    // END OF TESTS
}
