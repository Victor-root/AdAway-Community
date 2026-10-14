package org.adaway.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import kotlinx.coroutines.launch
import org.adaway.AdAwayApplication
import org.adaway.AppContainer

/** Runs [block] in the app scope, keeping the broadcast alive until it is done. */
fun BroadcastReceiver.launchAsync(context: Context, block: suspend (AppContainer) -> Unit) {
    val container = (context.applicationContext as AdAwayApplication).container
    val pending = goAsync()
    container.applicationScope.launch {
        try {
            block(container)
        } finally {
            pending.finish()
        }
    }
}
