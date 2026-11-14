package com.fitlens.companion.ui

import android.app.Application
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import com.fitlens.companion.ui.design.GlassOutlinedButton
import com.fitlens.companion.R
import com.fitlens.companion.ui.design.FitSheet
import com.fitlens.companion.ui.design.rememberDirty
import com.fitlens.companion.ui.design.sheetMayClose
import com.fitlens.companion.ui.design.SetCommentSheet
import com.fitlens.companion.ui.SymptomSheet
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val GUARD_TITLE = "Guard test sheet"
private const val GUARD_CLOSED = "Guard test closed"
private const val GUARD_SAVE = "Save it"

/** A sheet with one text field, open until it is closed; shows [GUARD_CLOSED] once it has been. */
@Composable
private fun GuardHost() {
    var open by remember { mutableStateOf(true) }
    if (open) {
        var text by remember { mutableStateOf("") }
        val unsaved = rememberDirty(text.trim())
        FitSheet(
            title = GUARD_TITLE,
            onDismiss = { open = false },
            confirmLabel = GUARD_SAVE,
            onConfirm = { open = false },
            dirty = unsaved
        ) {
            OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth())
        }
    } else {
        Text(GUARD_CLOSED)
    }
}

/** Shows a real sheet until it calls its close function, then shows [GUARD_CLOSED]. */
@Composable
private fun SiteHost(sheet: @Composable (close: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(true) }
    if (open) sheet { open = false } else Text(GUARD_CLOSED)
}

/** A sheet whose two values are "a" and "b, c" at the start; the button makes them "a, b" and "c" (the same joined text). */
@Composable
private fun SameTextHost() {
    var open by remember { mutableStateOf(true) }
    if (open) {
        var first by remember { mutableStateOf("a") }
        var second by remember { mutableStateOf("b, c") }
        val unsaved = rememberDirty(first, second)
        FitSheet(title = GUARD_TITLE, onDismiss = { open = false }, dirty = unsaved) {
            GlassOutlinedButton(onClick = { first = "a, b"; second = "c" }) { Text("Move the comma") }
        }
    } else {
        Text(GUARD_CLOSED)
    }
}

