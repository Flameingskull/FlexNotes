package com.fitlens.companion.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

/**
 * Reading Google Health Connect (#184), so values other apps record on the phone (steps, #185; what was eaten, #186;
 * bodyweight and body fat, #187; active calories, #188; resting heart rate and sleep, #189; vitals, #212) can be the primary source
 * for what FlexNotes records and calculates, and exercise sessions become cardio sets (#190, `HealthSessions`).
 * Health Connect runs on the phone, so this stays local: no internet permission, accounts or cloud. Since #191 what
 * FlexNotes logs can also be written to it (`HealthWrite`), each type only when the user switches it on.
 *
 * For each type switched to Health Connect in Settings ([Kind.chosen]), a sync reads the last [DAYS] days (the history
 * Health Connect allows by default). Totals are grouped by local day, so Health Connect applies the user's app
 * priority and a watch and a phone aren't counted twice; sleep is grouped by the day it ends. Bodyweight, body fat and
 * the vitals (blood pressure, glucose, blood oxygen, HRV and temperature, #212) are readings, each kept at its own time. Everything is kept as body values from [SOURCE]
 * (`Store.replaceHealthConnectDays`), replaced at each sync; typed values are never touched, and which one counts
 * follows `Sources.dayValues`.
 *
 * A sync runs when FlexNotes comes to the screen (at most every [MIN_GAP_MS]), from Settings › Health Connect's Sync
 * now, and hourly in the background when the user allowed background reading.
 */
object HealthConnect {
    const val SOURCE = Sources.HEALTH_CONNECT

    /** Health Connect's package: an app on Android 13 and earlier, part of the system from Android 14. */
    const val PROVIDER = "com.google.android.apps.healthdata"

    /** Lets the hourly sync read while FlexNotes isn't on screen. Asked for with the types; optional. */
    const val BACKGROUND = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"

    /**
     * Lets FlexNotes read data older than the [DAYS] Health Connect allows by default (#192). Asked for on its own, from
     * Settings › Health Connect; without it only the last [DAYS] days are ever read.
     */
    const val HISTORY = "android.permission.health.READ_HEALTH_DATA_HISTORY"

    /** The earliest day the full history is read from (#192). */
    val HISTORY_FROM: LocalDate = LocalDate.of(2010, 1, 1)

    /** Days read at each sync, today included. */
    const val DAYS = 30

    /** The least time between two syncs started by FlexNotes coming to the screen. */
    const val MIN_GAP_MS = 10 * 60_000L

    /** A night's sleep is dated by the day it ends: sleep from this hour the day before up to this hour (#189). */
    const val SLEEP_DAY_STARTS = 18

    private const val WORK = "fitlens_health_connect"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    private val _syncing = MutableStateFlow(false)

    /** A sync is running (Settings › Health Connect shows it). */
    val syncing: StateFlow<Boolean> = _syncing

    /** Whether Health Connect can be used on this phone. */
    enum class Status { AVAILABLE, NOT_INSTALLED, UPDATE_NEEDED, UNSUPPORTED }

