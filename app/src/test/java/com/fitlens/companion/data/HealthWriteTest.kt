package com.fitlens.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * Writing to Health Connect (#191): which values and workouts are written, and how a sync compares them with what was
 * written before, so nothing is written twice, edits replace and deletes are deleted. Synthetic data only.
 */
class HealthWriteTest {

    private fun rec(id: Long, name: String, unit: String, value: Double, time: String = "07:30:00", source: String = Sources.FLEXNOTES) =
        MRecord(id, name, unit, "2026-10-20", time, value, null, source)

    private fun set(id: Long, exercise: Long, date: String, position: Long, source: String = Sources.FLEXNOTES) =
        SetRow(id, exercise, date, 100.0, 5, 0.0, 0, false, null, source = source, position = position)

    @Test
    fun bodyweightIsWrittenInKilogramsAndHealthConnectsOwnIsNot() {
        val items = HealthWrite.readings(
            HealthWrite.Out.WEIGHT,
            listOf(
                rec(1, "Bodyweight", "kg", 80.0),
                rec(2, "Body Weight", "lbs", 176.0),
                rec(3, "Bodyweight", "kg", 79.0, source = Sources.HEALTH_CONNECT),
                rec(4, "Waist", "cm", 80.0),
                rec(5, "Bodyweight", "kg", 0.0)
            )
        )
        assertEquals(listOf("flexnotes-weight-1", "flexnotes-weight-2"), items.map { it.clientId })
        assertEquals(176.0 * WeightUnits.KG_PER_LB, items[1].value, 1e-9)
        assertEquals(LocalDateTime.of(2026, 10, 20, 7, 30), items[0].start)
    }

    @Test
    fun bodyFatAndATimelessValueAtNoon() {
        val items = HealthWrite.readings(HealthWrite.Out.BODY_FAT, listOf(rec(7, BodyFat.NAME, "%", 18.5, time = ""), rec(8, "Bodyweight", "kg", 80.0)))
        assertEquals(1, items.size)
        assertEquals("flexnotes-body-fat-7", items[0].clientId)
        assertEquals(LocalDateTime.of(2026, 10, 20, 12, 0), items[0].start)
    }

    @Test
    fun planWritesNewAndChangedOnceAndDeletesWhatsGone() {
        val t = LocalDateTime.of(2026, 10, 20, 7, 30)
        val same = HealthWrite.Item("flexnotes-weight-1", t, value = 80.0)
        val changed = HealthWrite.Item("flexnotes-weight-2", t, value = 81.0)
        val fresh = HealthWrite.Item("flexnotes-weight-3", t, value = 82.0)
        val written = listOf(
            HealthWrite.Written("flexnotes-weight-1", 1, same.key),
            HealthWrite.Written("flexnotes-weight-2", 3, HealthWrite.Item("flexnotes-weight-2", t, value = 80.5).key),
            HealthWrite.Written("flexnotes-weight-9", 1, "gone")
        )
        val plan = HealthWrite.plan(listOf(same, changed, fresh, fresh), written)
        assertEquals(listOf(changed to 4L, fresh to 1L), plan.write)
        assertEquals(listOf("flexnotes-weight-9"), plan.delete)
        // Written again with nothing changed: nothing to do.
        val again = HealthWrite.plan(listOf(same), listOf(HealthWrite.Written("flexnotes-weight-1", 1, same.key)))
        assertTrue(again.write.isEmpty() && again.delete.isEmpty())
    }

    @Test
    fun aWorkoutDayNeedsItsOwnSetsAndAStartAndEnd() {
        val sets = mapOf(
            "2026-10-20" to listOf(set(1, 20, "2026-10-20", 2), set(2, 10, "2026-10-20", 1), set(3, 20, "2026-10-20", 3)),
            "2026-10-21" to listOf(set(4, 10, "2026-10-21", 1)),
            "2026-10-22" to listOf(set(5, 30, "2026-10-22", 1, source = Sources.HEALTH_CONNECT))
        )
        val times = mapOf(
            "2026-10-20" to listOf(
                WorkoutTime("2026-10-20", "2026-10-20 18:00:00", "2026-10-20 18:40:00"),
                WorkoutTime("2026-10-20", "2026-10-20 19:00:00", "2026-10-20 19:30:00")
            ),
            "2026-10-21" to listOf(WorkoutTime("2026-10-21", "2026-10-21 18:00:00", "")),
            "2026-10-22" to listOf(WorkoutTime("2026-10-22", "2026-10-22 07:00:00", "2026-10-22 07:30:00"))
        )
        val names = mapOf(10L to "Squat", 20L to "Bench Press")
        val items = HealthWrite.workouts(sets, times, { names[it] }, "FlexNotes workout")
        assertEquals(1, items.size)
        val w = items[0]
        assertEquals("flexnotes-workouts-2026-10-20", w.clientId)
        assertEquals(LocalDateTime.of(2026, 10, 20, 18, 0), w.start)
        assertEquals(LocalDateTime.of(2026, 10, 20, 19, 30), w.end)
        assertEquals("Squat, Bench Press", w.notes)
    }

    @Test
    fun badDatesAreLeftOut() {
        assertNull(HealthWrite.at("not a date", "07:00"))
        assertEquals(LocalDateTime.of(2026, 10, 20, 12, 0), HealthWrite.at("2026-10-20", "nonsense"))
    }
}
