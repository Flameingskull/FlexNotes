package com.fitlens.companion.data

/**
 * Training blocks (#218): a workout's plan over the weeks, as named blocks in order (say, Build 3 weeks, Peak 2 weeks,
 * Deload 1 week), from a start date and optionally repeating. A [TrainingBlock.deload] block is a lighter one. The
 * calendar arithmetic is the medication cycle engine's (`MedSchedules.phaseOn`), so blocks count days, weeks and an
 * ongoing last block exactly as schedule phases do. Stored on the `routine` row (`blocks`, `block_start`,
 * `block_repeat`, v29). A block can also change the planned sets of a workout day logged while it runs (#235): its
 * weights by [TrainingBlock.weightPct] percent and its sets per exercise by [TrainingBlock.setChange]. Both are 0 (off)
 * by default, and they're kept in the same `blocks` text, so they need no database change.
 */
data class TrainingBlock(
    val name: String,
    val length: Int,
    val unit: PhaseUnit,
    val deload: Boolean = false,
    /** Percent added to each planned weight while the block runs (-10 lightens by a tenth); 0 leaves weights as they are. */
    val weightPct: Int = 0,
    /** Sets added to (or, when negative, taken from) each exercise's planned sets; 0 leaves them as they are. */
    val setChange: Int = 0
) {
    /** Whether logging a workout day in this block changes its planned sets (#235). */
    val adjusts: Boolean get() = weightPct != 0 || setChange != 0
}

/** A workout's blocks from [start] (an ISO date). [repeats] starts the first block again after the last. */
data class TrainingPlan(val blocks: List<TrainingBlock>, val start: String, val repeats: Boolean = false) {
    /** Whether the last block is ongoing: the plan then never ends or repeats, as with an ongoing phase (#228). */
    val ongoing: Boolean get() = blocks.lastOrNull()?.unit == PhaseUnit.ONGOING
}

/** Where a plan is on a date: the block, its 1-based [index] of [count], the day in it and, when it has an end, its length. */
data class BlockState(
    val index: Int,
    val count: Int,
    val block: TrainingBlock,
    val day: Long,
    val days: Long?,
    /** The block's last day (ISO), or null when it's ongoing. */
    val lastDay: String?,
    /** Which time round the plan is (1 for the first) when it repeats. */
    val cycle: Int,
    /** The block that follows, or null after the last one of a plan that doesn't repeat. */
    val next: TrainingBlock?,
    /** The block's first day (ISO), or "" when unknown. */
    val firstDay: String = ""
) {
    /** The week of the block the day falls in, 1-based. */
    val week: Long get() = (day - 1) / 7 + 1

    /** How many weeks the block runs, rounded up, or null when it's ongoing. */
    val weeks: Long? get() = days?.let { (it + 6) / 7 }
}

object TrainingBlocks {
    /** The longest name a block keeps. */
    const val MAX_NAME = 40

    /** The units a block can be counted in; ongoing only for the last block. */
    val UNITS = listOf(PhaseUnit.WEEKS, PhaseUnit.DAYS, PhaseUnit.ONGOING)

    /** The range a block's weight change is kept in, in percent. */
    val WEIGHT_PCT = -90..100

    /** The range a block's change to the sets per exercise is kept in. */
    val SET_CHANGE = -10..10

    /**
     * A plan's blocks as text, one per line: `length|unit|deload|name`. A block that changes the planned sets (#235)
     * writes its deload flag as `deload,weightPct,setChange`, so blocks saved before it read back unchanged. Read back
     * by [decode].
     */
    fun encode(blocks: List<TrainingBlock>): String? =
        blocks.takeIf { it.isNotEmpty() }?.joinToString("\n") { b ->
            val flag = if (b.deload) 1 else 0
            val flags = if (b.adjusts) "$flag,${b.weightPct},${b.setChange}" else "$flag"
            "${b.length}|${b.unit.code}|$flags|${cleanName(b.name)}"
        }

    /** The blocks stored by [encode]; unreadable lines are skipped, so a damaged value never stops the workout loading. */
    fun decode(text: String?): List<TrainingBlock> =
        text.orEmpty().lines().mapNotNull { line ->
            val parts = line.split('|', limit = 4)
            if (parts.size < 4) return@mapNotNull null
            val length = parts[0].toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            val flags = parts[2].split(',')
            TrainingBlock(
                parts[3], length, PhaseUnit.of(parts[1]), flags[0] == "1",
                weightPct = flags.getOrNull(1)?.toIntOrNull() ?: 0,
                setChange = flags.getOrNull(2)?.toIntOrNull() ?: 0
            )
        }.let(::normalised)

