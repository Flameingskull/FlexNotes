package com.fitlens.companion.data

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Who owns a workout row: imported from a FitNotes backup, or created (or edited) in FlexNotes. See [Workouts]. */
object Sources {
    const val FITNOTES = "fitnotes"
    const val FLEXNOTES = "fitlens"

    /** Body values read from Health Connect (#184): one total a day, replaced at each sync. */
    const val HEALTH_CONNECT = "health_connect"

    /**
     * The primary source rule (#184): of one measurement's values on one day, those that count. With Health Connect
     * as the primary source, its values when the day has any; otherwise FlexNotes's own (typed, the phone's step
     * counter, FitNotes), falling back to Health Connect's on a day with nothing else. Every value stays recorded and
     * visible in the Body tracker; only what's counted changes.
     */
    fun dayValues(day: List<MRecord>, hcPrimary: Boolean): List<MRecord> {
        val (hc, own) = day.partition { it.source == HEALTH_CONNECT }
        return if (hcPrimary) hc.ifEmpty { own } else own.ifEmpty { hc }
    }
}

data class Category(val id: Long, val name: String, val colour: Int, val sortOrder: Int, val source: String = Sources.FLEXNOTES) {
    val imported: Boolean get() = source == Sources.FITNOTES
}

/**
 * Exercise types (#14): which values a set records. They decide the set entry fields, the set columns (#101), the
 * graphs and the records.
 *
 * 0–3 are FitNotes's own type ids and are stored as FitNotes stores them, so imports and backups keep working. 4 and
 * up are FlexNotes's extra built-in types, such as weight and time for a loaded hold or carry. FitNotes never sends
 * them. [CUSTOM_BASE] and up are the user's own types ([CustomType], table `exercise_type`, #14), held in [custom]
 * by `Store` whenever the library is read, so every `uses*` check below answers for them too.
 */
object ExerciseTypes {
    const val WEIGHT_REPS = 0
    const val DISTANCE_TIME = 1
    const val WEIGHT_DISTANCE = 2
    const val TIME = 3
    const val WEIGHT_TIME = 4
    const val REPS_TIME = 5
    const val REPS_DISTANCE = 6
    const val WEIGHT_ONLY = 7
    const val REPS_ONLY = 8
    const val DISTANCE_ONLY = 9

    /** A training session (#240): a BJJ class, tennis, or a gym day's overall effort. One set is one session. */
    const val SESSION = 10

    /** The first id of a user-defined type (#14). Built-in ids stay below it, so the two can never collide. */
    const val CUSTOM_BASE = 100

    /** The user's own types by id, replaced by `Store` each time the library is read (#14). */
    @Volatile
    var custom: Map<Int, CustomType> = emptyMap()

    fun isCustom(type: Int): Boolean = type >= CUSTOM_BASE

    /** Every built-in type, in the order the type picker lists them: the two main types first. */
    val all = listOf(
        WEIGHT_REPS, DISTANCE_TIME, WEIGHT_TIME, WEIGHT_DISTANCE, REPS_TIME, REPS_DISTANCE,
        WEIGHT_ONLY, REPS_ONLY, DISTANCE_ONLY, TIME, SESSION
    )

    fun isSession(type: Int): Boolean = type == SESSION

    /**
     * A type's English name, or a custom type's own name. The screen shows `exerciseTypeText` (`ui/ModelText.kt`,
     * #156); this one keeps a custom type from taking a built-in type's name.
     */
    fun label(type: Int): String = when (type) {
        in custom -> custom.getValue(type).name
        DISTANCE_TIME -> "Distance & time"
        WEIGHT_DISTANCE -> "Weight & distance"
        TIME -> "Time"
        WEIGHT_TIME -> "Weight & time"
        REPS_TIME -> "Reps & time"
        REPS_DISTANCE -> "Reps & distance"
        WEIGHT_ONLY -> "Weight"
        REPS_ONLY -> "Reps"
        DISTANCE_ONLY -> "Distance"
        SESSION -> "Session"
        else -> "Weight & reps"
    }

