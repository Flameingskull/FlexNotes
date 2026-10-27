package com.fitlens.companion.ui

import android.content.res.Resources
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.fitlens.companion.R
import com.fitlens.companion.data.ActiveCalories
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.HealthConnect
import com.fitlens.companion.data.HealthWrite
import com.fitlens.companion.data.Settings
import com.fitlens.companion.ui.design.ConfirmSheet
import com.fitlens.companion.ui.design.SettingsActionRow
import com.fitlens.companion.ui.design.SettingsChoiceRow
import com.fitlens.companion.ui.design.SettingsGroup
import com.fitlens.companion.ui.design.SettingsNote
import com.fitlens.companion.ui.design.SettingsSwitchRow
import kotlinx.coroutines.launch

/**
 * Health Connect on a screen (#184): whether it can be used, the permissions FlexNotes has (read again each time the
 * screen comes back, so a change made in Health Connect's settings shows), the type it last asked for and was refused,
 * and [ask], which switches a type to Health Connect, asking for its permission first when needed.
 */
internal class HealthConnectAccess(
    val status: HealthConnect.Status,
    val granted: Set<String>?,
    val refused: HealthConnect.Kind?,
    val ask: (HealthConnect.Kind) -> Unit,
    /** Older data than the last [HealthConnect.DAYS] days was asked for and refused (#192). */
    val historyRefused: Boolean = false,
    /** Asks for [HealthConnect.HISTORY] on its own (#192). */
    val askHistory: () -> Unit = {},
    /** The type it last asked to write and was refused (#191). */
    val writeRefused: HealthWrite.Out? = null,
    /** Switches writing a type on, asking for its write permission first when needed (#191). */
    val askWrite: (HealthWrite.Out) -> Unit = {}
) {
    /** Older data may be read (#192). */
    val history: Boolean get() = granted?.contains(HealthConnect.HISTORY) == true

    /** [kind] is known not to be allowed (null while the permissions are still being read). */
    fun notAllowed(kind: HealthConnect.Kind): Boolean = granted != null && kind.permission !in granted

    /** Writing [out] is known not to be allowed (#191). */
    fun notAllowed(out: HealthWrite.Out): Boolean = granted != null && out.permission !in granted
}

/**
 * [onAllowed] runs once a type may be read: straight away when it already was, or after Health Connect's dialog;
 * [onWriteAllowed] the same once a type may be written (#191).
 */
@Composable
internal fun rememberHealthConnectAccess(
    onWriteAllowed: (HealthWrite.Out) -> Unit = {},
    onAllowed: (HealthConnect.Kind) -> Unit
): HealthConnectAccess {
    val ctx = LocalContext.current
    var status by remember { mutableStateOf(HealthConnect.status(ctx)) }
    var granted by remember { mutableStateOf<Set<String>?>(null) }
    var pending by remember { mutableStateOf<HealthConnect.Kind?>(null) }
    var refused by remember { mutableStateOf<HealthConnect.Kind?>(null) }
    var pendingWrite by remember { mutableStateOf<HealthWrite.Out?>(null) }
    var writeRefused by remember { mutableStateOf<HealthWrite.Out?>(null) }
    var askingHistory by remember { mutableStateOf(false) }
    var historyRefused by remember { mutableStateOf(false) }
    var resumes by remember { mutableIntStateOf(0) }
    val allowed by rememberUpdatedState(onAllowed)
    val writeAllowed by rememberUpdatedState(onWriteAllowed)
    LifecycleResumeEffect(Unit) {
        resumes++
        onPauseOrDispose { }
    }
    LaunchedEffect(resumes) {
        status = HealthConnect.status(ctx)
        granted = HealthConnect.granted(ctx)
    }
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { result ->
        granted = granted.orEmpty() + result
        if (askingHistory) {
            askingHistory = false
            historyRefused = HealthConnect.HISTORY !in result
            if (!historyRefused) HealthConnect.syncSoon(ctx)
            return@rememberLauncherForActivityResult
        }
        pendingWrite?.let { out ->
            pendingWrite = null
            if (out.permission in result) {
                writeRefused = null
                writeAllowed(out)
            } else {
                writeRefused = out
            }
            return@rememberLauncherForActivityResult
        }
        val kind = pending ?: return@rememberLauncherForActivityResult
        pending = null
        if (kind.permission in result) {
            refused = null
            allowed(kind)
        } else {
            refused = kind
        }
    }
    return HealthConnectAccess(
        status, granted, refused,
        ask = { kind ->
            if (granted?.contains(kind.permission) == true) {
                refused = null
                allowed(kind)
            } else {
                pending = kind
                pendingWrite = null
                launcher.launch(HealthConnect.permissionsFor(kind))
            }
        },
        historyRefused = historyRefused,
        askHistory = {
            pending = null
            pendingWrite = null
            askingHistory = true
            launcher.launch(setOf(HealthConnect.HISTORY))
        },
        writeRefused = writeRefused,
        askWrite = { out ->
            if (granted?.contains(out.permission) == true) {
                writeRefused = null
                writeAllowed(out)
            } else {
                pending = null
                pendingWrite = out
                launcher.launch(setOf(out.permission))
            }
        }
    )
}

