package com.fitlens.companion.data

/**
 * The emergency card (#215): what someone helping in an emergency needs at a glance. The details typed for it live in
 * [PortableSettings] (`emergency*`), so they travel with a `.flexnotes` backup; the current medications are worked out
 * here, plain Kotlin tested in `EmergencyCardTest`, and drawn by `report/EmergencyCardPdf`.
 */
data class EmergencyDetails(
    val name: String = "",
    val allergies: String = "",
    val conditions: String = "",
    val contact: String = ""
) {
    companion object {
        fun of(s: PortableSettings) = EmergencyDetails(
            s.emergencyName.orEmpty(), s.emergencyAllergies.orEmpty(), s.emergencyConditions.orEmpty(), s.emergencyContact.orEmpty()
        )
    }
}

object EmergencyCard {
    /** A running schedule's doses are looked for this far ahead, so off days and every-few-days doses still count. */
    const val LOOK_AHEAD_DAYS = 14
    /** A medication without a schedule counts as current when it was logged within these last days. */
    const val RECENT_DAYS = 30

    /** One dose as printed: amount, unit and time, e.g. "500 mg · 08:00". */
    data class Dose(val amount: Double, val unit: String, val time: String) {
        val text: String get() = "${fmtNum(amount)} $unit".trim() + " · $time"
    }

    /**
     * A current medication: its scheduled [doses] (amount and time, each once, by time), or, when it has none,
     * its [last] value logged within [RECENT_DAYS], shown as taken when needed.
     */
    data class Line(val medication: String, val doses: List<Dose>, val last: MRecord? = null) {
        val scheduled: Boolean get() = doses.isNotEmpty()
    }

    /**
     * The medications in [names] someone is taking on [today]: those a running schedule doses in the next
     * [LOOK_AHEAD_DAYS] days, then those logged by hand within [RECENT_DAYS], each in [names] order.
     */
    fun current(plan: MedPlan, records: List<MRecord>, names: List<String>, today: String = Dates.today()): List<Line> {
        val start = Dates.parse(today) ?: return emptyList()
        val due = (0 until LOOK_AHEAD_DAYS).flatMap { i -> MedSchedules.dueOn(plan, start.plusDays(i.toLong()).format(Dates.ISO)) }
        val since = start.minusDays(RECENT_DAYS.toLong()).format(Dates.ISO)
        return names.mapNotNull { n ->
            val doses = due.filter { it.dose.medication == n }
                .map { Dose(it.amount, it.dose.unit, it.time) }
                .distinct()
                .sortedWith(compareBy<Dose> { it.time }.thenBy { it.amount })
            if (doses.isNotEmpty()) return@mapNotNull Line(n, doses)
            val last = records.filter { it.name == n && it.date.take(10) > since && it.date.take(10) <= today }
                .maxByOrNull { Supply.stamp(it) }
            last?.let { Line(n, emptyList(), it) }
        }
    }
}
