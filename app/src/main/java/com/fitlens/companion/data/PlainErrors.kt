package com.fitlens.companion.data

import android.content.res.Resources
import android.util.Log
import com.fitlens.companion.R

/**
 * Failure messages in plain words (#274). A failed backup, restore, import or export used to end with the raw exception
 * text ("java.io.FileNotFoundException: /storage/emulated/0/... (Permission denied)"). [classify] sorts a failure into the
 * few causes a person can act on, and [reason] says what to do next. The exception itself goes to the log, never to the
 * screen. [classify] is plain Kotlin and tested in `PlainErrorsTest`.
 */
object PlainErrors {
    enum class Cause { NO_SPACE, PERMISSION, MISSING, UNREADABLE, UNKNOWN }

    /** The cause of [e], looking through the exceptions that caused it. */
    fun classify(e: Throwable?): Cause {
        var t = e
        var depth = 0
        while (t != null && depth < 8) {
            val name = t.javaClass.simpleName
            val text = t.message.orEmpty().lowercase()
            when {
                "no space left" in text || "enospc" in text || "not enough space" in text || "disk full" in text -> return Cause.NO_SPACE
                t is SecurityException || "permission denied" in text || "eacces" in text || "permission denial" in text -> return Cause.PERMISSION
                t is java.io.FileNotFoundException || "enoent" in text || "no such file" in text -> return Cause.MISSING
                t is java.util.zip.ZipException || t is java.io.EOFException || "Serialization" in name || "Json" in name ||
                    "malformed" in text || "unexpected end" in text || "not a zip" in text -> return Cause.UNREADABLE
            }
            t = t.cause?.takeIf { it !== t }
            depth++
        }
        return Cause.UNKNOWN
    }

    /** What happened and what to do next, with no class names or file paths. The exception is logged. */
    fun reason(res: Resources, e: Throwable?): String {
        if (e != null) runCatching { Log.w("FlexNotes", "A job failed", e) }
        return res.getString(
            when (classify(e)) {
                Cause.NO_SPACE -> R.string.pe_no_space
                Cause.PERMISSION -> R.string.pe_permission
                Cause.MISSING -> R.string.pe_missing
                Cause.UNREADABLE -> R.string.pe_unreadable
                Cause.UNKNOWN -> R.string.pe_unknown
            }
        )
    }
}
