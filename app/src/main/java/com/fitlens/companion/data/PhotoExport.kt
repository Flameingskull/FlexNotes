package com.fitlens.companion.data

import android.content.ContentResolver
import android.content.Context
import android.content.res.Resources
import android.net.Uri
import android.provider.DocumentsContract
import androidx.exifinterface.media.ExifInterface
import com.fitlens.companion.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * File names for exported photos (#195), plain Kotlin and unit tested (`PhotoExportTest`): `YYYY-MM-DD_<pose>.<ext>`,
 * with `_2`, `_3` and so on when the folder already has that name, so nothing there is ever overwritten.
 */
internal object ExportNames {
    /** [p]'s file name, not one of [taken] (lower-cased names already in the folder); the name chosen is added to it. */
    fun fileName(date: String?, pose: String, ext: String, taken: MutableSet<String>): String {
        val day = date?.take(10)?.takeIf { Dates.parse(it) != null } ?: "undated"
        val what = pose.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }.ifEmpty { "photo" }
        val base = "${day}_$what"
        var name = "$base.$ext"
        var n = 2
        while (name.lowercase(Locale.ROOT) in taken) name = "${base}_${n++}.$ext"
        taken += name.lowercase(Locale.ROOT)
        return name
    }

    /** The stored file's extension, lower case ("jpeg" kept as it is), or "jpg" when it has none. */
    fun extension(file: String): String =
        file.substringAfterLast('/').substringAfterLast('.', "").lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }.ifEmpty { "jpg" }

    fun mime(ext: String): String = when (ext) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "heic", "heif" -> "image/heif"
        "gif" -> "image/gif"
        else -> "image/jpeg"
    }

    /** When [p] was taken, by its FlexNotes date: its own time that day, else midday. Null when it has no date. */
    fun takenAt(p: Photo): LocalDateTime? {
        val day = Dates.parse(p.date?.take(10)) ?: return null
        val at = p.takenAt?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
        return if (at != null && at.toLocalDate() == day) at else day.atTime(12, 0)
    }
}

/**
 * What the automatic-backup copy (#195) has already written into a folder, so each run writes only new or changed
 * photos. Plain Kotlin, unit tested (`PhotoExportTest`). Kept as a small file on this phone ([PhotoExport.recordFile]),
 * like the folder itself never in a `.flexnotes` backup: the folder's URI on the first line, then one line a photo,
 * "id, signature, file name" separated by tabs. A record for another folder is ignored, so choosing a new backup
 * folder copies every photo there once.
 */
internal object ExportRecord {
    data class Entry(val signature: String, val name: String)

    /** The entries in [text] when it was written for [folder]; none otherwise, or for anything unreadable. */
    fun parse(text: String?, folder: String): Map<Long, Entry> {
        val lines = text?.lines() ?: return emptyMap()
        if (lines.firstOrNull() != folder) return emptyMap()
        return lines.drop(1).mapNotNull { line ->
            val parts = line.split('\t')
            val id = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            if (parts.size != 3 || parts[1].isEmpty() || parts[2].isEmpty()) return@mapNotNull null
            id to Entry(parts[1], parts[2])
        }.toMap()
    }

    fun encode(folder: String, entries: Map<Long, Entry>): String =
        (listOf(folder) + entries.entries.sortedBy { it.key }.map { (id, e) -> "$id\t${e.signature}\t${e.name}" }).joinToString("\n")

    /**
     * [p]'s signature: the stored file's [size] and [modified] time, and a hash of what its metadata carries (date,
     * time, pose, comment). A change in any of them copies the photo again.
     */
    fun signature(p: Photo, size: Long, modified: Long): String =
        "$size:$modified:${listOf(p.date, p.takenAt, p.pose, p.note).joinToString("\u0001").hashCode()}"

    /** The photos whose signature differs from the one recorded; [signature] is null for a photo whose file is missing. */
    fun due(photos: List<Photo>, record: Map<Long, Entry>, signature: (Photo) -> String?): List<Photo> =
        photos.filter { p -> signature(p)?.let { it != record[p.id]?.signature } == true }
}

/**
 * Exports photos as ordinary image files into a folder (#195): by hand from the Photos screen ([start]), into a folder
 * the user picks, and with each automatic backup when Settings › Backups › Copy photos as image files is on
 * ([backupCopy]), into a `Photos` folder inside the backup folder. Each file is named by [ExportNames], and a copy,
 * never the stored photo, gets EXIF `DateTimeOriginal` and `DateTimeDigitized` (with their offsets) set to its FlexNotes
 * date and time, the pose and comment in `ImageDescription` and the comment in `UserComment`, keeping its other EXIF.
 * Formats ExifInterface can't write (HEIC, for one) are copied as they are. Nothing in FlexNotes changes, and nothing
 * in the folder is deleted; the only file ever written again is the backup copy's own earlier file of the same photo,
 * when the photo changed and its name still fits. A missing photo file is skipped, one that can't be written fails;
 * neither stops the run. One export by hand at a time, cancelable, its progress in [progress].
 */
