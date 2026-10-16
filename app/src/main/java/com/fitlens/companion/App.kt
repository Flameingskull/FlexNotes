package com.fitlens.companion

import android.app.Application
import com.fitlens.companion.data.AutoBackup
import com.fitlens.companion.data.HealthConnect
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.Steps
import com.fitlens.companion.data.Store
import com.fitlens.companion.ui.BrandFonts
import com.fitlens.companion.ui.TimerService
import com.fitlens.companion.ui.AppScope

class App : Application() {
    override fun onCreate() {
        super.onCreate()
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
    }
}
