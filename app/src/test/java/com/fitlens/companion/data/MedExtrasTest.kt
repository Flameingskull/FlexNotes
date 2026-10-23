package com.fitlens.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Supply on hand and its run-out date (#209), injection site rotation (#210) and symptom ratings (#207). */
class MedExtrasTest {

    private fun value(id: Long, date: String, time: String, amount: Double, site: String? = null, name: String = "Creatine") =
        MRecord(id, name, "g", date, time, amount, null, "manual", site = site)

    private val dose = MedDose(1, "Creatine", 5.0, "g", "08:00")

    /** 5 g every day, or two weeks on and one off when [cycling]. */
    private fun plan(cycling: Boolean = false, skipped: Map<Long, Set<String>> = emptyMap()): MedPlan {
        val phases = if (cycling) {
            listOf(MedPhase(10, true, 2, PhaseUnit.WEEKS, listOf(dose)), MedPhase(11, false, 1, PhaseUnit.WEEKS))
        } else listOf(MedPhase(10, true, 52, PhaseUnit.WEEKS, listOf(dose)))
        return MedPlan(
            listOf(MedSchedule(1, "Daily", repeats = true, phases = phases)),
            listOf(MedActivation(1, 1, "2026-10-01")),
            skipped
        )
    }

    @Test
    fun theSupplyCountsDownFromWhenItWasCounted() {
        val info = MedInfo("Creatine", supply = 100.0, supplyFrom = "2026-10-10 12:00:00")
        val records = listOf(
            value(1, "2026-10-10", "08:00:00", 5.0), // before the count: already off the supply
            value(2, "2026-10-10", "20:00:00", 5.0),
            value(3, "2026-10-11", "08:00", 5.0),
            value(4, "2026-10-11", "08:00:00", 5.0, name = "Other")
        )
        assertEquals(90.0, Supply.left(info, records)!!, 1e-9)
        assertNull(Supply.left(MedInfo("Creatine"), records))
    }

    @Test
    fun itRunsOutOnTheFirstDayItCantCover() {
        // 22 g left on 23 October, today's 08:00 dose already taken: 24, 25, 26 and 27 October use 20 g, 28 October can't.
        val info = MedInfo("Creatine", supply = 27.0, supplyFrom = "2026-10-22 23:00:00", warnDays = 7)
        val taken = MRecord(5, "Creatine", "g", "2026-10-23", "08:05:00", 5.0, null, "manual", doseId = 1, due = "2026-10-23 08:00")
        val s = Supply.of(info, plan(), listOf(taken), today = "2026-10-23")!!
        assertEquals(22.0, s.left, 1e-9)
        assertEquals("2026-10-28", s.runsOut)
        assertTrue(s.low)
        val far = Supply.of(info.copy(warnDays = 3), plan(), listOf(taken), today = "2026-10-23")!!
        assertFalse(far.low)
    }

    @Test
    fun offPhasesAndSkipsDontUseTheSupply() {
        // Two weeks on from 1 October, one off from 15 October: 10 g on the 13th lasts the 13th and 14th, then the off
        // week, and runs out on the 22nd when the next cycle starts with 0 g.
        val info = MedInfo("Creatine", supply = 10.0, supplyFrom = "2026-10-12 23:00:00")
        assertEquals("2026-10-22", Supply.of(info, plan(cycling = true), emptyList(), today = "2026-10-13")!!.runsOut)
        // Today's dose skipped: it doesn't come off, so the supply lasts a dose longer, until the 23rd.
        val skipped = plan(cycling = true, skipped = mapOf(1L to setOf("2026-10-13 08:00")))
        assertEquals("2026-10-23", Supply.of(info, skipped, emptyList(), today = "2026-10-13")!!.runsOut)
    }

    @Test
    fun withNothingScheduledTheRecentAverageIsUsed() {
        val info = MedInfo("Creatine", supply = 70.0, supplyFrom = "2026-09-01 00:00:00")
        // 28 g logged over the last 28 days: 1 g a day, so 70 g less 28 g = 42 g lasts 42 days.
        val records = (1..28).map { value(it.toLong(), "2026-09-${(it + 1).toString().padStart(2, '0')}", "08:00:00", 1.0) }
        val s = Supply.of(info, MedPlan(), records, today = "2026-09-29")!!
        assertEquals(42.0, s.left, 1e-9)
        assertEquals("2026-11-10", s.runsOut)
        assertNull(Supply.of(info, MedPlan(), emptyList(), today = "2026-09-29")!!.runsOut)
    }

    @Test
    fun expiryIsFlaggedWithinTheWarningDays() {
        val info = MedInfo("Creatine", supply = 500.0, supplyFrom = "2026-10-01 00:00:00", warnDays = 7, expires = "2026-10-28")
        val soon = Supply.of(info, MedPlan(), emptyList(), today = "2026-10-23")!!
        assertTrue(soon.expiresSoon)
        assertFalse(soon.expired)
        val past = Supply.of(info, MedPlan(), emptyList(), today = "2026-10-29")!!
        assertTrue(past.expired)
        assertTrue(past.needsAttention)
    }

    @Test
    fun theNextSiteIsTheOneUsedLongestAgo() {
        assertEquals(InjectionSite.ABDOMEN_UPPER_LEFT, InjectionSites.suggest(emptyList(), "Creatine"))
        val used = InjectionSite.entries.mapIndexed { i, s -> value(i.toLong(), "2026-10-${(10 + i)}", "08:00:00", 1.0, s.key) }
        // Every site used once, in order: the first is the oldest.
        assertEquals(InjectionSite.ABDOMEN_UPPER_LEFT, InjectionSites.suggest(used, "Creatine"))
        val again = used + value(99, "2026-10-25", "08:00:00", 1.0, InjectionSite.ABDOMEN_UPPER_LEFT.key)
        assertEquals(InjectionSite.ABDOMEN_UPPER_RIGHT, InjectionSites.suggest(again, "Creatine"))
        // Another medication's sites don't count.
        assertEquals(InjectionSite.ABDOMEN_UPPER_LEFT, InjectionSites.suggest(again, "Other"))
        assertEquals(99L, InjectionSites.recent(again, "Creatine").first().id)
        assertNull(InjectionSite.of("elbow"))
    }

    @Test
    fun ratingsFollowTheSymptomList() {
        val plan = MedPlan(
            symptoms = listOf(Symptom(2, "Nausea", 0), Symptom(1, "Headache", 1)),
            ratings = listOf(
                SymptomRating(1, 1, "2026-10-02", 4), SymptomRating(2, 2, "2026-10-02", 2), SymptomRating(3, 2, "2026-10-01", 6)
            )
        )
        assertEquals(listOf("Nausea", "Headache"), Symptoms.ratingsOn(plan, "2026-10-02").map { it.first.name })
        assertEquals(listOf(6, 2), Symptoms.series(plan, 2).map { it.rating })
        assertTrue(Symptoms.ratingsOn(plan, "2026-10-03").isEmpty())
    }
}
