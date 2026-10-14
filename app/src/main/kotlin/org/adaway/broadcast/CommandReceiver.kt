package org.adaway.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Lets other apps, such as automation ones, turn ad blocking on or off, as the previous versions
 * did: the `org.adaway.action.SEND_COMMAND` action with a `COMMAND` extra, `START` or `STOP`. Only
 * apps the user granted the `org.adaway.permission.SEND_COMMAND` permission can send it.
 */
class CommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        when (intent.getStringExtra(EXTRA_COMMAND)) {
            START -> launchAsync(context) { it.blockingController.enableInBackground() }
            STOP -> launchAsync(context) { it.blockingController.disable() }
        }
    }

    private companion object {
        const val ACTION = "org.adaway.action.SEND_COMMAND"
        const val EXTRA_COMMAND = "COMMAND"
        const val START = "START"
        const val STOP = "STOP"
    }
}
