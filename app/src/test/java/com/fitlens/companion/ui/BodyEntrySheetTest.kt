package com.fitlens.companion.ui

import android.app.Application
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTextInput
import com.fitlens.companion.R
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.PortableSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The body value sheet opens at half height, goes full screen and back, and is not closed by choosing an option (#331). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h3200dp-mdpi")
class BodyEntrySheetTest : FunctionTest() {

    private val date = LongHistoryFixture.END
    private var closed = false

    private fun open(full: Boolean = false) {
        closed = false
        openScreen(LongHistoryFixture.snapshot(), preferences = PortableSettings(bodyEntryFullScreen = full)) { snap ->
            MeasurementEntrySheet(snap, date, initialName = LongHistoryFixture.BODYWEIGHT) { closed = true }
        }
    }

    private fun title() = app.getString(R.string.mse_log)
    private fun expand() = app.getString(R.string.sheet_expand)
    private fun collapse() = app.getString(R.string.sheet_collapse)

    private fun valueField() =
        compose.onNode(hasSetTextAction() and hasText(app.getString(R.string.body_value), substring = true, ignoreCase = true))

    private fun typeValue(text: String) {
        valueField().performTextInput(text)
        compose.waitForIdle()
    }

    @Test
    fun opensAtHalfHeightAndOffersFullScreen() {
        open()
        assertShows(title())
        assertShows(expand())
        assertNotShows(collapse())
    }

    @Test
    fun opensFullScreenWhenTheSettingIsOn() {
        open(full = true)
        assertShows(title())
        assertShows(collapse())
        assertNotShows(expand())
    }

    @Test
    fun theButtonGoesUpAndBackDown() {
        open()
        tap(expand())
        assertShows(collapse())
        assertNotShows(expand())
        tap(collapse())
        assertShows(expand())
        assertNotShows(collapse())
        assertShows(title())
        assertFalse(closed)
    }

    @Test
    fun typedTextSurvivesTheChangeOfHeight() {
        open()
        typeValue("61.7")
        assertShows("61.7")
        tap(expand())
        assertShows("61.7")
        tap(collapse())
        assertShows("61.7")
        assertFalse(closed)
    }

    @Test
    fun choosingAMeasurementOrTheDateNeverClosesItAtEitherHeight() {
        open()
        for (round in 0..1) {
            tap(LongHistoryFixture.WAIST)
            assertShows(title())
            tap(Dates.day(date))
            // The date picker is open over the sheet; the sheet is still there. OK keeps the same date and closes the picker.
            assertShows(title())
            tap(app.getString(R.string.cmn_ok))
            assertShows(title())
            assertFalse(closed)
            if (round == 0) tap(expand())
        }
        // The chosen measurement is now in the name field.
        assertTrue(compose.onAllNodes(hasSetTextAction() and hasText(LongHistoryFixture.WAIST)).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun cancelClosesItEvenWithTextTyped() {
        open()
        typeValue("61.7")
        tap(app.getString(R.string.cancel))
        assertTrue(closed)
    }

    @Test
    fun saveStoresTheValueAndClosesIt() {
        open()
        typeValue("61.7")
        tap(app.getString(R.string.type_save))
        assertTrue(awaitAppScopeJobs())
        assertTrue(closed)
        val saved = stored().records.firstOrNull { it.name == LongHistoryFixture.BODYWEIGHT && it.value == 61.7 }
        assertNotNull("The saved value 61.7 was not stored", saved)
        assertEquals(date, saved!!.date)
    }
}
