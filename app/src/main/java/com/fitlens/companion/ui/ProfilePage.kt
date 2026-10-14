package com.fitlens.companion.ui

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.fitlens.companion.R
import com.fitlens.companion.data.BodyFat
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.Energy
import com.fitlens.companion.data.LengthUnits
import com.fitlens.companion.data.PortableSettings
import com.fitlens.companion.data.Settings
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
        Energy.Lifestyle.of(prefs.profileLifestyle)?.let { lifestyleText(res, it) }
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
    SettingsChoiceRow(
        stringResource(R.string.prof_lifestyle),
        Energy.Lifestyle.entries.map { lifestyleText(res, it) },
        Energy.Lifestyle.of(prefs.profileLifestyle)?.ordinal ?: -1,
        summary = stringResource(R.string.prof_lifestyle_summary),
        descriptions = Energy.Lifestyle.entries.map { lifestyleDescription(res, it) }
    ) { i -> Settings.updatePortable { it.copy(profileLifestyle = Energy.Lifestyle.entries[i].key) } }
    if (pickBirth) {
        PickDateDialog(prefs.profileBirthDate ?: "1990-01-01", onDismiss = { pickBirth = false }) { d ->
            if (Energy.age(d, Dates.today()) != null) Settings.updatePortable { it.copy(profileBirthDate = d) }
        }
    }
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