/**
 * What to do when Health Connect can't be used: install or update it from the Play Store (Android 13 and earlier),
 * or a note that this phone doesn't have it. Nothing when it can be used.
 */
@Composable
internal fun HealthConnectMissingRows(status: HealthConnect.Status) {
    val ctx = LocalContext.current
    when (status) {
        HealthConnect.Status.AVAILABLE -> {}
        HealthConnect.Status.UNSUPPORTED -> SettingsNote(stringResource(R.string.hc_unsupported), error = true)
        HealthConnect.Status.NOT_INSTALLED, HealthConnect.Status.UPDATE_NEEDED -> {
            val install = status == HealthConnect.Status.NOT_INSTALLED
            SettingsNote(stringResource(if (install) R.string.hc_not_installed else R.string.hc_update_needed), error = true)
            SettingsActionRow(stringResource(if (install) R.string.hc_install else R.string.hc_update)) { HealthConnect.openStore(ctx) }
        }
    }
}

/** A type Health Connect refused, or that isn't allowed any more: a note, and Health Connect's settings to allow it. */
@Composable
internal fun HealthConnectNotAllowedRows(@StringRes what: Int) {
    val ctx = LocalContext.current
    SettingsNote(stringResource(R.string.hc_not_allowed, stringResource(what)), error = true)
    SettingsActionRow(stringResource(R.string.hc_open)) {
        if (!HealthConnect.openSettings(ctx)) openAppSettings(ctx)
    }
}

/** When Health Connect was last read: "Fri 16 Oct, 14:05", or Never. */
private fun syncedText(res: Resources, at: Long?): String = at?.let { Dates.dayTime(it) } ?: res.getString(R.string.hc_never)

private fun outcomeText(res: Resources, o: HealthConnect.Outcome): String = res.getString(
    when (o) {
        HealthConnect.Outcome.DONE -> R.string.hc_synced
        HealthConnect.Outcome.NOTHING_CHOSEN -> R.string.hc_nothing_chosen
        HealthConnect.Outcome.UNAVAILABLE -> R.string.hc_unavailable
        HealthConnect.Outcome.NOT_ALLOWED -> R.string.hc_sync_not_allowed
        HealthConnect.Outcome.FAILED -> R.string.hc_sync_failed
    }
)

/**
 * One type's source on Settings › Health Connect: FlexNotes ([own], the first choice) or Health Connect, asking for
 * the type's permission first. When it's refused, or no longer allowed, a note says so and opens Health Connect.
 */
@Composable
private fun HealthConnectSourceRow(
    kind: HealthConnect.Kind,
    access: HealthConnectAccess,
    chosen: Boolean,
    @StringRes title: Int,
    @StringRes summary: Int,
    own: String,
    ownDescription: String,
    hcDescription: String,
    @StringRes what: Int
) {
    val ctx = LocalContext.current
    SettingsChoiceRow(
        stringResource(title),
        listOf(own, stringResource(R.string.hc_source_hc)),
        if (chosen) 1 else 0,
        summary = stringResource(summary),
        descriptions = listOf(ownDescription, hcDescription)
    ) { i ->
        if (i == 0) {
            Settings.updatePortable { kind.choose(it, false) }
            HealthConnect.syncSoon(ctx)
        } else if (access.status == HealthConnect.Status.AVAILABLE) {
            access.ask(kind)
        }
    }
    if (access.refused == kind || (chosen && access.notAllowed(kind))) HealthConnectNotAllowedRows(what)
}

/**
 * Writing one type to Health Connect (#191): on asks for its write permission first; off stops writing it, and what
 * was written stays. When it's refused, or no longer allowed, a note says so and opens Health Connect.
 */
@Composable
private fun HealthWriteRow(out: HealthWrite.Out, access: HealthConnectAccess, on: Boolean, @StringRes title: Int, @StringRes summary: Int, @StringRes what: Int) {
    val ctx = LocalContext.current
    SettingsSwitchRow(
        stringResource(title),
        on,
        summary = stringResource(summary),
        enabled = access.status == HealthConnect.Status.AVAILABLE || on
    ) { checked ->
        if (!checked) {
            Settings.updatePortable { out.switch(it, false) }
            HealthConnect.syncSoon(ctx)
        } else {
            access.askWrite(out)
        }
    }
    if (access.writeRefused == out || (on && access.notAllowed(out))) {
        SettingsNote(stringResource(R.string.hcw_not_allowed, stringResource(what)), error = true)
        SettingsActionRow(stringResource(R.string.hc_open)) {
            if (!HealthConnect.openSettings(ctx)) openAppSettings(ctx)
        }
    }
}

