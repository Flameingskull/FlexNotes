package com.fitlens.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Training blocks (#218): blocks and deloads counted by the medication cycle engine, and how they're stored. */
class TrainingBlocksTest {

    private fun build(weeks: Int = 3) = TrainingBlock("Build", weeks, PhaseUnit.WEEKS)
    private fun deload() = TrainingBlock("Deload", 1, PhaseUnit.WEEKS, deload = true)

    @Test
    fun threeWeeksThenADeloadRepeats() {
        val plan = TrainingPlan(listOf(build(), deload()), "2026-11-02", repeats = true)
        assertNull(TrainingBlocks.on(plan, "2026-11-01"))
        val first = TrainingBlocks.on(plan, "2026-11-02")!!
        assertEquals(1, first.index)
        assertEquals(2, first.count)
        assertEquals(1L, first.day)
        assertEquals(1L, first.week)
        assertEquals(3L, first.weeks)
        assertEquals("2026-11-22", first.lastDay)
        assertEquals("Deload", first.next?.name)
        // Day 15 is the third week of the build.
        assertEquals(3L, TrainingBlocks.on(plan, "2026-11-16")!!.week)
        val deloadWeek = TrainingBlocks.on(plan, "2026-11-23")!!
        assertTrue(deloadWeek.block.deload)
        assertEquals(2, deloadWeek.index)
        assertEquals("Build", deloadWeek.next?.name)
        // Four weeks on, round two starts with the build again.
        val again = TrainingBlocks.on(plan, "2026-11-30")!!
        assertEquals(1, again.index)
        assertEquals(2, again.cycle)
    }

    @Test
    fun aPlanThatDoesntRepeatEndsAfterItsLastBlock() {
        val plan = TrainingPlan(listOf(build(2), deload()), "2026-11-02")
        assertNull(TrainingBlocks.on(plan, "2026-11-22")?.next)
        assertTrue(TrainingBlocks.on(plan, "2026-11-22")!!.block.deload)
        assertNull(TrainingBlocks.on(plan, "2026-11-23"))
    }

    @Test
    fun anOngoingLastBlockNeverEnds() {
        val plan = TrainingPlan(listOf(build(), TrainingBlock("Maintain", 1, PhaseUnit.ONGOING)), "2026-11-02", repeats = true)
        assertTrue(plan.ongoing)
        val later = TrainingBlocks.on(plan, "2027-06-01")!!
        assertEquals("Maintain", later.block.name)
        assertNull(later.days)
        assertNull(later.weeks)
        assertNull(later.lastDay)
        assertNull(later.next)
        // The block's first day, so an ongoing block reads "since Mon 23 Nov" rather than "week 30".
        assertEquals(Dates.epochDay("2027-06-01"), Dates.epochDay(later.firstDay) + later.day - 1)
    }

    @Test
    fun onlyTheLastBlockCanBeOngoing() {
        val blocks = TrainingBlocks.normalised(listOf(TrainingBlock("  Base \n block ", 0, PhaseUnit.ONGOING), deload()))
        assertEquals(PhaseUnit.WEEKS, blocks[0].unit)
        assertEquals(1, blocks[0].length)
        assertEquals("Base block", blocks[0].name)
    }

    @Test
    fun equalBlocksStayApart() {
        // Two identical blocks are still the first and the second.
        val plan = TrainingPlan(listOf(build(1), build(1)), "2026-11-02")
        assertEquals(1, TrainingBlocks.on(plan, "2026-11-08")!!.index)
        assertEquals(2, TrainingBlocks.on(plan, "2026-11-09")!!.index)
    }

    @Test
    fun blocksRoundTripThroughTheirStoredText() {
        val blocks = listOf(TrainingBlock("Peak | heavy", 10, PhaseUnit.DAYS), deload())
        val text = TrainingBlocks.encode(blocks)
        assertEquals(blocks, TrainingBlocks.decode(text))
        assertNull(TrainingBlocks.encode(emptyList()))
        // A damaged line is skipped rather than losing the rest.
        assertEquals(listOf(deload()), TrainingBlocks.decode("junk\n1|w|1|Deload"))
        val plan = TrainingBlocks.plan(text, "2026-11-02", repeats = true)!!
        assertTrue(plan.repeats)
        assertNull(TrainingBlocks.plan(text, null, repeats = false))
        assertNull(TrainingBlocks.plan(null, "2026-11-02", repeats = false))
        assertFalse(TrainingBlocks.plan("1|o|0|Always", "2026-11-02", repeats = true)!!.repeats)
    }

    @Test
    fun aBlockThatChangesTheSetsRoundTripsAndOldTextReadsUnchanged() {
        val lighter = deload().copy(weightPct = -10, setChange = -1)
        val blocks = listOf(build(), lighter)
        val text = TrainingBlocks.encode(blocks)!!
        assertEquals(blocks, TrainingBlocks.decode(text))
        assertTrue(text.contains("|1,-10,-1|"))
        // Blocks saved before #235 change nothing.
        assertFalse(TrainingBlocks.decode("1|w|1|Deload").single().adjusts)
        // Out-of-range values are kept within bounds.
        val wild = TrainingBlocks.decode("1|w|1,-500,40|Deload").single()
        assertEquals(-90, wild.weightPct)
        assertEquals(10, wild.setChange)
    }

    @Test
    fun aDeloadLightensWeightsToThePlateStepAndDropsASet() {
        val sets = listOf(PlannedSet(100.0, 5), PlannedSet(100.0, 5), PlannedSet(100.0, 5))
        val block = deload().copy(weightPct = -10, setChange = -1)
        val out = TrainingBlocks.adjust(sets, block) { TrainingBlocks.roundTo(it, 2.5) }
        assertEquals(listOf(PlannedSet(90.0, 5), PlannedSet(90.0, 5)), out)
        // 87 kg -15% is 73.95, on the 2.5 kg step 75.
        assertEquals(75.0, TrainingBlocks.adjust(listOf(PlannedSet(87.0, 5)), deload().copy(weightPct = -15)) { TrainingBlocks.roundTo(it, 2.5) }.single().weightKg, 1e-9)
        // Never fewer than one set, and a light weight never rounds to nothing.
        val one = TrainingBlocks.adjust(listOf(PlannedSet(2.0, 10)), deload().copy(weightPct = -50, setChange = -5)) { TrainingBlocks.roundTo(it, 2.5) }
        assertEquals(1, one.size)
        assertEquals(1.0, one.single().weightKg, 1e-9)
    }

    @Test
    fun aBlockCanAddSetsAndLeavesTimedSetsAndEmptyPlansAlone() {
        val run = listOf(PlannedSet(durationSec = 600))
        val more = TrainingBlocks.adjust(run, build().copy(weightPct = 5, setChange = 2)) { it }
        assertEquals(List(3) { PlannedSet(durationSec = 600) }, more)
        assertTrue(TrainingBlocks.adjust(emptyList(), deload().copy(setChange = 2)) { it }.isEmpty())
        assertEquals(run, TrainingBlocks.adjust(run, deload()) { it })
    }

    @Test
    fun runningOnListsOnlyWorkoutsWithARunningPlan() {
        val withPlan = Routine(1, "Push Pull Legs", plan = TrainingPlan(listOf(build()), "2026-11-02"))
        val without = Routine(2, "Stretching")
        val running = TrainingBlocks.runningOn(listOf(withPlan, without), "2026-11-03")
        assertEquals(listOf(1L), running.map { it.first.id })
        assertTrue(TrainingBlocks.runningOn(listOf(withPlan, without), "2026-10-01").isEmpty())
    }
}
