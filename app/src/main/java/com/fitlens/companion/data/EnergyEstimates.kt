package com.fitlens.companion.data

/**
 * [Energy]'s figures from what's logged (#173, #174): the bodyweight, body fat and height they use, each exercise's
 * calories on a day, and the daily total. Reads a [Snapshot] only; the rest after each set comes from the UI's
 * `restFor` through [Rest], so the same rest rules apply everywhere.
 */
object EnergyEstimates {
    /** How far from a date a bodyweight may be and still be used, as relative strength allows (#56). */
    const val BODYWEIGHT_DAYS = 14

    /** The rest after a set, in seconds: its date, exercise, set id, and whether it's the exercise's last that day. */
    fun interface Rest {
        fun seconds(date: String, exerciseId: Long, setId: Long, last: Boolean): Int
    }

    /** The bodyweight in kg nearest [date] within [BODYWEIGHT_DAYS], with its record, or null. */
    fun bodyweight(snap: Snapshot, date: String): Pair<Double, MRecord>? {
        val name = snap.bodyweightName ?: return null
        val r = snap.valueNear(name, date, BODYWEIGHT_DAYS)?.first ?: return null
        val kg = MeasureUnits.convert(r.value, r.unit, "kg")
        return if (kg > 0) kg to r else null
    }

    /** The body fat measurement's name, however it's spelt, or null when there's none. */
    fun bodyFatName(snap: Snapshot): String? =
        snap.recordsByName.keys.firstOrNull { BodyFat.isBodyFat(it) } ?: snap.allMeasurements.firstOrNull { BodyFat.isBodyFat(it.name) }?.name

    /** The latest body fat percent on or before [date], within [Energy.BODY_FAT_DAYS], with its record, or null. */
    fun bodyFat(snap: Snapshot, date: String): Pair<Double, MRecord>? {
        val name = bodyFatName(snap) ?: return null
        val r = snap.recordsByName[name].orEmpty().lastOrNull { it.date.take(10) <= date } ?: return null
        if (Dates.epochDay(date) - Dates.epochDay(r.date) > Energy.BODY_FAT_DAYS) return null
        return if (r.value in BodyFat.MIN_PERCENT..BodyFat.MAX_PERCENT) r.value to r else null
    }

    /** The latest Height measurement in cm on or before [date], or null. */
    fun heightMeasured(snap: Snapshot, date: String): Double? {
        val name = snap.recordsByName.keys.firstOrNull { BodyFat.Input.HEIGHT.matches(it) } ?: return null
        val r = snap.recordsByName[name].orEmpty().lastOrNull { it.date.take(10) <= date } ?: return null
        return LengthUnits.convert(r.value, r.unit, LengthUnits.CM).takeIf { it > 0 }
    }

    /** The height the formulas use: the profile's, or the latest Height measurement when the profile has none. */
    fun height(snap: Snapshot, prefs: PortableSettings, date: String): Double? = prefs.profileHeightCm ?: heightMeasured(snap, date)

    /** The resting energy inputs for [date]. */
    fun inputs(snap: Snapshot, prefs: PortableSettings, date: String): Energy.Inputs = Energy.Inputs(
        sex = BodyFat.Sex.of(prefs.profileSex),
        age = Energy.age(prefs.profileBirthDate, date),
        heightCm = height(snap, prefs, date),
        kg = bodyweight(snap, date)?.first,
        bodyFat = bodyFat(snap, date)?.first,
        lifestyle = Energy.Lifestyle.of(prefs.profileLifestyle)
    )

    /** Metres in [distance] logged in [unit] ("km", "mi" or "m"). */
    fun metres(distance: Double, unit: String): Double = when (unit) {
        DistanceUnits.MI -> distance * 1609.344
        DistanceUnits.M -> distance
        else -> distance * 1000.0
    }

    /** Is [exerciseId] logged as cardio (a distance or a time, without weight or reps)? */
    fun isCardio(snap: Snapshot, exerciseId: Long): Boolean {
        val type = snap.exercises[exerciseId]?.type ?: return false
        return !ExerciseTypes.usesWeight(type) && !ExerciseTypes.usesReps(type)
    }

