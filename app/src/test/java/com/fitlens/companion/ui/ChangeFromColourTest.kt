package com.fitlens.companion.ui

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasText
import androidx.compose.ui.text.TextLayoutResult
import com.fitlens.companion.data.BodyPart
import com.fitlens.companion.data.MRecord
import com.fitlens.companion.data.MeasurementDef
import com.fitlens.companion.data.MeasurementGoals
import com.fitlens.companion.data.PortableSettings
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.Sources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Every screen shows the same change, in the same colour, for the same value, under each choice (#372). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h3200dp-mdpi")
class ChangeFromColourTest : FunctionTest() {

    private val first = MRecord(1, "Bodyweight", "kg", "2026-10-28", "07:00:00", 90.0, null, Sources.FLEXNOTES)
    private val second = MRecord(2, "Bodyweight", "kg", "2026-10-29", "07:00:00", 87.0, null, Sources.FLEXNOTES)
    private val third = MRecord(3, "Bodyweight", "kg", "2026-10-29", "18:00:00", 88.0, null, Sources.FLEXNOTES)

    /** Bodyweight with "down is good": 90 on the 28th, then 87 and 88 on the 29th. */
    private fun snapshot(): Snapshot {
        val base = LongHistoryFixture.snapshot()
        val defs = listOf(MeasurementDef("Bodyweight", "kg", 0, MeasurementGoals.DECREASE, 0.0, true))
        return Snapshot(base.library, base.setPart, base.notes, BodyPart(defs, listOf(first, second, third), "kg"), base.photoPart, "kg", File("photos"))
    }

    private fun prefs(fromEarlierDay: Boolean) = PortableSettings(changeFromEarlierDay = fromEarlierDay)

    /** The latest value's change as the screens word it: from the earlier day's 90, or from the 87 just before. */
    private fun latestChange(fromEarlierDay: Boolean): String =
        changeText(app.resources, if (fromEarlierDay) first else second, third)

    private fun colours(text: String): List<Color> =
        compose.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().mapNotNull { node ->
            val layouts = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
            layouts.firstOrNull()?.layoutInput?.style?.color
        }

    /** The screen shows the latest value's change for this choice, and every copy of it has the verdict's colour. */
    private fun assertLatestChange(fromEarlierDay: Boolean) {
        val text = latestChange(fromEarlierDay)
        assertShows(text, substring = true)
        val found = colours(text)
        assertTrue("No coloured text \"$text\" on screen", found.isNotEmpty())
        val expected = if (fromEarlierDay) Brand.Rise else Brand.Fall
        found.forEach { assertEquals(text, expected, it) }
    }

    @Test
    fun theTrackRowFromTheEarlierDayIsGreen() {
        openScreen(snapshot(), preferences = prefs(true)) { s -> BodyScreen(s, nav) }
        assertLatestChange(true)
    }

    @Test
    fun theTrackRowFromTheValueJustBeforeIsRed() {
        openScreen(snapshot(), preferences = prefs(false)) { s -> BodyScreen(s, nav) }
        assertLatestChange(false)
    }

    @Test
    fun theTrackersHistoryFromTheEarlierDayIsGreen() {
        openScreen(snapshot(), preferences = prefs(true)) { s -> BodyScreen(s, nav) }
        tap(app.getString(com.fitlens.companion.R.string.body_tab_history))
        assertLatestChange(true)
    }

    @Test
    fun theTrackersHistoryFromTheValueJustBeforeIsRed() {
        openScreen(snapshot(), preferences = prefs(false)) { s -> BodyScreen(s, nav) }
        tap(app.getString(com.fitlens.companion.R.string.body_tab_history))
        assertLatestChange(false)
    }

    @Test
    fun aMeasurementsHistoryFromTheEarlierDayIsGreen() {
        openScreen(snapshot(), preferences = prefs(true)) { s -> BodyHistoryPane(s, "Bodyweight", onDelete = {}, onOpen = {}) }
        assertLatestChange(true)
    }

    @Test
    fun aMeasurementsHistoryFromTheValueJustBeforeIsRed() {
        openScreen(snapshot(), preferences = prefs(false)) { s -> BodyHistoryPane(s, "Bodyweight", onDelete = {}, onOpen = {}) }
        assertLatestChange(false)
    }

    @Test
    fun theDayLogFromTheEarlierDayIsGreen() {
        openScreen(snapshot(), preferences = prefs(true)) { s -> DayScreen(s, nav, "2026-10-29") }
        assertLatestChange(true)
    }

    @Test
    fun theDayLogFromTheValueJustBeforeIsRed() {
        openScreen(snapshot(), preferences = prefs(false)) { s -> DayScreen(s, nav, "2026-10-29") }
        assertLatestChange(false)
    }

    private val dose1 = MRecord(11, "Metformin", "mg", "2026-10-29", "08:00:00", 500.0, null, Sources.FLEXNOTES)
    private val dose2 = MRecord(12, "Metformin", "mg", "2026-10-29", "20:00:00", 1000.0, null, Sources.FLEXNOTES)

    /** Two doses on one day, as the Medications screen sees them. */
    private fun doses(): Snapshot {
        val base = LongHistoryFixture.snapshot()
        val defs = listOf(MeasurementDef("Metformin", "mg", 0, 0, 0.0, true, false, null, null, true))
        return Snapshot(base.library, base.setPart, base.notes, BodyPart(defs, listOf(dose1, dose2), "kg"), base.photoPart, "kg", File("photos")).medicationView
    }

    @Test
    fun aSecondDoseComparesWithTheFirstWhicheverChoiceIsSet() {
        val text = changeText(app.resources, dose1, dose2)
        openScreen(doses(), preferences = prefs(true)) { s -> BodyHistoryPane(s, "Metformin", onDelete = {}, onOpen = {}) }
        assertShows(text, substring = true)
    }

    @Test
    fun aSecondDoseComparesWithTheFirstWhenTheChoiceIsTheValueBefore() {
        val text = changeText(app.resources, dose1, dose2)
        openScreen(doses(), preferences = prefs(false)) { s -> BodyHistoryPane(s, "Metformin", onDelete = {}, onOpen = {}) }
        assertShows(text, substring = true)
    }

    @Test
    fun theGoalSheetOffersTheChoiceAndItChangesTheHistory() {
        openScreen(snapshot()) { s -> NavHarness(nav, s, Screen.BodyMeasurement("Bodyweight", 0)) }
        assertTrue(Settings.portable.value.changeFromEarlierDay)
        tap("Which way is good?", substring = true)
        tap(app.getString(com.fitlens.companion.R.string.change_from_value))
        tap(app.getString(com.fitlens.companion.R.string.goals_save))
        assertFalse(Settings.portable.value.changeFromEarlierDay)
        tap(app.getString(com.fitlens.companion.R.string.body_tab_history))
        assertLatestChange(false)
    }
}