    /** The plan a workout row holds, or null when it has none (no blocks or no readable start). */
    fun plan(blocks: String?, start: String?, repeats: Boolean): TrainingPlan? {
        val list = decode(blocks)
        val from = start?.takeIf { Dates.parse(it) != null } ?: return null
        return if (list.isEmpty()) null else TrainingPlan(list, from, repeats && list.lastOrNull()?.unit != PhaseUnit.ONGOING)
    }

    /**
     * The blocks as the rules want them: a length of at least one, names on one line and no longer than [MAX_NAME],
     * and only the last block ongoing (an earlier ongoing one becomes weeks, so it doesn't hide the blocks after it).
     */
    fun normalised(blocks: List<TrainingBlock>): List<TrainingBlock> =
        blocks.mapIndexed { i, b ->
            b.copy(
                name = cleanName(b.name),
                length = b.length.coerceIn(1, 999),
                weightPct = b.weightPct.coerceIn(WEIGHT_PCT),
                setChange = b.setChange.coerceIn(SET_CHANGE),
                unit = if (b.unit == PhaseUnit.ONGOING && i != blocks.lastIndex) PhaseUnit.WEEKS else b.unit
            )
        }

    private fun cleanName(name: String) = name.replace(Regex("\\s+"), " ").trim().take(MAX_NAME)

    /** The plan's state on [date] (ISO), or null before it starts and after the last block of a plan that doesn't repeat. */
    fun on(plan: TrainingPlan, date: String): BlockState? {
        val blocks = normalised(plan.blocks)
        if (blocks.isEmpty()) return null
        // Each block as a schedule phase with its own id, so equal blocks stay apart.
        val schedule = MedSchedule(
            id = 0L,
            name = "",
            repeats = plan.repeats && blocks.last().unit != PhaseUnit.ONGOING,
            phases = blocks.mapIndexed { i, b -> MedPhase(i + 1L, !b.deload, b.length, b.unit) }
        )
        val state = MedSchedules.phaseOn(schedule, MedActivation(0L, 0L, plan.start), date) ?: return null
        val i = state.index
        val next = when {
            i < blocks.lastIndex -> blocks[i + 1]
            schedule.repeats -> blocks.first()
            else -> null
        }
        return BlockState(i + 1, blocks.size, blocks[i], state.day, state.days, state.lastDay, state.cycle, next,
            Dates.parse(date)?.minusDays(state.day - 1)?.format(Dates.ISO).orEmpty())
    }

    /** Every workout with a plan running on [date], with where it is: what the day log shows. */
    fun runningOn(routines: List<Routine>, date: String): List<Pair<Routine, BlockState>> =
        routines.mapNotNull { r -> r.plan?.let { p -> on(p, date)?.let { r to it } } }

    /**
     * One exercise's planned [sets] as [block] changes them (#235): each weight above 0 moved by the block's percent and
     * passed through [round] (to the exercise's plate step, in kg), never rounded down to 0; then sets taken from the
     * end, always keeping one, or the last set repeated. An exercise with no planned sets stays without, and a time or
     * distance exercise (no weight) takes the set change only.
     */
    fun adjust(sets: List<PlannedSet>, block: TrainingBlock, round: (Double) -> Double): List<PlannedSet> {
        if (sets.isEmpty() || !block.adjusts) return sets
        val pct = block.weightPct.coerceIn(WEIGHT_PCT)
        val weighed = if (pct == 0) sets else sets.map { s ->
            if (s.weightKg <= 0.0) s else {
                val exact = s.weightKg * (100 + pct) / 100.0
                val rounded = round(exact)
                s.copy(weightKg = if (rounded > 0.0) rounded else exact)
            }
        }
        val change = block.setChange.coerceIn(SET_CHANGE)
        return when {
            change < 0 -> weighed.take((weighed.size + change).coerceAtLeast(1))
            change > 0 -> weighed + List(change) { weighed.last() }
            else -> weighed
        }
    }

    /** Rounds [value] to the nearest multiple of [step] (any step above 0), as a weight on the bar would be. */
    fun roundTo(value: Double, step: Double): Double =
        if (step <= 0.0) value else Math.round(value / step) * step

    /** A plan to start from: three weeks of building, then a deload week, repeating, from [start]. */
    fun starter(start: String, build: String, deload: String): TrainingPlan =
        TrainingPlan(listOf(TrainingBlock(build, 3, PhaseUnit.WEEKS), TrainingBlock(deload, 1, PhaseUnit.WEEKS, deload = true)), start, repeats = true)
}
