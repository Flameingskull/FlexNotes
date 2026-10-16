package com.fitlens.companion.data

import kotlin.math.abs

/**
 * What was eaten (#176), and the TDEE measured from it. A day's intake is kept as body values ("Calories eaten",
 * "Protein eaten", "Fat eaten", "Carbs eaten"), so it's graphed in the Body tracker, backed up and restored like any
 * measurement, and needs no new table. Several values of one kind on a date add up.
 *
 * The measured (adaptive) TDEE: over the [WINDOW_DAYS] before a date, the average logged intake less the energy the
 * bodyweight trend stored or released ([Energy.KCAL_PER_KG] a kg, the trend a least-squares line through the
 * weigh-ins, so a single heavy morning doesn't swing it). Plain Kotlin, tested in `IntakeTest`.
 *
 * Intake can also come from Health Connect (#186): each day's totals are kept as `Sources.HEALTH_CONNECT` values, and
 * with `PortableSettings.hcIntake` they count instead of what was typed on a day that has them (`Sources.dayValues`).
 */
object Intake {
    /** One kind of intake: its body value's name and unit. */
    enum class Kind(val label: String, val unit: String) {
        KCAL("Calories eaten", "kcal"),
        PROTEIN("Protein eaten", "g"),
        FAT("Fat eaten", "g"),
        CARBS("Carbs eaten", "g");

        fun matches(name: String): Boolean = name.trim().equals(label, ignoreCase = true)
    }

    /** Is [name] one of the intake values? */
    fun isIntake(name: String): Boolean = Kind.entries.any { it.matches(name) }

    /** The days measured over, ending the day before the date (whose intake may not all be logged yet). */
    const val WINDOW_DAYS = 28

    /** Days with logged calories the measurement needs in its window. */
    const val MIN_INTAKE_DAYS = 14

    /** Weigh-ins it needs, and how many days apart the first and last must be. */
    const val MIN_WEIGH_INS = 3
    const val MIN_SPAN_DAYS = 14

    /** Measured results outside this range are taken as a logging mistake, not a TDEE. */
    val PLAUSIBLE = 800.0..7000.0

    /** A TDEE measured from intake and the weight trend. */
    data class Measured(
        val tdee: Double,
        /** The average day's logged calories. */
        val intakeKcal: Double,
        /** The bodyweight trend, kg a week (negative when losing). */
        val kgPerWeek: Double
    )

    /** How far the logs are towards a measurement: days with calories, weigh-ins, and the days between them. */
    data class Progress(val intakeDays: Int, val weighIns: Int, val spanDays: Int) {
        val enough: Boolean get() = intakeDays >= MIN_INTAKE_DAYS && weighIns >= MIN_WEIGH_INS && spanDays >= MIN_SPAN_DAYS
    }

    /**
     * The TDEE measured as of [endDay] (an epoch day): [kcalByDay] is each day's logged calories, [weights] each
     * weigh-in as (epoch day, kg). Intake counts from the [WINDOW_DAYS] before [endDay]; weigh-ins from that window
     * and [endDay] itself. Null, with the [Progress], until there's enough, or when the result isn't plausible.
     */
    fun measure(kcalByDay: Map<Long, Double>, weights: List<Pair<Long, Double>>, endDay: Long): Pair<Measured?, Progress> {
        val from = endDay - WINDOW_DAYS
        val intake = kcalByDay.filter { (d, k) -> d in from until endDay && k > 0 }
        val ws = weights.filter { (d, kg) -> d in from..endDay && kg > 0 }
        val span = if (ws.isEmpty()) 0 else (ws.maxOf { it.first } - ws.minOf { it.first }).toInt()
        val progress = Progress(intake.size, ws.size, span)
        if (!progress.enough) return null to progress
        val slope = slope(ws) ?: return null to progress
        val avg = intake.values.average()
        val tdee = avg - slope * Energy.KCAL_PER_KG
        if (tdee !in PLAUSIBLE) return null to progress
        return Measured(tdee, avg, slope * 7) to progress
    }

    /** The least-squares slope of [points] (x = day, y = kg), in kg a day, or null when every x is the same. */
    internal fun slope(points: List<Pair<Long, Double>>): Double? {
        if (points.size < 2) return null
        val mx = points.map { it.first.toDouble() }.average()
        val my = points.map { it.second }.average()
        val sxx = points.sumOf { (x, _) -> (x - mx) * (x - mx) }
        if (abs(sxx) < 1e-9) return null
        return points.sumOf { (x, y) -> (x - mx) * (y - my) } / sxx
    }

    /** The values logged under [kind]'s name, whatever their capitals. */
    fun records(snap: Snapshot, kind: Kind): List<MRecord> =
        snap.recordsByName.entries.filter { kind.matches(it.key) }.flatMap { it.value }

    /** [date]'s intake records, of every kind and source. */
    fun recordsOn(snap: Snapshot, date: String): List<MRecord> =
        snap.recordsByDate[date].orEmpty().filter { isIntake(it.name) }

    /** [date]'s intake records the user logged (typed or imported), leaving out Health Connect's: what the sheet edits. */
    fun ownRecordsOn(snap: Snapshot, date: String): List<MRecord> =
        recordsOn(snap, date).filter { it.source != Sources.HEALTH_CONNECT }

    /** [date]'s total of each kind that counts, by the primary source rule ([hcPrimary]: Health Connect). */
    fun totals(snap: Snapshot, date: String, hcPrimary: Boolean = false): Map<Kind, Double> = sum(recordsOn(snap, date)) { Sources.dayValues(it, hcPrimary) }

    /** [date]'s total of each kind the user logged, without Health Connect's. */
    fun ownTotals(snap: Snapshot, date: String): Map<Kind, Double> = sum(ownRecordsOn(snap, date)) { it }

    /** Health Connect's records that count on [date] (#186), or none when what was typed counts. */
    fun healthConnectOn(snap: Snapshot, date: String, hcPrimary: Boolean): List<MRecord> {
        val on = recordsOn(snap, date)
        return Kind.entries.flatMap { k -> Sources.dayValues(on.filter { k.matches(it.name) }, hcPrimary) }.filter { it.source == Sources.HEALTH_CONNECT }
    }

    private fun sum(on: List<MRecord>, counted: (List<MRecord>) -> List<MRecord>): Map<Kind, Double> =
        Kind.entries.mapNotNull { k -> counted(on.filter { k.matches(it.name) }).takeIf { it.isNotEmpty() }?.let { k to it.sumOf { r -> r.value } } }.toMap()

    /** Each day's logged calories that count, by epoch day. */
    fun kcalByDay(snap: Snapshot, hcPrimary: Boolean = false): Map<Long, Double> =
        records(snap, Kind.KCAL).groupBy { Dates.epochDay(it.date) }.mapValues { e -> Sources.dayValues(e.value, hcPrimary).sumOf { it.value } }

    /** Every weigh-in as (epoch day, kg). */
    fun weighIns(snap: Snapshot): List<Pair<Long, Double>> {
        val name = snap.bodyweightName ?: return emptyList()
        return snap.recordsByName[name].orEmpty().map { Dates.epochDay(it.date) to MeasureUnits.convert(it.value, it.unit, "kg") }
    }

    /** The TDEE measured as of [date] from the snapshot, counting intake by the primary source rule. */
    fun measured(snap: Snapshot, date: String, hcPrimary: Boolean = false): Pair<Measured?, Progress> =
        measure(kcalByDay(snap, hcPrimary), weighIns(snap), Dates.epochDay(date))
}
