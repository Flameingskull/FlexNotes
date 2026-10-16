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

    /** Each exercise's own activity as a MET, for those that have one (#174). */
    fun exerciseMets(snap: Snapshot): Map<Long, Double> =
        snap.exercises.values.mapNotNull { e -> Energy.Activity.of(e.activity)?.let { e.id to it.met } }.toMap()

    /** [date]'s exercises as bouts for [Energy.bouts]; [mets] are exercises' own activities (#174). */
    fun bouts(snap: Snapshot, date: String, rest: Rest, mets: Map<Long, Double> = exerciseMets(snap)): List<Energy.Bout> {
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

    private val NO_REST = Rest { _, _, _, _ -> 0 }

    /**
     * The steps on [date] that logged walks and runs already count (#181), so the phone's steps for them aren't added
     * a second time. Only cardio and the exercises' own activities matter, so rests aren't needed.
     */
    fun walkedSteps(snap: Snapshot, date: String, inputs: Energy.Inputs): Double =
        bouts(snap, date, NO_REST, emptyMap()).sumOf { b ->
            Energy.walkedSteps(b, Energy.Activity.of(snap.exercises[b.exerciseId]?.activity), inputs.heightCm, inputs.sex)
        }

    /** Each exercise's net calories on [date] at [kg], biggest first. */
    fun exerciseKcal(snap: Snapshot, date: String, kg: Double, rest: Rest, mets: Map<Long, Double> = exerciseMets(snap)): List<Energy.BoutKcal> =
        bouts(snap, date, rest, mets).map { Energy.boutKcal(it, kg) }.sortedByDescending { it.kcal }

    /** Training calories a day, averaged over the [Energy.AVERAGE_DAYS] ending on [date], or from the plan. */
    data class Training(val kcalPerDay: Double, val fromLogs: Boolean, val days: Int)

    /**
     * The average day's training calories (#173): from the last [Energy.AVERAGE_DAYS] of logs once at least
     * [Energy.MIN_LOGGED_DAYS] days have passed since the first logged set, otherwise from [plan].
     */
    fun training(snap: Snapshot, date: String, kg: Double, rest: Rest, plan: Energy.Plan, mets: Map<Long, Double> = exerciseMets(snap)): Training {
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
        /** The daily total the goal uses: [measured]'s when it's chosen and there, otherwise [estimate]. */
        val tdee: Double?,
        val kg: Double?,
        /** The formulas' daily total: [baseKcal] plus [training]. */
        val estimate: Double? = tdee,
        /** The TDEE measured from intake and the weight trend (#176), when there's enough logged. */
        val measured: Intake.Measured? = null,
        val progress: Intake.Progress = Intake.Progress(0, 0, 0),
        /** [tdee] is [measured]'s. */
        val usesMeasured: Boolean = false,
        /** The average steps a day [baseKcal] is from (#175), or null when it's from [lifestyle]. */
        val steps: Steps.Average? = null
    ) {
        val missing: List<Energy.Missing>
            get() = resting.missing + if (lifestyle == null && steps == null) listOf(Energy.Missing.LIFESTYLE) else emptyList()
    }

    /** The average daily total (TDEE) as of [date]. */
    fun daily(snap: Snapshot, prefs: PortableSettings, date: String, rest: Rest, mets: Map<Long, Double> = exerciseMets(snap)): Daily {
        val inputs = inputs(snap, prefs, date)
        val resting = Energy.resting(inputs, Energy.Formula.of(prefs.bmrFormula))
        val kg = inputs.kg
        // With step counting on and enough days counted, the steps replace the chosen activity level (#175), less
        // the steps of logged walks and runs, which training already counts (#181).
        val steps = if (prefs.activityFromSteps && kg != null) Steps.average(snap, date, hcPrimary = prefs.hcSteps) { d -> walkedSteps(snap, d, inputs) } else null
        val base = resting.bmr?.let { b ->
            if (steps != null && kg != null) {
                b * Energy.Lifestyle.SITTING.factor + Energy.stepKcal(steps.perDay, kg, inputs.heightCm, inputs.sex)
            } else {
                inputs.lifestyle?.let { b * it.factor }
            }
        }
        val training = kg?.let { training(snap, date, it, rest, Energy.Plan.decode(prefs.trainingPlan) ?: Energy.Plan.DEFAULT, mets) }
        val estimate = if (base != null && training != null) base + training.kcalPerDay else null
        val (measured, progress) = Intake.measured(snap, date, prefs.hcIntake)
        val useMeasured = measured != null && prefs.tdeeSource == SOURCE_MEASURED
        val tdee = if (useMeasured) measured!!.tdee else estimate
        return Daily(resting, inputs.lifestyle, base, training, tdee, kg, estimate, measured, progress, useMeasured, steps)
    }

    /** Which TDEE the goal uses (#176): the formulas' estimate, or the one measured from intake when there is one. */
    const val SOURCE_ESTIMATE = "estimate"
    const val SOURCE_MEASURED = "measured"

    /** How the day's calorie need is set (#174): the target every day, or that day's total with the goal's change. */
    const val MODE_SAME = "same"
    const val MODE_TRAINING = "training"

    /** One day's calories (#174): what was burned and what the day needs. */
    data class Day(
        val date: String,
        val bmr: Double,
        val formula: Energy.Formula,
        /**
         * Daily activity outside training, digestion included: BMR × (lifestyle factor − 1), or with steps (#175)
         * BMR × (Sitting factor − 1) plus the day's steps.
         */
        val activityKcal: Double,
        val exercises: List<Energy.BoutKcal>,
        /** The day's net exercise calories added up. */
        val exerciseKcal: Double,
        val burned: Double,
        /** The daily need, or null when no target or goal is set. */
        val need: Double?,
        val mode: String,
        /** The goal's change a day (target − average daily total), or 0 when maintaining. */
        val goalKcal: Double,
        /** The steps counted that day (#175), or null when [activityKcal] is from the activity level or the average. */
        val steps: Double? = null,
        /** Of [steps], those left out because a logged walk or run already counts them (#181). */
        val walkedSteps: Double = 0.0
    )

    /**
     * [date]'s calories, or the [Daily] that says what's missing (#174). Burned is resting energy, daily activity and
     * the day's exercise. The need is the target on every day ([MODE_SAME]), or with [MODE_TRAINING] that day's burned
     * total plus the goal's daily change, so a training day needs more than a rest day.
     */
    fun day(snap: Snapshot, prefs: PortableSettings, date: String, rest: Rest): Pair<Day?, Daily> {
        val mets = exerciseMets(snap)
        val daily = daily(snap, prefs, date, rest, mets)
        val bmr = daily.resting.bmr
        val base = daily.baseKcal
        val kg = daily.kg
        if (bmr == null || base == null || kg == null) return null to daily
        val exercises = exerciseKcal(snap, date, kg, rest, mets)
        val exerciseKcal = exercises.sumOf { it.kcal }
        // A day with steps counted uses its own steps (so far, for today); other days use the average's activity.
        // Steps a logged walk or run took are left out, since its own calories count them (#181).
        val steps = if (daily.steps != null) Steps.on(snap, date, prefs.hcSteps) else null
        var walked = 0.0
        val activity = if (steps != null) {
            val inputs = inputs(snap, prefs, date)
            walked = minOf(steps, walkedSteps(snap, date, inputs))
            bmr * (Energy.Lifestyle.SITTING.factor - 1) + Energy.stepKcal(steps - walked, kg, inputs.heightCm, inputs.sex)
        } else {
            base - bmr
        }
        val burned = bmr + activity + exerciseKcal
        val tdee = daily.tdee
        val goal = Energy.Goal.decode(prefs.calorieGoal)
        val target = prefs.calorieTargetKcal?.toDouble() ?: if (goal != null && tdee != null) Energy.target(goal, tdee, kg) else null
        val goalKcal = if (target != null && tdee != null) target - tdee else 0.0
        val mode = if (prefs.calorieMode == MODE_TRAINING) MODE_TRAINING else MODE_SAME
        val need = when {
            target == null -> null
            mode == MODE_TRAINING -> burned + goalKcal
            else -> target
        }
        return Day(date, bmr, daily.resting.formula, activity, exercises, exerciseKcal, burned, need, mode, goalKcal, steps, walked) to daily
    }
}