    private val weightTypes = setOf(WEIGHT_REPS, WEIGHT_DISTANCE, WEIGHT_TIME, WEIGHT_ONLY)
    private val repTypes = setOf(WEIGHT_REPS, REPS_TIME, REPS_DISTANCE, REPS_ONLY)
    private val distanceTypes = setOf(DISTANCE_TIME, WEIGHT_DISTANCE, REPS_DISTANCE, DISTANCE_ONLY)
    private val timeTypes = setOf(DISTANCE_TIME, TIME, WEIGHT_TIME, REPS_TIME, SESSION)

    fun usesWeight(type: Int): Boolean = custom[type]?.weight ?: (type in weightTypes)
    fun usesReps(type: Int): Boolean = custom[type]?.reps ?: (type in repTypes)
    fun usesDistance(type: Int): Boolean = custom[type]?.distance ?: (type in distanceTypes)
    fun usesDuration(type: Int): Boolean = custom[type]?.time ?: (type in timeTypes)

    /** The user's own metric a custom type records (#14), or null when it records none (every built-in type). */
    fun metricOf(type: Int): CustomType? = custom[type]?.takeIf { it.metricName != null }

    /**
     * Whether an exercise's graphs and records are about time and distance rather than weight and reps. FitNotes's
     * own types keep their old rule (anything but weight and reps, once no set has a weight or reps), so existing
     * default graphs keep pointing at the same graph. FlexNotes's types decide from what they record.
     */
    fun timeBased(type: Int, anyWeightOrReps: Boolean): Boolean = when {
        type <= TIME -> type != WEIGHT_REPS && !anyWeightOrReps
        // A custom type has rep maxes only when it records both weight and reps (#14).
        isCustom(type) -> !(usesWeight(type) && usesReps(type))
        else -> !(usesWeight(type) && usesReps(type)) && (usesDuration(type) || usesDistance(type))
    }
}

/**
 * A user-defined exercise type (#14): a name, which of weight, reps, distance and time a set records, and optionally
 * one metric of the user's own with its unit ("Height", "cm"), stored in `workout_set.metric`. 1 to [MAX_VALUES]
 * values in all.
 */
data class CustomType(
    val id: Int,
    val name: String,
    val weight: Boolean,
    val reps: Boolean,
    val distance: Boolean,
    val time: Boolean,
    val metricName: String? = null,
    val metricUnit: String? = null
) {
    val valueCount: Int get() = listOf(weight, reps, distance, time, metricName != null).count { it }

    companion object {
        const val MAX_VALUES = 3
    }
}

/** An exercise in the library. [type] is one of [ExerciseTypes]. */
data class Exercise(
    val id: Long,
    val name: String,
    val categoryId: Long,
    val type: Int,
    val notes: String?,
    val source: String = Sources.FLEXNOTES,
    /** Starred in the exercise library, so it comes first in the pickers. */
    val favourite: Boolean = false,
    /** This exercise's + and − step in kg, or null for the global step (#15). */
    val weightStepKg: Double? = null,
    /** The graph the exercise opens on, as an index into its graph list, or -1 for the first (#15). */
    val defaultGraph: Int = -1,
    /** This exercise's rest length in seconds, or null for the global rest timer length (#15, #20). */
    val restSeconds: Int? = null,
    /** The unit this exercise's distances are logged in ([DistanceUnits]), or null for the global one (#7). */
    val distanceUnit: String? = null,
    /** The unit this exercise's weights are shown and typed in ("kg" or "lbs"), or null for the global one (#7). */
    val weightUnit: String? = null,
    /** Its own activity for its calories, an [Energy.Activity] key (#174), or null for automatic. */
    val activity: String? = null
) {
    val imported: Boolean get() = source == Sources.FITNOTES
}

