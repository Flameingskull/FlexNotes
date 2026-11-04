package com.fitlens.companion.ui

import android.app.Application
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import com.fitlens.companion.R
import com.fitlens.companion.data.Dates
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h3200dp-mdpi")
class CalendarScreenTest : FunctionTest() {

    private val today = ScreenFixture.DATE
    private val photoDay = "2026-10-20"
    private val plainDay = "2026-10-21"

    /** A day's square, found by the date it speaks. */
    private fun cell(date: String) = hasClickAction() and hasContentDescription(Dates.long(date), substring = true)

    /** How many days show a photo thumbnail right now (the photo layer is tagged `cal_photo`). */
    private fun thumbnails() = compose.onAllNodes(hasTestTag("cal_photo"), useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun aSelectedPhotoDayKeepsItsPhotoAndASecondTapOpensTheDay() {
        openScreen(ScreenFixture.snapshot(), ScreenFixture.clock) { snap -> NavHarness(nav, snap, Screen.Calendar) }
        // It opens on the clock's month, with today selected: today has a photo, and so does the 20th.
        assertShows(Dates.monthYear(LocalDate.parse(today)))
        compose.onNode(cell(today)).assertIsSelected()
        compose.onNode(cell(today)).assertContentDescriptionContains(app.getString(R.string.cal_spoken_today), ignoreCase = true)
        compose.onNode(cell(today)).assertContentDescriptionContains(app.getString(R.string.cal_spoken_photo), ignoreCase = true)
        assertEquals("both photo days show their photo while one is selected", 2, thumbnails())

        // Selecting another photo day keeps both photos, and only that day is selected.
        compose.onNode(cell(photoDay)).performClick()
        compose.waitForIdle()
        compose.onNode(cell(photoDay)).assertIsSelected()
        compose.onNode(cell(photoDay)).assertContentDescriptionContains(app.getString(R.string.cal_spoken_photo), ignoreCase = true)
        compose.onNode(cell(today)).assertIsNotSelected()
        assertEquals(2, thumbnails())

        // A day with no photo selected: still two photos on the grid.
        compose.onNode(cell(plainDay)).performClick()
        compose.waitForIdle()
        compose.onNode(cell(plainDay)).assertIsSelected()
        assertEquals(2, thumbnails())

        // A second tap on the selected day opens it in the day log.
        compose.onNode(cell(plainDay)).performClick()
        compose.waitForIdle()
        assertEquals(Screen.Day(plainDay), compose.runOnIdle { nav.top })
    }
}
