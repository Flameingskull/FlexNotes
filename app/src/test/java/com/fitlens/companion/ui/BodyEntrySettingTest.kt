package com.fitlens.companion.ui

import android.app.Application
import com.fitlens.companion.data.PortableSettings
import com.fitlens.companion.data.Settings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The "Open body value entry full screen" preference (#331): off by default, saved, and back to off on a reset. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h3200dp-mdpi")
class BodyEntrySettingTest : FunctionTest() {

    @Test
    fun offUntilTheUserSwitchesItOn() {
        assertFalse(Settings.currentPortable().bodyEntryFullScreen)
        assertFalse(PortableSettings().bodyEntryFullScreen)
    }

    @Test
    fun theChoiceIsSavedAndComesBackAfterARestart() {
        Settings.updatePortable { it.copy(bodyEntryFullScreen = true) }
        runBlocking { Settings.awaitWrites() }
        // Forget what is in memory, then load again from the database as a new start would.
        Settings.showWith(PortableSettings())
        Settings.init(app)
        runBlocking { Settings.awaitLoaded() }
        assertTrue(Settings.currentPortable().bodyEntryFullScreen)
    }

    @Test
    fun switchingItOffIsSavedToo() {
        Settings.updatePortable { it.copy(bodyEntryFullScreen = true) }
        Settings.updatePortable { it.copy(bodyEntryFullScreen = false) }
        runBlocking { Settings.awaitWrites() }
        Settings.showWith(PortableSettings(bodyEntryFullScreen = true))
        Settings.init(app)
        runBlocking { Settings.awaitLoaded() }
        assertFalse(Settings.currentPortable().bodyEntryFullScreen)
    }
}