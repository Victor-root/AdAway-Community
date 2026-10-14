package org.adaway.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.first

/** Restarts the VPN, or the root web server with its new version, right after an update of the app. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        launchAsync(context) { container ->
            container.dataInitializer.ensureInitialized()
            container.vpnController.ensureRunningInBackground()
            container.webServer.restartIfWanted(container.settingsRepository.settings.first())
        }
    }
}
