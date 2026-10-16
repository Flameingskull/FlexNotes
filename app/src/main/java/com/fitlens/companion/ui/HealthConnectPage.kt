package com.fitlens.companion.ui

import android.content.res.Resources
import androidx.activity.compose.rememberLauncherForActivityResult
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
import com.fitlens.companion.data.HealthConnect
import com.fitlens.companion.data.Settings
import com.fitlens.companion.ui.design.SettingsActionRow
import com.fitlens.companion.ui.design.SettingsChoiceRow
import com.fitlens.companion.ui.design.SettingsGroup
import com.fitlens.companion.ui.design.SettingsNote
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Health Connect on a screen (#184): whether it can be used, the permissions FlexNotes has (read again each time the
 * screen comes back, so a change made in Health Connect's settings shows), the type it last asked for and was refused,
 * and [ask], which switches a type to Health Connect, asking for its permission first when needed.
 */
internal class HealthConnectAccess(
    val status: HealthConnect.Status,
    val granted: Set<String>?,
    val refused: HealthConnect.Kind?,
    val ask: (HealthConnect.Kind) -> Unit
) {
    /** [kind] is known not to be allowed (null while the permissions are still being read). */
    fun notAllowed(kind: HealthConnect.Kind): Boolean = granted != null && kind.permission !in granted
}

/** [onAllowed] runs once a type may be read: straight away when it already was, or after Health Connect's dialog. */
@Composable
internal fun rememberHealthConnectAccess(onAllowed: (HealthConnect.Kind) -> Unit): HealthConnectAccess {
    val ctx = LocalContext.current
    var status by remember { mutableStateOf(HealthConnect.status(ctx)) }
    var granted by remember { mutableStateOf<Set<String>?>(null) }
    var pending by remember { mutableStateOf<HealthConnect.Kind?>(null) }
    var refused by remember { mutableStateOf<HealthConnect.Kind?>(null) }
    var resumes by remember { mutableIntStateOf(0) }
    val allowed by rememberUpdatedState(onAllowed)
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
        val kind = pending ?: return@rememberLauncherForActivityResult
        pending = null
        if (kind.permission in result) {
            refused = null
            allowed(kind)
        } else {
            refused = kind
        }
    }
    return HealthConnectAccess(status, granted, refused) { kind ->
        if (granted?.contains(kind.permission) == true) {
            refused = null
            allowed(kind)
        } else {
            pending = kind
            launcher.launch(HealthConnect.permissionsFor(kind))
        }
    }
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
internal fun HealthConnectNotAllowedRows(@androidx.annotation.StringRes what: Int) {
    val ctx = LocalContext.current
    SettingsNote(stringResource(R.string.hc_not_allowed, stringResource(what)), error = true)
    SettingsActionRow(stringResource(R.string.hc_open)) {
        if (!HealthConnect.openSettings(ctx)) openAppSettings(ctx)
    }
}

/** When Health Connect was last read: "16 Oct 2026, 14:05", or Never. */
private fun syncedText(res: Resources, at: Long?): String = at?.let {
    Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
} ?: res.getString(R.string.hc_never)

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
 * Settings › Health Connect (#184): for each type, FlexNotes or Health Connect as the primary source (steps, #185,
 * with the phone's step counter as a third choice; what was eaten, #186), the permission each needs, when it last
 * synced, Sync now, Health Connect's own settings, and what's read and why (Health Connect's privacy link opens here).
 */
@Composable
internal fun HealthConnectPage() {
    val ctx = LocalContext.current
    val res = ctx.resources
    val prefs by Settings.portable.collectAsState()
    val device by Settings.device.collectAsState()
    val syncing by HealthConnect.syncing.collectAsState()
    val access = rememberHealthConnectAccess { kind ->
        if (kind == HealthConnect.Kind.INTAKE) {
            Settings.updatePortable { it.copy(hcIntake = true) }
            HealthConnect.syncSoon(ctx)
        }
    }

    SettingsGroup(stringResource(R.string.hc_group_sources))
    HealthConnectMissingRows(access.status)
    StepsRows(prefs)
    SettingsChoiceRow(
        stringResource(R.string.hc_intake),
        listOf(stringResource(R.string.hc_intake_typed), stringResource(R.string.hc_source_hc)),
        if (prefs.hcIntake) 1 else 0,
        summary = stringResource(R.string.hc_intake_summary),
        descriptions = listOf(stringResource(R.string.hc_intake_typed_desc), stringResource(R.string.hc_intake_hc_desc))
    ) { i ->
        if (i == 0) {
            Settings.updatePortable { it.copy(hcIntake = false) }
            HealthConnect.syncSoon(ctx)
        } else if (access.status == HealthConnect.Status.AVAILABLE) {
            access.ask(HealthConnect.Kind.INTAKE)
        }
    }
    if (access.refused == HealthConnect.Kind.INTAKE || (prefs.hcIntake && access.notAllowed(HealthConnect.Kind.INTAKE))) {
        HealthConnectNotAllowedRows(R.string.hc_what_intake)
    }

    if (access.status == HealthConnect.Status.AVAILABLE) {
        SettingsGroup(stringResource(R.string.hc_group_sync))
        val chosen = prefs.hcSteps || prefs.hcIntake
        SettingsActionRow(
            stringResource(R.string.hc_sync_now),
            stringResource(R.string.hc_sync_now_summary, HealthConnect.DAYS),
            value = if (syncing) stringResource(R.string.hc_syncing) else syncedText(res, device.hcSyncedAt),
            enabled = chosen && !syncing,
            disabledReason = if (!chosen) stringResource(R.string.hc_nothing_chosen) else null
        ) {
            AppScope.scope.launch { UiEvents.show(outcomeText(res, HealthConnect.sync(ctx, force = true))) }
        }
        if (chosen) {
            SettingsNote(
                stringResource(
                    if (access.granted?.contains(HealthConnect.BACKGROUND) == true) R.string.hc_background_on else R.string.hc_background_off
                )
            )
        }
        SettingsActionRow(stringResource(R.string.hc_open), stringResource(R.string.hc_open_summary)) {
            if (!HealthConnect.openSettings(ctx)) openAppSettings(ctx)
        }
    }

    SettingsGroup(stringResource(R.string.hc_group_privacy))
    SettingsNote(stringResource(R.string.hc_privacy))
}
