package com.fitlens.companion.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.fitlens.companion.R
import com.fitlens.companion.data.MedReminders
import com.fitlens.companion.data.Settings
import com.fitlens.companion.ui.design.SettingsActionRow
import com.fitlens.companion.ui.design.SettingsChoiceRow
import com.fitlens.companion.ui.design.SettingsGroup
import com.fitlens.companion.ui.design.SettingsNote
import com.fitlens.companion.ui.design.SettingsNumberRow
import com.fitlens.companion.ui.design.SettingsSwitchRow

/**
 * Settings › Medications (#203): reminders on or off (asking for notifications when switched on), exact alarms on
 * Android 12 and later, the follow-up reminder, and whether medication names show on the lock screen (off by
 * default). Each change sets the next reminder again. Lock Medications (#214) asks for the phone's prompt to switch it on
 * or off; while it's on, names never show on the lock screen.
 */
@Composable
internal fun MedicationsPage() {
    val ctx = LocalContext.current
    val device by Settings.device.collectAsState()
    val notify = rememberNotificationAccess()
    var exact by remember { mutableStateOf(MedReminders.canExact(ctx)) }
    LifecycleResumeEffect(Unit) {
        exact = MedReminders.canExact(ctx)
        MedReminders.rearm()
        onPauseOrDispose { }
    }
    val why = stringResource(R.string.mr_notify_why)

    SettingsGroup(stringResource(R.string.mr_group_reminders))
    SettingsSwitchRow(stringResource(R.string.mr_reminders), device.medReminders, stringResource(R.string.mr_reminders_summary)) { on ->
        Settings.updateDevice { it.copy(medReminders = on) }
        if (on) notify.ask(why)
        MedReminders.rearm()
    }
    NotificationsOffNote(notify, device.medReminders)
    if (Build.VERSION.SDK_INT >= 31) {
        SettingsActionRow(
            stringResource(R.string.mr_exact),
            stringResource(R.string.mr_exact_summary),
            value = stringResource(if (exact) R.string.mr_exact_allowed else R.string.mr_exact_not_allowed),
            enabled = device.medReminders,
            disabledReason = stringResource(R.string.mr_needs_reminders),
            warning = if (!exact && device.medReminders) stringResource(R.string.mr_exact_warning) else null
        ) {
            val intent = Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                ctx.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                openAppSettings(ctx)
            }
        }
    }
    SettingsNumberRow(
        stringResource(R.string.mr_nudge),
        device.medNudgeMinutes,
        0..240,
        summary = stringResource(R.string.mr_nudge_summary),
        format = { m -> if (m == 0) ctx.getString(R.string.settings_value_off) else ctx.getString(R.string.mr_minutes, m) }
    ) { m ->
        Settings.updateDevice { it.copy(medNudgeMinutes = m) }
        MedReminders.rearm()
    }

    SettingsGroup(stringResource(R.string.mr_group_privacy))
    val lockable = MedLock.available(ctx)
    val lockSubtitle = stringResource(R.string.ml_prompt_setting)
    SettingsSwitchRow(
        stringResource(R.string.ml_lock),
        device.medLock && lockable,
        stringResource(R.string.ml_lock_summary),
        enabled = lockable || device.medLock,
        disabledReason = stringResource(R.string.ml_no_screen_lock)
    ) { on ->
        // Both ways need the prompt: on proves it works here, off stops anyone else simply switching it off.
        MedLock.prompt(ctx, lockSubtitle) { ok ->
            if (ok) {
                Settings.updateDevice { it.copy(medLock = on) }
                MedReminders.rearm()
            }
        }
    }
    SettingsChoiceRow(
        stringResource(R.string.mr_cal_lock),
        listOf(stringResource(R.string.mr_cal_hide), stringResource(R.string.mr_cal_count), stringResource(R.string.mr_cal_full)),
        device.medCalendar,
        summary = stringResource(R.string.mr_cal_lock_summary)
    ) { i -> Settings.updateDevice { it.copy(medCalendar = i) } }
    SettingsSwitchRow(
        stringResource(R.string.mr_names),
        device.medNamesOnLockScreen && !device.medLock,
        stringResource(R.string.mr_names_summary),
        enabled = !device.medLock,
        disabledReason = stringResource(R.string.ml_names_off)
    ) { on ->
        Settings.updateDevice { it.copy(medNamesOnLockScreen = on) }
    }
    SettingsNote(stringResource(R.string.mr_local_note))
}
