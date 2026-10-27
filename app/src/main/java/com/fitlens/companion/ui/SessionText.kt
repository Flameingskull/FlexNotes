package com.fitlens.companion.ui

import android.content.res.Resources
import com.fitlens.companion.R
import com.fitlens.companion.data.CardioZones
import com.fitlens.companion.data.ExerciseTypes
import com.fitlens.companion.data.Rounds
import com.fitlens.companion.data.SessionIntensity
import com.fitlens.companion.data.SessionTypes
import com.fitlens.companion.data.SetRow
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.fmtNum

/**
 * The words for a Session's stored values (#240, #241). The values stay as stored (ids that never change meaning);
 * only their names come from strings.xml.
 */

/** Is [exerciseId] a Session or a Rounds exercise (#246)? Both are timed training that counts toward load and calories. */
internal fun Snapshot.isSessionExercise(exerciseId: Long): Boolean = ExerciseTypes.isTimedSession(exercises[exerciseId]?.type ?: -1)

/** Is [exerciseId] a Rounds exercise (#246)? */
internal fun Snapshot.isRoundsExercise(exerciseId: Long): Boolean = ExerciseTypes.isRounds(exercises[exerciseId]?.type ?: -1)

/** A round length or rest as typed: minutes ("3", "2.5", "2,5") or "m:ss" ("2:30"); blank or unreadable is 0. */
internal fun roundsTextToSeconds(text: String): Int {
    val t = text.trim()
    if (t.contains(':')) {
        val parts = t.split(':').map { it.trim().toIntOrNull() ?: return 0 }
        return when (parts.size) {
            2 -> (parts[0] * 60 + parts[1]).coerceAtLeast(0)
            else -> 0
        }
    }
    return minutesToSeconds(t)
}

/** The rest between rounds as typed: seconds ("60") or "m:ss" ("1:30"); blank or unreadable is 0. */
internal fun roundsRestToSeconds(text: String): Int {
    val t = text.trim()
    if (t.contains(':')) return roundsTextToSeconds(t)
    return (t.toIntOrNull() ?: 0).coerceAtLeast(0)
}

/** Seconds as shown in a round length or rest field: "3", "2.5" minutes, or "1:20" when that isn't a tidy decimal. */
internal fun roundsSecondsToText(sec: Int): String =
    if (sec <= 0) "" else if (sec % 30 == 0) minutesText(sec) else "${sec / 60}:${(sec % 60).toString().padStart(2, '0')}"

/** A session's length in minutes as typed and shown: "90", or "7.5". */
internal fun minutesText(sec: Int): String = fmtNum(sec / 60.0, 1)

/** Minutes as typed ("90", "7,5") to whole seconds; blank or unreadable is 0. */
internal fun minutesToSeconds(text: String): Int =
    Math.round((text.trim().replace(',', '.').toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0) * 60.0).toInt()

internal fun sessionIntensityName(res: Resources, id: Int): String = res.getString(
    when (id) {
        SessionIntensity.TECHNICAL -> R.string.session_int_technical
        SessionIntensity.HARD_LIVE -> R.string.session_int_hard_live
        SessionIntensity.FIGHT_PACE -> R.string.session_int_fight_pace
        else -> R.string.session_int_class
    }
)

/** The intensity with a few plain words on what it means, for the picker. */
internal fun sessionIntensityOption(res: Resources, id: Int): String = res.getString(
    R.string.session_option,
    sessionIntensityName(res, id),
    res.getString(
        when (id) {
            SessionIntensity.TECHNICAL -> R.string.session_int_technical_desc
            SessionIntensity.HARD_LIVE -> R.string.session_int_hard_live_desc
            SessionIntensity.FIGHT_PACE -> R.string.session_int_fight_pace_desc
            else -> R.string.session_int_class_desc
        }
    )
)

