package org.adaway

import android.app.Application
import com.topjohnwu.superuser.Shell

class AdAwayApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        Shell.enableVerboseLogging = BuildConfig.DEBUG
        container = AppContainer(this)
    }
}
