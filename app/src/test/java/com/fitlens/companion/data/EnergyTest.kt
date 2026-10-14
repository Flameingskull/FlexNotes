package com.fitlens.companion.data

import com.fitlens.companion.data.BodyFat.Sex
import com.fitlens.companion.data.Energy.Goal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Energy (#172, #173, #174): each equation against a worked example, then goals, macros and the warnings. */
class EnergyTest {

    @Test
    fun mifflinStJeorMatchesWorkedExamples() {
        // 10 × 80 + 6.25 × 180 − 5 × 30 + 5
        assertEquals(1780.0, Energy.mifflin(Sex.MALE, 80.0, 180.0, 30), 1e-9)
        // 10 × 60 + 6.25 × 165 − 5 × 25 − 161
        assertEquals(1345.25, Energy.mifflin(Sex.FEMALE, 60.0, 165.0, 25), 1e-9)
    }

    @Test
    fun katchMcArdleUsesLeanMass() {
        // 80 kg at 15 % body fat is 68 kg lean: 370 + 21.6 × 68.
        assertEquals(1838.8, Energy.katch(68.0), 1e-9)
        val r = Energy.resting(Energy.Inputs(null, null, null, 80.0, 15.0, null), Energy.Formula.AUTO)
        assertEquals(Energy.Formula.KATCH, r.formula)
        assertEquals(1838.8, r.bmr!!, 1e-9)
    }

    @Test
    fun automaticFallsBackToMifflinAndNamesWhatsMissing() {
        val full = Energy.Inputs(Sex.MALE, 30, 180.0, 80.0, null, Energy.Lifestyle.SITTING)
        assertEquals(1780.0, Energy.resting(full, Energy.Formula.AUTO).bmr!!, 1e-9)
        // Mifflin chosen by hand ignores a known body fat.
        assertEquals(Energy.Formula.MIFFLIN, Energy.resting(full.copy(bodyFat = 15.0), Energy.Formula.MIFFLIN).formula)
        val r = Energy.resting(full.copy(sex = null, heightCm = null), Energy.Formula.AUTO)
        assertNull(r.bmr)
        assertEquals(listOf(Energy.Missing.SEX, Energy.Missing.HEIGHT), r.missing)
    }

    @Test
    fun ageIsWholeYearsOnTheDay() {
        assertEquals(35, Energy.age("1990-10-15", "2026-10-14"))
        assertEquals(36, Energy.age("1990-10-15", "2026-10-15"))
        assertNull(Energy.age("2030-01-01", "2026-10-14"))
        assertNull(Energy.age(null, "2026-10-14"))
    }

    @Test
    fun exerciseCaloriesAreNetOfRest() {
        // 5 METs for an hour at 80 kg: (5 − 1) × 80.
        assertEquals(320.0, Energy.netMetKcal(5.0, 80.0, 1.0), 1e-9)
        // Running 5 km in 30 min (166.7 m/min) at 70 kg: net VO₂ 33.3 ml/kg/min × 70 × 30 / 1000 × 5.
        assertEquals(350.0, Energy.acsmNetKcal(5000.0, 1800.0, 70.0), 1e-6)
        // Walking 5 km in an hour (83.3 m/min): net VO₂ 8.33.
        assertEquals(175.0, Energy.acsmNetKcal(5000.0, 3600.0, 70.0), 1e-6)
        val run = Energy.boutKcal(Energy.Bout(1, cardio = true, seconds = 1800.0, metres = 5000.0), 70.0)
        assertEquals(Energy.Method.ACSM_RUN, run.method)
        // 30 km in an hour is cycling: 12 METs.
        val ride = Energy.boutKcal(Energy.Bout(2, cardio = true, seconds = 3600.0, metres = 30000.0), 70.0)
        assertEquals(Energy.Method.CYCLING, ride.method)
        assertEquals(11.0 * 70.0, ride.kcal, 1e-9)
        // An exercise's own MET wins.
        assertEquals(2.0 * 80.0, Energy.boutKcal(Energy.Bout(3, false, 3600.0, met = 3.0), 80.0).kcal, 1e-9)
    }

    @Test
    fun strengthEffortFollowsRpe() {
        assertEquals(Energy.Effort.MODERATE, Energy.Effort.fromRpe(null))
        assertEquals(Energy.Effort.LIGHT, Energy.Effort.fromRpe(6.0))
        assertEquals(Energy.Effort.VIGOROUS, Energy.Effort.fromRpe(9.0))
    }

    @Test
    fun aLoggedWorkoutTimeIsSharedBySets() {
        val sets = List(3) { Energy.SetInput(1, false, 0.0, 0.0, null, 90.0) } +
            Energy.SetInput(2, false, 0.0, 0.0, null, 90.0)
        val shared = Energy.bouts(sets, workoutSeconds = 3600.0).associateBy { it.exerciseId }
        assertEquals(2700.0, shared.getValue(1).seconds, 1e-9)
        assertEquals(900.0, shared.getValue(2).seconds, 1e-9)
        // With no workout time, each set is 40 s of work plus its rest.
        val own = Energy.bouts(sets, workoutSeconds = 0.0).associateBy { it.exerciseId }
        assertEquals(3 * 130.0, own.getValue(1).seconds, 1e-9)
    }

    @Test
    fun goalsGiveTheirTargets() {
        val tdee = 2500.0
        // 0.5 % of 80 kg a week is 0.4 kg: 0.4 × 7700 / 7 = 440 kcal a day.
        assertEquals(2060.0, Energy.target(Goal.LoseRate(0.5), tdee, 80.0), 1e-9)
        assertEquals(2000.0, Energy.target(Goal.LoseDeficit(20), tdee, 80.0), 1e-9)
        assertEquals(2250.0, Energy.target(Goal.Recomp, tdee, 80.0), 1e-9)
        assertEquals(2687.5, Energy.target(Goal.LeanBulk, tdee, 80.0), 1e-9)
        assertEquals(2937.5, Energy.target(Goal.Bulk, tdee, 80.0), 1e-9)
        assertEquals(tdee, Energy.target(Goal.Maintain, tdee, 80.0), 1e-9)
        assertEquals(-0.4, Energy.weeklyChangeKg(2060.0, tdee), 1e-9)
    }

    @Test
    fun goalsSurviveBeingStored() {
        listOf(Goal.Maintain, Goal.LoseRate(0.75), Goal.LoseDeficit(25), Goal.Recomp, Goal.LeanBulk, Goal.Bulk, Goal.Custom(2200))
            .forEach { assertEquals(it, Goal.decode(it.encode())) }
        assertNull(Goal.decode("nonsense"))
        assertNull(Goal.decode("custom:12"))
    }

    @Test
    fun warningsFlagUnsafeTargets() {
        // 1 % a week exactly is allowed.
        val onePercent = Energy.target(Goal.LoseRate(1.0), 2500.0, 80.0)
        assertTrue(Energy.Warning.FAST_LOSS !in Energy.warnings(onePercent, 1780.0, 2500.0, 80.0, Sex.MALE))
        val low = Energy.warnings(1300.0, 1780.0, 2500.0, 80.0, Sex.MALE)
        assertEquals(listOf(Energy.Warning.BELOW_BMR, Energy.Warning.BELOW_FLOOR, Energy.Warning.FAST_LOSS), low)
        assertTrue(Energy.Warning.BELOW_FLOOR !in Energy.warnings(1300.0, 1200.0, 1500.0, 60.0, Sex.FEMALE))
    }

    @Test
    fun macrosSplitTheTarget() {
        // In a deficit at 80 kg: protein 2.2 g/kg = 176 g, fat max(64, 44) = 64 g, carbohydrate the rest.
        val m = Energy.macros(2000.0, 80.0, Goal.LoseDeficit(20))
        assertEquals(Energy.Macros(176, 64, 180), m)
        assertEquals(2000, m.kcal)
        // Bulking keeps protein at 1.6 g/kg and fat at 20 % of calories when that's more.
        assertEquals(128, Energy.macros(3000.0, 80.0, Goal.Bulk).proteinG)
        assertEquals(67, Energy.macros(3000.0, 80.0, Goal.Bulk).fatG)
    }

    @Test
    fun aPlannedWeekAveragesOverSevenDays() {
        // 3 × 60 min at 5 METs and 80 kg: 3 h × 4 × 80 = 960 kcal a week.
        assertEquals(960.0 / 7.0, Energy.Plan.DEFAULT.dailyKcal(80.0), 1e-9)
        assertEquals(Energy.Plan(4, 45, Energy.Effort.VIGOROUS), Energy.Plan.decode(Energy.Plan(4, 45, Energy.Effort.VIGOROUS).encode()))
        assertNull(Energy.Plan.decode("x"))
    }
}
