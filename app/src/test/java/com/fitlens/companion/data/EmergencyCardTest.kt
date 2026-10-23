package com.fitlens.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The emergency card's current medications (#215): scheduled doses ahead, recent ones logged by hand, nothing older. */
class EmergencyCardTest {

    private val morning = MedDose(1, "Metformin", 500.0, "mg", "08:00")
    private val evening = MedDose(2, "Metformin", 500.0, "mg", "21:00")
    private val plan = MedPlan(
        listOf(MedSchedule(1, "Daily", repeats = true, phases = listOf(MedPhase(10, true, 52, PhaseUnit.WEEKS, listOf(evening, morning))))),
        listOf(MedActivation(1, 1, "2026-10-01"))
    )

    private fun value(id: Long, name: String, date: String, amount: Double, unit: String = "g") =
        MRecord(id, name, unit, date, "09:00:00", amount, null, "manual")

    @Test
    fun scheduledDosesAreListedOnceByTime() {
        val lines = EmergencyCard.current(plan, emptyList(), listOf("Metformin"), today = "2026-10-23")
        val m = lines.single()
        assertTrue(m.scheduled)
        assertEquals(listOf("500 mg · 08:00", "500 mg · 21:00"), m.doses.map { it.text })
    }

    @Test
    fun aMedicationLoggedRecentlyByHandIsCurrent() {
        val records = listOf(
            value(1, "Ibuprofen", "2026-10-10", 200.0, "mg"),
            value(2, "Ibuprofen", "2026-10-20", 400.0, "mg"),
            value(3, "Creatine", "2026-08-01", 5.0)
        )
        val lines = EmergencyCard.current(plan, records, listOf("Metformin", "Ibuprofen", "Creatine"), today = "2026-10-23")
        // Creatine was last logged over 30 days ago, so it isn't current.
        assertEquals(listOf("Metformin", "Ibuprofen"), lines.map { it.medication })
        assertEquals(400.0, lines[1].last!!.value, 0.0)
        assertNull(lines[0].last)
    }

    @Test
    fun anEndedScheduleNoLongerCounts() {
        val ended = MedPlan(plan.schedules, listOf(MedActivation(1, 1, "2026-10-01", endedOn = "2026-10-05")))
        assertTrue(EmergencyCard.current(ended, emptyList(), listOf("Metformin"), today = "2026-10-23").isEmpty())
    }

    @Test
    fun theDetailsComeFromTheSettings() {
        val d = EmergencyDetails.of(PortableSettings(emergencyAllergies = "Penicillin", emergencyContact = "Sam 0400 000 000"))
        assertEquals(EmergencyDetails(allergies = "Penicillin", contact = "Sam 0400 000 000"), d)
    }
}
