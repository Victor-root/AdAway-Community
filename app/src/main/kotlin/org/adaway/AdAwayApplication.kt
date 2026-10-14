package org.adaway

import android.app.Application
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import org.adaway.system.Notifications
import org.adaway.work.SourceUpdateWorker

class AdAwayApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        Shell.enableVerboseLogging = BuildConfig.DEBUG
        container = AppContainer(this)
        Notifications.createChannels(this)
        container.applicationScope.launch {
            container.dataInitializer.ensureInitialized()
            container.settingsRepository.settings
                .distinctUntilChangedBy { it.sourcesPeriodicCheck to it.sourcesUnmeteredOnly }
                .collect { settings -> SourceUpdateWorker.schedule(this@AdAwayApplication, settings) }
        }
    }
}