/**
 * A sheet that holds typed values doesn't close on a tap outside, and keeps them (#334). Cancel and Save still close
 * it, and a sheet with nothing typed closes on a tap outside as before. Back is not driven here.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h3200dp-mdpi")
class SheetGuardTest : FunctionTest() {

    private fun open() = openScreen(LongHistoryFixture.snapshot()) { GuardHost() }

    /** Taps the dimmed area outside the sheet: the node Material 3 describes as "Close sheet" that can be clicked. */
    private fun tapOutside() {
        val scrim = compose.onAllNodes(hasContentDescription("Close sheet") and hasClickAction())
        assertTrue("No tap-outside (scrim) node found. On screen: ${drawn().distinct().take(40)}", scrim.fetchSemanticsNodes().isNotEmpty())
        scrim[0].performClick()
        compose.waitForIdle()
    }

    @Test
    fun theRuleRefusesOnlyWhileThereIsUnsavedInput() {
        assertFalse(sheetMayClose(true))
        assertTrue(sheetMayClose(false))
    }

    @Test
    fun typedTextKeepsTheSheetOpenOnATapOutside() {
        open()
        type("abc")
        tapOutside()
        assertShows(GUARD_TITLE)
        assertShows("abc")
        assertNotShows(GUARD_CLOSED)
    }

    @Test
    fun cancelClosesEvenWithTypedText() {
        open()
        type("abc")
        tap(app.getString(R.string.cancel))
        assertShows(GUARD_CLOSED)
    }

    @Test
    fun saveClosesWithTypedText() {
        open()
        type("abc")
        tap(GUARD_SAVE)
        assertShows(GUARD_CLOSED)
    }

    @Test
    fun anUntouchedSheetClosesOnATapOutside() {
        open()
        assertShows(GUARD_TITLE)
        tapOutside()
        assertShows(GUARD_CLOSED)
    }

    @Test
    fun theWorkoutCommentKeepsItsTextOnATapOutside() {
        openScreen(LongHistoryFixture.snapshot()) { snap ->
            SiteHost { close -> WorkoutCommentSheet(snap, LongHistoryFixture.END, close) }
        }
        type("Felt strong today")
        tapOutside()
        assertShows(app.getString(R.string.wk_comment_title))
        assertShows("Felt strong today")
        assertNotShows(GUARD_CLOSED)
    }

    @Test
    fun theWorkoutCommentClosesOnATapOutsideWhenNothingWasTyped() {
        openScreen(LongHistoryFixture.snapshot()) { snap ->
            SiteHost { close -> WorkoutCommentSheet(snap, LongHistoryFixture.END, close) }
        }
        assertShows(app.getString(R.string.wk_comment_title))
        tapOutside()
        assertShows(GUARD_CLOSED)
    }

    @Test
    fun theSetCommentKeepsItsTextOnATapOutsideAndCancelStillCloses() {
        openScreen(LongHistoryFixture.snapshot()) {
            SiteHost { close -> SetCommentSheet(describe = "Set 1", initial = null, onSave = {}, onDismiss = close) }
        }
        type("Left knee twinged")
        tapOutside()
        assertShows(app.getString(R.string.sv_comment))
        assertShows("Left knee twinged")
        tap(app.getString(R.string.cancel))
        assertShows(GUARD_CLOSED)
    }

    @Test
    fun theReferenceRangeKeepsItsNumberOnATapOutside() {
        openScreen(LongHistoryFixture.snapshot()) { snap ->
            val def = snap.measurementDefs.first { it.name == LongHistoryFixture.BODYWEIGHT }
            SiteHost { close -> ReferenceRangeSheet(def, close) }
        }
        type("65")
        tapOutside()
        assertShows("65")
        assertNotShows(GUARD_CLOSED)
    }

    @Test
    fun theSetCommentClosesOnATapOutsideWhenNothingWasTyped() {
        openScreen(LongHistoryFixture.snapshot()) {
            SiteHost { close -> SetCommentSheet(describe = "Set 1", initial = null, onSave = {}, onDismiss = close) }
        }
        assertShows(app.getString(R.string.sv_comment))
        tapOutside()
        assertShows(GUARD_CLOSED)
    }

    @Test
    fun typedTextKeepsTheSheetOpenOnADragDown() {
        open()
        type("abc")
        compose.onNodeWithText(GUARD_TITLE).performTouchInput { swipeDown(startY = 0f, endY = 250f, durationMillis = 300) }
        compose.waitForIdle()
        compose.onNodeWithText(GUARD_SAVE).assertIsDisplayed()
        assertShows("abc")
        assertNotShows(GUARD_CLOSED)
    }

    @Test
    fun anUntouchedSheetClosesOnADragDown() {
        open()
        compose.onNodeWithText(GUARD_TITLE).performTouchInput { swipeDown(startY = 0f, endY = 250f, durationMillis = 300) }
        compose.waitForIdle()
        assertShows(GUARD_CLOSED)
    }

    @Test
    fun theSymptomSheetKeepsANewNameOnATapOutside() {
        openScreen(LongHistoryFixture.snapshot()) { snap ->
            SiteHost { close -> SymptomSheet(snap, LongHistoryFixture.END, close) }
        }
        type("Headache")
        tapOutside()
        assertShows(app.getString(R.string.sym_sheet_title))
        assertShows("Headache")
        assertNotShows(GUARD_CLOSED)
    }

    @Test
    fun twoValuesThatJoinToTheSameTextAreStillSeenAsChanged() {
        openScreen(LongHistoryFixture.snapshot()) { SameTextHost() }
        tap("Move the comma")
        tapOutside()
        assertShows(GUARD_TITLE)
        assertNotShows(GUARD_CLOSED)
    }
}
