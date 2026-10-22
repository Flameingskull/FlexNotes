package com.fitlens.companion.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import com.fitlens.companion.R
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong
import kotlin.reflect.KClass

/**
 * Writing to Google Health Connect (#191), so other apps on the phone see what's logged in FlexNotes: bodyweight and
 * body fat as readings, and each workout day with a recorded start and end as a strength-training session. Each type
 * is switched on in Settings › Health Connect ([Out.on]), off by default, with its own write permission.
 *
 * Nothing is written twice: every record carries a FlexNotes id ([Item.clientId]: the value's row, or the workout's
 * date), so Health Connect keeps one record per id. At each sync, FlexNotes reads back what it wrote before and
 * compares: new values are written, changed ones written again with a higher version (so they replace the old), and
 * those deleted in FlexNotes are deleted from Health Connect. Values read from Health Connect are never written back,
 * and the reads in `HealthConnect` skip FlexNotes's own records, so nothing goes round in a loop. Switching a type off
 * stops writing it; what was written stays in Health Connect, where it can be deleted.
 */
object HealthWrite {

    /** A type FlexNotes writes: the record and the setting that switches it on. */
    enum class Out(val record: KClass<out Record>) {
        WEIGHT(WeightRecord::class),
        BODY_FAT(BodyFatRecord::class),
        WORKOUTS(ExerciseSessionRecord::class);

        /** The write permission Health Connect asks the user for. */
        val permission: String get() = HealthPermission.getWritePermission(record)

        /** Writing this type is switched on. */
        fun on(p: PortableSettings): Boolean = when (this) {
            WEIGHT -> p.hcWriteWeight
            BODY_FAT -> p.hcWriteBodyFat
            WORKOUTS -> p.hcWriteWorkouts
        }

        /** [p] with writing this type switched [on] or off. */
        fun switch(p: PortableSettings, on: Boolean): PortableSettings = when (this) {
            WEIGHT -> p.copy(hcWriteWeight = on)
            BODY_FAT -> p.copy(hcWriteBodyFat = on)
            WORKOUTS -> p.copy(hcWriteWorkouts = on)
        }

        /** The start of every id FlexNotes gives this type's records. */
        val prefix: String get() = "flexnotes-${name.lowercase().replace('_', '-')}-"
    }

    /** Some type is switched on to be written. */
    fun anyOn(p: PortableSettings): Boolean = Out.entries.any { it.on(p) }

    /**
     * One record as FlexNotes would write it: its id, when it starts (and ends, for a session), its value (kilograms
     * or percent) or title and notes. [key] is what's compared with the record already in Health Connect.
     */
    internal data class Item(
        val clientId: String,
        val start: LocalDateTime,
        val end: LocalDateTime? = null,
        val value: Double = 0.0,
        val title: String? = null,
        val notes: String? = null
    ) {
        val key: String get() = listOf(start.withNano(0), end?.withNano(0), (value * 1000).roundToLong(), title, notes).joinToString("|")
    }

    /** A record FlexNotes wrote before, as read back: its id, version and [Item.key]. */
    internal data class Written(val clientId: String, val version: Long, val key: String)

    /** What a sync changes: records to write (with the version each is written at) and ids to delete. */
    internal data class Plan(val write: List<Pair<Item, Long>>, val delete: List<String>)

    /**
     * Compares [wanted] with what's [written]: a new item is written at version 1, a changed one at its old version + 1
     * so it replaces the old; an unchanged one is left alone, and a written id no longer wanted is deleted. Internal for
     * `HealthWriteTest`.
     */
    internal fun plan(wanted: List<Item>, written: List<Written>): Plan {
        val before = written.associateBy { it.clientId }
        val ids = wanted.map { it.clientId }.toSet()
        val write = wanted.distinctBy { it.clientId }.mapNotNull { item ->
            val old = before[item.clientId]
            when {
                old == null -> item to 1L
                old.key != item.key -> item to old.version + 1
                else -> null
            }
        }
        return Plan(write, written.map { it.clientId }.filter { it !in ids }.distinct())
    }

    private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /** A body value's local date and time; a value without a time counts at noon. Internal for `HealthWriteTest`. */
    internal fun at(date: String, time: String?): LocalDateTime? {
        val d = Dates.parse(date) ?: return null
        val t = time?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: LocalTime.NOON
        return d.atTime(t)
    }

    /**
     * The readings to write for [out] (bodyweight in kilograms, body fat in percent): the user's own values (typed,
     * imported or from the phone's step counter), never Health Connect's. Internal for `HealthWriteTest`.
     */
    internal fun readings(out: Out, records: List<MRecord>): List<Item> = records.mapNotNull { r ->
        if (r.source == Sources.HEALTH_CONNECT || r.value <= 0) return@mapNotNull null
        val value = when (out) {
            Out.WEIGHT -> if (isBodyweight(r.name)) WeightUnits.convert(r.value, r.unit, "kg") else return@mapNotNull null
            Out.BODY_FAT -> if (r.name.equals(BodyFat.NAME, true) && r.value <= 100) r.value else return@mapNotNull null
            Out.WORKOUTS -> return@mapNotNull null
        }
        if (out == Out.WEIGHT && value > 1000) return@mapNotNull null
        Item(out.prefix + r.id, at(r.date, r.time) ?: return@mapNotNull null, value = value)
    }

    private fun isBodyweight(name: String): Boolean =
        name.equals(HealthConnect.Names.BODYWEIGHT, true) || name.equals("Body Weight", true)

