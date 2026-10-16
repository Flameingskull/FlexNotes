package com.fitlens.companion.data

import android.content.Context
import android.content.res.Resources
import com.fitlens.companion.R
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Outcome of an import, export or backup. [folderProblem]: the backup folder couldn't be reached. */
data class ImportSummary(val message: String, val ok: Boolean, val folderProblem: Boolean = false)

/** What a FitNotes import adds to FlexNotes and what it skips as already present. */
class ImportPlan {
    var categoriesAdded = 0
    var exercisesAdded = 0
    var exercisesSkipped = 0
    var workoutsAdded = 0
    var setsAdded = 0
    var setsSkipped = 0
    var commentsAdded = 0
    var commentsSkipped = 0
    var timesAdded = 0
    var timesSkipped = 0
    var measurementsAdded = 0
    var recordsAdded = 0
    var recordsSkipped = 0
    /** Values imported earlier without a unit FlexNotes understands, converted and given their unit by this import. */
    var unitsFixed = 0
    /** Cardio sets an earlier import added with FitNotes's distance as logged, converted to the user's unit (#180). */
    var distancesFixed = 0

    val nothingNew: Boolean
        get() = categoriesAdded + exercisesAdded + setsAdded + commentsAdded + timesAdded + measurementsAdded + recordsAdded +
            unitsFixed + distancesFixed == 0

    /** What the import adds, one line each. */
    fun addedLines(res: Resources): List<String> = buildList {
        fun count(id: Int, n: Int) { if (n > 0) add(res.getQuantityString(id, n, n)) }
        if (setsAdded > 0) {
            val sets = res.getQuantityString(R.plurals.imp_sets, setsAdded, setsAdded)
            add(
                if (workoutsAdded > 0) res.getString(
                    R.string.imp_sets_with_days, sets, res.getQuantityString(R.plurals.imp_new_days, workoutsAdded, workoutsAdded)
                )
                else res.getString(R.string.imp_sets_existing_days, sets)
            )
        }
        count(R.plurals.imp_exercises, exercisesAdded)
        count(R.plurals.imp_categories, categoriesAdded)
        count(R.plurals.imp_comments, commentsAdded)
        count(R.plurals.imp_times, timesAdded)
        count(R.plurals.imp_records, recordsAdded)
        count(R.plurals.imp_measurements, measurementsAdded)
        count(R.plurals.imp_units_fixed, unitsFixed)
        count(R.plurals.imp_distances_fixed, distancesFixed)
    }

    /** What the import skips because FlexNotes already has it (or the user removed it in FlexNotes). */
    fun skippedLines(res: Resources): List<String> = buildList {
        fun count(id: Int, n: Int) { if (n > 0) add(res.getQuantityString(id, n, n)) }
        count(R.plurals.imp_sets, setsSkipped)
        count(R.plurals.imp_comments_times, commentsSkipped + timesSkipped)
        count(R.plurals.imp_records, recordsSkipped)
        count(R.plurals.imp_exercises_deleted, exercisesSkipped)
    }

    fun describe(res: Resources): String {
        if (nothingNew) return res.getString(R.string.imp_nothing_new)
        val skipped = setsSkipped + commentsSkipped + timesSkipped + recordsSkipped
        val added = res.getString(R.string.imp_added, addedLines(res).joinToString(", "))
        return if (skipped > 0) added + " " + res.getQuantityString(R.plurals.imp_items_skipped, skipped, skipped) else added
    }
}

enum class FileKind { FITNOTES_BACKUP, BODY_CSV, WORKOUT_CSV, IMAGE, ARCHIVE, UNKNOWN }

object FitNotesImporter {

    const val FITNOTES_PACKAGE = "com.github.jamesgay.fitnotes"

