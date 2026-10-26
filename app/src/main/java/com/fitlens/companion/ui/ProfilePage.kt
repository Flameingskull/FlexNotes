package com.fitlens.companion.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.fitlens.companion.data.BodyFat
import com.fitlens.companion.data.CardioZones
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.Energy
import com.fitlens.companion.data.HealthConnect
import com.fitlens.companion.data.LengthUnits
import com.fitlens.companion.data.PortableSettings
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.Steps
import com.fitlens.companion.data.Store
import com.fitlens.companion.ui.design.SettingsActionRow
import com.fitlens.companion.ui.design.SettingsChoiceRow
import com.fitlens.companion.ui.design.SettingsGroup
import com.fitlens.companion.ui.design.SettingsNote
import com.fitlens.companion.ui.design.SettingsNumberRow
import kotlin.math.roundToInt

/** A height in cm as the length unit shows it, whole: "180 cm" or "71 in". */
internal fun heightText(cm: Double, lengthUnit: String): String =
    "${LengthUnits.convert(cm, LengthUnits.CM, lengthUnit).roundToInt()} $lengthUnit"

/** The date of birth and the age it gives today: "15 Oct 1990 · 35", or Not set. */
internal fun birthText(res: Resources, birth: String?): String {
    val age = Energy.age(birth, Dates.today()) ?: return res.getString(R.string.settings_not_set)
    return res.getString(R.string.prof_birth_value, Dates.medium(birth!!), age)
}

internal fun lifestyleText(res: Resources, l: Energy.Lifestyle): String = res.getString(
    when (l) {
        Energy.Lifestyle.SITTING -> R.string.prof_life_sitting
        Energy.Lifestyle.SOME_WALKING -> R.string.prof_life_some_walking
        Energy.Lifestyle.ON_FEET -> R.string.prof_life_on_feet
        Energy.Lifestyle.PHYSICAL -> R.string.prof_life_physical
        Energy.Lifestyle.HEAVY -> R.string.prof_life_heavy
    }
)

internal fun lifestyleDescription(res: Resources, l: Energy.Lifestyle): String = res.getString(
    when (l) {
        Energy.Lifestyle.SITTING -> R.string.prof_life_sitting_desc
        Energy.Lifestyle.SOME_WALKING -> R.string.prof_life_some_walking_desc
        Energy.Lifestyle.ON_FEET -> R.string.prof_life_on_feet_desc
        Energy.Lifestyle.PHYSICAL -> R.string.prof_life_physical_desc
        Energy.Lifestyle.HEAVY -> R.string.prof_life_heavy_desc
    }
)

/** One line for the main list's Profile row: what's set, or that nothing is. */
internal fun profileSummary(res: Resources, prefs: PortableSettings): String {
    val parts = listOfNotNull(
        BodyFat.Sex.of(prefs.profileSex)?.let { sexText(res, it) },
        Energy.age(prefs.profileBirthDate, Dates.today())?.let { res.getString(R.string.prof_age_n, it) },
        prefs.profileHeightCm?.let { heightText(it, prefs.lengthUnit) },
        if (prefs.activityFromSteps) res.getString(R.string.steps_short)
        else Energy.Lifestyle.of(prefs.profileLifestyle)?.let { lifestyleText(res, it) }
    )
    return if (parts.isEmpty()) res.getString(R.string.prof_summary_empty) else parts.joinToString(" · ")
}

/**
 * The profile's rows (#172): sex, date of birth (with the age it gives), height and daily activity outside training.
 * One set of values for every formula (body fat, resting energy, the TDEE calculator), shown and changed in place
 * wherever they're used: the Profile page and the TDEE calculator both use these rows.
 */
