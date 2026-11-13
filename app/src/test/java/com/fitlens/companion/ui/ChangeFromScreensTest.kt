package com.fitlens.companion.ui

import android.app.Application
import com.fitlens.companion.R
import com.fitlens.companion.data.BodyPart
import com.fitlens.companion.data.Settings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.MRecord
import com.fitlens.companion.data.MeasurementDef
import com.fitlens.companion.data.PortableSettings
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.Sources
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The Track row and the day log measure a body value's change from the same value, by the user's choice (#372). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h3200dp-mdpi")
class ChangeFromScreensTest : FunctionTest() {

    private val day = "2026-10-29"

    /** Bodyweight 90 on the 28th, then 89 and 88 on the 29th. */
    private fun twoValuesOnOneDay(): Snapshot {
        val base = LongHistoryFixture.snapshot()
        val defs = listOf(MeasurementDef("Bodyweight", "kg", 0, 0, 0.0, true))
        val records = listOf(
            MRecord(1, "Bodyweight", "kg", "2026-10-28", "07:00:00", 90.0, null, Sources.FLEXNOTES),
            MRecord(2, "Bodyweight", "kg", day, "07:00:00", 89.0, null, Sources.FLEXNOTES),
            MRecord(3, "Bodyweight", "kg", day, "18:00:00", 88.0, null, Sources.FLEXNOTES)
        )
        return Snapshot(base.library, base.setPart, base.notes, BodyPart(defs, records, "kg"), base.photoPart, "kg", File("photos"))
    }

    private fun since(date: String) = "since ${Dates.day(date)}"

    @Test
    fun theTrackRowComparesWithTheEarlierDayByDefault() {
        openScreen(twoValuesOnOneDay()) { snap -> BodyScreen(snap, nav) }
        assertShows(since("2026-10-28"), substring = true)
        assertNotShows(since(day), substring = true)
    }

    @Test
    fun theDayLogComparesBothValuesWithTheEarlierDayByDefault() {
        openScreen(twoValuesOnOneDay()) { snap -> DayScreen(snap, nav, day) }
        assertShows(since("2026-10-28"), substring = true)
        assertNotShows(since(day), substring = true)
    }

    @Test
    fun theTrackRowComparesWithTheValueJustBeforeWhenChosen() {
        openScreen(twoValuesOnOneDay(), preferences = PortableSettings(changeFromEarlierDay = false)) { snap -> BodyScreen(snap, nav) }
        assertShows(since(day), substring = true)
        assertNotShows(since("2026-10-28"), substring = true)
    }

    @Test
    fun theDayLogComparesWithTheValueJustBeforeWhenChosen() {
        openScreen(twoValuesOnOneDay(), preferences = PortableSettings(changeFromEarlierDay = false)) { snap -> DayScreen(snap, nav, day) }
        // 89 compares with the 28th; 88 compares with 89 on the same day.
        assertShows(since("2026-10-28"), substring = true)
        assertShows(since(day), substring = true)
    }

    @Test
    fun theSettingsRowIsThereAndSearchFindsIt() {
        openScreen(twoValuesOnOneDay()) { snap -> SettingsScreen(snap, nav) }
        assertShows(app.getString(R.string.settings_change_from))
        type("previous")
        assertShows(app.getString(R.string.settings_change_from))
        assertTrue(Settings.portable.value.changeFromEarlierDay)
    }

    @Test
    fun choosingTheValueJustBeforeSavesIt() {
        openScreen(twoValuesOnOneDay()) { snap -> SettingsScreen(snap, nav) }
        tap(app.getString(R.string.settings_change_from))
        tap(app.getString(R.string.change_from_value))
        compose.waitForIdle()
        assertFalse(Settings.portable.value.changeFromEarlierDay)
    }
}