    /**
     * A type of data FlexNotes reads: the record it's read from, the daily totals asked for (none for readings, which
     * are read one by one), the records it also reads where allowed ([also]: a session's distance), and the setting
     * that makes Health Connect its source.
     */
    enum class Kind(
        val record: KClass<out Record>,
        val metrics: Set<AggregateMetric<*>> = emptySet(),
        val also: Set<KClass<out Record>> = emptySet()
    ) {
        STEPS(StepsRecord::class, setOf(StepsRecord.COUNT_TOTAL)),
        INTAKE(
            NutritionRecord::class,
            setOf(NutritionRecord.ENERGY_TOTAL, NutritionRecord.PROTEIN_TOTAL, NutritionRecord.TOTAL_FAT_TOTAL, NutritionRecord.TOTAL_CARBOHYDRATE_TOTAL)
        ),
        WEIGHT(WeightRecord::class),
        BODY_FAT(BodyFatRecord::class),
        ACTIVE(ActiveCaloriesBurnedRecord::class, setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)),
        RESTING_HR(RestingHeartRateRecord::class, setOf(RestingHeartRateRecord.BPM_AVG)),
        SLEEP(SleepSessionRecord::class, setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL)),
        EXERCISE(ExerciseSessionRecord::class, also = setOf(DistanceRecord::class)),
        // Vitals (#212): readings, each kept at its own time.
        BLOOD_PRESSURE(BloodPressureRecord::class),
        GLUCOSE(BloodGlucoseRecord::class),
        SPO2(OxygenSaturationRecord::class),
        HRV(HeartRateVariabilityRmssdRecord::class),
        TEMPERATURE(BodyTemperatureRecord::class);

        /** The read permission Health Connect asks the user for. */
        val permission: String get() = HealthPermission.getReadPermission(record)

        /** Health Connect is chosen as this type's source. */
        fun chosen(p: PortableSettings): Boolean = when (this) {
            STEPS -> p.hcSteps && p.activityFromSteps
            INTAKE -> p.hcIntake
            WEIGHT -> p.hcWeight
            BODY_FAT -> p.hcBodyFat
            ACTIVE -> p.hcActive
            RESTING_HR -> p.hcRestingHr
            SLEEP -> p.hcSleep
            EXERCISE -> p.hcExercise
            BLOOD_PRESSURE -> p.hcBloodPressure
            GLUCOSE -> p.hcGlucose
            SPO2 -> p.hcSpo2
            HRV -> p.hcHrv
            TEMPERATURE -> p.hcTemperature
        }

        /**
         * [p] with Health Connect chosen ([on]) or not as this type's source. Steps are switched through `Steps`, which
         * also starts or stops the phone's step counter, so they're left as they are here.
         */
        fun choose(p: PortableSettings, on: Boolean): PortableSettings = when (this) {
            STEPS -> p
            INTAKE -> p.copy(hcIntake = on)
            WEIGHT -> p.copy(hcWeight = on)
            BODY_FAT -> p.copy(hcBodyFat = on)
            ACTIVE -> p.copy(hcActive = on)
            RESTING_HR -> p.copy(hcRestingHr = on)
            SLEEP -> p.copy(hcSleep = on)
            EXERCISE -> p.copy(hcExercise = on)
            BLOOD_PRESSURE -> p.copy(hcBloodPressure = on)
            GLUCOSE -> p.copy(hcGlucose = on)
            SPO2 -> p.copy(hcSpo2 = on)
            HRV -> p.copy(hcHrv = on)
            TEMPERATURE -> p.copy(hcTemperature = on)
        }
    }

    /** Body values read from Health Connect; those only it gives are made as FlexNotes's own the first time. */
    object Names {
        const val BODYWEIGHT = "Bodyweight"
        const val ACTIVE = "Active calories"
        const val ACTIVE_UNIT = "kcal"
        const val RESTING_HR = "Resting heart rate"
        const val RESTING_HR_UNIT = "bpm"
        const val SLEEP = "Sleep"
        const val SLEEP_UNIT = "h"

        // Vitals (#212): read from Health Connect or logged by hand, made as FlexNotes's own measurements.
        const val BP_SYSTOLIC = "Blood pressure (systolic)"
        const val BP_DIASTOLIC = "Blood pressure (diastolic)"
        const val BP_UNIT = "mmHg"
        const val GLUCOSE = "Blood glucose"
        const val GLUCOSE_UNIT = VitalUnits.MMOL
        const val SPO2 = "Blood oxygen"
        const val SPO2_UNIT = "%"
        const val HRV = "Heart rate variability"
        const val HRV_UNIT = "ms"
        const val TEMPERATURE = "Body temperature"
        const val TEMPERATURE_UNIT = VitalUnits.CELSIUS

        /** Every vital, name and unit, in the order "Add vitals" lists them. */
        val vitals: List<Pair<String, String>> = listOf(
            BP_SYSTOLIC to BP_UNIT, BP_DIASTOLIC to BP_UNIT, GLUCOSE to GLUCOSE_UNIT, SPO2 to SPO2_UNIT,
            HRV to HRV_UNIT, TEMPERATURE to TEMPERATURE_UNIT
        )
    }

    /** What a sync did. */
    enum class Outcome { DONE, NOTHING_CHOSEN, UNAVAILABLE, NOT_ALLOWED, FAILED }

    fun status(context: Context): Status = when (HealthConnectClient.getSdkStatus(context, PROVIDER)) {
        HealthConnectClient.SDK_AVAILABLE -> Status.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Status.UPDATE_NEEDED
        else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) Status.UNSUPPORTED else Status.NOT_INSTALLED
    }

    /** The permissions to ask for when [kinds] are switched to Health Connect: theirs, and background reading. */
    fun permissionsFor(vararg kinds: Kind): Set<String> =
        kinds.flatMap { k -> listOf(k.permission) + k.also.map { HealthPermission.getReadPermission(it) } }.toSet() + BACKGROUND

    /** Some type is read from Health Connect or written to it (#191). */
    fun anyChosen(p: PortableSettings): Boolean = Kind.entries.any { it.chosen(p) } || HealthWrite.anyOn(p)

    /** The Health Connect permissions FlexNotes has, or none when Health Connect can't be used. */
    suspend fun granted(context: Context): Set<String> {
        if (status(context) != Status.AVAILABLE) return emptySet()
        return try {
            HealthConnectClient.getOrCreate(context.applicationContext).permissionController.getGrantedPermissions()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptySet()
        }
    }

    /** Called once per process from `App`: keeps the hourly background sync in step with the settings. */
    fun start(context: Context) {
        val app = context.applicationContext
        scope.launch {
            Settings.awaitLoaded()
            runCatching { schedule(app) }
        }
    }

    /** FlexNotes came to the screen, where Health Connect can always be read: sync unless one ran a moment ago. */
    fun onAppVisible(context: Context) {
        val app = context.applicationContext
        scope.launch { runCatching { sync(app) } }
    }

    /** Syncs now in the background, after a type was switched to Health Connect, and updates the schedule. */
    fun syncSoon(context: Context) {
        val app = context.applicationContext
        scope.launch {
            runCatching { sync(app, force = true) }
            runCatching { schedule(app) }
        }
    }

    /** Starts or stops the hourly background sync to match the settings and the background permission. */
    suspend fun schedule(context: Context) {
        val app = context.applicationContext
        val wm = WorkManager.getInstance(app)
        val p = Settings.currentPortable()
        // Reading while FlexNotes is closed needs background reading; writing (#191) doesn't.
        if (!(Kind.entries.any { it.chosen(p) } && BACKGROUND in granted(app)) && !HealthWrite.anyOn(p)) {
            wm.cancelUniqueWork(WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<HealthConnectWorker>(1, TimeUnit.HOURS).build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /**
     * Reads the last [DAYS] days of every type switched to Health Connect and allowed, and keeps them; then writes
     * what's switched on to be written (#191, `HealthWrite`). Unless [force]d, it does nothing within [MIN_GAP_MS] of
     * the last sync. One sync at a time.
     */
    suspend fun sync(context: Context, force: Boolean = false): Outcome = lock.withLock {
        Settings.awaitLoaded()
        val app = context.applicationContext
        val p = Settings.currentPortable()
        val kinds = Kind.entries.filter { it.chosen(p) }
        val outs = HealthWrite.Out.entries.filter { it.on(p) }
        if (kinds.isEmpty() && outs.isEmpty()) return@withLock Outcome.NOTHING_CHOSEN
        if (status(app) != Status.AVAILABLE) return@withLock Outcome.UNAVAILABLE
        val last = Settings.current().hcSyncedAt
        if (!force && last != null && System.currentTimeMillis() - last in 0 until MIN_GAP_MS) return@withLock Outcome.DONE
        _syncing.value = true
        try {
            val client = HealthConnectClient.getOrCreate(app)
            val granted = client.permissionController.getGrantedPermissions()
            val allowed = kinds.filter { it.permission in granted }
            if (allowed.isEmpty() && outs.none { it.permission in granted }) return@withLock Outcome.NOT_ALLOWED
            // Reading can fail while FlexNotes is closed without background reading; writing still goes ahead.
            val reading = runCatching {
                if (allowed.isNotEmpty()) {
                    val today = LocalDate.now()
                    val from = today.minusDays(DAYS - 1L)
                    val series = allowed.filter { it != Kind.EXERCISE }.flatMap { read(app, client, it, from, today) }
                    Store.replaceHealthConnectDays(series)
                    if (Kind.EXERCISE in allowed) {
                        val distance = HealthPermission.getReadPermission(DistanceRecord::class) in granted
                        HealthSessions.add(sessions(app, client, from, today, distance))
                    }
                    readHistory(app, client, allowed, granted, from)
                }
            }
            reading.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            HealthWrite.sync(app, client, granted)
            // A failed read is tried again the next time FlexNotes opens.
            if (reading.isSuccess) Settings.updateDeviceNow { it.copy(hcSyncedAt = System.currentTimeMillis()) }
            if (reading.isFailure) Outcome.FAILED else Outcome.DONE
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Outcome.FAILED
        } finally {
            _syncing.value = false
        }
    }

    /**
     * The full history (#192): once older data may be read ([HISTORY]), each allowed type not read back yet has
     * everything from [HISTORY_FROM] to the day before [before] read, a year at a time, and is then remembered in
     * [DeviceSettings.hcHistoryKinds]. Without the permission that record is cleared, so allowing it again reads the
     * history again. A failure leaves the type to be tried at the next sync and never fails the regular one.
     */
    private suspend fun readHistory(context: Context, client: HealthConnectClient, allowed: List<Kind>, granted: Set<String>, before: LocalDate) {
        val done = Settings.current().hcHistoryKinds?.split(',')?.filter { it.isNotBlank() }?.toSet().orEmpty()
        if (HISTORY !in granted) {
            if (done.isNotEmpty()) Settings.updateDeviceNow { it.copy(hcHistoryKinds = null) }
            return
        }
        val due = allowed.filter { it.name !in done }
        if (due.isEmpty()) return
        val finished = mutableSetOf<String>()
        for (kind in due) {
            try {
                var end = before.minusDays(1)
                while (!end.isBefore(HISTORY_FROM)) {
                    val start = maxOf(end.minusYears(1).plusDays(1), HISTORY_FROM)
                    if (kind == Kind.EXERCISE) {
                        val distance = HealthPermission.getReadPermission(DistanceRecord::class) in granted
                        HealthSessions.add(sessions(context, client, start, end, distance))
                    } else {
                        Store.replaceHealthConnectDays(read(context, client, kind, start, end))
                    }
                    end = start.minusDays(1)
                }
                finished += kind.name
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Tried again at the next sync.
            }
        }
        if (finished.isNotEmpty()) Settings.updateDeviceNow { it.copy(hcHistoryKinds = (done + finished).sorted().joinToString(",")) }
    }

    /** [kind]'s values from [from] to [to] (local days), as the series `Store` keeps; a day with none has none. */
    private suspend fun read(context: Context, client: HealthConnectClient, kind: Kind, from: LocalDate, to: LocalDate): List<Store.HealthSeries> {
        val dates = days(from, to)
        when (kind) {
            Kind.WEIGHT -> return listOf(
                readings(context, client, WeightRecord::class, from, to, Names.BODYWEIGHT, "kg", dates) { it.time to it.weight.inKilograms }
            )
            Kind.BODY_FAT -> return listOf(
                readings(context, client, BodyFatRecord::class, from, to, BodyFat.NAME, "%", dates) { it.time to it.percentage.value }
            )
            // Each blood pressure reading is two values, kept at the same time (#212).
            Kind.BLOOD_PRESSURE -> return listOf(
                readings(context, client, BloodPressureRecord::class, from, to, Names.BP_SYSTOLIC, Names.BP_UNIT, dates, own = true) {
                    it.time to it.systolic.inMillimetersOfMercury
                },
                readings(context, client, BloodPressureRecord::class, from, to, Names.BP_DIASTOLIC, Names.BP_UNIT, dates, own = true) {
                    it.time to it.diastolic.inMillimetersOfMercury
                }
            )
            Kind.GLUCOSE -> return listOf(
                readings(context, client, BloodGlucoseRecord::class, from, to, Names.GLUCOSE, Names.GLUCOSE_UNIT, dates, own = true) {
                    it.time to it.level.inMillimolesPerLiter
                }
            )
            Kind.SPO2 -> return listOf(
                readings(context, client, OxygenSaturationRecord::class, from, to, Names.SPO2, Names.SPO2_UNIT, dates, own = true) {
                    it.time to it.percentage.value
                }
            )
            Kind.HRV -> return listOf(
                readings(context, client, HeartRateVariabilityRmssdRecord::class, from, to, Names.HRV, Names.HRV_UNIT, dates, own = true) {
                    it.time to it.heartRateVariabilityMillis
                }
            )
            Kind.TEMPERATURE -> return listOf(
                readings(context, client, BodyTemperatureRecord::class, from, to, Names.TEMPERATURE, Names.TEMPERATURE_UNIT, dates, own = true) {
                    it.time to it.temperature.inCelsius
                }
            )
            else -> {}
        }
        // Sleep is grouped from the evening before, so each night counts on the day it ends (#189).
        val sleep = kind == Kind.SLEEP
        val start = if (sleep) from.minusDays(1).atTime(SLEEP_DAY_STARTS, 0) else from.atStartOfDay()
        val end = if (sleep) to.atTime(SLEEP_DAY_STARTS, 0) else to.plusDays(1).atStartOfDay()
        val groups = client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = kind.metrics,
                timeRangeFilter = TimeRangeFilter.between(start, end),
                timeRangeSlicer = Period.ofDays(1)
            )
        )
        val byDate = groups.associate { g ->
            val day = g.startTime.toLocalDate().let { if (sleep) it.plusDays(1) else it }
            day.format(Dates.ISO) to g.result
        }
        fun series(name: String, unit: String, own: Boolean, value: (AggregationResult) -> Double?) = Store.HealthSeries.daily(
            name, unit,
            dates.associateWith { d -> byDate[d]?.let { r -> value(r)?.takeIf { it > 0 }?.let { Store.HealthDay(it, comment(context, r)) } } },
            own
        )
        return when (kind) {
            Kind.STEPS -> listOf(series(Steps.NAME, Steps.UNIT, false) { it[StepsRecord.COUNT_TOTAL]?.toDouble() })
            Kind.INTAKE -> Intake.Kind.entries.map { k ->
                series(k.label, k.unit, true) { r ->
                    when (k) {
                        Intake.Kind.KCAL -> r[NutritionRecord.ENERGY_TOTAL]?.inKilocalories
                        Intake.Kind.PROTEIN -> r[NutritionRecord.PROTEIN_TOTAL]?.inGrams
                        Intake.Kind.FAT -> r[NutritionRecord.TOTAL_FAT_TOTAL]?.inGrams
                        Intake.Kind.CARBS -> r[NutritionRecord.TOTAL_CARBOHYDRATE_TOTAL]?.inGrams
                    }
                }
            }
            Kind.ACTIVE -> listOf(
                series(Names.ACTIVE, Names.ACTIVE_UNIT, true) { it[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories }
            )
            Kind.RESTING_HR -> listOf(
                series(Names.RESTING_HR, Names.RESTING_HR_UNIT, true) { it[RestingHeartRateRecord.BPM_AVG]?.toDouble() }
            )
            Kind.SLEEP -> listOf(
                series(Names.SLEEP, Names.SLEEP_UNIT, true) { r -> r[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.let { it.seconds / 3600.0 } }
            )
            Kind.WEIGHT, Kind.BODY_FAT, Kind.EXERCISE, Kind.BLOOD_PRESSURE, Kind.GLUCOSE, Kind.SPO2, Kind.HRV, Kind.TEMPERATURE -> emptyList()
        }
    }

    /**
     * Every exercise session that started from [from] to [to] (#190), on the local date it started, with the distance
     * its own app recorded during it when [distance] may be read.
     */
    private suspend fun sessions(context: Context, client: HealthConnectClient, from: LocalDate, to: LocalDate, distance: Boolean): List<HealthSession> {
        val found = mutableListOf<HealthSession>()
        var page: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from.atStartOfDay(), to.plusDays(1).atStartOfDay()),
                    pageToken = page
                )
            )
            // FlexNotes's own workouts (#191) are already in the day log.
            response.records.filter { it.metadata.dataOrigin.packageName != context.packageName }.forEach { r ->
                val metres = if (!distance) null else try {
                    client.aggregate(
                        AggregateRequest(
                            metrics = setOf(DistanceRecord.DISTANCE_TOTAL),
                            timeRangeFilter = TimeRangeFilter.between(r.startTime, r.endTime),
                            dataOriginFilter = setOf(r.metadata.dataOrigin)
                        )
                    )[DistanceRecord.DISTANCE_TOTAL]?.inMeters
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                val date = LocalDateTime.ofInstant(r.startTime, r.startZoneOffset ?: ZoneId.systemDefault()).toLocalDate()
                found += HealthSession(
                    id = r.metadata.id,
                    date = date.format(Dates.ISO),
                    type = r.exerciseType,
                    title = r.title,
                    durationSec = Duration.between(r.startTime, r.endTime).seconds.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
                    metres = metres,
                    app = appLabel(context, r.metadata.dataOrigin.packageName)
                )
            }
            page = response.pageToken
        } while (!page.isNullOrEmpty())
        return found
    }

    /**
     * Every [type] reading from [from] to [to] (#187), each kept on its own local date and time, in [unit]. [value]
     * gives a reading's time and value. [own] makes the measurement as FlexNotes's own the first time (the vitals, #212).
     */
    private suspend fun <T : Record> readings(
        context: Context, client: HealthConnectClient, type: KClass<T>, from: LocalDate, to: LocalDate,
        name: String, unit: String, dates: List<String>, own: Boolean = false, value: (T) -> Pair<Instant, Double>
    ): Store.HealthSeries {
        val found = mutableListOf<Reading>()
        var page: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = type,
                    timeRangeFilter = TimeRangeFilter.between(from.atStartOfDay(), to.plusDays(1).atStartOfDay()),
                    pageToken = page
                )
            )
            // FlexNotes's own readings (#191) are already kept as the user's values.
            response.records.filter { it.metadata.dataOrigin.packageName != context.packageName }.forEach { r ->
                val (at, v) = value(r)
                found += Reading(at, v, commentFor(listOfNotNull(appLabel(context, r.metadata.dataOrigin.packageName))))
            }
            page = response.pageToken
        } while (!page.isNullOrEmpty())
        return Store.HealthSeries(name, unit, readingsByDate(found, dates, ZoneId.systemDefault()), own)
    }

    /** A reading from Health Connect: when, its value, and the comment it's kept with. */
    internal data class Reading(val at: Instant, val value: Double, val comment: String)

    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    /**
     * [readings] on their local date and time in [zone], to the minute, for every one of [dates] (a date without any
     * has none, so its old values go). One reading a minute is kept, so a weigh-in shared by two apps (a scale's app
     * and a health app) counts once. Internal for `HealthConnectTest`.
     */
    internal fun readingsByDate(readings: List<Reading>, dates: List<String>, zone: ZoneId): Map<String, List<Store.HealthDay>> {
        val byDate = readings.filter { it.value > 0 }
            .sortedBy { it.at }
            .map { r -> LocalDateTime.ofInstant(r.at, zone).withSecond(0).withNano(0) to r }
            .distinctBy { it.first }
            .groupBy({ it.first.toLocalDate().format(Dates.ISO) }) { (t, r) -> Store.HealthDay(r.value, r.comment, t.format(TIME)) }
        return dates.associateWith { byDate[it].orEmpty() }
    }

    /** Every date from [from] to [to], ISO. Internal for `HealthConnectTest`. */
    internal fun days(from: LocalDate, to: LocalDate): List<String> =
        generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.map { it.format(Dates.ISO) }.toList()

    /** A day's value is kept with "Health Connect" and the apps it came from, where the phone lets FlexNotes name them. */
    private fun comment(context: Context, r: AggregationResult): String =
        commentFor(r.dataOrigins.mapNotNull { appLabel(context, it.packageName) }.distinct().sorted())

    /** The name of the app [packageName], or null for FlexNotes itself or an app the phone won't name. */
    private fun appLabel(context: Context, packageName: String): String? {
        if (packageName == context.packageName) return null
        val pm = context.packageManager
        return try {
            @Suppress("DEPRECATION")
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString().takeIf { it.isNotBlank() }
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    /** "Health Connect" or "Health Connect · MacrosFirst". Internal for `HealthConnectTest`. */
    internal fun commentFor(apps: List<String>): String =
        if (apps.isEmpty()) NAME else "$NAME · ${apps.joinToString(", ")}"

    /** The product's name, kept with each value as its source. */
    const val NAME = "Health Connect"

    /** Opens Health Connect's own settings, where its permissions can be changed. False when the phone has no such screen. */
    fun openSettings(context: Context): Boolean {
        val actions = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) add("android.health.connect.action.HEALTH_HOME_SETTINGS")
            add("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
        }
        return actions.any { a ->
            try {
                context.startActivity(Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            } catch (e: ActivityNotFoundException) {
                false
            }
        }
    }

    /** Opens Health Connect in the Play Store, to install or update it (Android 13 and earlier). */
    fun openStore(context: Context) {
        val uri = Uri.parse("market://details?id=$PROVIDER&url=healthconnect%3A%2F%2Fonboarding")
        val intent = Intent(Intent.ACTION_VIEW, uri).setPackage("com.android.vending").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: ActivityNotFoundException) {
                // No store on this phone; the page says what's needed.
            }
        }
    }
}

/** Syncs Health Connect hourly in the background (#184), when the user allowed background reading. */
class HealthConnectWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { HealthConnect.sync(applicationContext, force = true) }
        return Result.success()
    }
}