data class SetRow(
    val id: Long,
    val exerciseId: Long,
    val date: String,
    val weightKg: Double,
    val reps: Int,
    val distance: Double,
    val durationSec: Int,
    val isPr: Boolean,
    val comment: String?,
    val source: String = Sources.FLEXNOTES,
    /** Working, warm-up, drop or failure ([SetTypes], #43). */
    val setType: Int = SetTypes.WORKING,
    /** Effort as RPE (1–10, half steps), or null when not recorded (#44). RIR is shown as 10 − RPE. */
    val rpe: Double? = null,
    /** The set's place in its day (#70): sets and exercises are shown in this order. */
    val position: Long = 0L,
    /** The superset (#18) its exercise belongs to on that day; 0 when it isn't in one. */
    val superset: Int = 0,
    /** Ticked off in "mark sets complete" mode (#19). */
    val done: Boolean = false,
    /** The rest prescribed after this set by the workout it was logged from (#138), or null for none. */
    val restSeconds: Int? = null,
    /** The value of its exercise's custom metric (#14), or null when it has none. */
    val metric: Double? = null,
    /** A Session's hard or live seconds (#240), at most [durationSec]; null when not recorded. */
    val liveSec: Int? = null,
    /** A Session's [SessionIntensity] id (#240), or null when unset. */
    val intensity: Int? = null,
    /** A Session's [SessionTypes] id (#240), or null when unset. */
    val sessionType: Int? = null,
    /** The cardio zone 1–5 the user trained in (#241), or null when not recorded. */
    val zone: Int? = null
) {
    val imported: Boolean get() = source == Sources.FITNOTES
    val isWarmup: Boolean get() = setType == SetTypes.WARMUP
}

/**
 * What kind of set a set is (#43). Stored as `workout_set.set_type`; imports and older rows are [WORKING]. Each type
 * has a one-letter badge, so the meaning never rests on colour alone.
 */
object SetTypes {
    const val WORKING = 0
    const val WARMUP = 1
    const val DROP = 2
    const val FAILURE = 3

    val all = listOf(WORKING, WARMUP, DROP, FAILURE)

    /** The badge letter, or null for a working set (which needs none). */
    fun badge(t: Int): String? = when (t) {
        WARMUP -> "W"
        DROP -> "D"
        FAILURE -> "F"
        else -> null
    }

    /** The CSV value (#31). */
    fun csv(t: Int): String = when (t) {
        WARMUP -> "warmup"
        DROP -> "drop"
        FAILURE -> "failure"
        else -> "working"
    }
}

/**
 * How intense a session was (#240): the stored ids never change meaning. [multiplier] scales the calories of a sport
 * (#244 uses it); until then it is only recorded.
 */
object SessionIntensity {
    const val TECHNICAL = 0
    const val CLASS = 1
    const val HARD_LIVE = 2
    const val FIGHT_PACE = 3

    val all = listOf(TECHNICAL, CLASS, HARD_LIVE, FIGHT_PACE)

    fun multiplier(id: Int): Double = when (id) {
        TECHNICAL -> 0.8
        HARD_LIVE -> 1.2
        FIGHT_PACE -> 1.4
        else -> 1.0
    }

    fun valid(id: Int?): Int? = id?.takeIf { it in all }
}

/** What kind of session it was (#240). Stored ids never change meaning. */
object SessionTypes {
    const val TECHNICAL = 0
    const val DRILLING = 1
    const val LIVE_SPARRING = 2
    const val CONDITIONING = 3
    const val MIXED = 4
    const val COMPETITION = 5
    const val STRENGTH = 6

    val all = listOf(TECHNICAL, DRILLING, LIVE_SPARRING, CONDITIONING, MIXED, COMPETITION, STRENGTH)

    fun valid(id: Int?): Int? = id?.takeIf { it in all }
}

/** The cardio zones 1–5 (#241), the user's own record of how hard a session was. */
object CardioZones {
    val all = listOf(1, 2, 3, 4, 5)

