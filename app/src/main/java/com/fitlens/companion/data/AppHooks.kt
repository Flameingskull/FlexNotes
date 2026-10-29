package com.fitlens.companion.data

import android.content.Context
import android.content.Intent

/**
 * What the `data` layer needs from the app on top of it, without importing `ui` (#309). `App.onCreate` sets these once,
 * before anything else starts, and an Application is always created before a worker, receiver or activity runs, so
 * every entry point sees the real values. The defaults are safe for tests and tools that never start `App`.
 */
object AppHooks {
    /** The notification accent colour as an ARGB int; 0 is the system default. */
    @Volatile var accent: Int = 0

    /** The intent that opens the app, for a notification tap. The default is the launcher intent from the package manager. */
    @Volatile var openApp: (Context) -> Intent = { c -> c.packageManager.getLaunchIntentForPackage(c.packageName) ?: Intent() }

    /** Called when stored data was re-read (the home-screen widget draws itself again). The default does nothing. */
    @Volatile var dataChanged: () -> Unit = {}
}
