package com.fitlens.companion.ui

import com.fitlens.companion.data.BodyPart
import com.fitlens.companion.data.MRecord
import com.fitlens.companion.data.MeasurementDef
import com.fitlens.companion.data.MeasurementGoals
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.Sources
import com.fitlens.companion.ui.LongHistoryFixture
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import android.app.Application
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class PhotoCompareVerdictTest {

    private fun snapshotWith(goalType: Int, goalValue: Double = 0.0): Snapshot {
        val base = LongHistoryFixture.snapshot()
        val defs = listOf(MeasurementDef("Waist", "cm", 0, goalType, goalValue, true))
        val records = listOf(
            MRecord(1, "Waist", "cm", "2026-09-01", "07:30:00", 92.0, null, Sources.FLEXNOTES),
            MRecord(2, "Waist", "cm", "2026-10-01", "07:30:00", 90.0, null, Sources.FLEXNOTES)
        )
        val body = BodyPart(defs, records, "kg")
        return Snapshot(base.library, base.setPart, base.notes, body, base.photoPart, "kg", File("photos"))
    }

    private fun better(goalType: Int, goalValue: Double = 0.0, plain: Boolean = false): Boolean? {
        return compareRows(snapshotWith(goalType, goalValue), "2026-09-01", "2026-10-01", plain).single().better
    }

    @Test
    fun downIsGoodMakesASmallerWaistGreen() {
        val result = better(MeasurementGoals.DECREASE)
        assertTrue(result == true)
    }

    @Test
    fun upIsGoodMakesItRed() {
        val result = better(MeasurementGoals.INCREASE)
        assertTrue(result == false)
    }

    @Test
    fun closerToTheTargetIsGood() {
        val result = better(MeasurementGoals.TARGET, 85.0)
        assertTrue(result == true)
    }

    @Test
    fun furtherFromTheTargetIsBad() {
        val result = better(MeasurementGoals.TARGET, 95.0)
        assertTrue(result == false)
    }

    @Test
    fun noDirectionFollowsTheDefault() {
        val result = better(MeasurementGoals.NONE, plain = false)
        assertTrue(result == false)
    }

    @Test
    fun noDirectionIsPlainWhenChosen() {
        val result = better(MeasurementGoals.NONE, plain = true)
        assertNull(result)
    }

    @Test
    fun aGoalIgnoresThePlainChoice() {
        val result = better(MeasurementGoals.DECREASE, plain = true)
        assertTrue(result == true)
    }
}
