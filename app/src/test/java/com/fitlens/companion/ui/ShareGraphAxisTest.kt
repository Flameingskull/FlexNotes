package com.fitlens.companion.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareGraphAxisTest {

    private fun cp(x: Long, y: Double, date: String) = ChartPoint(x, y, date)

    @Test
    fun withoutAPeriodTheAxisIsTheFirstToTheLastPoint() {
        val pts = listOf(cp(100, 1.0, "d"), cp(140, 1.0, "d"))
        val (first, last) = graphAxisDays(pts, null)
        assertEquals(100L to 140L, first to last)
    }

    @Test
    fun aSinglePointIsAtLeastADayWide() {
        val pts = listOf(cp(100, 1.0, "d"))
        val (first, last) = graphAxisDays(pts, null)
        assertEquals(100L to 101L, first to last)
    }

    @Test
    fun aPeriodSetsTheAxisEvenWhenThePointsAreFewAndLate() {
        val pts = listOf(cp(330, 1.0, "d"), cp(335, 1.0, "d"))
        val period = ChartPeriod(100L, 340L)
        val (first, last) = graphAxisDays(pts, period)
        assertEquals(100L to 340L, first to last)
    }

    @Test
    fun theAxisAgreesWithTheOneOnScreen() {
        val pts = listOf(cp(40, 1.0, "d"), cp(50, 1.0, "d"))
        val period = ChartPeriod(0L, 100L)
        val (first, last) = graphAxisDays(pts, period)
        val viewRange = viewRange(pts, ChartViewport(), period)
        assertEquals(first.toLong() to last.toLong(), viewRange.first.toLong() to viewRange.second.toLong())
    }
}