/**
 * Full history (#192): Health Connect lets FlexNotes read the last [HealthConnect.DAYS] days. This row explains what
 * allowing older data does, in a sheet, before Health Connect's own dialog asks; refusing keeps the last
 * [HealthConnect.DAYS] days. Once allowed, it opens Health Connect, where it can be taken back.
 */
@Composable
private fun HistoryRow(access: HealthConnectAccess) {
    val ctx = LocalContext.current
    var explaining by remember { mutableStateOf(false) }
    SettingsActionRow(
        stringResource(R.string.hc_history),
        stringResource(R.string.hc_history_summary, HealthConnect.DAYS),
        value = if (access.history) stringResource(R.string.hc_history_on) else stringResource(R.string.hc_history_off, HealthConnect.DAYS)
    ) {
        if (access.history) {
            if (!HealthConnect.openSettings(ctx)) openAppSettings(ctx)
        } else {
            explaining = true
        }
    }
    if (access.historyRefused && !access.history) {
        SettingsNote(stringResource(R.string.hc_history_refused, HealthConnect.DAYS), error = true)
    }
    if (explaining) {
        ConfirmSheet(
            title = stringResource(R.string.hc_history_ask_title),
            message = stringResource(R.string.hc_history_ask_text, HealthConnect.DAYS),
            confirmLabel = stringResource(R.string.hc_history_ask_go),
            onDismiss = { explaining = false },
            onConfirm = {
                explaining = false
                access.askHistory()
            },
            destructive = false
        )
    }
}

/**
 * Settings › Health Connect (#184): for each type, FlexNotes or Health Connect as the primary source (steps, #185,
 * with the phone's step counter as a third choice; what was eaten, #186; active calories, #188; bodyweight and body
 * fat, #187; resting heart rate and sleep, #189; exercise sessions as cardio sets, #190), what's written to it (#191:
 * bodyweight, body fat and workouts, each switched on here), the permission each needs, when it last synced, Sync now, Health
 * Connect's own settings, and what's read and why (Health Connect's privacy link opens here).
 */
