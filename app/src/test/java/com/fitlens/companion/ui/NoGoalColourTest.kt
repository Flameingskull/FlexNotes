package com.fitlens.companion.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import com.fitlens.companion.R
import com.fitlens.companion.data.MeasurementDef
import com.fitlens.companion.data.MeasurementGoals
import com.fitlens.companion.data.PortableSettings
import com.fitlens.companion.data.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Settings choice for measurements with no goal direction, and that it reaches coloured figures at once (#330). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h3200dp-mdpi")
class NoGoalColourTest : FunctionTest() {

    @Test
    fun theRowIsOnSettingsAndSearchFindsIt() {
        openScreen(LongHistoryFixture.snapshot()) { snap -> SettingsScreen(snap, nav) }
        assertShows(app.getString(R.string.settings_no_goal_colour))
        type("which way")
        assertShows(app.getString(R.string.settings_no_goal_colour))
        assertFalse(Settings.portable.value.plainNoGoal)
    }

    @Test
    fun choosingPlainInSettingsSavesIt() {
        openScreen(LongHistoryFixture.snapshot()) { snap -> SettingsScreen(snap, nav) }
        tap(app.getString(R.string.settings_no_goal_colour))
        tap(app.getString(R.string.mg_plain_on))
        compose.waitForIdle()
        assertTrue(Settings.portable.value.plainNoGoal)
    }

    @Test
    fun theChangeColourFollowsTheSettingAtOnce() {
        var rise: Color? = null
        var fall: Color? = null
        var normal: Color? = null
        openScreen(LongHistoryFixture.snapshot()) {
            Column {
                rise = changeColour(null, 80.0, 81.0)
                fall = changeColour(null, 81.0, 80.0)
                normal = MaterialTheme.colorScheme.onSurface
                Text("x")
            }
        }
        assertEquals(Brand.Rise, rise)
        assertEquals(Brand.Fall, fall)

        // Put the preference on screen without saving it: a real save runs in the background and races with the
        // test's database being closed (CI: "attempt to re-open an already-closed object").
        compose.runOnIdle { Settings.showWith(PortableSettings(plainNoGoal = true)) }
        compose.waitForIdle()

        assertEquals(normal, rise)
        assertEquals(normal, fall)
    }

    @Test
    fun aMeasurementWithADirectionIgnoresThePlainChoice() {
        var up: Color? = null
        var down: Color? = null
        openScreen(LongHistoryFixture.snapshot(), preferences = PortableSettings(plainNoGoal = true)) {
            Column {
                up = changeColour(MeasurementDef("Weight", "kg", 0, MeasurementGoals.INCREASE, 0.0, true), 80.0, 81.0)
                down = changeColour(MeasurementDef("Weight", "kg", 0, MeasurementGoals.DECREASE, 0.0, true), 80.0, 81.0)
                Text("x")
            }
        }
        assertEquals(Brand.Rise, up)
        assertEquals(Brand.Fall, down)
    }

    @Test
    fun searchingTheAmericanSpellingFindsTheRow() {
        openScreen(LongHistoryFixture.snapshot()) { snap -> SettingsScreen(snap, nav) }
        type("color")
        assertShows(app.getString(R.string.settings_no_goal_colour))
    }
}
