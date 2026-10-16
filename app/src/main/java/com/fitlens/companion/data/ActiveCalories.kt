package com.fitlens.companion.data

/**
 * Active calories from Health Connect (#188): what a watch or phone counted as burned in movement, above resting
 * energy, kept a day at a time as the **Active calories** body value. Chosen in Settings › Health Connect
 * (`PortableSettings.hcActive`), they're the daily activity in the energy estimate, ahead of steps and the activity
 * level, once [MIN_DAYS] days have them.
 *
 * The rule against counting twice: a day's activity is the Sitting level plus that day's active calories less what
 * its logged exercise already counts, never below none. Logged workouts keep their own estimate, so a day burns the
 * Sitting level plus the larger of its active calories and its logged exercise. Steps aren't added beside them, since
 * the active calories already count walking (and the walks and runs of #181).
 */
object ActiveCalories {
    const val NAME = HealthConnect.Names.ACTIVE

    /** Days with active calories needed before their average replaces steps and the activity level. */
    const val MIN_DAYS = Steps.MIN_DAYS

    /** Every Active calories value, whatever the measurement's capitals. */
    private fun records(snap: Snapshot): List<MRecord> =
        snap.recordsByName.entries.firstOrNull { it.key.equals(NAME, ignoreCase = true) }?.value.orEmpty()

    /** The active calories recorded on [date] (Health Connect's when there are any), or null when there are none. */
    fun on(snap: Snapshot, date: String): Double? =
        Sources.dayValues(records(snap).filter { it.date.take(10) == date }, hcPrimary = true)
            .takeIf { it.isNotEmpty() }?.sumOf { it.value }

    /**
     * The average active calories a day over the [Energy.AVERAGE_DAYS] ending on [date], from the days that have
     * them, or null with fewer than [MIN_DAYS]. Today is left out while it's still being counted. [covered] gives a
     * day's calories its logged exercise already counts, taken off that day but never below none; the average of
     * what's taken off is [Steps.Average.leftOutPerDay].
     */
    fun average(snap: Snapshot, date: String, today: String = Dates.today(), covered: (String) -> Double = { 0.0 }): Steps.Average? {
        val start = Energy.averageStart(date)
        val byDay = records(snap)
            .filter { val d = it.date.take(10); d in start..date && d != today }
            .groupBy { it.date.take(10) }
            .mapValues { e -> Sources.dayValues(e.value, hcPrimary = true).sumOf { it.value } }
        if (byDay.size < MIN_DAYS) return null
        val kept = byDay.mapValues { (d, kcal) -> maxOf(0.0, kcal - covered(d)) }
        val total = byDay.values.sum()
        val keptTotal = kept.values.sum()
        return Steps.Average(keptTotal / byDay.size, byDay.size, (total - keptTotal) / byDay.size)
    }
}