@Composable
internal fun HealthConnectPage() {
    val ctx = LocalContext.current
    val res = ctx.resources
    val prefs by Settings.portable.collectAsState()
    val device by Settings.device.collectAsState()
    val syncing by HealthConnect.syncing.collectAsState()
    val access = rememberHealthConnectAccess(
        onWriteAllowed = { out ->
            Settings.updatePortable { out.switch(it, true) }
            HealthConnect.syncSoon(ctx)
        }
    ) { kind ->
        // Steps are switched on their own row, which also stops the phone's step counter.
        if (kind != HealthConnect.Kind.STEPS) {
            Settings.updatePortable { kind.choose(it, true) }
            HealthConnect.syncSoon(ctx)
        }
    }
    val typed = stringResource(R.string.hc_intake_typed)
    val typedDesc = stringResource(R.string.hc_typed_desc)

    SettingsGroup(stringResource(R.string.hc_group_sources))
    HealthConnectMissingRows(access.status)
    StepsRows(prefs)
    HealthConnectSourceRow(
        HealthConnect.Kind.INTAKE, access, prefs.hcIntake, R.string.hc_intake, R.string.hc_intake_summary,
        typed, stringResource(R.string.hc_intake_typed_desc), stringResource(R.string.hc_intake_hc_desc), R.string.hc_what_intake
    )
    HealthConnectSourceRow(
        HealthConnect.Kind.ACTIVE, access, prefs.hcActive, R.string.hc_active, R.string.hc_active_summary,
        stringResource(R.string.hc_active_own), stringResource(R.string.hc_active_own_desc),
        stringResource(R.string.hc_active_hc_desc, ActiveCalories.MIN_DAYS), R.string.hc_what_active
    )

    SettingsGroup(stringResource(R.string.hc_group_body))
    HealthConnectSourceRow(
        HealthConnect.Kind.WEIGHT, access, prefs.hcWeight, R.string.hc_weight, R.string.hc_weight_summary,
        typed, typedDesc, stringResource(R.string.hc_weight_hc_desc), R.string.hc_what_weight
    )
    HealthConnectSourceRow(
        HealthConnect.Kind.BODY_FAT, access, prefs.hcBodyFat, R.string.hc_body_fat, R.string.hc_body_fat_summary,
        typed, typedDesc, stringResource(R.string.hc_body_fat_hc_desc), R.string.hc_what_body_fat
    )
    HealthConnectSourceRow(
        HealthConnect.Kind.RESTING_HR, access, prefs.hcRestingHr, R.string.hc_resting_hr, R.string.hc_resting_hr_summary,
        typed, typedDesc, stringResource(R.string.hc_resting_hr_hc_desc), R.string.hc_what_resting_hr
    )
    HealthConnectSourceRow(
        HealthConnect.Kind.SLEEP, access, prefs.hcSleep, R.string.hc_sleep, R.string.hc_sleep_summary,
        typed, typedDesc, stringResource(R.string.hc_sleep_hc_desc), R.string.hc_what_sleep
    )

    // Vitals (#212): each a reading at its own time, also logged by hand in the Body tracker.
    SettingsGroup(stringResource(R.string.hc_group_vitals))
    HealthConnectSourceRow(
        HealthConnect.Kind.BLOOD_PRESSURE, access, prefs.hcBloodPressure, R.string.hc_bp, R.string.hc_bp_summary,
        typed, typedDesc, stringResource(R.string.hc_bp_hc_desc), R.string.hc_what_bp
    )
    HealthConnectSourceRow(
        HealthConnect.Kind.GLUCOSE, access, prefs.hcGlucose, R.string.hc_glucose, R.string.hc_glucose_summary,
        typed, typedDesc, stringResource(R.string.hc_glucose_hc_desc), R.string.hc_what_glucose
    )
    HealthConnectSourceRow(
        HealthConnect.Kind.SPO2, access, prefs.hcSpo2, R.string.hc_spo2, R.string.hc_spo2_summary,
        typed, typedDesc, stringResource(R.string.hc_spo2_hc_desc), R.string.hc_what_spo2
    )
    HealthConnectSourceRow(
        HealthConnect.Kind.HRV, access, prefs.hcHrv, R.string.hc_hrv, R.string.hc_hrv_summary,
        typed, typedDesc, stringResource(R.string.hc_hrv_hc_desc), R.string.hc_what_hrv
    )
    HealthConnectSourceRow(
        HealthConnect.Kind.TEMPERATURE, access, prefs.hcTemperature, R.string.hc_temperature, R.string.hc_temperature_summary,
        typed, typedDesc, stringResource(R.string.hc_temperature_hc_desc), R.string.hc_what_temperature
    )

    SettingsGroup(stringResource(R.string.hc_group_workouts))
    HealthConnectSourceRow(
        HealthConnect.Kind.EXERCISE, access, prefs.hcExercise, R.string.hc_exercise, R.string.hc_exercise_summary,
        stringResource(R.string.hc_exercise_own), stringResource(R.string.hc_exercise_own_desc),
        stringResource(R.string.hc_exercise_hc_desc, HealthConnect.DAYS), R.string.hc_what_exercise
    )

    SettingsGroup(stringResource(R.string.hcw_group))
    SettingsNote(stringResource(R.string.hcw_note))
    HealthWriteRow(HealthWrite.Out.WEIGHT, access, prefs.hcWriteWeight, R.string.hcw_weight, R.string.hcw_weight_summary, R.string.hcw_what_weight)
    HealthWriteRow(HealthWrite.Out.BODY_FAT, access, prefs.hcWriteBodyFat, R.string.hcw_body_fat, R.string.hcw_body_fat_summary, R.string.hcw_what_body_fat)
    HealthWriteRow(HealthWrite.Out.WORKOUTS, access, prefs.hcWriteWorkouts, R.string.hcw_workouts, R.string.hcw_workouts_summary, R.string.hcw_what_workouts)

    if (access.status == HealthConnect.Status.AVAILABLE) {
        SettingsGroup(stringResource(R.string.hc_group_sync))
        val chosen = HealthConnect.anyChosen(prefs)
        SettingsActionRow(
            stringResource(R.string.hc_sync_now),
            stringResource(R.string.hc_sync_now_summary, HealthConnect.DAYS),
            value = if (syncing) stringResource(R.string.hc_syncing) else syncedText(res, device.hcSyncedAt),
            enabled = chosen && !syncing,
            disabledReason = if (!chosen) stringResource(R.string.hc_nothing_chosen) else null
        ) {
            AppScope.scope.launch { UiEvents.show(outcomeText(res, HealthConnect.sync(ctx, force = true))) }
        }
        if (HealthConnect.Kind.entries.any { it.chosen(prefs) }) {
            SettingsNote(
                stringResource(
                    if (access.granted?.contains(HealthConnect.BACKGROUND) == true) R.string.hc_background_on else R.string.hc_background_off
                )
            )
            HistoryRow(access)
        }
        SettingsActionRow(stringResource(R.string.hc_open), stringResource(R.string.hc_open_summary)) {
            if (!HealthConnect.openSettings(ctx)) openAppSettings(ctx)
        }
    }

    SettingsGroup(stringResource(R.string.hc_group_privacy))
    SettingsNote(stringResource(R.string.hc_privacy))
}
