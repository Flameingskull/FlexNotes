package com.fitlens.companion.data

import kotlin.math.sqrt

/**
 * Training load (#217): the acute:chronic workload ratio and monotony, worked out from the logged sets.
 *
 * A day's load is its volume (kg lifted, weight × reps) or its number of sets ([Measure]); a day without training is
 * 0. The **acute** load is the average day over the last [ACUTE_DAYS] (the day included), the **chronic** load the
 * average over the last [CHRONIC_DAYS], and the **ratio** acute ÷ chronic: about 1 is steady, above [SPIKE] is a
 * spike that's linked to a higher injury risk. The ratio waits until training has been logged for [CHRONIC_DAYS], so
 * the first weeks don't all read as spikes. **Monotony** is the last week's average day ÷ its standard deviation
 * (Foster): above [HIGH_MONOTONY] the days are too alike; **strain** is the week's load × monotony.
 * Warm-ups count as the stat sets say (#43). Plain Kotlin, tested in `TrainingLoadTest`.
 */
object TrainingLoad {
    enum class Measure { VOLUME, SETS }

    const val ACUTE_DAYS = 7
    const val CHRONIC_DAYS = 28
    const val SPIKE = 1.5
    /** Under this the load is dropping off: not a risk on its own, but fitness built up is being lost. */
    const val LOW = 0.8
    const val HIGH_MONOTONY = 2.0

    /** One day's figures. [ratio] and [monotony] are null until there's enough to work them out. */
    data class Day(
        val day: Long,
        val load: Double,
        val acute: Double,
        val chronic: Double,
        val ratio: Double?,
        val monotony: Double?,
        val strain: Double?
    ) {
        val spike: Boolean get() = (ratio ?: 0.0) > SPIKE
    }

    /** One set's load. */
    fun loadOf(s: SetRow, measure: Measure): Double = when (measure) {
        Measure.VOLUME -> Analysis.volumeKg(s)
        Measure.SETS -> 1.0
    }

    /** Each training day's load, by epoch day. */
    fun daily(sets: List<SetRow>, measure: Measure): Map<Long, Double> =
        sets.groupBy { Dates.epochDay(it.date.take(10)) }.mapValues { e -> e.value.sumOf { loadOf(it, measure) } }.filterValues { it > 0 }

    /** The figures for every day from [from] to [to], both included, from each day's [load]. */
    fun series(load: Map<Long, Double>, from: Long, to: Long): List<Day> {
        if (load.isEmpty() || to < from) return emptyList()
        val first = load.keys.min()
        return (from..to).map { d ->
            val week = (d - ACUTE_DAYS + 1..d).map { load[it] ?: 0.0 }
            val acute = week.average()
            val chronic = (d - CHRONIC_DAYS + 1..d).sumOf { load[it] ?: 0.0 } / CHRONIC_DAYS
            val enough = d - first + 1 >= CHRONIC_DAYS
            val ratio = if (enough && chronic > 0) acute / chronic else null
            val sd = sqrt(week.sumOf { (it - acute) * (it - acute) } / week.size)
            val monotony = if (acute > 0 && sd > 1e-9) acute / sd else null
            Day(d, load[d] ?: 0.0, acute, chronic, ratio, monotony, monotony?.let { it * week.sum() })
        }
    }

    /** The figures for the [days] up to [today] from the snapshot's stat sets. */
    fun of(snap: Snapshot, measure: Measure, today: Long, days: Long): List<Day> =
        series(daily(snap.statSets, measure), today - days + 1, today)
}
