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

    @Test
    fun theChangeRunsEarlierToLaterWhicheverSideEachPhotoIsOn() {
        val snap = snapshotWith(MeasurementGoals.DECREASE)
        val forward = compareRows(snap, "2026-09-01", "2026-10-01", false).single()
        val reversed = compareRows(snap, "2026-10-01", "2026-09-01", false).single()
        org.junit.Assert.assertEquals(forward.change, reversed.change)
        org.junit.Assert.assertTrue(reversed.change.isNotBlank())
        assertTrue(reversed.better == true)
        // The columns stay under their photos: the newer photo is on the left now.
        org.junit.Assert.assertEquals(forward.left, reversed.right)
        org.junit.Assert.assertEquals(forward.right, reversed.left)
    }

    @Test
    fun equalDistanceFromATargetHasNoColourInPhotoCompare() {
        // 92 and 90 are both 1 away from 91.
        val snap = snapshotWith(MeasurementGoals.TARGET, 91.0)
        assertNull(compareRows(snap, "2026-09-01", "2026-10-01", false).single().better)
        assertNull(compareRows(snap, "2026-10-01", "2026-09-01", false).single().better)
    }

    @Test
    fun theImageWritesTheChangeBesideTheLaterPhotosValue() {
        val snap = snapshotWith(MeasurementGoals.DECREASE)
        // Earlier photo on the left: 92 then 90, the change goes beside the right (later) value.
        val forward = compareImageRows(compareRows(snap, "2026-09-01", "2026-10-01", false), leftIsLater = false).single()
        org.junit.Assert.assertEquals("92", forward.second)
        org.junit.Assert.assertEquals("90  −2", forward.third)
        // After Swap the later photo is on the left: the change goes beside the left value.
        val swapped = compareImageRows(compareRows(snap, "2026-10-01", "2026-09-01", false), leftIsLater = true).single()
        org.junit.Assert.assertEquals("90  −2", swapped.second)
        org.junit.Assert.assertEquals("92", swapped.third)
    }
}
