package com.fitlens.companion.data

import kotlin.math.roundToInt

/**
 * The daily readiness check-in and score (#216). A check-in is five ratings from 1 to 5 (sleep quality, soreness,
 * mood, stress and energy), kept as FlexNotes's own body values named by [Item], like what was eaten (#176): they're
 * graphed in the Body tracker, backed up and restored like any measurement, and need no new table.
 *
 * The score (0–100) combines what's there for the day: the check-in, heart rate variability and resting heart rate
 * against the user's own usual (the average over the [BASELINE_DAYS] before), and hours slept. Each part is scaled to
 * 0–1 and weighted ([Part.weight]); a missing part leaves the others' weights to share the score. Plain Kotlin,
 * tested in `ReadinessTest`. It's a guide to how recovered the day looks, not a medical measure.
 */
object Readiness {
    /** One check-in rating: its body value's name, and whether a higher rating means more ready. */
    enum class Item(val label: String, val higherIsBetter: Boolean) {
        SLEEP("Sleep quality", true),
        SORENESS("Soreness", false),
        MOOD("Mood", true),
        STRESS("Stress", false),
        ENERGY("Energy", true);

        fun matches(name: String): Boolean = name.trim().equals(label, ignoreCase = true)
    }

    /** The ratings' unit. */
    const val UNIT = "/5"
    const val MIN = 1
    const val MAX = 5

    /** Is [name] one of the check-in values? */
    fun isCheckIn(name: String): Boolean = Item.entries.any { it.matches(name) }

    /** The days before a date that make the usual HRV and resting heart rate, and how many of them need a value. */
    const val BASELINE_DAYS = 28
    const val MIN_BASELINE = 5

    /** Scores from here up read as ready; below [LOW] as take it easy. */
    const val HIGH = 70
    const val LOW = 40

    /** What a part of the score is made from. */
    enum class Source(val weight: Double) { CHECK_IN(0.5), HRV(0.25), RESTING_HR(0.15), SLEEP(0.10) }

    /** One part of a day's score: [value] is the day's figure (the check-in's average rating, ms, bpm or hours), [usual] its baseline. */
    data class Part(val source: Source, val score: Double, val value: Double, val usual: Double? = null)

    /** A day's score, 0–100, with the parts it was made from. */
    data class Score(val day: Long, val score: Int, val parts: List<Part>) {
        fun part(s: Source): Part? = parts.firstOrNull { it.source == s }
    }

    /** The day-by-day figures a score reads, by epoch day. */
    data class Inputs(
        val checkIns: Map<Long, Map<Item, Double>>,
        val hrv: Map<Long, Double>,
        val restingHr: Map<Long, Double>,
        val sleep: Map<Long, Double>
    ) {
        /** Every day that has something to score. */
        val days: Set<Long> get() = checkIns.keys + hrv.keys + restingHr.keys + sleep.keys
    }

    /** A rating's share of ready, 0–1: 5 is 1 when higher is better, 1 is 1 when it isn't. */
    fun ratingScore(item: Item, rating: Double): Double {
        val r = ((rating.coerceIn(MIN.toDouble(), MAX.toDouble()) - MIN) / (MAX - MIN))
        return if (item.higherIsBetter) r else 1 - r
    }

    /** The average of [byDay]'s values in the [BASELINE_DAYS] before [day], or null with fewer than [MIN_BASELINE]. */
    fun baseline(byDay: Map<Long, Double>, day: Long): Double? {
        val before = (day - BASELINE_DAYS until day).mapNotNull { byDay[it] }
        return if (before.size < MIN_BASELINE) null else before.average()
    }