object PhotoExport {
    data class Progress(val done: Int, val total: Int)

    /** What an export did. [cancelled]: stopped by the user after [exported] files. */
    data class Result(val exported: Int, val skipped: Int, val failed: Int, val cancelled: Boolean)

    /** The folder the automatic-backup copy writes into, inside the backup folder. */
    const val BACKUP_SUBFOLDER = "Photos"

    /** How often the backup copy saves its record while it runs, in files. */
    private const val SAVE_EVERY = 20

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val _progress = MutableStateFlow<Progress?>(null)

    /** The export running, or null. */
    val progress: StateFlow<Progress?> = _progress

    private val EXIF_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

    /** "FlexNotes progress photo · Front · 16 Oct 2026 · comment", an exported photo's `ImageDescription`. */
    fun description(res: Resources, p: Photo): String = listOfNotNull(
        res.getString(R.string.ph_export_description),
        if (p.pose.isBlank()) null else poseText(res, p.pose),
        p.date?.let { Dates.medium(it) },
        p.note?.trim()?.takeIf { it.isNotEmpty() }
    ).joinToString(" · ")

    /**
     * Exports [photos] into the folder [tree] in the background, and [onDone] gets the result. False when an export is
     * already running.
     */
    fun start(context: Context, tree: Uri, photos: List<Photo>, onDone: (Result) -> Unit): Boolean {
        if (job?.isActive == true) return false
        val app = context.applicationContext
        _progress.value = Progress(0, photos.size)
        job = scope.launch {
            var exported = 0
            var skipped = 0
            var failed = 0
            var cancelled = false
            val temp = File(app.cacheDir, "export").apply { mkdirs() }
            try {
                val resolver = app.contentResolver
                val rootId = DocumentsContract.getTreeDocumentId(tree)
                val parent = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
                val taken = HashSet(children(resolver, tree, rootId).keys)
                photos.forEachIndexed { i, p ->
                    coroutineContext.ensureActive()
                    val name = ExportNames.fileName(p.date, p.pose, ExportNames.extension(p.file), taken)
                    when (write(app, resolver, parent, null, p, name, temp)) {
                        Written.OK -> exported++
                        Written.MISSING -> skipped++
                        Written.FAILED -> failed++
                    }
                    _progress.value = Progress(i + 1, photos.size)
                }
            } catch (e: CancellationException) {
                cancelled = true
            } catch (e: Exception) {
                failed += (photos.size - exported - skipped - failed).coerceAtLeast(0)
            } finally {
                temp.deleteRecursively()
                _progress.value = null
                onDone(Result(exported, skipped, failed, cancelled))
            }
        }
        return true
    }

    /** Stops the running export after the file it's writing. */
    fun cancel() {
        job?.cancel()
    }

    /** The record of what the backup copy wrote, on this phone only. */
    private fun recordFile(context: Context) = File(context.noBackupFilesDir, "photo_exports.tsv")

