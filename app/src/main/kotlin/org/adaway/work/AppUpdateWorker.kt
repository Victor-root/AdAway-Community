package org.adaway.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.adaway.AdAwayApplication
import org.adaway.data.settings.Settings
import org.adaway.system.Notifications
import java.util.concurrent.TimeUnit

/** Daily check of the app releases, notifying a newer one. */
class AppUpdateWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as AdAwayApplication).container
        if (container.appUpdater.check()?.isNewer == true) Notifications.showAppUpdateAvailable(applicationContext)
        return Result.success()
    }

    companion object {
        private const val NAME = "app-update"
        private const val REPEAT_DAYS = 1L

        /** Schedules or cancels the daily check according to the settings. */
        fun schedule(context: Context, settings: Settings) {
            val workManager = WorkManager.getInstance(context)
            if (!settings.appUpdatePeriodicCheck) {
                workManager.cancelUniqueWork(NAME)
                return
            }
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val request = PeriodicWorkRequestBuilder<AppUpdateWorker>(REPEAT_DAYS, TimeUnit.DAYS)
                .setInitialDelay(REPEAT_DAYS, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()
            workManager.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