    /** The share of maximum heart rate each zone starts and ends at (percent), the conventional five bands. */
    fun percentRange(zone: Int): IntRange = when (zone) {
        1 -> 50..60
        2 -> 60..70
        3 -> 70..80
        4 -> 80..90
        else -> 90..100
    }

    fun valid(zone: Int?): Int? = zone?.takeIf { it in all }

    /** The Tanaka estimate of maximum heart rate for [age] (208 − 0.7 × age), rounded to a whole beat per minute. */
    fun tanakaMaxHr(age: Int): Int = Math.round(208.0 - 0.7 * age).toInt()

    /** The user's maximum heart rate in bpm range (#241), which [PortableSettings] checks on read. */
    val maxHrRange = 100..230

    /** A maximum heart rate, and whether it is [estimated] from the age rather than the user's own figure. */
    data class MaxHr(val bpm: Int, val estimated: Boolean)

    /**
     * The maximum heart rate cardio zones use on [onIso]: the profile's own figure first, else the Tanaka estimate
     * from the profile's date of birth (marked estimated), else null (#241). A figure outside [maxHrRange] is ignored.
     */
    fun maxHr(profileMaxHr: Int?, birthIso: String?, onIso: String): MaxHr? {
        profileMaxHr?.takeIf { it in maxHrRange }?.let { return MaxHr(it, false) }
        val age = Energy.age(birthIso, onIso) ?: return null
        return MaxHr(tanakaMaxHr(age).coerceIn(maxHrRange), true)
    }

    /** A bpm range for [zone] given a maximum heart rate. */
    fun bpmRange(zone: Int, maxHr: Int): IntRange {
        val p = percentRange(zone)
        return Math.round(maxHr * p.first / 100.0).toInt()..Math.round(maxHr * p.last / 100.0).toInt()
    }
}

/** Session load (#240): effort times minutes, the unit of training stress for a session. */
object SessionLoad {
    /** RPE (0–10) times minutes; null when the session has no RPE, so it doesn't count. */
    fun of(rpe: Double?, durationSec: Int): Double? = rpe?.let { it * durationSec / 60.0 }

    /** The hard minutes recorded, never more than the session's own length. */
    fun hardMinutes(liveSec: Int?, durationSec: Int): Double? = liveSec?.let { minOf(it, durationSec) / 60.0 }
}

/** Effort per set (#44): stored as RPE, entered and shown as RPE or RIR. */
object Effort {
    const val OFF = "off"
    const val RPE = "rpe"
    const val RIR = "rir"

    /** The RPE choices, in half steps. */
    val rpeSteps = listOf(6.0, 6.5, 7.0, 7.5, 8.0, 8.5, 9.0, 9.5, 10.0)

    /** The RIR choices; "5+" is stored as RPE 5. */
    val rirSteps = listOf(0, 1, 2, 3, 4, 5)

    fun rpeFromRir(rir: Int): Double = (10 - rir.coerceIn(0, 5)).toDouble()

    fun rirFromRpe(rpe: Double): Int = (10.0 - rpe).toInt().coerceIn(0, 5)
}

