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

/** The "compare a body value with" choice is saved, restored after a reload, and defaults to the earlier day (#372). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class ChangeFromSettingTest : FunctionTest() {

    @Test
    fun theValueJustBeforeIsStoredAndReadBack() {
        Settings.updatePortable { it.copy(changeFromEarlierDay = false) }
        runBlocking { Settings.awaitWrites() }
        Settings.showWith(PortableSettings())
        assertTrue(Settings.portable.value.changeFromEarlierDay)
        Settings.reloadPortable()
        assertFalse(Settings.portable.value.changeFromEarlierDay)
    }

    @Test
    fun aMissingRowReadsTheEarlierDay() {
        Settings.updatePortable { it.copy(changeFromEarlierDay = false) }
        runBlocking { Settings.awaitWrites() }
        Settings.updatePortable { it.copy(changeFromEarlierDay = true) }
        runBlocking { Settings.awaitWrites() }
        Settings.showWith(PortableSettings(changeFromEarlierDay = false))
        Settings.reloadPortable()
        assertTrue(Settings.portable.value.changeFromEarlierDay)
    }
}
