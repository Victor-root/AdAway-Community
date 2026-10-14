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
import org.adaway.data.settings.BlockingMethod
import org.adaway.data.settings.Settings
import java.util.concurrent.TimeUnit

/** Restarts the VPN the user wants when Android or a battery manager stopped it. */
class VpnHeartbeatWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as AdAwayApplication).container
        container.dataInitializer.ensureInitialized()
        container.vpnController.ensureRunningInBackground()
        return Result.success()
    }

    companion object {
        private const val NAME = "vpn-heartbeat"
        private const val REPEAT_MINUTES = 15L

        /** Runs while the user wants the VPN, cancelled as soon as they turn it off. */
        fun schedule(context: Context, settings: Settings) {
            val workManager = WorkManager.getInstance(context)
            if (settings.blockingMethod != BlockingMethod.VPN || !settings.vpnWanted) {
                workManager.cancelUniqueWork(NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<VpnHeartbeatWorker>(REPEAT_MINUTES, TimeUnit.MINUTES)
                .setInitialDelay(REPEAT_MINUTES, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            workManager.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
