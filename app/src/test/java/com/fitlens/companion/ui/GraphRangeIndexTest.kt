package com.fitlens.companion.ui

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h3200dp-mdpi")
class GraphRangeIndexTest : FunctionTest() {

    @Test
    fun allTimeIsTheLastRangeInTheMenu() {
        assertEquals(RANGES.lastIndex, RANGE_ALL)
        assertEquals("All", RANGES[RANGE_ALL].first)
    }

    @Test
    fun everyOtherRangeIsAWholeNumberOfMonths() {
        for (i in 0 until RANGE_ALL) {
            assertEquals(listOf(1, 3, 6, 12)[i], rangeMonths(i))
        }
        assertEquals(0, rangeMonths(RANGE_ALL))
    }
}
