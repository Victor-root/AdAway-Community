package org.adaway

import android.app.Application
import android.content.Context
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import org.adaway.data.settings.Settings
import org.adaway.system.Notifications
import org.adaway.work.SourceUpdateWorker
import org.adaway.work.VpnHeartbeatWorker

class AdAwayApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        Shell.enableVerboseLogging = BuildConfig.DEBUG
        container = AppContainer(this)
        Notifications.createChannels(this)
        scheduleOnChange({ it.sourcesPeriodicCheck to it.sourcesUnmeteredOnly }, SourceUpdateWorker.Companion::schedule)
        scheduleOnChange({ it.blockingMethod to it.vpnWanted }, VpnHeartbeatWorker.Companion::schedule)
    }

    /** Keeps a periodic work in line with the settings it depends on. */
    private fun scheduleOnChange(key: (Settings) -> Any, schedule: (Context, Settings) -> Unit) {
        container.applicationScope.launch {
            container.dataInitializer.ensureInitialized()
            container.settingsRepository.settings
                .distinctUntilChangedBy(key)
                .collect { settings -> schedule(this@AdAwayApplication, settings) }
        }
    }
}
