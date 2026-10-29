package com.fitlens.companion

import android.app.Application
import com.fitlens.companion.data.AppHooks
import com.fitlens.companion.data.AutoBackup
import com.fitlens.companion.data.HealthConnect
import com.fitlens.companion.data.LogReminders
import com.fitlens.companion.data.MedReminders
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.Steps
import com.fitlens.companion.data.Store
import com.fitlens.companion.ui.Brand
import com.fitlens.companion.ui.BrandFonts
import com.fitlens.companion.ui.MainActivity
import com.fitlens.companion.ui.TimerService
import com.fitlens.companion.ui.TodayWidgets
import com.fitlens.companion.ui.AppScope
import androidx.compose.ui.graphics.toArgb
import android.content.Intent

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // What the data layer needs from the UI (#309), set before anything can post a notification or refresh a widget.
        AppHooks.accent = Brand.Gold.toArgb()
        AppHooks.openApp = { context -> Intent(context, MainActivity::class.java) }
        AppHooks.dataChanged = { TodayWidgets.refreshSoon() }
        AppScope.init(this)
        BrandFonts.init(this)
        Store.init(this)
        Settings.init(this)
        AutoBackup.start(this)
        // Daily steps (#175): the background reading follows the setting.
        Steps.start(this)
        // Health Connect (#184): the hourly background sync follows the settings and its permission.
        HealthConnect.start(this)
        // The timers' notification follows the workout timer wherever it is started or stopped (#12).
        TimerService.watch(this)
        // Medication reminders (#203): the next one is set once the data has loaded, and after every change.
        MedReminders.init(this)
        // Reminders for weigh-ins, measurements, photos and workout days (#219).
        LogReminders.init(this)
        // The home-screen widget (#220) is drawn again after every change to the data.
        TodayWidgets.init(this)
    }
}
