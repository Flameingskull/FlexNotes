package com.fitlens.companion.data

import java.time.LocalDate
import java.time.Period
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Energy: resting and daily calories, exercise calories, goals and macros (#172, #173, #174). Plain Kotlin, unit
 * tested (EnergyTest). Every figure is an estimate: resting energy equations are typically within about 10 % for a
 * person, so screens say so and show the inputs used.
 *
 * - **Resting energy (BMR):** Mifflin-St Jeor (1990), the most accurate equation for the general population in the
 *   American Dietetic Association's 2005 review: 10 × kg + 6.25 × cm − 5 × age + 5 (men) or − 161 (women). With a
 *   recent body fat value, Katch-McArdle (370 + 21.6 × lean kg) is used instead, being more accurate for lean and
 *   trained people.
 * - **Daily activity outside training** ([Lifestyle]): a factor on BMR that includes digestion. Or, from the phone's
 *   step counter (#175), the Sitting factor plus the walking in the steps above [STEP_BASELINE] ([stepKcal]).
 * - **Exercise:** net calories only, (MET − 1) × kg × hours, since resting energy is already counted. METs from the
 *   2024 Adult Compendium of Physical Activities. Running and walking with a distance and time use the ACSM metabolic
 *   equations (VO₂ from speed, 5 kcal per litre of oxygen), which beat a flat MET.
 * - **Goals** ([Goal]) and **macros** ([macros]) turn the daily total into a target.
 */
object Energy {
    /** Kilocalories in a kilogram of body weight change, the usual planning figure. */
    const val KCAL_PER_KG = 7700.0

    /** A body fat value older than this isn't used for Katch-McArdle, as for the body fat calculator. */
    const val BODY_FAT_DAYS = BodyFat.STALE_DAYS

    /** The usual error of a resting energy estimate for one person, in percent. */
    const val TYPICAL_ERROR_PERCENT = 10

    /** Daily activity outside training, as a factor on BMR (#172). */
    enum class Lifestyle(val key: String, val factor: Double) {
        SITTING("sitting", 1.2),
        SOME_WALKING("some_walking", 1.35),
        ON_FEET("on_feet", 1.5),
        PHYSICAL("physical", 1.7),
        HEAVY("heavy", 1.9);

        companion object {
            fun of(key: String?): Lifestyle? = entries.firstOrNull { it.key == key }
        }
    }

    /** Which resting energy equation to use (#173). */
    enum class Formula(val key: String) {
        AUTO("auto"), MIFFLIN("mifflin"), KATCH("katch");

        companion object {
            fun of(key: String?): Formula = entries.firstOrNull { it.key == key } ?: AUTO
        }
    }

    /** How hard a strength session was, with its Compendium MET (resistance training, 2024: 3.5, 5.0, 6.0). */
    enum class Effort(val key: String, val met: Double) {
        LIGHT("light", 3.5), MODERATE("moderate", 5.0), VIGOROUS("vigorous", 6.0);

        companion object {
            fun of(key: String?): Effort = entries.firstOrNull { it.key == key } ?: MODERATE

            /** From a session's average RPE: up to 6 is light, up to 8 moderate, above that vigorous. */
            fun fromRpe(rpe: Double?): Effort = when {
                rpe == null -> MODERATE
                rpe <= 6.0 -> LIGHT
                rpe <= 8.0 -> MODERATE
                else -> VIGOROUS
            }
        }
    }

    /**
     * An exercise's own activity (#174), with its 2024 Adult Compendium MET, chosen in the exercise editor when the
     * automatic estimate doesn't fit. Stored by its key, so values that share a MET stay apart.
     */
    enum class Activity(val key: String, val met: Double) {
        STRENGTH_LIGHT("strength_light", 3.5),
        STRENGTH_MODERATE("strength_moderate", 5.0),
        STRENGTH_VIGOROUS("strength_vigorous", 6.0),
        CIRCUIT("circuit", 8.0),
        CALISTHENICS("calisthenics", 3.8),
        HIIT("hiit", 8.0),
        WALKING("walking", 3.5),
        RUNNING("running", 9.8),
        CYCLING("cycling", 6.8),
        ROWING("rowing", 7.0),
        ELLIPTICAL("elliptical", 5.0),
        STAIRS("stairs", 9.0),
        SWIMMING("swimming", 7.0),
        SPORTS("sports", 6.0),
        YOGA("yoga", 2.8),
        STRETCHING("stretching", 2.3);

        companion object {
            fun of(key: String?): Activity? = entries.firstOrNull { it.key == key }
        }
    }

    // ---------- Resting energy ----------

    /** Mifflin-St Jeor resting energy in kcal a day. */
    fun mifflin(sex: BodyFat.Sex, kg: Double, cm: Double, age: Int): Double =
        10.0 * kg + 6.25 * cm - 5.0 * age + if (sex == BodyFat.Sex.MALE) 5.0 else -161.0

    /** Katch-McArdle resting energy in kcal a day, from lean (fat-free) mass in kg. */
    fun katch(leanKg: Double): Double = 370.0 + 21.6 * leanKg

    /** Whole years between [birthIso] and [onIso], or null when either can't be read or the birth is later. */
    fun age(birthIso: String?, onIso: String): Int? {
        val birth = birthIso?.let { Dates.parse(it) } ?: return null
        val on = Dates.parse(onIso) ?: return null
        if (birth.isAfter(on)) return null
        return Period.between(birth, on).years
    }

    /** The inputs a resting energy estimate needs; null ones are named on screen, never guessed. */
    data class Inputs(
        val sex: BodyFat.Sex?,
        val age: Int?,
        val heightCm: Double?,
        val kg: Double?,
        /** Body fat percent logged within [BODY_FAT_DAYS], or null. */
        val bodyFat: Double?,
        val lifestyle: Lifestyle?
    )

    /** What a resting energy estimate is missing. */
    enum class Missing { SEX, AGE, HEIGHT, BODYWEIGHT, LIFESTYLE }

    /** A resting energy estimate: its value, the equation used, and anything missing (then [bmr] is null). */
    data class Resting(val bmr: Double?, val formula: Formula, val missing: List<Missing>)

    /**
     * Resting energy from [inputs] with [choice]: Automatic uses Katch-McArdle when a recent body fat value is known,
     * otherwise Mifflin-St Jeor. Katch-McArdle needs only bodyweight and body fat.
     */
    fun resting(inputs: Inputs, choice: Formula): Resting {
        val katch = choice == Formula.KATCH || (choice == Formula.AUTO && inputs.bodyFat != null)
        if (katch && inputs.bodyFat != null) {
            val kg = inputs.kg ?: return Resting(null, Formula.KATCH, listOf(Missing.BODYWEIGHT))
            return Resting(katch(kg * (1 - inputs.bodyFat / 100.0)), Formula.KATCH, emptyList())
        }
        val missing = listOfNotNull(
            if (inputs.sex == null) Missing.SEX else null,
            if (inputs.age == null) Missing.AGE else null,
            if (inputs.heightCm == null) Missing.HEIGHT else null,
            if (inputs.kg == null) Missing.BODYWEIGHT else null
        )
        if (missing.isNotEmpty()) return Resting(null, Formula.MIFFLIN, missing)
        return Resting(mifflin(inputs.sex!!, inputs.kg!!, inputs.heightCm!!, inputs.age!!), Formula.MIFFLIN, emptyList())
    }

    // ---------- Steps (#175) ----------

    /** Steps a day the Sitting level already allows for (moving about at home or work); only steps above it add. */
    const val STEP_BASELINE = 2500

    /** A stride in metres: about 0.415 × height for men and 0.413 × height for women, or 0.75 m without a height. */
    fun strideMetres(heightCm: Double?, sex: BodyFat.Sex?): Double =
        heightCm?.takeIf { it > 0 }?.let { it / 100.0 * if (sex == BodyFat.Sex.FEMALE) 0.413 else 0.415 } ?: 0.75

    /**
     * Net kcal for [steps] in a day at [kg]: the steps above [STEP_BASELINE], walked at an easy pace on the flat (ACSM
     * walking: 0.1 ml of oxygen per kg per metre, 5 kcal per litre).
     */
    fun stepKcal(steps: Double, kg: Double, heightCm: Double?, sex: BodyFat.Sex?): Double {
        if (kg <= 0) return 0.0
        val metres = max(0.0, steps - STEP_BASELINE) * strideMetres(heightCm, sex)
        return 0.1 * metres * kg / 1000.0 * 5.0
    }

    /** A running stride is about this many times a walking one, at an easy running pace (#181). */
    const val RUN_STRIDE_FACTOR = 1.4

    /**
     * The steps a logged walk or run took (#181), which the phone counted too when it was carried: its distance over
     * the stride, a running stride being [RUN_STRIDE_FACTOR] times as long. [activity] is the exercise's own activity;
     * without one, a cardio bout with a distance and a time at walking or running speed counts. 0 for anything else.
     */
    fun walkedSteps(b: Bout, activity: Activity?, heightCm: Double?, sex: BodyFat.Sex?): Double {
        if (b.metres <= 0) return 0.0
        val speed = if (b.seconds > 0) b.metres / (b.seconds / 60.0) else null
        val running = when (activity) {
            Activity.WALKING -> false
            Activity.RUNNING -> true
            null -> {
                if (!b.cardio || speed == null || speed > RUN_MAX_M_PER_MIN) return 0.0
                speed > WALK_MAX_M_PER_MIN
            }
            else -> return 0.0
        }
        return b.metres / (strideMetres(heightCm, sex) * if (running) RUN_STRIDE_FACTOR else 1.0)
    }

    // ---------- Exercise ----------

    /** Net kcal for [met] over [hours] at [kg]: resting energy is already counted, so one MET comes off. */
    fun netMetKcal(met: Double, kg: Double, hours: Double): Double = max(0.0, met - 1.0) * kg * max(0.0, hours)

    /** Walking speeds up to this (metres a minute, 6 km/h) use the ACSM walking equation; faster is running. */
    const val WALK_MAX_M_PER_MIN = 100.0

    /** Faster than this (20 km/h) is taken as cycling, by the Compendium's speed bands. */
    const val RUN_MAX_M_PER_MIN = 333.0

    /**
     * Net kcal for walking or running [metres] in [seconds] on the flat (ACSM metabolic equations): walking
     * VO₂ = 0.1 × speed + 3.5, running VO₂ = 0.2 × speed + 3.5 (ml/kg/min, speed in m/min); the 3.5 resting part comes
     * off, and each litre of oxygen is 5 kcal.
     */
    fun acsmNetKcal(metres: Double, seconds: Double, kg: Double): Double {
        if (metres <= 0 || seconds <= 0 || kg <= 0) return 0.0
        val minutes = seconds / 60.0
        val speed = metres / minutes
        val netVo2 = if (speed <= WALK_MAX_M_PER_MIN) 0.1 * speed else 0.2 * speed
        return netVo2 * kg * minutes / 1000.0 * 5.0
    }

    /** Cycling METs by speed, from the Compendium: under 16 km/h 4.0, 16–19 6.8, 19–22 8.0, 22–25.5 10.0, faster 12.0. */
    fun cyclingMet(kmPerHour: Double): Double = when {
        kmPerHour < 16 -> 4.0
        kmPerHour < 19 -> 6.8
        kmPerHour < 22 -> 8.0
        kmPerHour < 25.5 -> 10.0
        else -> 12.0
    }

    /** General cardio with no distance, by effort (Compendium aerobic and conditioning: 4.0, 6.0, 8.0). */
    fun cardioMet(effort: Effort): Double = when (effort) {
        Effort.LIGHT -> 4.0
        Effort.MODERATE -> 6.0
        Effort.VIGOROUS -> 8.0
    }

    /** One exercise's part of a day, as the estimate needs it. */
    data class Bout(
        val exerciseId: Long,
        /** Strength (sets with rests) or cardio (a distance and/or a time). */
        val cardio: Boolean,
        /** How long it took, rests included for strength, in seconds. */
        val seconds: Double,
        val metres: Double = 0.0,
        val effort: Effort = Effort.MODERATE,
        /** The exercise's own MET when the user chose an activity for it (#174), else null for automatic. */
        val met: Double? = null,
        val sets: Int = 0
    )

    /** How a bout's calories were worked out, for the breakdown. */
    enum class Method { MET, ACSM_WALK, ACSM_RUN, CYCLING }

    /** A bout's net calories and how they were found. */
    data class BoutKcal(val bout: Bout, val kcal: Double, val method: Method, val met: Double?)

    /** Net kcal for [b] at [kg]. */
    fun boutKcal(b: Bout, kg: Double): BoutKcal {
        val hours = b.seconds / 3600.0
        b.met?.let { return BoutKcal(b, netMetKcal(it, kg, hours), Method.MET, it) }
        if (!b.cardio) {
            val met = b.effort.met
            return BoutKcal(b, netMetKcal(met, kg, hours), Method.MET, met)
        }
        if (b.metres > 0 && b.seconds > 0) {
            val speed = b.metres / (b.seconds / 60.0)
            return when {
                speed <= WALK_MAX_M_PER_MIN -> BoutKcal(b, acsmNetKcal(b.metres, b.seconds, kg), Method.ACSM_WALK, null)
                speed <= RUN_MAX_M_PER_MIN -> BoutKcal(b, acsmNetKcal(b.metres, b.seconds, kg), Method.ACSM_RUN, null)
                else -> {
                    val met = cyclingMet(speed * 60.0 / 1000.0)
                    BoutKcal(b, netMetKcal(met, kg, hours), Method.CYCLING, met)
                }
            }
        }
        val met = cardioMet(b.effort)
        return BoutKcal(b, netMetKcal(met, kg, hours), Method.MET, met)
    }

    /** About how long one strength set takes to do, before its rest, when no time was logged. */
    const val STRENGTH_SET_SECONDS = 40.0

    /**
     * The effort a cardio zone stands for when a session has no intensity (#241): zones 1 and 2 are light, 3 moderate,
     * 4 and 5 vigorous, as the RPE [Effort.fromRpe] reads them.
     */
    fun zoneRpe(zone: Int): Double = when {
        zone <= 2 -> 5.0
        zone == 3 -> 7.0
        else -> 9.0
    }

    /** One logged set as the estimate needs it. */
    data class SetInput(
        val exerciseId: Long,
        val cardio: Boolean,
        val metres: Double,
        val seconds: Double,
        val rpe: Double?,
        /** The rest after it ([restFor] in the UI), in seconds. */
        val restSeconds: Double
    )

    /**
     * A day's bouts from its sets (#174). With a logged workout time ([workoutSeconds] > 0), the strength exercises
     * share what the cardio didn't use, by their set counts; otherwise each strength set counts
     * [STRENGTH_SET_SECONDS] (or its logged time) plus its rest. Cardio counts its own logged time.
     */
    fun bouts(sets: List<SetInput>, workoutSeconds: Double, mets: Map<Long, Double> = emptyMap()): List<Bout> {
        val byExercise = sets.groupBy { it.exerciseId }
        val cardioTime = sets.filter { it.cardio }.sumOf { it.seconds }
        val strengthSets = sets.count { !it.cardio }
        val shared = if (workoutSeconds > 0 && strengthSets > 0) max(0.0, workoutSeconds - cardioTime) else 0.0
        return byExercise.map { (ex, l) ->
            val cardio = l.all { it.cardio }
            val rpes = l.mapNotNull { it.rpe }
            val effort = Effort.fromRpe(if (rpes.isEmpty()) null else rpes.average())
            val seconds = when {
                cardio -> l.sumOf { it.seconds }
                shared > 0 -> shared * l.size / strengthSets
                else -> l.sumOf { (if (it.seconds > 0) it.seconds else STRENGTH_SET_SECONDS) + it.restSeconds }
            }
            Bout(ex, cardio, seconds, l.sumOf { it.metres }, effort, mets[ex], l.size)
        }
    }

    // ---------- Goals ----------

    /** A calorie goal (#173), stored as text by [encode] and read back by [decode]. */
    sealed class Goal {
        object Maintain : Goal()
        /** Lose [percentPerWeek] of bodyweight a week (0.25 to 1). */
        data class LoseRate(val percentPerWeek: Double) : Goal()
        /** Eat [percent] under the daily total (10, 20 or 25). */
        data class LoseDeficit(val percent: Int) : Goal()
        /** A 10 % deficit with high protein. */
        object Recomp : Goal()
        /** 5 to 10 % over (7.5 % used). */
        object LeanBulk : Goal()
        /** 15 to 20 % over (17.5 % used). */
        object Bulk : Goal()
        data class Custom(val kcal: Int) : Goal()

        fun encode(): String = when (this) {
            Maintain -> "maintain"
            is LoseRate -> "rate:" + fmtNum(percentPerWeek, 2)
            is LoseDeficit -> "deficit:$percent"
            Recomp -> "recomp"
            LeanBulk -> "lean"
            Bulk -> "bulk"
            is Custom -> "custom:$kcal"
        }

        /** In a deficit, so protein sits at the top of its range. */
        val isDeficit: Boolean get() = this is LoseRate || this is LoseDeficit || this == Recomp

        companion object {
            val RATES = listOf(0.25, 0.5, 0.75, 1.0)
            val DEFICITS = listOf(10, 20, 25)

            fun decode(s: String?): Goal? {
                val t = s?.trim() ?: return null
                val arg = t.substringAfter(':', "")
                return when (t.substringBefore(':')) {
                    "maintain" -> Maintain
                    "rate" -> arg.toDoubleOrNull()?.takeIf { it > 0 && it <= 2 }?.let { LoseRate(it) }
                    "deficit" -> arg.toIntOrNull()?.takeIf { it in 1..50 }?.let { LoseDeficit(it) }
                    "recomp" -> Recomp
                    "lean" -> LeanBulk
                    "bulk" -> Bulk
                    "custom" -> arg.toIntOrNull()?.takeIf { it in 500..10000 }?.let { Custom(it) }
                    else -> null
                }
            }
        }
    }

    /** The daily target for [goal] from the daily total [tdee] and bodyweight [kg]. */
    fun target(goal: Goal, tdee: Double, kg: Double): Double = when (goal) {
        Goal.Maintain -> tdee
        is Goal.LoseRate -> tdee - kg * goal.percentPerWeek / 100.0 * KCAL_PER_KG / 7.0
        is Goal.LoseDeficit -> tdee * (1 - goal.percent / 100.0)
        Goal.Recomp -> tdee * 0.90
        Goal.LeanBulk -> tdee * 1.075
        Goal.Bulk -> tdee * 1.175
        is Goal.Custom -> goal.kcal.toDouble()
    }

    /** The expected change in bodyweight a week, in kg, eating [target] against [tdee]. */
    fun weeklyChangeKg(target: Double, tdee: Double): Double = (target - tdee) * 7.0 / KCAL_PER_KG

    /** Why a target needs care. */
    enum class Warning { BELOW_BMR, BELOW_FLOOR, FAST_LOSS }

    /** The usual lowest daily intake without medical supervision: 1200 kcal for women, 1500 for men. */
    fun floorKcal(sex: BodyFat.Sex?): Int = if (sex == BodyFat.Sex.FEMALE) 1200 else 1500

    /** Warnings for eating [target] with resting energy [bmr], daily total [tdee] and bodyweight [kg]. */
    fun warnings(target: Double, bmr: Double, tdee: Double, kg: Double, sex: BodyFat.Sex?): List<Warning> = listOfNotNull(
        if (target < bmr) Warning.BELOW_BMR else null,
        if (target < floorKcal(sex)) Warning.BELOW_FLOOR else null,
        if (kg > 0 && -weeklyChangeKg(target, tdee) / kg > 0.01 + 1e-9) Warning.FAST_LOSS else null
    )

    // ---------- Macros ----------

    /** Daily protein, fat and carbohydrate in grams; [kcal] is their total. */
    data class Macros(val proteinG: Int, val fatG: Int, val carbG: Int) {
        val kcal: Int get() = proteinG * 4 + fatG * 9 + carbG * 4
    }

    /** Protein in g per kg: the top of 1.6–2.2 in a deficit or recomposition, the middle when maintaining, else 1.6. */
    fun proteinPerKg(goal: Goal): Double = when {
        goal.isDeficit -> 2.2
        goal == Goal.Maintain -> 1.8
        else -> 1.6
    }

    /**
     * Macros for [kcal] at [kg]: protein by [proteinPerKg], fat at least 0.8 g per kg and 20 % of calories, and
     * carbohydrate the rest (never below zero).
     */
    fun macros(kcal: Double, kg: Double, goal: Goal): Macros {
        val protein = (kg * proteinPerKg(goal)).roundToInt()
        val fat = max(kg * 0.8, kcal * 0.20 / 9.0).roundToInt()
        val carbs = max(0.0, (kcal - protein * 4 - fat * 9) / 4.0).roundToInt()
        return Macros(protein, fat, carbs)
    }

    // ---------- Planned training ----------

    /** A planned week of training when fewer than [MIN_LOGGED_DAYS] days are logged: sessions, minutes, effort. */
    data class Plan(val sessions: Int, val minutes: Int, val effort: Effort) {
        fun encode(): String = "$sessions,$minutes,${effort.key}"

        /** Net kcal a day, averaged over the week, for strength sessions at [kg]. */
        fun dailyKcal(kg: Double): Double = netMetKcal(effort.met, kg, sessions * minutes / 60.0) / 7.0

        companion object {
            val DEFAULT = Plan(3, 60, Effort.MODERATE)

            fun decode(s: String?): Plan? {
                val p = s?.split(',') ?: return null
                if (p.size != 3) return null
                val n = p[0].toIntOrNull()?.takeIf { it in 0..14 } ?: return null
                val m = p[1].toIntOrNull()?.takeIf { it in 0..300 } ?: return null
                return Plan(n, m, Effort.of(p[2]))
            }
        }
    }

    /** Days of logs the training average needs before it's trusted over the plan. */
    const val MIN_LOGGED_DAYS = 14

    /** The days the training average looks back over. */
    const val AVERAGE_DAYS = 28

    /** The first day of the [AVERAGE_DAYS] ending on [date] (inclusive), as an ISO date. */
    fun averageStart(date: String): String =
        (Dates.parse(date) ?: LocalDate.now()).minusDays((AVERAGE_DAYS - 1).toLong()).format(Dates.ISO)
}
