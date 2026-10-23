package com.fitlens.companion.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * What the Medications screens keep about a medication beyond its values and schedules (#209, #210), and the symptoms
 * rated beside them (#207). The calculations ([Supply.of], [InjectionSites.suggest]) are plain Kotlin over these
 * models, tested in `MedExtrasTest`; the writes in [MedExtras] refresh the body area, which holds the medications.
 */

/**
 * A medication's own details (#209, #210): the [supply] on hand when it was counted at [supplyFrom]
 * (`yyyy-MM-dd HH:mm:ss`), a warning [warnDays] before it runs out or expires, an [expires] date, and whether doses are
 * [injected]. A medication with nothing set has [MedInfo.none].
 */
data class MedInfo(
    val medication: String,
    val supply: Double? = null,
    val supplyFrom: String? = null,
    val warnDays: Int = DEFAULT_WARN_DAYS,
    val expires: String? = null,
    val injected: Boolean = false
) {
    val tracksSupply: Boolean get() = supply != null

    companion object {
        const val DEFAULT_WARN_DAYS = 7
        fun none(medication: String) = MedInfo(medication)
    }
}

/** A symptom the user rates (#207). */
data class Symptom(val id: Long, val name: String, val sortOrder: Int = 0)

/** A symptom's rating on a date, 0–10 (#207). */
data class SymptomRating(val id: Long, val symptomId: Long, val date: String, val rating: Int, val comment: String? = null)

/** How much of a medication is left and when it runs out (#209). */
data class SupplyState(
    /** What's left: the counted supply less every value logged since. Never below 0. */
    val left: Double,
    /** The first day the supply can't cover that day's doses, or null when nothing uses it up. */
    val runsOut: String?,
    /** True when it runs out within the warning days (or has run out). */
    val low: Boolean,
    val expired: Boolean,
    /** True when it expires within the warning days. */
    val expiresSoon: Boolean
) {
    val needsAttention: Boolean get() = low || expired || expiresSoon
}

object Supply {
    /** The furthest ahead a run-out date is looked for. */
    const val HORIZON_DAYS = 366
    /** When nothing is scheduled, use is the average of the values logged over these last days. */
    const val AVERAGE_DAYS = 28

    private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun now(): String = LocalDateTime.now().format(STAMP)

    /** A value's moment as `yyyy-MM-dd HH:mm:ss`, so it compares with [MedInfo.supplyFrom]; no time sorts first. */
    fun stamp(r: MRecord): String {
        val t = r.time.trim()
        val time = when {
            t.length >= 8 -> t.take(8)
            t.length == 5 -> "$t:00"
            else -> "00:00:00"
        }
        return "${r.date.take(10)} $time"
    }

    /** What's left of [info]'s supply: the count less every value of the medication logged after it. */
    fun left(info: MedInfo, records: List<MRecord>): Double? {
        val supply = info.supply ?: return null
        val from = info.supplyFrom.orEmpty()
        val used = records.filter { it.name == info.medication && stamp(it) > from }.sumOf { it.value }
        return (supply - used).coerceAtLeast(0.0)
    }

    /**
     * The supply of [info] on [today]: what's left, and the day it runs out, walking the running schedules forward
     * (today's doses not yet taken or skipped, then every later day's, so off phases and pauses count). With nothing
     * scheduled, the average logged over the last [AVERAGE_DAYS] days is used instead. Null when no supply is set.
     */
    fun of(info: MedInfo, plan: MedPlan, records: List<MRecord>, today: String = Dates.today()): SupplyState? {
        val left = left(info, records) ?: return null
        val name = info.medication
        val start = Dates.parse(today) ?: return null
        var remaining = left
        var runsOut: String? = null
        var scheduled = false
        for (i in 0 until HORIZON_DAYS) {
            val date = start.plusDays(i.toLong()).format(Dates.ISO)
            val due = MedSchedules.dueOn(plan, date).filter { it.dose.medication == name }
            val open = if (i == 0) {
                due.filter { MedSchedules.takenRecord(records, it) == null && !plan.isSkipped(it.dose.id, it.due) }
            } else due
            if (due.isNotEmpty()) scheduled = true
            val need = open.sumOf { it.amount }
            if (need > 0 && remaining + 1e-9 < need) { runsOut = date; break }
            remaining -= need
        }
        if (!scheduled) {
            val since = start.minusDays(AVERAGE_DAYS.toLong()).format(Dates.ISO)
            val perDay = records.filter { it.name == name && it.date.take(10) > since && it.date.take(10) <= today }
                .sumOf { it.value } / AVERAGE_DAYS
            runsOut = if (perDay > 0) start.plusDays((left / perDay).toLong()).format(Dates.ISO) else null
        }
        if (left <= 0.0) runsOut = today
        val warnBy = start.plusDays(info.warnDays.coerceAtLeast(0).toLong()).format(Dates.ISO)
        val expires = info.expires
        return SupplyState(
            left = left,
            runsOut = runsOut,
            low = runsOut != null && runsOut <= warnBy,
            expired = expires != null && expires < today,
            expiresSoon = expires != null && expires >= today && expires <= warnBy
        )
    }
}

/**
 * Injection sites (#210): ten areas over the abdomen, thighs, upper arms and buttocks, each stored by its [key]. The
 * next site suggested is the one used longest ago, never-used ones first, in [entries] order.
 */
enum class InjectionSite(val key: String) {
    ABDOMEN_UPPER_LEFT("abd-ul"),
    ABDOMEN_UPPER_RIGHT("abd-ur"),
    ABDOMEN_LOWER_RIGHT("abd-lr"),
    ABDOMEN_LOWER_LEFT("abd-ll"),
    THIGH_LEFT("thigh-l"),
    THIGH_RIGHT("thigh-r"),
    ARM_LEFT("arm-l"),
    ARM_RIGHT("arm-r"),
    GLUTE_LEFT("glute-l"),
    GLUTE_RIGHT("glute-r");

    companion object {
        fun of(key: String?): InjectionSite? = entries.firstOrNull { it.key == key }
    }
}

object InjectionSites {
    /** When each site was last used for [medication]: its latest value's stamp. */
    fun lastUsed(records: List<MRecord>, medication: String): Map<InjectionSite, String> {
        val out = HashMap<InjectionSite, String>()
        records.filter { it.name == medication }.forEach { r ->
            val site = InjectionSite.of(r.site) ?: return@forEach
            val at = Supply.stamp(r)
            if ((out[site] ?: "") < at) out[site] = at
        }
        return out
    }

    /** The next site for [medication]: one never used, else the one used longest ago. */
    fun suggest(records: List<MRecord>, medication: String): InjectionSite {
        val last = lastUsed(records, medication)
        return InjectionSite.entries.minWith(compareBy<InjectionSite> { last[it] ?: "" }.thenBy { it.ordinal })
    }

    /** The [count] most recent sited values of [medication], newest first. */
    fun recent(records: List<MRecord>, medication: String, count: Int = 8): List<MRecord> =
        records.filter { it.name == medication && InjectionSite.of(it.site) != null }
            .sortedByDescending { Supply.stamp(it) }.take(count)
}

object Symptoms {
    const val MAX = 10

    /** Every rating of [plan] on [date], in the symptom list's order. */
    fun ratingsOn(plan: MedPlan, date: String): List<Pair<Symptom, SymptomRating>> =
        plan.symptoms.mapNotNull { s -> plan.ratings.firstOrNull { it.symptomId == s.id && it.date == date }?.let { s to it } }

    /** One symptom's ratings, oldest first. */
    fun series(plan: MedPlan, symptomId: Long): List<SymptomRating> =
        plan.ratings.filter { it.symptomId == symptomId }.sortedBy { it.date }
}

/** The writes for [MedInfo], sites and symptoms, each in one transaction, refreshing the body area. */
object MedExtras {
    private suspend fun <T> write(block: (MedDao) -> T): T = withContext(Dispatchers.IO) {
        val db = Store.db
        val result = db.transaction { block(db.medDao) }
        Store.refresh(Area.BODY)
        result
    }

    private fun row(i: MedInfo) = MedInfoRow(
        i.medication, i.supply, i.supplyFrom, i.warnDays.coerceIn(0, 365),
        i.expires?.takeIf { Dates.parse(it) != null }, if (i.injected) 1 else 0
    )

    /** Saves [info] as it is (for the supply sheet, the injected switch and Undo). */
    suspend fun setInfo(info: MedInfo): Unit = write { r -> r.setInfo(row(info)) }

    /** Sets what's on hand now to [amount], counting down from this moment. */
    suspend fun count(info: MedInfo, amount: Double): Unit = write { r ->
        r.setInfo(row(info.copy(supply = amount.coerceAtLeast(0.0), supplyFrom = Supply.now())))
    }

    /** Sets the site of a value (#210), or clears it with null. */
    suspend fun setSite(recordId: Long, site: InjectionSite?): Unit = write { r -> r.setSite(recordId, site?.key) }

    suspend fun addSymptom(name: String): Long = write { r ->
        val clean = name.trim().replace(Regex("\\s+"), " ")
        r.symptomId(clean) ?: r.addSymptom(SymptomRow(0L, clean, r.nextSymptomOrder()))
    }

    suspend fun renameSymptom(id: Long, name: String): Unit = write { r ->
        val clean = name.trim().replace(Regex("\\s+"), " ")
        if (clean.isNotEmpty()) r.renameSymptom(id, clean)
    }

    /** Deletes a symptom and every rating of it. */
    suspend fun deleteSymptom(id: Long): Unit = write { r ->
        r.deleteRatingsOf(id)
        r.deleteSymptom(id)
    }

    /** Puts a deleted symptom and its ratings back, for Undo. */
    suspend fun restoreSymptom(s: Symptom, ratings: List<SymptomRating>): Unit = write { r ->
        r.addSymptom(SymptomRow(s.id, s.name, s.sortOrder))
        ratings.forEach { r.rate(it.symptomId, it.date, it.rating, it.comment) }
    }

    /** Rates [symptomId] on [date] (0–10), replacing that day's rating. */
    suspend fun rate(symptomId: Long, date: String, rating: Int, comment: String?): Unit = write { r ->
        r.rate(symptomId, date, rating.coerceIn(0, Symptoms.MAX), comment?.trim()?.ifBlank { null })
    }

    suspend fun clearRating(symptomId: Long, date: String): Unit = write { r -> r.clearRating(symptomId, date) }
}