@Composable
internal fun ProfileRows(prefs: PortableSettings) {
    val res = LocalContext.current.resources
    var pickBirth by remember { mutableStateOf(false) }
    SettingsChoiceRow(
        stringResource(R.string.prof_sex),
        BodyFat.Sex.entries.map { sexText(res, it) },
        BodyFat.Sex.of(prefs.profileSex)?.ordinal ?: -1,
        summary = stringResource(R.string.prof_sex_summary)
    ) { i -> Settings.updatePortable { it.copy(profileSex = BodyFat.Sex.entries[i].key) } }
    SettingsActionRow(
        stringResource(R.string.prof_birth),
        stringResource(R.string.prof_birth_summary),
        value = birthText(res, prefs.profileBirthDate),
        sheetOpen = pickBirth
    ) { pickBirth = true }
    // Height is the profile's own value, in the length unit; the Height measurement is left as it is.
    val unit = prefs.lengthUnit
    val range = if (unit == LengthUnits.IN) 39..99 else 100..250
    val shown = prefs.profileHeightCm?.let { LengthUnits.convert(it, LengthUnits.CM, unit).roundToInt() }
        ?: if (unit == LengthUnits.IN) 67 else 170
    SettingsNumberRow(
        stringResource(R.string.prof_height),
        shown.coerceIn(range),
        range,
        summary = if (prefs.profileHeightCm == null) stringResource(R.string.prof_height_unset) else stringResource(R.string.prof_height_summary),
        format = { "$it $unit" }
    ) { v -> Settings.updatePortable { it.copy(profileHeightCm = LengthUnits.convert(v.toDouble(), unit, LengthUnits.CM)) } }
    // Maximum heart rate (#241): optional; unset, cardio zones use the estimate from the date of birth.
    SettingsNumberRow(
        stringResource(R.string.prof_maxhr),
        (prefs.profileMaxHr ?: Energy.age(prefs.profileBirthDate, Dates.today())?.let { CardioZones.tanakaMaxHr(it) } ?: 190)
            .coerceIn(CardioZones.maxHrRange),
        CardioZones.maxHrRange,
        summary = if (prefs.profileMaxHr == null) stringResource(R.string.prof_maxhr_unset) else stringResource(R.string.prof_maxhr_summary),
        format = { res.getString(R.string.prof_maxhr_bpm, it) }
    ) { v -> Settings.updatePortable { it.copy(profileMaxHr = v.coerceIn(CardioZones.maxHrRange)) } }
    if (prefs.profileMaxHr != null) {
        SettingsActionRow(
            stringResource(R.string.prof_maxhr_clear),
            stringResource(R.string.prof_maxhr_clear_summary)
        ) { Settings.updatePortable { it.copy(profileMaxHr = null) } }
    }
    SettingsChoiceRow(
        stringResource(R.string.prof_lifestyle),
        Energy.Lifestyle.entries.map { lifestyleText(res, it) },
        Energy.Lifestyle.of(prefs.profileLifestyle)?.ordinal ?: -1,
        summary = stringResource(R.string.prof_lifestyle_summary),
        descriptions = Energy.Lifestyle.entries.map { lifestyleDescription(res, it) }
    ) { i -> Settings.updatePortable { it.copy(profileLifestyle = Energy.Lifestyle.entries[i].key) } }
    StepsRows(prefs)
    if (pickBirth) {
        PickDateDialog(prefs.profileBirthDate ?: "1990-01-01", onDismiss = { pickBirth = false }) { d ->
            if (Energy.age(d, Dates.today()) != null) Settings.updatePortable { it.copy(profileBirthDate = d) }
        }
    }
}

/** A whole number of steps: "8,532". */
internal fun stepsText(steps: Double): String = kcalText(steps)

/** Opens this app's page in the phone's settings, where a refused permission can be allowed. */
internal fun openAppSettings(context: Context) {
    val intent = Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // No settings screen on this phone; the note above says what to allow.
    }
}

/** Where steps come from (#175, #185): Off, the phone's step counter, or Health Connect. */
internal fun stepsSourceText(res: Resources, prefs: PortableSettings): String = res.getString(
    when {
        !prefs.activityFromSteps -> R.string.steps_source_off
        prefs.hcSteps -> R.string.hc_source_hc
        else -> R.string.steps_source_phone
    }
)

/**
 * Daily activity from steps (#175), under Daily activity: where they come from, Off, the phone's step counter (asks
 * for the Physical activity permission) or Health Connect (#185, asks for its steps permission), then what's been
 * recorded. Until [Steps.MIN_DAYS] days have steps the activity level above is still used. Also on Settings ›
 * Health Connect.
 */
