package org.adaway.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.first

/** Restarts the VPN after the device starts, if the user wants it running and allows it at startup. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        launchAsync(context) { container ->
            container.dataInitializer.ensureInitialized()
            if (container.settingsRepository.settings.first().vpnStartOnBoot) {
                container.vpnController.ensureRunningInBackground()
            }
        }
    }
}
