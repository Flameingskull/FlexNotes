package com.fitlens.companion.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
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
 * Exports photos as ordinary image files into a folder the user picks (#195, first slice: by hand from the Photos
 * screen). Each file is named by [ExportNames], and a copy, never the stored photo, gets EXIF `DateTimeOriginal` and
 * `DateTimeDigitized` (with their offsets) set to its FlexNotes date and time, the pose and comment in
 * `ImageDescription` and the comment in `UserComment`, keeping its other EXIF. Formats ExifInterface can't write
 * (HEIC, for one) are copied as they are. Nothing in FlexNotes changes, and nothing in the folder is overwritten or
 * deleted. A missing photo file is skipped, one that can't be written fails; neither stops the run. One export at a
 * time, cancelable, its progress in [progress].
 */
object PhotoExport {
    data class Progress(val done: Int, val total: Int)

    /** What an export did. [cancelled]: stopped by the user after [exported] files. */
    data class Result(val exported: Int, val skipped: Int, val failed: Int, val cancelled: Boolean)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val _progress = MutableStateFlow<Progress?>(null)

    /** The export running, or null. */
    val progress: StateFlow<Progress?> = _progress

    private val EXIF_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

    /**
     * Exports [photos] into the folder [tree] in the background; [describe] words a photo's `ImageDescription`, and
     * [onDone] gets the result. False when an export is already running.
     */
    fun start(context: Context, tree: Uri, photos: List<Photo>, describe: (Photo) -> String, onDone: (Result) -> Unit): Boolean {
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
                val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                val taken = existingNames(app, tree)
                photos.forEachIndexed { i, p ->
                    coroutineContext.ensureActive()
                    val source = File(Store.photoDir, p.file)
                    if (!source.isFile || !source.canRead()) {
                        skipped++
                    } else {
                        var doc: Uri? = null
                        try {
                            val ext = ExportNames.extension(p.file)
                            val copy = File(temp, "export.$ext")
                            source.copyTo(copy, overwrite = true)
                            if (!writeExif(copy, p, describe(p))) source.copyTo(copy, overwrite = true)
                            val name = ExportNames.fileName(p.date, p.pose, ext, taken)
                            doc = DocumentsContract.createDocument(resolver, parent, ExportNames.mime(ext), name)
                            val out = doc?.let { resolver.openOutputStream(it, "w") }
                            if (out == null) {
                                failed++
                            } else {
                                out.use { o -> copy.inputStream().use { it.copyTo(o) } }
                                doc = null
                                exported++
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            failed++
                        } finally {
                            // A file left half written by a failure or a cancel is removed; finished ones stay.
                            doc?.let { d -> runCatching { DocumentsContract.deleteDocument(resolver, d) } }
                        }
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

    /** The names already in the folder, lower case. */
    private fun existingNames(context: Context, tree: Uri): MutableSet<String> {
        val names = HashSet<String>()
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        context.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            while (c.moveToNext()) c.getString(0)?.let { names += it.lowercase(Locale.ROOT) }
        }
        return names
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
