package com.fitlens.companion.data

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Settings that belong to this phone (#38): folders and their permissions, schedules, and state such as the last
 * import or the safety copy. They live in DataStore, outside `fitlens.db`, so they're never in a `.flexnotes` backup
 * and a restore never replaces them.
 */
data class DeviceSettings(
    /** The FitNotes backup folder (a tree URI). */
    val backupFolder: String? = null,
    val autoSync: Boolean = false,
    val autoBackupFolder: String? = null,
    /** 0 = off, 1 = daily, 7 = weekly. */
    val autoBackupDays: Int = 0,
    val autoBackupKeep: Int = 5,
    val autoBackupLast: Long? = null,
    /** The last automatic backup that failed since the last one that worked, as "time|message". */
    val autoBackupError: String? = null,
    val backupAfterChanges: Boolean = false,
    val backupDirty: Boolean = false,
    /** Automatic backups also copy new or changed photos as image files into the backup folder's `Photos` folder
     *  (#195). Off until the user turns it on in Settings › Backups. */
    val backupPhotos: Boolean = false,
    val lastImportName: String? = null,
    val lastImportAt: Long? = null,
    val lastImportModified: Long? = null,
    val safetyAt: Long? = null,
    val safetyReason: String? = null,
    /** The last important result, as "time|level|text" (see `UiEvents`). */
    val lastResult: String? = null,
    /** The graph hint shows until the user has tapped a point and opened a graph full screen once each (#50). */
    val chartTapSeen: Boolean = false,
    val chartExpandSeen: Boolean = false,
    /** The rest-over sound (#20) as a ringtone URI, or null for the phone's default notification sound. Sounds are
     *  files on this phone, so the choice stays here and never travels in backups. */
    val restSoundUri: String? = null,
    /** The calendar's filter (#9), as `CalendarFilter.encode` wrote it, or null for none. */
    val calendarFilter: String? = null,
    /** The guided setup (#29) has been finished or skipped on this phone, or wasn't needed because data was here. */
    val setupDone: Boolean = false,
    /** The app version (`versionCode`) whose "What's new" was last shown or skipped on this phone (#33). */
    val whatsNewSeen: Long = 0,
    /** The last step counter reading (#175), `Steps.Reading.encode`, or null before the first. The counter belongs to
     *  this phone, so it never travels in backups. */
    val stepsLast: String? = null,
    /** When Health Connect was last read on this phone (#184), epoch milliseconds, or null before the first sync. */
    val hcSyncedAt: Long? = null,
    /** The Health Connect types whose full history has been read on this phone (#192), as `Kind` names separated by
     *  commas. Cleared when older data is no longer allowed, so allowing it again reads the history again. */
    val hcHistoryKinds: String? = null,
    /** The lab results from Health Connect already added on this phone (#225), one key a line (`LabResults.seen`), so
     *  a result is added once and stays as the user left it after an edit or a delete. */
    val hcLabIds: String? = null,
    /** Medication reminders (#203): a notification when a scheduled dose is due. On unless switched off. */
    val medReminders: Boolean = true,
    /** Medication names on the lock screen (#203). Off: the lock screen shows only "Medication reminder". */
    val medNamesOnLockScreen: Boolean = false,
    /** Lock Medications (#214): the Medications screens need the phone's fingerprint, face or PIN prompt, and
     *  reminders name no medication. Off unless switched on; belongs to this phone, like its screen lock. */
    val medLock: Boolean = false,
    /** Minutes after a dose is due to remind again while it isn't taken or skipped (#203); 0 for no follow-up. */
    val medNudgeMinutes: Int = 30,
    /** Snoozed reminders (#203), `MedReminders.Snoozes.encode`: due time to when it rings again. Phone state. */
    val medSnoozes: String? = null,
    /** The camera's last photo behind the body outline (#230): its opacity in percent, 0 to 60. */
    val cameraPhotoOpacity: Int = 30,
    /** The camera's body outline (#230): `BodyOutline.THIN`, `MEDIUM` or `BOLD`. */
    val cameraOutlineLine: Int = BodyOutline.BOLD
)

/**
 * Preferences that belong to the user, not the phone. They stay in the database's `meta` table, so they go into
 * `.flexnotes` backups and restore on another phone.
 */
data class PortableSettings(
    /** "kg" or "lbs". */
    val weightUnit: String = "kg",
    /** Set when the user chose the unit by hand, so a FitNotes import doesn't change it back. */
    val weightUnitManual: Boolean = false,
    /** Distances are logged and shown in this unit unless the exercise has its own (#7): "km", "mi" or "m".
     *  Distances are stored as typed, so changing it relabels them rather than converting them. */
    val distanceUnit: String = DistanceUnits.KM,
    /** Body lengths are shown in this unit (#7): "cm" or "in". They're stored as logged and converted for display. */
    val lengthUnit: String = LengthUnits.CM,
    /** The weight stepper's step in kg, or null for the default. */
    val weightIncrementKg: Double? = null,
    val keepScreenOn: Boolean = true,
    /** Celebrate a new personal record when a set is saved (#23). */
    val celebratePrs: Boolean = true,
    /** Where a new set's fields come from (#97): [AUTOFILL_LAST] or [AUTOFILL_EMPTY]. Routines (#21) add a third. */
    val autofillSource: String = AUTOFILL_LAST,
    /** After updating a set, select the next one of the day so it can be adjusted and saved in turn (#97). */
    val autoSelectNext: Boolean = false,
    /** Add the date and time to the names of backups saved or shared by hand (#30). Automatic backups always do. */
    val backupTimestamp: Boolean = true,
    /** Count warm-up sets in records and statistics (#43). */
    val warmupsCount: Boolean = false,
    /** The estimated-1RM formula's key (`Records.Formula`), used everywhere an estimate appears (#42). */
    val e1rmFormula: String = "auto",
    /** The most reps a set can have to be estimated from (FitNotes's Estimated 1RM settings, #148), or 0 for the
     *  formula's own limit. Never above the formula's limit. */
    val e1rmMaxReps: Int = 0,
    /** The plate calculator's bar in kg, or null for a standard bar in the display unit (#28). */
    val barKg: Double? = null,
    /** The plate calculator counts the bar in the target weight (#28). */
    val countBar: Boolean = true,
    /** The plates on hand in the display unit, comma-separated, or null for the standard set (#28). */
    val plates: String? = null,
    /** Show the W, D and F badges on sets (#43). */
    val showSetType: Boolean = true,
    /** Effort per set (#44): [Effort.OFF], [Effort.RPE] or [Effort.RIR]. */
    val effortMode: String = Effort.OFF,
    /** The first day of the week for the calendar and weekly analysis (#7): 1 = Monday … 7 = Sunday (ISO). */
    val weekStart: Int = 1,
    /** Show each exercise's category colour on the day log (#8). */
    val homeShowCategories: Boolean = true,
    /** How many sets each exercise card on the day log shows (#8): 0 for all, otherwise 1–10. */
    val homeSetsShown: Int = 0,
    /** The routine last chosen in the library's switcher (#21), or 0 for All exercises. */
    val lastRoutineId: Long = 0L,
    /** Start the workout timer when the first set of today is saved (#12). */
    val workoutTimerAuto: Boolean = false,
    /** The rest timer's length in seconds (#20). */
    val restSeconds: Int = 90,
    /** Start the rest timer when a set is saved (#20). */
    val restAutoStart: Boolean = false,
    /** Vibrate when the rest timer ends (#20). */
    val restVibrate: Boolean = true,
    /** Play a sound when the rest timer ends (#20); which sound is phone-only ([DeviceSettings.restSoundUri]). */
    val restSound: Boolean = true,
    /** The rest-over sound's volume, 10 to 100 percent of the notification volume (#20). */
    val restVolume: Int = 80,
    /** "Mark sets complete" mode (#19): tick boxes on sets and progress per exercise and workout. */
    val markComplete: Boolean = false,
    /** The chart kind chosen per graph (#137), "graph=kind;…" (`ChartKind.encode`), or null when none was chosen. */
    val graphKinds: String? = null,
    /** Graphs pinned to Analysis → Overview (#55), in order (`PinnedGraph.encode`), or null when none are pinned. */
    val pinnedGraphs: String? = null,
    /** The exercises each exercise's graph is compared with (#53), "id=id,id;…" (`GraphCompare.encode`). */
    val graphCompare: String? = null,
    /** Sex for the body fat formula (#153) and resting energy (#172), `BodyFat.Sex.key`, or null until chosen. */
    val profileSex: String? = null,
    /** The profile (#172): date of birth (ISO), so age is worked out for each date, or null. */
    val profileBirthDate: String? = null,
    /** The emergency card (#215): the name printed on it, allergies, conditions and an emergency contact, as typed. */
    val emergencyName: String? = null,
    val emergencyAllergies: String? = null,
    val emergencyConditions: String? = null,
    val emergencyContact: String? = null,
    /** Height in cm, the profile's own value (not the Height measurement), or null. */
    val profileHeightCm: Double? = null,
    /** Daily activity outside training, `Energy.Lifestyle.key`, or null. */
    val profileLifestyle: String? = null,
    /** Daily activity from the phone's step counter (#175) instead of [profileLifestyle], once enough days are counted. */
    val activityFromSteps: Boolean = false,
    /** Steps come from Health Connect (#185) rather than the phone's step counter; only with [activityFromSteps]. */
    val hcSteps: Boolean = false,
    /** What was eaten comes from Health Connect (#186) when it has values for the day, rather than what was typed. */
    val hcIntake: Boolean = false,
    /** A scale's or watch's bodyweight readings are read from Health Connect into Bodyweight (#187). */
    val hcWeight: Boolean = false,
    /** Body fat readings are read from Health Connect into Body fat (#187). */
    val hcBodyFat: Boolean = false,
    /**
     * Daily activity comes from Health Connect's active calories (#188), ahead of [activityFromSteps] and
     * [profileLifestyle], once enough days have them.
     */
    val hcActive: Boolean = false,
    /** Resting heart rate is read from Health Connect (#189). */
    val hcRestingHr: Boolean = false,
    /** Sleep is read from Health Connect (#189). */
    val hcSleep: Boolean = false,
    /** Exercise sessions from Health Connect become cardio sets (#190). */
    val hcExercise: Boolean = false,
    /** Vitals read from Health Connect (#212): blood pressure, blood glucose, blood oxygen, HRV and body temperature. */
    val hcBloodPressure: Boolean = false,
    val hcGlucose: Boolean = false,
    val hcSpo2: Boolean = false,
    val hcHrv: Boolean = false,
    val hcTemperature: Boolean = false,
    /** Water and caffeine read from Health Connect (#226): each drink at its own time. */
    val hcWater: Boolean = false,
    val hcCaffeine: Boolean = false,
    /** Menstrual cycle read from Health Connect (#222): period days, kept as Period values. */
    val hcCycle: Boolean = false,
    /** The cycle's phases are left off body and exercise graphs (#222); shown by default when there are Period values. */
    val cycleBandsOff: Boolean = false,
    /** Lab results read from Health Connect's medical records (#225), where the phone offers them. */
    val hcLabs: Boolean = false,
    /** Bodyweight logged in FlexNotes is written to Health Connect (#191). */
    val hcWriteWeight: Boolean = false,
    /** Body fat logged in FlexNotes is written to Health Connect (#191). */
    val hcWriteBodyFat: Boolean = false,
    /** Each logged workout with a start and end is written to Health Connect as an exercise session (#191). */
    val hcWriteWorkouts: Boolean = false,
    /** The TDEE calculator's goal (#173), `Energy.Goal.encode`, or null for none chosen. */
    val calorieGoal: String? = null,
    /** The daily calorie target set from the calculator, or null. */
    val calorieTargetKcal: Int? = null,
    /** The macro targets set with it, in grams a day, or null. */
    val macroProteinG: Int? = null,
    val macroFatG: Int? = null,
    val macroCarbG: Int? = null,
    /** Which resting energy equation, `Energy.Formula.key`. */
    val bmrFormula: String = "auto",
    /** A planned week of training for when too little is logged, `Energy.Plan.encode`, or null for the default. */
    val trainingPlan: String? = null,
    /** The day's calorie need (#174): `EnergyEstimates.MODE_SAME` (the target every day) or `MODE_TRAINING`. */
    val calorieMode: String = EnergyEstimates.MODE_SAME,
    /** Which TDEE the goal uses (#176): `EnergyEstimates.SOURCE_ESTIMATE` (the formulas) or `SOURCE_MEASURED`. */
    val tdeeSource: String = EnergyEstimates.SOURCE_ESTIMATE,
    /** Show the calories card on the day log (#174). */
    val homeShowCalories: Boolean = true,
    /** Show the readiness card on the day log (#216). */
    val homeShowReadiness: Boolean = true,
    /** Show the water and caffeine card on the day log (#221). */
    val homeShowDrinks: Boolean = true,
    /** Usual bedtime, HH:mm (#221): when the caffeine still active is estimated. */
    val bedtime: String = Drinks.DEFAULT_BEDTIME,
    /** The pose new photos get (#46): null to ask each time, [Poses.NONE] for none, or one of [Poses.all]. */
    val photoDefaultPose: String? = null,
    /** How the Photos grid is grouped (#46), one of [MediaPrefs.GROUPS]. */
    val photoGroupBy: String = MediaPrefs.GROUP_MONTH,
    /** Open the slideshow and video screen with the options last used (#46). */
    val rememberVideoOpts: Boolean = true,
    /** Those options (`SlideshowPrefs.encode`), or null for the defaults. */
    val videoOpts: String? = null,
    /** The PDF report's pages: dark (as in the app) or light (for printing) (#46). */
    val pdfDark: Boolean = true,
    /** Photos per day in the PDF report's daily log, 0 to 4 (#46). */
    val pdfPhotosPerDay: Int = 2
) {
    companion object {
        const val AUTOFILL_LAST = "last"
        const val AUTOFILL_EMPTY = "empty"
    }
}

private const val STORE_NAME = "fitlens_device_settings"

private val Context.deviceStore: DataStore<Preferences> by preferencesDataStore(
    name = STORE_NAME,
    produceMigrations = { listOf(MetaToDeviceMigration) }
)

/**
 * The only way FlexNotes reads or writes settings. Screens observe [device] and [portable] and change them with
 * [updateDevice] and [updatePortable]. Nothing else touches DataStore or the settings rows in `meta`, so where a
 * setting is stored never shows.
 *
 * Both sets are loaded once, off the main thread, when the app starts. After that this in-memory copy is the truth
 * for the process: reads are instant, and every change is written through in order.
 */
object Settings {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()
    private lateinit var store: DataStore<Preferences>
    private lateinit var loaded: Deferred<Unit>
    /** True once DataStore has loaded, so its one-time copy from `meta` has run and the old rows can go (#98). */
    @Volatile private var deviceStoreReady = false

    private val _device = MutableStateFlow(DeviceSettings())
    val device: StateFlow<DeviceSettings> = _device

    private val _portable = MutableStateFlow(PortableSettings())
    val portable: StateFlow<PortableSettings> = _portable

    /** Called once per process from `App`, after `Store.init`. Starts loading in the background. */
    fun init(context: Context) {
        store = context.applicationContext.deviceStore
        loaded = scope.async {
            // A damaged settings file or database must not stop the app: it starts from the defaults instead.
            runCatching {
                val prefs = store.data.first()
                _device.value = deviceFrom { prefs[stringPreferencesKey(it)] }
                deviceStoreReady = true
            }
            runCatching { dropLegacyDeviceRows() }
            runCatching { _portable.value = portableFrom(Store.db::getMeta) }
            Unit
        }
    }

    /** Waits for the first load. Everything that reads settings goes through here. */
    suspend fun awaitLoaded() = loaded.await()

    /**
     * The current phone settings. Loading takes a few milliseconds at start-up; a caller that arrives before it's
     * done waits for it rather than seeing defaults (a background backup must never think it's switched off).
     */
    fun current(): DeviceSettings {
        if (!loaded.isCompleted) runBlocking { loaded.await() }
        return _device.value
    }

    fun currentPortable(): PortableSettings {
        if (!loaded.isCompleted) runBlocking { loaded.await() }
        return _portable.value
    }

    /** Changes phone settings. The change shows at once and is saved in the background, in order. */
    fun updateDevice(change: (DeviceSettings) -> DeviceSettings) {
        current()
        _device.updateAndGet(change)
        scope.launch { saveDevice() }
    }

    /** Like [updateDevice], but returns only once the change is on disk. For background work that may end soon. */
    suspend fun updateDeviceNow(change: (DeviceSettings) -> DeviceSettings) {
        awaitLoaded()
        _device.updateAndGet(change)
        saveDevice()
    }

    /** Changes the user's preferences, saves them to `meta` and refreshes the data snapshot that shows them. */
    fun updatePortable(change: (PortableSettings) -> PortableSettings) {
        currentPortable()
        _portable.updateAndGet { old -> withPlatesConverted(old, change(old)) }
        scope.launch {
            writeLock.withLock { savePortable(_portable.value) }
            // Preferences only change how the data is shown, so nothing is re-read (#60).
            Store.refresh()
        }
    }

    /**
     * Data tools › Reset settings to defaults (#41): the preferences go back to their defaults ([resetToDefaults]).
     * Workouts, photos, measurements, backups, folders and the backup schedule are untouched. Returns what was
     * there before, for Undo ([restorePreferences]).
     */
    fun resetPreferences(): Pair<PortableSettings, DeviceSettings> {
        val before = currentPortable() to current()
        updatePortable { it.resetToDefaults() }
        updateDevice { it.resetToDefaults() }
        return before
    }

    /** Undoes [resetPreferences]: only the values a reset changes are put back, so nothing newer is lost. */
    fun restorePreferences(before: Pair<PortableSettings, DeviceSettings>) {
        updatePortable { before.first }
        updateDevice { it.withPreferencesFrom(before.second) }
    }

    /**
     * A Settings page's ⋮ › Reset this section (#41): only [group]'s preferences go back to their defaults. Returns
     * what was there before, for Undo ([restoreGroup]).
     */
    fun resetGroup(group: PreferenceGroup): Pair<PortableSettings, DeviceSettings> {
        val before = currentPortable() to current()
        updatePortable { it.withGroupFrom(group, PortableSettings()) }
        updateDevice { it.withGroupFrom(group, DeviceSettings()) }
        return before
    }

    /** Undoes [resetGroup]: only [group]'s values are put back, so a change made since on another page stays. */
    fun restoreGroup(group: PreferenceGroup, before: Pair<PortableSettings, DeviceSettings>) {
        updatePortable { it.withGroupFrom(group, before.first) }
        updateDevice { it.withGroupFrom(group, before.second) }
    }

    /** True when [group] has a value that isn't its default, so Reset this section would change something. */
    fun isGroupChanged(group: PreferenceGroup, portable: PortableSettings, device: DeviceSettings): Boolean =
        portable.withGroupFrom(group, PortableSettings()) != portable || device.withGroupFrom(group, DeviceSettings()) != device

    /**
     * Re-reads the preferences from the database. Called from `Store.reload()`, so a restore or an import that set
     * the weight unit is picked up with the rest of the data.
     */
    fun reloadPortable() {
        if (!::loaded.isInitialized || !loaded.isCompleted) return
        _portable.value = portableFrom(Store.db::getMeta)
    }

    /**
     * Removes the phone-only rows that `meta` kept after the move to DataStore in 1.0.21 (#98). It only runs once
     * DataStore has loaded, because loading is what copies them over: someone updating straight from 1.0.20 keeps
     * every value. It runs at start-up and after a restore, so an older backup's stale rows don't linger either.
     * No schema change is needed, and an older build simply finds no rows and falls back to its defaults.
     */
    fun dropLegacyDeviceRows() {
        if (deviceStoreReady) Store.db.deleteMeta(DEVICE_KEYS)
    }

    private suspend fun saveDevice() = writeLock.withLock {
        val values = _device.value.toMap()
        store.edit { p ->
            values.forEach { (k, v) ->
                val key = stringPreferencesKey(k)
                if (v == null) p.remove(key) else p[key] = v
            }
        }
    }

    /** Writes every preference in one transaction, so a change is saved whole or not at all. */
    private fun savePortable(s: PortableSettings) {
        Store.db.setMetas(mapOf(
            P_WEIGHT_UNIT to s.weightUnit,
            P_WEIGHT_UNIT_MANUAL to if (s.weightUnitManual) "1" else null,
            P_WEIGHT_INCREMENT to s.weightIncrementKg?.toString(),
            P_DISTANCE_UNIT to s.distanceUnit.takeIf { it != DistanceUnits.KM },
            P_LENGTH_UNIT to s.lengthUnit.takeIf { it != LengthUnits.CM },
            P_KEEP_SCREEN_ON to if (s.keepScreenOn) null else "0",
            P_CELEBRATE_PRS to if (s.celebratePrs) null else "0",
            P_AUTOFILL to s.autofillSource.takeIf { it != PortableSettings.AUTOFILL_LAST },
            P_AUTO_SELECT_NEXT to if (s.autoSelectNext) "1" else null,
            P_BACKUP_TIMESTAMP to if (s.backupTimestamp) null else "0",
            P_WARMUPS_COUNT to if (s.warmupsCount) "1" else null,
            P_E1RM_FORMULA to s.e1rmFormula.takeIf { it != "auto" },
            P_E1RM_MAX_REPS to s.e1rmMaxReps.takeIf { it > 0 }?.toString(),
            P_BAR_KG to s.barKg?.toString(),
            P_COUNT_BAR to if (s.countBar) null else "0",
            P_PLATES to s.plates,
            P_SHOW_SET_TYPE to if (s.showSetType) null else "0",
            P_EFFORT_MODE to s.effortMode.takeIf { it != Effort.OFF },
            P_WEEK_START to s.weekStart.takeIf { it != 1 }?.toString(),
            P_HOME_CATEGORIES to if (s.homeShowCategories) null else "0",
            P_HOME_SETS to s.homeSetsShown.takeIf { it != 0 }?.toString(),
            P_LAST_ROUTINE to s.lastRoutineId.takeIf { it != 0L }?.toString(),
            P_TIMER_AUTO to if (s.workoutTimerAuto) "1" else null,
            P_REST_SECONDS to s.restSeconds.takeIf { it != 90 }?.toString(),
            P_REST_AUTO to if (s.restAutoStart) "1" else null,
            P_REST_VIBRATE to if (s.restVibrate) null else "0",
            P_REST_SOUND to if (s.restSound) null else "0",
            P_REST_VOLUME to s.restVolume.takeIf { it != 80 }?.toString(),
            P_MARK_COMPLETE to if (s.markComplete) "1" else null,
            P_GRAPH_KINDS to s.graphKinds?.takeIf { it.isNotBlank() },
            P_PINNED_GRAPHS to s.pinnedGraphs?.takeIf { it.isNotBlank() },
            P_GRAPH_COMPARE to s.graphCompare?.takeIf { it.isNotBlank() },
            P_PROFILE_SEX to s.profileSex?.takeIf { it.isNotBlank() },
            P_PROFILE_BIRTH to s.profileBirthDate?.takeIf { it.isNotBlank() },
            P_EMERGENCY_NAME to s.emergencyName?.takeIf { it.isNotBlank() },
            P_EMERGENCY_ALLERGIES to s.emergencyAllergies?.takeIf { it.isNotBlank() },
            P_EMERGENCY_CONDITIONS to s.emergencyConditions?.takeIf { it.isNotBlank() },
            P_EMERGENCY_CONTACT to s.emergencyContact?.takeIf { it.isNotBlank() },
            P_PROFILE_HEIGHT to s.profileHeightCm?.toString(),
            P_PROFILE_LIFESTYLE to s.profileLifestyle?.takeIf { it.isNotBlank() },
            P_ACTIVITY_STEPS to if (s.activityFromSteps) "1" else null,
            P_HC_STEPS to if (s.hcSteps) "1" else null,
            P_HC_INTAKE to if (s.hcIntake) "1" else null,
            P_HC_WEIGHT to if (s.hcWeight) "1" else null,
            P_HC_BODY_FAT to if (s.hcBodyFat) "1" else null,
            P_HC_ACTIVE to if (s.hcActive) "1" else null,
            P_HC_RESTING_HR to if (s.hcRestingHr) "1" else null,
            P_HC_SLEEP to if (s.hcSleep) "1" else null,
            P_HC_EXERCISE to if (s.hcExercise) "1" else null,
            P_HC_BLOOD_PRESSURE to if (s.hcBloodPressure) "1" else null,
            P_HC_GLUCOSE to if (s.hcGlucose) "1" else null,
            P_HC_SPO2 to if (s.hcSpo2) "1" else null,
            P_HC_HRV to if (s.hcHrv) "1" else null,
            P_HC_TEMPERATURE to if (s.hcTemperature) "1" else null,
            P_HC_WATER to if (s.hcWater) "1" else null,
            P_HC_CAFFEINE to if (s.hcCaffeine) "1" else null,
            P_HC_CYCLE to if (s.hcCycle) "1" else null,
            P_CYCLE_BANDS_OFF to if (s.cycleBandsOff) "1" else null,
            P_HC_LABS to if (s.hcLabs) "1" else null,
            P_HCW_WEIGHT to if (s.hcWriteWeight) "1" else null,
            P_HCW_BODY_FAT to if (s.hcWriteBodyFat) "1" else null,
            P_HCW_WORKOUTS to if (s.hcWriteWorkouts) "1" else null,
            P_CALORIE_GOAL to s.calorieGoal?.takeIf { it.isNotBlank() },
            P_CALORIE_TARGET to s.calorieTargetKcal?.toString(),
            P_MACRO_PROTEIN to s.macroProteinG?.toString(),
            P_MACRO_FAT to s.macroFatG?.toString(),
            P_MACRO_CARB to s.macroCarbG?.toString(),
            P_BMR_FORMULA to s.bmrFormula.takeIf { it != "auto" },
            P_TRAINING_PLAN to s.trainingPlan?.takeIf { it.isNotBlank() },
            P_CALORIE_MODE to s.calorieMode.takeIf { it != EnergyEstimates.MODE_SAME },
            P_TDEE_SOURCE to s.tdeeSource.takeIf { it != EnergyEstimates.SOURCE_ESTIMATE },
            P_HOME_CALORIES to if (s.homeShowCalories) null else "0",
            P_HOME_READINESS to if (s.homeShowReadiness) null else "0",
            P_HOME_DRINKS to if (s.homeShowDrinks) null else "0",
            P_BEDTIME to s.bedtime.takeIf { it != Drinks.DEFAULT_BEDTIME },
            P_PHOTO_POSE to MediaPrefs.storeDefaultPose(s.photoDefaultPose),
            P_PHOTO_GROUP to s.photoGroupBy.takeIf { it != MediaPrefs.GROUP_MONTH },
            P_REMEMBER_VIDEO to if (s.rememberVideoOpts) null else "0",
            P_VIDEO_OPTS to s.videoOpts?.takeIf { it.isNotBlank() },
            P_PDF_STYLE to if (s.pdfDark) null else "light",
            P_PDF_PHOTOS to s.pdfPhotosPerDay.takeIf { it != 2 }?.toString()
        ))
    }

    // ---------- Storage keys. The names match the old `meta` keys, so the migration is a straight copy. ----------

    private const val D_BACKUP_FOLDER = "backup_folder"
    private const val D_AUTO_SYNC = "auto_sync"
    private const val D_AUTO_FOLDER = "auto_backup_folder"
    private const val D_AUTO_DAYS = "auto_backup_days"
    private const val D_AUTO_KEEP = "auto_backup_keep"
    private const val D_AUTO_LAST = "auto_backup_last"
    private const val D_AUTO_ERROR = "auto_backup_error"
    private const val D_AFTER_CHANGES = "auto_backup_on_change"
    private const val D_DIRTY = "auto_backup_dirty"
    private const val D_IMPORT_NAME = "last_import_name"
    private const val D_IMPORT_AT = "last_import_at"
    private const val D_IMPORT_MODIFIED = "last_import_modified"
    private const val D_SAFETY_AT = "safety_at"
    private const val D_SAFETY_REASON = "safety_reason"
    private const val D_LAST_RESULT = "last_result"
    private const val D_CHART_TAP = "chart_hint_tap"
    private const val D_CHART_EXPAND = "chart_hint_expand"
    private const val D_REST_SOUND_URI = "rest_sound_uri"
    private const val D_CALENDAR_FILTER = "calendar_filter"
    private const val D_SETUP_DONE = "setup_done"
    private const val D_WHATS_NEW_SEEN = "whats_new_seen"
    private const val D_STEPS_LAST = "steps_last"
    private const val D_HC_SYNCED = "hc_synced_at"
    private const val D_BACKUP_PHOTOS = "backup_photos"
    private const val D_HC_HISTORY = "hc_history_kinds"
    private const val D_HC_LAB_IDS = "hc_lab_ids"
    private const val D_MED_REMINDERS = "med_reminders"
    private const val D_MED_NAMES = "med_names_lock_screen"
    private const val D_MED_LOCK = "med_lock"
    private const val D_MED_NUDGE = "med_nudge_minutes"
    private const val D_MED_SNOOZES = "med_snoozes"
    private const val D_CAM_OPACITY = "camera_photo_opacity"
    private const val D_CAM_LINE = "camera_outline_line"

    /** Every phone-only key, as it was named in `meta` before 1.0.21. */
    internal val DEVICE_KEYS = listOf(
        D_BACKUP_FOLDER, D_AUTO_SYNC, D_AUTO_FOLDER, D_AUTO_DAYS, D_AUTO_KEEP, D_AUTO_LAST, D_AUTO_ERROR,
        D_AFTER_CHANGES, D_DIRTY, D_IMPORT_NAME, D_IMPORT_AT, D_IMPORT_MODIFIED, D_SAFETY_AT, D_SAFETY_REASON,
        D_LAST_RESULT
    )

    private const val P_WEIGHT_UNIT = "weight_unit"
    private const val P_WEIGHT_UNIT_MANUAL = "weight_unit_manual"
    private const val P_WEIGHT_INCREMENT = "weight_increment"
    private const val P_DISTANCE_UNIT = "distance_unit"
    private const val P_LENGTH_UNIT = "length_unit"
    private const val P_KEEP_SCREEN_ON = "keep_screen_on"
    private const val P_CELEBRATE_PRS = "celebrate_prs"
    private const val P_AUTOFILL = "autofill_source"
    private const val P_AUTO_SELECT_NEXT = "auto_select_next"
    private const val P_BACKUP_TIMESTAMP = "backup_timestamp"
    private const val P_WARMUPS_COUNT = "warmups_count"
    private const val P_E1RM_FORMULA = "e1rm_formula"
    private const val P_E1RM_MAX_REPS = "e1rm_max_reps"
    private const val P_BAR_KG = "bar_kg"
    private const val P_COUNT_BAR = "count_bar"
    private const val P_PLATES = "plates"
    private const val P_SHOW_SET_TYPE = "show_set_type"
    private const val P_EFFORT_MODE = "effort_mode"
    private const val P_WEEK_START = "week_start"
    private const val P_HOME_CATEGORIES = "home_show_categories"
    private const val P_HOME_SETS = "home_sets_shown"
    private const val P_LAST_ROUTINE = "last_routine"
    private const val P_TIMER_AUTO = "workout_timer_auto"
    private const val P_REST_SECONDS = "rest_seconds"
    private const val P_REST_AUTO = "rest_auto_start"
    private const val P_REST_VIBRATE = "rest_vibrate"
    private const val P_REST_SOUND = "rest_sound"
    private const val P_REST_VOLUME = "rest_volume"
    private const val P_MARK_COMPLETE = "mark_complete"
    private const val P_GRAPH_KINDS = "graph_kinds"
    private const val P_PINNED_GRAPHS = "pinned_graphs"
    private const val P_GRAPH_COMPARE = "graph_compare"
    private const val P_PROFILE_SEX = "profile_sex"
    // The profile and the TDEE calculator (#172, #173).
    private const val P_PROFILE_BIRTH = "profile_birth_date"
    private const val P_EMERGENCY_NAME = "emergency_name"
    private const val P_EMERGENCY_ALLERGIES = "emergency_allergies"
    private const val P_EMERGENCY_CONDITIONS = "emergency_conditions"
    private const val P_EMERGENCY_CONTACT = "emergency_contact"
    private const val P_PROFILE_HEIGHT = "profile_height_cm"
    private const val P_PROFILE_LIFESTYLE = "profile_lifestyle"
    private const val P_ACTIVITY_STEPS = "activity_from_steps"
    private const val P_HC_STEPS = "hc_steps"
    private const val P_HC_INTAKE = "hc_intake"
    private const val P_HC_WEIGHT = "hc_weight"
    private const val P_HC_BODY_FAT = "hc_body_fat"
    private const val P_HC_ACTIVE = "hc_active"
    private const val P_HC_RESTING_HR = "hc_resting_hr"
    private const val P_HC_EXERCISE = "hc_exercise"
    private const val P_HC_BLOOD_PRESSURE = "hc_blood_pressure"
    private const val P_HC_GLUCOSE = "hc_glucose"
    private const val P_HC_SPO2 = "hc_spo2"
    private const val P_HC_HRV = "hc_hrv"
    private const val P_HC_TEMPERATURE = "hc_temperature"
    private const val P_HC_WATER = "hc_water"
    private const val P_HC_CAFFEINE = "hc_caffeine"
    private const val P_HC_CYCLE = "hc_cycle"
    private const val P_CYCLE_BANDS_OFF = "cycle_bands_off"
    private const val P_HC_LABS = "hc_labs"
    private const val P_HCW_WEIGHT = "hc_write_weight"
    private const val P_HCW_BODY_FAT = "hc_write_body_fat"
    private const val P_HCW_WORKOUTS = "hc_write_workouts"
    private const val P_HC_SLEEP = "hc_sleep"
    private const val P_CALORIE_GOAL = "calorie_goal"
    private const val P_CALORIE_TARGET = "calorie_target_kcal"
    private const val P_MACRO_PROTEIN = "macro_protein_g"
    private const val P_MACRO_FAT = "macro_fat_g"
    private const val P_MACRO_CARB = "macro_carb_g"
    private const val P_BMR_FORMULA = "bmr_formula"
    private const val P_TRAINING_PLAN = "training_plan"
    private const val P_CALORIE_MODE = "calorie_mode"
    private const val P_TDEE_SOURCE = "tdee_source"
    private const val P_HOME_CALORIES = "home_show_calories"
    private const val P_HOME_READINESS = "home_show_readiness"
    private const val P_HOME_DRINKS = "home_show_drinks"
    private const val P_BEDTIME = "bedtime"
    // Progress photos and media (#46), with the names the issue gave them.
    private const val P_PHOTO_POSE = "photo_default_pose"
    private const val P_PHOTO_GROUP = "photo_group_by"
    private const val P_REMEMBER_VIDEO = "remember_video_opts"
    private const val P_VIDEO_OPTS = "video_opts"
    private const val P_PDF_STYLE = "pdf_style"
    private const val P_PDF_PHOTOS = "pdf_photos_per_day"

    private fun bool(v: String?) = v == "1"

    private fun deviceFrom(get: (String) -> String?) = DeviceSettings(
        backupFolder = get(D_BACKUP_FOLDER),
        autoSync = bool(get(D_AUTO_SYNC)),
        autoBackupFolder = get(D_AUTO_FOLDER),
        autoBackupDays = get(D_AUTO_DAYS)?.toIntOrNull() ?: 0,
        autoBackupKeep = get(D_AUTO_KEEP)?.toIntOrNull() ?: 5,
        autoBackupLast = get(D_AUTO_LAST)?.toLongOrNull(),
        autoBackupError = get(D_AUTO_ERROR),
        backupAfterChanges = bool(get(D_AFTER_CHANGES)),
        backupDirty = bool(get(D_DIRTY)),
        backupPhotos = bool(get(D_BACKUP_PHOTOS)),
        lastImportName = get(D_IMPORT_NAME),
        lastImportAt = get(D_IMPORT_AT)?.toLongOrNull(),
        lastImportModified = get(D_IMPORT_MODIFIED)?.toLongOrNull(),
        safetyAt = get(D_SAFETY_AT)?.toLongOrNull(),
        safetyReason = get(D_SAFETY_REASON),
        lastResult = get(D_LAST_RESULT),
        chartTapSeen = bool(get(D_CHART_TAP)),
        chartExpandSeen = bool(get(D_CHART_EXPAND)),
        restSoundUri = get(D_REST_SOUND_URI),
        calendarFilter = get(D_CALENDAR_FILTER),
        setupDone = bool(get(D_SETUP_DONE)),
        whatsNewSeen = get(D_WHATS_NEW_SEEN)?.toLongOrNull() ?: 0,
        stepsLast = get(D_STEPS_LAST),
        hcSyncedAt = get(D_HC_SYNCED)?.toLongOrNull(),
        hcHistoryKinds = get(D_HC_HISTORY),
        hcLabIds = get(D_HC_LAB_IDS),
        medReminders = get(D_MED_REMINDERS) != "0",
        medNamesOnLockScreen = bool(get(D_MED_NAMES)),
        medLock = bool(get(D_MED_LOCK)),
        medNudgeMinutes = get(D_MED_NUDGE)?.toIntOrNull()?.coerceIn(0, 240) ?: 30,
        medSnoozes = get(D_MED_SNOOZES),
        cameraPhotoOpacity = get(D_CAM_OPACITY)?.toIntOrNull()?.coerceIn(0, 60) ?: 30,
        cameraOutlineLine = get(D_CAM_LINE)?.toIntOrNull()?.takeIf { it in BodyOutline.THIN..BodyOutline.BOLD } ?: BodyOutline.BOLD
    )

    private fun DeviceSettings.toMap(): Map<String, String?> = mapOf(
        D_BACKUP_FOLDER to backupFolder,
        D_AUTO_SYNC to if (autoSync) "1" else "0",
        D_AUTO_FOLDER to autoBackupFolder,
        D_AUTO_DAYS to autoBackupDays.toString(),
        D_AUTO_KEEP to autoBackupKeep.toString(),
        D_AUTO_LAST to autoBackupLast?.toString(),
        D_AUTO_ERROR to autoBackupError,
        D_AFTER_CHANGES to if (backupAfterChanges) "1" else "0",
        D_DIRTY to if (backupDirty) "1" else null,
        D_BACKUP_PHOTOS to if (backupPhotos) "1" else null,
        D_IMPORT_NAME to lastImportName,
        D_IMPORT_AT to lastImportAt?.toString(),
        D_IMPORT_MODIFIED to lastImportModified?.toString(),
        D_SAFETY_AT to safetyAt?.toString(),
        D_SAFETY_REASON to safetyReason,
        D_LAST_RESULT to lastResult,
        D_CHART_TAP to if (chartTapSeen) "1" else null,
        D_CHART_EXPAND to if (chartExpandSeen) "1" else null,
        D_REST_SOUND_URI to restSoundUri,
        D_CALENDAR_FILTER to calendarFilter,
        D_SETUP_DONE to if (setupDone) "1" else null,
        D_WHATS_NEW_SEEN to whatsNewSeen.takeIf { it > 0 }?.toString(),
        D_STEPS_LAST to stepsLast,
        D_HC_SYNCED to hcSyncedAt?.toString(),
        D_HC_HISTORY to hcHistoryKinds,
        D_HC_LAB_IDS to hcLabIds,
        D_MED_REMINDERS to if (medReminders) null else "0",
        D_MED_NAMES to if (medNamesOnLockScreen) "1" else null,
        D_MED_LOCK to if (medLock) "1" else null,
        D_MED_NUDGE to medNudgeMinutes.takeIf { it != 30 }?.toString(),
        D_MED_SNOOZES to medSnoozes,
        D_CAM_OPACITY to cameraPhotoOpacity.takeIf { it != 30 }?.toString(),
        D_CAM_LINE to cameraOutlineLine.takeIf { it != BodyOutline.BOLD }?.toString()
    )

    private fun portableFrom(get: (String) -> String?) = PortableSettings(
        weightUnit = get(P_WEIGHT_UNIT) ?: "kg",
        weightUnitManual = get(P_WEIGHT_UNIT_MANUAL) != null,
        weightIncrementKg = get(P_WEIGHT_INCREMENT)?.toDoubleOrNull()?.takeIf { it > 0 },
        // An unknown unit (from a later build's backup) falls back to the default.
        distanceUnit = DistanceUnits.of(get(P_DISTANCE_UNIT)) ?: DistanceUnits.KM,
        lengthUnit = LengthUnits.of(get(P_LENGTH_UNIT)) ?: LengthUnits.CM,
        keepScreenOn = get(P_KEEP_SCREEN_ON) != "0",
        celebratePrs = get(P_CELEBRATE_PRS) != "0",
        // An unknown value (say, "routine" from a later build's backup) falls back to the default.
        autofillSource = get(P_AUTOFILL)?.takeIf { it == PortableSettings.AUTOFILL_EMPTY } ?: PortableSettings.AUTOFILL_LAST,
        autoSelectNext = bool(get(P_AUTO_SELECT_NEXT)),
        backupTimestamp = get(P_BACKUP_TIMESTAMP) != "0",
        warmupsCount = bool(get(P_WARMUPS_COUNT)),
        // An unknown formula (from a later build's backup) falls back to Automatic.
        e1rmFormula = Records.Formula.of(get(P_E1RM_FORMULA)).key,
        e1rmMaxReps = get(P_E1RM_MAX_REPS)?.toIntOrNull()?.takeIf { it in 1..Records.MAX_ESTIMATE_REPS } ?: 0,
        barKg = get(P_BAR_KG)?.toDoubleOrNull()?.takeIf { it >= 0 },
        countBar = get(P_COUNT_BAR) != "0",
        plates = get(P_PLATES)?.takeIf { it.isNotBlank() },
        showSetType = get(P_SHOW_SET_TYPE) != "0",
        effortMode = get(P_EFFORT_MODE)?.takeIf { it == Effort.RPE || it == Effort.RIR } ?: Effort.OFF,
        weekStart = get(P_WEEK_START)?.toIntOrNull()?.takeIf { it in 1..7 } ?: 1,
        homeShowCategories = get(P_HOME_CATEGORIES) != "0",
        homeSetsShown = get(P_HOME_SETS)?.toIntOrNull()?.takeIf { it in 1..10 } ?: 0,
        lastRoutineId = get(P_LAST_ROUTINE)?.toLongOrNull() ?: 0L,
        workoutTimerAuto = bool(get(P_TIMER_AUTO)),
        // Any exact length from 1 s to 60 min (#105).
        restSeconds = get(P_REST_SECONDS)?.toIntOrNull()?.takeIf { it in 1..3600 } ?: 90,
        restAutoStart = bool(get(P_REST_AUTO)),
        restVibrate = get(P_REST_VIBRATE) != "0",
        restSound = get(P_REST_SOUND) != "0",
        restVolume = get(P_REST_VOLUME)?.toIntOrNull()?.coerceIn(10, 100) ?: 80,
        markComplete = bool(get(P_MARK_COMPLETE)),
        graphKinds = get(P_GRAPH_KINDS)?.takeIf { it.isNotBlank() },
        pinnedGraphs = get(P_PINNED_GRAPHS)?.takeIf { it.isNotBlank() },
        graphCompare = get(P_GRAPH_COMPARE)?.takeIf { it.isNotBlank() },
        profileSex = get(P_PROFILE_SEX)?.takeIf { it.isNotBlank() },
        // Each profile value is checked as it's read, so a bad or newer backup can't break the formulas (#172).
        profileBirthDate = get(P_PROFILE_BIRTH)?.takeIf { Dates.parse(it) != null },
        emergencyName = get(P_EMERGENCY_NAME)?.takeIf { it.isNotBlank() },
        emergencyAllergies = get(P_EMERGENCY_ALLERGIES)?.takeIf { it.isNotBlank() },
        emergencyConditions = get(P_EMERGENCY_CONDITIONS)?.takeIf { it.isNotBlank() },
        emergencyContact = get(P_EMERGENCY_CONTACT)?.takeIf { it.isNotBlank() },
        profileHeightCm = get(P_PROFILE_HEIGHT)?.toDoubleOrNull()?.takeIf { it in 50.0..260.0 },
        profileLifestyle = Energy.Lifestyle.of(get(P_PROFILE_LIFESTYLE))?.key,
        activityFromSteps = bool(get(P_ACTIVITY_STEPS)),
        hcSteps = bool(get(P_HC_STEPS)) && bool(get(P_ACTIVITY_STEPS)),
        hcIntake = bool(get(P_HC_INTAKE)),
        hcWeight = bool(get(P_HC_WEIGHT)),
        hcBodyFat = bool(get(P_HC_BODY_FAT)),
        hcActive = bool(get(P_HC_ACTIVE)),
        hcRestingHr = bool(get(P_HC_RESTING_HR)),
        hcSleep = bool(get(P_HC_SLEEP)),
        hcExercise = bool(get(P_HC_EXERCISE)),
        hcBloodPressure = bool(get(P_HC_BLOOD_PRESSURE)),
        hcGlucose = bool(get(P_HC_GLUCOSE)),
        hcSpo2 = bool(get(P_HC_SPO2)),
        hcHrv = bool(get(P_HC_HRV)),
        hcTemperature = bool(get(P_HC_TEMPERATURE)),
        hcWater = bool(get(P_HC_WATER)),
        hcCaffeine = bool(get(P_HC_CAFFEINE)),
        hcCycle = bool(get(P_HC_CYCLE)),
        cycleBandsOff = bool(get(P_CYCLE_BANDS_OFF)),
        hcLabs = bool(get(P_HC_LABS)),
        hcWriteWeight = bool(get(P_HCW_WEIGHT)),
        hcWriteBodyFat = bool(get(P_HCW_BODY_FAT)),
        hcWriteWorkouts = bool(get(P_HCW_WORKOUTS)),
        calorieGoal = Energy.Goal.decode(get(P_CALORIE_GOAL))?.encode(),
        calorieTargetKcal = get(P_CALORIE_TARGET)?.toIntOrNull()?.takeIf { it in 500..10000 },
        macroProteinG = get(P_MACRO_PROTEIN)?.toIntOrNull()?.takeIf { it in 0..1000 },
        macroFatG = get(P_MACRO_FAT)?.toIntOrNull()?.takeIf { it in 0..1000 },
        macroCarbG = get(P_MACRO_CARB)?.toIntOrNull()?.takeIf { it in 0..2000 },
        bmrFormula = Energy.Formula.of(get(P_BMR_FORMULA)).key,
        trainingPlan = Energy.Plan.decode(get(P_TRAINING_PLAN))?.encode(),
        calorieMode = if (get(P_CALORIE_MODE) == EnergyEstimates.MODE_TRAINING) EnergyEstimates.MODE_TRAINING else EnergyEstimates.MODE_SAME,
        tdeeSource = if (get(P_TDEE_SOURCE) == EnergyEstimates.SOURCE_MEASURED) EnergyEstimates.SOURCE_MEASURED else EnergyEstimates.SOURCE_ESTIMATE,
        homeShowCalories = get(P_HOME_CALORIES) != "0",
        homeShowReadiness = get(P_HOME_READINESS) != "0",
        homeShowDrinks = get(P_HOME_DRINKS) != "0",
        bedtime = get(P_BEDTIME)?.takeIf { Drinks.minutesOf(it) != null } ?: Drinks.DEFAULT_BEDTIME,
        // Unknown values (from a later build's backup) fall back to the defaults (#46).
        photoDefaultPose = MediaPrefs.defaultPoseOf(get(P_PHOTO_POSE)),
        photoGroupBy = MediaPrefs.groupOf(get(P_PHOTO_GROUP)),
        rememberVideoOpts = get(P_REMEMBER_VIDEO) != "0",
        videoOpts = get(P_VIDEO_OPTS)?.takeIf { it.isNotBlank() },
        pdfDark = get(P_PDF_STYLE) != "light",
        pdfPhotosPerDay = MediaPrefs.photosPerDayOf(get(P_PDF_PHOTOS))
    )
}

/**
 * Copies the phone-only settings from `meta` into DataStore the first time 1.0.21 or later runs, keeping every
 * value. [Settings.dropLegacyDeviceRows] then removes the rows (#98). Keep this while anyone may still update from
 * 1.0.20 or earlier.
 */
private object MetaToDeviceMigration : DataMigration<Preferences> {
    private val MIGRATED = stringPreferencesKey("migrated_from_meta")

    override suspend fun shouldMigrate(currentData: Preferences): Boolean = currentData[MIGRATED] == null

    override suspend fun migrate(currentData: Preferences): Preferences {
        val p = currentData.toMutablePreferences()
        Settings.DEVICE_KEYS.forEach { k -> Store.db.getMeta(k)?.let { p[stringPreferencesKey(k)] = it } }
        p[MIGRATED] = "1"
        return p
    }

    override suspend fun cleanUp() {}
}

/**
 * The user's preferences at their defaults (#41). What they made rather than set stays: pinned graphs and their
 * comparisons, the routine last open, the profile (#172), the calorie target set from it (#173) and the emergency
 * card's details (#215).
 */
internal fun PortableSettings.resetToDefaults(): PortableSettings = PortableSettings(
    pinnedGraphs = pinnedGraphs,
    graphCompare = graphCompare,
    lastRoutineId = lastRoutineId,
    profileSex = profileSex,
    profileBirthDate = profileBirthDate,
    emergencyName = emergencyName,
    emergencyAllergies = emergencyAllergies,
    emergencyConditions = emergencyConditions,
    emergencyContact = emergencyContact,
    profileHeightCm = profileHeightCm,
    profileLifestyle = profileLifestyle,
    activityFromSteps = activityFromSteps,
    hcSteps = hcSteps,
    hcIntake = hcIntake,
    hcWeight = hcWeight,
    hcBodyFat = hcBodyFat,
    hcActive = hcActive,
    hcRestingHr = hcRestingHr,
    hcSleep = hcSleep,
    hcExercise = hcExercise,
    hcBloodPressure = hcBloodPressure,
    hcGlucose = hcGlucose,
    hcSpo2 = hcSpo2,
    hcHrv = hcHrv,
    hcTemperature = hcTemperature,
    hcWater = hcWater,
    hcCaffeine = hcCaffeine,
    hcCycle = hcCycle,
    hcLabs = hcLabs,
    hcWriteWeight = hcWriteWeight,
    hcWriteBodyFat = hcWriteBodyFat,
    hcWriteWorkouts = hcWriteWorkouts,
    calorieGoal = calorieGoal,
    calorieTargetKcal = calorieTargetKcal,
    macroProteinG = macroProteinG,
    macroFatG = macroFatG,
    macroCarbG = macroCarbG,
    bmrFormula = bmrFormula,
    trainingPlan = trainingPlan,
    calorieMode = calorieMode,
    tdeeSource = tdeeSource
)

/**
 * This phone's preferences at their defaults (#41): the rest-over sound and the calendar filter. Folders and their
 * permissions, the backup schedule, the last import, the safety copy and the app's own state are kept.
 */
internal fun DeviceSettings.resetToDefaults(): DeviceSettings = withPreferencesFrom(DeviceSettings())

/** These settings with [other]'s preferences, the values [resetToDefaults] changes. */
internal fun DeviceSettings.withPreferencesFrom(other: DeviceSettings): DeviceSettings =
    copy(restSoundUri = other.restSoundUri, calendarFilter = other.calendarFilter)

/** The preferences one Settings page holds, which its Reset this section resets together (#41). */
enum class PreferenceGroup {
    /** Home Screen Settings: categories and sets shown on the day log (#8). */
    HOME,
    /** The rest timer: length, auto-start, vibrate, sound, volume and the phone's rest-over sound (#20). */
    REST,
    /** Progress photos and media: default pose, grouping, slideshow and video options, the PDF report (#46). */
    MEDIA
}

/** These preferences with [group]'s values taken from [other]: defaults to reset, the old values for Undo. */
internal fun PortableSettings.withGroupFrom(group: PreferenceGroup, other: PortableSettings): PortableSettings = when (group) {
    PreferenceGroup.HOME -> copy(
        homeShowCategories = other.homeShowCategories, homeSetsShown = other.homeSetsShown, homeShowCalories = other.homeShowCalories,
        homeShowReadiness = other.homeShowReadiness, homeShowDrinks = other.homeShowDrinks
    )
    PreferenceGroup.REST -> copy(
        restSeconds = other.restSeconds, restAutoStart = other.restAutoStart, restVibrate = other.restVibrate,
        restSound = other.restSound, restVolume = other.restVolume
    )
    PreferenceGroup.MEDIA -> copy(
        photoDefaultPose = other.photoDefaultPose, photoGroupBy = other.photoGroupBy,
        rememberVideoOpts = other.rememberVideoOpts, videoOpts = other.videoOpts,
        pdfDark = other.pdfDark, pdfPhotosPerDay = other.pdfPhotosPerDay
    )
}

/** This phone's settings with [group]'s values taken from [other]. Only the rest timer keeps one here, its sound. */
internal fun DeviceSettings.withGroupFrom(group: PreferenceGroup, other: DeviceSettings): DeviceSettings = when (group) {
    PreferenceGroup.REST -> copy(restSoundUri = other.restSoundUri)
    PreferenceGroup.HOME, PreferenceGroup.MEDIA -> this
}

/**
 * Switching between kg and lbs converts the plate calculator's plate list (#117), the only weight setting stored in
 * the display unit rather than in kg. A list the same change edited is left as it was given.
 */
internal fun withPlatesConverted(old: PortableSettings, new: PortableSettings): PortableSettings {
    if (old.weightUnit == new.weightUnit || new.plates == null || new.plates != old.plates) return new
    val converted = new.plates.split(',', ' ', ';').mapNotNull { it.trim().replace(',', '.').toDoubleOrNull() }
        .joinToString(", ") { fmtNum(WeightUnits.convert(it, old.weightUnit, new.weightUnit), 2) }
    return new.copy(plates = converted.ifBlank { null })
}
