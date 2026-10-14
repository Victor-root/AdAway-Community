package org.adaway.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the VPN right after an update of the app, if the user wanted it running. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        launchAsync(context) { container ->
            container.dataInitializer.ensureInitialized()
            container.vpnController.ensureRunningInBackground()
        }
    }
}
