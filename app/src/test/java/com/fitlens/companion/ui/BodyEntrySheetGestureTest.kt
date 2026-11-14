package com.fitlens.companion.ui

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.fitlens.companion.R
import com.fitlens.companion.data.BodyPart
import com.fitlens.companion.data.MRecord
import com.fitlens.companion.data.MeasurementDef
import com.fitlens.companion.data.PortableSettings
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.Sources
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

/** The expandable body value sheet's gestures, its closing rules and its real height (#331, #334). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h3200dp-mdpi")
class BodyEntrySheetGestureTest : FunctionTest() {

    private val date = LongHistoryFixture.END
    private var closed = false

    private fun open(
        snapshot: Snapshot = LongHistoryFixture.snapshot(),
        prefs: PortableSettings = PortableSettings(),
        name: String = LongHistoryFixture.BODYWEIGHT
    ) {
        closed = false
        openScreen(snapshot, preferences = prefs) { snap ->
            MeasurementEntrySheet(snap, date, initialName = name) { closed = true }
        }
    }

    private fun title() = app.getString(R.string.mse_log)
    private fun expand() = app.getString(R.string.sheet_expand)
    private fun collapse() = app.getString(R.string.sheet_collapse)

    private fun sheetHeight() = compose.onNodeWithTag("fit-sheet").fetchSemanticsNode().size.height

    private fun valueField() =
        compose.onNode(hasSetTextAction() and hasText(app.getString(R.string.body_value), substring = true, ignoreCase = true))

    private fun nameField() =
        compose.onNode(hasSetTextAction() and hasText(app.getString(R.string.mse_measurement), substring = true, ignoreCase = true))

    private fun typeValue(text: String) {
        valueField().performTextInput(text)
        compose.waitForIdle()
    }

    private fun tapOutside() = tap("close sheet", substring = true)

    private fun swipeUpOnHandle() {
        compose.onNodeWithTag("sheet-handle").performTouchInput {
            swipe(start = center, end = Offset(center.x, center.y - 200f), durationMillis = 300)
        }
        compose.waitForIdle()
    }

    private fun swipeDownOnTitle() {
        compose.onNodeWithText(title()).performTouchInput {
            swipe(start = center, end = Offset(center.x, center.y + 300f), durationMillis = 300)
        }
        compose.waitForIdle()
    }

    /** Neck 38 and Waist 85 cm on the day before, so a male profile of 180 cm gives a Body fat estimate. */
    private fun bodyFatSnapshot(): Snapshot {
        val base = LongHistoryFixture.snapshot()
        val defs = listOf(
            MeasurementDef("Body fat", "%", 0, 0, 0.0, true),
            MeasurementDef("Neck", "cm", 1, 0, 0.0, true),
            MeasurementDef("Waist", "cm", 2, 0, 0.0, true)
        )
        val records = listOf(
            MRecord(1, "Neck", "cm", "2026-10-29", "07:00:00", 38.0, null, Sources.FLEXNOTES),
            MRecord(2, "Waist", "cm", "2026-10-29", "07:00:00", 85.0, null, Sources.FLEXNOTES)
        )
        return Snapshot(base.library, base.setPart, base.notes, BodyPart(defs, records, "kg"), base.photoPart, "kg", File("photos"))
    }

    @Test
    fun aSwipeUpOnTheDragHandleGoesFullScreenAndTheSheetIsReallyTaller() {
        open()
        val half = sheetHeight()
        assertShows(expand())
        swipeUpOnHandle()
        assertShows(collapse())
        assertFalse(closed)
        val full = sheetHeight()
        assertTrue("Full screen ($full px) should be much taller than the usual height ($half px)", full > half * 2)
    }

    @Test
    fun theArrowReallyChangesTheSheetsHeightBothWays() {
        open()
        val half = sheetHeight()
        tap(expand())
        assertTrue(sheetHeight() > half * 2)
        tap(collapse())
        assertTrue("Back at the usual height", abs(sheetHeight() - half) <= 2)
    }

    @Test
    fun aSwipeDownOnTheTitleClosesAnUntouchedSheet() {
        open()
        swipeDownOnTitle()
        assertTrue(closed)
    }

    @Test
    fun aSwipeDownOnTheTitleKeepsWhatWasTyped() {
        open()
        typeValue("61.7")
        swipeDownOnTitle()
        assertFalse(closed)
        assertShows(title())
        assertShows("61.7")
    }

    @Test
    fun aTapOutsideClosesACleanSheetAtFullScreen() {
        open(prefs = PortableSettings(bodyEntryFullScreen = true))
        assertShows(collapse())
        tapOutside()
        assertTrue(closed)
    }

    @Test
    fun aTapOutsideAtFullScreenWithTextShrinksItAndKeepsTheText() {
        open(prefs = PortableSettings(bodyEntryFullScreen = true))
        typeValue("61.7")
        tapOutside()
        assertFalse(closed)
        assertShows("61.7")
        assertShows(expand())
    }

    @Test
    fun anUntouchedBodyFatSheetClosesOnATapOutside() {
        open(snapshot = bodyFatSnapshot(), prefs = PortableSettings(profileSex = "male", profileHeightCm = 180.0), name = "Body fat")
        val filled = valueField().fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text
        assertTrue("The estimate should have filled the value, but it holds \"$filled\"", !filled.isNullOrBlank())
        tapOutside()
        assertTrue(closed)
    }

    @Test
    fun aTypedNewNameSurvivesATapOutside() {
        open()
        nameField().performTextInput("Grip strength")
        compose.waitForIdle()
        tapOutside()
        assertFalse(closed)
        assertShows(title())
    }

    @Test
    fun choosingAChipIsNotTypingSoATapOutsideCloses() {
        open()
        tap(LongHistoryFixture.WAIST)
        tapOutside()
        assertTrue(closed)
    }
}