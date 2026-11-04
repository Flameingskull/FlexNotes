package com.fitlens.companion.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Full-screen zoom (#96): time and values zoom apart, stay inside the chart and reset to the whole chart. */
class ChartViewportTest {

    private val eps = 1e-5f

    @Test
    fun zoomingValuesLeavesTimeAlone() {
        val v = ChartViewport().transformY(centroid = 0.5f, pan = 0f, zoom = 2f)
        assertEquals(0f, v.from, eps)
        assertEquals(1f, v.to, eps)
        assertEquals(0.25f, v.yFrom, eps)
        assertEquals(0.75f, v.yTo, eps)
        assertFalse(v.isFull)
        assertTrue(v.copy(yFrom = 0f, yTo = 1f).isFull)
    }

    @Test
    fun draggingDownShowsHigherValues() {
        val zoomed = ChartViewport().transformY(0.5f, 0f, 4f)
        val moved = zoomed.transformY(0.5f, pan = 0.5f, zoom = 1f)
        assertTrue(moved.yFrom > zoomed.yFrom)
        assertEquals(zoomed.yTo - zoomed.yFrom, moved.yTo - moved.yFrom, eps)
    }

    @Test
    fun valuesStayInsideTheChartAndStopAtTheLimit() {
        val v = ChartViewport().transformY(0f, pan = -10f, zoom = 1000f)
        assertEquals(0f, v.yFrom, eps)
        assertEquals(0.05f, v.yTo, eps)
        val out = v.transformY(0.5f, 0f, 0.001f)
        assertTrue(out.valuesFull)
    }

    @Test
    fun zoomingTimeLeavesValuesAlone() {
        val v = ChartViewport(yFrom = 0.2f, yTo = 0.6f).transform(0.5f, 0f, 2f)
        assertEquals(0.2f, v.yFrom, eps)
        assertEquals(0.6f, v.yTo, eps)
        assertEquals(0.5f, v.to - v.from, eps)
    }

    @Test
    fun withoutAPeriodTheAxisIsTheDataSpan() {
        val points = listOf(ChartPoint(100, 0.0, "2024-01-01"), ChartPoint(110, 0.0, "2024-01-02"))
        val (xMin, xMax) = viewRange(points, ChartViewport())
        assertEquals(100.0, xMin, 1e-9)
        assertEquals(110.0, xMax, 1e-9)
    }

    @Test
    fun aSingleDayWithoutAPeriodIsWidenedToTwoDays() {
        val point = ChartPoint(5, 0.0, "2024-01-01")
        val (xMin, xMax) = viewRange(listOf(point), ChartViewport())
        assertEquals(4.0, xMin, 1e-9)
        assertEquals(6.0, xMax, 1e-9)
    }

    @Test
    fun aPeriodWiderThanTheDataSetsTheAxis() {
        val points = listOf(ChartPoint(200, 0.0, "2024-01-01"), ChartPoint(210, 0.0, "2024-01-02"))
        val period = ChartPeriod(100L, 300L)
        val (xMin, xMax) = viewRange(points, ChartViewport(), period)
        assertEquals(100.0, xMin, 1e-9)
        assertEquals(300.0, xMax, 1e-9)
    }

    @Test
    fun zoomIsAFractionOfThePeriod() {
        val points = listOf(ChartPoint(40, 0.0, "2024-01-01"), ChartPoint(50, 0.0, "2024-01-02"))
        val period = ChartPeriod(0L, 100L)
        val (xMin, xMax) = viewRange(points, ChartViewport(from = 0.5f, to = 1f), period)
        assertEquals(50.0, xMin, 1e-9)
        assertEquals(100.0, xMax, 1e-9)
    }

    @Test
    fun theTrendRangeAndThePlotRangeAreTheSame() {
        val points = listOf(ChartPoint(100, 0.0, "2024-01-01"), ChartPoint(110, 0.0, "2024-01-02"))
        val vp = ChartViewport()
        val period = ChartPeriod(90L, 120L)
        val (xMin1, xMax1) = viewRange(points, vp, period)
        val (xMin2, xMax2) = viewRange(points, vp, period)
        assertEquals(xMin1, xMin2, 1e-9)
        assertEquals(xMax1, xMax2, 1e-9)
    }
}