internal fun sessionTypeText(res: Resources, id: Int): String = res.getString(
    when (id) {
        SessionTypes.DRILLING -> R.string.session_type_drilling
        SessionTypes.LIVE_SPARRING -> R.string.session_type_live
        SessionTypes.CONDITIONING -> R.string.session_type_conditioning
        SessionTypes.MIXED -> R.string.session_type_mixed
        SessionTypes.COMPETITION -> R.string.session_type_competition
        SessionTypes.STRENGTH -> R.string.session_type_strength
        else -> R.string.session_type_technical
    }
)

internal fun zoneText(res: Resources, zone: Int): String = res.getString(R.string.session_zone_n, zone)

/** The plain word for a session rating on the 0–10 scale (CR-10): 0 Rest up to 10 Maximal. */
internal fun sessionRpeWord(res: Resources, rpe: Double): String = res.getString(
    when {
        rpe < 0.5 -> R.string.session_rpe_0
        rpe < 1.5 -> R.string.session_rpe_1
        rpe < 2.5 -> R.string.session_rpe_2
        rpe < 3.5 -> R.string.session_rpe_3
        rpe < 4.5 -> R.string.session_rpe_4
        rpe < 6.5 -> R.string.session_rpe_5
        rpe < 10.0 -> R.string.session_rpe_7
        else -> R.string.session_rpe_10
    }
)

/**
 * The line under a session's duration in set lists: its rating, intensity, type, hard minutes and zone, in that
 * order, each only when recorded.
 */
internal fun sessionMarks(res: Resources, s: SetRow, rounds: Boolean = false, countNames: List<String> = emptyList()): SetMarks {
    if (rounds) return roundsMarks(res, s, countNames)
    val parts = listOfNotNull(
        // A session with no rating can't count in training load, so the row says so quietly (#243).
        s.rpe?.let { res.getString(R.string.session_rpe_line, fmtNum(it, 1), sessionRpeWord(res, it)) },
        SessionIntensity.valid(s.intensity)?.let { sessionIntensityName(res, it) },
        SessionTypes.valid(s.sessionType)?.let { sessionTypeText(res, it) },
        s.liveSec?.let { res.getString(R.string.session_hard_line, minutesText(it)) },
        CardioZones.valid(s.zone)?.let { zoneText(res, it) }
    ) + countParts(countNames, s.countA, s.countB) + listOfNotNull(
        res.getString(R.string.session_no_rpe_hint).takeIf { s.rpe == null }
    )
    val line = parts.joinToString(" · ").ifBlank { null }
    return SetMarks(null, null, line, line)
}

/** A set's counts as they read on its line (#266): "3 Takedowns". A count that is unset or 0 is left out. */
internal fun countParts(names: List<String>, a: Int?, b: Int?): List<String> =
    listOf(a, b).mapIndexedNotNull { i, v ->
        val name = names.getOrNull(i)
        if (name == null || v == null || v <= 0) null else "$v $name"
    }

/**
 * The line under a Rounds set's time (#246), always in this order and each part only when recorded: "6 × 3 min · 1 min
 * rest · 2 hard · RPE 8 (Hard) · Zone 3".
 */
internal fun roundsMarks(res: Resources, s: SetRow, countNames: List<String> = emptyList()): SetMarks {
    val roundSec = Rounds.roundSec(s)
    val hard = Rounds.hardRounds(s)
    val parts = Rounds.lineParts(res, s.reps, roundSec, s.restSeconds, hard) + listOfNotNull(
        s.rpe?.let { res.getString(R.string.session_rpe_line, fmtNum(it, 1), sessionRpeWord(res, it)) },
        CardioZones.valid(s.zone)?.let { zoneText(res, it) }
    ) + countParts(countNames, s.countA, s.countB) + listOfNotNull(
        res.getString(R.string.session_no_rpe_hint).takeIf { s.rpe == null }
    )
    val line = parts.joinToString(" · ").ifBlank { null }
    return SetMarks(null, null, line, line)
}
