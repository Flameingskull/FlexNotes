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
    /** [SESSION] is the sum of rating x minutes over a day's Session sets (#242); unrated sessions don't count. */
    enum class Measure { VOLUME, SETS, SESSION }

    const val ACUTE_DAYS = 7
    const val CHRONIC_DAYS = 28
    const val SPIKE = 1.5
    /** Under this the load is dropping off: not a risk on its own, but fitness built up is being lost. */
    const val LOW = 0.8
    const val HIGH_MONOTONY = 2.0

    /** The top of the good band (#242): from [LOW] to here is steady; above it, up to [SPIKE], is caution. */
    const val GOOD_MAX = 1.3
    /** A day whose load is more than this fraction above the previous training day's is flagged (#242). */
    const val JUMP = 0.5

    /** Where a load ratio sits (#242): under [LOW], up to [GOOD_MAX], up to [SPIKE], or above it. */
    enum class Band { LOW, GOOD, CAUTION, HIGH }

    fun band(ratio: Double): Band = when {
        ratio < LOW -> Band.LOW
        ratio <= GOOD_MAX -> Band.GOOD
        ratio <= SPIKE -> Band.CAUTION
        else -> Band.HIGH
    }

    /**
     * Whether an exercise is a Session. The one place to extend when another exercise type counts as a session
     * (Rounds, #246).
     */
    fun sessionTest(snap: Snapshot): (Long) -> Boolean = { id -> isSessionType(snap.exercises[id]?.type ?: -1) }

    fun isSessionType(type: Int): Boolean = ExerciseTypes.isSession(type)

    /** The measure the day log's card uses: Session load once any session is logged, otherwise volume. */
    fun preferredMeasure(snap: Snapshot): Measure {
        val isSession = sessionTest(snap)
        return if (snap.statSets.any { isSession(it.exerciseId) }) Measure.SESSION else Measure.VOLUME
    }

    /** How many sessions from epoch day [from] to [to] have no rating, so they aren't counted. */
    fun unrated(sets: List<SetRow>, isSession: (Long) -> Boolean, from: Long, to: Long): Int =
        sets.count { s ->
            val d = Dates.epochDay(s.date.take(10))
            isSession(s.exerciseId) && s.rpe == null && d in from..to
        }

    /**
     * The rise in load on [day] over the previous training day (0.6 is 60% more), when it's above [JUMP]; null when
     * [day] has no load, there's no earlier training day, or the rise is within [JUMP].
     */
    fun jumpOn(load: Map<Long, Double>, day: Long): Double? {
        val today = load[day]?.takeIf { it > 0 } ?: return null
        val before = load.filterKeys { it < day }.maxByOrNull { it.key }?.value?.takeIf { it > 0 } ?: return null
        val rise = today / before - 1.0
        return if (rise > JUMP) rise else null
    }

    /** One week of internal and external load (#242): the 7 days ending [endDay]. */
    data class Week(val endDay: Long, val sessionLoad: Double, val hardMinutes: Double, val volumeKg: Double)

    /** The [weeks] 7-day blocks ending at [today], oldest first. */
    fun weekly(snap: Snapshot, today: Long, weeks: Int): List<Week> {
        val isSession = sessionTest(snap)
        val sets = snap.statSets
        val sessionLoad = daily(sets, Measure.SESSION, isSession)
        val volume = daily(sets, Measure.VOLUME, isSession)
        val hardByDay = sets.filter { isSession(it.exerciseId) }
            .groupBy { Dates.epochDay(it.date.take(10)) }
            .mapValues { e -> e.value.sumOf { SessionLoad.hardMinutes(it.liveSec, it.durationSec) ?: 0.0 } }
        return (weeks - 1 downTo 0).map { k ->
            val end = today - 7L * k
            val days = end - 6..end
            Week(end, days.sumOf { sessionLoad[it] ?: 0.0 }, days.sumOf { hardByDay[it] ?: 0.0 }, days.sumOf { volume[it] ?: 0.0 })
        }
    }

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
    fun loadOf(s: SetRow, measure: Measure, isSession: (Long) -> Boolean = { false }): Double = when (measure) {
        Measure.VOLUME -> Analysis.volumeKg(s)
        Measure.SETS -> 1.0
        Measure.SESSION -> if (isSession(s.exerciseId)) SessionLoad.of(s.rpe, s.durationSec) ?: 0.0 else 0.0
    }

    /** Each training day's load, by epoch day. [isSession] says which exercises are Sessions, for [Measure.SESSION]. */
    fun daily(sets: List<SetRow>, measure: Measure, isSession: (Long) -> Boolean = { false }): Map<Long, Double> =
        sets.groupBy { Dates.epochDay(it.date.take(10)) }
            .mapValues { e -> e.value.sumOf { loadOf(it, measure, isSession) } }.filterValues { it > 0 }

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
        series(daily(snap.statSets, measure, sessionTest(snap)), today - days + 1, today)
}