data class MeasurementDef(
    val name: String,
    val unit: String,
    val sortOrder: Int,
    val goalType: Int,
    val goalValue: Double,
    val enabled: Boolean,
    /** Created in FlexNotes rather than imported from FitNotes. */
    val custom: Boolean = false,
    /** For custom metrics: the FitNotes measurement whose values fill it in (null = match by name). */
    val link: String? = null,
    /**
     * The unit this measurement is shown in when it differs from the global weight or length unit (#7), or null.
     * Only a unit of the same kind applies; values stay stored in [unit].
     */
    val displayUnit: String? = null,
    /**
     * A medication (#199): shown only on the Medications screen, never in a body view, graph, estimate, export or
     * Health Connect write. Its values stay in the same table, so switching moves the whole history without a copy.
     */
    val medication: Boolean = false,
    /**
     * The reference range (#211): values below [refLow] or above [refHigh] are flagged Low or High. Either may be
     * missing (an upper limit only, say); stored in [unit] and converted with it when shown.
     */
    val refLow: Double? = null,
    val refHigh: Double? = null,
    /** A lab marker (#211): listed under LAB RESULTS on the Body tracker's Track tab. */
    val lab: Boolean = false
) {
    /** The range is set. */
    val hasRange: Boolean get() = refLow != null || refHigh != null

    /** Whether [value] is below the range (-1), above it (1) or in it or with no range (0). */
    fun rangeFlag(value: Double): Int = when {
        refLow != null && value < refLow - 1e-9 -> -1
        refHigh != null && value > refHigh + 1e-9 -> 1
        else -> 0
    }

    /** Lower-case FitNotes name this custom metric takes its values from. */
    val matchKey: String get() = (link?.takeIf { it.isNotBlank() } ?: name).trim().lowercase()
}

/**
 * A measurement's goal direction (#27), stored in `measurement.goal_type` as FitNotes does: none, increase, decrease
 * or a specific value (`goal_value`). Increase and decrease may also carry a target value.
 */
object MeasurementGoals {
    const val NONE = 0
    const val INCREASE = 1
    const val DECREASE = 2
    const val TARGET = 3

    val all = listOf(NONE, INCREASE, DECREASE, TARGET)

    /** Whether going from [from] to [to] moves towards the goal, or null when there's no goal or no change. */
    fun isImprovement(type: Int, target: Double, from: Double, to: Double): Boolean? {
        if (to == from) return null
        return when (type) {
            INCREASE -> to > from
            DECREASE -> to < from
            TARGET -> if (target <= 0) null else kotlin.math.abs(to - target) < kotlin.math.abs(from - target)
            else -> null
        }
    }
}

data class MRecord(
    val id: Long,
    val name: String,
    val unit: String,
    val date: String,
    val time: String,
    val value: Double,
    val comment: String?,
    val source: String,
    /** The scheduled dose it was taken for (#202), or null. */
    val doseId: Long? = null,
    /** When that dose was due, `yyyy-MM-dd HH:mm` (#202). */
    val due: String? = null,
    /** Where an injected dose went (#210): an `InjectionSites` key, or null. */
    val site: String? = null
)

object Poses {
    const val NONE = ""
    val all = listOf("Front", "Side", "Back", "Other")
}

object DateSources {
    const val EXIF = "exif"          // camera metadata (most reliable)
    const val EXIF_EDITED = "exif_edited" // EXIF DateTime: when the file was last changed (#162)
    const val MEDIA = "media"        // Android media library "date taken"
    const val FILENAME = "filename"  // parsed from e.g. IMG_20230826_132000.jpg
    const val FILE = "file"          // file modified time (least reliable)
    const val MANUAL = "manual"      // set by the user
    const val NONE = "none"

    /** Sources worth a second look by the user. */
    fun needsReview(s: String) = s == FILE || s == NONE || s == EXIF_EDITED
}

data class Photo(
    val id: Long,
    val file: String,
    val date: String?,
    val takenAt: String?,
    val dateSource: String,
    val pose: String,
    val note: String?,
    val originalName: String?
)

data class WorkoutTime(val date: String, val start: String, val end: String)

