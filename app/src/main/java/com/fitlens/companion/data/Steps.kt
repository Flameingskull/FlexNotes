package com.fitlens.companion.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.provider.Settings as AndroidSettings
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Daily steps from the phone's own step counter (#175), for the daily activity part of the calorie estimate. Local
 * only: `Sensor.TYPE_STEP_COUNTER` with the Physical activity permission, and no internet. Steps can come from Health
 * Connect instead (#185, `PortableSettings.hcSteps`): the counter is then off and `HealthConnect.sync` writes each
 * day's total, and [on] and [average] follow the primary source rule (`Sources.dayValues`).
 *
 * The sensor counts every step since the phone started. FlexNotes reads it when it opens and, where the phone allows,
 * every 15 minutes in the background, and adds the steps since the last reading to that day's **Steps** value in the
 * Body tracker, where it can be viewed, graphed, edited or deleted like any other value. Steps over a gap that crosses
 * midnight are shared between the days by time. Steps are only counted while counting is on.
 */
object Steps {
    /** The Body tracker measurement that holds each day's steps. */
    const val NAME = "Steps"
    const val UNIT = "steps"
    const val SOURCE = "phone"
    const val TIME = "00:00:00"

    /** Days of steps needed before the average replaces the chosen activity level. */
    const val MIN_DAYS = 3

    /** More steps than this between two readings is taken as a faulty sensor and ignored. */
    const val MAX_STEPS_PER_HOUR = 30_000L

    private const val WORK = "fitlens_steps"
    private const val READ_TIMEOUT_MS = 10_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    /** Does this phone have a step counter? */
    fun available(context: Context): Boolean =
        context.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

    /** Has the user allowed FlexNotes to read physical activity? */
    fun permitted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    /** Counting with the phone's step counter is on, allowed and possible on this phone. */
    fun counting(context: Context): Boolean =
        Settings.currentPortable().let { it.activityFromSteps && !it.hcSteps } && permitted(context) && available(context)

    /** Turns step counting on (the permission is already granted), starts the schedule and takes a first reading. */
    fun turnOn(context: Context) {
        val app = context.applicationContext
        Settings.updatePortable { it.copy(activityFromSteps = true, hcSteps = false) }
        scope.launch {
            runCatching { schedule(app) }
            runCatching { sample(app) }
        }
    }

    /** Turns step counting off. Steps already recorded stay in the Body tracker. */
    fun turnOff(context: Context) = stopCounter(context) { it.copy(activityFromSteps = false, hcSteps = false) }

    /**
     * Steps from Health Connect (#185; its permission is already granted): the phone's counter stops, and the next
     * sync fills Steps with Health Connect's daily totals. Steps already counted stay.
     */
    fun useHealthConnect(context: Context) {
        stopCounter(context) { it.copy(activityFromSteps = true, hcSteps = true) }
        HealthConnect.syncSoon(context)
    }

    private fun stopCounter(context: Context, change: (PortableSettings) -> PortableSettings) {
        val app = context.applicationContext
        Settings.updatePortable(change)
        scope.launch {
            runCatching { schedule(app) }
            runCatching { HealthConnect.schedule(app) }
            // The next time counting is turned on starts afresh, so the time it was off isn't counted.
            Settings.updateDeviceNow { it.copy(stepsLast = null) }
        }
    }

    /** Called once per process from `App`: keeps the background schedule in step with the setting. */
    fun start(context: Context) {
        val app = context.applicationContext
        scope.launch {
            Settings.awaitLoaded()
            runCatching { schedule(app) }
        }
    }

    /** FlexNotes came to the screen, where the sensor always answers: record the steps since the last reading. */
    fun onAppVisible(context: Context) {
        val app = context.applicationContext
        scope.launch { runCatching { sample(app) } }
    }

    /** Starts or stops the 15-minute background reading to match the setting and the permission. */
    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context.applicationContext)
        if (!counting(context)) {
            wm.cancelUniqueWork(WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<StepWorker>(15, TimeUnit.MINUTES).build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /**
     * Reads the counter once and records the steps since the last reading, by date. The first reading only sets the
     * starting point. After a restart the counter starts again from 0, so its whole value is new steps.
     */
    suspend fun sample(context: Context) = lock.withLock {
        Settings.awaitLoaded()
        if (!counting(context)) return@withLock
        val counter = readCounter(context) ?: return@withLock
        val now = System.currentTimeMillis()
        val boot = bootCount(context)
        val last = Reading.decode(Settings.current().stepsLast)
        val reading = Reading(counter, now, boot)
        val byDate = last?.let { split(it, reading, now - SystemClock.elapsedRealtime(), ZoneId.systemDefault()) }.orEmpty()
        if (byDate.isNotEmpty()) Store.addSteps(byDate)
        Settings.updateDeviceNow { it.copy(stepsLast = reading.encode()) }
    }

    /** A counter reading: steps since the phone started, when it was read, and the phone's restart count (or -1). */
    data class Reading(val counter: Long, val atMillis: Long, val boot: Int) {
        fun encode(): String = "$counter|$atMillis|$boot"

        companion object {
            fun decode(s: String?): Reading? {
                val p = s?.split('|') ?: return null
                if (p.size != 3) return null
                return Reading(p[0].toLongOrNull() ?: return null, p[1].toLongOrNull() ?: return null, p[2].toIntOrNull() ?: -1)
            }
        }
    }

    /**
     * The steps between [last] and [now] by date (ISO), in [zone]. After a restart (the counter went down, or the
     * restart count changed) the steps are the whole new count, taken since [bootMillis]. A gap across midnight is
     * shared between the days in proportion to the time in each. Nothing for a clock that went backwards or a count
     * too large to be real. Internal for `StepsTest`.
     */
    internal fun split(last: Reading, now: Reading, bootMillis: Long, zone: ZoneId): Map<String, Long> {
        val restarted = now.counter < last.counter || (last.boot >= 0 && now.boot >= 0 && now.boot != last.boot)
        val steps = if (restarted) now.counter else now.counter - last.counter
        val from = if (restarted) maxOf(last.atMillis, bootMillis) else last.atMillis
        val to = now.atMillis
        if (steps <= 0 || to <= from) return emptyMap()
        val hours = (to - from) / 3_600_000.0
        if (steps > MAX_STEPS_PER_HOUR * maxOf(1.0, hours)) return emptyMap()
        val first = Instant.ofEpochMilli(from).atZone(zone).toLocalDate()
        val lastDay = Instant.ofEpochMilli(to).atZone(zone).toLocalDate()
        if (first == lastDay) return mapOf(lastDay.format(Dates.ISO) to steps)
        val out = LinkedHashMap<String, Long>()
        var given = 0L
        var day: LocalDate = first
        while (day <= lastDay) {
            val start = maxOf(from, day.atStartOfDay(zone).toInstant().toEpochMilli())
            val end = minOf(to, day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
            val share = if (day == lastDay) steps - given else steps * (end - start) / (to - from)
            if (share > 0) out[day.format(Dates.ISO)] = share
            given += share
            day = day.plusDays(1)
        }
        return out
    }

    /** The phone's restart count, to tell a restart from a counter that simply went down, or -1. */
    private fun bootCount(context: Context): Int =
        runCatching { AndroidSettings.Global.getInt(context.contentResolver, AndroidSettings.Global.BOOT_COUNT) }.getOrDefault(-1)

    /** One value from the step counter, or null when there's none within a few seconds (in the background, many phones send none). */
    private suspend fun readCounter(context: Context): Long? {
        val sm = context.getSystemService(SensorManager::class.java) ?: return null
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return null
        return withTimeoutOrNull(READ_TIMEOUT_MS) {
            suspendCancellableCoroutine<Long?> { cont ->
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        sm.unregisterListener(this)
                        if (cont.isActive) cont.resume(event.values[0].toLong())
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                }
                if (!sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL, 0)) {
                    cont.resume(null)
                    return@suspendCancellableCoroutine
                }
                cont.invokeOnCancellation { sm.unregisterListener(listener) }
            }
        }
    }

    // ---------- Reading the recorded steps ----------

    /** The steps recorded on [date] from the primary source ([hcPrimary]: Health Connect), or null when there are none. */
    fun on(snap: Snapshot, date: String, hcPrimary: Boolean = false): Double? =
        Sources.dayValues(snap.recordsByName[NAME].orEmpty().filter { it.date.take(10) == date }, hcPrimary)
            .takeIf { it.isNotEmpty() }?.sumOf { it.value }

    /**
     * Average steps a day and the days it's taken from. [leftOutPerDay] is the average a day taken off [perDay] as
     * logged walks and runs (#181).
     */
    data class Average(val perDay: Double, val days: Int, val leftOutPerDay: Double = 0.0)

    /**
     * The average steps a day over the [Energy.AVERAGE_DAYS] ending on [date], from the days with steps recorded, or
     * null with fewer than [MIN_DAYS]. Today is left out while it's still being counted. [leftOut] gives a day's steps
     * already counted elsewhere (logged walks and runs, #181), taken off that day's steps but never below none.
     */
    fun average(
        snap: Snapshot, date: String, today: String = Dates.today(), hcPrimary: Boolean = false,
        leftOut: (String) -> Double = { 0.0 }
    ): Average? {
        val start = Energy.averageStart(date)
        val byDay = snap.recordsByName[NAME].orEmpty()
            .filter { val d = it.date.take(10); d in start..date && d != today }
            .groupBy { it.date.take(10) }
            .mapValues { e -> Sources.dayValues(e.value, hcPrimary).sumOf { it.value } }
        if (byDay.size < MIN_DAYS) return null
        val kept = byDay.mapValues { (d, steps) -> maxOf(0.0, steps - leftOut(d)) }
        val total = byDay.values.sum()
        val keptTotal = kept.values.sum()
        return Average(keptTotal / byDay.size, byDay.size, (total - keptTotal) / byDay.size)
    }
}

/** Reads the step counter in the background every 15 minutes (#175), when the phone lets it. */
class StepWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { Steps.sample(applicationContext) }
        return Result.success()
    }
}