    /** The logged workout time on [date], in seconds (0 when none, or while the timer still runs). */
    fun workoutSeconds(snap: Snapshot, date: String): Double =
        snap.workoutTimes[date].orEmpty().filter { it.end.isNotBlank() }.sumOf { Dates.secondsBetween(it.start, it.end) }.toDouble()

    /** [date]'s exercises as bouts for [Energy.bouts]; [mets] are exercises' own activities (#174). */
    fun bouts(snap: Snapshot, date: String, rest: Rest, mets: Map<Long, Double> = emptyMap()): List<Energy.Bout> {
        val sets = snap.setsByDate[date].orEmpty()
        if (sets.isEmpty()) return emptyList()
        val lastOf = sets.groupBy { it.exerciseId }.mapValues { e -> e.value.last().id }
        val inputs = sets.map { s ->
            Energy.SetInput(
                exerciseId = s.exerciseId,
                cardio = isCardio(snap, s.exerciseId),
                metres = if (s.distance > 0) metres(s.distance, snap.distanceUnit(s.exerciseId)) else 0.0,
                seconds = s.durationSec.toDouble(),
                rpe = s.rpe,
                restSeconds = rest.seconds(date, s.exerciseId, s.id, s.id == lastOf[s.exerciseId]).toDouble()
            )
        }
        return Energy.bouts(inputs, workoutSeconds(snap, date), mets)
    }

    /** Each exercise's net calories on [date] at [kg], biggest first. */
    fun exerciseKcal(snap: Snapshot, date: String, kg: Double, rest: Rest, mets: Map<Long, Double> = emptyMap()): List<Energy.BoutKcal> =
        bouts(snap, date, rest, mets).map { Energy.boutKcal(it, kg) }.sortedByDescending { it.kcal }

    /** Training calories a day, averaged over the [Energy.AVERAGE_DAYS] ending on [date], or from the plan. */
    data class Training(val kcalPerDay: Double, val fromLogs: Boolean, val days: Int)

    /**
     * The average day's training calories (#173): from the last [Energy.AVERAGE_DAYS] of logs once at least
     * [Energy.MIN_LOGGED_DAYS] days have passed since the first logged set, otherwise from [plan].
     */
    fun training(snap: Snapshot, date: String, kg: Double, rest: Rest, plan: Energy.Plan, mets: Map<Long, Double> = emptyMap()): Training {
        val first = snap.setsByDate.keys.minOrNull()
        val logged = first != null && Dates.epochDay(date) - Dates.epochDay(first) >= Energy.MIN_LOGGED_DAYS - 1
        if (!logged) return Training(plan.dailyKcal(kg), false, 0)
        val start = Energy.averageStart(date)
        val days = snap.setsByDate.keys.filter { it in start..date }
        val total = days.sumOf { d -> exerciseKcal(snap, d, kg, rest, mets).sumOf { it.kcal } }
        return Training(total / Energy.AVERAGE_DAYS, true, days.size)
    }

    /** The daily total and its parts, or what's missing for it. */
    data class Daily(
        val resting: Energy.Resting,
        val lifestyle: Energy.Lifestyle?,
        /** BMR × the lifestyle factor, the day without training. */
        val baseKcal: Double?,
        val training: Training?,
        val tdee: Double?,
        val kg: Double?
    ) {
        val missing: List<Energy.Missing>
            get() = resting.missing + if (lifestyle == null) listOf(Energy.Missing.LIFESTYLE) else emptyList()
    }

    /** The average daily total (TDEE) as of [date]. */
    fun daily(snap: Snapshot, prefs: PortableSettings, date: String, rest: Rest, mets: Map<Long, Double> = emptyMap()): Daily {
        val inputs = inputs(snap, prefs, date)
        val resting = Energy.resting(inputs, Energy.Formula.of(prefs.bmrFormula))
        val base = resting.bmr?.let { b -> inputs.lifestyle?.let { b * it.factor } }
        val kg = inputs.kg
        val training = kg?.let { training(snap, date, it, rest, Energy.Plan.decode(prefs.trainingPlan) ?: Energy.Plan.DEFAULT, mets) }
        val tdee = if (base != null && training != null) base + training.kcalPerDay else null
        return Daily(resting, inputs.lifestyle, base, training, tdee, kg)
    }
}
