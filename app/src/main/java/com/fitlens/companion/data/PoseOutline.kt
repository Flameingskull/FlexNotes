package com.fitlens.companion.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The body outline of a photo (#230): closed lines around the person, each a [FloatArray] of x, y pairs as fractions
 * of the photo's [width] and [height] (0 to 1), so the camera can map them with the photo's own crop. No [loops]: no
 * person was found.
 */
class BodyOutline(val width: Int, val height: Int, val loops: List<FloatArray>) {
    val found: Boolean get() = loops.isNotEmpty()

    /** One line per loop after the size: `w h`, then each loop's numbers. Read back by [decode]. */
    fun encode(): String = buildString {
        append(width).append(' ').append(height).append('\n')
        loops.forEach { loop -> append(loop.joinToString(" ") { "%.4f".format(java.util.Locale.ROOT, it) }).append('\n') }
    }

    companion object {
        /** The camera's line choices, kept in `DeviceSettings.cameraOutlineLine`. */
        const val THIN = 0
        const val MEDIUM = 1
        const val BOLD = 2

        /** A mask cell counts as the person at this confidence or more. */
        const val THRESHOLD = 0.5f

        /** The person must cover at least this share of the photo, or the outline is treated as not found. */
        const val MIN_SHARE = 0.02

        /** Loops shorter than this many points are specks, not part of the body. */
        private const val MIN_POINTS = 12

        fun decode(text: String): BodyOutline? = runCatching {
            val lines = text.lines().filter { it.isNotBlank() }
            val (w, h) = lines.first().trim().split(' ').map { it.toInt() }
            val loops = lines.drop(1).map { line -> line.trim().split(' ').map { it.toFloat() }.toFloatArray() }
            if (w <= 0 || h <= 0 || loops.any { it.size < 4 || it.size % 2 != 0 }) null else BodyOutline(w, h, loops)
        }.getOrNull()

        /**
         * Traces [mask] (row-major, [w] × [h], true for the person) into an outline. Only the largest connected
         * region is kept, so stray specks in the background don't get a line; holes inside it (the gap between an arm
         * and the body) keep theirs. Marching squares on the pixel grid, padded so every loop closes, then chained
         * into loops and lightly smoothed. Pure, tested in `PoseOutlineTest`.
         */
        fun trace(mask: BooleanArray, w: Int, h: Int): BodyOutline {
            require(mask.size == w * h)
            val body = largest(mask, w, h) ?: return BodyOutline(w, h, emptyList())
            if (body.count { it } < MIN_SHARE * w * h) return BodyOutline(w, h, emptyList())
            fun at(x: Int, y: Int) = x in 0 until w && y in 0 until h && body[y * w + x]
            // Points sit on cell edges, in doubled pixel coordinates so they're exact integers: the edge between
            // pixels (x, y) and (x + 1, y) is (2x + 1, 2y).
            val links = HashMap<Long, LongArray>()
            fun key(x: Int, y: Int) = (x.toLong() shl 32) or (y.toLong() and 0xffffffffL)
            fun link(a: Long, b: Long) {
                links[a] = links[a]?.let { longArrayOf(it[0], b) } ?: longArrayOf(b, Long.MIN_VALUE)
                links[b] = links[b]?.let { longArrayOf(it[0], a) } ?: longArrayOf(a, Long.MIN_VALUE)
            }
            for (y in -1 until h) for (x in -1 until w) {
                val tl = at(x, y)
                val tr = at(x + 1, y)
                val br = at(x + 1, y + 1)
                val bl = at(x, y + 1)
                val top = key(2 * x + 1, 2 * y)
                val right = key(2 * x + 2, 2 * y + 1)
                val bottom = key(2 * x + 1, 2 * y + 2)
                val left = key(2 * x, 2 * y + 1)
                val crossed = buildList {
                    if (tl != tr) add(top)
                    if (tr != br) add(right)
                    if (bl != br) add(bottom)
                    if (tl != bl) add(left)
                }
                when (crossed.size) {
                    2 -> link(crossed[0], crossed[1])
                    // A saddle: the two diagonal corners are separate, as the 4-connected [largest] sees them.
                    4 -> if (tl) { link(top, left); link(right, bottom) } else { link(top, right); link(bottom, left) }
                }
            }
            val seen = HashSet<Long>()
            val loops = ArrayList<FloatArray>()
            for (start in links.keys) {
                if (start in seen) continue
                val points = ArrayList<Long>()
                var prev = Long.MIN_VALUE
                var cur = start
                while (cur !in seen) {
                    seen += cur
                    points += cur
                    val n = links[cur] ?: break
                    val next = if (n[0] != prev) n[0] else n[1]
                    if (next == Long.MIN_VALUE) break
                    prev = cur
                    cur = next
                }
                if (points.size < MIN_POINTS) continue
                loops += smooth(points.map { (it shr 32).toInt() }, points.map { it.toInt() }, w, h)
            }
            return BodyOutline(w, h, loops)
        }

        /** A closed loop averaged over five neighbouring points, as fractions of the photo. */
        private fun smooth(xs: List<Int>, ys: List<Int>, w: Int, h: Int): FloatArray {
            val n = xs.size
            val out = FloatArray(n * 2)
            for (i in 0 until n) {
                var sx = 0f
                var sy = 0f
                for (d in -2..2) {
                    val j = ((i + d) % n + n) % n
                    sx += xs[j]
                    sy += ys[j]
                }
                // Doubled grid to pixels, then a pixel's centre to a fraction of the photo.
                out[2 * i] = ((sx / 5f / 2f + 0.5f) / w).coerceIn(0f, 1f)
                out[2 * i + 1] = ((sy / 5f / 2f + 0.5f) / h).coerceIn(0f, 1f)
            }
            return out
        }

        /** The largest 4-connected region of [mask], or null if it's empty. */
        internal fun largest(mask: BooleanArray, w: Int, h: Int): BooleanArray? {
            val label = IntArray(w * h)
            var best = 0
            var bestSize = 0
            var next = 0
            val stack = IntArray(w * h)
            for (i in mask.indices) {
                if (!mask[i] || label[i] != 0) continue
                next++
                var size = 0
                var top = 0
                stack[top++] = i
                label[i] = next
                while (top > 0) {
                    val p = stack[--top]
                    size++
                    val x = p % w
                    val y = p / w
                    fun visit(q: Int) {
                        if (mask[q] && label[q] == 0) { label[q] = next; stack[top++] = q }
                    }
                    if (x > 0) visit(p - 1)
                    if (x < w - 1) visit(p + 1)
                    if (y > 0) visit(p - w)
                    if (y < h - 1) visit(p + w)
                }
                if (size > bestSize) { bestSize = size; best = next }
            }
            if (best == 0) return null
            return BooleanArray(w * h) { label[it] == best }
        }
    }
}

/**
 * Finding the body in a photo for the camera's outline (#230), on the phone with ML Kit's bundled selfie segmentation
 * model: no download and no internet. Each photo is worked out once, off the main thread, and kept in memory and in
 * `cacheDir/outline`, so opening the camera again shows the line straight away.
 */
object PoseOutlines {
    private const val DIR = "outline"
    /** The long side of the copy the model sees; enough for a clean line, quick to work out. */
    private const val LONG_SIDE = 512

    private val memory = HashMap<String, BodyOutline>()
    private val lock = Mutex()

    /** The outline of [file], or null if it can't be read or worked out. */
    suspend fun of(ctx: Context, file: File): BodyOutline? = withContext(Dispatchers.Default) {
        if (!file.exists()) return@withContext null
        val id = cacheKey(file)
        lock.withLock {
            memory[id]?.let { return@withContext it }
            val cached = File(File(ctx.cacheDir, DIR), "$id.txt")
            val fromDisk = runCatching { cached.takeIf { it.exists() }?.readText()?.let(BodyOutline::decode) }.getOrNull()
            val outline = fromDisk ?: runCatching { work(file) }.getOrNull()?.also { o ->
                runCatching { cached.parentFile?.mkdirs(); cached.writeText(o.encode()) }
            }
            outline?.also { memory[id] = it }
        }
    }

    /** The file's path, size and time, so a replaced photo is worked out again. */
    private fun cacheKey(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1").digest("${file.absolutePath}|${file.length()}|${file.lastModified()}".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun work(file: File): BodyOutline? {
        val bitmap = load(file) ?: return null
        val segmenter = Segmentation.getClient(
            SelfieSegmenterOptions.Builder().setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE).build()
        )
        try {
            val mask = Tasks.await(segmenter.process(InputImage.fromBitmap(bitmap, 0)))
            val w = mask.width
            val h = mask.height
            // The buffer keeps the byte order ML Kit wrote it in.
            val buffer = mask.buffer
            buffer.rewind()
            val floats = buffer.asFloatBuffer()
            val person = BooleanArray(w * h) { floats.get(it) >= BodyOutline.THRESHOLD }
            return BodyOutline.trace(person, w, h)
        } finally {
            segmenter.close()
            bitmap.recycle()
        }
    }

    /** The photo turned upright as its EXIF says (as the camera shows it), at about [LONG_SIDE] on its long side. */
    private fun load(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= LONG_SIDE) sample *= 2
        val raw = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val matrix = Matrix()
        when (runCatching { ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
        }
        val scale = LONG_SIDE.toFloat() / maxOf(raw.width, raw.height)
        if (scale < 1f) matrix.postScale(scale, scale)
        if (matrix.isIdentity) return raw
        val upright = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        if (upright !== raw) raw.recycle()
        return upright
    }
}