    /** [day]'s score, or null when there's nothing to make it from. */
    fun score(inputs: Inputs, day: Long): Score? {
        val parts = buildList {
            inputs.checkIns[day]?.takeIf { it.isNotEmpty() }?.let { c ->
                add(Part(Source.CHECK_IN, c.entries.map { (i, v) -> ratingScore(i, v) }.average(), c.values.average()))
            }
            inputs.hrv[day]?.let { v ->
                // 20% above the usual reads as fully ready, 20% below as not at all.
                baseline(inputs.hrv, day)?.takeIf { it > 0 }?.let { b -> add(Part(Source.HRV, (0.5 + (v / b - 1) * 2.5).coerceIn(0.0, 1.0), v, b)) }
            }
            inputs.restingHr[day]?.let { v ->
                // Five beats a minute under the usual reads as fully ready, five over as not at all.
                baseline(inputs.restingHr, day)?.let { b -> add(Part(Source.RESTING_HR, (0.5 - (v - b) / 10).coerceIn(0.0, 1.0), v, b)) }
            }
            inputs.sleep[day]?.takeIf { it > 0 }?.let { h -> add(Part(Source.SLEEP, ((h - 5) / 3).coerceIn(0.0, 1.0), h)) }
        }
        if (parts.isEmpty()) return null
        val weight = parts.sumOf { it.source.weight }
        return Score(day, (100 * parts.sumOf { it.score * it.source.weight } / weight).roundToInt(), parts)
    }

    /** A score for every day that has one, oldest first. */
    fun history(inputs: Inputs): List<Score> = inputs.days.sorted().mapNotNull { score(inputs, it) }

    /** The average score over the 7 days before [day] (not counting it), or null when none of them has one. */
    fun weekAverage(history: List<Score>, day: Long): Double? =
        history.filter { it.day in day - 7 until day }.takeIf { it.isNotEmpty() }?.map { it.score }?.average()

    /** The check-in values logged under [item]'s name, whatever their capitals. */
    private fun records(snap: Snapshot, item: Item): List<MRecord> =
        snap.recordsByName.entries.filter { item.matches(it.key) }.flatMap { it.value }

    /** [date]'s check-in records. */
    fun recordsOn(snap: Snapshot, date: String): List<MRecord> = snap.recordsByDate[date].orEmpty().filter { isCheckIn(it.name) }

    /** [date]'s check-in, each rating's latest value. */
    fun checkInOn(snap: Snapshot, date: String): Map<Item, Double> =
        Item.entries.mapNotNull { i -> recordsOn(snap, date).filter { i.matches(it.name) }.maxByOrNull { it.time }?.let { i to it.value } }.toMap()

    /** Each day's value of the measurement named [name], averaged ([sum]: added up), by the primary source rule. */
    private fun byDay(snap: Snapshot, name: String, hcPrimary: Boolean, sum: Boolean = false): Map<Long, Double> =
        snap.recordsByName.entries.filter { it.key.equals(name, ignoreCase = true) }.flatMap { it.value }
            .groupBy { Dates.epochDay(it.date) }
            .mapValues { e -> Sources.dayValues(e.value, hcPrimary).map { it.value }.let { if (sum) it.sum() else it.average() } }
            .filterValues { !it.isNaN() }

    /** Everything a score reads from the snapshot, with Health Connect first where the settings say so. */
    fun inputs(snap: Snapshot, prefs: PortableSettings): Inputs {
        val checkIns = mutableMapOf<Long, MutableMap<Item, Pair<String, Double>>>()
        Item.entries.forEach { i ->
            records(snap, i).forEach { r ->
                val day = checkIns.getOrPut(Dates.epochDay(r.date)) { mutableMapOf() }
                // The latest rating of the day counts.
                if ((day[i]?.first ?: "") <= r.time) day[i] = r.time to r.value
            }
        }
        return Inputs(
            checkIns = checkIns.mapValues { e -> e.value.mapValues { it.value.second } },
            hrv = byDay(snap, HealthConnect.Names.HRV, prefs.hcHrv),
            restingHr = byDay(snap, HealthConnect.Names.RESTING_HR, prefs.hcRestingHr),
            sleep = byDay(snap, HealthConnect.Names.SLEEP, prefs.hcSleep, sum = true)
        )
    }
}
