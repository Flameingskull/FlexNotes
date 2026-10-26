package com.fitlens.companion.ui

import android.content.res.Resources
import com.fitlens.companion.R
import com.fitlens.companion.data.CardioZones
import com.fitlens.companion.data.ExerciseTypes
import com.fitlens.companion.data.SessionIntensity
import com.fitlens.companion.data.SessionTypes
import com.fitlens.companion.data.SetRow
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.fmtNum

/**
 * The words for a Session's stored values (#240, #241). The values stay as stored (ids that never change meaning);
 * only their names come from strings.xml.
 */

/** Is [exerciseId] a Session exercise? */
internal fun Snapshot.isSessionExercise(exerciseId: Long): Boolean = ExerciseTypes.isSession(exercises[exerciseId]?.type ?: -1)

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
internal fun sessionMarks(res: Resources, s: SetRow): SetMarks {
    val parts = listOfNotNull(
        // A session with no rating can't count in training load, so the row says so quietly (#243).
        s.rpe?.let { res.getString(R.string.session_rpe_line, fmtNum(it, 1), sessionRpeWord(res, it)) },
        SessionIntensity.valid(s.intensity)?.let { sessionIntensityName(res, it) },
        SessionTypes.valid(s.sessionType)?.let { sessionTypeText(res, it) },
        s.liveSec?.let { res.getString(R.string.session_hard_line, minutesText(it)) },
        CardioZones.valid(s.zone)?.let { zoneText(res, it) },
        res.getString(R.string.session_no_rpe_hint).takeIf { s.rpe == null }
    )
    val line = parts.joinToString(" · ").ifBlank { null }
    return SetMarks(null, null, line, line)
}
