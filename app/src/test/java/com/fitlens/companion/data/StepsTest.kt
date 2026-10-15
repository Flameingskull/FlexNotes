package com.fitlens.companion.data

import com.fitlens.companion.data.BodyFat.Sex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** Daily steps (#175): counter readings shared out by date, and the calories the steps add. */
class StepsTest {
    private val zone = ZoneId.of("Europe/London")
    private fun at(iso: String): Long = LocalDateTime.parse(iso).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun stepsOnOneDayGoToThatDay() {
        val out = Steps.split(Steps.Reading(10_000, at("2026-10-15T09:00"), 4), Steps.Reading(12_500, at("2026-10-15T09:15"), 4), 0, zone)
        assertEquals(mapOf("2026-10-15" to 2_500L), out)
    }

    @Test
    fun aGapAcrossMidnightIsSharedByTime() {
        // 22:00 to 02:00: two hours on each side.
        val out = Steps.split(Steps.Reading(0, at("2026-10-14T22:00"), 4), Steps.Reading(4_001, at("2026-10-15T02:00"), 4), 0, zone)
        assertEquals(2_000L, out["2026-10-14"])
        assertEquals(2_001L, out["2026-10-15"])
        assertEquals(4_001L, out.values.sum())
    }

    @Test
    fun afterARestartTheWholeCountIsNew() {
        val boot = at("2026-10-15T08:00")
        // The counter went down: the phone restarted at 08:00 and has counted 900 steps since.
        val down = Steps.split(Steps.Reading(50_000, at("2026-10-14T23:00"), 4), Steps.Reading(900, at("2026-10-15T09:00"), 4), boot, zone)
        assertEquals(mapOf("2026-10-15" to 900L), down)
        // The restart count changed although the counter is higher than before.
        val counted = Steps.split(Steps.Reading(100, at("2026-10-15T07:00"), 4), Steps.Reading(3_000, at("2026-10-15T09:00"), 5), boot, zone)
        assertEquals(mapOf("2026-10-15" to 3_000L), counted)
    }

    @Test
    fun nothingForNoStepsABackwardClockOrAFaultyCount() {
        assertTrue(Steps.split(Steps.Reading(500, at("2026-10-15T09:00"), 4), Steps.Reading(500, at("2026-10-15T10:00"), 4), 0, zone).isEmpty())
        assertTrue(Steps.split(Steps.Reading(500, at("2026-10-15T10:00"), 4), Steps.Reading(900, at("2026-10-15T09:00"), 4), 0, zone).isEmpty())
        assertTrue(Steps.split(Steps.Reading(0, at("2026-10-15T09:00"), 4), Steps.Reading(90_000, at("2026-10-15T10:00"), 4), 0, zone).isEmpty())
    }

    @Test
    fun readingsRoundTrip() {
        val r = Steps.Reading(12_345, 1_792_000_000_000, 7)
        assertEquals(r, Steps.Reading.decode(r.encode()))
        assertNull(Steps.Reading.decode("rubbish"))
        assertNull(Steps.Reading.decode(null))
    }

    @Test
    fun stepCaloriesCountOnlyStepsAboveTheBaseline() {
        assertEquals(0.0, Energy.stepKcal(2_000.0, 80.0, 180.0, Sex.MALE), 1e-9)
        // 7,500 steps above the baseline × 0.747 m = 5,602.5 m; 0.1 ml/kg/m × 80 kg = 44,820 ml; × 5 kcal/L.
        assertEquals(224.1, Energy.stepKcal(10_000.0, 80.0, 180.0, Sex.MALE), 1e-9)
        assertEquals(0.75, Energy.strideMetres(null, null), 1e-9)
        assertEquals(0.68145, Energy.strideMetres(165.0, Sex.FEMALE), 1e-9)
    }

    private fun bout(metres: Double, seconds: Double, cardio: Boolean = true) = Energy.Bout(1L, cardio, seconds, metres)

    @Test
    fun loggedWalksAndRunsAreTakenAsTheirSteps() {
        // 5 km in an hour is a walk: 5,000 m over a 0.747 m stride.
        assertEquals(5_000.0 / 0.747, Energy.walkedSteps(bout(5_000.0, 3_600.0), null, 180.0, Sex.MALE), 1e-6)
        // 5 km in 25 minutes is a run, with a stride 1.4 times as long.
        assertEquals(5_000.0 / (0.747 * 1.4), Energy.walkedSteps(bout(5_000.0, 1_500.0), null, 180.0, Sex.MALE), 1e-6)
        // An exercise's own activity decides, even without a time.
        assertEquals(5_000.0 / 0.747, Energy.walkedSteps(bout(5_000.0, 0.0), Energy.Activity.WALKING, 180.0, Sex.MALE), 1e-6)
        assertEquals(0.0, Energy.walkedSteps(bout(5_000.0, 900.0), Energy.Activity.CYCLING, 180.0, Sex.MALE), 1e-9)
        // Cycling speed, no time, no distance, or not cardio: no steps.
        assertEquals(0.0, Energy.walkedSteps(bout(10_000.0, 1_200.0), null, 180.0, Sex.MALE), 1e-9)
        assertEquals(0.0, Energy.walkedSteps(bout(5_000.0, 0.0), null, 180.0, Sex.MALE), 1e-9)
        assertEquals(0.0, Energy.walkedSteps(bout(0.0, 1_800.0), null, 180.0, Sex.MALE), 1e-9)
        assertEquals(0.0, Energy.walkedSteps(bout(5_000.0, 3_600.0, cardio = false), null, 180.0, Sex.MALE), 1e-9)
    }

    @Test
    fun aLoggedWalkIsNotCountedTwice() {
        // 12,000 steps on a day with a logged 5 km walk: only the 5,306 other steps add daily activity.
        val walked = Energy.walkedSteps(bout(5_000.0, 3_600.0), null, 180.0, Sex.MALE)
        val walk = Energy.boutKcal(bout(5_000.0, 3_600.0), 80.0).kcal
        val both = Energy.stepKcal(12_000.0 - walked, 80.0, 180.0, Sex.MALE) + walk
        assertEquals(Energy.stepKcal(12_000.0, 80.0, 180.0, Sex.MALE), both, 1.0)
        assertTrue(Energy.stepKcal(12_000.0 - walked, 80.0, 180.0, Sex.MALE) < Energy.stepKcal(12_000.0, 80.0, 180.0, Sex.MALE))
    }
}
