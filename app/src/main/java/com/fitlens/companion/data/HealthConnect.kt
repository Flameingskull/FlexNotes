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
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
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
import java.time.LocalDate
import java.time.Period
import java.util.concurrent.TimeUnit

/**
 * Reading Google Health Connect (#184), so values other apps record on the phone (steps, #185; what was eaten, #186)
 * can be the primary source for what FlexNotes records and calculates. Health Connect runs on the phone, so this stays
 * local: read only, no internet permission, accounts or cloud.
 *
 * For each type switched to Health Connect in Settings (`PortableSettings.hcSteps`, `hcIntake`), a sync reads the
 * daily totals of the last [DAYS] days (the history Health Connect allows by default) grouped by local day, so
 * Health Connect applies the user's app priority and a watch and a phone aren't counted twice. Each day's total is
 * kept as a body value from [SOURCE] (`Store.replaceHealthConnectDays`), replaced at each sync; typed values are never
 * touched, and which one counts follows `Sources.dayValues`.
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

    /** Days read at each sync, today included. */
    const val DAYS = 30

    /** The least time between two syncs started by FlexNotes coming to the screen. */
    const val MIN_GAP_MS = 10 * 60_000L

    private const val WORK = "fitlens_health_connect"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    private val _syncing = MutableStateFlow(false)

    /** A sync is running (Settings › Health Connect shows it). */
    val syncing: StateFlow<Boolean> = _syncing

    /** Whether Health Connect can be used on this phone. */
    enum class Status { AVAILABLE, NOT_INSTALLED, UPDATE_NEEDED, UNSUPPORTED }

    /** A type of data FlexNotes reads, and the setting that makes Health Connect its primary source. */
    enum class Kind(val permission: String, val metrics: Set<AggregateMetric<*>>) {
        STEPS(HealthPermission.getReadPermission(StepsRecord::class), setOf(StepsRecord.COUNT_TOTAL)),
        INTAKE(
            HealthPermission.getReadPermission(NutritionRecord::class),
            setOf(NutritionRecord.ENERGY_TOTAL, NutritionRecord.PROTEIN_TOTAL, NutritionRecord.TOTAL_FAT_TOTAL, NutritionRecord.TOTAL_CARBOHYDRATE_TOTAL)
        );

        /** Health Connect is chosen as this type's source. */
        fun chosen(p: PortableSettings): Boolean = when (this) {
            STEPS -> p.hcSteps && p.activityFromSteps
            INTAKE -> p.hcIntake
        }
    }

    /** What a sync did. */
    enum class Outcome { DONE, NOTHING_CHOSEN, UNAVAILABLE, NOT_ALLOWED, FAILED }

    fun status(context: Context): Status = when (HealthConnectClient.getSdkStatus(context, PROVIDER)) {
        HealthConnectClient.SDK_AVAILABLE -> Status.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Status.UPDATE_NEEDED
        else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) Status.UNSUPPORTED else Status.NOT_INSTALLED
    }

    /** The permissions to ask for when [kinds] are switched to Health Connect: theirs, and background reading. */
    fun permissionsFor(vararg kinds: Kind): Set<String> = kinds.map { it.permission }.toSet() + BACKGROUND

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
        val wanted = Kind.entries.any { it.chosen(Settings.currentPortable()) }
        if (!wanted || BACKGROUND !in granted(app)) {
            wm.cancelUniqueWork(WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<HealthConnectWorker>(1, TimeUnit.HOURS).build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /**
     * Reads the last [DAYS] days of every type switched to Health Connect and allowed, and keeps each day's totals.
     * Unless [force]d, it does nothing within [MIN_GAP_MS] of the last sync. One sync at a time.
     */
    suspend fun sync(context: Context, force: Boolean = false): Outcome = lock.withLock {
        Settings.awaitLoaded()
        val app = context.applicationContext
        val kinds = Kind.entries.filter { it.chosen(Settings.currentPortable()) }
        if (kinds.isEmpty()) return@withLock Outcome.NOTHING_CHOSEN
        if (status(app) != Status.AVAILABLE) return@withLock Outcome.UNAVAILABLE
        val last = Settings.current().hcSyncedAt
        if (!force && last != null && System.currentTimeMillis() - last in 0 until MIN_GAP_MS) return@withLock Outcome.DONE
        _syncing.value = true
        try {
            val client = HealthConnectClient.getOrCreate(app)
            val granted = client.permissionController.getGrantedPermissions()
            val allowed = kinds.filter { it.permission in granted }
            if (allowed.isEmpty()) return@withLock Outcome.NOT_ALLOWED
            val today = LocalDate.now()
            val from = today.minusDays(DAYS - 1L)
            val series = allowed.flatMap { read(app, client, it, from, today) }
            Store.replaceHealthConnectDays(series)
            Settings.updateDeviceNow { it.copy(hcSyncedAt = System.currentTimeMillis()) }
            Outcome.DONE
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Outcome.FAILED
        } finally {
            _syncing.value = false
        }
    }

    /** [kind]'s daily totals from [from] to [to] (local days), as the series `Store` keeps; a day with none is null. */
    private suspend fun read(context: Context, client: HealthConnectClient, kind: Kind, from: LocalDate, to: LocalDate): List<Store.HealthSeries> {
        val groups = client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = kind.metrics,
                timeRangeFilter = TimeRangeFilter.between(from.atStartOfDay(), to.plusDays(1).atStartOfDay()),
                timeRangeSlicer = Period.ofDays(1)
            )
        )
        val byDate = groups.associate { it.startTime.toLocalDate().format(Dates.ISO) to it.result }
        val dates = days(from, to)
        fun series(name: String, unit: String, own: Boolean, value: (AggregationResult) -> Double?) = Store.HealthSeries(
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
        }
    }

    /** Every date from [from] to [to], ISO. Internal for `HealthConnectTest`. */
    internal fun days(from: LocalDate, to: LocalDate): List<String> =
        generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.map { it.format(Dates.ISO) }.toList()

    /** A day's value is kept with "Health Connect" and the apps it came from, where the phone lets FlexNotes name them. */
    private fun comment(context: Context, r: AggregationResult): String {
        val pm = context.packageManager
        val apps = r.dataOrigins.map { it.packageName }.filter { it != context.packageName }.mapNotNull { p ->
            try {
                @Suppress("DEPRECATION")
                pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString().takeIf { it.isNotBlank() }
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }.distinct().sorted()
        return commentFor(apps)
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
