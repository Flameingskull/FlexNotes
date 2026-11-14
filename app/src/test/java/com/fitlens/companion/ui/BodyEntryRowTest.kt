package com.fitlens.companion.ui

import android.app.Application
import com.fitlens.companion.R
import com.fitlens.companion.data.Settings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Settings switch "Open body value entry full screen" (#331): shown, searchable, and it changes the preference. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h3200dp-mdpi")
class BodyEntryRowTest : FunctionTest() {

    @Test
    fun theRowIsOnSettingsAndOffByDefault() {
        openScreen(LongHistoryFixture.snapshot()) { snap -> SettingsScreen(snap, nav) }
        assertShows(app.getString(R.string.settings_body_entry_full))
        assertFalse(Settings.portable.value.bodyEntryFullScreen)
    }

    @Test
    fun searchFindsItByItsWords() {
        openScreen(LongHistoryFixture.snapshot()) { snap -> SettingsScreen(snap, nav) }
        type("full screen")
        assertShows(app.getString(R.string.settings_body_entry_full))
    }

    @Test
    fun tappingItSwitchesItOnAndAgainOff() {
        openScreen(LongHistoryFixture.snapshot()) { snap -> SettingsScreen(snap, nav) }
        tap(app.getString(R.string.settings_body_entry_full))
        assertTrue(Settings.portable.value.bodyEntryFullScreen)
        tap(app.getString(R.string.settings_body_entry_full))
        assertFalse(Settings.portable.value.bodyEntryFullScreen)
    }
}