object Dates {
    val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val long = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault())
    private val medium = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
    private val short = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    private val weekday = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
    private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
    private val monthYearShort = DateTimeFormatter.ofPattern("MMM yy", Locale.getDefault())

    fun parse(s: String?): LocalDate? = try {
        if (s == null || s.length < 10) null else LocalDate.parse(s.substring(0, 10), ISO)
    } catch (e: Exception) {
        null
    }

    fun long(s: String): String = parse(s)?.format(long) ?: s
    fun medium(s: String): String = parse(s)?.format(medium) ?: s
    fun short(s: String): String = parse(s)?.format(short) ?: s
    /** The weekday and date people plan by, "Mon 27 Oct", with the year only when it isn't this year ("Mon 2 Nov 2027"). */
    fun day(s: String): String {
        val d = parse(s) ?: return s
        return if (d.year == LocalDate.now().year) d.format(weekday) else d.format(long)
    }
    fun monthYear(d: LocalDate): String = d.format(monthYear)
    fun monthYearShort(d: LocalDate): String = d.format(monthYearShort)
    fun epochDay(s: String): Long = parse(s)?.toEpochDay() ?: 0L
    fun today(): String = LocalDate.now().format(ISO)

    /**
     * Parses a workout start/finish stamp. FitNotes writes `yyyy-MM-dd HH:mm:ss` — a space separator and no zone —
     * which `OffsetDateTime.parse` rejects and `LocalDateTime.parse` only accepts with a `T`. Every caller wrapped
     * the failure in a catch that returned zero, so workout durations silently never appeared (#72).
     * Accepts either separator, and ignores a trailing offset if one is ever added.
     */
    fun dateTime(s: String?): LocalDateTime? {
        if (s.isNullOrBlank()) return null
        val t = s.trim().substringBefore('+').substringBefore('Z').trim().replace(' ', 'T')
        return try {
            LocalDateTime.parse(t)
        } catch (e: Exception) {
            null
        }
    }

    /** Seconds between two workout stamps, or 0 when either is missing or unparseable. */
    fun secondsBetween(start: String?, end: String?): Long {
        val s = dateTime(start) ?: return 0L
        val e = dateTime(end) ?: return 0L
        return maxOf(0L, Duration.between(s, e).seconds)
    }
}

fun fmtNum(v: Double, maxDecimals: Int = 2): String {
    val s = String.format(Locale.US, "%.${maxDecimals}f", v)
    return if (s.contains('.')) s.trimEnd('0').trimEnd('.') else s
}

fun fmtSigned(v: Double, maxDecimals: Int = 2): String =
    (if (v > 0) "+" else if (v < 0) "−" else "±") + fmtNum(kotlin.math.abs(v), maxDecimals)

fun fmtDuration(sec: Int): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return when {
        h > 0 -> String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else -> String.format(Locale.US, "%d:%02d", m, s)
    }
}

/**
 * Weight units for values that carry their own unit, such as body measurements (#117). Those keep the unit they were
 * stored in; everything shown or typed follows the weight unit setting, and is converted back before it's saved, so
 * switching between kg and lbs never changes or rounds what's stored.
 */
object WeightUnits {
    /** The international avoirdupois pound, exactly 0.45359237 kg by definition (1959), #139. */
    const val KG_PER_LB = 0.45359237
    /** Pounds per kilogram, from the exact definition rather than a rounded literal (#139). */
    const val LB_PER_KG = 1.0 / KG_PER_LB

    /** "kg" or "lbs" for a unit that's a weight (any common spelling), otherwise null. */
    fun of(unit: String?): String? = when (unit?.trim()?.lowercase()) {
        "kg", "kgs", "kilo", "kilos", "kilogram", "kilograms" -> "kg"
        "lb", "lbs", "pound", "pounds" -> "lbs"
        else -> null
    }

    /** [value] in [from] expressed in [to]; unchanged unless both are weights and differ. */
    fun convert(value: Double, from: String?, to: String?): Double {
        val f = of(from) ?: return value
        val t = of(to) ?: return value
        return when {
            f == t -> value
            t == "lbs" -> value * LB_PER_KG
            else -> value / LB_PER_KG
        }
    }

    /** A measurement as shown in [display]: its weight values and unit converted, anything else unchanged. */
    fun shown(d: MeasurementDef, display: String): MeasurementDef =
        if (of(d.unit) == null) d else d.copy(unit = display, goalValue = convert(d.goalValue, d.unit, display))

