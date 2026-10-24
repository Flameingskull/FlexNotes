package com.fitlens.companion.data

import kotlin.math.pow

/**
 * Water and caffeine (#221): each drink is logged with its time, as FlexNotes's own body values named by [Kind]
 * ("Water", ml; "Caffeine", mg), so they're graphed in the Body tracker and backed up like any measurement, with no
 * new table. Several on a day add up.
 *
 * Caffeine still active at bedtime is estimated with first-order elimination, a [HALF_LIFE_HOURS] half-life: each
 * drink's caffeine × 0.5^(hours to bedtime ÷ half-life). It's set against the sleep logged for that night, which
 * Health Connect dates by the day it ends (#189), so the night after a date's bedtime is the next date's Sleep.
 * Drinks can also be read from Health Connect (#226), each at its own time; which ones count on a day follows the
 * primary source rule ([counted]). Plain Kotlin, tested in `DrinksTest`. An adult's half-life varies from about 3 to 7 hours, so it's a guide.
 */
object Drinks {
    enum class Kind(val label: String, val unit: String) {
        WATER("Water", "ml"),
        CAFFEINE("Caffeine", "mg");

        fun matches(name: String): Boolean = name.trim().equals(label, ignoreCase = true)
    }

    /** Is [name] one of the drink values? */
    fun isDrink(name: String): Boolean = Kind.entries.any { it.matches(name) }

    const val HALF_LIFE_HOURS = 5.0

    /** Bedtime when none is set. */
    const val DEFAULT_BEDTIME = "23:00"

    /** The bedtimes offered, evening to after midnight. */
    val BEDTIMES = listOf("20:00", "20:30", "21:00", "21:30", "22:00", "22:30", "23:00", "23:30", "00:00", "00:30", "01:00", "01:30", "02:00")

    /** Quick amounts: water in ml. */
    val WATER_AMOUNTS = listOf(250.0, 330.0, 500.0, 750.0)

    /** Typical servings' caffeine, in mg. */
    enum class Serving(val mg: Double) { ESPRESSO(63.0), COFFEE(95.0), TEA(47.0), ENERGY_DRINK(80.0), COLA(34.0) }

    /** One drink: when (minutes after the start of its day) and how much. */
    data class Dose(val minutes: Int, val mg: Double)

    /** "HH:mm" or "HH:mm:ss" as minutes after midnight, or null. */
    fun minutesOf(time: String): Int? {
        val p = time.trim().split(':')
        val h = p.getOrNull(0)?.toIntOrNull() ?: return null
        val m = p.getOrNull(1)?.toIntOrNull() ?: 0
        return if (h in 0..23 && m in 0..59) h * 60 + m else null
    }

    /**
     * Bedtime as minutes after the start of the day: a time before noon is taken as after midnight (so "00:30" is
     * 24 h 30 after the day began).
     */
    fun bedtimeMinutes(bedtime: String): Int {
        val m = minutesOf(bedtime) ?: (23 * 60)
        return if (m < 12 * 60) m + 24 * 60 else m
    }

    /** The caffeine (mg) of [doses] still active at [bedMinutes]; drinks after bedtime don't count. */
    fun activeAt(doses: List<Dose>, bedMinutes: Int): Double =
        doses.filter { it.minutes <= bedMinutes }.sumOf { it.mg * 0.5.pow((bedMinutes - it.minutes) / 60.0 / HALF_LIFE_HOURS) }

    /** [date]'s drink records, of [kind] or both. */
    fun recordsOn(snap: Snapshot, date: String, kind: Kind? = null): List<MRecord> =
        snap.recordsByDate[date].orEmpty().filter { r -> if (kind == null) isDrink(r.name) else kind.matches(r.name) }

    /**
     * [date]'s [kind] drinks that count, by the primary source rule (#226): with Health Connect chosen ([hcPrimary]),
     * the drinks read from it when the day has any; otherwise the ones typed, falling back to Health Connect's.
     */
    fun counted(snap: Snapshot, date: String, kind: Kind, hcPrimary: Boolean): List<MRecord> =
        Sources.dayValues(recordsOn(snap, date, kind), hcPrimary)

    /** [date]'s total of [kind]. */
    fun total(snap: Snapshot, date: String, kind: Kind, hcPrimary: Boolean = false): Double =
        counted(snap, date, kind, hcPrimary).sumOf { it.value }

    /** [date]'s caffeine as doses, each at its own time (typed or read from Health Connect). */
    fun caffeineDoses(snap: Snapshot, date: String, hcPrimary: Boolean = false): List<Dose> =
        counted(snap, date, Kind.CAFFEINE, hcPrimary).mapNotNull { r -> minutesOf(r.time)?.let { Dose(it, r.value) } }

    /** Caffeine still active at [date]'s [bedtime], or null when none was logged that day. */
    fun atBedtime(snap: Snapshot, date: String, bedtime: String, hcPrimary: Boolean = false): Double? =
        caffeineDoses(snap, date, hcPrimary).takeIf { it.isNotEmpty() }?.let { activeAt(it, bedtimeMinutes(bedtime)) }

    /** The hours slept the night after [date]'s bedtime (the next date's Sleep), by the primary source rule. */
    fun sleepAfter(snap: Snapshot, date: String, hcPrimary: Boolean): Double? {
        val next = Dates.parse(date)?.plusDays(1)?.format(Dates.ISO) ?: return null
        val sleep = snap.recordsByDate[next].orEmpty().filter { it.name.equals(HealthConnect.Names.SLEEP, ignoreCase = true) }
        return Sources.dayValues(sleep, hcPrimary).takeIf { it.isNotEmpty() }?.sumOf { it.value }
    }

    /** One night: the caffeine active at bedtime and the sleep after it (either may be missing). */
    data class Night(val date: String, val caffeineMg: Double?, val sleepHours: Double?)

    /** The [days] nights up to [date] that have caffeine or sleep, latest first. */
    fun nights(snap: Snapshot, date: String, bedtime: String, hcPrimary: Boolean, days: Int = 7, hcCaffeine: Boolean = false): List<Night> {
        val end = Dates.parse(date) ?: return emptyList()
        return (0 until days).map { end.minusDays(it.toLong()).format(Dates.ISO) }
            .map { d -> Night(d, atBedtime(snap, d, bedtime, hcCaffeine), sleepAfter(snap, d, hcPrimary)) }
            .filter { it.caffeineMg != null || it.sleepHours != null }
    }
}
