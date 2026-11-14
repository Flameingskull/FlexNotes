package com.fitlens.companion.ui

import android.app.Application
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTextInput
import com.fitlens.companion.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** "Save and add another" on the body value sheet saves, keeps the sheet open and clears the value (#331). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h3200dp-mdpi")
class BodyEntryAddAnotherTest : FunctionTest() {

    private fun title() = app.getString(R.string.mse_log)
    private fun addAnother() = app.getString(R.string.mse_save_add)

    private fun openSheetFromTheBodyTracker() {
        openScreen(LongHistoryFixture.snapshot()) { snap -> BodyScreen(snap, nav) }
        tap(app.getString(R.string.day_log_value))
        assertShows(title())
    }

    /** The sheet on its own, so a chip's name cannot also match a row of the Body tracker behind it. */
    private fun openSheetAlone() {
        openScreen(LongHistoryFixture.snapshot()) { snap ->
            MeasurementEntrySheet(snap, LongHistoryFixture.END, initialName = LongHistoryFixture.BODYWEIGHT) {}
        }
        assertShows(title())
    }

    private fun valueField() =
        compose.onNode(hasSetTextAction() and hasText(app.getString(R.string.body_value), substring = true, ignoreCase = true))

    private fun typeValue(text: String) {
        valueField().performTextInput(text)
        compose.waitForIdle()
    }

    /** Whether any text field on screen holds exactly [text]. */
    private fun fieldHolds(text: String) =
        compose.onAllNodes(hasSetTextAction() and hasText(text)).fetchSemanticsNodes().isNotEmpty()

    private fun storedValue(name: String, value: Double) =
        stored().records.any { it.name == name && Math.abs(it.value - value) < 0.001 }

    @Test
    fun saveAndAddAnotherSavesKeepsTheSheetOpenAndClearsTheValue() {
        openSheetFromTheBodyTracker()
        typeValue("61.7")
        assertTrue(fieldHolds("61.7"))
        tap(addAnother())
        assertTrue(awaitAppScopeJobs())
        // Saved, as the database says.
        assertTrue(storedValue(LongHistoryFixture.BODYWEIGHT, 61.7))
        // The sheet is still open and the value is cleared, ready for the next one.
        show(stored())
        assertShows(title())
        assertFalse(fieldHolds("61.7"))
        // And the saved value shows on the screen behind.
        assertShows("61.7", substring = true)
    }

    @Test
    fun twoValuesInARowForTwoMeasurements() {
        openSheetAlone()
        typeValue("61.7")
        tap(addAnother())
        assertTrue(awaitAppScopeJobs())
        tap(LongHistoryFixture.WAIST)
        typeValue("90.2")
        tap(addAnother())
        assertTrue(awaitAppScopeJobs())
        assertTrue(storedValue(LongHistoryFixture.BODYWEIGHT, 61.7))
        assertTrue(storedValue(LongHistoryFixture.WAIST, 90.2))
        show(stored())
        assertShows(title())
        assertFalse(fieldHolds("90.2"))
    }

    @Test
    fun anEmptyValueIsNotSavedAndTheSheetStaysOpen() {
        openSheetAlone()
        tap(addAnother())
        awaitAppScopeJobs()
        assertShows(title())
        assertTrue(stored().records.none { it.name == LongHistoryFixture.BODYWEIGHT })
    }

    @Test
    fun editingAValueShowsDeleteAndNotSaveAndAddAnother() {
        val snap = LongHistoryFixture.snapshot()
        val existing = snap.records.first { it.name == LongHistoryFixture.BODYWEIGHT }
        openScreen(snap) { s -> MeasurementEntrySheet(s, existing.date, existing = existing) {} }
        assertShows(app.getString(R.string.mse_edit, existing.name))
        assertShows(app.getString(R.string.lib_delete))
        assertNotShows(addAnother())
    }

    @Test
    fun aNewValueOffersSaveAndAddAnotherAndSave() {
        openSheetAlone()
        assertShows(addAnother())
        assertShows(app.getString(R.string.type_save))
    }
}