    fun shown(r: MRecord, display: String): MRecord =
        if (of(r.unit) == null) r else r.copy(unit = display, value = convert(r.value, r.unit, display))
}

/**
 * Distance units (#7). Distances are stored as typed, with no unit of their own: an exercise's distances are in its
 * own unit when it has one, otherwise in the global unit. Changing either relabels them; nothing is converted.
 */
object DistanceUnits {
    const val KM = "km"
    const val MI = "mi"
    const val M = "m"
    val ALL = listOf(KM, MI, M)

    /** A known unit, or null. */
    fun of(unit: String?): String? = unit?.trim()?.lowercase()?.takeIf { it in ALL }

    /** Metres in one [unit] (km, mi or m), or null for another. */
    fun metresPer(unit: String?): Double? = when (of(unit)) {
        KM -> 1000.0
        MI -> 1609.344
        M -> 1.0
        else -> null
    }

    /**
     * [metres] in [unit], to six decimal places so a conversion doesn't leave float noise (5 mi is 8.04672 km). An
     * unknown unit is taken as km, the default.
     */
    fun fromMetres(metres: Double, unit: String?): Double =
        Math.round(metres / (metresPer(unit) ?: 1000.0) * 1_000_000.0) / 1_000_000.0
}

/** Length units for body measurements (#7): values keep the unit they were logged in and are shown in the user's. */
object LengthUnits {
    const val CM = "cm"
    const val IN = "in"
    const val CM_PER_IN = 2.54

    /** "cm" or "in" for a unit that's a length (any common spelling), otherwise null. */
    fun of(unit: String?): String? = when (unit?.trim()?.lowercase()) {
        "cm", "cms", "centimetre", "centimetres", "centimeter", "centimeters" -> CM
        "in", "ins", "inch", "inches", "\"" -> IN
        else -> null
    }

    fun convert(value: Double, from: String?, to: String?): Double {
        val f = of(from) ?: return value
        val t = of(to) ?: return value
        return when {
            f == t -> value
            t == IN -> value / CM_PER_IN
            else -> value * CM_PER_IN
        }
    }
}

/**
 * Body measurement units (#117, #7): a weight is shown in the weight unit and a length in the length unit; any other
 * unit (%, bpm, a custom one) is left alone. Values are stored in the unit they were logged in.
 */
object MeasureUnits {
    /**
     * The unit [unit] is shown in, given the user's weight and length units, or the measurement's own [override] when
     * it's the same kind (#7).
     */
    fun display(unit: String?, weightUnit: String, lengthUnit: String, override: String? = null): String? = when {
        override != null && sameKind(unit, override) -> WeightUnits.of(override) ?: LengthUnits.of(override) ?: VitalUnits.of(override)
        WeightUnits.of(unit) != null -> weightUnit
        LengthUnits.of(unit) != null -> lengthUnit
        else -> unit
    }

    /**
     * The units a measurement stored in [unit] can be shown in: kg and lbs, cm and in, mmol/L and mg/dL, °C and °F
     * (#212), or none for other units.
     */
    fun choices(unit: String?): List<String> = when {
        WeightUnits.of(unit) != null -> listOf("kg", "lbs")
        LengthUnits.of(unit) != null -> listOf(LengthUnits.CM, LengthUnits.IN)
        else -> VitalUnits.choices(unit)
    }

    /** A weight, a length, a blood glucose or a temperature: a unit FlexNotes converts to another of its kind. */
    fun convertible(unit: String?): Boolean = WeightUnits.of(unit) != null || LengthUnits.of(unit) != null || VitalUnits.of(unit) != null

    /** Both are weights, both lengths, or both the same kind of vital unit (#212), so one converts to the other. */
    fun sameKind(a: String?, b: String?): Boolean =
        (WeightUnits.of(a) != null && WeightUnits.of(b) != null) || (LengthUnits.of(a) != null && LengthUnits.of(b) != null) ||
            VitalUnits.sameKind(a, b)