    /**
     * The workout sessions to write: each date with sets logged in FlexNotes (not Health Connect's) and a recorded
     * start and end, from the earliest start to the latest end, titled [title], its notes the day's exercises in
     * order. A day without both times is left out, as a session needs them. Internal for `HealthWriteTest`.
     */
    internal fun workouts(
        sets: Map<String, List<SetRow>>,
        times: Map<String, List<WorkoutTime>>,
        names: (Long) -> String?,
        title: String
    ): List<Item> = sets.mapNotNull { (date, day) ->
        val own = day.filter { it.source != Sources.HEALTH_CONNECT }
        if (own.isEmpty()) return@mapNotNull null
        val spans = times[date].orEmpty().mapNotNull { t ->
            val s = stamp(t.start) ?: return@mapNotNull null
            val e = stamp(t.end) ?: return@mapNotNull null
            if (e.isAfter(s)) s to e else null
        }
        if (spans.isEmpty()) return@mapNotNull null
        val exercises = own.sortedBy { it.position }.map { it.exerciseId }.distinct().mapNotNull(names)
        Item(
            Out.WORKOUTS.prefix + date,
            spans.minOf { it.first },
            spans.maxOf { it.second },
            title = title,
            notes = exercises.joinToString(", ").takeIf { it.isNotEmpty() }
        )
    }

    private fun stamp(s: String): LocalDateTime? = s.trim().takeIf { it.isNotEmpty() }?.let { runCatching { LocalDateTime.parse(it, STAMP) }.getOrNull() }

    /**
     * Brings every type switched on and allowed in step with FlexNotes's data: writes what's new or changed and deletes
     * what was deleted. A type that fails is tried again at the next sync and never stops the others.
     */
    internal suspend fun sync(context: Context, client: HealthConnectClient, granted: Set<String>) {
        val p = Settings.currentPortable()
        val outs = Out.entries.filter { it.on(p) && it.permission in granted }
        if (outs.isEmpty()) return
        val snap = Store.snapshot.value ?: return
        val zone = ZoneId.systemDefault()
        for (out in outs) {
            try {
                val wanted = when (out) {
                    Out.WEIGHT, Out.BODY_FAT -> readings(out, snap.body.rawRecords)
                    Out.WORKOUTS -> workouts(snap.setsByDate, snap.workoutTimes, { snap.exercises[it]?.name }, context.getString(R.string.hcw_session_title))
                }
                val plan = plan(wanted, written(context, client, out))
                plan.write.chunked(BATCH).forEach { batch -> client.insertRecords(batch.map { (item, v) -> record(out, item, v, zone) }) }
                plan.delete.chunked(BATCH).forEach { ids -> client.deleteRecords(out.record, emptyList(), ids) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Tried again at the next sync.
            }
        }
    }

    private const val BATCH = 100

    private fun offset(t: LocalDateTime, zone: ZoneId): ZoneOffset = zone.rules.getOffset(t)

    private fun record(out: Out, item: Item, version: Long, zone: ZoneId): Record {
        val meta = Metadata.manualEntry(clientRecordId = item.clientId, clientRecordVersion = version)
        val off = offset(item.start, zone)
        val start = item.start.toInstant(off)
        return when (out) {
            Out.WEIGHT -> WeightRecord(start, off, Mass.kilograms(item.value), meta)
            Out.BODY_FAT -> BodyFatRecord(start, off, Percentage(item.value), meta)
            Out.WORKOUTS -> {
                val end = item.end ?: item.start.plusMinutes(1)
                val endOff = offset(end, zone)
                ExerciseSessionRecord(
                    startTime = start, startZoneOffset = off, endTime = end.toInstant(endOff), endZoneOffset = endOff,
                    metadata = meta, exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
                    title = item.title, notes = item.notes
                )
            }
        }
    }

    /** Every [out] record FlexNotes wrote before, with its id, version and [Item.key]. */
    private suspend fun written(context: Context, client: HealthConnectClient, out: Out): List<Written> = when (out) {
        Out.WEIGHT -> own(context, client, WeightRecord::class, out) { id, r -> Item(id, local(r.time, r.zoneOffset), value = r.weight.inKilograms) }
        Out.BODY_FAT -> own(context, client, BodyFatRecord::class, out) { id, r -> Item(id, local(r.time, r.zoneOffset), value = r.percentage.value) }
        Out.WORKOUTS -> own(context, client, ExerciseSessionRecord::class, out) { id, r ->
            Item(id, local(r.startTime, r.startZoneOffset), local(r.endTime, r.endZoneOffset), title = r.title, notes = r.notes)
        }
    }

    /** FlexNotes's own [type] records with an [out] id, read back page by page; [item] gives each one's [Item]. */
    private suspend fun <T : Record> own(
        context: Context, client: HealthConnectClient, type: KClass<T>, out: Out, item: (String, T) -> Item
    ): List<Written> {
        val found = mutableListOf<Written>()
        var page: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = type,
                    timeRangeFilter = TimeRangeFilter.between(
                        HealthConnect.HISTORY_FROM.atStartOfDay(), LocalDate.now().plusDays(2).atStartOfDay()
                    ),
                    dataOriginFilter = setOf(DataOrigin(context.packageName)),
                    pageToken = page
                )
            )
            response.records.forEach { r ->
                val id = r.metadata.clientRecordId?.takeIf { it.startsWith(out.prefix) } ?: return@forEach
                found += Written(id, r.metadata.clientRecordVersion, item(id, r).key)
            }
            page = response.pageToken
        } while (!page.isNullOrEmpty())
        return found
    }

    private fun local(t: Instant, off: ZoneOffset?): LocalDateTime =
        LocalDateTime.ofInstant(t, off ?: ZoneId.systemDefault())
}
