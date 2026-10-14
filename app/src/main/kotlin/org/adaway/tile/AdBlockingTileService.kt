package org.adaway.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.adaway.AdAwayApplication

/** The quick settings tile, on while ad blocking is. The class name of the previous versions keeps the tile users placed. */
class AdBlockingTileService : TileService() {
    private val container get() = (application as AdAwayApplication).container
    private var listening: CoroutineScope? = null

    override fun onStartListening() {
        val scope = CoroutineScope(Dispatchers.Main.immediate + Job())
        listening = scope
        scope.launch { container.rootController.refresh() }
        scope.launch { container.blockingController.active.collect(::showActive) }
    }

    override fun onStopListening() {
        listening?.cancel()
        listening = null
    }

    override fun onClick() {
        container.applicationScope.launch {
            container.dataInitializer.ensureInitialized()
            val blocking = container.blockingController
            if (blocking.active.first()) blocking.disable() else blocking.enableInBackground()
        }
    }

    private fun showActive(active: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
