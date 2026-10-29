package com.fitlens.companion.data

import android.content.res.Resources
import com.fitlens.companion.R
import kotlin.math.abs

/*
 * Words for stored values that the data layer itself writes into notifications, reports and exports (#309), so `data/`
 * never reaches up into `ui/`. Screens use them too.
 */

/** "Front", "Side", "Back", "Other"; any other stored pose is shown as it is. */
internal fun poseText(res: Resources, pose: String): String = when (pose) {
    "Front" -> res.getString(R.string.pose_front)
    "Side" -> res.getString(R.string.pose_side)
    "Back" -> res.getString(R.string.pose_back)
    "Other" -> res.getString(R.string.pose_other)
    "" -> res.getString(R.string.pose_not_set)
    else -> pose
}

/** A set's counts as they read on its line (#266): "3 Takedowns". A count that is unset or 0 is left out. */
internal fun countParts(names: List<String>, a: Int?, b: Int?): List<String> =
    listOf(a, b).mapIndexedNotNull { i, v ->
        val name = names.getOrNull(i)
        if (name == null || v == null || v <= 0) null else "$v $name"
    }

/** "Abdomen, upper left", an injection site's full name (#210). */
internal fun siteName(res: Resources, site: InjectionSite): String = res.getString(
    when (site) {
        InjectionSite.ABDOMEN_UPPER_LEFT -> R.string.inj_abd_ul
        InjectionSite.ABDOMEN_UPPER_RIGHT -> R.string.inj_abd_ur
        InjectionSite.ABDOMEN_LOWER_RIGHT -> R.string.inj_abd_lr
        InjectionSite.ABDOMEN_LOWER_LEFT -> R.string.inj_abd_ll
        InjectionSite.THIGH_LEFT -> R.string.inj_thigh_l
        InjectionSite.THIGH_RIGHT -> R.string.inj_thigh_r
        InjectionSite.ARM_LEFT -> R.string.inj_arm_l
        InjectionSite.ARM_RIGHT -> R.string.inj_arm_r
        InjectionSite.GLUTE_LEFT -> R.string.inj_glute_l
        InjectionSite.GLUTE_RIGHT -> R.string.inj_glute_r
    }
)

/** A body measurement's place in the user's order, by name; unknown names go last. */
fun defOrder(snap: Snapshot, name: String): Int =
    snap.measurementDefs.firstOrNull { it.name == name }?.sortOrder ?: 999

/** A set as one line of text: "60 kg · 8 reps", "5 km · 25:00". Shared by screens and the PDF report. */
fun describeSet(
    res: Resources, snap: Snapshot, weightKg: Double, reps: Int, distance: Double, duration: Int, exerciseId: Long? = null,
    /** A custom type's metric (#14), shown with its unit. */
    metric: Double? = null
): String {
    val parts = ArrayList<String>()
    // A bodyweight set has no weight to show; "0 kg x 10 reps" read as though the weight had been lost (#74).
    if (weightKg != 0.0) parts.add("${snap.fmtWeight(weightKg, exerciseId)} ${snap.weightUnitOf(exerciseId)}")
    // A Rounds set's reps are its rounds (#246).
    val isRounds = exerciseId?.let { snap.exercises[it] }?.let { ExerciseTypes.isRounds(it.type) } == true
    if (reps > 0) parts.add(res.getQuantityString(if (isRounds) R.plurals.rounds_count else R.plurals.reps_count, reps, reps))
    // Distances carry their exercise's unit, or the global one when the exercise isn't known (#7).
    if (distance > 0) parts.add("${fmtNum(distance)} ${exerciseId?.let { snap.distanceUnit(it) } ?: snap.globalDistanceUnit}")
    if (duration > 0) parts.add(fmtDuration(duration))
    if (metric != null) {
        val m = exerciseId?.let { snap.exercises[it] }?.let { ExerciseTypes.metricOf(it.type) }
        parts.add(listOfNotNull(fmtNum(metric, 2), m?.metricUnit, m?.metricName?.lowercase()).joinToString(" "))
    }
    return parts.joinToString(" · ").ifBlank { "—" }
}

/** "Range 1–3 mmol/L", "Range up to 3 mmol/L" or "Range from 1 mmol/L", or null when no range is set. */
internal fun rangeText(res: Resources, def: MeasurementDef?): String? {
    if (def == null || !def.hasRange) return null
    val unit = def.unit.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""
    val low = def.refLow
    val high = def.refHigh
    return when {
        low != null && high != null -> res.getString(R.string.lab_range_both, fmtNum(low), "${fmtNum(high)}$unit")
        high != null -> res.getString(R.string.lab_range_high, "${fmtNum(high)}$unit")
        else -> res.getString(R.string.lab_range_low, "${fmtNum(low ?: 0.0)}$unit")
    }
}