    /**
     * The automatic-backup copy (#195): writes every photo in [photos] that's new or changed since the last run into
     * the `Photos` folder inside the backup folder [tree], made when it isn't there. Runs in the caller's coroutine
     * (the backup's), and keeps its record as it goes, so a run stopped part way carries on from there next time.
     * [Result.skipped] counts photos whose files are missing.
     */
    suspend fun backupCopy(context: Context, tree: Uri, photos: List<Photo>): Result = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val res = app.resources
        val resolver = app.contentResolver
        val folderKey = tree.toString()
        val recordFile = recordFile(app)
        val record = HashMap(ExportRecord.parse(runCatching { recordFile.readText() }.getOrNull(), folderKey))
        // Photos deleted from FlexNotes leave the record; their files stay in the folder.
        record.keys.retainAll(photos.map { it.id }.toSet())
        val signatures = photos.associate { p ->
            val f = File(Store.photoDir, p.file)
            p.id to if (f.isFile && f.canRead()) ExportRecord.signature(p, f.length(), f.lastModified()) else null
        }
        val missing = signatures.count { it.value == null }
        val due = ExportRecord.due(photos, record) { signatures[it.id] }.sortedBy { it.date }
        fun save() = runCatching { recordFile.writeText(ExportRecord.encode(folderKey, record)) }
        if (due.isEmpty()) {
            save()
            return@withContext Result(0, missing, 0, false)
        }
        var exported = 0
        var failed = 0
        var cancelled = false
        val temp = File(app.cacheDir, "backup_photos").apply { mkdirs() }
        try {
            val folderId = subfolder(resolver, tree)
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, folderId)
            val docs = children(resolver, tree, folderId)
            val taken = HashSet(docs.keys)
            due.forEachIndexed { i, p ->
                currentCoroutineContext().ensureActive()
                // A changed photo replaces the file this copy wrote for it before, when that file is still there and
                // its name still fits the photo's date and pose; otherwise it gets a new file and the old one stays.
                val old = record[p.id]?.name?.lowercase(Locale.ROOT)?.takeIf { it in docs }
                if (old != null) taken -= old
                val name = ExportNames.fileName(p.date, p.pose, ExportNames.extension(p.file), taken)
                val reuse = old != null && name.lowercase(Locale.ROOT) == old
                if (old != null && !reuse) taken += old
                val existing = if (reuse) old?.let { docs[it] }?.let { DocumentsContract.buildDocumentUriUsingTree(tree, it) } else null
                when (write(app, resolver, parent, existing, p, name, temp, description(res, p))) {
                    Written.OK -> {
                        exported++
                        signatures[p.id]?.let { record[p.id] = ExportRecord.Entry(it, name) }
                    }
                    Written.MISSING -> {}
                    Written.FAILED -> failed++
                }
                if ((i + 1) % SAVE_EVERY == 0) save()
            }
        } catch (e: CancellationException) {
            cancelled = true
        } catch (e: Exception) {
            failed += (due.size - exported - failed).coerceAtLeast(0)
        } finally {
            temp.deleteRecursively()
            save()
        }
        if (cancelled) throw CancellationException("Photo copy stopped")
        Result(exported, missing, failed, false)
    }

    private enum class Written { OK, MISSING, FAILED }

    /**
     * Writes [p] as [name] into the folder [parent], or over [existing] (the backup copy's own earlier file of it).
     * A file left half written by a failure or a cancel is removed; finished ones stay.
     */
    private suspend fun write(
        context: Context,
        resolver: ContentResolver,
        parent: Uri,
        existing: Uri?,
        p: Photo,
        name: String,
        temp: File,
        text: String = description(context.resources, p)
    ): Written {
        val source = File(Store.photoDir, p.file)
        if (!source.isFile || !source.canRead()) return Written.MISSING
        var created: Uri? = null
        return try {
            val ext = ExportNames.extension(p.file)
            val copy = File(temp, "export.$ext")
            source.copyTo(copy, overwrite = true)
            if (!writeExif(copy, p, text)) source.copyTo(copy, overwrite = true)
            currentCoroutineContext().ensureActive()
            val doc = existing
                ?: DocumentsContract.createDocument(resolver, parent, ExportNames.mime(ext), name)?.also { created = it }
            val out = doc?.let { resolver.openOutputStream(it, if (existing != null) "wt" else "w") }
            if (out == null) {
                Written.FAILED
            } else {
                out.use { o -> copy.inputStream().use { it.copyTo(o) } }
                created = null
                Written.OK
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Written.FAILED
        } finally {
            created?.let { d -> runCatching { DocumentsContract.deleteDocument(resolver, d) } }
        }
    }

    /** The document id of the backup folder's `Photos` folder, made when it isn't there. */
    private fun subfolder(resolver: ContentResolver, tree: Uri): String {
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, rootId)
        resolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR && c.getString(1) == BACKUP_SUBFOLDER) {
                    return c.getString(0)
                }
            }
        }
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
        val made = DocumentsContract.createDocument(resolver, parent, DocumentsContract.Document.MIME_TYPE_DIR, BACKUP_SUBFOLDER)
            ?: throw IOException("Couldn't make the $BACKUP_SUBFOLDER folder")
        return DocumentsContract.getDocumentId(made)
    }

    /** The files in the folder [folderId] of [tree]: lower-case name to document id. */
    private fun children(resolver: ContentResolver, tree: Uri, folderId: String): Map<String, String> {
        val found = HashMap<String, String>()
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, folderId)
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                found[name.lowercase(Locale.ROOT)] = c.getString(0)
            }
        }
        return found
    }

    /** Sets [file]'s capture date and time, offset, description and comment. False when its format can't be written. */
    private fun writeExif(file: File, p: Photo, description: String): Boolean = try {
        val exif = ExifInterface(file.path)
        ExportNames.takenAt(p)?.let { at ->
            val stamp = at.format(EXIF_TIME)
            val offset = ZoneId.systemDefault().rules.getOffset(at).let { if (it.totalSeconds == 0) "+00:00" else it.id.take(6) }
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, stamp)
            exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, stamp)
            exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, offset)
            exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_DIGITIZED, offset)
        }
        exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, description)
        p.note?.trim()?.takeIf { it.isNotEmpty() }?.let { exif.setAttribute(ExifInterface.TAG_USER_COMMENT, it) }
        exif.saveAttributes()
        true
    } catch (e: Exception) {
        false
    }

    /** Photos dated from [from] to [to] (ISO, inclusive), oldest first. */
    fun inRange(photos: List<Photo>, from: String, to: String): List<Photo> =
        photos.filter { p -> p.date?.take(10)?.let { it >= from && it <= to } == true }.sortedBy { it.date }
}
