package com.fitlens.companion.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The workout and schedule editors keep their unsaved draft as JSON text in the screen's saved state (#304). These round
 * trips fail when a field added to one of the draft classes can't be serialised, before a phone does.
 */
class DraftSerializationTest {
    // The same settings as `ScreenState`'s draft Json.
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun aWorkoutsExercisesSurviveTheRoundTrip() {
        val exercises = listOf(
            PlannedExercise(
                exerciseId = 7L, fill = Routines.FILL_PLANNED, superset = 2, restSeconds = 90, restAfterSeconds = Routines.REST_PREVIOUS,
                sets = listOf(
                    PlannedSet(weightKg = 82.5, reps = 5, setType = SetTypes.WARMUP, restSeconds = 120),
                    PlannedSet(distance = 1.5, durationSec = 600, metric = 0.0, liveSec = 120, intensity = 3, sessionType = 1, zone = 2, countA = 4, countB = 0)
                )
            ),
            PlannedExercise(exerciseId = 9L)
        )
        val codec = ListSerializer(PlannedExercise.serializer())
        assertEquals(exercises, json.decodeFromString(codec, json.encodeToString(codec, exercises)))
    }

    @Test
    fun aTrainingPlanAndNoPlanSurviveTheRoundTrip() {
        val plan = TrainingPlan(
            listOf(
                TrainingBlock("Build", 3, PhaseUnit.WEEKS, weightPct = -10, setChange = 1),
                TrainingBlock("Deload", 1, PhaseUnit.WEEKS, deload = true),
                TrainingBlock("Keep going", 1, PhaseUnit.ONGOING)
            ),
            "2026-11-02", repeats = true
        )
        val codec = TrainingPlan.serializer().nullable
        assertEquals(plan, json.decodeFromString(codec, json.encodeToString(codec, plan)))
        assertNull(json.decodeFromString(codec, json.encodeToString(codec, null)))
    }

    @Test
    fun aSchedulesPhasesSurviveTheRoundTrip() {
        val phases = listOf(
            MedPhase(
                3L, true, 8, PhaseUnit.WEEKS,
                listOf(
                    MedDose(1L, "Creatine", 5.0, "g", "08:30", MedDays(weekdays = setOf(1, 3, 5)), "with food"),
                    MedDose(0L, "Prednisone", 20.0, "mg", days = MedDays(every = 2), step = -2.5, stepEvery = 7, stepLimit = 5.0)
                )
            ),
            MedPhase(0L, false, 4, PhaseUnit.WEEKS),
            MedPhase(0L, true, 1, PhaseUnit.ONGOING)
        )
        val codec = ListSerializer(MedPhase.serializer())
        assertEquals(phases, json.decodeFromString(codec, json.encodeToString(codec, phases)))
        val days = MedDays(weekdays = setOf(2, 4))
        assertEquals(days, json.decodeFromString(MedDays.serializer(), json.encodeToString(MedDays.serializer(), days)))
    }
}