/** "▲ High" or "▼ Low" for a value outside [def]'s range, or null for one in it or without a range. */
internal fun flagText(res: Resources, def: MeasurementDef?, value: Double): String? = when (def?.rangeFlag(value)) {
    1 -> res.getString(R.string.lab_flag_high)
    -1 -> res.getString(R.string.lab_flag_low)
    else -> null
}

/**
 * Where [a] is on [date], as dates people plan by (#237), never a day count: "On until Mon 3 Nov", "Off · on again
 * Tue 4 Nov", "Off until Fri 14 Nov, then it ends", "Ongoing since Mon 6 Oct", "Paused since Sat 4 Oct",
 * "Starts Sat 1 Nov", "Finished".
 */
internal fun phaseText(res: Resources, s: MedSchedule, a: MedActivation, date: String): String {
    val pause = a.pauses.firstOrNull { it.to == null }
    return when {
        a.start > date -> res.getString(R.string.md_starts, Dates.day(a.start))
        pause != null && date >= pause.from -> res.getString(R.string.md_paused_since, Dates.day(pause.from))
        !a.runsOn(date) -> res.getString(R.string.md_ended)
        else -> {
            val st = MedSchedules.phaseOn(s, a, date) ?: return res.getString(R.string.md_finished)
            val last = st.lastDay
            val phases = s.phases.filter { it.length > 0 }
            // Another phase follows unless this is the last one of a schedule that doesn't repeat.
            val more = s.repeats || phases.indexOf(st.phase) < phases.size - 1
            when {
                // An ongoing phase (#228) has no end: when the run began says more than a count of days.
                last == null -> if (st.phase.on) res.getString(R.string.md_phase_on_ongoing, Dates.day(a.start))
                    else res.getString(R.string.md_phase_off_ongoing)
                st.phase.on -> res.getString(R.string.md_phase_on, Dates.day(last))
                !more -> res.getString(R.string.md_phase_off_last, Dates.day(last))
                else -> res.getString(R.string.md_phase_off, Dates.day(Dates.plusDays(last, 1)))
            }
        }
    }
}

/** "2 weeks on · 1 week off, repeating", or "2 weeks on · then on, ongoing" (#228). */
internal fun scheduleSummary(res: Resources, s: MedSchedule): String {
    if (s.phases.isEmpty()) return res.getString(R.string.md_no_phases)
    val parts = s.phases.joinToString(" · ") { p ->
        if (p.unit == PhaseUnit.ONGOING) res.getString(if (p.on) R.string.md_summary_on_ongoing else R.string.md_summary_off_ongoing)
        else res.getString(if (p.on) R.string.md_summary_on else R.string.md_summary_off, phaseLength(res, p.length, p.unit))
    }
    return if (s.repeats && !s.ongoing) res.getString(R.string.md_summary_repeating, parts) else parts
}

/** "8 weeks", "1 month", or "ongoing" (#228). */
internal fun phaseLength(res: Resources, n: Int, unit: PhaseUnit): String = when (unit) {
    PhaseUnit.DAYS -> res.getQuantityString(R.plurals.md_days, n, n)
    PhaseUnit.WEEKS -> res.getQuantityString(R.plurals.md_weeks, n, n)
    PhaseUnit.MONTHS -> res.getQuantityString(R.plurals.md_months, n, n)
    PhaseUnit.YEARS -> res.getQuantityString(R.plurals.md_years, n, n)
    PhaseUnit.ONGOING -> res.getString(R.string.md_ongoing)
}

/** "Down 5 mg every 7 days, to 10 mg" or "Up 2.5 mg every 14 days", a dose's taper or step-up (#234); null without one. */
internal fun taperText(res: Resources, d: MedDose): String? {
    if (!d.steps) return null
    val every = res.getQuantityString(R.plurals.md_step_every, d.stepEvery, d.stepEvery)
    fun amount(v: Double) = "${fmtNum(v)} ${d.unit}".trim()
    val by = amount(abs(d.step))
    return when {
        d.step < 0 -> res.getString(R.string.md_taper_down, by, every, amount(minOf(d.stepLimit ?: 0.0, d.amount)))
        d.stepLimit != null -> res.getString(R.string.md_taper_up, by, every, amount(maxOf(d.stepLimit, d.amount)))
        else -> res.getString(R.string.md_taper_up_open, by, every)
    }
}