    /** [value] in [from] expressed in [to]; unchanged unless they're the same kind and differ. */
    fun convert(value: Double, from: String?, to: String?): Double = when {
        WeightUnits.of(from) != null -> WeightUnits.convert(value, from, to)
        VitalUnits.of(from) != null -> VitalUnits.convert(value, from, to)
        else -> LengthUnits.convert(value, from, to)
    }

    fun shown(d: MeasurementDef, weightUnit: String, lengthUnit: String): MeasurementDef {
        val to = display(d.unit, weightUnit, lengthUnit, d.displayUnit) ?: return d
        return if (to == d.unit) d else d.copy(
            unit = to, goalValue = convert(d.goalValue, d.unit, to),
            refLow = d.refLow?.let { convert(it, d.unit, to) }, refHigh = d.refHigh?.let { convert(it, d.unit, to) }
        )
    }

    fun shown(r: MRecord, weightUnit: String, lengthUnit: String, override: String? = null): MRecord {
        val to = display(r.unit, weightUnit, lengthUnit, override) ?: return r
        return if (to == r.unit) r else r.copy(unit = to, value = convert(r.value, r.unit, to))
    }
}

/**
 * Blood glucose and body temperature units (#212), converted like weights and lengths but never by a global unit:
 * only a measurement's own display unit ([MeasurementDef.displayUnit]) shows them in the other one. Plain Kotlin,
 * tested in `VitalUnitsTest`.
 */
object VitalUnits {
    const val MMOL = "mmol/L"
    const val MGDL = "mg/dL"
    const val CELSIUS = "°C"
    const val FAHRENHEIT = "°F"

    /** mg/dL in one mmol/L of glucose (its molar mass, 180.16 g/mol, over 10). */
    const val MGDL_PER_MMOL = 18.016

    fun glucose(unit: String?): String? = when (unit?.trim()?.lowercase()) {
        "mmol/l", "mmol" -> MMOL
        "mg/dl" -> MGDL
        else -> null
    }

    fun temperature(unit: String?): String? = when (unit?.trim()?.lowercase()?.removePrefix("°")?.trim()) {
        "c", "celsius" -> CELSIUS
        "f", "fahrenheit" -> FAHRENHEIT
        else -> null
    }

    /** The canonical name of a glucose or temperature unit, or null for any other. */
    fun of(unit: String?): String? = glucose(unit) ?: temperature(unit)

    fun sameKind(a: String?, b: String?): Boolean =
        (glucose(a) != null && glucose(b) != null) || (temperature(a) != null && temperature(b) != null)

    fun choices(unit: String?): List<String> = when {
        glucose(unit) != null -> listOf(MMOL, MGDL)
        temperature(unit) != null -> listOf(CELSIUS, FAHRENHEIT)
        else -> emptyList()
    }

    /** [value] in [from] expressed in [to]; unchanged unless both are glucose or both temperature units and differ. */
    fun convert(value: Double, from: String?, to: String?): Double {
        if (!sameKind(from, to)) return value
        return when (of(from) to of(to)) {
            MMOL to MGDL -> value * MGDL_PER_MMOL
            MGDL to MMOL -> value / MGDL_PER_MMOL
            CELSIUS to FAHRENHEIT -> value * 9.0 / 5.0 + 32.0
            FAHRENHEIT to CELSIUS -> (value - 32.0) * 5.0 / 9.0
            else -> value
        }
    }
}

/**
 * The rest a logged workout prescribes for one exercise on one date (#138), copied from the workout day when it was
 * logged, so it still applies after the workout is edited: [restSeconds] between its sets (when a set has none of its
 * own) and [restAfterSeconds] after its last set, before the next exercise. Null means not prescribed.
 */
data class WorkoutRest(val restSeconds: Int? = null, val restAfterSeconds: Int? = null) {
    val isEmpty: Boolean get() = restSeconds == null && restAfterSeconds == null
}
