package org.adaway.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import org.adaway.AdAwayApplication
import org.adaway.data.settings.Settings
import org.adaway.system.Notifications
import java.util.concurrent.TimeUnit

/** Periodic check of the sources: applies the updates, or notifies them when automatic sync is off. */
class SourceUpdateWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as AdAwayApplication).container
        container.dataInitializer.ensureInitialized()
        val settings = container.settingsRepository.settings.first()
        if (container.blockListUpdater.checkForUpdates() != true) return Result.success()
        if (settings.sourcesAutoSync) {
            container.blockListUpdater.syncAndApply(onlyPending = false)
        } else {
            Notifications.showSourceUpdateAvailable(applicationContext)
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "sources-update"
        private const val REPEAT_HOURS = 6L
        private const val INITIAL_DELAY_HOURS = 3L

        /** Schedules or cancels the periodic check according to the settings. */
        fun schedule(context: Context, settings: Settings) {
            val workManager = WorkManager.getInstance(context)
            if (!settings.sourcesPeriodicCheck) {
                workManager.cancelUniqueWork(NAME)
                return
            }
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (settings.sourcesUnmeteredOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .setRequiresStorageNotLow(true)
                .build()
            val request = PeriodicWorkRequestBuilder<SourceUpdateWorker>(REPEAT_HOURS, TimeUnit.HOURS)
                .setInitialDelay(INITIAL_DELAY_HOURS, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            workManager.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
