package com.fitlens.companion.ui

import com.fitlens.companion.data.MeasurementDef
import com.fitlens.companion.data.MeasurementGoals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A graph's good-or-bad rule and its key mode travel as one value (#370). */
class MoveRuleTest {

    private fun def(type: Int, target: Double = 0.0) = MeasurementDef("Weight", "kg", 0, type, target, true)

    @Test
    fun aSpecificValueGivesTheTargetKeyFromTheDefinitionNotFromTheProbe() {
        val rule = measurementRule(def(MeasurementGoals.TARGET, 80.0), false)
        assertEquals(ChartKeyMode.TARGET, rule.keyMode)
        assertEquals(true, rule(85.0, 83.0))
        assertEquals(false, rule(83.0, 85.0))
        assertNull(rule(78.0, 82.0))
        // The key is the same with the plain choice on.
        assertEquals(ChartKeyMode.TARGET, measurementRule(def(MeasurementGoals.TARGET, 80.0), true).keyMode)
    }

    @Test
    fun eachDirectionHasItsOwnKeyMode() {
        assertEquals(ChartKeyMode.UP, measurementRule(def(MeasurementGoals.INCREASE), false).keyMode)
        assertEquals(ChartKeyMode.DOWN, measurementRule(def(MeasurementGoals.DECREASE), false).keyMode)
        assertEquals(ChartKeyMode.NONE, measurementRule(def(MeasurementGoals.NONE), true).keyMode)
        assertEquals(ChartKeyMode.NONE, measurementRule(null, true).keyMode)
        assertEquals(ChartKeyMode.UP, measurementRule(null, false).keyMode)
        // A specific value with no target typed has no direction: plain means no colour.
        assertEquals(ChartKeyMode.NONE, measurementRule(def(MeasurementGoals.TARGET, 0.0), true).keyMode)
    }

    @Test
    fun theRuleGivesTheSameVerdictAsMeasurementVerdict() {
        val moves = listOf(80.0 to 81.0, 81.0 to 80.0, 80.0 to 80.0, 78.0 to 82.0, 90.0 to 79.0)
        val defs = listOf(
            null,
            def(MeasurementGoals.NONE),
            def(MeasurementGoals.INCREASE),
            def(MeasurementGoals.DECREASE),
            def(MeasurementGoals.TARGET, 80.0),
            def(MeasurementGoals.TARGET, 0.0)
        )
        for (d in defs) for (plain in listOf(false, true)) {
            val rule = measurementRule(d, plain)
            for ((from, to) in moves) assertEquals(measurementVerdict(d, plain, from, to), rule(from, to))
        }
    }

    @Test
    fun aTargetOfHalfStarsItsNearestPointAndKeyModeOfFallsBackToTheProbe() {
        val points = listOf(
            ChartPoint(1L, 0.0, "2026-10-01"),
            ChartPoint(2L, 0.6, "2026-10-02"),
            ChartPoint(3L, 2.0, "2026-10-03")
        )
        // From 1.0 to 0.0 are both 0.5 from the target, so a probe would see no colour; the key says TARGET.
        val rule = measurementRule(def(MeasurementGoals.TARGET, 0.5), false)
        assertEquals(ChartKeyMode.TARGET, keyModeOf(rule))
        assertEquals(2L, bestPoint(points, rule)?.x)
        // A rule that is not a MoveRule still has its key worked out by probing.
        assertEquals(ChartKeyMode.DOWN, keyModeOf(::lowerIsBetter))
    }
}
