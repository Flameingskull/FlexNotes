package com.fitlens.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SportLibraryTest {

    private fun session(id: Long, name: String, kind: String?, fields: Int?) =
        Exercise(id, name, 0L, ExerciseTypes.SESSION, null, sessionKind = kind, sessionFields = fields)

    @Test
    fun noChoiceRecordsEveryField() {
        SessionMetrics.all.forEach { assertTrue(SessionMetrics.has(null, it)) }
        assertFalse(SessionMetrics.has(SessionMetrics.ZONE, SessionMetrics.TYPE))
    }

    @Test
    fun withNothingToCompareTheKindsUsualFieldsAreSuggested() {
        val s = SportLibrary.suggest(SessionKinds.RACKET, emptyList())
        assertEquals(SessionKinds.defaults(SessionKinds.RACKET), s.fields)
        assertTrue(s.basedOn.isEmpty())
    }

    @Test
    fun aNewSportFollowsMostSportsOfTheSameKind() {
        val zoneOnly = SessionMetrics.ZONE
        val library = listOf(
            session(1, "Tennis", SessionKinds.RACKET, zoneOnly or SessionMetrics.LIVE),
            session(2, "Squash", SessionKinds.RACKET, zoneOnly),
            session(3, "Badminton", SessionKinds.RACKET, zoneOnly),
            session(4, "Boxing", SessionKinds.COMBAT, SessionMetrics.ALL),
            Exercise(5, "Bench Press", 0L, ExerciseTypes.WEIGHT_REPS, null)
        )
        val s = SportLibrary.suggest(SessionKinds.RACKET, library)
        assertEquals(zoneOnly, s.fields)
        assertEquals(listOf("Badminton", "Squash", "Tennis"), s.basedOn)
        // The exercise being edited isn't compared with itself.
        assertEquals(listOf("Badminton", "Squash"), SportLibrary.suggest(SessionKinds.RACKET, library, exceptId = 1).basedOn)
    }

    @Test
    fun theBuiltInSportsAreSessionsWithAKindAndUniqueNames() {
        assertEquals(16, SportLibrary.exercises.size)
        assertEquals(16, SportLibrary.exercises.map { it.name.lowercase() }.toSet().size)
        SportLibrary.exercises.forEach { assertTrue(it.kind in SessionKinds.all) }
    }

    @Test
    fun theExtraSportsHaveRealActivitiesAndAreNotTheSeededOnes() {
        val seeded = SportLibrary.exercises.map { it.name.lowercase() }.toSet()
        assertEquals(SportLibrary.extras.size, SportLibrary.extras.map { it.name.lowercase() }.toSet().size)
        SportLibrary.extras.forEach {
            assertTrue(it.name, it.name.lowercase() !in seeded)
            assertTrue(it.name, it.kind in SessionKinds.all)
            assertTrue(it.name, it.type == ExerciseTypes.SESSION || it.type == ExerciseTypes.ROUNDS)
            // Every sport carries an activity that exists, so its calories use that sport's METs.
            assertTrue(it.name, Energy.Activity.of(it.activity) != null)
        }
        assertTrue(SportLibrary.extras.any { it.type == ExerciseTypes.ROUNDS })
    }

    @Test
    fun thePickerMarksWhatTheLibraryAlreadyHasIgnoringCaseAndSpacing() {
        val picks = SportLibrary.picks(listOf("  bag   work ", "Bench Press"))
        assertTrue(picks.first { it.sport.name == "Bag Work" }.inLibrary)
        assertFalse(picks.first { it.sport.name == "Pad Work" }.inLibrary)
        assertEquals(SportLibrary.extras.size, picks.size)
    }

    @Test
    fun addingSkipsNamesAlreadyInTheLibraryAndAnythingNotPicked() {
        val add = SportLibrary.toAdd(listOf("Bag Work", "pad work", "Fencing", "Not A Sport"), listOf("Bag Work"))
        assertEquals(listOf("Pad Work", "Fencing"), add.map { it.name })
        assertTrue(SportLibrary.toAdd(emptyList(), emptyList()).isEmpty())
    }
}
