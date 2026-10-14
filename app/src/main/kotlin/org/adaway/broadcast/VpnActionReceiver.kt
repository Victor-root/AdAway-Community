package org.adaway.broadcast

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** The buttons of the VPN notification. */
class VpnActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ENABLE -> launchAsync(context) { it.vpnController.enableInBackground() }
            DISABLE -> launchAsync(context) { it.vpnController.disable() }
        }
    }

    companion object {
        const val ENABLE = "org.adaway.action.ENABLE_VPN"
        const val DISABLE = "org.adaway.action.DISABLE_VPN"

        fun pendingIntent(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, VpnActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
