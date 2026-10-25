package com.fitlens.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Medications (#199): a measurement marked as a medication is shown only on the Medications screen. The body area
 * never holds it, so no body view, graph, estimate, export or Health Connect write sees it, and each value is in
 * exactly one half.
 */
class MedicationsTest {

    private fun def(name: String, unit: String, medication: Boolean = false, custom: Boolean = true) =
        MeasurementDef(name, unit, 0, 0, 0.0, true, custom = custom, medication = medication)

    private fun rec(id: Long, name: String, unit: String, value: Double, date: String = "2026-10-20") =
        MRecord(id, name, unit, date, "08:00:00", value, null, "manual")

    private val defs = listOf(
        def("Bodyweight", "kg", custom = false),
        def("Waist", "cm"),
        def("Creatine", "g", medication = true),
        def("Metformin", "mg", medication = true)
    )
    private val records = listOf(
        rec(1, "Bodyweight", "kg", 80.0),
        rec(2, "Waist", "cm", 85.0),
        rec(3, "Creatine", "g", 5.0),
        rec(4, "Metformin", "mg", 500.0, "2026-10-21"),
        rec(5, "Metformin", "mg", 500.0, "2026-10-22")
    )

    private fun snapshot(body: BodyPart) = Snapshot(
        LibraryPart(emptyMap(), emptyMap(), emptyList(), emptyList(), emptyMap()),
        SetPart(emptyList(), false),
        NotesPart(emptyMap(), emptyMap(), emptyMap()),
        body,
        PhotoPart(emptyList()),
        "kg",
        File("photos")
    )

    @Test
    fun theBodyAreaLeavesMedicationsOut() {
        val snap = snapshot(BodyPart(defs, records, "kg"))
        assertEquals(listOf("Bodyweight", "Waist"), snap.allMeasurements.map { it.name })
        assertEquals(listOf("Bodyweight", "Waist"), snap.usedMeasurements.map { it.name })
        assertEquals(setOf(1L, 2L), snap.records.map { it.id }.toSet())
        assertFalse(snap.recordsByName.containsKey("Creatine"))
        // A day with only a medication isn't a body day: the day log, calendar and timeline don't show it.
        assertFalse(snap.recordsByDate.containsKey("2026-10-22"))
        assertFalse(snap.allDates.contains("2026-10-22"))
        assertFalse(snap.showsMedications)
    }

    @Test
    fun theMedicationViewHoldsOnlyMedicationsWithTheirWholeHistory() {
        val snap = snapshot(BodyPart(defs, records, "kg"))
        val meds = snap.medicationView
        assertTrue(meds.showsMedications)
        assertEquals(listOf("Creatine", "Metformin"), meds.allMeasurements.map { it.name })
        assertEquals(listOf("Creatine", "Metformin"), snap.medications.map { it.name })
        assertEquals(2, meds.recordsByName.getValue("Metformin").size)
        assertEquals(setOf(3L, 4L, 5L), meds.records.map { it.id }.toSet())
        // Built once and shared, so screens keyed on bodyKey don't recompute; a view of a view is itself.
        assertSame(meds.bodyKey, snap.medicationView.bodyKey)
        assertSame(meds, meds.medicationView)
        // Every value is counted once across both halves.
        assertEquals(records.size, snap.records.size + meds.records.size)
        assertEquals(records.size, snap.storedRecordCount)
    }

    @Test
    fun aNameFromTheOtherHalfIsRecognised() {
        val snap = snapshot(BodyPart(defs, records, "kg"))
        assertTrue(snap.inOtherHalf("creatine "))
        assertFalse(snap.inOtherHalf("Waist"))
        assertTrue(snap.medicationView.inOtherHalf("Bodyweight"))
        assertFalse(snap.medicationView.inOtherHalf("Metformin"))
        assertFalse(snap.inOtherHalf("Something new"))
        assertEquals(setOf("Waist", "Creatine", "Metformin"), snap.allCustomNames.toSet())
    }

    @Test
    fun switchingBackReturnsTheHistory() {
        val back = defs.map { if (it.name == "Creatine") it.copy(medication = false) else it }
        val snap = snapshot(BodyPart(back, records, "kg"))
        assertEquals(1, snap.recordsByName.getValue("Creatine").size)
        assertEquals(listOf("Metformin"), snap.medications.map { it.name })
    }

    @Test
    fun unitsFollowTheSplitWithoutLosingAnything() {
        val lbs = BodyPart(defs, records, "kg").withUnits("lbs", "cm")
        assertEquals(176.37, lbs.records.first { it.name == "Bodyweight" }.value, 0.01)
        assertEquals(listOf("Creatine", "Metformin"), lbs.medications.allMeasurements.map { it.name })
        assertEquals(500.0, lbs.medications.recordsByName.getValue("Metformin").first().value, 0.0)
    }

    @Test
    fun healthConnectWritesNeverSeeAMedication() {
        val body = BodyPart(defs + def("Body fat", "%", custom = false), records + rec(6, "Body fat", "%", 18.0), "kg")
        val ids = body.rawRecords.map { it.id }.toSet()
        assertFalse(ids.contains(3L))
        assertFalse(ids.contains(4L))
        assertEquals(1, HealthWrite.readings(HealthWrite.Out.WEIGHT, body.rawRecords).size)
        assertEquals(1, HealthWrite.readings(HealthWrite.Out.BODY_FAT, body.rawRecords).size)
    }

    @Test
    fun theDoseLogAndAdherenceCountOnlyScheduledDoses() {
        val dose = MedDose(7, "Metformin", 500.0, "mg", "08:00")
        val plan = MedPlan(
            listOf(MedSchedule(1, "Daily", phases = listOf(MedPhase(1, true, 1, PhaseUnit.YEARS, listOf(dose))))),
            listOf(MedActivation(1, 1, "2026-10-20")),
            skipped = mapOf(7L to setOf("2026-10-21 08:00"))
        )
        val taken = rec(9, "Metformin", "mg", 500.0, "2026-10-20").copy(doseId = 7, due = "2026-10-20 08:00", comment = "With food")
        val byHand = rec(10, "Creatine", "g", 5.0, "2026-10-21")
        val log = MedSchedules.doseLog(plan, listOf(taken, byHand), "2026-10-20", "2026-10-30", today = "2026-10-23")
        // The 20th taken, the 21st skipped, the 22nd missed, the 23rd still due; the 24th on hasn't come yet.
        assertEquals(
            listOf(MedSchedules.Outcome.TAKEN, null, MedSchedules.Outcome.SKIPPED, MedSchedules.Outcome.MISSED, MedSchedules.Outcome.DUE),
            log.map { it.outcome }
        )
        val a = MedSchedules.adherence(log).single()
        assertEquals(MedSchedules.Adherence("Metformin", 4, 1, 1, 1), a)
        assertEquals(33, a.percent)
        assertEquals(listOf("Creatine"), MedSchedules.doseLog(plan, listOf(taken, byHand), "2026-10-20", "2026-10-30", "2026-10-23", setOf("Creatine")).map { it.medication })
    }

    @Test
    fun theMedicationsCsvHoldsOnlyMedications() {
        val snap = snapshot(BodyPart(defs, records, "kg"))
        val csv = CsvExport.medications(snap, "2026-10-01", "2026-10-31", today = "2026-10-31")
        val lines = csv.trim().lines()
        assertEquals(CsvExport.MEDICATION_COLUMNS.joinToString(","), lines.first())
        assertEquals(4, lines.size)
        assertFalse(csv.contains("Bodyweight"))
        assertFalse(csv.contains("Waist"))
        assertTrue(lines.drop(1).all { it.contains(",Logged,") })
    }

    @Test
    fun theMedicationsCsvGivesAnInjectedDosesSite() {
        val sited = records + rec(99, "Metformin", "mg", 500.0, "2026-10-12").copy(site = InjectionSite.THIGH_LEFT.key)
        val csv = CsvExport.medications(snapshot(BodyPart(defs, sited, "kg")), "2026-10-01", "2026-10-31", today = "2026-10-31")
        assertEquals("Site", CsvExport.MEDICATION_COLUMNS.last())
        assertTrue(csv.lines().any { it.contains("Metformin") && it.endsWith(",Left thigh") })
    }

    @Test
    fun theMeasurementsOtherFeaturesReadCantBeMedications() {
        listOf(
            "Bodyweight", "Body Weight", "body fat", "Body Fat %", "Height", "Steps", "Active calories",
            "Resting heart rate", "Sleep", "Calories eaten", "Protein eaten", "Sleep quality", "Soreness", " ", ""
        ).forEach { assertFalse(it, Medications.canBeMedication(it)) }
        listOf("Creatine", "Metformin", "Vitamin D", "Testosterone cypionate", "Waist").forEach {
            assertTrue(it, Medications.canBeMedication(it))
        }
    }
}
