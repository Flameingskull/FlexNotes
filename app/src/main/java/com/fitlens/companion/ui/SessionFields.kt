package com.fitlens.companion.ui

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fitlens.companion.R
import com.fitlens.companion.data.CardioZones
import com.fitlens.companion.data.SessionIntensity
import com.fitlens.companion.data.SessionMetrics
import com.fitlens.companion.data.SessionTypes
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.ui.design.DropdownPill
import com.fitlens.companion.ui.design.FitSheet
import com.fitlens.companion.ui.design.StepperField

private const val MINUTE_STEP = 5.0

private fun minutes(s: String): Double = s.trim().replace(',', '.').toDoubleOrNull() ?: 0.0

private fun stepMinutes(text: String, dir: Int): String =
    fmtNum(kotlin.math.max(0.0, minutes(text) + dir * MINUTE_STEP), 1).let { if (it == "0") "" else it }

/** A count as typed: digits only, so "" means none. */
internal fun countText(v: Int?): String = v?.toString() ?: ""

internal fun countValue(text: String): Int? = text.filter { it.isDigit() }.take(6).toIntOrNull()

/**
 * One stepper per count the exercise names (#266), for a Session or Rounds set: "Takedowns", "Points". Nothing shows for
 * an exercise with no counts. A count with a value is shown even if its name was later removed, so it can be fixed.
 */
@Composable
internal fun CountFields(
    names: List<String>,
    aText: String,
    onA: (String) -> Unit,
    bText: String,
    onB: (String) -> Unit
) {
    val fallback = stringResource(R.string.count_field_fallback)
    listOf(Triple(aText, onA, 0), Triple(bText, onB, 1)).forEach { (text, set, i) ->
        val name = names.getOrNull(i)
        if (name != null || text.isNotBlank()) StepperField(
            label = name ?: fallback,
            value = text,
            onValue = { set(it.filter { c -> c.isDigit() }.take(6)) },
            onStep = { dir -> set(((countValue(text) ?: 0) + dir).coerceAtLeast(0).takeIf { it > 0 }?.toString() ?: "") },
            keyboard = KeyboardType.Number
        )
    }
}

/**
 * The fields of a Session set (#240, #241), shared by the Track tab and the workout editor's predefined sets: its
 * length in minutes, how hard the whole session felt (only when [onRpe] is given, because a workout can't predefine a
 * rating), hard or live minutes, intensity, type and cardio zone. Everything but the length is optional. Emits its
 * fields into the caller's column so they space like the other fields.
 */