    fun displayName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
        }
    } catch (e: Exception) {
        null
    } ?: uri.lastPathSegment

    /** Work out what a shared/opened file is by looking at its first bytes. */
    fun sniff(context: Context, uri: Uri): FileKind {
        val mime = try { context.contentResolver.getType(uri) } catch (e: Exception) { null }
        if (mime != null && mime.startsWith("image/")) return FileKind.IMAGE
        val head = ByteArray(64)
        val n = try {
            context.contentResolver.openInputStream(uri)?.use { it.read(head) } ?: -1
        } catch (e: Exception) { -1 }
        if (n <= 0) return FileKind.UNKNOWN
        val text = String(head, 0, n, Charsets.ISO_8859_1)
        return when {
            text.startsWith("SQLite format 3") -> FileKind.FITNOTES_BACKUP
            text.startsWith("PK") -> FileKind.ARCHIVE
            text.startsWith("Date,Time,Measurement") -> FileKind.BODY_CSV
            text.startsWith("Date,Exercise") -> FileKind.WORKOUT_CSV
            (head[0].toInt() and 0xFF) == 0xFF && (head[1].toInt() and 0xFF) == 0xD8 -> FileKind.IMAGE
            text.startsWith("\u0089PNG") -> FileKind.IMAGE
            text.length > 12 && text.substring(4, 8) == "ftyp" -> FileKind.IMAGE // HEIC
            else -> FileKind.UNKNOWN
        }
    }

    // ---------- FitNotes backups: merged into FlexNotes, never replacing it ----------

    /** A FitNotes backup copied into FlexNotes's cache and checked, waiting for the user to confirm the import. */
    class StagedImport(
        val file: File,
        val name: String,
        val modified: Long,
        val plan: ImportPlan,
        /** Application context, so [importStaged] can take the safety copy (#47) before merging and word its result. */
        val context: Context
    )

    /** Result of [prepare]: a staged import, or a message saying why the file can't be imported. */
    class Prepared(val staged: StagedImport?, val error: String?)

    private val lock = Mutex()

    /**
     * Copies a FitNotes backup into the cache and works out what importing it would add and skip, without changing
     * anything. Call [importStaged] to import it or [discard] to drop it.
     */
    suspend fun prepare(context: Context, uri: Uri, sourceModified: Long = 0L): Prepared = withContext(Dispatchers.IO) {
        val tmp = File(context.cacheDir, "import_${System.nanoTime()}.fitnotes")
        try {
            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            }
            if (copied == null) return@withContext Prepared(null, context.getString(R.string.imp_cant_open))
            val header = ByteArray(16)
            tmp.inputStream().use { it.read(header) }
            if (!String(header, Charsets.ISO_8859_1).startsWith("SQLite format 3")) {
                deleteStage(tmp)
                return@withContext Prepared(null, context.getString(R.string.imp_not_fitnotes))
            }
            val plan = lock.withLock { runMerge(tmp, apply = false) }
            Prepared(
                StagedImport(
                    tmp, displayName(context, uri) ?: context.getString(R.string.imp_backup_name), sourceModified, plan,
                    context.applicationContext
                ),
                null
            )
        } catch (e: Exception) {
            deleteStage(tmp)
            Prepared(null, context.getString(R.string.imp_cant_read, e.message ?: e.javaClass.simpleName))
        }
    }

    /** Imports a staged backup (merging it into FlexNotes) and deletes the staged copy. */
    suspend fun importStaged(staged: StagedImport): ImportSummary = withContext(Dispatchers.IO) {
        val ctx = staged.context
        try {
            // The way back (#47): a data-only safety copy before the merge. Without one the import doesn't run.
            val safety = Backups.safetyCopy(ctx, ctx.getString(R.string.imp_safety_reason, staged.name))
            if (!safety.ok) return@withContext ImportSummary(ctx.getString(R.string.imp_safety_failed, safety.message), false)
            val plan = lock.withLock { runMerge(staged.file, apply = true) }
            Settings.updateDeviceNow {
                it.copy(
                    lastImportName = staged.name,
                    lastImportAt = System.currentTimeMillis(),
                    lastImportModified = if (staged.modified > 0) staged.modified else it.lastImportModified
                )
            }
            Store.reload()
            ImportSummary(ctx.getString(R.string.imp_done, staged.name, plan.describe(ctx.resources)), true)
        } catch (e: Exception) {
            ImportSummary(ctx.getString(R.string.imp_failed, e.message ?: e.javaClass.simpleName), false)
        } finally {
            deleteStage(staged.file)
        }
    }

    fun discard(staged: StagedImport) = deleteStage(staged.file)

    private fun deleteStage(f: File) {
        f.delete()
        File(f.path + "-journal").delete()
    }

    /**
     * Imports a FitNotes backup straight away, without the summary step (used by the automatic folder sync).
     * The backup is merged into FlexNotes as described in [Workouts]: nothing in FlexNotes is deleted or overwritten.
     */
    suspend fun importBackup(context: Context, uri: Uri, sourceModified: Long = 0L): ImportSummary {
        val p = prepare(context, uri, sourceModified)
        val staged = p.staged ?: return ImportSummary(p.error ?: context.getString(R.string.imp_failed_short), false)
        return importStaged(staged)
    }

    /** Rolls back a dry run's transaction, carrying the plan it worked out. */
    private class DryRun(val plan: ImportPlan) : RuntimeException()

    /**
     * Opens the staged backup and merges it into [db] in one transaction. With [apply] = false every change is rolled
     * back (a dry run), so the plan is exactly what a real import would do.
     */
    internal fun runMerge(
        file: File,
        apply: Boolean,
        db: Db = Store.db,
        distanceUnit: String = Settings.portable.value.distanceUnit
    ): ImportPlan {
        val src = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            return try {
                db.transaction {
                    val plan = merge(src, db, distanceUnit)
                    if (!apply) throw DryRun(plan)
                    plan
                }
            } catch (dry: DryRun) {
                dry.plan
            }
        } finally {
            src.close()
        }
    }

    private inline fun SQLiteDatabase.each(sql: String, block: (Cursor) -> Unit): Boolean = try {
        rawQuery(sql, null).use { c -> while (c.moveToNext()) block(c) }
        true
    } catch (e: Exception) {
        false // table missing in older FitNotes versions
    }

    /** A category or exercise already in FlexNotes, for matching. */
    private class Owned(val id: Long, val key: String, val source: String, val fitnotesId: Long?)

    private const val SKIP = -1L

    private fun owned(rows: List<LibraryOrigin>): MutableList<Owned> =
        rows.mapTo(ArrayList()) { Owned(it.id, Workouts.nameKey(it.name), it.source, it.fitnotes_id) }

    /**
     * FlexNotes id for a FitNotes category or exercise: a rename or delete the user made in FlexNotes first ([SKIP] when
     * deleted), then the untouched imported row with the same FitNotes id and name, then any row with the same name.
     * Null when nothing matches and a new row is needed.
     */
    private fun resolve(kind: String, fnId: Long, key: String, rows: List<Owned>, links: Map<String, Long?>): Long? {
        val lk = "$kind|$key"
        if (links.containsKey(lk)) {
            val target = links[lk] ?: return SKIP
            if (rows.any { it.id == target }) return target
        }
        rows.firstOrNull { it.source == Sources.FITNOTES && it.fitnotesId == fnId && it.key == key }?.let { return it.id }
        return rows.firstOrNull { it.key == key }?.id
    }

    /**
     * Merges a FitNotes backup into FlexNotes following the conflict rules in [Workouts]: only adds rows, matches
     * categories and exercises by name, and skips sets, comments, times and body records that are already present.
     * The caller runs it inside one transaction on [db], off the main thread.
     */
    /**
     * Merges the FitNotes backup [src] into [db]. [distanceUnit] is the user's distance unit, for exercises without
     * their own (#7, #180).
     */
    internal fun merge(src: SQLiteDatabase, db: Db, distanceUnit: String = DistanceUnits.KM): ImportPlan {
        val plan = ImportPlan()
        val w = db.importDao
        val workouts = db.workoutDao
        val all = db.snapshotDao

        // What the user changed in FlexNotes (see Workouts, conflict rule 5).
        val links = HashMap<String, Long?>()
        val skips = HashMap<String, Int>()
        for (rule in w.rules()) {
            val k = rule.kind + "|" + rule.key
            if (rule.kind == Workouts.RULE_CATEGORY || rule.kind == Workouts.RULE_EXERCISE) {
                links[k] = rule.target_id
            } else {
                skips[k] = (skips[k] ?: 0) + 1
            }
        }
        fun consumeSkip(kind: String, key: String): Boolean {
            val k = "$kind|$key"
            val n = skips[k] ?: 0
            if (n <= 0) return false
            skips[k] = n - 1
            return true
        }

        // ---------- Categories ----------
        val categories = owned(w.categories())
        val catMap = HashMap<Long, Long>()
        var catOrder = workouts.lastCategoryOrder()
        src.each("SELECT _id, name, colour, sort_order FROM Category ORDER BY sort_order, _id") { c ->
            val fnId = c.lng(0)
            val name = c.strOr(1).trim()
            val key = Workouts.nameKey(name)
            if (key.isEmpty()) return@each
            val found = resolve(Workouts.RULE_CATEGORY, fnId, key, categories, links)
            if (found == null) {
                catOrder++
                val id = w.addCategory(name, c.int(2), catOrder, Sources.FITNOTES, fnId)
                categories.add(Owned(id, key, Sources.FITNOTES, fnId))
                catMap[fnId] = id
                plan.categoriesAdded++
            } else {
                catMap[fnId] = found
            }
        }

        // ---------- Exercises ----------
        val exercises = owned(w.exercises())
        val exMap = HashMap<Long, Long>()
        // FitNotes's cardio exercises (distance and time), whose sets carry their distance unit (#180).
        val cardio = HashSet<Long>()
        src.each("SELECT _id, name, category_id, exercise_type_id, notes FROM exercise ORDER BY _id") { c ->
            val fnId = c.lng(0)
            if (c.int(3) == FITNOTES_CARDIO) cardio.add(fnId)
            val name = c.strOr(1).trim()
            val key = Workouts.nameKey(name)
            if (key.isEmpty()) return@each
            val found = resolve(Workouts.RULE_EXERCISE, fnId, key, exercises, links)
            if (found == null) {
                val cat = catMap[c.lng(2)]?.takeIf { it != SKIP } ?: Workouts.UNCATEGORISED
                val id = w.addExercise(name, cat, c.int(3), c.str(4), Sources.FITNOTES, fnId)
                exercises.add(Owned(id, key, Sources.FITNOTES, fnId))
                exMap[fnId] = id
                plan.exercisesAdded++
            } else {
                exMap[fnId] = found
                if (found == SKIP) plan.exercisesSkipped++
            }
        }

        // ---------- Sets ----------
        // Sets already in FlexNotes, counted by date, exercise and values (identical sets are common, e.g. 3 x 5 x 100 kg).
        val present = HashMap<String, Int>()
        val workoutDates = HashSet<String>()
        for (s in w.setValues()) {
            val k = Workouts.setKey(s.exercise_id, s.date, s.weight, s.reps, s.distance, s.duration.toInt())
            present[k] = (present[k] ?: 0) + 1
            workoutDates.add(s.date.take(10))
        }
        // Each FitNotes cardio set keeps its distance in the unit it was logged in (`training_log.unit`); FlexNotes keeps
        // an exercise's distances in its own unit or the user's (#7). Converted here, so nothing is fixed by hand (#180).
        val ownDistanceUnits = w.exerciseDistanceUnits().associate { it.id to it.distance_unit }
        // Sets an earlier import added as logged, by FitNotes id: a re-import converts them instead of adding them again.
        val earlier = w.importedDistances(Sources.FITNOTES).associateBy { it.fitnotes_id }
        val setSqlWithUnits = """
            SELECT t._id, t.exercise_id, t.date, t.metric_weight, t.reps, t.distance, t.duration_seconds, t.is_personal_record,
              (SELECT group_concat(cm.comment, ' / ') FROM Comment cm WHERE cm.owner_type_id = 1 AND cm.owner_id = t._id),
              t.unit
            FROM training_log t ORDER BY t.date, t._id
        """.trimIndent()
        val setSqlWithComments = """
            SELECT t._id, t.exercise_id, t.date, t.metric_weight, t.reps, t.distance, t.duration_seconds, t.is_personal_record,
              (SELECT group_concat(cm.comment, ' / ') FROM Comment cm WHERE cm.owner_type_id = 1 AND cm.owner_id = t._id), NULL
            FROM training_log t ORDER BY t.date, t._id
        """.trimIndent()
        val setSqlPlain = "SELECT _id, exercise_id, date, metric_weight, reps, 0, 0, 0, NULL, NULL FROM training_log ORDER BY date, _id"
        val mergeSet: (Cursor) -> Unit = mergeSet@{ c ->
            val exId = exMap[c.lng(1)]
            val date = c.strOr(2).take(10)
            if (exId == null || exId == SKIP || Dates.parse(date) == null) {
                plan.setsSkipped++
                return@mergeSet
            }
            val logged = c.dbl(5)
            val metres = if (logged > 0 && c.lng(1) in cardio && !c.isNull(9)) fitNotesDistanceMetres(c.int(9)) else null
            val distance = if (metres == null) logged else DistanceUnits.fromMetres(logged * metres, ownDistanceUnits[exId] ?: distanceUnit)
            val k = Workouts.setKey(exId, date, c.dbl(3), c.int(4), distance, c.int(6))
            val n = present[k] ?: 0
            if (n > 0) {
                present[k] = n - 1
                plan.setsSkipped++
                return@mergeSet
            }
            val asLogged = Workouts.setKey(exId, date, c.dbl(3), c.int(4), logged, c.int(6))
            if (asLogged != k) {
                // Imported before as logged: convert that set rather than add the same one again.
                val old = earlier[c.lng(0)]
                if (old != null && old.exercise_id == exId && Workouts.setKey(exId, date, c.dbl(3), c.int(4), old.distance, c.int(6)) == asLogged) {
                    w.setDistance(old.id, distance)
                    present[asLogged] = ((present[asLogged] ?: 0) - 1).coerceAtLeast(0)
                    plan.distancesFixed++
                    plan.setsSkipped++
                    return@mergeSet
                }
                if (consumeSkip(Workouts.RULE_SET, asLogged)) {
                    plan.setsSkipped++
                    return@mergeSet
                }
            }
            if (consumeSkip(Workouts.RULE_SET, k)) {
                plan.setsSkipped++
                return@mergeSet
            }
            w.addSet(exId, date, c.dbl(3), c.int(4), distance, c.int(6), c.int(7), c.str(8), Sources.FITNOTES, c.lng(0))
            plan.setsAdded++
            if (workoutDates.add(date)) plan.workoutsAdded++
        }
        // On FitNotes versions without these columns the query fails before reading any row, so the next one is tried.
        if (!src.each(setSqlWithUnits, mergeSet) && !src.each(setSqlWithComments, mergeSet)) src.each(setSqlPlain, mergeSet)

        // ---------- Workout comments and times ----------
        val comments = HashSet<String>()
        for (row in all.workoutComments()) comments.add(Workouts.commentKey(row.date, row.comment))
        src.each("SELECT date, comment FROM WorkoutComment") { c ->
            val date = c.strOr(0).take(10)
            val text = c.strOr(1).trim()
            if (text.isEmpty()) return@each
            val k = Workouts.commentKey(date, text)
            if (k in comments || consumeSkip(Workouts.RULE_COMMENT, k)) {
                plan.commentsSkipped++
                return@each
            }
            workouts.addComment(date, text, Sources.FITNOTES)
            comments.add(k)
            plan.commentsAdded++
        }
        val times = HashSet<String>()
        for (row in all.workoutTimes()) times.add(Workouts.timeKey(row.date, row.start, row.finish))
        src.each("SELECT workout_date, start_date_time, end_date_time FROM WorkoutTime") { c ->
            val date = c.strOr(0).take(10)
            val k = Workouts.timeKey(date, c.str(1), c.str(2))
            if (k in times || consumeSkip(Workouts.RULE_TIME, k)) {
                plan.timesSkipped++
                return@each
            }
            workouts.addTime(date, c.str(1), c.str(2), Sources.FITNOTES)
            times.add(k)
            plan.timesAdded++
        }

        // ---------- Measurements ----------
        // Values for a measurement matching a custom metric go into that metric.
        val aliases = customAliases(w)
        val body = db.bodyDao
        fun target(name: String) = aliases[name.trim().lowercase()] ?: name
        // FitNotes's own units setting: 0 is imperial (lbs, inches), 1 metric.
        var metric = 1
        src.each("SELECT metric FROM settings LIMIT 1") { c -> metric = c.int(0) }
        // The units the user has set in FlexNotes. Until they pick a weight unit, FlexNotes takes FitNotes's (below).
        val weightUnit = if (db.metaDao.has("weight_unit_manual")) WeightUnits.of(db.metaDao.get("weight_unit")) ?: "kg"
            else if (metric == 0) "lbs" else "kg"
        val lengthUnit = LengthUnits.of(db.metaDao.get("length_unit")) ?: LengthUnits.CM

        // Values already in FlexNotes, by name, date and time, to recognise a value imported or typed before whatever
        // unit it was kept in.
        val existing = HashMap<String, MutableList<MeasurementRecordRow>>()
        val manual = HashMap<String, MutableList<MeasurementRecordRow>>()
        for (r in all.records()) {
            existing.getOrPut(r.name + "|" + r.date + "|" + r.time) { ArrayList() } += r
            if (r.source == "manual") manual.getOrPut(r.name + "|" + r.date) { ArrayList() } += r
        }
        fun same(a: Double, b: Double) = fmtNum(a, 3) == fmtNum(b, 3)
        /** [value] in FitNotes's [from] is the same as [r]'s value, in whatever unit [r] is kept in. */
        fun matches(r: MeasurementRecordRow, value: Double, from: String) =
            same(r.value, if (MeasureUnits.sameKind(from, r.unit)) MeasureUnits.convert(value, from, r.unit) else value)

        // The unit each FlexNotes measurement's values are recorded in: the one the user sees it in.
        val units = HashMap<String, String>()
        /** The unit a value in FitNotes's [from] is recorded in under [name]: the user's, when one converts to the other. */
        fun recordUnit(name: String, from: String): String {
            val to = units[name] ?: return from
            return if (MeasureUnits.sameKind(from, to)) to else from
        }

        fun addRecord(name: String, from: String, date: String, time: String, value: Double, comment: String?) {
            val unit = recordUnit(name, from)
            // Converted before it's recorded; the same unit on both sides keeps the figure as it is.
            val recorded = if (unit == from) value else MeasureUnits.convert(value, from, unit)
            val here = existing[name + "|" + date + "|" + time].orEmpty()
            val match = here.firstOrNull { matches(it, value, from) }
            if (match != null) {
                // An earlier import that couldn't tell the unit kept FitNotes's figure without one: put it right.
                if (match.source == "fitnotes" && !MeasureUnits.convertible(match.unit) && MeasureUnits.convertible(from) &&
                    same(match.value, value)
                ) {
                    w.setRecordValueUnit(match.id, recorded, unit)
                    plan.unitsFixed++
                }
                plan.recordsSkipped++
                return
            }
            // A value typed in by hand on that day, or an imported value the user edited or deleted, stays as they left
            // it (#166).
            if (manual[name + "|" + date].orEmpty().any { matches(it, value, from) } ||
                consumeSkip(Workouts.RULE_RECORD, Workouts.recordKey(name, date, time, recorded)) ||
                consumeSkip(Workouts.RULE_RECORD, Workouts.recordKey(name, date, time, value))
            ) {
                plan.recordsSkipped++
                return
            }
            val id = w.addRecord(name, unit, date, time, recorded, comment, "fitnotes")
            existing.getOrPut(name + "|" + date + "|" + time) { ArrayList() } +=
                MeasurementRecordRow(id, name, unit, date, time, recorded, comment, "fitnotes")
            plan.recordsAdded++
        }

        val defs = HashMap<Long, Pair<String, String>>()
        val readDefinition: (Cursor) -> Unit = readDefinition@{ c ->
            val name = c.strOr(1)
            val into = aliases[name.trim().lowercase()] ?: name
            val have = body.definition(into)
            val from = fitNotesUnit(c.strOr(2), c.strOr(7), metric == 0, have?.unit, name)
            defs[c.lng(0)] = into to from
            // The unit the user sees this measurement in, or for a new one the user's weight or length unit.
            val shown = when {
                have != null && MeasureUnits.convertible(have.unit) ->
                    MeasureUnits.display(have.unit, weightUnit, lengthUnit, have.display_unit) ?: have.unit
                else -> MeasureUnits.display(from, weightUnit, lengthUnit) ?: from
            }
            units[into] = shown
            val goal = if (MeasureUnits.sameKind(from, shown)) MeasureUnits.convert(c.dbl(5), from, shown) else c.dbl(5)
            when {
                have == null -> {
                    w.addMeasurement(name, shown, c.int(3), c.int(4), goal, c.int(6))
                    plan.measurementsAdded++
                }
                // A measurement with no unit yet takes this one; the unit the user set is never changed.
                have.unit.isBlank() -> w.setUnitIfNone(into, shown)
                // A FitNotes measurement the user hasn't edited follows FitNotes's order and goal (#27).
                have.custom == 0 && have.edited == 0 ->
                    w.refreshMeasurement(name, have.unit, c.int(3), c.int(4), MeasureUnits.convert(goal, shown, have.unit), c.int(6))
            }
        }
        // Not every FitNotes version keeps a long name for each unit.
        val longName = if (src.each("SELECT long_name FROM MeasurementUnit LIMIT 1") {}) "IFNULL(u.long_name, '')" else "''"
        src.each(
            "SELECT m._id, m.name, IFNULL(u.short_name, ''), m.sort_order, m.goal_type, m.goal_value, m.enabled, $longName " +
                "FROM Measurement m LEFT JOIN MeasurementUnit u ON u._id = m.unit_id",
            readDefinition
        )
        src.each("SELECT measurement_id, date, time, value, comment FROM MeasurementRecord") { c ->
            val def = defs[c.lng(0)] ?: return@each
            addRecord(def.first, def.second, c.strOr(1).take(10), c.strOr(2), c.dbl(3), c.str(4))
        }
        // Very old FitNotes versions kept body weight, always in kilograms, in a separate table.
        src.each("SELECT date, body_weight_metric, body_fat, comments FROM BodyWeight") { c ->
            val date = c.strOr(0).take(10)
            val time = c.strOr(0).drop(11).take(8)
            val bodyweight = target("Bodyweight")
            if (bodyweight !in units) {
                val have = body.definition(bodyweight)?.takeIf { MeasureUnits.convertible(it.unit) }
                units[bodyweight] = have?.let { MeasureUnits.display(it.unit, weightUnit, lengthUnit, it.display_unit) } ?: weightUnit
            }
            if (c.dbl(1) > 0) addRecord(bodyweight, "kg", date, time, c.dbl(1), c.str(3))
            if (c.dbl(2) > 0) addRecord(target("Body Fat"), "%", date, time, c.dbl(2), null)
        }

        // FitNotes's weight unit is used for display unless the user has picked one in FlexNotes.
        if (!db.metaDao.has("weight_unit_manual")) {
            db.metaDao.put(MetaRow("weight_unit", weightUnit))
        }
        return plan
    }

    /** Imports a FitNotes Body Tracker CSV export. Rows already present are skipped. */
    suspend fun importBodyCsv(context: Context, uri: Uri): ImportSummary = withContext(Dispatchers.IO) {
        try {
            val lines = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readLines() }
                ?: return@withContext ImportSummary(context.getString(R.string.imp_cant_open), false)
            if (lines.isEmpty() || !lines[0].startsWith("Date,Time,Measurement")) {
                return@withContext ImportSummary(context.getString(R.string.imp_csv_not_body), false)
            }
            val (added, skipped) = mergeBodyCsv(lines, Store.db)
            Store.reload()
            val res = context.resources
            ImportSummary(
                res.getString(
                    R.string.imp_csv_done,
                    res.getQuantityString(R.plurals.imp_csv_added, added, added),
                    res.getQuantityString(R.plurals.imp_csv_present, skipped, skipped)
                ),
                true
            )
        } catch (e: Exception) {
            ImportSummary(context.getString(R.string.imp_csv_failed, e.message ?: e.javaClass.simpleName), false)

        }
    }

    /**
     * Adds the values of a Body Tracker CSV ([lines], header first) to [db] in one transaction, skipping those already
     * there. Returns how many were added and how many skipped. Off the main thread.
     */
    internal fun mergeBodyCsv(lines: List<String>, db: Db): Pair<Int, Int> = db.transaction {
        val w = db.importDao
        var added = 0
        var skipped = 0
        val aliases = customAliases(w)
        // Values the user edited or deleted after an earlier import (#166), counted like the backup import's skips.
        val skips = HashMap<String, Int>()
        w.rules().filter { it.kind == Workouts.RULE_RECORD }.forEach { skips[it.key] = (skips[it.key] ?: 0) + 1 }
        for (line in lines.drop(1)) {
            if (line.isBlank()) continue
            val f = parseCsvLine(line)
            if (f.size < 4) continue
            val date = f[0].take(10)
            val time = f[1]
            val name = aliases[f[2].trim().lowercase()] ?: f[2]
            val value = f[3].toDoubleOrNull() ?: continue
            val unit = f.getOrElse(4) { "" }
            val comment = f.getOrElse(5) { "" }
            if (w.hasRecord(name, date, time, value)) { skipped++; continue }
            val key = Workouts.recordKey(name, date, time, value)
            val n = skips[key] ?: 0
            if (n > 0) { skips[key] = n - 1; skipped++; continue }
            db.bodyDao.ensure(name, unit, 999)
            w.addRecord(name, unit, date, time, value, comment, "csv")
            added++
        }
        added to skipped
    }

    /** Lower-case FitNotes measurement name → custom metric that takes its values. */
    /**
     * Metres in one of FitNotes's distance units, by the id a set keeps in `training_log.unit` (#180): 0 km, 1 miles,
     * 2 metres, 3 yards. Metres is confirmed from a real backup; the others follow FitNotes's order of units. An
     * unknown id gives null, and the distance is kept as logged.
     */
    internal fun fitNotesDistanceMetres(unit: Int): Double? = when (unit) {
        0 -> 1000.0
        1 -> 1609.344
        2 -> 1.0
        3 -> 0.9144
        else -> null
    }

    /** FitNotes's exercise type for distance and time (cardio). */
    private const val FITNOTES_CARDIO = 1

    /**
     * The unit a FitNotes measurement's values are in: its [short] name if FlexNotes knows it ("in", "kg"), else its
     * [long] name ("Inches"). With neither, a weight or length is told apart by the unit of the FlexNotes measurement it
     * goes into ([target]) or of the standard measurement of that [name], and gets FitNotes's units setting
     * ([imperial]): pounds or inches, otherwise kilograms or centimetres. Anything else ("%") keeps its short name.
     * Internal for `FitNotesImporterTest`.
     */
    internal fun fitNotesUnit(short: String, long: String, imperial: Boolean, target: String?, name: String): String {
        if (MeasureUnits.convertible(short)) return short.trim()
        (WeightUnits.of(long) ?: LengthUnits.of(long))?.let { return it }
        if (short.isNotBlank()) return short
        val like = target?.takeIf { MeasureUnits.convertible(it) }
            ?: StandardMeasurements.all.firstOrNull { it.first.equals(name.trim(), ignoreCase = true) }?.second
        return when {
            WeightUnits.of(like) != null -> if (imperial) "lbs" else "kg"
            LengthUnits.of(like) != null -> if (imperial) LengthUnits.IN else LengthUnits.CM
            else -> short
        }
    }

    private fun customAliases(w: ImportDao): Map<String, String> {
        val out = HashMap<String, String>()
        for (m in w.customMetrics()) {
            val key = (m.link?.takeIf { it.isNotBlank() } ?: m.name).trim().lowercase()
            out[key] = m.name
        }
        return out
    }

    fun parseCsvLine(line: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < line.length && line[i + 1] == '"') { sb.append('"'); i++ } else inQuotes = false
                } else sb.append(ch)
            } else {
                when (ch) {
                    '"' -> inQuotes = true
                    ',' -> { out.add(sb.toString()); sb.setLength(0) }
                    else -> sb.append(ch)
                }
            }
            i++
        }
        out.add(sb.toString())
        return out
    }
}