@Composable
internal fun StepsRows(prefs: PortableSettings) {
    val ctx = LocalContext.current
    val res = ctx.resources
    val available = remember { Steps.available(ctx) }
    var allowed by remember { mutableStateOf(Steps.permitted(ctx)) }
    var refused by remember { mutableStateOf(false) }
    var hcMissing by remember { mutableStateOf(false) }
    val source = when {
        !prefs.activityFromSteps -> 0
        prefs.hcSteps -> 2
        else -> 1
    }
    val hc = rememberHealthConnectAccess { Steps.useHealthConnect(ctx) }
    LifecycleResumeEffect(source) {
        allowed = Steps.permitted(ctx)
        // Allowed in the phone's settings while counting was already on: start the background reading.
        if (allowed && source == 1) Steps.start(ctx)
        onPauseOrDispose { }
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted
        refused = !granted
        if (granted) Steps.turnOn(ctx)
    }
    SettingsChoiceRow(
        stringResource(R.string.steps_use),
        listOf(res.getString(R.string.steps_source_off), res.getString(R.string.steps_source_phone), res.getString(R.string.hc_source_hc)),
        source,
        summary = stringResource(R.string.steps_use_summary, Steps.MIN_DAYS),
        descriptions = listOf(
            stringResource(R.string.steps_source_off_desc),
            stringResource(if (available) R.string.steps_source_phone_desc else R.string.steps_no_sensor),
            stringResource(R.string.steps_source_hc_desc)
        )
    ) { i ->
        hcMissing = false
        refused = false
        when (i) {
            0 -> Steps.turnOff(ctx)
            1 -> when {
                !available -> {}
                allowed -> Steps.turnOn(ctx)
                else -> ask.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            }
            else -> if (hc.status == HealthConnect.Status.AVAILABLE) hc.ask(HealthConnect.Kind.STEPS) else hcMissing = true
        }
    }
    if (hcMissing || (source == 2 && hc.status != HealthConnect.Status.AVAILABLE)) {
        HealthConnectMissingRows(hc.status)
        return
    }
    if (hc.refused == HealthConnect.Kind.STEPS || (source == 2 && hc.notAllowed(HealthConnect.Kind.STEPS))) {
        HealthConnectNotAllowedRows(R.string.hc_what_steps)
        if (source != 2) return
    }
    if (source == 1 && !available) {
        SettingsNote(stringResource(R.string.steps_no_sensor), error = true)
        return
    }
    if (!allowed && (source == 1 || refused)) {
        SettingsNote(stringResource(R.string.steps_not_allowed), error = true)
        SettingsActionRow(stringResource(R.string.steps_open_settings)) { openAppSettings(ctx) }
        return
    }
    if (source == 0) return
    val snap by Store.snapshot.collectAsState()
    val s = snap ?: return
    val today = Dates.today()
    val todaySteps = Steps.on(s, today, prefs.hcSteps) ?: 0.0
    val average = Steps.average(s, today, hcPrimary = prefs.hcSteps)
    SettingsNote(
        if (average != null) {
            stringResource(R.string.steps_status, stepsText(todaySteps), stepsText(average.perDay), average.days)
        } else {
            stringResource(R.string.steps_status_waiting, stepsText(todaySteps), Steps.MIN_DAYS)
        }
    )
}

/** Settings → Profile (#172): the profile's rows, and the TDEE calculator that uses them. */
@Composable
internal fun ProfilePage(nav: Nav) {
    val prefs by Settings.portable.collectAsState()
    SettingsGroup(stringResource(R.string.prof_group))
    ProfileRows(prefs)
    SettingsNote(stringResource(R.string.prof_note))
    SettingsGroup(stringResource(R.string.prof_group_uses))
    SettingsActionRow(
        stringResource(R.string.tdee_title),
        stringResource(R.string.prof_tdee_summary),
        value = prefs.calorieTargetKcal?.let { stringResource(R.string.tdee_kcal, kcalText(it.toDouble())) }
    ) { nav.push(Screen.Tdee) }
}