@Composable
internal fun SessionFields(
    date: String,
    durationText: String,
    onDuration: (String) -> Unit,
    liveText: String,
    onLive: (String) -> Unit,
    intensity: Int?,
    onIntensity: (Int?) -> Unit,
    sessionType: Int?,
    onSessionType: (Int?) -> Unit,
    zone: Int?,
    onZone: (Int?) -> Unit,
    onOpenProfile: () -> Unit,
    rpe: Double? = null,
    onRpe: ((Double?) -> Unit)? = null,
    /** The exercise's [SessionMetrics] (#249); null shows all. A field with a value is always shown, so it can be fixed. */
    fields: Int? = null
) {
    val res = LocalContext.current.resources
    var zoneHelp by remember { mutableStateOf(false) }

    StepperField(
        label = stringResource(R.string.session_field_duration),
        value = durationText,
        onValue = onDuration,
        onStep = { dir -> onDuration(stepMinutes(durationText, dir)) }
    )
    if (onRpe != null) {
        // The rating is held as typed while it is being typed, and re-read whenever it changes from outside.
        var text by remember(rpe) { mutableStateOf(rpe?.let { fmtNum(it, 1) } ?: "") }
        StepperField(
            label = stringResource(R.string.session_field_rpe),
            value = text,
            onValue = { t ->
                text = t
                val v = t.trim().replace(',', '.')
                if (v.isEmpty()) onRpe(null)
                else v.toDoubleOrNull()?.let { onRpe(Math.round(it.coerceIn(0.0, 10.0) * 2.0) / 2.0) }
            },
            onStep = { dir -> onRpe(if (rpe == null) 5.0 else (rpe + dir * 0.5).coerceIn(0.0, 10.0)) }
        )
        Text(
            if (rpe == null) stringResource(R.string.session_rpe_unrated) else sessionRpeWord(res, rpe),
            Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (SessionMetrics.has(fields, SessionMetrics.LIVE) || liveText.isNotBlank()) StepperField(
        label = stringResource(R.string.session_field_hard),
        value = liveText,
        onValue = onLive,
        onStep = { dir -> onLive(stepMinutes(liveText, dir)) }
    )
    if (minutes(liveText) > minutes(durationText)) {
        Text(
            stringResource(R.string.session_hard_too_long),
            Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
    val notSet = stringResource(R.string.session_not_set)
    if (SessionMetrics.has(fields, SessionMetrics.INTENSITY) || intensity != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val ids = SessionIntensity.all
        DropdownPill(
            stringResource(R.string.session_intensity),
            listOf(notSet) + ids.map { sessionIntensityOption(res, it) },
            ids.indexOf(intensity) + 1
        ) { i -> onIntensity(ids.getOrNull(i - 1)) }
    }
    if (SessionMetrics.has(fields, SessionMetrics.TYPE) || sessionType != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val ids = SessionTypes.all
        DropdownPill(
            stringResource(R.string.session_type),
            listOf(notSet) + ids.map { sessionTypeText(res, it) },
            ids.indexOf(sessionType) + 1
        ) { i -> onSessionType(ids.getOrNull(i - 1)) }
    }
    if (SessionMetrics.has(fields, SessionMetrics.ZONE) || zone != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val zones = CardioZones.all
        DropdownPill(
            stringResource(R.string.session_zone),
            listOf(notSet) + zones.map { zoneText(res, it) },
            zones.indexOf(zone) + 1
        ) { i -> onZone(zones.getOrNull(i - 1)) }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { zoneHelp = true }) {
            Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.session_zone_help))
        }
    }
    if (zoneHelp) ZoneHelpSheet(date, onDismiss = { zoneHelp = false }, onOpenProfile = onOpenProfile)
}

/**
 * What each cardio zone feels like (#241), opened only from its info button. Beats per minute come from the profile:
 * the maximum heart rate typed there, else an estimate from the date of birth (said to be an estimate), else the feel,
 * the talk test and the percentages alone, with a way to the Profile page.
 */
@Composable
internal fun ZoneHelpSheet(date: String, onDismiss: () -> Unit, onOpenProfile: () -> Unit) {
    val res = LocalContext.current.resources
    val prefs by Settings.portable.collectAsState()
    val maxHr = CardioZones.maxHr(prefs.profileMaxHr, prefs.profileBirthDate, date)
    FitSheet(
        title = stringResource(R.string.session_zone_help_title),
        onDismiss = onDismiss,
        dismissLabel = stringResource(R.string.session_zone_close)
    ) {
        Text(
            when {
                maxHr == null -> stringResource(R.string.session_zone_no_max)
                maxHr.estimated -> stringResource(R.string.session_zone_max_estimated, maxHr.bpm)
                else -> stringResource(R.string.session_zone_max_own, maxHr.bpm)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CardioZones.all.forEach { z ->
            val p = CardioZones.percentRange(z)
            Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    res.getString(R.string.session_zone_head, zoneText(res, z), p.first, p.last),
                    style = MaterialTheme.typography.titleSmall,
                    color = Brand.Gold
                )
                if (maxHr != null) {
                    val r = CardioZones.bpmRange(z, maxHr.bpm)
                    Text(
                        res.getString(if (maxHr.estimated) R.string.session_zone_bpm_est else R.string.session_zone_bpm, r.first, r.last),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(zoneFeel(res, z), style = MaterialTheme.typography.bodyMedium)
                Text(zoneTalk(res, z), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (maxHr == null || maxHr.estimated) {
            TextButton(onClick = { onDismiss(); onOpenProfile() }) {
                Text(stringResource(if (maxHr == null) R.string.session_zone_add_profile else R.string.session_zone_set_profile))
            }
        }
    }
}

private fun zoneFeel(res: Resources, z: Int): String = res.getString(
    when (z) {
        1 -> R.string.session_zone_feel_1
        2 -> R.string.session_zone_feel_2
        3 -> R.string.session_zone_feel_3
        4 -> R.string.session_zone_feel_4
        else -> R.string.session_zone_feel_5
    }
)

private fun zoneTalk(res: Resources, z: Int): String = res.getString(
    when (z) {
        1 -> R.string.session_zone_talk_1
        2 -> R.string.session_zone_talk_2
        3 -> R.string.session_zone_talk_3
        4 -> R.string.session_zone_talk_4
        else -> R.string.session_zone_talk_5
    }
)
